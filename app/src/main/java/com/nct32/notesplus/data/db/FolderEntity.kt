package com.nct32.notesplus.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a folder that the user explicitly created (which may have no notes yet).
 *
 * Folders that are only referenced by notes (never explicitly created) do not need a row here —
 * the visible folder list is the union of explicitly created folders and folders present on
 * notes.
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val name: String
)
