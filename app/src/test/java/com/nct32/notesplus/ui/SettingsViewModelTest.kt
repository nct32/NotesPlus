package com.nct32.notesplus.ui

import com.nct32.notesplus.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * The ViewModel delegates to the process-wide [AppSettings] singleton, so these tests verify the
 * delegation: the exposed StateFlows reflect the singleton's current values and the setters
 * update them. Each test restores the singleton's state at the end so other tests are unaffected
 * (the singleton is in-memory in JVM tests, seeded from defaults).
 */
class SettingsViewModelTest {

    @Test
    fun exposes_the_singleton_toggles() {
        val viewModel = SettingsViewModel()
        assertEquals(AppSettings.instance.autocorrectEnabled.value, viewModel.autocorrectEnabled.value)
        assertEquals(AppSettings.instance.summarizeEnabled.value, viewModel.summarizeEnabled.value)
        assertEquals(AppSettings.instance.rewriteEnabled.value, viewModel.rewriteEnabled.value)
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

}
