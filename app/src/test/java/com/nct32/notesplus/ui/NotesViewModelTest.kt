package com.nct32.notesplus.ui

import com.nct32.notesplus.data.NotesRepository
import com.nct32.notesplus.data.db.FakeFolderDao
import com.nct32.notesplus.data.db.FakeNoteDao
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

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
    private lateinit var viewModel: NotesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = NotesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun visibleNotes_reflects_instant_search() = runTest(dispatcher) {
        // `visibleNotes` is shared with WhileSubscribed, so an active collector is required
        // (the UI provides this via collectAsStateWithLifecycle).
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()

        // The repository starts empty, so seed a note to search against.
        repository.create("SearchableNote", "searchable body")
        advanceUntilIdle()
        assertTrue(viewModel.visibleNotes.value.isNotEmpty())

        viewModel.setSearchQuery("zzz_no_such_note_zzz")
        advanceUntilIdle()
        assertTrue(viewModel.visibleNotes.value.isEmpty())

        viewModel.setSearchQuery("")
        advanceUntilIdle()
        assertTrue(viewModel.visibleNotes.value.isNotEmpty())
        collector.cancel()
    }

    @Test
    fun selectFolder_filters_visible_notes() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        val before = viewModel.visibleNotes.value.size

        viewModel.selectFolder("zzz_no_such_folder_zzz")
        advanceUntilIdle()
        assertEquals(0, viewModel.visibleNotes.value.size)

        viewModel.selectFolder(null)
        advanceUntilIdle()
        assertEquals(before, viewModel.visibleNotes.value.size)
        collector.cancel()
    }

    @Test
    fun createNote_adds_to_visible_notes() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        val before = viewModel.visibleNotes.value.size

        viewModel.createNote("BrandNewNote", "brand new body")
        advanceUntilIdle()
        assertEquals(before + 1, viewModel.visibleNotes.value.size)
        assertTrue(viewModel.visibleNotes.value.any { it.title == "BrandNewNote" })
        collector.cancel()
    }

    @Test
    fun deleteNote_removes_from_visible_notes() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        val created = repository.create("ToDelete", "body")
        advanceUntilIdle()

        viewModel.deleteNote(created.id)
        advanceUntilIdle()
        assertTrue(viewModel.visibleNotes.value.none { it.id == created.id })
        collector.cancel()
    }

    @Test
    fun folders_stateflow_exposes_folder_names() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.folders.collect {} }
        advanceUntilIdle()

        // The repository starts empty, so create a note in a folder first.
        repository.create("FolderNote", "body", folder = "ExposedFolder")
        advanceUntilIdle()
        assertTrue(viewModel.folders.value.isNotEmpty())
        assertTrue(viewModel.folders.value.contains("ExposedFolder"))
        collector.cancel()
    }

    @Test
    fun createFolder_adds_folder_even_without_notes() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.folders.collect {} }
        advanceUntilIdle()

        val created = viewModel.createFolder("BrandNewFolder")
        advanceUntilIdle()
        assertEquals("BrandNewFolder", created)
        assertTrue(viewModel.folders.value.contains("BrandNewFolder"))
        collector.cancel()
    }

    @Test
    fun createFolder_blank_name_returns_null() = runTest(dispatcher) {
        val created = viewModel.createFolder("   ")
        advanceUntilIdle()
        assertNull(created)
    }

    @Test
    fun deleteFolder_moves_notes_to_unfiled() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        val created = repository.create("InFolder", "body", folder = "DoomedFolder")
        advanceUntilIdle()

        viewModel.deleteFolder("DoomedFolder")
        advanceUntilIdle()

        val fetched = repository.getById(created.id)
        assertNotNull(fetched)
        assertNull(fetched?.folder)
        // The folder is gone from the folders list.
        assertTrue(viewModel.folders.value.none { it == "DoomedFolder" })
        collector.cancel()
    }

    @Test
    fun deleteFolder_resets_selected_folder_filter() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        repository.create("InFolder", "body", folder = "DoomedFolder")
        advanceUntilIdle()

        viewModel.selectFolder("DoomedFolder")
        advanceUntilIdle()
        assertEquals("DoomedFolder", viewModel.selectedFolder.value)

        viewModel.deleteFolder("DoomedFolder")
        advanceUntilIdle()
        assertNull(viewModel.selectedFolder.value)
        collector.cancel()
    }

    @Test
    fun moveNoteToFolder_updates_note_folder() = runTest(dispatcher) {
        val collector: Job = launch { viewModel.visibleNotes.collect {} }
        advanceUntilIdle()
        val created = repository.create("Movable", "body", folder = "OldFolder")
        advanceUntilIdle()

        viewModel.moveNoteToFolder(created, "NewFolder")
        advanceUntilIdle()
        assertEquals("NewFolder", repository.getById(created.id)?.folder)

        viewModel.moveNoteToFolder(created, null)
        advanceUntilIdle()
        assertNull(repository.getById(created.id)?.folder)
        collector.cancel()
    }

    @Test
    fun layout_defaults_to_list() {
        assertEquals(NoteListLayout.List, viewModel.layout.value)
        assertEquals(1, viewModel.gridColumns)
    }

    @Test
    fun setLayout_updates_layout_and_grid_columns() {
        viewModel.setLayout(NoteListLayout.Grid)
        assertEquals(NoteListLayout.Grid, viewModel.layout.value)
        assertEquals(2, viewModel.gridColumns)

        viewModel.setLayout(NoteListLayout.Custom(4))
        assertEquals(NoteListLayout.Custom(4), viewModel.layout.value)
        assertEquals(4, viewModel.gridColumns)

        viewModel.setLayout(NoteListLayout.List)
        assertEquals(NoteListLayout.List, viewModel.layout.value)
        assertEquals(1, viewModel.gridColumns)
    }

    @Test
    fun setCustomColumns_clamps_to_1_through_10() {
        viewModel.setLayout(NoteListLayout.Custom(5))

        viewModel.setCustomColumns(0)
        assertEquals(NoteListLayout.Custom(1), viewModel.layout.value)

        viewModel.setCustomColumns(99)
        assertEquals(NoteListLayout.Custom(10), viewModel.layout.value)

        viewModel.setCustomColumns(7)
        assertEquals(NoteListLayout.Custom(7), viewModel.layout.value)
        assertEquals(7, viewModel.gridColumns)
    }

    @Test
    fun selectCustomLayout_preserves_previous_column_count() {
        viewModel.selectCustomLayout()
        assertEquals(NoteListLayout.Custom(2), viewModel.layout.value)

        viewModel.setCustomColumns(6)
        viewModel.setLayout(NoteListLayout.List)
        viewModel.selectCustomLayout()
        assertEquals(NoteListLayout.Custom(6), viewModel.layout.value)
    }

    @Test
    fun settings_toggles_default_to_enabled() {
        assertTrue(viewModel.autocorrectEnabled.value)
        assertTrue(viewModel.summarizeEnabled.value)
    }

    @Test
    fun settings_toggles_update_stateflow_and_persist() {
        viewModel.setAutocorrectEnabled(false)
        viewModel.setSummarizeEnabled(false)
        assertFalse(viewModel.autocorrectEnabled.value)
        assertFalse(viewModel.summarizeEnabled.value)

        // A fresh ViewModel (e.g. after process restart) sees the persisted values.
        val fresh = NotesViewModel(repository)
        assertFalse(fresh.autocorrectEnabled.value)
        assertFalse(fresh.summarizeEnabled.value)

        // Restore defaults so other tests are unaffected.
        viewModel.setAutocorrectEnabled(true)
        viewModel.setSummarizeEnabled(true)
    }
}
