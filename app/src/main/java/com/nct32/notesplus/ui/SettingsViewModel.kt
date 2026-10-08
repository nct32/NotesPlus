package com.nct32.notesplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nct32.notesplus.settings.AppSettings
import com.nct32.notesplus.settings.ThemeMode
import com.nct32.notesplus.update.ReleaseChannel
import com.nct32.notesplus.update.UpdateCheckResult
import com.nct32.notesplus.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** The update channel the user has opted into (persisted). */
    val releaseChannel: StateFlow<ReleaseChannel> = AppSettings.instance.releaseChannel

    /** Whether the app checks for updates automatically on launch (persisted). */
    val autoCheckUpdates: StateFlow<Boolean> = AppSettings.instance.autoCheckUpdates

    /**
     * Whether a manual "Check for updates" is currently running (drives the small progress
     * indicator in the Updates section).
     */
    private val _checkingForUpdates = MutableStateFlow(false)
    val checkingForUpdates: StateFlow<Boolean> = _checkingForUpdates.asStateFlow()

    /**
     * A one-shot message to surface after a manual check: "You're up to date" or the error
     * text. `null` when nothing to show; the screen clears it after displaying.
     */
    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

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

    /** Sets the update channel (persisted). */
    fun setReleaseChannel(channel: ReleaseChannel) {
        AppSettings.instance.setReleaseChannel(channel)
    }

    /** Enables/disables the automatic update check on launch (persisted). */
    fun setAutoCheckUpdates(enabled: Boolean) {
        AppSettings.instance.setAutoCheckUpdates(enabled)
    }

    /** Clears a previously shown [updateCheckMessage] (call after displaying it). */
    fun clearUpdateCheckMessage() {
        _updateCheckMessage.value = null
    }

    /**
     * Manually checks for updates: always runs (bypasses the auto-check preference and the
     * once-per-process flag). While checking, [checkingForUpdates] is `true`. On completion:
     * an available update makes the app-wide update dialog appear (driven by
     * [UpdateManager.dialogVisible]); otherwise [updateCheckMessage] carries a
     * "You're up to date" (or error) message for the screen to show as a snackbar.
     */
    fun checkForUpdates() {
        if (_checkingForUpdates.value) return
        _checkingForUpdates.value = true
        _updateCheckMessage.value = null
        viewModelScope.launch {
            val result = UpdateManager.checkForUpdate()
            _checkingForUpdates.value = false
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    // The update dialog appears via UpdateManager.dialogVisible; no message.
                }
                is UpdateCheckResult.UpToDate, is UpdateCheckResult.NoApk ->
                    _updateCheckMessage.value = "You're up to date"
                is UpdateCheckResult.Error ->
                    _updateCheckMessage.value = result.message.ifBlank { "Update check failed" }
            }
        }
    }
}
