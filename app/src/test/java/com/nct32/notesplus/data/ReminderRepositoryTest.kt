package com.nct32.notesplus.data

import com.nct32.notesplus.data.db.FakeReminderDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Room-backed [ReminderRepository].
 *
 * The repository is constructed with an in-memory [FakeReminderDao] so the tests run on a plain
 * JVM (no Android `Context` / `AppDatabase` needed) while still exercising the real
 * entity→domain mapping, the reactive `StateFlow` derivation, and the CRUD logic.
 *
 * The repository's hosting scope uses an [UnconfinedTestDispatcher] so the `stateIn` collector
 * runs synchronously on each DAO emission — reads of [ReminderRepository.reminders] are
 * deterministic (no `advanceUntilIdle` needed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepositoryTest {

    /** A fresh repository backed by an in-memory DAO fake (empty to start). */
    private fun repo() =
        ReminderRepository(FakeReminderDao(), CoroutineScope(UnconfinedTestDispatcher()))

    private fun ids(reminders: List<Reminder>) = reminders.map { it.id }

    @Test
    fun starts_empty() {
        // No seed reminders: a brand-new repository has none.
        val r = repo()
        assertTrue(r.reminders.value.isEmpty())
        // A freshly created reminder is the only one with its unique id.
        val created = runBlocking { r.create("UniqueReminderTitle") }
        assertNotNull(created.id)
        assertTrue(r.getAll().any { it.id == created.id })
    }

    @Test
    fun create_returns_reminder_with_defaults() = runBlocking {
        val created = repo().create("My reminder")
        assertEquals("My reminder", created.title)
        assertFalse(created.completed)
        assertNull(created.dueAt)
        assertTrue(created.createdAt > 0)
    }

    @Test
    fun create_with_due_at_stores_it() = runBlocking {
        val dueAt = System.currentTimeMillis() + 3_600_000L
        val created = repo().create("With due", dueAt)
        assertEquals(dueAt, created.dueAt)
    }

    @Test
    fun toggleCompleted_flips_the_flag() = runBlocking {
        val r = repo()
        val created = r.create("Toggle me")
        assertFalse(created.completed)

        r.toggleCompleted(created.id)
        val afterFirst = r.getAll().first { it.id == created.id }
        assertTrue(afterFirst.completed)

        r.toggleCompleted(created.id)
        val afterSecond = r.getAll().first { it.id == created.id }
        assertFalse(afterSecond.completed)
    }

    @Test
    fun toggleCompleted_unknown_id_is_noop() = runBlocking {
        val r = repo()
        val before = r.reminders.value.size
        r.toggleCompleted("zzz_no_such_id_zzz")
        assertEquals(before, r.reminders.value.size)
    }

    @Test
    fun update_changes_title_and_due_at() = runBlocking {
        val r = repo()
        val created = r.create("Original title")
        val newDueAt = System.currentTimeMillis() + 3_600_000L

        r.update(created.id, "Edited title", newDueAt)

        val updated = r.getAll().first { it.id == created.id }
        assertEquals("Edited title", updated.title)
        assertEquals(newDueAt, updated.dueAt)
    }

    @Test
    fun update_clears_due_at_to_null() = runBlocking {
        val r = repo()
        val dueAt = System.currentTimeMillis() + 3_600_000L
        val created = r.create("With date", dueAt)

        r.update(created.id, "With date", null)

        val updated = r.getAll().first { it.id == created.id }
        assertNull(updated.dueAt)
    }

    @Test
    fun update_preserves_completed_and_created_at() = runBlocking {
        val r = repo()
        val created = r.create("To complete", System.currentTimeMillis() + 1_000L)
        r.toggleCompleted(created.id)
        // Read the current state from the repository (the `created` reference is stale after the
        // toggle, since the repository stores a distinct instance).
        val toggled = r.getAll().first { it.id == created.id }
        assertTrue(toggled.completed)

        r.update(created.id, "Edited", null)

        val updated = r.getAll().first { it.id == created.id }
        // Editing must not reset the completed flag or the creation timestamp.
        assertTrue(updated.completed)
        assertEquals(created.createdAt, updated.createdAt)
    }

    @Test
    fun update_unknown_id_is_noop() = runBlocking {
        val r = repo()
        val before = r.reminders.value.size
        r.update("zzz_no_such_id_zzz", "Ghost", null)
        assertEquals(before, r.reminders.value.size)
    }

    @Test
    fun delete_removes_the_reminder() = runBlocking {
        val r = repo()
        val created = r.create("Doomed reminder")
        r.delete(created.id)
        assertTrue(r.getAll().none { it.id == created.id })
    }

    @Test
    fun delete_unknown_id_is_noop() = runBlocking {
        val r = repo()
        val before = r.reminders.value.size
        r.delete("zzz_no_such_id_zzz")
        assertEquals(before, r.reminders.value.size)
    }

    @Test
    fun sortReminders_incomplete_first_by_due_date() {
        val now = System.currentTimeMillis()
        val noDate = Reminder("no date", "n", null, false, now)
        val far = Reminder("far", "f", now + 10_000L, false, now)
        val soon = Reminder("soon", "s", now + 1_000L, false, now)

        val sorted = ReminderRepository.sortReminders(listOf(far, noDate, soon))
        assertEquals(listOf(soon.id, far.id, noDate.id), ids(sorted))
    }

    @Test
    fun sortReminders_completed_after_incomplete() {
        val now = System.currentTimeMillis()
        val completed = Reminder("done", "d", now + 1_000L, true, now)
        val incomplete = Reminder("todo", "t", now + 2_000L, false, now)

        val sorted = ReminderRepository.sortReminders(listOf(completed, incomplete))
        assertEquals(listOf(incomplete.id, completed.id), ids(sorted))
    }

    @Test
    fun sortReminders_completed_newest_first() {
        val now = System.currentTimeMillis()
        val older = Reminder("older done", "o", null, true, now - 10_000L)
        val newer = Reminder("newer done", "n", null, true, now)

        val sorted = ReminderRepository.sortReminders(listOf(older, newer))
        assertEquals(listOf(newer.id, older.id), ids(sorted))
    }

    @Test
    fun sortReminders_dateless_incomplete_sorts_last_among_incomplete() {
        val now = System.currentTimeMillis()
        val withDate = Reminder("with date", "w", now + 5_000L, false, now)
        val noDate = Reminder("no date", "n", null, false, now)

        val sorted = ReminderRepository.sortReminders(listOf(noDate, withDate))
        assertEquals(listOf(withDate.id, noDate.id), ids(sorted))
    }

    @Test
    fun getAll_returns_sorted_reminders() = runBlocking {
        val r = repo()
        val completed = r.create("Completed item")
        r.toggleCompleted(completed.id)
        val soon = r.create("Soon item")
        // The incomplete "Soon item" must sort before the completed "Completed item".
        assertTrue(r.getAll().first().id == soon.id)
    }
}
