package com.nct32.notesplus.data

/**
 * A single reminder in the Notes+ app.
 *
 * @param id Unique identifier for the reminder.
 * @param title Short, human-readable reminder title.
 * @param dueAt Optional epoch millis (UTC) when the reminder is due. `null` means "no date".
 * @param completed Whether the reminder has been marked as completed.
 * @param createdAt Epoch millis (UTC) when the reminder was created.
 */
data class Reminder(
    val id: String,
    val title: String,
    val dueAt: Long?,
    val completed: Boolean,
    val createdAt: Long
)
