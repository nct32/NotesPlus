package com.nct32.notesplus.text

/**
 * The result of a [RewriteEngine] rewrite operation.
 *
 * @param rewrittenText The rewritten text.
 * @param changes Human-readable descriptions of every change that was made, in the
 * order they were applied.
 */
data class RewriteResult(
    val rewrittenText: String,
    val changes: List<String>,
) {
    /** `true` when at least one change was made. */
    val hasChanges: Boolean get() = changes.isNotEmpty()
}

/**
 * A pure-Kotlin rewrite engine for casual English notes.
 *
 * No ML, no assets, no network. Four deterministic rewrite passes are provided:
 *  - [improveTone]: curated synonym substitution (very good -> excellent, think -> believe, ...),
 *  - [shorten]: filler word/phrase removal and verbose-phrase compression,
 *  - [expand]: contraction and abbreviation expansion (don't -> do not, asap -> as soon as possible),
 *  - [fixGrammar]: standalone i -> I, missing apostrophes, a/an agreement, space cleanup.
 *
 * Each pass is conservative: only text matching a known rule is rewritten, and every
 * substitution is reported as a human-readable entry in [RewriteResult.changes].
 *
 * Phrase rules are applied in a single left-to-right pass (longest phrase first), so a
 * replacement can never be re-matched by a later rule.
 */
object RewriteEngine {

    // ------------------------------------------------------------------
    // improveTone
    // ------------------------------------------------------------------

    /**
     * Rewrites [text] with a curated synonym-substitution map, making the tone more
     * formal and precise. Matching is case-insensitive and replacements preserve the
     * original casing. "but" and "so" are only replaced at the start of a sentence.
     */
    fun improveTone(text: String): RewriteResult {
        val changes = mutableListOf<String>()
        val rewritten = COMBINED_TONE_PATTERN.replace(text) { match ->
            val rule = TONE_RULES.first { it.matches(match.value) }
            changes += "replaced '${describeValue(match.value)}' with '${rule.to}'"
            matchCase(rule.to, match.value)
        }
        return RewriteResult(rewritten, changes)
    }

    // ------------------------------------------------------------------
    // shorten
    // ------------------------------------------------------------------

    /**
     * Shortens [text] by removing filler words/phrases and compressing verbose phrases.
     * Removals are cleaned up so no double spaces or dangling punctuation are left behind.
     */
    fun shorten(text: String): RewriteResult {
        val changes = mutableListOf<String>()
        var removedFiller = false
        var result = COMBINED_SHORTEN_PATTERN.replace(text) { match ->
            val rule = SHORTEN_RULES.first { it.matches(match.value) }
            if (rule.to.isEmpty()) {
                removedFiller = true
                changes += if (rule.from.contains(' ')) {
                    "removed filler phrase '${rule.from}'"
                } else {
                    "removed filler word '${rule.from}'"
                }
                " "
            } else {
                changes += "shortened '${describeValue(match.value)}' to '${rule.to}'"
                matchCase(rule.to, match.value)
            }
        }
        if (removedFiller) {
            result = result
                .replace(DOUBLE_SPACE, " ")
                .replace(TRAILING_PUNCTUATION, "$1")
                .trim()
            val leftover = LEFTOVER_PUNCTUATION.find(result)
            if (leftover != null) {
                result = result.substring(leftover.value.length)
                changes += "removed leftover punctuation"
            }
        }
        return RewriteResult(result, changes)
    }

    // ------------------------------------------------------------------
    // expand
    // ------------------------------------------------------------------

    /**
     * Expands [text]: contractions become full forms (don't -> do not) and common
     * abbreviations become full words (asap -> as soon as possible). Matching is
     * case-insensitive except for the "I" contractions, and replacements preserve the
     * original casing.
     */
    fun expand(text: String): RewriteResult {
        val changes = mutableListOf<String>()
        val rewritten = COMBINED_EXPAND_PATTERN.replace(text) { match ->
            val rule = EXPAND_RULES.first { it.matches(match.value) }
            changes += "expanded '${describeValue(match.value)}' to '${rule.to}'"
            matchCase(rule.to, match.value)
        }
        return RewriteResult(rewritten, changes)
    }

