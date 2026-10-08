package com.nct32.notesplus.text

/**
 * A single correction produced by [AutocorrectEngine].
 *
 * @param original The exact text that was replaced (e.g. `"teh"`).
 * @param replacement The replacement text (e.g. `"the"`).
 */
data class Fix(
    val original: String,
    val replacement: String,
)

/**
 * The result of running [AutocorrectEngine.correct] on a piece of text.
 *
 * @param correctedText The fully corrected text.
 * @param fixes Every replacement that was made, in the order they were applied.
 */
data class AutocorrectResult(
    val correctedText: String,
    val fixes: List<Fix>,
) {
    /** `true` when at least one correction was made. */
    val hasFixes: Boolean get() = fixes.isNotEmpty()
}

/**
 * A pure-Kotlin, offline autocorrect engine for casual English notes.
 *
 * No ML, no assets, no network. Corrections come from:
 *  1. a curated [CommonMisspellings] map (recieve -> receive, teh -> the, ...), and
 *  2. a small set of deterministic grammar rules (i -> I, dont -> don't, a apple -> an apple,
 *     collapsing double spaces).
 *
 * The engine is conservative: it only rewrites text that matches a known rule, so
 * legitimate names, jargon, and unknown words are left untouched.
 */
object AutocorrectEngine {

    /**
     * Corrects [text] and returns the corrected text together with the list of individual
     * fixes that were applied.
     *
     * The input is never modified in place; a new string is returned.
     */
    fun correct(text: String): AutocorrectResult {
        val fixes = mutableListOf<Fix>()

        // 1. Single-word corrections from the curated misspelling map.
        var corrected = WORD_PATTERN.replace(text) { match ->
            val word = match.value
            val fix = correctWord(word) ?: return@replace word
            fixes += Fix(fix.original, fix.replacement)
            fix.replacement
        }

        // 2. Multi-word grammar rules (i -> I, missing apostrophes, a/an agreement, double spaces).
        corrected = applyGrammarRules(corrected, fixes)

        return AutocorrectResult(corrected, fixes)
    }

    /**
     * Returns a fix for a single [word] if it is a known misspelling, or `null` when the
     * word should be left as-is.
     */
    private fun correctWord(word: String): Fix? {
        // Preserve the leading/trailing quote marks and punctuation that [WORD_PATTERN] keeps.
        val core = word.trim { it in QUOTE_CHARS }
        if (core.isEmpty()) return null

        val correction = CommonMisspellings.MAP[core.lowercase()] ?: return null
        return Fix(word, surround(correction, word, core))
    }

    /**
     * Applies the multi-word grammar rules in a fixed order:
     *  - `i` -> `I` (standalone lowercase first person)
     *  - missing apostrophes: dont -> don't, cant -> can't, isnt -> isn't, im -> I'm, ...
     *  - a/an agreement: `a apple` -> `an apple`, `an orange` stays, `an book` -> `a book`
     *  - collapse runs of 2+ spaces into a single space
     */
    private fun applyGrammarRules(text: String, fixes: MutableList<Fix>): String {
        var result = text

        // Standalone lowercase "i" -> "I".
        result = STANDALONE_I.replace(result) { match ->
            fixes += Fix(match.value, "I")
            "I"
        }

        // Missing apostrophes in common contractions. Replacements may reference a capture
        // group (e.g. "I'm $1" for the im rule), so the match is expanded manually.
        for ((pattern, replacement) in CONTRACTION_RULES) {
            result = pattern.replace(result) { match ->
                val expanded = if ("$1" in replacement) {
                    replacement.replace("$1", match.groupValues[1])
                } else {
                    replacement
                }
                fixes += Fix(match.value, expanded)
                expanded
            }
        }

        // a/an agreement.
        result = A_BEFORE_VOWEL.replace(result) { match ->
            fixes += Fix(match.value, "an ${match.groupValues[1]}")
            "an ${match.groupValues[1]}"
        }
        result = AN_BEFORE_CONSONANT.replace(result) { match ->
            fixes += Fix(match.value, "a ${match.groupValues[1]}")
            "a ${match.groupValues[1]}"
        }

        // Collapse double (or worse) spaces.
        result = DOUBLE_SPACE.replace(result) { match ->
            fixes += Fix(match.value, " ")
            " "
        }

        return result
    }

    /**
     * Re-applies the original leading/trailing quote/punctuation of [original] around
     * [replacement] (the corrected core), preserving the original casing of the first letter.
     */
    private fun surround(replacement: String, original: String, originalCore: String): String {
        val leading = original.length - original.trimStart { it in QUOTE_CHARS }.length
        val trailing = original.length - original.trimEnd { it in QUOTE_CHARS }.length
        val base = if (originalCore.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement
        return original.substring(0, leading) + base + original.substring(original.length - trailing)
    }

    // ---- patterns & rules ----

    /** A word: 2+ letters (internal apostrophes allowed, e.g. "don't"). */
    private val WORD_PATTERN = Regex("""\b[A-Za-z]{2,}(?:'[A-Za-z]+)?\b""")

    /** A standalone lowercase "i" (the first person pronoun). */
    private val STANDALONE_I = Regex("""(?<![A-Za-z])i(?![A-Za-z])""")

    /** Missing apostrophes in common contractions (case-insensitive). */
    private val CONTRACTION_RULES: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\bdont\b""") to "don't",
        Regex("""(?i)\bcant\b""") to "can't",
        Regex("""(?i)\bisnt\b""") to "isn't",
        Regex("""(?i)\bwont\b""") to "won't",
        Regex("""(?i)\bdidnt\b""") to "didn't",
        Regex("""(?i)\bdoesnt\b""") to "doesn't",
        Regex("""(?i)\bcouldnt\b""") to "couldn't",
        Regex("""(?i)\bwouldnt\b""") to "wouldn't",
        Regex("""(?i)\bshouldnt\b""") to "shouldn't",
        Regex("""(?i)\bwasnt\b""") to "wasn't",
        Regex("""(?i)\barent\b""") to "aren't",
        Regex("""(?i)\bhasnt\b""") to "hasn't",
        Regex("""(?i)\bhavent\b""") to "haven't",
        Regex("""(?i)\bwerent\b""") to "weren't",
        // "im" is ambiguous (the pronoun vs. the abbreviation), so it is only expanded when
        // followed by a lowercase word — i.e. it starts a clause. (String replacement so the
        // $1 group reference is honored.)
        Regex("""(?i)\bim\s+([a-z])""") to "I'm $1"
    )

    /** "a" followed by a vowel-starting word. */
    private val A_BEFORE_VOWEL = Regex("""(?i)\ba\s+([aeiou][a-z]*)\b""")

    /** "an" followed by a consonant-starting word. */
    private val AN_BEFORE_CONSONANT = Regex("""(?i)\ban\s+([bcdfghjklmnpqrstvwxyz][a-z]*)\b""")

    /** Two or more consecutive spaces. */
    private val DOUBLE_SPACE = Regex(""" {2,}""")

    /** Quote/punctuation characters kept around a word by [WORD_PATTERN] matches. */
    private const val QUOTE_CHARS = "'\""
}
