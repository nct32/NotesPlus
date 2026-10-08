package com.nct32.notesplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Expand
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nct32.notesplus.text.AutocorrectEngine
import com.nct32.notesplus.text.Fix
import com.nct32.notesplus.text.RewriteEngine
import com.nct32.notesplus.text.Summarizer
import com.nct32.notesplus.ui.dialogs.DeleteNoteDialog
import com.nct32.notesplus.ui.rich.RichStyle
import com.nct32.notesplus.ui.rich.parseMarkdown
import com.nct32.notesplus.ui.rich.spansOf
import com.nct32.notesplus.ui.rich.toMarkdown
import com.nct32.notesplus.ui.rich.toggleStyle
import com.nct32.notesplus.ui.theme.NotesTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The full note editor screen.
 *
 * ## Save model
 *
 * The primary save model is **explicit**: the check action (or the back arrow) persists the note.
 * Tapping the check saves the note and then returns to the notes list home page. The back arrow
 * saves any unsaved changes before navigating away, so "back" never silently discards work. A
 * safety net in the ViewModel also persists dirty state on clear.
 *
 * ## Text fields
 *
 * The **title** field is driven by [TextFieldState] (the modern state-based API), which owns the
 * text, selection, composing region, and IME input connection internally — the most robust path
 * for the on-screen keyboard.
 *
 * The **body** field is driven by a single, atomic [androidx.compose.ui.text.input.TextFieldValue]
 * (hoisted via `remember { mutableStateOf(TextFieldValue()) }`) so its
 * [androidx.compose.ui.text.AnnotatedString] can carry inline [androidx.compose.ui.text.SpanStyle]
 * spans for rich formatting (bold / italic / strikethrough / underline). The whole value is updated
 * in one atomic write in `onValueChange` (the incoming value is passed through as-is) — it is never
 * rebuilt from separate text + selection states, which is exactly what desynced the IME
 * InputConnection and froze the keyboard before.
 *
 * The [NoteEditorViewModel] remains the source of truth for **saving**: a [snapshotFlow] over the
 * body's marked-up text ([com.nct32.notesplus.ui.rich.toMarkdown]) pushes every edit into the
 * ViewModel, so save / tick-save / save-on-back / the [androidx.lifecycle.ViewModel.onCleared]
 * safety net all keep working. Inline formatting is persisted as lightweight Markdown markers
 * (see [com.nct32.notesplus.ui.rich.toMarkdown] / [com.nct32.notesplus.ui.rich.parseMarkdown]).
 *
 * ## Focus
 *
 * For a brand-new note ([NoteEditor.NEW]), the **title** field is auto-focused so the user can
 * start typing immediately. For an existing note, nothing is auto-focused (the keyboard stays
 * hidden until the user taps a field).
 *
 * [onBack] is invoked when the user taps the back arrow (compact window). On expanded windows the
 * editor is shown side-by-side with the list, so the back arrow simply returns to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteKey: NoteEditor,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory(noteKey)),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val autocorrectEnabled by viewModel.autocorrectEnabled.collectAsStateWithLifecycle()
    val summarizeEnabled by viewModel.summarizeEnabled.collectAsStateWithLifecycle()
    val rewriteEnabled by viewModel.rewriteEnabled.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }

    // Writing tools state.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAutocorrectDialog by remember { mutableStateOf(false) }
    var showRewriteDialog by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }

    // The title field is driven by a [TextFieldState], which manages the text, selection,
    // composing region, and the IME InputConnection internally — the most robust path for the
    // on-screen keyboard (and CJK / non-English composition).
    val titleState = rememberTextFieldState()

    // The body field is driven by a single, atomic [TextFieldValue] (hoisted via
    // `remember { mutableStateOf(TextFieldValue()) }`) so its [AnnotatedString] can carry inline
    // [androidx.compose.ui.text.SpanStyle] spans for rich formatting (bold / italic / strikethrough /
    // underline). The whole value — text, selection, and composing region — is updated in one atomic
    // write in `onValueChange` (the incoming value is passed through as-is), so the IME
    // InputConnection is never desynced by a recomposition that rebuilds a value from separate
    // text + selection states (that two-source rebuild is exactly what caused the frozen keyboard
    // before). Each note entry is its own composition, and the seed effect below re-seeds the
    // content when the note changes, so switching notes always shows the correct content.
    //
    // The parent keeps a reference to [bodyValue] so the rewrite / autocorrect actions can read the
    // current selection and apply programmatic edits to the field.
    val bodyValue = remember { mutableStateOf(TextFieldValue()) }

    // Set to true once both fields have been seeded with the note's content. The edit-sync
    // snapshotFlows below only start after this, so the field's initial (empty) text is never
    // pushed over the note's loaded content (avoids a race with the async load).
    var fieldsSeeded by remember { mutableStateOf(false) }

    // Seed both fields with the note's loaded content once the (async) load completes. For a new
    // note there is nothing to load (isLoading is false from the start) and the fields stay empty.
    // Idempotent: the ViewModel's onTitleChange/onBodyChange only mark the note dirty when the
    // value actually changes, so re-seeding the same text is a no-op.
    //
    // The body is seeded by parsing the stored (marked-up) string back into an [AnnotatedString]
    // with its inline spans, then writing the whole [TextFieldValue] in a single atomic update.
    LaunchedEffect(noteKey.noteId, isLoading) {
        if (!isLoading) {
            titleState.setTextAndPlaceCursorAtEnd(viewModel.title.value)
            val annotated = parseMarkdown(viewModel.body.value)
            val length = annotated.text.length
            bodyValue.value = TextFieldValue(
                annotatedString = annotated,
                selection = TextRange(length),
                composition = TextRange(0),
            )
            fieldsSeeded = true
        }
    }

    // Push title edits into the ViewModel (the source of truth for saving). Only starts after the
    // fields are seeded, so the initial empty text is never pushed over the loaded content.
    LaunchedEffect(viewModel, fieldsSeeded) {
        if (!fieldsSeeded) return@LaunchedEffect
        snapshotFlow { titleState.text.toString() }
            .distinctUntilChanged()
            .collect { viewModel.onTitleChange(it) }
    }

    // Whether the body is blank, derived so the toolbar only recomposes when the blank state
    // flips (not on every keystroke). The full [body] string is read by the (rarely-shown) tool
    // dialogs — never in the toolbar.
    val bodyIsBlank by remember { derivedStateOf { body.isBlank() } }

    // The body as PLAIN text (Markdown formatting markers stripped). The writing tools
    // (Autocorrect / Summarize / Rewrite) operate on plain text, so they must read this —
    // never the marked-up [body] string, whose `**` / `*` / `~~` / `<u>` markers and backslash
    // escapes would corrupt their output and misalign the rewrite's selection indices.
    val bodyPlainText by remember {
        derivedStateOf { bodyValue.value.annotatedString.text }
    }

    // If a tool is switched off in Settings while the editor is open, hide its UI.
    LaunchedEffect(autocorrectEnabled) {
        if (!autocorrectEnabled) showAutocorrectDialog = false
    }
    LaunchedEffect(summarizeEnabled) {
        if (!summarizeEnabled) summary = null
    }
    LaunchedEffect(rewriteEnabled) {
        if (!rewriteEnabled) showRewriteDialog = false
    }

    val titleFocusRequester = remember { FocusRequester() }
    LaunchedEffect(noteKey.noteId) {
        if (noteKey.noteId == NoteEditor.NEW) {
            titleFocusRequester.requestFocus()
        }
    }

    // Dismiss the IME/input UI (hide the soft keyboard and clear focus) before navigating away,
    // so leaving the editor never leaves the typing UI open.
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    fun dismissInputUi() {
        softwareKeyboardController?.hide()
        focusManager.clearFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isDirty -> if (isSaved) "Unsaved changes" else "New note"
                            isSaved -> "Edit note"
                            else -> "New note"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Persist unsaved changes, dismiss the input UI, then navigate away.
                        viewModel.saveIfDirty()
                        dismissInputUi()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAutocorrectDialog = true },
                        enabled = autocorrectEnabled && !bodyIsBlank,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Spellcheck,
                            contentDescription = "Autocorrect"
                        )
                    }
                    IconButton(
                        onClick = { summary = Summarizer.summarize(bodyPlainText) },
                        enabled = summarizeEnabled && !bodyIsBlank,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Summarize,
                            contentDescription = "Summarize"
                        )
                    }
                    IconButton(
                        onClick = { showRewriteDialog = true },
                        enabled = rewriteEnabled && !bodyIsBlank,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.TextSnippet,
                            contentDescription = "Rewrite"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = isSaved,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete note"
                        )
                    }
                    IconButton(onClick = {
                        if (viewModel.save()) {
                            // Saved: dismiss the input UI, then return to the notes list home page.
                            dismissInputUi()
                            onBack()
                        }
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save note"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // Expressive, borderless title field, driven by [TextFieldState].
            //
            // [TextFieldState] manages the text, selection, composing region, and the IME
            // InputConnection internally, so on-screen keystrokes (including non-English
            // composition) are never desynced by a recomposition. Edits are pushed to the
            // ViewModel by the snapshotFlow above; the field itself never rebuilds a value.
            BasicTextField(
                state = titleState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .padding(top = 8.dp, bottom = 4.dp),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                // BasicTextField's cursor defaults to LocalTextStyle.current.color, which is
                // Color.Unspecified here (the app Typography sets no color) and therefore falls
                // back to the platform default black — invisible in dark mode. Pin it to the
                // theme's onSurface so it always follows light/dark.
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                decorator = TextFieldDecorator { innerTextField ->
                    if (titleState.text.isEmpty()) {
                        Text(
                            text = "Title",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Inline formatting toolbar, shown only while the body has a non-collapsed selection.
            // Tapping a button toggles that style over the selected range.
            RichTextToolbar(
                bodyValue = bodyValue,
                modifier = Modifier
                    .fillMaxWidth()
            )

            // Large, expressive body field that fills the remaining space and scrolls.
            NoteBodyField(
                bodyValue = bodyValue,
                viewModel = viewModel,
                fieldsSeeded = fieldsSeeded,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Extractive summary, shown inline below the body when the user taps Summarize.
            summary?.let { text ->
                Spacer(modifier = Modifier.height(16.dp))
                SummaryCard(summary = text, onDismiss = { summary = null })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Folder assignment row + dropdown.
            FolderPickerRow(
                folder = folder,
                folders = folders,
                expanded = folderMenuExpanded,
                onToggle = { folderMenuExpanded = it },
                onFolderSelected = { viewModel.onFolderChange(it) }
            )
        }
    }

    if (showDeleteDialog) {
        DeleteNoteDialog(
            noteTitle = title,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.delete()
                // Popping to the list; nothing to save after deletion.
                onBack()
            }
        )
    }

    if (showAutocorrectDialog) {
        AutocorrectPreviewDialog(
            body = bodyPlainText,
            onDismiss = { showAutocorrectDialog = false },
            onApply = { fixes ->
                showAutocorrectDialog = false
                if (fixes.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No fixes needed — your note looks good!")
                    }
                } else {
                    val corrected = AutocorrectEngine.correct(bodyPlainText).correctedText
                    // Apply the corrected text to the field (and the ViewModel via the
                    // snapshotFlow). Autocorrect operates on plain text, so the corrected text is
                    // stored as a plain [AnnotatedString] — any inline formatting spans in the body
                    // are cleared (documented behavior). The cursor is placed at the end.
                    val correctedAnnotated = AnnotatedString(corrected)
                    bodyValue.value = TextFieldValue(
                        annotatedString = correctedAnnotated,
                        selection = TextRange(corrected.length),
                        composition = TextRange(0),
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (fixes.size == 1) "Fixed 1 issue" else "Fixed ${fixes.size} issues"
                        )
                    }
                }
            }
        )
    }

    if (showRewriteDialog) {
        RewriteMenuDialog(
            onDismiss = { showRewriteDialog = false },
            onApply = { action ->
                showRewriteDialog = false
                // Read the current selection from the field's state. A valid (non-collapsed,
                // in-bounds) selection is rewritten in place; otherwise the whole body is
                // rewritten.
                val current = bodyValue.value
                val selection = current.selection
                // Rewrite operates on PLAIN text (formatting markers stripped), so the
                // selection indices and the spliced result are both in plain-text coordinates.
                val plain = bodyPlainText
                val hasSelection = (selection.start != selection.end) &&
                    (selection.start >= 0) && (selection.end <= plain.length)
                val start = if (hasSelection) selection.start else 0
                val end = if (hasSelection) selection.end else plain.length
                val target = plain.substring(start, end)
                val result = action.rewrite(target)
                val newBody = plain.substring(0, start) + result.rewrittenText + plain.substring(end)
                // Apply to the field (and the ViewModel via the snapshotFlow). Rewrite operates on
                // plain text, so the new text is stored as a plain [AnnotatedString] — any inline
                // formatting spans in the replaced range (or the whole body, for a full rewrite)
                // are cleared (documented behavior). The cursor is placed at the end.
                val newAnnotated = AnnotatedString(newBody)
                bodyValue.value = TextFieldValue(
                    annotatedString = newAnnotated,
                    selection = TextRange(newBody.length),
                    composition = TextRange(0),
                )
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (result.hasChanges) {
                            if (result.changes.size == 1) "1 change applied" else "${result.changes.size} changes applied"
                        } else {
                            "No changes needed"
                        }
                    )
                }
            }
        )
    }
}

