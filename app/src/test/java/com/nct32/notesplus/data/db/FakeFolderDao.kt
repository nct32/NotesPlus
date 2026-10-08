package com.nct32.notesplus.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [FolderDao] for JVM unit tests (reactive, like the real DAO). */
class FakeFolderDao : FolderDao {

    private val table = MutableStateFlow<List<FolderEntity>>(emptyList())

    override fun getAll(): Flow<List<FolderEntity>> = table

    override suspend fun upsert(folder: FolderEntity) {
        table.value = table.value.filterNot { it.name == folder.name } + folder
    }

    override suspend fun deleteByName(name: String) {
        table.value = table.value.filterNot { it.name == name }
    }
}
