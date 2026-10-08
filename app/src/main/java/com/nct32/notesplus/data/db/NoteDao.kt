package com.nct32.notesplus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [NoteEntity]s.
 */
@Dao
interface NoteDao {

    /**
     * Emits the full list of notes whenever the table changes, most recently updated first
     * (ties broken by creation time, newest first) so the notes list shows the freshest notes
     * at the top.
     */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, createdAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Sets the folder of every note currently in [folder] to `null` (unfiles them). Used when a
     * folder is deleted.
     */
    @Query("UPDATE notes SET folder = NULL WHERE folder = :folder")
    suspend fun unfileAllInFolder(folder: String)
}