/**
 * The note body text field, driven by a single atomic [TextFieldValue] whose
 * [AnnotatedString] carries inline [androidx.compose.ui.text.SpanStyle] spans for rich
 * formatting (bold / italic / strikethrough / underline).
 *
 * ## Why this exists
 *
 * The field is isolated in its own composable so the per-keystroke read of the value (for the
 * placeholder and the edit-sync flow) is scoped to this single composable: typing recomposes only
 * the body field, not the whole screen (the toolbar, title field, summary card, and folder row
 * stay stable).
 *
 * ## Keyboard safety (the critical constraint)
 *
 * The entire [TextFieldValue] — text, selection, and composing region — lives in ONE
 * `remember { mutableStateOf(TextFieldValue()) }` and is updated **atomically** in
 * `onValueChange` (the incoming value is passed straight through). We never rebuild the value
 * from separate text + selection states; that two-source rebuild is exactly what desynced the IME
 * InputConnection and froze the on-screen keyboard before.
 *
 * Edits are pushed to the [NoteEditorViewModel] (the source of truth for saving) via a
 * [snapshotFlow] over the marked-up text ([toMarkdown]), so save / tick-save / save-on-back / the
 * onCleared safety net all keep working. The field scrolls internally, so no `.verticalScroll()`
 * modifier is needed.
 */
