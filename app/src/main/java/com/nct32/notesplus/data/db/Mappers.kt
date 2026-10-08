package com.nct32.notesplus.data.db

import com.nct32.notesplus.data.Note
import com.nct32.notesplus.data.Reminder

/** Maps a [NoteEntity] (Room) to the domain [Note]. */
fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    folder = folder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Maps a domain [Note] to a [NoteEntity] (Room). */
fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    folder = folder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Maps a [ReminderEntity] (Room) to the domain [Reminder]. */
fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    title = title,
    dueAt = dueAt,
    completed = completed,
    createdAt = createdAt
)

/** Maps a domain [Reminder] to a [ReminderEntity] (Room). */
fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    dueAt = dueAt,
    completed = completed,
    createdAt = createdAt
)
