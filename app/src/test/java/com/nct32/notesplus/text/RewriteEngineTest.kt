package com.nct32.notesplus.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin [RewriteEngine].
 *
 * Each of the four rewrite passes is exercised for: multiple substitutions, no-op on clean
 * text, casing preservation, and (where relevant) that no double spaces are left behind.
 */
class RewriteEngineTest {

    // ------------------------------------------------------------------
    // improveTone
    // ------------------------------------------------------------------

    @Test
    fun improveTone_applies_multiple_substitutions() {
        val result = RewriteEngine.improveTone("I think it is a big problem")
        assertEquals("I believe it is a considerable issue", result.rewrittenText)
        assertEquals(3, result.changes.size)
        assertTrue(result.hasChanges)
    }

    @Test
    fun improveTone_is_noop_on_clean_text() {
        val result = RewriteEngine.improveTone("The weather is beautiful today")
        assertEquals("The weather is beautiful today", result.rewrittenText)
        assertTrue(result.changes.isEmpty())
        assertFalse(result.hasChanges)
    }

    @Test
    fun improveTone_preserves_original_casing() {
        val result = RewriteEngine.improveTone("I Think This Is Big")
        assertEquals("I Believe This Is Considerable", result.rewrittenText)
        assertEquals(2, result.changes.size)
    }

    @Test
    fun improveTone_replaces_but_and_so_only_at_sentence_start() {
        // Sentence-start "But" and "So" are replaced...
        val start = RewriteEngine.improveTone("But I agree. So I left.")
        assertEquals("However I agree. Therefore I left.", start.rewrittenText)
        assertEquals(2, start.changes.size)

        // ...while mid-sentence "but"/"so" are left alone.
        val mid = RewriteEngine.improveTone("I agree but I left so I stayed")
        assertEquals("I agree but I left so I stayed", mid.rewrittenText)
        assertTrue(mid.changes.isEmpty())
    }

    @Test
    fun improveTone_replaces_about_but_not_about_to() {
        val plain = RewriteEngine.improveTone("I need about 5 minutes")
        assertEquals("I require approximately 5 minutes", plain.rewrittenText)
        assertEquals(2, plain.changes.size)

        // "about to" is a fixed construction and must not become "approximately to".
        val aboutTo = RewriteEngine.improveTone("I am about to leave")
        assertEquals("I am about to leave", aboutTo.rewrittenText)
        assertTrue(aboutTo.changes.isEmpty())
    }

    @Test
    fun improveTone_applies_every_specified_synonym() {
        val cases = mapOf(
            "very good" to "excellent",
            "a lot of" to "many",
            "kind of" to "somewhat",
            "gonna" to "going to",
            "wanna" to "want to",
            "get" to "obtain",
            "give" to "provide",
            "ask" to "inquire",
            "check" to "verify",
            "try" to "attempt",
            "problem" to "issue",
            "answer" to "response",
            "idea" to "concept",
            "part" to "section",
            "nice" to "pleasant",
            "big" to "considerable",
            "start" to "begin",
            "end" to "finish",
            "help" to "assist",
            "show" to "demonstrate",
            "think" to "believe",
            "want" to "would like",
            "need" to "require",
            "buy" to "purchase",
            "also" to "additionally",
            "use" to "utilize",
            "real" to "genuine",
            "important" to "crucial",
            "happy" to "pleased",
            "sad" to "unhappy",
            "angry" to "frustrated",
            "fast" to "quick",
            "slow" to "gradual",
            "easy" to "straightforward",
            "hard" to "difficult",
            "wrong" to "incorrect",
            "right" to "correct"
        )
        for ((from, to) in cases) {
            val result = RewriteEngine.improveTone("the $from thing")
            assertEquals(
                "expected '$to' for '$from' but was '${result.rewrittenText}'",
                "the $to thing",
                result.rewrittenText
            )
        }
    }

    // ------------------------------------------------------------------
    // shorten
    // ------------------------------------------------------------------

