package com.nct32.notesplus.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity backing a [com.nct32.notesplus.data.Reminder].
 *
 * Stored in the app's internal SQLite database (internal app storage), so reminders survive app
 * kills and phone restarts without any storage permissions.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val dueAt: Long?,
    val completed: Boolean,
    val createdAt: Long
)
