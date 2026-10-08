package com.nct32.notesplus.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity backing a [com.nct32.notesplus.data.Note].
 *
 * Stored in the app's internal SQLite database (internal app storage), so notes survive app
 * kills and phone restarts without any storage permissions.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val folder: String?,
    val createdAt: Long,
    val updatedAt: Long
)
