package com.nct32.notesplus.data

import com.nct32.notesplus.data.db.AppDatabase
import com.nct32.notesplus.data.db.FolderDao
import com.nct32.notesplus.data.db.FolderEntity
import com.nct32.notesplus.data.db.NoteDao
import com.nct32.notesplus.data.db.toDomain
import com.nct32.notesplus.data.db.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Persistent data source for [Note]s and folders, backed by Room (SQLite in the app's
 * **internal** storage).
 *
 * The source of truth is the `notes` and `folders` tables. [notes] and [folderNames] are
 * [StateFlow]s derived from the DAOs' reactive `Flow`s, so the UI updates reactively whenever a
 * row changes. All writes are durable on disk, so notes and folders survive app kills and phone
 * restarts (no storage permissions required — the DB lives in the app's private directory).
 *
 * ## Two write paths
 *
 * - **Reactive / async writes** ([create], [update], [delete], [createFolder], [deleteFolder]):
 *   `suspend` functions intended to be called from a `viewModelScope` (they run on a background
 *   thread). Used by the notes list screen.
 * - **Synchronous durable writes** ([createInternal], [updateInternal], [deleteInternal]):
 *   plain (blocking) functions that write through [runBlocking] so the write is guaranteed to be
 *   durable *before* the function returns. Used by the note editor's save-on-back / swipe-away
 *   safety net, where an async write could be cancelled (ViewModel cleared / process killed)
 *   before it completes — i.e. data loss.
 *
 * [NotesRepository] is constructed with its DAOs (dependency-injected) so it can be unit-tested
 * with in-memory fakes. The process-wide shared instance is exposed via [instance], built from
 * [AppDatabase.db] (initialized in `MainActivity.onCreate`).
 */
class NotesRepository(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    /** Hosts the `stateIn` collectors for the derived flows. */
    private val scope: CoroutineScope
) {

    /**
     * The full list of notes, sourced from the `notes` table (most recently updated first).
     *
     * Shared with [SharingStarted.Eagerly]: the repository is a process-wide singleton and this
     * is the source of truth for the UI, so the derived flow is kept warm for the process
     * lifetime. That also makes [folderNoteCount] (a synchronous read of [notes]) reliable
     * regardless of whether a UI subscriber is currently active.
     */
    val notes: StateFlow<List<Note>> = noteDao.getAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The explicitly created folder names, sourced from the `folders` table. */
    val folderNames: StateFlow<List<String>> = folderDao.getAll()
        .map { list -> list.map { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ------------------------------------------------------------------
    // Synchronous helpers over the in-memory snapshot (cheap, no DB I/O).
    // ------------------------------------------------------------------

    /**
     * Applies the instant-search [query] (case-insensitive, matches title or body) and the
     * [folder] filter (`null` = all folders) to the given [notes]. Pure function — no DB I/O.
     */
    fun applyFilters(notes: List<Note>, query: String, folder: String?): List<Note> {
        val q = query.trim().lowercase()
        return notes.filter { note ->
            val matchesFolder = folder == null || note.folder == folder
            val matchesQuery = q.isEmpty() ||
                note.title.lowercase().contains(q) ||
                note.body.lowercase().contains(q)
            matchesFolder && matchesQuery
        }
    }

    /** How many notes are currently assigned to [folder] (from the in-memory snapshot). */
    fun folderNoteCount(folder: String): Int = notes.value.count { it.folder == folder }

    // ------------------------------------------------------------------
    // Reactive / async CRUD (call from a viewModelScope).
    // ------------------------------------------------------------------

    /** Creates a new note and returns it. [folder] may be `null` for unfiled. */
    suspend fun create(title: String, body: String, folder: String? = null): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            folder = folder,
            createdAt = now,
            updatedAt = now
        )
        noteDao.upsert(note.toEntity())
        return note
    }

    /**
     * Updates the note with [note.id], preserving its [Note.createdAt] and bumping
     * [Note.updatedAt]. Returns the updated note. No-op if the id is unknown.
     */
    suspend fun update(note: Note): Note {
        val existing = noteDao.getById(note.id) ?: return note
        val updated = note.copy(
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        noteDao.upsert(updated.toEntity())
        return updated
    }

    /** Deletes the note with [id]. No-op if the id is unknown. */
    suspend fun delete(id: String) = noteDao.deleteById(id)

    /** Returns the note with [id], or `null` if it does not exist. */
    suspend fun getById(id: String): Note? = noteDao.getById(id)?.toDomain()

    // ------------------------------------------------------------------
    // Synchronous durable writes (editor save-on-back / swipe-away safety net).
    //
    // These block (via [runBlocking]) until the SQLite write is durable. A single small write is
    // sub-millisecond, so the main-thread block on save/back/swipe-away is negligible, and it
    // guarantees no data loss when the ViewModel is cleared or the app is swiped away.
    // ------------------------------------------------------------------

    /**
     * Creates a new note and **synchronously** writes it to the database (blocking until durable).
     * Used by the editor so a save is never lost when the ViewModel is cleared or the app is
     * swiped away.
     */
    fun createInternal(title: String, body: String, folder: String? = null): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            folder = folder,
            createdAt = now,
            updatedAt = now
        )
        runBlocking { noteDao.upsert(note.toEntity()) }
        return note
    }

    /**
     * Updates the note with [note.id], **synchronously** writing to the database (blocking until
     * durable). Preserves [Note.createdAt] and bumps [Note.updatedAt].
     */
    fun updateInternal(note: Note): Note {
        val updated = note.copy(updatedAt = System.currentTimeMillis())
        runBlocking { noteDao.upsert(updated.toEntity()) }
        return updated
    }

    /** Deletes the note with [id], **synchronously** (blocking until durable). */
    fun deleteInternal(id: String) {
        runBlocking { noteDao.deleteById(id) }
    }

    // ------------------------------------------------------------------
    // Folders.
    // ------------------------------------------------------------------

    /**
     * Creates a folder with the trimmed [name]. Returns the trimmed name, or `null` if the name
     * is blank (nothing created). Idempotent (re-creating an existing folder is a no-op).
     */
    suspend fun createFolder(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        folderDao.upsert(FolderEntity(trimmed))
        return trimmed
    }

    /**
     * Deletes the folder [name] and moves all of its notes to unfiled (folder = null).
     */
    suspend fun deleteFolder(name: String) {
        noteDao.unfileAllInFolder(name)
        folderDao.deleteByName(name)
    }

    companion object {
        /**
         * Shared instance so state survives ViewModel recreation. Built from the shared
         * [AppDatabase]; the main dispatcher hosts the derived-flow collectors.
         */
        val instance: NotesRepository by lazy {
            val db = AppDatabase.db
            NotesRepository(db.noteDao(), db.folderDao(), CoroutineScope(Dispatchers.Main))
        }
    }
}
