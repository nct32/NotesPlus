package com.nct32.notesplus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
 * View model for the note editor screen, scoped to a single [NoteEditor] navigation key.
 *
 * A fresh [NoteEditorViewModel] is created for each editor entry (via
 * `rememberViewModelStoreNavEntryDecorator`), so editing one note never leaks state into another.
 *
 * For a [NoteEditor] carrying the [NoteEditor.NEW] sentinel, the editor starts with empty fields
 * and saving creates a new note. For a real id, the existing note is loaded and saving updates it.
 *
 * ## Save model: explicit save + safety net
 *
 * The primary save model is **explicit**: the user taps the check (or the back arrow) to persist.
 * The back arrow saves any unsaved changes before navigating away, so "back" never silently
 * discards work. As a safety net, [onCleared] also persists any dirty state synchronously — this
 * covers the case where the ViewModel is destroyed without the UI getting a chance to call
 * [save] (e.g. the user swipes the app away from recents while the editor is open).
 *
 * Notes that are completely empty (blank title AND blank body) are never persisted: creating a
 * note with nothing in it is a no-op, and clearing an existing note down to nothing deletes it.
 */
class NoteEditorViewModel(
    val noteKey: NoteEditor,
    private val repository: NotesRepository = NotesRepository.instance
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _title = MutableStateFlow("")
    private val _body = MutableStateFlow("")
    private val _folder = MutableStateFlow<String?>(null)
    private val _isSaved = MutableStateFlow(false)
    private val _isDirty = MutableStateFlow(false)

    /** `true` while the existing note (if any) is being loaded. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** The title being edited. */
    val title: StateFlow<String> = _title.asStateFlow()

    /** The body being edited. */
    val body: StateFlow<String> = _body.asStateFlow()

    /** The folder being edited (`null` = unfiled). */
    val folder: StateFlow<String?> = _folder.asStateFlow()

    /** `true` once the note has been saved at least once. */
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    /** `true` when the current fields differ from what was last persisted. */
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /**
     * Whether the autocorrect tool is enabled (persisted in [com.nct32.notesplus.settings.SettingsStore]).
     * The editor disables its autocorrect action when this is `false`.
     */
    val autocorrectEnabled: StateFlow<Boolean> = AppSettings.instance.autocorrectEnabled

    /**
     * Whether the summarize tool is enabled (persisted in [com.nct32.notesplus.settings.SettingsStore]).
     * The editor disables its summarize action when this is `false`.
     */
    val summarizeEnabled: StateFlow<Boolean> = AppSettings.instance.summarizeEnabled

    /**
     * Whether the rewrite quick actions are enabled (persisted in
     * [com.nct32.notesplus.settings.SettingsStore]). The editor disables its rewrite action
     * when this is `false`.
     */
    val rewriteEnabled: StateFlow<Boolean> = AppSettings.instance.rewriteEnabled

    /**
     * The folders the user can pick from in the editor: every explicitly created folder plus
     * every folder currently referenced by a note.
     */
    val folders: StateFlow<List<String>> = combine(
        repository.notes,
        repository.folderNames
    ) { notes, created ->
        (created + notes.mapNotNull { it.folder }).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The id of the note currently being edited (null until saved for a new note). */
    private var currentId: String? = null

    /** The original creation timestamp, preserved across updates. */
    private var currentCreatedAt: Long = 0L

    init {
        // Mark as loading synchronously (before the async load begins) so observers can reliably
        // wait for the load to complete. For NEW notes there is nothing to load, so the flag stays
        // false from the start.
        if (noteKey.noteId != NoteEditor.NEW) {
            _isLoading.value = true
        }
        viewModelScope.launch {
            val existing = if (noteKey.noteId == NoteEditor.NEW) null else repository.getById(noteKey.noteId)
            if (existing != null) {
                currentId = existing.id
                currentCreatedAt = existing.createdAt
                _title.value = existing.title
                _body.value = existing.body
                _folder.value = existing.folder
                _isSaved.value = true
            }
            _isLoading.value = false
        }
    }

    fun onTitleChange(value: String) {
        // Idempotent: only mark dirty when the value actually changes. This lets the editor seed
        // the fields from the loaded note (and re-apply programmatic edits) without falsely
        // flagging an untouched note as having unsaved changes.
        if (_title.value != value) {
            _title.value = value
            _isDirty.value = true
        }
    }

    fun onBodyChange(value: String) {
        // Idempotent: only mark dirty when the value actually changes. See [onTitleChange].
        if (_body.value != value) {
            _body.value = value
            _isDirty.value = true
        }
    }

    fun onFolderChange(value: String?) {
        _folder.value = value
        _isDirty.value = true
    }

    /**
     * Persists the current fields **synchronously** so the repository is updated before the
     * caller continues (e.g. before popping the back stack).
     *
     * - New note (unsaved): creates the note, unless title and body are both blank (no-op).
     * - Existing note: updates it, bumping [Note.updatedAt]. Clearing an existing note to a
     *   blank title AND blank body keeps the note (as empty) rather than deleting it — deletion
     *   is always an explicit, confirmed action via [delete].
     *
     * Returns `true` if the note exists in the repository afterwards.
     */
    fun save(): Boolean {
        val title = _title.value.trim()
        val body = _body.value.trim()
        val folder = _folder.value?.trim()?.takeIf { it.isNotEmpty() }
        val id = currentId
        return if (id == null) {
            if (title.isEmpty() && body.isEmpty()) {
                // Nothing to save for a brand-new, empty note.
                false
            } else {
                val created = repository.createInternal(title, body, folder)
                currentId = created.id
                currentCreatedAt = created.createdAt
                _isSaved.value = true
                _isDirty.value = false
                true
            }
        } else {
            repository.updateInternal(
                Note(
                    id = id,
                    title = title,
                    body = body,
                    folder = folder,
                    createdAt = currentCreatedAt,
                    updatedAt = 0L
                )
            )
            _isDirty.value = false
            true
        }
    }

    /**
     * Persists unsaved changes if there are any. Called from the back arrow so navigation never
     * silently discards the user's edits.
     */
    fun saveIfDirty() {
        if (_isDirty.value) save()
    }

    /**
     * Deletes the currently-edited note (if it exists) and marks the editor as no longer
     * backed by a repository note. Returns `true` if a note was actually deleted.
     */
    fun delete(): Boolean {
        val id = currentId ?: return false
        repository.deleteInternal(id)
        currentId = null
        _isSaved.value = false
        _isDirty.value = false
        return true
    }

    /**
     * Safety net: if the ViewModel is cleared with unsaved changes (e.g. the user swipes the app
     * away from recents), persist them synchronously so no work is lost.
     */
    override fun onCleared() {
        if (_isDirty.value) save()
    }

    class Factory(
        private val noteKey: NoteEditor,
        private val repository: NotesRepository = NotesRepository.instance
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(noteKey, repository) as T
    }
}
