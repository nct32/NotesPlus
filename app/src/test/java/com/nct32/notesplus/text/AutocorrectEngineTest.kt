package com.nct32.notesplus.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin [AutocorrectEngine].
 */
class AutocorrectEngineTest {

    // ---- misspelling map ----

    @Test
    fun corrects_common_misspellings() {
        val result = AutocorrectEngine.correct("I recieve a letter and definately seperate the files")
        assertEquals(
            "I receive a letter and definitely separate the files",
            result.correctedText
        )
        assertEquals(3, result.fixes.size)
        assertTrue(result.hasFixes)
    }

    @Test
    fun corrects_the_documented_typo_set() {
        val cases = mapOf(
            "occured" to "occurred",
            "untill" to "until",
            "wich" to "which",
            "thier" to "their",
            "becuase" to "because",
            "tommorow" to "tomorrow",
            "throught" to "through",
            "goverment" to "government",
            "teh" to "the",
            "adn" to "and",
            "wiht" to "with",
            "fo" to "for",
            "taht" to "that"
        )
        for ((misspelled, expected) in cases) {
            val result = AutocorrectEngine.correct("the $misspelled thing")
            assertEquals(
                "expected '$expected' for '$misspelled' but was '${result.correctedText}'",
                "the $expected thing",
                result.correctedText
            )
        }
    }

    @Test
    fun corrects_the_full_specified_misspelling_list() {
        // Every entry of the curated map must be applied by the engine.
        for ((misspelled, expected) in CommonMisspellings.MAP) {
            val result = AutocorrectEngine.correct("the $misspelled thing")
            assertEquals(
                "expected '$expected' for '$misspelled' but was '${result.correctedText}'",
                "the $expected thing",
                result.correctedText
            )
        }
    }

    @Test
    fun preserves_original_case_of_corrected_word() {
        val result = AutocorrectEngine.correct("I Recieve a letter")
        assertEquals("I Receive a letter", result.correctedText)
    }

    @Test
    fun corrects_multiple_occurrences_of_same_word() {
        val result = AutocorrectEngine.correct("teh cat sat on teh mat")
        assertEquals("the cat sat on the mat", result.correctedText)
        assertEquals(2, result.fixes.count { it.original == "teh" && it.replacement == "the" })
    }

    // ---- grammar rules ----

    @Test
    fun capitalizes_standalone_lowercase_i() {
        val result = AutocorrectEngine.correct("i went to the park")
        assertEquals("I went to the park", result.correctedText)
        assertTrue(result.fixes.any { it.original == "i" && it.replacement == "I" })
    }

    @Test
    fun does_not_capitalize_i_inside_words() {
        val result = AutocorrectEngine.correct("the cat and the dog")
        assertEquals("the cat and the dog", result.correctedText)
        assertFalse(result.hasFixes)
    }

    @Test
    fun adds_missing_apostrophes_to_contractions() {
        val result = AutocorrectEngine.correct("i dont know why cant we go")
        assertEquals("I don't know why can't we go", result.correctedText)
    }

    @Test
    fun expands_im_only_when_starting_a_clause() {
        val result = AutocorrectEngine.correct("im going to the store")
        assertEquals("I'm going to the store", result.correctedText)
    }

    @Test
    fun fixes_a_an_agreement() {
        val aResult = AutocorrectEngine.correct("i ate a apple")
        assertEquals("I ate an apple", aResult.correctedText)

        val anResult = AutocorrectEngine.correct("i ate a orange")
        assertEquals("I ate an orange", anResult.correctedText)
    }

    @Test
    fun leaves_correct_a_an_usage_untouched() {
        // "an orange" and "a book" are already correct and must not be changed.
        val result = AutocorrectEngine.correct("i ate an orange and a book")
        assertEquals("I ate an orange and a book", result.correctedText)
        assertTrue(result.fixes.none { it.original.startsWith("a ") || it.original.startsWith("an ") })
    }

    @Test
    fun collapses_double_spaces() {
        val result = AutocorrectEngine.correct("the cat   sat on the mat")
        assertEquals("the cat sat on the mat", result.correctedText)
        assertTrue(result.fixes.any { it.original == "   " && it.replacement == " " })
    }

    // ---- no false positives ----

    @Test
    fun leaves_unknown_words_untouched() {
        // "weathr" and "zzzqqq" are not in the misspelling map and must be left as-is.
        val result = AutocorrectEngine.correct("the weathr and zzzqqq were here")
        assertEquals("the weathr and zzzqqq were here", result.correctedText)
        assertFalse(result.hasFixes)
    }

    // ---- invariants ----

    @Test
    fun clean_text_is_returned_unchanged() {
        val clean = "The quick brown fox jumped over the lazy dog."
        val result = AutocorrectEngine.correct(clean)
        assertEquals(clean, result.correctedText)
        assertTrue(result.fixes.isEmpty())
        assertFalse(result.hasFixes)
    }

    @Test
    fun empty_text_is_returned_unchanged() {
        val result = AutocorrectEngine.correct("")
        assertEquals("", result.correctedText)
        assertTrue(result.fixes.isEmpty())
    }

    @Test
    fun fixes_list_matches_corrected_text() {
        val result = AutocorrectEngine.correct("teh cat and teh dog")
        // Re-applying the fixes to the original text must reproduce the corrected text.
        var rebuilt = "teh cat and teh dog"
        for (fix in result.fixes) {
            rebuilt = rebuilt.replaceFirst(fix.original, fix.replacement)
        }
        assertEquals(result.correctedText, rebuilt)
    }
}
