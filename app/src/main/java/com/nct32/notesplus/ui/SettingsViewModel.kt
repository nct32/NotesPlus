package com.nct32.notesplus.ui

import androidx.lifecycle.ViewModel
import com.nct32.notesplus.settings.AppSettings
import com.nct32.notesplus.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the Settings screen, exposing the app's writing-tool toggles as
 * [StateFlow]s for unidirectional data flow.
 *
 * The values are backed by the process-wide [AppSettings] singleton (SharedPreferences-backed),
 * so toggling a switch here is immediately visible in the note editor (which observes the same
 * flows) and is persisted across app restarts.
 */
class SettingsViewModel : ViewModel() {

    /** Whether the autocorrect tool is enabled (persisted). */
    val autocorrectEnabled: StateFlow<Boolean> = AppSettings.instance.autocorrectEnabled

    /** Whether the summarize tool is enabled (persisted). */
    val summarizeEnabled: StateFlow<Boolean> = AppSettings.instance.summarizeEnabled

    /** Whether the rewrite quick actions are enabled (persisted). */
    val rewriteEnabled: StateFlow<Boolean> = AppSettings.instance.rewriteEnabled

    /** The app's appearance mode (persisted). */
    val themeMode: StateFlow<ThemeMode> = AppSettings.instance.themeMode

    /** Enables/disables the autocorrect tool (persisted). */
    fun setAutocorrectEnabled(enabled: Boolean) {
        AppSettings.instance.setAutocorrectEnabled(enabled)
    }

    /** Enables/disables the summarize tool (persisted). */
    fun setSummarizeEnabled(enabled: Boolean) {
        AppSettings.instance.setSummarizeEnabled(enabled)
    }

    /** Enables/disables the rewrite quick actions (persisted). */
    fun setRewriteEnabled(enabled: Boolean) {
        AppSettings.instance.setRewriteEnabled(enabled)
    }

    /** Sets the app's appearance mode (persisted). */
    fun setThemeMode(mode: ThemeMode) {
        AppSettings.instance.setThemeMode(mode)
    }
}
