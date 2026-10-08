package com.nct32.notesplus.text

/**
 * A pure-Kotlin, extractive summarizer for casual English notes.
 *
 * No ML, no assets, no network. It scores each sentence by the frequency of its non-stopword
 * content words (a word-frequency / TextRank-style signal, normalized by the number of content
 * words so long sentences are not simply favored), picks the top ~25% of sentences (2-3 for
 * typical notes, at least 1), and returns them in their original order joined by spaces.
 *
 * Texts with fewer than 3 sentences cannot be meaningfully summarized: [summarize] returns
 * the trimmed input in that case (or an empty string when the input is blank).
 */
object Summarizer {

    /**
     * Returns an extractive summary of [text], or the trimmed text itself (or `""`) when the
     * input has fewer than 3 sentences.
     */
    fun summarize(text: String): String {
        val sentences = splitSentences(text)
        if (sentences.size < 3) return text.trim()

        val frequencies = buildFrequencies(sentences)
        val scores = sentences.map { score(it, frequencies) }
        val count = targetSentenceCount(sentences.size)

        // Pick the top `count` sentences by score (ties broken by earlier position),
        // then restore original document order.
        val chosen = scores
            .mapIndexed { index, sentenceScore -> index to sentenceScore }
            .sortedWith(compareByDescending<Pair<Int, Double>> { (_, sentenceScore) -> sentenceScore }.thenBy { (index, _) -> index })
            .take(count)
            .map { (index, _) -> index }
            .sorted()

        return chosen.joinToString(separator = " ") { sentences[it] }
    }

    /** How many sentences to keep: ~25% of the text, clamped to 2..3. */
    private fun targetSentenceCount(total: Int): Int =
        ((total + 3) / 4).coerceIn(2, 3)

    /** Splits [text] into sentences, keeping only non-blank ones. */
    private fun splitSentences(text: String): List<String> =
        SENTENCE_SPLIT
            .split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Document-wide word frequencies for [sentences]: each content word mapped to how many
     * times it appears across the whole text.
     */
    private fun buildFrequencies(sentences: List<String>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (sentence in sentences) {
            for (word in contentWords(sentence)) {
                counts[word] = (counts[word] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * Scores a sentence: the sum of the document frequency of each content word, divided by
     * the number of content words (average content-word frequency).
     */
    private fun score(sentence: String, frequencies: Map<String, Int>): Double {
        val words = contentWords(sentence)
        if (words.isEmpty()) return 0.0
        val total = words.sumOf { frequencies[it] ?: 0 }
        return total.toDouble() / words.size
    }

    /** The non-stopword, alphabetic words of [sentence] (lowercased). */
    private fun contentWords(sentence: String): List<String> =
        WORD_PATTERN
            .findAll(sentence)
            .map { it.value.lowercase() }
            .filter { (it.length > 1) && (it !in STOPWORDS) }
            .toList()

    // ---- patterns ----

    /** Splits on sentence-ending punctuation followed by whitespace (or end of text). */
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

    /** A word: 2+ letters (internal apostrophes allowed). */
    private val WORD_PATTERN = Regex("""\b[A-Za-z]{2,}(?:'[A-Za-z]+)?\b""")

    /** Common English stopwords excluded from scoring. */
    private val STOPWORDS: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "if", "then", "else", "because", "so",
        "as", "of", "at", "by", "for", "with", "about", "against", "between", "into",
        "through", "during", "before", "after", "above", "below", "to", "from", "up",
        "down", "in", "out", "on", "off", "over", "under", "again", "further", "once",
        "here", "there", "when", "where", "why", "how", "all", "any", "both", "each",
        "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only",
        "own", "same", "too", "very", "can", "will", "just", "should", "now", "is",
        "are", "was", "were", "be", "been", "being", "have", "has", "had", "having",
        "do", "does", "did", "doing", "i", "you", "he", "she", "it", "we", "they",
        "me", "him", "her", "us", "them", "my", "your", "his", "its", "our", "their",
        "this", "that", "these", "those", "what", "which", "who", "whom", "whose",
    )
}
