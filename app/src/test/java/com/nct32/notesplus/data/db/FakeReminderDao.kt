package com.nct32.notesplus.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ReminderDao] for JVM unit tests (reactive, like the real DAO). */
class FakeReminderDao : ReminderDao {

    private val table = MutableStateFlow<List<ReminderEntity>>(emptyList())

    override fun getAll(): Flow<List<ReminderEntity>> = table

    override suspend fun getById(id: String): ReminderEntity? =
        table.value.firstOrNull { it.id == id }

    override suspend fun upsert(reminder: ReminderEntity) {
        table.value = table.value.filterNot { it.id == reminder.id } + reminder
    }

    override suspend fun deleteById(id: String) {
        table.value = table.value.filterNot { it.id == id }
    }
}
