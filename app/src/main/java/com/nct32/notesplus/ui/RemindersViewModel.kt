package com.nct32.notesplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nct32.notesplus.data.Reminder
import com.nct32.notesplus.data.ReminderRepository
import com.nct32.notesplus.reminders.ReminderAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for the reminders UI, exposing [StateFlow]s for unidirectional data
 * flow.
 *
 * [reminders] is derived from the repository's raw list and re-sorted on every change using
 * [ReminderRepository.sortReminders]: incomplete reminders first (earliest due date first,
 * dateless last), completed reminders after (newest first).
 *
 * Reminders are persisted in Room (see [ReminderRepository]) and, when they have a due time,
 * also get an [android.app.AlarmManager] alarm via [ReminderAlarmScheduler] so the app can
 * notify the user when the reminder is due.
 *
 * ## Context strategy
 *
 * The alarm scheduler needs an Android [android.content.Context]. Rather than making this an
 * `AndroidViewModel` (which would require an `Application` in plain JVM unit tests), the
 * scheduler is a process-wide singleton initialized in `MainActivity.onCreate` — the same
 * pattern as `AppDatabase.init` / `AppSettings.init`. In JVM unit tests it is simply a no-op.
 */
class RemindersViewModel(
    private val repository: ReminderRepository = ReminderRepository.instance
) : ViewModel() {

    /** All reminders, sorted for display (incomplete first by due date, completed after). */
    val reminders: StateFlow<List<Reminder>> = repository.reminders
        .map { ReminderRepository.sortReminders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Adds a new reminder with [title] and an optional [dueAt], then schedules its due-time
     * alarm (a no-op when there is no due time).
     */
    fun addReminder(title: String, dueAt: Long? = null) {
        viewModelScope.launch {
            val reminder = repository.create(title, dueAt)
            ReminderAlarmScheduler.schedule(reminder)
        }
    }

    /**
     * Updates the reminder with [id], replacing its [title] and [dueAt]. The reminder's
     * completed state and creation time are preserved (see [ReminderRepository.update]).
     * Afterwards the due-time alarm is re-scheduled for the (possibly new) due time — or
     * cancelled when the due time was cleared.
     */
    fun updateReminder(id: String, title: String, dueAt: Long? = null) {
        viewModelScope.launch {
            repository.update(id, title, dueAt)
            // Re-read the persisted state (completed flag, createdAt) so the scheduler's
            // decision (e.g. "completed → cancel") sees the current values.
            repository.getById(id)?.let { ReminderAlarmScheduler.schedule(it) }
        }
    }

    /**
     * Toggles the completed state of the reminder with [id]. Marking it completed cancels its
     * due-time alarm; un-completing it re-schedules the alarm if the due time is still in the
     * future.
     */
    fun toggleReminder(id: String) {
        viewModelScope.launch {
            repository.toggleCompleted(id)
            repository.getById(id)?.let { ReminderAlarmScheduler.schedule(it) }
        }
    }

    /** Deletes the reminder with [id] and cancels its due-time alarm. */
    fun deleteReminder(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            ReminderAlarmScheduler.cancel(id)
        }
    }
}
