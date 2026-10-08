package com.nct32.notesplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nct32.notesplus.settings.ThemeMode
import com.nct32.notesplus.ui.theme.NotesTheme

/**
 * The Settings screen: a Material 3 Expressive page with a "Smart text tools" section that lets
 * the user turn the note editor's writing tools on/off.
 *
 * Each row is a clean list-item card (icon + title + description + [Switch]). Toggling a switch
 * updates the shared [com.nct32.notesplus.settings.AppSettings] store immediately, so the editor
 * reacts live (its toolbar actions enable/disable in real time) and the choice is persisted
 * across app restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val autocorrectEnabled by viewModel.autocorrectEnabled.collectAsStateWithLifecycle()
    val summarizeEnabled by viewModel.summarizeEnabled.collectAsStateWithLifecycle()
    val rewriteEnabled by viewModel.rewriteEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Smart text tools [BETA]",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )

            SettingSwitchRow(
                label = "Autocorrect",
                description = "Fix typos and grammar in notes with one tap.",
                icon = Icons.Rounded.Spellcheck,
                checked = autocorrectEnabled,
                onCheckedChange = viewModel::setAutocorrectEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingSwitchRow(
                label = "Summarize",
                description = "Condense long notes into a short summary.",
                icon = Icons.Rounded.Summarize,
                checked = summarizeEnabled,
                onCheckedChange = viewModel::setSummarizeEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingSwitchRow(
                label = "Rewrite",
                description = "Quick actions to improve tone, shorten, expand, or fix grammar.",
                icon = Icons.Rounded.AutoFixHigh,
                checked = rewriteEnabled,
                onCheckedChange = viewModel::setRewriteEnabled
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )
            ThemeModeSelector(
                selected = themeMode,
                onSelected = viewModel::setThemeMode
            )
        }
    }
}

/**
 * A Material 3 single-selection segmented button row for the app's appearance mode:
 * System / Light / Dark. Selecting a segment updates the shared settings store, so the whole
 * app switches color scheme live and the choice is persisted across restarts.
 */
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Choose how the app's colors follow the system or stay fixed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.System -> Icons.Rounded.BrightnessAuto
                                    ThemeMode.Light -> Icons.Rounded.LightMode
                                    ThemeMode.Dark -> Icons.Rounded.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(mode.name) }
                    )
                }
            }
        }
    }
}

/**
 * A single settings list-item row: a card with an icon, a title, a one-line description, and a
 * [Switch]. Consistent with the expressive card styling used across the app (20dp corner radius).
 */
@Composable
private fun SettingSwitchRow(
    label: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun SettingsScreenPreview() {
    NotesTheme {
        SettingsScreen()
    }
}
