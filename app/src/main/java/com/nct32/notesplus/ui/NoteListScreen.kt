package com.nct32.notesplus.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nct32.notesplus.data.Note
import com.nct32.notesplus.ui.rich.plainText
import com.nct32.notesplus.ui.dialogs.DeleteFolderDialog
import com.nct32.notesplus.ui.dialogs.DeleteNoteDialog
import com.nct32.notesplus.ui.dialogs.MoveToFolderDialog
import com.nct32.notesplus.ui.dialogs.NewFolderDialog
import com.nct32.notesplus.ui.theme.NotesTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The notes list screen: a Material 3 Expressive list of notes with instant search, folder
 * filtering, folder management (create / delete), an add-note action, and a layout switcher
 * (single-column [NoteListLayout.List], two-column [NoteListLayout.Grid], or a custom
 * [NoteListLayout.Custom] grid with 1..10 notes per row).
 *
 * [onNoteClick] is invoked when the user taps a note (pushes the editor). [onAddNote] is invoked
 * when the user taps the add action.
 *
 * Each note card has a menu (⋮) to move the note to a folder (or unfile it) or to delete it
 * (with a Material 3 confirmation dialog). Each folder chip has a small delete button, and a
 * "New folder" chip opens the create-folder dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onNoteClick: (Note) -> Unit,
    onAddNote: () -> Unit,
    viewModel: NotesViewModel = viewModel(),
) {
    val notes by viewModel.visibleNotes.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val welcomeDismissed by viewModel.welcomeDismissed.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val updateDialogVisible by viewModel.updateDialogVisible.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // One auto update check per app process: the first composition of the Notes tab triggers
    // it (the UpdateManager guards against re-checks within the process and honors the
    // auto-check setting).
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(context)
    }

    // Surface a failed download as a snackbar, then clear the error so a later failure
    // with the same message is shown again.
    LaunchedEffect(downloadError) {
        downloadError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearDownloadError()
        }
    }

    // Dialog state.
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var noteToMove by remember { mutableStateOf<Note?>(null) }

    // Local copy so the delegated property can be smart-cast.
    val currentLayout = layout
    val gridColumns = when (currentLayout) {
        NoteListLayout.List -> 1
        NoteListLayout.Grid -> 2
        is NoteListLayout.Custom -> currentLayout.columns
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes+") },
                actions = {
                    LayoutSelector(
                        layout = currentLayout,
                        onLayoutSelected = viewModel::setLayout,
                        onCustomSelected = viewModel::selectCustomLayout
                    )
                }
            )
        },
        floatingActionButton = {
            AddNoteAction(onClick = onAddNote)
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NotesSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (!welcomeDismissed) {
                WelcomeBanner(
                    onDismiss = viewModel::dismissWelcome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Dismissible update banner: shown while a newer release is available and the
            // user chose "Remind me later" (the dialog is hidden, the update stays offered
            // here for the rest of the session). Hidden while a download is in flight.
            availableUpdate?.let { update ->
                if (!updateDialogVisible && downloadProgress == null) {
                    UpdateBanner(
                        tag = update.release.tag_name,
                        onDownload = { viewModel.downloadUpdate(context) },
                        onDismiss = viewModel::dismissUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            FolderChipsRow(
                folders = folders,
                selectedFolder = selectedFolder,
                onFolderSelected = viewModel::selectFolder,
                onNewFolder = { showNewFolderDialog = true },
                onFolderDelete = { folderToDelete = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (currentLayout is NoteListLayout.Custom) {
                CustomColumnsRow(
                    columns = currentLayout.columns,
                    onColumnsChange = viewModel::setCustomColumns,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notes.isEmpty()) {
                EmptyNotesState(
                    hasFilter = (searchQuery.isNotBlank() || selectedFolder != null),
                    onAddNote = onAddNote,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (currentLayout == NoteListLayout.List) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteListItem(
                            note = note,
                            onClick = { onNoteClick(note) },
                            onMoveToFolder = { noteToMove = note },
                            onDelete = { noteToDelete = note }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteGridItem(
                            note = note,
                            onClick = { onNoteClick(note) },
                            onMoveToFolder = { noteToMove = note },
                            onDelete = { noteToDelete = note }
                        )
                    }
                }
            }
        }
    }

    // New folder dialog.
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name -> scope.launch { viewModel.createFolder(name) } }
        )
    }

    // Delete folder dialog.
    folderToDelete?.let { name ->
        DeleteFolderDialog(
            folderName = name,
            noteCount = viewModel.folderNoteCount(name),
            onDismiss = { folderToDelete = null },
            onConfirm = { viewModel.deleteFolder(name) }
        )
    }

    // Delete note dialog (from the list context menu).
    noteToDelete?.let { note ->
        DeleteNoteDialog(
            noteTitle = note.title,
            onDismiss = { noteToDelete = null },
            onConfirm = { viewModel.deleteNote(note.id) }
        )
    }

    // Move-to-folder dialog (from the list context menu).
    noteToMove?.let { note ->
        MoveToFolderDialog(
            noteTitle = note.title,
            folders = folders,
            currentFolder = note.folder,
            onDismiss = { noteToMove = null },
            onPick = { folder -> viewModel.moveNoteToFolder(note, folder) }
        )
    }

    // "Update available" dialog: shown while the UpdateManager's dialog flag is set (i.e.
    // a check found an unskipped newer release and the user hasn't dismissed or skipped it).
    if (updateDialogVisible) {
        availableUpdate?.let { update ->
            UpdateAvailableDialog(
                tag = update.tag,
                releaseName = update.release.name,
                onUpdateNow = { viewModel.downloadUpdate(context) },
                onRemindLater = viewModel::dismissUpdate,
                onSkipVersion = viewModel::skipVersion
            )
        }
    }

    // Cancellable download progress dialog (shown while an APK download is in flight).
    downloadProgress?.let { progress ->
        DownloadProgressDialog(
            progress = progress,
            onCancel = viewModel::cancelDownload
        )
    }
}

/**
 * First-launch welcome banner: a small, dismissible Material 3 banner shown above the folder
 * chips. It is shown only while [com.nct32.notesplus.settings.SettingsStore.welcomeDismissed]
 * is `false` (i.e. the first launch ever); dismissing it persists the choice so it never
 * shows again.
 */
@Composable
private fun WelcomeBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WavingHand,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome to Notes+",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Add a note to get started.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss welcome message"
                )
            }
        }
    }
}

