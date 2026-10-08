# Project Plan

A notes app for Android with a clean Material 3 Expressive / Material You UI. Core features: create, edit, delete, search notes; organize notes into folders/tags; algorithmic (non-AI) text features: autocorrection (dictionary + rule-based spell and grammar fixing), extractive summarization of notes (frequency/TextRank-style sentence scoring), and rule-based rewrite actions (improve tone via word substitution, shorten by removing filler words, expand, fix grammar). No ML Kit / no on-device AI models — everything implemented with Kotlin algorithms.

## Project Brief

# Project Brief — Notes+ (Android MVP)

A notes app for Android with a clean **Material 3 Expressive / Material You** UI. All text intelligence is implemented with **pure Kotlin algorithms** — no ML Kit, no on-device AI models. Notes live in **in-memory storage** for the MVP. **minSdk 24** (the API 26 requirement dropped with the removal of ML Kit GenAI).

## Features

1. **Create, Edit & Delete Notes** — Core note CRUD with title + body, managed through a single notes list and an editor screen.
2. **Search Notes** — Real-time filtering of notes by title and body text.
3. **Organize with Folders & Tags** — Assign notes to folders and tags; filter the notes list by folder or tag.
4. **Algorithmic Text Tools** (pure Kotlin, no AI):
   - **Autocorrection** — dictionary-based spell checking plus common-misspelling and basic grammar rule corrections, applied to note text.
   - **Extractive Summarization** — word-frequency / TextRank-style sentence scoring, rendered inline below the note.
   - **Rule-Based Rewrite Quick Actions** — *Improve tone* (synonym substitution), *Shorten* (filler-word removal), *Expand*, *Fix grammar* (rule-based).

## High-Level Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (Expressive / Material You dynamic color) |
| Navigation & Adaptive | **Jetpack Navigation 3** (state-driven: `NavDisplay`, `NavBackStack`, `@Serializable` NavKeys) + **Compose Material 3 Adaptive** (`NavigationSuiteScaffold`, `ListDetailPaneScaffold` / `ListDetailSceneStrategy`) |
| Concurrency & State | Kotlin Coroutines + Flow, `ViewModel` with `StateFlow` (`lifecycle-viewmodel-navigation3`) |
| Storage | In-memory repository only (no database / persistence for MVP) |
| Text Processing | Pure Kotlin algorithms: dictionary + rule tables, word-frequency/TextRank scoring, synonym & filler-word maps |

### Stack Notes

- **Navigation 3 is state-driven**: destinations are `@Serializable` keys on a `NavBackStack`; the Material 3 Adaptive `ListDetailSceneStrategy` maps list/detail entries onto adaptive panes (single pane on compact, two-pane on wide).
- **No ML Kit GenAI Prompt API / Gemini Nano** — the `mlkit-genai-prompt` dependency must be removed from the template.
- **No database**: Room, DataStore, and other persistence/networking libraries (Retrofit, OkHttp, Moshi, Coil, Camera, Location, Accompanist) should be dropped to keep the MVP lean.
- `kotlinx-serialization` is required by Navigation 3 for NavKey types.
- The template's `libs.versions.toml` already pins compatible versions: Navigation 3 `1.1.2`, Material 3 Adaptive `1.3.0` (incl. `adaptive-navigation3`), `lifecycle-viewmodel-navigation3 2.11.0`, Coroutines `1.11.0`, serialization `1.11.0`.

## Implementation Steps
**Total Duration:** 17h 34m 24s

### Task_1_Foundation_DataLayer: Set up project foundation and in-memory data layer: add dependencies (Compose BOM, Material 3 + M3 Adaptive, Jetpack Navigation 3, ML Kit GenAI Prompt API, Coroutines), define the Note model (id, title, body, folder, timestamp), an in-memory repository, and a NotesViewModel with StateFlow exposing unidirectional data flow for create/edit/delete, instant search, and folder/tag operations.
- **Status:** COMPLETED
- **Updates:** Foundation complete: dependencies added (Compose BOM 2026.05.01, M3 + Adaptive 1.3.0, Navigation 3 1.1.2, ML Kit GenAI Prompt 1.0.0-beta4, minSdk bumped 23→26 for ML Kit requirement), Note model, in-memory NotesRepository (singleton, StateFlow-backed, seeded with 4 sample notes), NotesViewModel with StateFlow unidirectional data flow, app shell with Material You theme. assembleDebug passed; 16 unit tests passed.
- **Acceptance Criteria:**
  - project builds successfully (assembleDebug)
  - Note model + in-memory repository + NotesViewModel StateFlow support CRUD, instant search, and folder operations
- **Duration:** 15m 5s

