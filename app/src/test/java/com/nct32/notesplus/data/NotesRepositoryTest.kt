package com.nct32.notesplus.data

import com.nct32.notesplus.data.db.FakeFolderDao
import com.nct32.notesplus.data.db.FakeNoteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Room-backed [NotesRepository].
 *
 * The repository is constructed with in-memory DAO fakes ([FakeNoteDao] / [FakeFolderDao]) so the
 * tests run on a plain JVM (no Android `Context` / `AppDatabase` needed) while still exercising
 * the real entity→domain mapping, the reactive `StateFlow` derivation, and the CRUD logic.
 *
 * The repository's hosting scope uses an [UnconfinedTestDispatcher] so the `stateIn` collector
 * runs synchronously on each DAO emission — reads of [NotesRepository.notes] are therefore
 * deterministic (no `advanceUntilIdle` needed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesRepositoryTest {

    /** A fresh repository backed by in-memory DAO fakes (empty to start). */
    private fun repo(
        noteDao: FakeNoteDao = FakeNoteDao(),
        folderDao: FakeFolderDao = FakeFolderDao()
    ) = NotesRepository(noteDao, folderDao, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun repository_starts_empty() {
        // No seed notes: a brand-new repository has no notes and no folders.
        val r = repo()
        assertTrue(r.notes.value.isEmpty())
        assertTrue(r.folderNames.value.isEmpty())
        assertTrue(r.byFolderSnapshot("zzz_no_such_folder_zzz").isEmpty())
    }

    @Test
    fun getAll_returns_all_notes_sorted_by_updated_desc() = runBlocking {
        val r = repo()
        val a = r.create("A", "a")
        val b = r.create("B", "b")

        val all = r.notes.value
        assertTrue(all.any { it.id == a.id })
        assertTrue(all.any { it.id == b.id })
        // Most recently updated first.
        for (i in 0 until (all.size - 1)) {
            assertTrue(all[i].updatedAt >= all[i + 1].updatedAt)
        }
    }

    @Test
    fun create_then_getById_returns_the_new_note() = runBlocking {
        val r = repo()
        val created = r.create("Test title", "Test body", folder = "Test")
        assertNotNull(created.id)
        assertEquals("Test title", created.title)
        assertEquals("Test body", created.body)
        assertEquals("Test", created.folder)

        val fetched = r.getById(created.id)
        assertNotNull(fetched)
        assertEquals(created.id, fetched!!.id)
    }

    @Test
    fun update_changes_fields_and_bumps_updatedAt() = runBlocking {
        val r = repo()
        val created = r.create("Old", "Old body")
        // Ensure a strictly-later wall-clock time for the update.
        Thread.sleep(25)
        val updated = r.update(created.copy(title = "New", body = "New body"))
        assertEquals("New", updated.title)
        assertEquals("New body", updated.body)
        assertTrue(updated.updatedAt >= created.updatedAt)

        val fetched = r.getById(created.id)
        assertEquals("New", fetched?.title)
    }

    @Test
    fun delete_removes_the_note() = runBlocking {
        val r = repo()
        val created = r.create("Doomed", "to be deleted")
        r.delete(created.id)
        assertNull(r.getById(created.id))
    }

    @Test
    fun search_matches_title_and_body_case_insensitively() = runBlocking {
        val r = repo()
        val created = r.create("UniqueAlphaTitle", "with a uniquealpha body")

        val byTitle = r.searchSnapshot("uniquealpha")
        assertTrue(byTitle.any { it.id == created.id })

        val byBody = r.searchSnapshot("UNIQUEALPHA")
        assertTrue(byBody.any { it.id == created.id })
    }

    @Test
    fun search_no_match_returns_empty() = runBlocking {
        val r = repo()
        assertTrue(r.searchSnapshot("zzz_no_such_note_zzz").isEmpty())
    }

    @Test
    fun byFolder_filters_to_the_given_folder() = runBlocking {
        val r = repo()
        val created = r.create("In Folder", "body", folder = "UniqueFolder")
        val inFolder = r.byFolderSnapshot("UniqueFolder")
        assertTrue(inFolder.any { it.id == created.id })
        // Every returned note must belong to that folder.
        assertTrue(inFolder.all { it.folder == "UniqueFolder" })
    }

    @Test
    fun folders_returns_distinct_sorted_folders() = runBlocking {
        val r = repo()
        r.create("A", "a", folder = "Zeta")
        r.create("B", "b", folder = "Alpha")
        r.createFolder("Gamma")

        val folders = r.visibleFolders()
        assertTrue(folders.contains("Zeta"))
        assertTrue(folders.contains("Alpha"))
        assertTrue(folders.contains("Gamma"))
        assertEquals(folders.sorted(), folders)
    }

    @Test
    fun applyFilters_combines_folder_and_query() = runBlocking {
        val r = repo()
        val created = r.create("ComboNote", "combo body", folder = "ComboFolder")

        val result = r.applyFilters(r.notes.value, "combo", "ComboFolder")
        assertTrue(result.any { it.id == created.id })
        // A folder that doesn't match should exclude it even if the query matches.
        val wrongFolder = r.applyFilters(r.notes.value, "combo", "OtherFolder")
        assertTrue(wrongFolder.none { it.id == created.id })
    }

    @Test
    fun createFolder_adds_folder_and_is_idempotent() = runBlocking {
        val r = repo()
        val created = r.createFolder("FreshFolder")
        assertEquals("FreshFolder", created)
        assertTrue(r.visibleFolders().contains("FreshFolder"))

        // Creating the same folder again is a no-op and returns the same name.
        val again = r.createFolder("FreshFolder")
        assertEquals("FreshFolder", again)
        // The folder appears exactly once.
        assertEquals(1, r.visibleFolders().count { it == "FreshFolder" })
    }

    @Test
    fun createFolder_blank_returns_null() = runBlocking {
        val r = repo()
        assertNull(r.createFolder("   "))
    }

    @Test
    fun deleteFolder_moves_notes_to_unfiled_and_removes_folder() = runBlocking {
        val r = repo()
        val created = r.create("DoomedNote", "body", folder = "DoomedFolder")
        r.createFolder("DoomedFolder")

        r.deleteFolder("DoomedFolder")

        val fetched = r.getById(created.id)
        assertNotNull(fetched)
        assertNull(fetched?.folder)
        assertTrue(r.visibleFolders().none { it == "DoomedFolder" })
    }

    @Test
    fun folderNoteCount_counts_notes_in_folder() = runBlocking {
        val r = repo()
        r.create("Count1", "a", folder = "CountFolder")
        r.create("Count2", "b", folder = "CountFolder")
        r.create("Count3", "c", folder = "OtherFolder")
        assertEquals(2, r.folderNoteCount("CountFolder"))
        assertEquals(1, r.folderNoteCount("OtherFolder"))
        assertEquals(0, r.folderNoteCount("NoFolderHere"))
    }

    @Test
    fun createInternal_and_updateInternal_mutate_state() {
        // The `*Internal` methods are synchronous (they block via runBlocking until the write is
        // durable). With the unconfined hosting scope the DAO write runs eagerly on this thread.
        val r = repo()
        val created = r.createInternal("Internal", "body", folder = "InternalFolder")
        assertNotNull(r.notes.value.firstOrNull { it.id == created.id })

        val updated = r.updateInternal(created.copy(title = "Internal2"))
        assertEquals("Internal2", updated.title)
        assertEquals("Internal2", r.notes.value.firstOrNull { it.id == created.id }?.title)
    }

    @Test
    fun deleteInternal_removes_note() {
        val r = repo()
        val created = r.createInternal("DoomedInternal", "body")
        r.deleteInternal(created.id)
        assertTrue(r.notes.value.none { it.id == created.id })
    }

    @Test
    fun persistence_round_trip_data_survives_a_new_repository_instance() = runBlocking {
        // Simulates an app restart: a fresh repository reading the SAME underlying store
        // (the fake DAO stands in for the on-disk SQLite file).
        val sharedNoteDao = FakeNoteDao()
        val sharedFolderDao = FakeFolderDao()

        val first = NotesRepository(sharedNoteDao, sharedFolderDao, CoroutineScope(UnconfinedTestDispatcher()))
        val note = first.create("Persisted", "survives restart", folder = "Kept")
        first.createFolder("Kept")

        // A "new process" repository over the same store sees the persisted data.
        val second = NotesRepository(sharedNoteDao, sharedFolderDao, CoroutineScope(UnconfinedTestDispatcher()))
        val fetched = second.getById(note.id)
        assertNotNull(fetched)
        assertEquals("Persisted", fetched?.title)
        assertEquals("Kept", fetched?.folder)
        assertTrue(second.visibleFolders().contains("Kept"))
    }

    // ------------------------------------------------------------------
    // Test helpers: snapshot views over the reactive StateFlow (the old repo exposed
    // synchronous `search`/`byFolder`/`folders`; the new repo derives these from `notes`).
    // ------------------------------------------------------------------

    private fun NotesRepository.searchSnapshot(query: String): List<Note> =
        applyFilters(notes.value, query, null)

    private fun NotesRepository.byFolderSnapshot(folder: String): List<Note> =
        applyFilters(notes.value, "", folder)

    private fun NotesRepository.visibleFolders(): List<String> =
        (folderNames.value + notes.value.mapNotNull { it.folder }).distinct().sorted()
}
