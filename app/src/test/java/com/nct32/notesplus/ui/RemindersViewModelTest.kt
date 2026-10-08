package com.nct32.notesplus.ui

import com.nct32.notesplus.data.ReminderRepository
import com.nct32.notesplus.data.db.FakeReminderDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * A repository backed by an in-memory DAO fake (no Android `Context` / `AppDatabase`
     * needed). The hosting scope uses an [UnconfinedTestDispatcher] so the repository's reactive
     * `StateFlow` updates synchronously on each DAO write.
     */
    private val repository = ReminderRepository(
        FakeReminderDao(),
        CoroutineScope(UnconfinedTestDispatcher())
    )
    private lateinit var viewModel: RemindersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = RemindersViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addReminder_adds_to_reminders() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        viewModel.addReminder("BrandNewReminder")
        advanceUntilIdle()

        assertTrue(viewModel.reminders.value.any { it.title == "BrandNewReminder" })
        collector.cancel()
    }

    @Test
    fun addReminder_with_due_at_stores_it() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()
        val dueAt = System.currentTimeMillis() + 3_600_000L

        viewModel.addReminder("WithDue", dueAt)
        advanceUntilIdle()

        val added = viewModel.reminders.value.first { it.title == "WithDue" }
        assertEquals(dueAt, added.dueAt)
        collector.cancel()
    }

    @Test
    fun updateReminder_updates_title_and_due_at() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        val created = repository.create("Old title")
        advanceUntilIdle()
        val newDueAt = System.currentTimeMillis() + 3_600_000L

        viewModel.updateReminder(created.id, "New title", newDueAt)
        advanceUntilIdle()

        val updated = viewModel.reminders.value.first { it.id == created.id }
        assertEquals("New title", updated.title)
        assertEquals(newDueAt, updated.dueAt)
        collector.cancel()
    }

    @Test
    fun updateReminder_preserves_completed_state() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        val created = repository.create("To complete")
        repository.toggleCompleted(created.id)
        advanceUntilIdle()
        assertTrue(viewModel.reminders.value.first { it.id == created.id }.completed)

        viewModel.updateReminder(created.id, "Edited")
        advanceUntilIdle()

        val updated = viewModel.reminders.value.first { it.id == created.id }
        assertEquals("Edited", updated.title)
        assertTrue(updated.completed)
        collector.cancel()
    }

    @Test
    fun toggleReminder_flips_completed() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        val created = repository.create("ToggleReminder")
        advanceUntilIdle()

        viewModel.toggleReminder(created.id)
        advanceUntilIdle()
        assertTrue(viewModel.reminders.value.first { it.id == created.id }.completed)

        viewModel.toggleReminder(created.id)
        advanceUntilIdle()
        assertFalse(viewModel.reminders.value.first { it.id == created.id }.completed)
        collector.cancel()
    }

    @Test
    fun deleteReminder_removes_it() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        val created = repository.create("ToDeleteReminder")
        advanceUntilIdle()

        viewModel.deleteReminder(created.id)
        advanceUntilIdle()
        assertTrue(viewModel.reminders.value.none { it.id == created.id })
        collector.cancel()
    }

    @Test
    fun reminders_are_sorted_incomplete_first_then_completed() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.reminders.collect {} }
        advanceUntilIdle()

        val completed = repository.create("CompletedReminder")
        repository.toggleCompleted(completed.id)
        val incomplete = repository.create("IncompleteReminder")
        advanceUntilIdle()

        val list = viewModel.reminders.value
        // The incomplete reminder must appear before the completed one.
        val incompleteIndex = list.indexOfFirst { it.id == incomplete.id }
        val completedIndex = list.indexOfFirst { it.id == completed.id }
        assertTrue(incompleteIndex >= 0)
        assertTrue(completedIndex >= 0)
        assertTrue(incompleteIndex < completedIndex)
        collector.cancel()
    }
}