    // ------------------------------------------------------------------
    // fixGrammar
    // ------------------------------------------------------------------

    /**
     * Fixes common grammar issues in [text]:
     *  - standalone lowercase "i" -> "I" (word-boundary, never inside a word)
     *  - missing apostrophes: dont -> don't, cant -> can't, isnt -> isn't, im -> I'm, aint -> am not
     *  - a/an agreement: a apple -> an apple, an book -> a book
     *  - collapses runs of 2+ spaces into a single space
     */
    fun fixGrammar(text: String): RewriteResult {
        val changes = mutableListOf<String>()
        var result = text

        // Standalone lowercase "i" -> "I".
        result = STANDALONE_I.replace(result) {
            changes += "capitalized 'i' to 'I'"
            "I"
        }

        // Missing apostrophes in common contractions.
        for (rule in APOSTROPHE_RULES) {
            result = rule.pattern.replace(result) { match ->
                changes += rule.describe(match.value)
                matchCase(rule.replacement, match.value)
            }
        }

        // a/an agreement.
        result = A_BEFORE_VOWEL.replace(result) { match ->
            val word = match.groupValues[1]
            changes += "changed 'a $word' to 'an $word'"
            "an $word"
        }
        result = AN_BEFORE_CONSONANT.replace(result) { match ->
            val word = match.groupValues[1]
            changes += "changed 'an $word' to 'a $word'"
            "a $word"
        }

        // Collapse runs of 2+ spaces into a single space.
        result = DOUBLE_SPACE.replace(result) {
            changes += "collapsed extra spaces"
            " "
        }

        return RewriteResult(result, changes)
    }

    // ------------------------------------------------------------------
    // shared rule machinery
    // ------------------------------------------------------------------

    /** A single rewrite rule: replace [from] with [to] (an empty [to] means "remove"). */
    private data class Rule(
        val from: String,
        val to: String,
        val caseInsensitive: Boolean = true,
        val sentenceStartOnly: Boolean = false,
        val trailingLookahead: String? = null,
    ) {
        /** `true` when this rule's [from] matches [matchValue]. */
        fun matches(matchValue: String): Boolean {
            val a = if (from.length > 1) from.replace(WHITESPACE, " ") else from
            val b = if (matchValue.length > 1) matchValue.replace(WHITESPACE, " ") else matchValue
            return if (caseInsensitive) a.equals(b, ignoreCase = true) else a == b
        }

        /**
         * Builds the self-contained regex alternative for this rule.
         *
         * When [caseInsensitive] is set, the whole pattern is wrapped in a scoped `(?i:...)`
         * group so the case-insensitive flag applies to the matched word (a bare `(?i:)` would
         * be an empty group and match nothing). The wrapping is per-alternative — not a global
         * [RegexOption.IGNORE_CASE] — so case-sensitive rules (e.g. "I'm") keep their casing.
         */
        fun alternative(): String {
            val escaped = Regex.escape(from).replace("\\ ", "\\s+")
            val start = when {
                sentenceStartOnly -> "(?:^|(?<=[.!?]\\s))"
                from.length == 1 -> "(?:(?<=\\s)|^)"
                else -> "\\b"
            }
            val end = when {
                from.length == 1 -> "(?=\\s|[,.;:!?)]|$)"
                from.endsWith(".") -> ""
                else -> "\\b"
            }
            val pattern = "$start$escaped$end${trailingLookahead ?: ""}"
            return if (caseInsensitive) "(?i:$pattern)" else pattern
        }
    }

    /** Builds one combined alternation regex for [rules], longest `from` first. */
    private fun combinedPattern(rules: List<Rule>): Regex =
        Regex(rules.sortedByDescending { it.from.length }.joinToString("|") { it.alternative() })

    /** Collapses whitespace runs and lowercases [s] for change descriptions. */
    private fun describeValue(s: String): String = s.replace(WHITESPACE, " ").lowercase()

