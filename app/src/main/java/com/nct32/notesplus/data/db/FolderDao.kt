package com.nct32.notesplus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [FolderEntity]s (explicitly created folders).
 */
@Dao
interface FolderDao {

    /** Emits the full list of explicitly created folders whenever the table changes. */
    @Query("SELECT * FROM folders")
    fun getAll(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE name = :name")
    suspend fun deleteByName(name: String)
}
