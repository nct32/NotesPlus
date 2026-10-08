package com.nct32.notesplus.ui

import com.nct32.notesplus.settings.AppSettings
import com.nct32.notesplus.update.GitHubAsset
import com.nct32.notesplus.update.GitHubRelease
import com.nct32.notesplus.update.ReleaseChannel
import com.nct32.notesplus.update.UpdateCheckResult
import com.nct32.notesplus.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * The ViewModel delegates to the process-wide [AppSettings] singleton, so these tests verify the
 * delegation: the exposed StateFlows reflect the singleton's current values and the setters
 * update them. Each test restores the singleton's state at the end so other tests are unaffected
 * (the singleton is in-memory in JVM tests, seeded from defaults).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        UpdateManager.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        UpdateManager.resetForTesting()
        AppSettings.resetForTesting()
    }

    @Test
    fun exposes_the_singleton_toggles() {
        val viewModel = SettingsViewModel()
        assertEquals(AppSettings.instance.autocorrectEnabled.value, viewModel.autocorrectEnabled.value)
        assertEquals(AppSettings.instance.summarizeEnabled.value, viewModel.summarizeEnabled.value)
        assertEquals(AppSettings.instance.rewriteEnabled.value, viewModel.rewriteEnabled.value)
        assertEquals(AppSettings.instance.releaseChannel.value, viewModel.releaseChannel.value)
    }

    @Test
    fun setAutocorrectEnabled_updates_stateflow() {
        val viewModel = SettingsViewModel()
        val previous = viewModel.autocorrectEnabled.value
        try {
            viewModel.setAutocorrectEnabled(!previous)
            assertEquals(!previous, viewModel.autocorrectEnabled.value)
            // The singleton (observed by the editor) sees the same change live.
            assertEquals(!previous, AppSettings.instance.autocorrectEnabled.value)
        } finally {
            viewModel.setAutocorrectEnabled(previous)
        }
    }

    @Test
    fun setSummarizeEnabled_updates_stateflow() {
        val viewModel = SettingsViewModel()
        val previous = viewModel.summarizeEnabled.value
        try {
            viewModel.setSummarizeEnabled(false)
            assertFalse(viewModel.summarizeEnabled.value)
            viewModel.setSummarizeEnabled(true)
            assertTrue(viewModel.summarizeEnabled.value)
        } finally {
            viewModel.setSummarizeEnabled(previous)
        }
    }

    @Test
    fun setRewriteEnabled_updates_stateflow() {
        val viewModel = SettingsViewModel()
        val previous = viewModel.rewriteEnabled.value
        try {
            viewModel.setRewriteEnabled(false)
            assertFalse(viewModel.rewriteEnabled.value)
            viewModel.setRewriteEnabled(true)
            assertTrue(viewModel.rewriteEnabled.value)
        } finally {
            viewModel.setRewriteEnabled(previous)
        }
    }

    @Test
    fun setReleaseChannel_updates_stateflow() {
        val viewModel = SettingsViewModel()
        val previous = viewModel.releaseChannel.value
        try {
            viewModel.setReleaseChannel(ReleaseChannel.Experimental)
            assertEquals(ReleaseChannel.Experimental, viewModel.releaseChannel.value)
            // The singleton (observed by the update check) sees the same change live.
            assertEquals(ReleaseChannel.Experimental, AppSettings.instance.releaseChannel.value)
        } finally {
            viewModel.setReleaseChannel(previous)
        }
    }

    @Test
    fun autoCheckUpdates_exposes_and_persists_the_setting() {
        val viewModel = SettingsViewModel()
        assertEquals(AppSettings.instance.autoCheckUpdates.value, viewModel.autoCheckUpdates.value)

        viewModel.setAutoCheckUpdates(false)
        assertFalse(viewModel.autoCheckUpdates.value)
        // The singleton (observed by UpdateManager) sees the same change live.
        assertFalse(AppSettings.instance.autoCheckUpdates.value)

        viewModel.setAutoCheckUpdates(true)
        assertTrue(viewModel.autoCheckUpdates.value)
    }

    @Test
    fun checkForUpdates_upToDate_sets_message() = runTest(dispatcher) {
        UpdateManager.updateCheckProvider = { _, _ -> UpdateCheckResult.UpToDate }
        UpdateManager.installedVersionProvider = { "v1.0.0" }
        val viewModel = SettingsViewModel()

        assertFalse(viewModel.checkingForUpdates.value)
        viewModel.checkForUpdates()
        assertTrue(viewModel.checkingForUpdates.value)
        advanceUntilIdle()

        assertFalse(viewModel.checkingForUpdates.value)
        assertEquals("You're up to date", viewModel.updateCheckMessage.value)
        // The up-to-date result shows no dialog.
        assertFalse(UpdateManager.dialogVisible.value)

        viewModel.clearUpdateCheckMessage()
        assertNull(viewModel.updateCheckMessage.value)
    }

    @Test
    fun checkForUpdates_error_sets_error_message() = runTest(dispatcher) {
        UpdateManager.updateCheckProvider = { _, _ -> UpdateCheckResult.Error("Network error") }
        UpdateManager.installedVersionProvider = { "v1.0.0" }
        val viewModel = SettingsViewModel()

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertFalse(viewModel.checkingForUpdates.value)
        assertEquals("Network error", viewModel.updateCheckMessage.value)
        assertFalse(UpdateManager.dialogVisible.value)
    }

    @Test
    fun checkForUpdates_updateAvailable_shows_dialog_and_no_message() = runTest(dispatcher) {
        UpdateManager.updateCheckProvider = { _, _ ->
            UpdateCheckResult.UpdateAvailable(
                release = GitHubRelease(tag_name = "v1.0.0"),
                asset = GitHubAsset(name = "NotesPlus-v1.0.0.apk"),
            )
        }
        UpdateManager.installedVersionProvider = { "v0.1.0" }
        val viewModel = SettingsViewModel()

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertFalse(viewModel.checkingForUpdates.value)
        assertNull(viewModel.updateCheckMessage.value)
        // The dialog is driven by the manager's dialogVisible flag.
        assertTrue(UpdateManager.dialogVisible.value)
        assertEquals("v1.0.0", UpdateManager.availableUpdate.value?.tag)
    }

    @Test
    fun checkForUpdates_ignores_reentry_while_checking() = runTest(dispatcher) {
        var calls = 0
        UpdateManager.updateCheckProvider = { _, _ ->
            calls++
            UpdateCheckResult.UpToDate
        }
        UpdateManager.installedVersionProvider = { "v1.0.0" }
        val viewModel = SettingsViewModel()

        viewModel.checkForUpdates()
        viewModel.checkForUpdates() // re-entrant call while the first is in flight
        advanceUntilIdle()

        assertEquals(1, calls)
        assertFalse(viewModel.checkingForUpdates.value)
    }

}