/**
 * Dismissible update banner: a small Material 3 card above the notes list announcing that a
 * newer release is available, with a [Button] to download + install it and an X to dismiss
 * for the current session only.
 */
@Composable
private fun UpdateBanner(
    tag: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notes+ $tag is available",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Download and install the latest release.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onDownload) {
                Text("Download")
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss update notification"
                )
            }
        }
    }
}

/**
 * Material 3 "Update available" dialog: offers a newer release with three choices —
 * "Update now" (primary, starts the APK download + install flow), "Remind me later"
 * (dismisses the dialog; the [UpdateBanner] keeps offering the update), and "Skip this
 * version" (persists the release tag so this exact version is never offered again; a newer
 * release will still prompt). Tapping outside behaves the same as "Remind me later".
 */
@Composable
private fun UpdateAvailableDialog(
    tag: String,
    releaseName: String?,
    onUpdateNow: () -> Unit,
    onRemindLater: () -> Unit,
    onSkipVersion: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onRemindLater,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Update available") },
        text = {
            Column {
                Text(updateReadyText(tag, releaseName))
                releaseName?.let { name ->
                    if (name != tag) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdateNow) {
                Text("Update now")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRemindLater) {
                    Text("Remind me later")
                }
                TextButton(onClick = onSkipVersion) {
                    Text("Skip this version")
                }
            }
        }
    )
}

/**
 * The dialog's main line: "Notes+ <tag> is ready to install." — the release name is
 * included when it is present and distinct from the tag.
 */
private fun updateReadyText(tag: String, releaseName: String?): String =
    if (!releaseName.isNullOrBlank() && releaseName != tag) {
        "Notes+ $releaseName ($tag) is ready to install."
    } else {
        "Notes+ $tag is ready to install."
    }

/**
 * Cancellable Material 3 progress dialog shown while a release APK is downloading:
 * a determinate progress bar plus the current percentage.
 */
@Composable
private fun DownloadProgressDialog(
    progress: Float,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Downloading update") },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddNoteAction(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add note"
        )
    }
}

