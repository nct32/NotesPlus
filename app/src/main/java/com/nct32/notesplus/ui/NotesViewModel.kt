package com.nct32.notesplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nct32.notesplus.data.Note
import com.nct32.notesplus.data.NotesRepository
import com.nct32.notesplus.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Layout modes for the notes list screen.
 *
 * - [List]: single column of expressive cards (the default).
 * - [Grid]: two-column grid of compact cards.
 * - [Custom]: grid with [columns] columns (1..10), chosen by the user.
 */
sealed interface NoteListLayout {
    data object List : NoteListLayout
    data object Grid : NoteListLayout
    data class Custom(val columns: Int) : NoteListLayout
}

/**
 * Single source of truth for the notes UI, exposing [StateFlow]s for unidirectional data flow.
 *
 * Supports CRUD, instant search (via [searchQuery]), folder filtering (via [selectedFolder]),
 * and list layout switching (via [layout]). The visible list is derived by combining the
 * repository's notes with the current search and folder state, so it updates reactively as any
 * of them change.
 */
class NotesViewModel(
    private val repository: NotesRepository = NotesRepository.instance
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    /** Current instant-search query. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    /** Currently selected folder filter. `null` means "all folders". */
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _layout = MutableStateFlow<NoteListLayout>(NoteListLayout.List)
    /**
     * Current list layout mode. Kept in memory only (MVP scope: not persisted across launches).
     */
    val layout: StateFlow<NoteListLayout> = _layout.asStateFlow()

    /**
     * Whether the autocorrect tool is enabled (persisted in [com.nct32.notesplus.settings.SettingsStore]).
     * The editor hides/disables its autocorrect action when this is `false`.
     */
    val autocorrectEnabled: StateFlow<Boolean> = AppSettings.instance.autocorrectEnabled

    /**
     * Whether the summarize tool is enabled (persisted in [com.nct32.notesplus.settings.SettingsStore]).
     * The editor hides/disables its summarize action when this is `false`.
     */
    val summarizeEnabled: StateFlow<Boolean> = AppSettings.instance.summarizeEnabled

    /**
     * Whether the rewrite quick actions are enabled (persisted in
     * [com.nct32.notesplus.settings.SettingsStore]). The editor disables its rewrite action
     * when this is `false`.
     */
    val rewriteEnabled: StateFlow<Boolean> = AppSettings.instance.rewriteEnabled

    /** Enables/disables the autocorrect tool (persisted). */
    fun setAutocorrectEnabled(enabled: Boolean) {
        AppSettings.instance.setAutocorrectEnabled(enabled)
    }

    /** Enables/disables the summarize tool (persisted). */
    fun setSummarizeEnabled(enabled: Boolean) {
        AppSettings.instance.setSummarizeEnabled(enabled)
    }

    /** Enables/disables the rewrite quick actions (persisted). */
    fun setRewriteEnabled(enabled: Boolean) {
        AppSettings.instance.setRewriteEnabled(enabled)
    }

    /**
     * Whether the first-launch welcome banner has been dismissed (persisted in
     * [com.nct32.notesplus.settings.SettingsStore]). The banner is shown only while this is
     * `false`, i.e. on the first launch ever.
     */
    val welcomeDismissed: StateFlow<Boolean> = AppSettings.instance.welcomeDismissed

    /** Dismisses the first-launch welcome banner (persisted — it never shows again). */
    fun dismissWelcome() {
        AppSettings.instance.setWelcomeDismissed(true)
    }

    /** Last column count chosen in the custom layout, so it is preserved when switching back. */
    private var lastCustomColumns = 2

    /**
     * Number of columns for the current layout: 1 for [NoteListLayout.List], 2 for
     * [NoteListLayout.Grid], and the user-chosen count for [NoteListLayout.Custom].
     */
    val gridColumns: Int
        get() = when (val current = _layout.value) {
            NoteListLayout.List -> 1
            NoteListLayout.Grid -> 2
            is NoteListLayout.Custom -> current.columns
        }

    /**
     * Distinct, sorted list of folders. Combines explicitly created folders (which may have no
     * notes yet) with folders referenced by notes, so a freshly created folder shows up
     * immediately even before any note is assigned to it.
     */
    val folders: StateFlow<List<String>> = combine(
        repository.notes,
        repository.folderNames
    ) { notes, created ->
        (created + notes.mapNotNull { it.folder }).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notes after applying the current search query and folder filter. */
    val visibleNotes: StateFlow<List<Note>> = combine(
        repository.notes,
        _searchQuery,
        _selectedFolder
    ) { notes, query, folder ->
        repository.applyFilters(notes, query, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sets the instant-search query. */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Selects a folder filter. Pass `null` to show all folders. */
    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    /** Switches the list layout. */
    fun setLayout(layout: NoteListLayout) {
        if (layout is NoteListLayout.Custom) lastCustomColumns = layout.columns
        _layout.value = layout
    }

    /**
     * Switches to the custom layout, preserving the previously chosen column count (defaults
     * to 2 when there is none yet).
     */
    fun selectCustomLayout() {
        _layout.value = when (val current = _layout.value) {
            is NoteListLayout.Custom -> current
            else -> NoteListLayout.Custom(lastCustomColumns)
        }
    }

    /** Sets the number of notes per row for the custom layout (clamped to 1..10). */
    fun setCustomColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 10)
        lastCustomColumns = clamped
        _layout.value = NoteListLayout.Custom(clamped)
    }

    /** Creates a new note. */
    fun createNote(title: String, body: String, folder: String? = null) {
        viewModelScope.launch {
            repository.create(title, body, folder)
        }
    }

    /** Updates an existing note. */
    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.update(note)
        }
    }

    /** Deletes the note with the given [id]. */
    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    /**
     * Moves [note] into [folder] (or unfiles it when [folder] is null) by updating the
     * repository. Used by the list item's "Move to folder" action.
     */
    fun moveNoteToFolder(note: Note, folder: String?) {
        viewModelScope.launch {
            repository.update(note.copy(folder = folder))
        }
    }

    /**
     * Creates a new folder with the given [name]. Returns the trimmed name that was created,
     * or `null` if the name was blank (nothing created).
     */
    suspend fun createFolder(name: String): String? = repository.createFolder(name)

    /**
     * Deletes the folder [name], moving all of its notes to unfiled. If [name] is the currently
     * selected filter, the filter is reset to "all folders".
     */
    fun deleteFolder(name: String) {
        viewModelScope.launch {
            repository.deleteFolder(name)
            if (_selectedFolder.value == name) {
                _selectedFolder.value = null
            }
        }
    }

    /** Returns how many notes are currently assigned to [folder]. */
    fun folderNoteCount(folder: String): Int = repository.folderNoteCount(folder)
}
