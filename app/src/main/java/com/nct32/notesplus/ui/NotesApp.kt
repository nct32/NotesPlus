package com.nct32.notesplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nct32.notesplus.ui.theme.NotesTheme

/**
 * Top-level app shell for Notes+.
 *
 * - [NavigationSuiteScaffold] provides the adaptive top-level navigation area (bar -> rail ->
 *   drawer based on window size class).
 * - [rememberNavBackStack] + [NavDisplay] provide state-driven Navigation 3 navigation.
 * - [ListDetailSceneStrategy] renders the notes list and the editor as ONE pane on compact
 *   windows and SIDE-BY-SIDE on expanded windows (tablets / foldables).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotesApp() {
    val backStack = rememberNavBackStack(NoteList)

    // Remove the default horizontal gap between the list and detail panes.
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = backStack.lastOrNull() !is Reminders && backStack.lastOrNull() !is Settings,
                onClick = {
                    // Switch to the Notes tab. Reset the back stack to a single Notes root so
                    // any open editor / previous tab state is discarded. No-op if already on Notes.
                    val top = backStack.lastOrNull()
                    if (top is Reminders || top is Settings) {
                        backStack.clear()
                        backStack.add(NoteList)
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                        contentDescription = null,
                    )
                },
                label = { Text("Notes") },
            )
            item(
                selected = backStack.lastOrNull() is Reminders,
                onClick = {
                    // Switch to the Reminders tab. Reset the back stack to a single Reminders
                    // root so any open editor / previous tab state is discarded. No-op if already
                    // on Reminders.
                    if (backStack.lastOrNull() !is Reminders) {
                        backStack.clear()
                        backStack.add(Reminders)
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                    )
                },
                label = { Text("Reminders") },
            )
            item(
                selected = backStack.lastOrNull() is Settings,
                onClick = {
                    // Switch to the Settings tab. Reset the back stack to a single Settings
                    // root so any open editor / previous tab state is discarded. No-op if already
                    // on Settings.
                    if (backStack.lastOrNull() !is Settings) {
                        backStack.clear()
                        backStack.add(Settings)
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                    )
                },
                label = { Text("Settings") },
            )
        }
    ) {
        NavDisplay(
            backStack = backStack,
            // Only pop when the top entry is an editor, so back on the list exits the app.
            onBack = {
                if (backStack.lastOrNull() is NoteEditor) {
                    backStack.removeLastOrNull()
                }
            },
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            sceneStrategies = listOf(listDetailStrategy),
            entryProvider = entryProvider {
                entry<NoteList>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            DetailPlaceholder()
                        }
                    )
                ) {
                    NoteListScreen(
                        onNoteClick = { backStack.add(NoteEditor(it.id)) },
                        onAddNote = { backStack.add(NoteEditor(NoteEditor.NEW)) }
                    )
                }
                entry<NoteEditor>(
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { key ->
                    NoteEditorScreen(
                        noteKey = key,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Reminders> {
                    RemindersScreen()
                }
                entry<Settings> {
                    SettingsScreen()
                }
            }
        )
    }
}

/**
 * Shown in the detail pane on expanded windows when no note is selected.
 */
@Composable
private fun DetailPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Select a note to view it",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "or tap + to create a new note",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun NotesAppPreview() {
    NotesTheme {
        NotesApp()
    }
}