    @Test
    fun shorten_removes_fillers_and_compresses_phrases() {
        val result = RewriteEngine.shorten("I actually really like this, in order to be honest")
        assertEquals("I like this, to be honest", result.rewrittenText)
        assertEquals(3, result.changes.size)
        assertTrue(result.hasChanges)
    }

    @Test
    fun shorten_is_noop_on_clean_text() {
        val result = RewriteEngine.shorten("The meeting is at noon")
        assertEquals("The meeting is at noon", result.rewrittenText)
        assertTrue(result.changes.isEmpty())
        assertFalse(result.hasChanges)
    }

    @Test
    fun shorten_leaves_no_double_spaces_after_removal() {
        val result = RewriteEngine.shorten("I just really like it")
        assertEquals("I like it", result.rewrittenText)
        assertEquals(2, result.changes.size)
        assertFalse(result.rewrittenText.contains("  "))
    }

    @Test
    fun shorten_compresses_verbose_phrases() {
        val result = RewriteEngine.shorten("I did it in order to learn, due to the fact that it was required")
        assertEquals("I did it to learn, because it was required", result.rewrittenText)
        assertEquals(2, result.changes.size)
    }

    @Test
    fun shorten_preserves_original_casing() {
        val result = RewriteEngine.shorten("I did it IN ORDER TO learn")
        assertEquals("I did it TO learn", result.rewrittenText)
        assertEquals(1, result.changes.size)
    }

    @Test
    fun shorten_applies_every_specified_rule() {
        val cases = mapOf(
            "actually" to "",
            "basically" to "",
            "literally" to "",
            "just" to "",
            "really" to "",
            "very" to "",
            "quite" to "",
            "in order to" to "to",
            "due to the fact that" to "because",
            "at this point in time" to "now",
            "in the event that" to "if",
            "for the purpose of" to "for",
            "with regard to" to "regarding",
            "in spite of" to "despite",
            "on the other hand" to "however",
            "as a matter of fact" to "in fact",
            "each and every" to "every",
            "first and foremost" to "first",
            "when it comes to" to "for",
            "in terms of" to "regarding"
        )
        for ((from, to) in cases) {
            val result = RewriteEngine.shorten("x $from y")
            val expected = "x $to y".replace("  ", " ").trim()
            assertEquals(
                "expected '$expected' for '$from' but was '${result.rewrittenText}'",
                expected,
                result.rewrittenText
            )
        }
    }

    // ------------------------------------------------------------------
    // expand
    // ------------------------------------------------------------------

    @Test
    fun expand_expands_multiple_contractions() {
        val result = RewriteEngine.expand("I don't know, can't you see it's fine?")
        assertEquals("I do not know, cannot you see it is fine?", result.rewrittenText)
        assertEquals(3, result.changes.size)
        assertTrue(result.hasChanges)
    }

    @Test
    fun expand_is_noop_on_clean_text() {
        val result = RewriteEngine.expand("The meeting is scheduled for noon")
        assertEquals("The meeting is scheduled for noon", result.rewrittenText)
        assertTrue(result.changes.isEmpty())
        assertFalse(result.hasChanges)
    }

    @Test
    fun expand_preserves_original_casing() {
        val result = RewriteEngine.expand("I Can't Do It")
        assertEquals("I Cannot Do It", result.rewrittenText)
        assertEquals(1, result.changes.size)
    }

    @Test
    fun expand_expands_abbreviations() {
        val result = RewriteEngine.expand("Send me the info asap, thx")
        assertEquals("Send me the information as soon as possible, thanks", result.rewrittenText)
        assertEquals(3, result.changes.size)
    }

    @Test
    fun expand_expands_i_contraction_case_sensitively() {
        // Capital "I'm" is expanded...
        val capital = RewriteEngine.expand("I'm happy")
        assertEquals("I am happy", capital.rewrittenText)
        assertEquals(1, capital.changes.size)

        // ...but lowercase "i'm" is left alone (the I-contractions are case-sensitive).
        val lowercase = RewriteEngine.expand("i'm happy")
        assertEquals("i'm happy", lowercase.rewrittenText)
        assertTrue(lowercase.changes.isEmpty())
    }

