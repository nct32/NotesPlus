package com.nct32.notesplus.ui.rich

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure span<->Markdown conversion in [RichTextMarkdown] used to persist the
 * note body's inline formatting (bold / italic / strikethrough / underline) as a plain String in
 * Room.
 */
class RichTextMarkdownTest {

    // ---- helpers -------------------------------------------------------------

    private fun bold(start: Int, end: Int) =
        AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), start, end)

    private fun italic(start: Int, end: Int) =
        AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), start, end)

    private fun strike(start: Int, end: Int) =
        AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)

    private fun underline(start: Int, end: Int) =
        AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

    private fun styleOf(annotated: AnnotatedString, start: Int, end: Int): RichStyle? =
        spansOf(annotated).firstOrNull { it.start == start && it.end == end }?.style

    // ---- plain text (no spans) ----------------------------------------------

    @Test
    fun plain_text_without_spans_round_trips() {
        val annotated = AnnotatedString("just plain text")
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("just plain text", md)
        assertEquals(annotated, RichTextMarkdown.fromMarkdown(md))
    }

    @Test
    fun empty_text_round_trips() {
        assertEquals("", RichTextMarkdown.toMarkdown(AnnotatedString("")))
        assertEquals("", RichTextMarkdown.fromMarkdown("").text)
    }

    // ---- each style individually --------------------------------------------

    @Test
    fun bold_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("Hello world", listOf(bold(0, 5))))
        assertEquals("**Hello** world", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 5))
    }

    @Test
    fun italic_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("Hello world", listOf(italic(6, 11))))
        assertEquals("Hello *world*", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Italic, styleOf(parsed, 6, 11))
    }

    @Test
    fun strikethrough_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("Hello world", listOf(strike(0, 11))))
        assertEquals("~~Hello world~~", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Strikethrough, styleOf(parsed, 0, 11))
    }

    @Test
    fun underline_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("Hello world", listOf(underline(0, 5))))
        assertEquals("<u>Hello</u> world", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Underline, styleOf(parsed, 0, 5))
    }

    // ---- multiple / adjacent styles -----------------------------------------

    @Test
    fun multiple_non_overlapping_spans_round_trip() {
        val text = "a b c d"
        val md = RichTextMarkdown.toMarkdown(
            AnnotatedString(
                text,
                listOf(bold(0, 1), italic(2, 3), strike(4, 5), underline(6, 7))
            )
        )
        assertEquals("**a** *b* ~~c~~ <u>d</u>", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("a b c d", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 1))
        assertEquals(RichStyle.Italic, styleOf(parsed, 2, 3))
        assertEquals(RichStyle.Strikethrough, styleOf(parsed, 4, 5))
        assertEquals(RichStyle.Underline, styleOf(parsed, 6, 7))
    }

    @Test
    fun adjacent_different_styles_round_trip() {
        // "Hello" bold (0..5), "world" italic (6..11)
        val md = RichTextMarkdown.toMarkdown(
            AnnotatedString("Hello world", listOf(bold(0, 5), italic(6, 11)))
        )
        assertEquals("**Hello** *world*", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 5))
        assertEquals(RichStyle.Italic, styleOf(parsed, 6, 11))
    }

    // ---- nested spans --------------------------------------------------------

    @Test
    fun nested_bold_containing_italic_round_trips() {
        // Bold over the whole "Hello world", italic over "lo wor" (inside the bold).
        val annotated = AnnotatedString("Hello world", listOf(bold(0, 11), italic(3, 9)))
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("**Hel*lo wor*ld**", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 11))
        assertEquals(RichStyle.Italic, styleOf(parsed, 3, 9))
    }

    @Test
    fun nested_three_levels_round_trips() {
        // Bold > italic > underline, fully nested.
        val annotated = AnnotatedString("abcdef", listOf(bold(0, 6), italic(1, 5), underline(2, 4)))
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("**a*b<u>cd</u>e*f**", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("abcdef", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 6))
        assertEquals(RichStyle.Italic, styleOf(parsed, 1, 5))
        assertEquals(RichStyle.Underline, styleOf(parsed, 2, 4))
    }

    // ---- same-style adjacent / overlapping spans (merge) --------------------

    @Test
    fun adjacent_same_style_spans_merge_and_round_trip() {
        // Two adjacent bold spans -> serialize as a single bold span.
        val md = RichTextMarkdown.toMarkdown(
            AnnotatedString("Hello world", listOf(bold(0, 5), bold(5, 11)))
        )
        assertEquals("**Hello world**", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 11))
    }

    @Test
    fun overlapping_same_style_spans_merge_and_round_trip() {
        val md = RichTextMarkdown.toMarkdown(
            AnnotatedString("Hello world", listOf(bold(0, 6), bold(5, 11)))
        )
        assertEquals("**Hello world**", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("Hello world", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 11))
    }

    // ---- literal marker-like text escaping ----------------------------------

    @Test
    fun literal_double_asterisk_is_escaped_and_round_trips() {
        val annotated = AnnotatedString("a ** b")
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("""a \*\* b""", md)
        // A literal `**` must not be misread as bold on parse.
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("a ** b", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
        assertEquals(annotated, parsed)
    }

    @Test
    fun literal_single_asterisk_is_escaped_and_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("a * b"))
        assertEquals("""a \* b""", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("a * b", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun literal_tildes_and_angle_brackets_are_escaped_and_round_trip() {
        val annotated = AnnotatedString("x ~~ y <u> z </u>")
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("""x \~\~ y \<u\> z \<\/u\>""", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("x ~~ y <u> z </u>", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
        assertEquals(annotated, parsed)
    }

    @Test
    fun literal_backslash_is_escaped_and_round_trips() {
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("a\\b"))
        assertEquals("""a\\b""", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("a\\b", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun escaped_text_next_to_real_marker_round_trips() {
        // A real bold span followed by a literal `*`.
        val annotated = AnnotatedString("ab*c", listOf(bold(0, 2)))
        val md = RichTextMarkdown.toMarkdown(annotated)
        assertEquals("""**ab**\*c""", md)
        val parsed = RichTextMarkdown.fromMarkdown(md)
        assertEquals("ab*c", parsed.text)
        assertEquals(RichStyle.Bold, styleOf(parsed, 0, 2))
    }

    // ---- malformed markers are literal --------------------------------------

    @Test
    fun unmatched_open_marker_is_literal() {
        val parsed = RichTextMarkdown.fromMarkdown("**bold but no close")
        assertEquals("**bold but no close", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun lone_double_tilde_is_literal() {
        val parsed = RichTextMarkdown.fromMarkdown("a ~~ b")
        assertEquals("a ~~ b", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun unclosed_underline_is_literal() {
        val parsed = RichTextMarkdown.fromMarkdown("<u>never closed")
        assertEquals("<u>never closed", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun empty_marker_pair_is_literal() {
        val parsed = RichTextMarkdown.fromMarkdown("****")
        assertEquals("****", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    @Test
    fun unbalanced_single_asterisk_is_literal() {
        val parsed = RichTextMarkdown.fromMarkdown("a * b")
        assertEquals("a * b", parsed.text)
        assertTrue(spansOf(parsed).isEmpty())
    }

    // ---- parse -> toMarkdown idempotence ------------------------------------

    @Test
    fun parse_then_toMarkdown_is_idempotent() {
        val original = "**Hello** *world* ~~strike~~ <u>under</u>"
        val once = RichTextMarkdown.fromMarkdown(original)
        val md1 = RichTextMarkdown.toMarkdown(once)
        val md2 = RichTextMarkdown.toMarkdown(RichTextMarkdown.fromMarkdown(md1))
        assertEquals(md1, md2)
    }

    // ---- toggleStyle --------------------------------------------------------

    @Test
    fun toggle_on_adds_style() {
        val annotated = AnnotatedString("Hello world")
        val toggled = toggleStyle(annotated, RichStyle.Bold, 0, 5)
        assertEquals("Hello world", toggled.text)
        assertEquals(RichStyle.Bold, styleOf(toggled, 0, 5))
    }

    @Test
    fun toggle_off_removes_style() {
        val annotated = AnnotatedString("Hello world", listOf(bold(0, 5)))
        val toggled = toggleStyle(annotated, RichStyle.Bold, 0, 5)
        assertEquals("Hello world", toggled.text)
        assertTrue(spansOf(toggled).isEmpty())
    }

    @Test
    fun toggle_off_partial_preserves_outside_parts() {
        // Bold over 0..11, toggle off the middle 3..8 -> bold remains on 0..3 and 8..11.
        val annotated = AnnotatedString("Hello world", listOf(bold(0, 11)))
        val toggled = toggleStyle(annotated, RichStyle.Bold, 3, 8)
        assertEquals("Hello world", toggled.text)
        val spans = spansOf(toggled)
        assertEquals(2, spans.size)
        assertTrue(spans.any { it.start == 0 && it.end == 3 && it.style == RichStyle.Bold })
        assertTrue(spans.any { it.start == 8 && it.end == 11 && it.style == RichStyle.Bold })
    }

    @Test
    fun toggle_on_extends_and_merges() {
        // Bold over 0..5, toggle bold on 4..11 -> single bold span 0..11.
        val annotated = AnnotatedString("Hello world", listOf(bold(0, 5)))
        val toggled = toggleStyle(annotated, RichStyle.Bold, 4, 11)
        assertEquals("Hello world", toggled.text)
        assertEquals(RichStyle.Bold, styleOf(toggled, 0, 11))
    }

    @Test
    fun toggle_preserves_other_styles() {
        val annotated = AnnotatedString("Hello world", listOf(bold(0, 5)))
        val toggled = toggleStyle(annotated, RichStyle.Italic, 6, 11)
        assertEquals("Hello world", toggled.text)
        assertEquals(RichStyle.Bold, styleOf(toggled, 0, 5))
        assertEquals(RichStyle.Italic, styleOf(toggled, 6, 11))
    }

    @Test
    fun toggle_on_empty_range_is_noop() {
        val annotated = AnnotatedString("Hello world")
        val toggled = toggleStyle(annotated, RichStyle.Bold, 3, 3)
        assertTrue(spansOf(toggled).isEmpty())
    }

    @Test
    fun toMarkdown_ignores_unrecognized_spans() {
        // A color-only span is not one of the four styles and is dropped from the output.
        val colorSpan = AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 5)
        val md = RichTextMarkdown.toMarkdown(AnnotatedString("Hello world", listOf(colorSpan)))
        assertEquals("Hello world", md)
        assertFalse(md.contains("**"))
    }

    // ---- plainText (marker stripping for display) ----------------------------

    @Test
    fun plainText_strips_all_style_markers() {
        // Bold / italic / strikethrough / underline markers are removed, leaving the text.
        val md = "**Bold** *italic* ~~strike~~ <u>under</u>"
        assertEquals("Bold italic strike under", plainText(md))
    }

    @Test
    fun plainText_strips_escaped_marker_chars() {
        // Escaped marker-like characters collapse to their literal form.
        val md = "a \\* b \\~ c \\< d \\> e \\/ f \\\\ g"
        assertEquals("a * b ~ c < d > e / f \\ g", plainText(md))
    }

    @Test
    fun plainText_matches_fromMarkdown_text() {
        // plainText is exactly the text that fromMarkdown would produce.
        val md = "**Hello** world with a \\* literal star"
        assertEquals(RichTextMarkdown.fromMarkdown(md).text, plainText(md))
    }

    @Test
    fun plainText_is_identity_for_plain_text() {
        assertEquals("just plain text", plainText("just plain text"))
        assertEquals("", plainText(""))
    }
}
