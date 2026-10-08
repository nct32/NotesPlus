package com.nct32.notesplus.ui.rich

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Lightweight Markdown serialization for the four inline styles the note editor supports:
 *
 * - **Bold**         -> `**text**`
 * - *Italic*         -> `*text*`
 * - Strikethrough    -> `~~text~~`
 * - Underline        -> `<u>text</u>`
 *
 * The note body is stored in Room as a plain [String]. To persist inline formatting without
 * changing the schema, [RichTextMarkdown.toMarkdown] serializes the [AnnotatedString] spans into
 * these markers and [RichTextMarkdown.fromMarkdown] turns them back into spans.
 *
 * ## Nesting
 *
 * The supported case is non-overlapping spans (what the editor's toggle logic produces). Spans of
 * *different* styles that are adjacent are serialized side by side, and a span that is fully
 * contained in another is serialized with nested markers (outer first), e.g. bold containing
 * italic becomes `**Hel*lo w*orld**`. Such well-formed input round-trips exactly.
 *
 * ## Escaping scheme
 *
 * Literal marker-like characters in the plain text are escaped with a backslash so that
 * [RichTextMarkdown.fromMarkdown] cannot mistake them for real markers and the round-trip is safe.
 * The following characters are escaped wherever they appear in the plain text:
 *
 * | character | escaped as |
 * |-----------|------------|
 * | `\`       | `\\`       |
 * | `*`       | `\*`       |
 * | `~`       | `\~`       |
 * | `<`       | `\<`       |
 * | `>`       | `\>`       |
 * | `/`       | `\/`       |
 *
 * [RichTextMarkdown.fromMarkdown] reverses this: a backslash immediately followed by one of the
 * six characters above yields that literal character. A backslash followed by any other character
 * is kept as a literal backslash (so hand-written text is never silently mangled). The markers
 * emitted by [RichTextMarkdown.toMarkdown] are never escaped, so they are recognized on parse.
 *
 * ## Robustness
 *
 * Malformed / unbalanced markers (an opening marker with no matching close, a stray close marker,
 * or an empty marker pair) are treated as literal text, so parsing never throws and never drops
 * user content.
 */

/** The four inline styles the editor can apply, with their [SpanStyle] and Markdown markers. */
enum class RichStyle(
    val style: SpanStyle,
    val openMarker: String,
    val closeMarker: String,
) {
    Bold(
        style = SpanStyle(fontWeight = FontWeight.Bold),
        openMarker = "**",
        closeMarker = "**",
    ),
    Italic(
        style = SpanStyle(fontStyle = FontStyle.Italic),
        openMarker = "*",
        closeMarker = "*",
    ),
    Strikethrough(
        style = SpanStyle(textDecoration = TextDecoration.LineThrough),
        openMarker = "~~",
        closeMarker = "~~",
    ),
    Underline(
        style = SpanStyle(textDecoration = TextDecoration.Underline),
        openMarker = "<u>",
        closeMarker = "</u>",
    ),
}

/** A single styled range, as produced by [spansOf] or [RichTextMarkdown.fromMarkdown]. */
data class RichSpan(
    val start: Int,
    val end: Int,
    val style: RichStyle,
)

/**
 * Returns the [RichStyle] this [spanStyle] represents, or `null` if it does not set any of the
 * four recognized style properties (e.g. a color-only span).
 */
private fun styleOf(spanStyle: SpanStyle): RichStyle? =
    when {
        spanStyle.fontWeight == FontWeight.Bold -> RichStyle.Bold
        spanStyle.fontStyle == FontStyle.Italic -> RichStyle.Italic
        spanStyle.textDecoration == TextDecoration.LineThrough -> RichStyle.Strikethrough
        spanStyle.textDecoration == TextDecoration.Underline -> RichStyle.Underline
        else -> null
    }

/**
 * Extracts the recognized [RichStyle] spans from [annotated], as a list of [RichSpan].
 *
 * Spans are returned sorted by [RichSpan.start] (ties broken by longer span first) so callers can
 * reason about them in document order.
 */
fun spansOf(annotated: AnnotatedString): List<RichSpan> {
    val text = annotated.text
    val spans = mutableListOf<RichSpan>()
    annotated.spanStyles.forEach { (item, start, end) ->
        val style = styleOf(item) ?: return@forEach
        val coercedStart = start.coerceIn(0, text.length)
        val coercedEnd = end.coerceIn(coercedStart, text.length)
        if (coercedStart < coercedEnd) {
            spans.add(RichSpan(coercedStart, coercedEnd, style))
        }
    }
    return spans.sortedWith(compareBy({ it.start }, { -it.end }))
}

/**
 * Merges [spans] that are of the *same* [RichStyle] and overlap or are adjacent, so the result
 * contains no overlapping same-style spans. Spans of different styles are left untouched (the
 * caller is responsible for not producing overlapping different-style spans).
 *
 * The result is sorted by [RichSpan.start] (ties broken by longer span first).
 */
fun mergeSameStyleSpans(spans: List<RichSpan>): List<RichSpan> {
    // Group by style, merge within each group, then re-sort by start.
    val mergedByStyle = spans
        .groupBy { it.style }
        .mapValues { (_, group) ->
            val sorted = group.sortedWith(compareBy({ it.start }, { -it.end }))
            val merged = mutableListOf<RichSpan>()
            for (span in sorted) {
                val last = merged.lastOrNull()
                if ((last != null) && (span.start <= last.end)) {
                    // Overlaps or is adjacent to the previous span of the same style: extend it.
                    merged[merged.lastIndex] = last.copy(end = maxOf(last.end, span.end))
                } else {
                    merged.add(span)
                }
            }
            merged
        }
    return mergedByStyle.values.flatten().sortedWith(compareBy({ it.start }, { -it.end }))
}

/**
 * Pure-Kotlin conversion between an [AnnotatedString] (with [SpanStyle] spans) and plain text with
 * lightweight Markdown markers, used to persist rich text in a plain-String database column.
 *
 * See the file-level KDoc for the marker set, the nesting rules, the escaping scheme, and the
 * malformed-marker behavior.
 */
object RichTextMarkdown {

    /** Characters escaped in the serialized output (see the file-level KDoc for the scheme). */
    private val ESCAPABLE: Set<Char> = setOf('\\', '*', '~', '<', '>', '/')

    /**
     * Serializes [annotated] into a plain [String] with Markdown markers for each recognized span.
     *
     * Non-overlapping spans are wrapped individually; adjacent different-style spans are placed
     * side by side; a span fully contained in another is nested (outer marker first). Literal
     * marker-like characters in the text are escaped so the result round-trips through
     * [fromMarkdown].
     */
    fun toMarkdown(annotated: AnnotatedString): String {
        val text = annotated.text
        if (text.isEmpty()) return ""
        val merged = mergeSameStyleSpans(spansOf(annotated))
        val forest = buildTree(merged, text.length)
        return serializeRange(text, 0, text.length, forest)
    }

    /**
     * Parses a marked-up [text] (as produced by [toMarkdown], or hand-written) into an
     * [AnnotatedString] with a [SpanStyle] span for each well-formed marker.
     *
     * This is the exact inverse of [toMarkdown] for well-formed input. Malformed / unbalanced
     * markers are treated as literal text, so this never throws and never drops user content.
     */
    fun fromMarkdown(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val (segments, _) = parseSegments(text, 0, emptyList())
        val (plain, spans) = flatten(segments)
        val builder = AnnotatedString.Builder(plain)
        for (span in spans) {
            builder.addStyle(span.style.style, span.start, span.end)
        }
        return builder.toAnnotatedString()
    }

    // ---- serialization -------------------------------------------------------

    /**
     * Builds a containment forest from [spans] (already sorted by start asc / end desc). A span
     * whose range lies inside another becomes its child; otherwise it is a sibling. Spans are
     * clamped to their parent so the tree stays well-formed (a defensive no-op for the supported
     * non-overlapping / nested cases).
     */
    private fun buildTree(spans: List<RichSpan>, textLength: Int): List<TreeSpan> {
        val root = TreeSpan(RichSpan(0, textLength, RichStyle.Bold))
        val stack = mutableListOf(root)
        for (span in spans) {
            // Close any spans that end before this one starts.
            while ((stack.size > 1) && (stack.last().span.end <= span.start)) {
                stack.removeAt(stack.lastIndex)
            }
            val parent = stack.last()
            val clampedStart = maxOf(span.start, parent.span.start)
            val clampedEnd = minOf(span.end, parent.span.end)
            if (clampedStart < clampedEnd) {
                val node = TreeSpan(RichSpan(clampedStart, clampedEnd, span.style))
                parent.children.add(node)
                stack.add(node)
            }
        }
        return root.children
    }

    /**
     * Emits [text][start..end) with each of [children] (all within [start..end), in document
     * order, non-overlapping) rendered as `openMarker + content + closeMarker`. Gaps between
     * children are escaped plain text.
     */
    private fun serializeRange(
        text: String,
        start: Int,
        end: Int,
        children: List<TreeSpan>,
    ): String {
        val sb = StringBuilder()
        var cursor = start
        for (child in children) {
            val childStart = child.span.start
            val childEnd = child.span.end
            if (childStart > cursor) {
                sb.append(escape(text, cursor, childStart))
            }
            sb.append(child.span.style.openMarker)
            sb.append(serializeRange(text, childStart, childEnd, child.children))
            sb.append(child.span.style.closeMarker)
            cursor = childEnd
        }
        if (cursor < end) {
            sb.append(escape(text, cursor, end))
        }
        return sb.toString()
    }

    /** Escapes every escapable character in [text][from..to) with a leading backslash. */
    private fun escape(text: String, from: Int, to: Int): String {
        val sb = StringBuilder()
        for (i in from until to) {
            val c = text[i]
            if (c in ESCAPABLE) sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    // ---- parsing -------------------------------------------------------------

    /**
     * Parses [text] starting at [i] with [openStack] as the currently-open styles (innermost
     * last). Returns the parsed [Segment]s and the index where parsing stopped (either at the
     * close marker of the innermost open style, or at the end of the text).
     */
    private fun parseSegments(
        text: String,
        i: Int,
        openStack: List<RichStyle>,
    ): Pair<List<Segment>, Int> {
        val segments = mutableListOf<Segment>()
        val textBuf = StringBuilder()
        var j = i
        while (j < text.length) {
            // 1. Escape sequence: a backslash followed by an escapable char is that literal char.
            if (text[j] == '\\' && j + 1 < text.length) {
                val next = text[j + 1]
                val isEscapable = next in ESCAPABLE
                if (isEscapable) {
                    textBuf.append(next)
                    j += 2
                } else {
                    // A backslash not followed by an escapable char is a literal backslash.
                    textBuf.append('\\')
                    j += 1
                }
                continue
            }
            // 2. The close marker of the innermost open style ends the current level.
            val innermost = openStack.lastOrNull()
            if ((innermost != null) && text.startsWith(innermost.closeMarker, j)) {
                flushText(segments, textBuf)
                return segments to j
            }
            // 3. Opening marker (longest first so `**` wins over `*`).
            val openStyle = RichStyle.entries
                .sortedByDescending { it.openMarker.length }
                .firstOrNull { text.startsWith(it.openMarker, j) }
            if (openStyle != null) {
                val openEnd = j + openStyle.openMarker.length
                val (content, contentEnd) = parseSegments(text, openEnd, openStack + openStyle)
                val hasClose = (contentEnd < text.length) &&
                    text.startsWith(openStyle.closeMarker, contentEnd)
                val nonEmpty = contentEnd > openEnd
                if (hasClose && nonEmpty) {
                    flushText(segments, textBuf)
                    segments.add(StyledSegment(openStyle, content))
                    j = contentEnd + openStyle.closeMarker.length
                } else {
                    // Unbalanced (no matching close) or empty pair: keep the marker as literal.
                    textBuf.append(text, j, openEnd)
                    j = openEnd
                }
                continue
            }
            // 4. Ordinary character (also covers stray close markers such as a lone `</u>`).
            textBuf.append(text[j])
            j += 1
        }
        flushText(segments, textBuf)
        return segments to j
    }

    private fun flushText(segments: MutableList<Segment>, buf: StringBuilder) {
        if (buf.isNotEmpty()) {
            segments.add(TextSegment(buf.toString()))
            buf.clear()
        }
    }

    /** Flattens the [Segment] tree into the plain text and the list of styled ranges. */
    private fun flatten(segments: List<Segment>): Pair<String, List<RichSpan>> {
        val sb = StringBuilder()
        val spans = mutableListOf<RichSpan>()
        fun render(segs: List<Segment>) {
            for (seg in segs) {
                when (seg) {
                    is TextSegment -> sb.append(seg.value)
                    is StyledSegment -> {
                        val contentStart = sb.length
                        render(seg.content)
                        val contentEnd = sb.length
                        spans.add(RichSpan(contentStart, contentEnd, seg.style))
                    }
                }
            }
        }
        render(segments)
        return sb.toString() to spans
    }

    // ---- internal types ------------------------------------------------------

    private class TreeSpan(
        val span: RichSpan,
        val children: MutableList<TreeSpan> = mutableListOf(),
    )

    private sealed class Segment

    private data class TextSegment(val value: String) : Segment()

    private data class StyledSegment(val style: RichStyle, val content: List<Segment>) : Segment()
}

/**
 * Serializes [annotated] to Markdown. Top-level convenience wrapper around
 * [RichTextMarkdown.toMarkdown] kept for existing call sites.
 */
fun toMarkdown(annotated: AnnotatedString): String = RichTextMarkdown.toMarkdown(annotated)

/**
 * Parses Markdown into an [AnnotatedString]. Top-level convenience wrapper around
 * [RichTextMarkdown.fromMarkdown] kept for existing call sites.
 */
fun parseMarkdown(text: String): AnnotatedString = RichTextMarkdown.fromMarkdown(text)

/**
 * Returns the plain text of a marked-up [text] (as produced by [RichTextMarkdown.toMarkdown]),
 * with all formatting markers and escape backslashes removed. Useful for displaying a stored
 * body (e.g. in a note-list preview) without leaking raw `**` / `*` / `~~` / `<u>` markers or
 * backslash escapes to the user.
 *
 * This is exactly the text that [RichTextMarkdown.fromMarkdown] would produce, minus the spans.
 */
fun plainText(text: String): String = RichTextMarkdown.fromMarkdown(text).text

/**
 * Toggles [style] over the range [rangeStart]..[rangeEnd] of [annotated], returning a new
 * [AnnotatedString].
 *
 * - If the range is **already fully covered** by [style], the style is removed from the range
 *   (toggle off).
 * - Otherwise the style is added/extended over the range (toggle on). Any existing span of the
 *   same style that intersects the range is removed and replaced by a single span covering the
 *   whole range, so the result never contains overlapping same-style spans.
 *
 * The plain text is unchanged; only the spans are modified. All other (non-matching) spans are
 * preserved.
 */
fun toggleStyle(
    annotated: AnnotatedString,
    style: RichStyle,
    rangeStart: Int,
    rangeEnd: Int,
): AnnotatedString {
    val text = annotated.text
    if (text.isEmpty() || rangeStart >= rangeEnd) return annotated
    val start = rangeStart.coerceIn(0, text.length)
    val end = rangeEnd.coerceIn(start, text.length)
    if (start >= end) return annotated

    val spans = spansOf(annotated)
    // The style is "on" for the range if some same-style span already fully covers it.
    val isOn = spans.any { (it.style == style) && (it.start <= start) && (end <= it.end) }

    // Build the output span list. Unrelated styles are preserved as-is. Same-style spans keep
    // only the parts that lie strictly outside [start, end): when toggling on, the intersection is
    // then covered by the single span added below; when toggling off, the intersection is simply
    // left unstyled.
    val out = mutableListOf<RichSpan>()
    for (span in spans) {
        if (span.style != style) {
            out.add(span)
            continue
        }
        if (span.start < start) out.add(span.copy(end = start))
        if (end < span.end) out.add(RichSpan(end, span.end, style))
    }
    if (!isOn) {
        out.add(RichSpan(start, end, style))
    }

    // Merge adjacent/overlapping same-style spans so the result is clean (and the toolbar's
    // "is on" check stays accurate after extending a style over a partially-styled range).
    val merged = mergeSameStyleSpans(out)

    val builder = AnnotatedString.Builder(text)
    for (span in merged) {
        builder.addStyle(span.style.style, span.start, span.end)
    }
    return builder.toAnnotatedString()
}
