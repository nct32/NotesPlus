package com.nct32.notesplus.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin extractive [Summarizer].
 */
class SummarizerTest {

    @Test
    fun returns_trimmed_text_when_fewer_than_three_sentences() {
        val text = "  The cat sat down. It was a good cat.  "
        assertEquals("The cat sat down. It was a good cat.", Summarizer.summarize(text))
    }

    @Test
    fun returns_empty_string_for_blank_input() {
        assertEquals("", Summarizer.summarize("   \n  "))
    }

    @Test
    fun returns_single_sentence_unchanged() {
        assertEquals("Just one sentence here.", Summarizer.summarize("Just one sentence here."))
    }

    @Test
    fun summary_keeps_at_least_one_sentence() {
        val text = "One sentence. Two sentences. Three sentences."
        val summary = Summarizer.summarize(text)
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun summary_sentences_are_original_sentences_in_original_order() {
        val s1 = "The team shipped the onboarding flow on friday."
        val s2 = "The weather was nice that day."
        val s3 = "Onboarding was the main topic of the meeting."
        val s4 = "Everyone liked the new onboarding tour."
        val text = "$s1 $s2 $s3 $s4"

        val summary = Summarizer.summarize(text)
        val originals = listOf(s1, s2, s3, s4)

        // The summary must contain 2-3 of the original sentences.
        val chosen = originals.filter { summary.contains(it) }
        assertTrue("summary should contain 2-3 original sentences, was '$summary'", chosen.size in 2..3)

        // Original order must be preserved in the summary.
        val positions = chosen.map { originals.indexOf(it) }
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun summary_prefers_sentences_with_repeated_content_words() {
        // "onboarding" appears in 3 of the 4 sentences, so at least one of those sentences
        // (and the most frequent one first) must be in the summary.
        val s1 = "The onboarding flow needs a short interactive tour."
        val s2 = "The weather report was boring and long."
        val s3 = "Onboarding highlights folders, search, and the summary feature."
        val s4 = "We also discussed the onboarding checklist for launch."
        val text = "$s1 $s2 $s3 $s4"

        val summary = Summarizer.summarize(text)
        assertTrue("summary should include an onboarding sentence", "onboarding" in summary.lowercase())
        // The filler sentence with no repeated content words should not be picked.
        assertFalse("filler sentence should not be in summary", summary.contains(s2))
    }

    @Test
    fun summary_of_long_text_is_about_quarter_of_sentences() {
        val sentences = (1..8).map { "Sentence number $it talks about topic $it and details." }
        val text = sentences.joinToString(" ")

        val summary = Summarizer.summarize(text)
        val chosen = sentences.filter { summary.contains(it) }
        // ~25% of 8 = 2, clamped to 2..3.
        assertTrue("expected 2-3 sentences, was ${chosen.size}", chosen.size in 2..3)
    }

    @Test
    fun summary_is_idempotent_and_deterministic() {
        val text = "The cat chased the mouse. The mouse ran away. The cat is a good hunter. " +
            "The mouse hid in the hole. The cat waited patiently. The mouse came out again."
        val first = Summarizer.summarize(text)
        val second = Summarizer.summarize(text)
        assertEquals(first, second)
    }
}
