package com.nct32.notesplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import com.nct32.notesplus.data.db.AppDatabase
import com.nct32.notesplus.reminders.ReminderAlarmScheduler
import com.nct32.notesplus.reminders.ReminderNotifications
import com.nct32.notesplus.settings.AppSettings
import com.nct32.notesplus.settings.ThemeMode
import com.nct32.notesplus.ui.NotesApp
import com.nct32.notesplus.ui.theme.NotesTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bind the shared settings store to SharedPreferences-backed persistence so user
        // toggles survive app restarts.
        AppSettings.init(applicationContext)
        // Initialize the shared Room database (internal storage) so the repositories can read
        // and write notes / reminders / folders. Must happen before any UI reads the data.
        AppDatabase.init(applicationContext)
        // Bind the reminder alarm scheduler to the application context so the ViewModels can
        // schedule / cancel due-time alarms. Also create the "Reminders" notification channel.
        ReminderAlarmScheduler.init(applicationContext)
        ReminderNotifications.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            // Resolve the effective dark-theme flag from the user's appearance choice.
            // "System" follows the device setting; "Light"/"Dark" are fixed. Material You
            // dynamic color is kept in both modes (handled inside NotesTheme).
            val themeMode by AppSettings.instance.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            NotesTheme(darkTheme = darkTheme) {
                NotesApp()
            }
        }
    }
}