    /**
     * Returns [replacement] with its casing adapted to [original]: each replacement word
     * takes the casing of the corresponding original word (ALL CAPS -> ALL CAPS,
     * Capitalized -> Capitalized, otherwise lowercase).
     */
    private fun matchCase(replacement: String, original: String): String {
        val originalWords = original.split(WHITESPACE)
        return replacement.split(WHITESPACE).mapIndexed { index, word ->
            val source = originalWords.getOrNull(index) ?: return@mapIndexed word
            when {
                source.length > 1 && source == source.uppercase() -> word.uppercase()
                source.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
                else -> word
            }
        }.joinToString(" ")
    }

    // ------------------------------------------------------------------
    // rule tables
    // ------------------------------------------------------------------

    /** Curated synonym substitutions for [improveTone]. */
    private val TONE_RULES: List<Rule> = listOf(
        Rule("very good", "excellent"),
        Rule("a lot of", "many"),
        Rule("lot of", "many"),
        Rule("kind of", "somewhat"),
        Rule("sort of", "somewhat"),
        Rule("kinda", "somewhat"),
        Rule("gonna", "going to"),
        Rule("wanna", "want to"),
        Rule("gotta", "have to"),
        Rule("ain't", "am not"),
        Rule("y'all", "you all"),
        Rule("nice", "pleasant"),
        Rule("big", "considerable"),
        Rule("start", "begin"),
        Rule("end", "finish"),
        Rule("help", "assist"),
        Rule("show", "demonstrate"),
        Rule("think", "believe"),
        Rule("want", "would like"),
        Rule("need", "require"),
        Rule("buy", "purchase"),
        // "about to" is a fixed construction and must not become "approximately to".
        Rule("about", "approximately", trailingLookahead = "(?!\\s+to\\b)"),
        Rule("also", "additionally"),
        Rule("but", "however", sentenceStartOnly = true),
        Rule("so", "therefore", sentenceStartOnly = true),
        Rule("get", "obtain"),
        Rule("give", "provide"),
        Rule("ask", "inquire"),
        Rule("check", "verify"),
        Rule("use", "utilize"),
        Rule("try", "attempt"),
        Rule("problem", "issue"),
        Rule("answer", "response"),
        Rule("idea", "concept"),
        Rule("part", "section"),
        Rule("piece", "portion"),
        Rule("real", "genuine"),
        Rule("important", "crucial"),
        Rule("happy", "pleased"),
        Rule("sad", "unhappy"),
        Rule("angry", "frustrated"),
        Rule("fast", "quick"),
        Rule("slow", "gradual"),
        Rule("easy", "straightforward"),
        Rule("hard", "difficult"),
        Rule("wrong", "incorrect"),
        Rule("right", "correct"),
    )

    /** Filler removal and phrase compression rules for [shorten]. */
    private val SHORTEN_RULES: List<Rule> = listOf(
        Rule("it is important to note that", "note that"),
        Rule("at this point in time", "now"),
        Rule("at the end of the day", "ultimately"),
        Rule("due to the fact that", "because"),
        Rule("in the event that", "if"),
        Rule("for the purpose of", "for"),
        Rule("in order to", "to"),
        Rule("with regard to", "regarding"),
        Rule("in spite of", "despite"),
        Rule("on the other hand", "however"),
        Rule("as a matter of fact", "in fact"),
        Rule("in my opinion", "I think"),
        Rule("each and every", "every"),
        Rule("first and foremost", "first"),
        Rule("when it comes to", "for"),
        Rule("in terms of", "regarding"),
        Rule("actually", ""),
        Rule("basically", ""),
        Rule("literally", ""),
        Rule("just", ""),
        Rule("really", ""),
        Rule("very", ""),
        Rule("quite", ""),
    )

