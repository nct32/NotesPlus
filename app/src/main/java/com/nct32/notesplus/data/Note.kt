package com.nct32.notesplus.data

/**
 * A single note in the Notes+ app.
 *
 * @param id Unique identifier for the note.
 * @param title Short, human-readable title.
 * @param body Full note content.
 * @param folder Optional folder / tag used to group and filter notes. `null` means unfiled.
 * @param createdAt Epoch millis (UTC) when the note was first created.
 * @param updatedAt Epoch millis (UTC) when the note was last modified.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val folder: String?,
    val createdAt: Long,
    val updatedAt: Long
)
