package com.nct32.notesplus.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level navigation key for the notes list. This is the root of the back stack.
 */
@Serializable
data object NoteList : NavKey

/**
 * Navigation key for the note editor.
 *
 * [noteId] is the id of the note to edit, or the [NEW] sentinel when creating a new note.
 */
@Serializable
data class NoteEditor(val noteId: String) : NavKey {

    companion object {
        /** Sentinel [noteId] used to indicate a brand-new note. */
        const val NEW: String = "new"
    }
}

/**
 * Top-level navigation key for the reminders list. This is a second root of the back stack,
 * alongside [NoteList], selected via the top-level navigation area.
 */
@Serializable
data object Reminders : NavKey

/**
 * Top-level navigation key for the settings screen. This is a third root of the back stack,
 * alongside [NoteList] and [Reminders], selected via the top-level navigation area.
 */
@Serializable
data object Settings : NavKey
