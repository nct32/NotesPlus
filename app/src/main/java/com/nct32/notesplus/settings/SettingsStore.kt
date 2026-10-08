package com.nct32.notesplus.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nct32.notesplus.update.ReleaseChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal persistence contract for boolean settings.
 *
 * Keeping persistence behind this interface makes [SettingsStore] pure Kotlin and fully
 * unit-testable on the JVM (tests inject an in-memory fake instead of SharedPreferences).
 */
interface SettingsPersistence {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

/**
 * [SettingsPersistence] backed by the built-in Android [SharedPreferences] — no new
 * dependencies required.
 */
class SharedPreferencesSettingsPersistence(
    private val prefs: SharedPreferences
) : SettingsPersistence {

    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    companion object {
        /** Name of the SharedPreferences file used by the app. */
        const val PREFS_NAME = "notes_plus_settings"

        fun create(context: Context): SharedPreferencesSettingsPersistence =
            SharedPreferencesSettingsPersistence(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}

/**
 * In-memory [SettingsPersistence] used as a safe fallback when no Android context is
 * available (e.g. plain JVM unit tests). Values are kept for the lifetime of the process.
 */
class InMemorySettingsPersistence : SettingsPersistence {
    private val values = HashMap<String, Boolean>()

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] ?: default
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    private val strings = HashMap<String, String>()
    override fun getString(key: String, default: String): String = strings[key] ?: default
    override fun putString(key: String, value: String) {
        strings[key] = value
    }
}

/**
 * The app's appearance mode, selectable in Settings.
 *
 * - [System] follows the device's current light/dark setting (the default).
 * - [Light] always uses the light color scheme.
 * - [Dark] always uses the dark color scheme.
 *
 * Stored as a string (the enum name) so it survives app restarts.
 */
enum class ThemeMode {
    System,
    Light,
    Dark;

    companion object {
        /** Parses a persisted string back into a [ThemeMode], falling back to [System]. */
        fun fromString(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}

/**
 * The app's settings store: the single source of truth for user-configurable toggles.
 *
 * Values are persisted through [SettingsPersistence] (SharedPreferences on device) and exposed
 * as [StateFlow]s so the UI reacts live. A process-wide singleton ([AppSettings.instance]) is
 * shared by every ViewModel, so toggling a setting in one screen is immediately visible in
 * others.
 *
 * ## Adding a new toggle (e.g. `rewriteEnabled`)
 *
 * 1. Add a `KEY_*` constant and a `DEFAULT_*` constant in [Companion].
 * 2. Add a `MutableStateFlow` seeded from `persistence`, a public `StateFlow`, and a setter.
 *
 * Nothing else changes — the persistence layer, the singleton, and the ViewModels are
 * agnostic to which toggles exist.
 */
class SettingsStore(private val persistence: SettingsPersistence) {

    private val _autocorrectEnabled = MutableStateFlow(
        persistence.getBoolean(KEY_AUTOCORRECT_ENABLED, DEFAULT_AUTOCORRECT_ENABLED)
    )
    /** Whether the autocorrect tool is available in the editor (default: [DEFAULT_AUTOCORRECT_ENABLED]). */
    val autocorrectEnabled: StateFlow<Boolean> = _autocorrectEnabled.asStateFlow()

    private val _summarizeEnabled = MutableStateFlow(
        persistence.getBoolean(KEY_SUMMARIZE_ENABLED, DEFAULT_SUMMARIZE_ENABLED)
    )
    /** Whether the summarize tool is available in the editor (default: [DEFAULT_SUMMARIZE_ENABLED]). */
    val summarizeEnabled: StateFlow<Boolean> = _summarizeEnabled.asStateFlow()

    private val _rewriteEnabled = MutableStateFlow(
        persistence.getBoolean(KEY_REWRITE_ENABLED, DEFAULT_REWRITE_ENABLED)
    )
    /** Whether the rewrite quick actions are available in the editor (default: [DEFAULT_REWRITE_ENABLED]). */
    val rewriteEnabled: StateFlow<Boolean> = _rewriteEnabled.asStateFlow()

    private val _welcomeDismissed = MutableStateFlow(
        persistence.getBoolean(KEY_WELCOME_DISMISSED, DEFAULT_WELCOME_DISMISSED)
    )
    /**
     * Whether the user has dismissed the first-launch welcome banner (default: [DEFAULT_WELCOME_DISMISSED]).
     * Once set to `true` the banner never shows again.
     */
    val welcomeDismissed: StateFlow<Boolean> = _welcomeDismissed.asStateFlow()

    /** Enables/disables autocorrect and persists the choice. */
    fun setAutocorrectEnabled(enabled: Boolean) {
        persistence.putBoolean(KEY_AUTOCORRECT_ENABLED, enabled)
        _autocorrectEnabled.value = enabled
    }

    /** Enables/disables summarize and persists the choice. */
    fun setSummarizeEnabled(enabled: Boolean) {
        persistence.putBoolean(KEY_SUMMARIZE_ENABLED, enabled)
        _summarizeEnabled.value = enabled
    }

    /** Enables/disables the rewrite quick actions and persists the choice. */
    fun setRewriteEnabled(enabled: Boolean) {
        persistence.putBoolean(KEY_REWRITE_ENABLED, enabled)
        _rewriteEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromString(persistence.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE.name))
    )
    /**
     * The app's appearance mode (default: [ThemeMode.System]). Drives whether the app uses the
     * light, dark, or system-following color scheme.
     */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _releaseChannel = MutableStateFlow(
        ReleaseChannel.fromString(persistence.getString(KEY_RELEASE_CHANNEL, DEFAULT_RELEASE_CHANNEL.name))
    )
    /**
     * The update channel the user has opted into (default: [ReleaseChannel.Stable]).
     * Controls which GitHub releases the update check offers.
     */
    val releaseChannel: StateFlow<ReleaseChannel> = _releaseChannel.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(
        persistence.getBoolean(KEY_AUTO_CHECK_UPDATES, DEFAULT_AUTO_CHECK_UPDATES)
    )
    /**
     * Whether the app automatically checks for updates in the background (default:
     * [DEFAULT_AUTO_CHECK_UPDATES]). When `false`, updates are only found when the user
     * explicitly checks.
     */
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _skippedReleaseTag = MutableStateFlow(
        readSkippedReleaseTag(persistence)
    )
    /**
     * The tag of the release the user chose to skip (default: `null`). While set, the update
     * check never offers that release again.
     *
     * Persistence note: `SharedPreferences` has no nullable string, so `null` is stored as the
     * empty string (see [readSkippedReleaseTag] / [setSkippedReleaseTag]).
     */
    val skippedReleaseTag: StateFlow<String?> = _skippedReleaseTag.asStateFlow()

    /** Marks the first-launch welcome banner as dismissed and persists the choice. */
    fun setWelcomeDismissed(dismissed: Boolean) {
        persistence.putBoolean(KEY_WELCOME_DISMISSED, dismissed)
        _welcomeDismissed.value = dismissed
    }

    /** Sets the app's appearance mode and persists the choice. */
    fun setThemeMode(mode: ThemeMode) {
        persistence.putString(KEY_THEME_MODE, mode.name)
        _themeMode.value = mode
    }

    /** Sets the update channel and persists the choice. */
    fun setReleaseChannel(channel: ReleaseChannel) {
        persistence.putString(KEY_RELEASE_CHANNEL, channel.name)
        _releaseChannel.value = channel
    }

    /** Enables/disables the automatic background update check and persists the choice. */
    fun setAutoCheckUpdates(enabled: Boolean) {
        persistence.putBoolean(KEY_AUTO_CHECK_UPDATES, enabled)
        _autoCheckUpdates.value = enabled
    }

    /**
     * Sets the skipped release tag and persists it. Pass `null` to clear the skip (the empty
     * string is persisted for `null`, see [skippedReleaseTag]).
     */
    fun setSkippedReleaseTag(tag: String?) {
        persistence.putString(KEY_SKIPPED_RELEASE_TAG, tag.orEmpty())
        _skippedReleaseTag.value = tag
    }

    companion object {
        const val KEY_AUTOCORRECT_ENABLED = "autocorrect_enabled"
        const val KEY_SUMMARIZE_ENABLED = "summarize_enabled"
        const val KEY_REWRITE_ENABLED = "rewrite_enabled"
        const val KEY_WELCOME_DISMISSED = "welcome_dismissed"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_RELEASE_CHANNEL = "release_channel"
        const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        const val KEY_SKIPPED_RELEASE_TAG = "skipped_release_tag"

        const val DEFAULT_AUTOCORRECT_ENABLED = true
        const val DEFAULT_SUMMARIZE_ENABLED = true
        const val DEFAULT_REWRITE_ENABLED = true
        const val DEFAULT_WELCOME_DISMISSED = false
        val DEFAULT_THEME_MODE: ThemeMode = ThemeMode.System
        val DEFAULT_RELEASE_CHANNEL: ReleaseChannel = ReleaseChannel.Stable
        const val DEFAULT_AUTO_CHECK_UPDATES = true

        /**
         * Reads the persisted skipped release tag, mapping the empty-string sentinel (used for
         * "no skip", since `SharedPreferences` cannot store `null`) back to `null`.
         */
        fun readSkippedReleaseTag(persistence: SettingsPersistence): String? {
            val raw = persistence.getString(KEY_SKIPPED_RELEASE_TAG, "")
            return raw.ifEmpty { null }
        }
    }
}

/**
 * Process-wide holder for the shared [SettingsStore].
 *
 * [init] must be called once with an Android context (done in [com.nct32.notesplus.MainActivity])
 * so that real SharedPreferences-backed persistence is used. If [instance] is accessed before
 * [init] (e.g. in plain JVM unit tests), a safe in-memory store with default values is created
 * instead, so nothing crashes.
 */
object AppSettings {

    private var store: SettingsStore? = null

    /** The shared settings store for the whole process. */
    val instance: SettingsStore
        get() = store ?: SettingsStore(InMemorySettingsPersistence()).also { store = it }

    /** Binds the shared store to SharedPreferences-backed persistence. Idempotent. */
    fun init(context: Context) {
        if (store == null) {
            store = SettingsStore(SharedPreferencesSettingsPersistence.create(context))
        }
    }

    /**
     * Replaces the shared store with a fresh in-memory one (plain JVM unit tests only, so a
     * test that mutated a setting never leaks into another test).
     */
    fun resetForTesting() {
        store = SettingsStore(InMemorySettingsPersistence())
    }
}
