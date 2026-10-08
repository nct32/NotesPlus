package com.nct32.notesplus.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin [SettingsStore] (JVM-safe: uses in-memory persistence).
 */
class SettingsStoreTest {

    /** A fake persistence layer that behaves like SharedPreferences. */
    private class FakePersistence : SettingsPersistence {
        val values = HashMap<String, Boolean>()
        val strings = HashMap<String, String>()
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] ?: default
        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
        override fun getString(key: String, default: String): String = strings[key] ?: default
        override fun putString(key: String, value: String) {
            strings[key] = value
        }
    }

    @Test
    fun defaults_are_enabled() {
        val store = SettingsStore(FakePersistence())
        assertTrue(store.autocorrectEnabled.value)
        assertTrue(store.summarizeEnabled.value)
        assertTrue(store.rewriteEnabled.value)
        // The welcome banner is shown on first launch (not dismissed by default).
        assertFalse(store.welcomeDismissed.value)
        // The theme follows the system by default.
        assertEquals(ThemeMode.System, store.themeMode.value)
    }

    @Test
    fun setThemeMode_updates_stateflow_and_persists() {
        val persistence = FakePersistence()
        val store = SettingsStore(persistence)

        store.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, store.themeMode.value)
        assertEquals("Dark", persistence.getString(SettingsStore.KEY_THEME_MODE, "System"))

        store.setThemeMode(ThemeMode.Light)
        assertEquals(ThemeMode.Light, store.themeMode.value)
        assertEquals("Light", persistence.getString(SettingsStore.KEY_THEME_MODE, "System"))

        store.setThemeMode(ThemeMode.System)
        assertEquals(ThemeMode.System, store.themeMode.value)
        assertEquals("System", persistence.getString(SettingsStore.KEY_THEME_MODE, "System"))
    }

    @Test
    fun themeMode_survives_restart() {
        val persistence = FakePersistence()
        val first = SettingsStore(persistence)
        first.setThemeMode(ThemeMode.Dark)

        // Simulates an app restart: a fresh store over the same persisted values.
        val second = SettingsStore(persistence)
        assertEquals(ThemeMode.Dark, second.themeMode.value)
    }

    @Test
    fun themeMode_unknown_persisted_value_falls_back_to_system() {
        val persistence = FakePersistence()
        persistence.putString(SettingsStore.KEY_THEME_MODE, "Bogus")

        val store = SettingsStore(persistence)
        assertEquals(ThemeMode.System, store.themeMode.value)
    }

    @Test
    fun setWelcomeDismissed_updates_stateflow_and_persists() {
        val persistence = FakePersistence()
        val store = SettingsStore(persistence)

        store.setWelcomeDismissed(true)
        assertTrue(store.welcomeDismissed.value)
        assertTrue(persistence.getBoolean(SettingsStore.KEY_WELCOME_DISMISSED, false))
    }

    @Test
    fun welcomeDismissed_survives_restart() {
        val persistence = FakePersistence()
        val first = SettingsStore(persistence)
        first.setWelcomeDismissed(true)

        // Simulates an app restart: a fresh store over the same persisted values.
        val second = SettingsStore(persistence)
        assertTrue(second.welcomeDismissed.value)
    }

    @Test
    fun setAutocorrectEnabled_updates_stateflow_and_persists() {
        val persistence = FakePersistence()
        val store = SettingsStore(persistence)

        store.setAutocorrectEnabled(false)
        assertFalse(store.autocorrectEnabled.value)
        assertFalse(persistence.getBoolean(SettingsStore.KEY_AUTOCORRECT_ENABLED, true))

        store.setAutocorrectEnabled(true)
        assertTrue(store.autocorrectEnabled.value)
        assertTrue(persistence.getBoolean(SettingsStore.KEY_AUTOCORRECT_ENABLED, true))
    }

    @Test
    fun setSummarizeEnabled_updates_stateflow_and_persists() {
        val persistence = FakePersistence()
        val store = SettingsStore(persistence)

        store.setSummarizeEnabled(false)
        assertFalse(store.summarizeEnabled.value)
        assertFalse(persistence.getBoolean(SettingsStore.KEY_SUMMARIZE_ENABLED, true))

        store.setSummarizeEnabled(true)
        assertTrue(store.summarizeEnabled.value)
    }

    @Test
    fun setRewriteEnabled_updates_stateflow_and_persists() {
        val persistence = FakePersistence()
        val store = SettingsStore(persistence)

        store.setRewriteEnabled(false)
        assertFalse(store.rewriteEnabled.value)
        assertFalse(persistence.getBoolean(SettingsStore.KEY_REWRITE_ENABLED, true))

        store.setRewriteEnabled(true)
        assertTrue(store.rewriteEnabled.value)
        assertTrue(persistence.getBoolean(SettingsStore.KEY_REWRITE_ENABLED, true))
    }

    @Test
    fun new_store_reads_back_persisted_values() {
        val persistence = FakePersistence()
        val first = SettingsStore(persistence)
        first.setAutocorrectEnabled(false)
        first.setSummarizeEnabled(false)
        first.setRewriteEnabled(false)

        // Simulates an app restart: a fresh store over the same persisted values.
        val second = SettingsStore(persistence)
        assertFalse(second.autocorrectEnabled.value)
        assertFalse(second.summarizeEnabled.value)
        assertFalse(second.rewriteEnabled.value)
    }

    @Test
    fun toggling_one_setting_does_not_affect_the_other() {
        val store = SettingsStore(FakePersistence())
        store.setAutocorrectEnabled(false)
        assertTrue(store.summarizeEnabled.value)
        assertFalse(store.autocorrectEnabled.value)
    }

    @Test
    fun appSettings_instance_is_a_process_wide_singleton() {
        assertSame(AppSettings.instance, AppSettings.instance)
    }

    @Test
    fun appSettings_instance_has_default_values_before_any_toggle() {
        // In a JVM test no Android context is available, so the in-memory fallback store
        // is used and both toggles default to enabled.
        assertTrue(AppSettings.instance.autocorrectEnabled.value)
        assertTrue(AppSettings.instance.summarizeEnabled.value)
    }
}