    @Test
    fun expand_applies_every_specified_rule() {
        val contractions = mapOf(
            "don't" to "do not",
            "can't" to "cannot",
            "won't" to "will not",
            "isn't" to "is not",
            "aren't" to "are not",
            "wasn't" to "was not",
            "weren't" to "were not",
            "it's" to "it is",
            "we're" to "we are",
            "we've" to "we have",
            "we'll" to "we will",
            "they're" to "they are",
            "they've" to "they have",
            "they'll" to "they will",
            "that's" to "that is",
            "there's" to "there is",
            "what's" to "what is",
            "let's" to "let us",
            "couldn't" to "could not",
            "shouldn't" to "should not",
            "wouldn't" to "would not",
            "didn't" to "did not",
            "hasn't" to "has not",
            "haven't" to "have not",
            "doesn't" to "does not"
        )
        for ((from, to) in contractions) {
            val result = RewriteEngine.expand("x $from y")
            assertEquals(
                "expected '$to' for '$from' but was '${result.rewrittenText}'",
                "x $to y",
                result.rewrittenText
            )
        }
    }

    // ------------------------------------------------------------------
    // fixGrammar
    // ------------------------------------------------------------------

    @Test
    fun fixGrammar_fixes_i_apostrophes_and_articles() {
        val result = RewriteEngine.fixGrammar("i dont like a apple, an orange is fine")
        assertEquals("I don't like an apple, an orange is fine", result.rewrittenText)
        assertEquals(3, result.changes.size)
        assertTrue(result.hasChanges)
    }

    @Test
    fun fixGrammar_is_noop_on_clean_text() {
        val result = RewriteEngine.fixGrammar("The book is on the table")
        assertEquals("The book is on the table", result.rewrittenText)
        assertTrue(result.changes.isEmpty())
        assertFalse(result.hasChanges)
    }

    @Test
    fun fixGrammar_fixes_a_an_agreement() {
        val result = RewriteEngine.fixGrammar("I have a umbrella and an house")
        assertEquals("I have an umbrella and a house", result.rewrittenText)
        assertEquals(2, result.changes.size)
    }

    @Test
    fun fixGrammar_collapses_extra_spaces() {
        val result = RewriteEngine.fixGrammar("I   like    this")
        assertEquals("I like this", result.rewrittenText)
        assertTrue(result.hasChanges)
        assertFalse(result.rewrittenText.contains("  "))
    }

    @Test
    fun fixGrammar_capitalizes_standalone_i_but_not_inside_words() {
        val result = RewriteEngine.fixGrammar("i went to the university")
        assertEquals("I went to the university", result.rewrittenText)
        // Only the standalone "i" is capitalized; the "i" inside "university" is untouched.
        assertEquals(1, result.changes.size)
    }

    @Test
    fun fixGrammar_fixes_lowercase_im_but_not_uppercase_im() {
        val lowercase = RewriteEngine.fixGrammar("im going to the store")
        assertEquals("I'm going to the store", lowercase.rewrittenText)
        assertEquals(1, lowercase.changes.size)

        // "IM" (instant messaging) is left alone.
        val uppercase = RewriteEngine.fixGrammar("IM a fan")
        assertEquals("IM a fan", uppercase.rewrittenText)
        assertTrue(uppercase.changes.isEmpty())
    }

    @Test
    fun fixGrammar_fixes_missing_apostrophes() {
        val cases = mapOf(
            "dont" to "don't",
            "cant" to "can't",
            "isnt" to "isn't",
            "aint" to "am not"
        )
        for ((from, to) in cases) {
            val result = RewriteEngine.fixGrammar("I $from know")
            assertEquals(
                "expected '$to' for '$from' but was '${result.rewrittenText}'",
                "I $to know",
                result.rewrittenText
            )
        }
    }
}