    /** Contraction and abbreviation expansions for [expand]. */
    private val EXPAND_RULES: List<Rule> = listOf(
        Rule("don't", "do not"),
        Rule("can't", "cannot"),
        Rule("won't", "will not"),
        Rule("isn't", "is not"),
        Rule("aren't", "are not"),
        Rule("wasn't", "was not"),
        Rule("weren't", "were not"),
        Rule("it's", "it is"),
        Rule("I'm", "I am", caseInsensitive = false),
        Rule("I've", "I have", caseInsensitive = false),
        Rule("I'll", "I will", caseInsensitive = false),
        Rule("I'd", "I would", caseInsensitive = false),
        Rule("we're", "we are"),
        Rule("we've", "we have"),
        Rule("we'll", "we will"),
        Rule("they're", "they are"),
        Rule("they've", "they have"),
        Rule("they'll", "they will"),
        Rule("that's", "that is"),
        Rule("there's", "there is"),
        Rule("what's", "what is"),
        Rule("let's", "let us"),
        Rule("couldn't", "could not"),
        Rule("shouldn't", "should not"),
        Rule("wouldn't", "would not"),
        Rule("didn't", "did not"),
        Rule("hasn't", "has not"),
        Rule("haven't", "have not"),
        Rule("doesn't", "does not"),
        Rule("info", "information"),
        Rule("asap", "as soon as possible"),
        Rule("btw", "by the way"),
        Rule("e.g.", "for example"),
        Rule("i.e.", "that is"),
        Rule("ok", "okay"),
        Rule("u", "you"),
        Rule("thx", "thanks"),
        Rule("pls", "please"),
        Rule("tmrw", "tomorrow"),
        Rule("msg", "message"),
    )

    private val COMBINED_TONE_PATTERN: Regex = combinedPattern(TONE_RULES)
    private val COMBINED_SHORTEN_PATTERN: Regex = combinedPattern(SHORTEN_RULES)
    private val COMBINED_EXPAND_PATTERN: Regex = combinedPattern(EXPAND_RULES)

    // ------------------------------------------------------------------
    // fixGrammar patterns
    // ------------------------------------------------------------------

    /** A standalone lowercase "i" (not inside a word, not the "i" of "i.e."). */
    private val STANDALONE_I = Regex("""(?<![A-Za-z])i(?![A-Za-z.])""")

    /** A missing-apostrophe fix: [pattern] -> [replacement] with a change description. */
    private data class ApostropheRule(
        val pattern: Regex,
        val replacement: String,
        val describe: (String) -> String,
    )

    private val APOSTROPHE_RULES: List<ApostropheRule> = listOf(
        ApostropheRule(Regex("""(?i)\bdont\b"""), "don't") { value -> "changed '${value.lowercase()}' to 'don't'" },
        ApostropheRule(Regex("""(?i)\bcant\b"""), "can't") { value -> "changed '${value.lowercase()}' to 'can't'" },
        ApostropheRule(Regex("""(?i)\bisnt\b"""), "isn't") { value -> "changed '${value.lowercase()}' to 'isn't'" },
        ApostropheRule(Regex("""(?i)\baint\b"""), "am not") { value -> "changed '${value.lowercase()}' to 'am not'" },
        // "im" is only rewritten in lowercase so that "IM" (instant messaging) is left alone.
        ApostropheRule(Regex("""(?<![A-Za-z])im(?![A-Za-z])"""), "I'm") { value -> "changed '$value' to 'I'm'" },
    )

    /** "a" followed by a vowel-starting word. */
    private val A_BEFORE_VOWEL = Regex("""(?i)\ba\s+([aeiou][a-z]*)\b""")

    /** "an" followed by a consonant-starting word. */
    private val AN_BEFORE_CONSONANT = Regex("""(?i)\ban\s+([bcdfghjklmnpqrstvwxyz][a-z]*)\b""")

    /** Two or more consecutive spaces. */
    private val DOUBLE_SPACE = Regex(" {2,}")

    /** Whitespace runs, used for normalization and word splitting. */
    private val WHITESPACE = Regex("\\s+")

    /** Whitespace run immediately before punctuation (e.g. the space in `word ,`). */
    private val TRAILING_PUNCTUATION = Regex("\\s+([,;:!?])")

    /** Leading punctuation left dangling after a filler removal (e.g. `, ` at the start). */
    private val LEFTOVER_PUNCTUATION = Regex("^[,;:]+\\s*")
}
