package com.nct32.notesplus.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [NoteDao] for JVM unit tests.
 *
 * Mimics the real DAO's reactive behavior: [getAll] is a `Flow` that re-emits whenever a row is
 * upserted/deleted, and it applies the same ordering as the real DAO (`updatedAt DESC,
 * createdAt DESC`) so tests observe the same list order the UI does.
 */
class FakeNoteDao : NoteDao {

    private val table = MutableStateFlow<List<NoteEntity>>(emptyList())

    override fun getAll(): Flow<List<NoteEntity>> = table.map {
        it.sortedWith(
            compareByDescending<NoteEntity> { it.updatedAt }
                .thenByDescending { it.createdAt }
        )
    }

    override suspend fun getById(id: String): NoteEntity? =
        table.value.firstOrNull { it.id == id }

    override suspend fun upsert(note: NoteEntity) {
        table.value = table.value.filterNot { it.id == note.id } + note
    }

    override suspend fun deleteById(id: String) {
        table.value = table.value.filterNot { it.id == id }
    }

    override suspend fun unfileAllInFolder(folder: String) {
        table.value = table.value.map {
            if (it.folder == folder) it.copy(folder = null) else it
        }
    }
}