@Composable
private fun NotesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // The search field is intentionally NOT focusable on launch. The framework
    // auto-grants focus to the first focusable text node when a screen appears, which would
    // pop up the soft keyboard. By keeping the field non-focusable until the user actually
    // taps it, there is no focusable text node for the framework to auto-grant, so no
    // auto-focus and no keyboard on launch.
    //
    // IMPORTANT: the real gate is `focusProperties { canFocus = ... }` (the documented way to
    // make a composable unfocusable). `Modifier.focusable(false)` is a no-op in Compose — it
    // merely does not add a focus node and does NOT suppress the text field's own internal
    // focus target — so it alone would not prevent the auto-focus. We keep it for clarity but
    // rely on `canFocus` for the actual behavior.
    //
    // (Note: we deliberately do NOT reactively clearFocus() in response to focus changes —
    // that previously caused an infinite focus/clear loop that saturated the main thread and
    // crashed the app on launch.)
    var searchFocusable by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search notes") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    // Non-focusable until the user taps it. `focusProperties { canFocus }` is
                    // the documented gate: while `searchFocusable` is false the field's focus
                    // target is excluded from focus search/auto-grant, so nothing focuses it on
                    // launch. (The `focusable` modifier is a no-op here — see the note above.)
                    .focusable(searchFocusable)
                    .focusProperties { canFocus = searchFocusable }
                    .focusRequester(searchFocusRequester)
                    // A real tap makes the field focusable and requests focus, opening the
                    // keyboard. requestFocus() re-evaluates `canFocus` at call time, so it sees
                    // the just-set `searchFocusable = true`. This is the only place focus is
                    // granted, so there is nothing to loop on.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            awaitFirstDown()
                            searchFocusable = true
                            searchFocusRequester.requestFocus()
                        }
                    }
            )
        }
    }
}

@Composable
private fun FolderChipsRow(
    folders: List<String>,
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onFolderDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFolder == null,
            onClick = { onFolderSelected(null) },
            label = { Text("All") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        folders.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = selectedFolder == folder,
                    onClick = { onFolderSelected(folder) },
                    label = { Text(folder) }
                )
                // Small delete button to remove this folder (its notes become unfiled).
                IconButton(
                    onClick = { onFolderDelete(folder) },
                    modifier = Modifier
                        .offset(x = (-4).dp)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete folder $folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = onNewFolder,
            label = { Text("New folder") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun NoteListItem(
    note: Note,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Wrap the button and its menu in a Box so the DropdownMenu anchors to the
                // button (an orphan sibling outside the button's scope renders bottom-left).
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Note options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NoteOptionsMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onMoveToFolder = onMoveToFolder,
                        onDelete = onDelete
                    )
                }
            }
            Text(
                text = plainText(note.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!note.folder.isNullOrBlank()) {
                    FolderChip(note.folder)
                }
                Text(
                    text = relativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact note card used in the grid layouts ([NoteListLayout.Grid] and
 * [NoteListLayout.Custom]): title + short snippet + folder chip with smaller text and padding
 * so many columns fit comfortably.
 */
@Composable
private fun NoteGridItem(
    note: Note,
    onClick: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Wrap the button and its menu in a Box so the DropdownMenu anchors to the
                // button (an orphan sibling outside the button's scope renders bottom-left).
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Note options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    NoteOptionsMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onMoveToFolder = onMoveToFolder,
                        onDelete = onDelete
                    )
                }
            }
            Text(
                text = plainText(note.body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!note.folder.isNullOrBlank()) {
                    FolderChip(note.folder, compact = true)
                }
                Text(
                    text = relativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Shared options menu (⋮) for note cards: move to folder / delete. */
@Composable
private fun NoteOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMoveToFolder: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Move to folder") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onMoveToFolder()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

/**
 * Layout switcher in the top app bar: List / Grid / Custom rows. Selecting "Custom rows"
 * reveals the [CustomColumnsRow] where the user picks the number of notes per row.
 */
@Composable
private fun LayoutSelector(
    layout: NoteListLayout,
    onLayoutSelected: (NoteListLayout) -> Unit,
    onCustomSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = when (layout) {
                NoteListLayout.List -> Icons.AutoMirrored.Rounded.ViewList
                NoteListLayout.Grid -> Icons.Rounded.GridOn
                is NoteListLayout.Custom -> Icons.Rounded.Tune
            },
            contentDescription = "Change list layout"
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("List") },
            leadingIcon = {
                Icon(imageVector = Icons.AutoMirrored.Rounded.ViewList, contentDescription = null)
            },
            onClick = {
                expanded = false
                onLayoutSelected(NoteListLayout.List)
            }
        )
        DropdownMenuItem(
            text = { Text("Grid") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.GridOn, contentDescription = null)
            },
            onClick = {
                expanded = false
                onLayoutSelected(NoteListLayout.Grid)
            }
        )
        DropdownMenuItem(
            text = { Text("Custom rows") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Tune, contentDescription = null)
            },
            onClick = {
                expanded = false
                onCustomSelected()
            }
        )
    }
}

