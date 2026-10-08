package com.nct32.notesplus.ui

import com.nct32.notesplus.data.NotesRepository
import com.nct32.notesplus.data.db.FakeFolderDao
import com.nct32.notesplus.data.db.FakeNoteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * A repository backed by in-memory DAO fakes (no Android `Context` / `AppDatabase` needed).
     * The hosting scope uses an [UnconfinedTestDispatcher] so the repository's reactive
     * `StateFlow`s update synchronously on each DAO write.
     */
    private val repository = NotesRepository(
        FakeNoteDao(),
        FakeFolderDao(),
        CoroutineScope(UnconfinedTestDispatcher())
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun new_note_save_creates_note_with_folder() = runTest(dispatcher) {
        val vm = NoteEditorViewModel(NoteEditor(NoteEditor.NEW), repository)
        advanceUntilIdle()

        vm.onTitleChange("My new note")
        vm.onBodyChange("body text")
        vm.onFolderChange("Work")
        assertTrue(vm.isDirty.value)

        val saved = vm.save()
        assertTrue(saved)
        assertTrue(vm.isSaved.value)
        assertFalse(vm.isDirty.value)

        val all = repository.notes.value
        assertTrue(
            all.any { it.title == "My new note" && it.body == "body text" && it.folder == "Work" }
        )
    }

    @Test
    fun new_note_empty_save_is_noop() = runTest(dispatcher) {
        val vm = NoteEditorViewModel(NoteEditor(NoteEditor.NEW), repository)
        advanceUntilIdle()

        vm.onTitleChange("   ")
        vm.onBodyChange("")
        val saved = vm.save()
        assertFalse(saved)
        assertFalse(vm.isSaved.value)
    }

    @Test
    fun existing_note_save_updates_and_bumps_updatedAt() = runTest(dispatcher) {
        val created = repository.create("Orig", "orig body", folder = "Work")
        val vm = NoteEditorViewModel(NoteEditor(created.id), repository)
        advanceUntilIdle()
        assertEquals("Orig", vm.title.value)
        assertEquals("Work", vm.folder.value)

        vm.onTitleChange("Updated")
        assertTrue(vm.isDirty.value)
        val saved = vm.save()
        assertTrue(saved)

        val fetched = repository.getById(created.id)
        assertEquals("Updated", fetched?.title)
        assertTrue(fetched!!.updatedAt >= created.updatedAt)
    }

    @Test
    fun existing_note_cleared_to_blank_keeps_note_as_empty() = runTest(dispatcher) {
        val created = repository.create("ToClear", "body")
        val vm = NoteEditorViewModel(NoteEditor(created.id), repository)
        advanceUntilIdle()

        vm.onTitleChange("")
        vm.onBodyChange("")
        val saved = vm.save()
        // Saving keeps the note (as empty) rather than deleting it — deletion is explicit.
        assertTrue(saved)
        assertTrue(vm.isSaved.value)
        val fetched = repository.getById(created.id)
        assertNotNull(fetched)
        assertEquals("", fetched?.title)
        assertEquals("", fetched?.body)
    }

    @Test
    fun delete_removes_note_and_resets_state() = runTest(dispatcher) {
        val created = repository.create("ToDelete", "body")
        val vm = NoteEditorViewModel(NoteEditor(created.id), repository)
        advanceUntilIdle()

        assertTrue(vm.delete())
        assertNull(repository.getById(created.id))
        assertFalse(vm.isSaved.value)
        assertFalse(vm.isDirty.value)
    }

    @Test
    fun saveIfDirty_persists_dirty_state_as_safety_net() = runTest(dispatcher) {
        // `onCleared()` delegates to `saveIfDirty()`, so this exercises the same safety-net
        // path that runs when the ViewModel is cleared with unsaved changes.
        val vm = NoteEditorViewModel(NoteEditor(NoteEditor.NEW), repository)
        advanceUntilIdle()

        vm.onTitleChange("Saved on clear")
        vm.onBodyChange("body")
        assertTrue(vm.isDirty.value)

        vm.saveIfDirty()

        val all = repository.notes.value
        assertTrue(all.any { it.title == "Saved on clear" })
    }

    @Test
    fun saveIfDirty_only_saves_when_dirty() = runTest(dispatcher) {
        val created = repository.create("Stable", "body")
        val vm = NoteEditorViewModel(NoteEditor(created.id), repository)
        advanceUntilIdle()

        // Not dirty yet: saveIfDirty should be a no-op.
        vm.saveIfDirty()
        assertEquals("Stable", repository.getById(created.id)?.title)

        // Now dirty: saveIfDirty should persist.
        vm.onTitleChange("Changed")
        vm.saveIfDirty()
        assertEquals("Changed", repository.getById(created.id)?.title)
    }
}