@Composable
private fun NoteBodyField(
    bodyValue: MutableState<TextFieldValue>,
    viewModel: NoteEditorViewModel,
    fieldsSeeded: Boolean,
    modifier: Modifier = Modifier
) {
    // Push body edits into the ViewModel (the source of truth for saving). The value is serialized
    // to marked-up Markdown (spans -> markers) so the formatting survives a save/reload. Only
    // starts after the fields are seeded, so the initial empty text is never pushed over the
    // loaded content.
    LaunchedEffect(viewModel, fieldsSeeded) {
        if (!fieldsSeeded) return@LaunchedEffect
        snapshotFlow { toMarkdown(bodyValue.value.annotatedString) }
            .distinctUntilChanged()
            .collect { viewModel.onBodyChange(it) }
    }

    BasicTextField(
        value = bodyValue.value,
        onValueChange = { newValue ->
            // Atomic, single-source update: pass the incoming value through as-is. Never rebuild
            // from separate text + selection states (that desyncs the IME).
            bodyValue.value = newValue
        },
        modifier = modifier,
        // Comfortable reading size: slightly smaller than bodyLarge (16sp) with the same
        // 24sp line height for comfortable line spacing.
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        // BasicTextField's cursor defaults to LocalTextStyle.current.color, which is
        // Color.Unspecified here (the app Typography sets no color) and therefore falls
        // back to the platform default black — invisible in dark mode. Pin it to the
        // theme's onSurface so it always follows light/dark.
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        // Multi-line body: keep the standard Return key (ImeAction.None) so the user can
        // add line breaks. A Done/Next ime action would replace Return and block them.
        singleLine = false,
        maxLines = Int.MAX_VALUE,
        minLines = 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.None
        ),
        decorationBox = { innerTextField ->
            if (bodyValue.value.text.isEmpty()) {
                Text(
                    text = "Start writing…",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            innerTextField()
        }
    )
}

/**
 * Inline formatting toolbar for the note body. Shown only while the body has a non-collapsed
 * selection; each button toggles the corresponding [RichStyle] over the selected range.
 *
 * The toolbar reads the selection through a [derivedStateOf] that only flips when the selection
 * goes collapsed <-> non-collapsed, so it does not recompose on every keystroke.
 */
@Composable
private fun RichTextToolbar(
    bodyValue: MutableState<TextFieldValue>,
    modifier: Modifier = Modifier
) {
    val hasSelection by remember {
        derivedStateOf {
            val s = bodyValue.value.selection
            (s.start != s.end) && (s.start >= 0)
        }
    }
    if (!hasSelection) return

    val current = bodyValue.value
    val selection = current.selection
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    // Recompute the recognized spans only when the text/spans or the selection range change.
    val spans = remember(current.annotatedString, start, end) { spansOf(current.annotatedString) }
    fun isOn(style: RichStyle) =
        spans.any { (it.style == style) && (it.start <= start) && (end <= it.end) }
    fun toggle(style: RichStyle) {
        val cur = bodyValue.value
        val s = cur.selection
        val newAnnotated = toggleStyle(
            cur.annotatedString,
            style,
            minOf(s.start, s.end),
            maxOf(s.start, s.end)
        )
        // Atomic update: keep the current selection and composing region.
        bodyValue.value = cur.copy(annotatedString = newAnnotated)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RichFormatButton(Icons.Rounded.FormatBold, "Bold", isOn(RichStyle.Bold)) {
            toggle(RichStyle.Bold)
        }
        RichFormatButton(Icons.Rounded.FormatItalic, "Italic", isOn(RichStyle.Italic)) {
            toggle(RichStyle.Italic)
        }
        RichFormatButton(Icons.Rounded.FormatStrikethrough, "Strikethrough", isOn(RichStyle.Strikethrough)) {
            toggle(RichStyle.Strikethrough)
        }
        RichFormatButton(Icons.Rounded.FormatUnderlined, "Underline", isOn(RichStyle.Underline)) {
            toggle(RichStyle.Underline)
        }
    }
}

/** A single formatting button in the [RichTextToolbar]. */
@Composable
private fun RichFormatButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * M3 card shown inline below the note body with the extractive summary produced by
 * [Summarizer]. Uses the secondary container color to visually distinguish it from the note.
 */
@Composable
private fun SummaryCard(
    summary: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Summarize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss summary",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Preview dialog for the autocorrect action: shows every fix that would be applied
 * (original -> replacement) and lets the user apply them all to the note body.
 */
@Composable
private fun AutocorrectPreviewDialog(
    body: String,
    onDismiss: () -> Unit,
    onApply: (List<Fix>) -> Unit
) {
    val result = remember(body) { AutocorrectEngine.correct(body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Spellcheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Autocorrect") },
        text = {
            if (result.fixes.isEmpty()) {
                Text(
                    text = "No fixes needed — your note looks good!",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    result.fixes.forEach { fix ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fix.original,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fix.replacement,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(result.fixes) },
                enabled = result.fixes.isNotEmpty()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * One rewrite action offered by the [RewriteMenuDialog].
 */
private sealed interface RewriteAction {
    data object ImproveTone : RewriteAction
    data object Shorten : RewriteAction
    data object Expand : RewriteAction
    data object FixGrammar : RewriteAction

    /** Runs the matching [RewriteEngine] pass over [text]. */
    fun rewrite(text: String) = when (this) {
        ImproveTone -> RewriteEngine.improveTone(text)
        Shorten -> RewriteEngine.shorten(text)
        Expand -> RewriteEngine.expand(text)
        FixGrammar -> RewriteEngine.fixGrammar(text)
    }
}

/**
 * M3 dialog listing the four rewrite quick actions (Improve tone, Shorten, Expand, Fix
 * grammar). Tapping an action invokes [onApply] with it; the caller decides the scope
 * (selection vs. whole body) and applies the result.
 */
@Composable
private fun RewriteMenuDialog(
    onDismiss: () -> Unit,
    onApply: (RewriteAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TextSnippet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Rewrite") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                RewriteActionRow(
                    label = "Improve tone",
                    description = "Swap casual wording for more formal synonyms.",
                    icon = Icons.Rounded.AutoFixHigh,
                    onClick = { onApply(RewriteAction.ImproveTone) }
                )
                RewriteActionRow(
                    label = "Shorten",
                    description = "Remove filler words and compress verbose phrases.",
                    icon = Icons.AutoMirrored.Rounded.ShortText,
                    onClick = { onApply(RewriteAction.Shorten) }
                )
                RewriteActionRow(
                    label = "Expand",
                    description = "Expand contractions and abbreviations to full forms.",
                    icon = Icons.Rounded.Expand,
                    onClick = { onApply(RewriteAction.Expand) }
                )
                RewriteActionRow(
                    label = "Fix grammar",
                    description = "Fix capitalization, a/an, and missing apostrophes.",
                    icon = Icons.Rounded.Check,
                    onClick = { onApply(RewriteAction.FixGrammar) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** A tappable row in the rewrite menu: icon + label + one-line description. */
@Composable
private fun RewriteActionRow(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A row showing the currently selected folder (or "No folder") with a dropdown menu to pick a
 * different folder.
 */
@Composable
private fun FolderPickerRow(
    folder: String?,
    folders: List<String>,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Wrap the row and its menu in a Box so the DropdownMenu anchors to the row
    // (an orphan sibling outside the row's scope renders bottom-left).
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onToggle(true) },
                    onLongClick = { onToggle(true) }
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folder ?: "No folder",
                style = MaterialTheme.typography.bodyLarge,
                color = if (folder == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Choose folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onToggle(false) }
        ) {
            DropdownMenuItem(
                text = { Text("No folder") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DriveFileRenameOutline,
                        contentDescription = null
                    )
                },
                onClick = {
                    onFolderSelected(null)
                    onToggle(false)
                }
            )
            folders.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onFolderSelected(f)
                        onToggle(false)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteEditorScreenPreview() {
    NotesTheme {
        NoteEditorScreen(
            noteKey = NoteEditor(NoteEditor.NEW),
            onBack = {}
        )
    }
}