/**
 * Shown while [NoteListLayout.Custom] is active: a clearly labeled row of chips to pick how
 * many notes appear per row (1..10).
 */
@Composable
private fun CustomColumnsRow(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Notes per row: $columns",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        (1..10).forEach { n ->
            FilterChip(
                selected = n == columns,
                onClick = { onColumnsChange(n) },
                label = { Text(n.toString()) }
            )
        }
    }
}

@Composable
private fun FolderChip(folder: String, compact: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = folder,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 2.dp else 4.dp
            )
        )
    }
}

@Composable
private fun EmptyNotesState(
    hasFilter: Boolean,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (hasFilter) Icons.Rounded.Search else Icons.Rounded.EditNote,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilter) "No matching notes" else "No notes yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasFilter) "Try a different search or folder."
            else "Tap + to create your first note.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasFilter) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddNote) {
                Text("New note")
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteListScreenPreview() {
    NotesTheme {
        NoteListScreen(
            onNoteClick = {},
            onAddNote = {}
        )
    }
}

/**
 * Preview of the "Update available" dialog in the light color scheme, rendered with a
 * representative tag and release name.
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun UpdateAvailableDialogPreview() {
    NotesTheme {
        UpdateAvailableDialog(
            tag = "alpha-0.0.3",
            releaseName = "Version alpha-0.0.3",
            onUpdateNow = {},
            onRemindLater = {},
            onSkipVersion = {}
        )
    }
}

/**
 * Sample notes used by the card previews below. [NOW] is captured once so the relative-time
 * labels are stable within a preview session.
 */
private val NOW: Long = System.currentTimeMillis()

private val PREVIEW_NOTES = listOf(
    Note(
        id = "preview-1",
        title = "Meeting notes",
        body = "Discuss the Q3 roadmap, assign owners for each milestone, and schedule a follow-up.",
        folder = "Work",
        createdAt = NOW - 3L * 24 * 60 * 60 * 1000,
        updatedAt = NOW - 5 * 60 * 1000
    ),
    Note(
        id = "preview-2",
        title = "Grocery list",
        body = "Oat milk, eggs, spinach, coffee beans, and dark chocolate.",
        folder = null,
        createdAt = NOW - 24 * 60 * 60 * 1000,
        updatedAt = NOW - 2 * 60 * 60 * 1000
    ),
    Note(
        id = "preview-3",
        title = "Book ideas",
        body = "The Pragmatic Programmer, Deep Work, and a re-read of The Design of Everyday Things.",
        folder = "Personal",
        createdAt = NOW - 6L * 24 * 60 * 60 * 1000,
        updatedAt = NOW - 3L * 24 * 60 * 60 * 1000
    )
)

/**
 * Preview of the list-mode note card ([NoteListItem]) in the light color scheme, showing the
 * `surfaceContainerHigh` card background, 1dp `outlineVariant` border, and soft shadow against
 * the page background.
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteListItemPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PREVIEW_NOTES.forEach { note ->
                    NoteListItem(
                        note = note,
                        onClick = {},
                        onMoveToFolder = {},
                        onDelete = {}
                    )
                }
            }
        }
    }
}

/**
 * Preview of the list-mode note card ([NoteListItem]) in the dark color scheme, so card
 * contrast can be checked in both modes.
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteListItemPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PREVIEW_NOTES.forEach { note ->
                    NoteListItem(
                        note = note,
                        onClick = {},
                        onMoveToFolder = {},
                        onDelete = {}
                    )
                }
            }
        }
    }
}

/**
 * Preview of the grid-mode note card ([NoteGridItem]) in the light color scheme.
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteGridItemPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PREVIEW_NOTES.take(2).forEach { note ->
                    NoteGridItem(
                        note = note,
                        onClick = {},
                        onMoveToFolder = {},
                        onDelete = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Preview of the grid-mode note card ([NoteGridItem]) in the dark color scheme.
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NoteGridItemPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PREVIEW_NOTES.take(2).forEach { note ->
                    NoteGridItem(
                        note = note,
                        onClick = {},
                        onMoveToFolder = {},
                        onDelete = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
