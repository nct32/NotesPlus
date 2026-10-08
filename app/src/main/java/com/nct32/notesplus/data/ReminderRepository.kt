package com.nct32.notesplus.data

import com.nct32.notesplus.data.db.AppDatabase
import com.nct32.notesplus.data.db.ReminderDao
import com.nct32.notesplus.data.db.toDomain
import com.nct32.notesplus.data.db.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Persistent data source for [Reminder]s backed by Room (SQLite in the app's internal storage).
 *
 * The source of truth is the `reminders` table. [reminders] is a [StateFlow] derived from the
 * DAO's reactive `Flow`, so the UI updates reactively whenever a row changes. All mutations are
 * `suspend` functions that write to the database; the writes are durable on disk, so reminders
 * survive app kills and phone restarts.
 *
 * [ReminderRepository] is constructed with its DAO (dependency-injected) so it can be
 * unit-tested against an in-memory database. The process-wide shared instance is exposed via
 * [instance], built from [AppDatabase.db] (initialized in `MainActivity.onCreate`).
 */
class ReminderRepository(
    private val reminderDao: ReminderDao,
    /** Hosts the `stateIn` collector for the derived flow. */
    private val scope: CoroutineScope
) {

    /**
     * The full, unsorted list of reminders, sourced from the `reminders` table.
     *
     * Shared with [SharingStarted.Eagerly]: the repository is a process-wide singleton and this
     * is the source of truth for the UI, so the derived flow is kept warm for the process
     * lifetime. That also makes [getAll] (a synchronous read of [reminders]) reliable regardless
     * of whether a UI subscriber is currently active.
     */
    val reminders: StateFlow<List<Reminder>> = reminderDao.getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Returns all reminders, sorted for display: incomplete first (by due date), then completed. */
    fun getAll(): List<Reminder> = sortReminders(reminders.value)

    /**
     * Returns the current (unsorted) list of reminders as a plain snapshot. Used by the alarm
     * scheduler (e.g. on boot) to iterate every reminder without display sorting.
     */
    fun snapshot(): List<Reminder> = reminders.value

    /** Returns the reminder with [id], or `null` if it doesn't exist. */
    suspend fun getById(id: String): Reminder? = reminderDao.getById(id)?.toDomain()

    /** Creates a new reminder and returns it. [dueAt] may be `null` for "no date". */
    suspend fun create(title: String, dueAt: Long? = null): Reminder {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            title = title,
            dueAt = dueAt,
            completed = false,
            createdAt = now
        )
        reminderDao.upsert(reminder.toEntity())
        return reminder
    }

    /** Toggles the [Reminder.completed] flag of the reminder with [id]. No-op if unknown. */
    suspend fun toggleCompleted(id: String) {
        val existing = reminderDao.getById(id) ?: return
        reminderDao.upsert(existing.toDomain().copy(completed = !existing.completed).toEntity())
    }

    /**
     * Updates the reminder with [id], replacing its [title] and [dueAt] while preserving its
     * [Reminder.completed] and [Reminder.createdAt] (so editing never resets the completed
     * state or the creation timestamp). No-op if the id is unknown.
     */
    suspend fun update(id: String, title: String, dueAt: Long?) {
        val existing = reminderDao.getById(id) ?: return
        reminderDao.upsert(
            existing.toDomain().copy(title = title, dueAt = dueAt).toEntity()
        )
    }

    /** Deletes the reminder with [id]. No-op if the id is unknown. */
    suspend fun delete(id: String) = reminderDao.deleteById(id)

    companion object {
        /**
         * Shared instance so state survives ViewModel recreation. Built from the shared
         * [AppDatabase]; the main dispatcher hosts the derived-flow collector.
         */
        val instance: ReminderRepository by lazy {
            val db = AppDatabase.db
            ReminderRepository(db.reminderDao(), CoroutineScope(Dispatchers.Main))
        }

        /**
         * Display ordering for reminders:
         *
         * 1. Incomplete reminders first, ordered by due date (earliest first). Reminders without
         *    a due date sort last among the incomplete ones, then by creation time (newest first).
         * 2. Completed reminders after, most recently created first.
         */
        fun sortReminders(reminders: List<Reminder>): List<Reminder> {
            val incomplete = reminders.filter { !it.completed }
                .sortedWith(
                    compareBy<Reminder> { it.dueAt == null }
                        .thenBy { it.dueAt ?: Long.MAX_VALUE }
                        .thenByDescending { it.createdAt }
                )
            val completed = reminders.filter { it.completed }
                .sortedByDescending { it.createdAt }
            return incomplete + completed
        }
    }
}