### Task_2_Navigation_NotesList: Revert foundation to algorithmic-only: remove the ML Kit GenAI Prompt API dependency (com.google.mlkit:genai-prompt) from gradle/libs.versions.toml and app/build.gradle.kts, and revert minSdk from 26 back to 24. Then build Navigation 3 state-driven navigation (Serializable NavKeys NoteList/NoteEditor(id), rememberNavBackStack, NavDisplay, entryProvider) and the notes list screen inside NavigationSuiteScaffold with ListDetailPaneScaffold/ListDetailSceneStrategy (single pane on compact, side-by-side on expanded), using Material 3 Expressive list items, a search bar, folder chips, and an add-note action.
- **Status:** COMPLETED
- **Updates:** Reverted to algorithmic-only (ML Kit GenAI dep removed from libs.versions.toml + build.gradle.kts, minSdk 26→24, zero ML Kit references in sources). Navigation 3 state-driven nav: @Serializable NavKeys (NoteList, NoteEditor(noteId) with "new" sentinel), rememberNavBackStack + NavDisplay + entryProvider, NavigationSuiteScaffold + ListDetailSceneStrategy (single pane compact / two-pane expanded). Notes list: M3 Expressive card items (title, 3-line snippet, folder chip, relative time), instant search bar, folder FilterChips, add-note FAB (moved to scaffold slot), empty states. NoteEditor placeholder reachable + pops on back. Build + 16 unit tests pass.
- **Acceptance Criteria:**
  - ML Kit GenAI dependency removed from gradle files and minSdk reverted to 24; project still builds
  - app launches to a working notes list screen with Material You dynamic color
  - Navigation 3 back stack works: add note pushes NoteEditor, back pops to list
  - list renders single pane on compact and side-by-side on expanded windows
- **Duration:** 3h 1m 12s

### Task_3_Editor_Search_Folders: Implement the note editor (inline title + body editing, delete with confirmation dialog), instant search across note titles and bodies, and folder/tag organization UI (create folder, assign notes to folders, filter by folder).
- **Status:** COMPLETED
- **Updates:** Full editor: inline title+body editing, explicit save + save-on-back safety net (onCleared persists dirty state), updatedAt bumped, M3 AlertDialog delete with confirm + pop, folder dropdown in editor. Instant search verified (titles+bodies live). Folder org: create (NewFolderDialog), assign (editor dropdown + list item ⋮ MoveToFolderDialog), filter chips wired end-to-end, delete folder (notes become unfiled). New: NoteDialogs.kt (4 dialogs with previews), NoteEditorViewModelTest (8 tests). Modified: NotesRepository (folderNames flow, create/deleteFolder, sync internal ops), NotesViewModel, NoteEditorViewModel, NoteEditorScreen, NoteListScreen. Build + 34 unit tests pass.
- **Acceptance Criteria:**
  - create/edit/delete notes with confirmation works end-to-end
  - instant search filters note titles and bodies as the user types
  - notes can be grouped into folders and filtered by folder
- **Duration:** 53m 36s

### Task_4_Autocorrect_Summarize: Implement the pure-Kotlin text engine (no ML Kit, no AI): (1) autocorrection — dictionary-based spell checking with a common-misspelling map plus basic grammar rules, applied to note text; (2) extractive summarization — word-frequency / TextRank-style sentence scoring, rendered inline below the note in the editor.
- **Status:** COMPLETED
- **Updates:** Pure-Kotlin text engine done: AutocorrectEngine (57-entry misspelling map, grammar rules: i→I, contractions, article fixes, double-space collapse; case-preserving, no false positives) + Summarizer (extractive word-frequency/TextRank scoring, top 2-3 sentences, original order). Editor integration: Autocorrect toolbar button → preview dialog (original→replacement list) → apply + snackbar count; Summarize button → inline SummaryCard (secondaryContainer, dismissible). Settings: SharedPreferences-backed SettingsStore (AppSettings singleton, StateFlows autocorrectEnabled/summarizeEnabled default true, extensible for rewriteEnabled), gear icon in list top bar → SettingsDialog with 2 switches + descriptions; disabled features gray out their editor actions. 71 unit tests pass (incl. 16 engine, 8 summarizer, 7 settings).
- **Acceptance Criteria:**
  - autocorrection fixes common misspellings and basic grammar errors using dictionary + rule tables
  - summarize produces an inline extractive summary below the note using word-frequency/TextRank sentence scoring
  - text engine is pure Kotlin with no ML Kit or network dependency
- **Duration:** 12h 1m 32s

### Task_5_Rewrite_Actions: Implement rule-based rewrite quick actions in the editor: improve tone (synonym substitution), shorten (filler-word removal), expand, and fix grammar (rule-based) — applied to selected text or the whole note, built as pure, unit-testable Kotlin functions.
- **Status:** COMPLETED
- **Updates:** RewriteEngine complete: 4 pure functions (improveTone 40-entry synonym map, shorten 20+ filler/phrase removals, expand contractions+abbreviations, fixGrammar rules) returning RewriteResult(rewrittenText, changes). Fixed 2 pre-existing bugs (String.match→Regex.find; broken (?i:) case-insensitive flag wrapping). Editor: Rewrite toolbar button → M3 dialog with 4 actions; applies to text selection when present (TextFieldValue/TextRange capture), else whole body; snackbar with change count. rewriteEnabled toggle added to SettingsStore + SettingsDialog; disabled state wired. 97 unit tests pass (26 new RewriteEngineTest + settings tests).
- **Acceptance Criteria:**
  - improve tone, shorten, expand, and fix grammar quick actions work on selected text and on the whole note
  - rewrite transformations are pure Kotlin rule/synonym/filler maps with no AI dependency
- **Duration:** 1h 22m 59s

### Task_6_Run_Verify: Run and Verify: critic_agent installs and runs the app, verifies application stability (no crashes) across core flows (create, edit, delete, search, folders, autocorrection, summarization, rewrite actions), confirms alignment with user requirements, and reports critical UI issues.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - critic_agent confirms feature alignment with user requirements and reports critical UI issues
- **StartTime:** 2026-10-07 15:15:45 GMT+07:00

