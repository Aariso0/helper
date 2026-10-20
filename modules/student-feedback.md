---
module: student-feedback
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["feedback-screen", "quick-note-screen", "feedback-timeline"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
---

# Student feedback

Quick feedback notes per student with teacher-editable preset tags and an optional one-line text. Search and filter by student, tag, and date; teacher-only in the prototype.

## # In Use
- **Material 3 chips + TextField + Snackbar** — FilterChips for preset tags, one-line input, undo snackbar; all official, no component library.
- **Commit-on-tap** — tapping a preset chip saves the note immediately (with whatever one-liner is typed), then offers Undo; the <10s path is one tap.
- **`data-repositories`** — immediate writes through `core-data` (design rule 5: autosave, no Save button on this screen).
- **ViewModel + StateFlow** — draft text, tag list, and filter state as unidirectional state.

## # Excluded
- **Multi-tag notes** — README §6 schema is one tag per note (student, text, tag, date); many-to-many adds a join table and a chip-toggle UI the prototype doesn't need. Lost a close call.
- **Rich text / multi-paragraph editor** — the design is "optional one line"; a plain one-line input keeps the flow under 10 seconds.
- **Server-side search, paging, or indexing** — at 40–60 students × months of notes, an in-memory `Flow` filter is instant; no search library.
- **Separate "tag manager" settings screen** — tags are edited in place (long-press a chip to rename/delete, "+" to add); no setup wall means no settings maze.
- **Students/parents viewing feedback** — explicitly teacher-only in the prototype (README 4.2); any visibility is a post-server-phase question.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| UI components | Material 3 via Compose BOM | `2026.09.00` | FilterChip, TextField, Snackbar all built in |
| State | ViewModel + StateFlow | `2.11.0` (lifecycle) | draft text + filters as derived state; official UDF |
| Persistence | `data-repositories` | `core-data` contract | commit-on-tap writes, immediate autosave |
| Date & time | `java.time.LocalDate` (desugared) | `desugar_jdk_libs 2.1.5` | note dates and date-range filter on any min SDK |

## File & folder layout
```
student-feedback/
├─ viewmodel/            FeedbackViewModel: draft note, tag edits, filter state, derived lists
├─ ui/
│  ├─ QuickNoteScreen.kt    preset chip grid + one-line input; tap chip = commit (typed params: studentId)
│  ├─ FeedbackScreen.kt     searchable list, filter chips by tag, student and date filters
│  ├─ TagChips.kt           preset tag display + in-place long-press edit / "+" add
│  └─ FeedbackTimeline.kt   chronological notes composable, embedded by roster's student page
└─ (no navigation, no routes — all entries arrive as typed params from app-shell)
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. Confirm `core-data` (`data-repositories`) is available; no new Gradle dependencies — chips, text fields, and snackbars ship with the project's Compose BOM.
2. Create `viewmodel/` — seeded preset tags (editable placeholders until Phase 0 supplies the client's real tags), draft one-liner, filter state, immediate-commit logic through repositories.
3. Create `QuickNoteScreen` (chip grid + one-line field; a chip tap commits the note with current text, then shows Undo).
4. Create `FeedbackScreen` (list + student/tag/date filters), `TagChips` (in-place tag editing), and `FeedbackTimeline` for roster's student page.
5. Hand `feedback-screen`, `quick-note-screen`, and `feedback-timeline` to `app-shell` (routes) and `roster` (timeline embed).

## Why this shape
The screen exists to hit the README's <10s-with-a-preset-tag target, so the interaction was reduced to its floor: choosing a tag *is* committing — the optional one-liner, if the teacher typed one first, rides along on the same tap. That makes the fastest path one tap and the complete path two (type, tap), with an Undo snackbar covering mis-taps; a separate Save/Done button would have added a tap to every note against design rule 1. One tag per note follows the README's own schema rather than the looser "tag(s)" phrasing — at prototype scale, multi-tag would cost a join table, chip-toggle state, and filter ambiguity for no stated need; if the client asks for it later, that's a redo with real input. Tags are edited in place instead of behind a settings screen because of the no-setup-wall rule — the teacher discovers editing by long-pressing the very chips they use daily. Preset tags seed as editable placeholders and get swapped for the client's real examples in Phase 0 (README open question #7), so nothing hardcodes invented vocabulary. The filter list is an in-memory `Flow` over repositories: at 40–60 students, debounced search + chip filters are instantaneous, and adding paging/indexing would be speculative complexity. The three exported surfaces split cleanly by consumption: `quick-note-screen` (route from a student page's + button via `app-shell`), `feedback-screen` (the full searchable list), and `feedback-timeline` (embedded directly into roster's student page).

## Connections
- **Consumes:** `data-repositories` from `core-data` — FeedbackNote/FeedbackTag writes and reads, Student names for display and filtering.
- **Produces:** `feedback-screen` — searchable/filterable note list (typed params: optional `studentId` preset); `quick-note-screen` — preset-tag capture flow (typed `(studentId)` params + `onDone`); `feedback-timeline` — chronological notes composable embedded by `roster`'s student page.
- **Talks to via:** in-process composables — routes wired by `app-shell`, timeline imported directly by `roster`; no navigation dependency and no reads of other modules' internals.

## Open questions
- None. (The client's real preset tag list arrives with Phase 0 — the UI is data-driven, so nothing is blocked on it.)

## Build notes for the implementing agent
- Chip tap commits through repositories immediately — never stage notes in memory waiting for a Save; Undo performs a delete through the same path.
- `studentId` arrives typed from `app-shell` (student page's + button) or the list doubles as the entry point; never parse routes here.
- Seed tags are placeholders labeled as such — replaceable in place, no migration needed (tags are teacher-editable rows).
- The timeline embed must render read-only with its own quick-add affordance routing back through the provided `onQuickNote` callback — feedback code never navigates.
- Keep the one-line input single-line; do not grow it into a rich editor.

## Build progress
- **Owner:** builder (single-agent build)
- **Coded:**
  - `viewmodel/FeedbackViewModel.kt` — UDF state: notes/tags/students + query/tag/date filters combined into `FeedbackUiState`; `commitNote(tag, todayEpochDay)` returns the saved note (Undo deletes it), `undo`, `addTag`/`renameTag`/`deleteTag`, `ensureTags` seeding in init; date filter enum ALL/TODAY/WEEK/MONTH.
  - `viewmodel/FeedbackViewModelProvider.kt` — `feedbackViewModel(key, studentIdPreset)` composable factory reading `AppContainer` from `HelperApplication`; distinct keys keep the three surfaces on separate instances.
  - `ui/TagChips.kt` — `TagChipRow`: FlowRow of FilterChips + "Add" AssistChip; long-press (wrapper `combinedClickable`, chip's own onClick owns taps — no double-fire) opens rename/delete dialog; add dialog for "+".
  - `ui/QuickNoteScreen.kt` — commit-on-tap surface: editable chip row where tap = commit (with typed one-liner), Undo snackbar deletes through the same path; single-line input anchored in scrollable content; typed params `(studentId, onDone)`.
  - `ui/FeedbackScreen.kt` — searchable list: query over student name/tag/text, tag chips single-select, date FilterChips (All time/Today/This week/This month), date-grouped rows, empty states; typed params `(onDone, studentIdPreset?)`.
  - `ui/FeedbackTimeline.kt` — read-only chronological embed for roster's student page; "Quick note" FilledTonalButton emits `onQuickNote`, never navigates; takes latest 20 notes.
  - `ui/DateText.kt` — `epochDayToDisplayDate()` (Today/Yesterday/`d MMM yyyy`) over desugared `LocalDate`.
  - `seed/SeedTags.kt` — placeholder tag set (per card: replaceable in place, Phase 0 swaps real vocabulary).
  - Gradle: `material-icons-core` added (material3 no longer pulls it transitively); lifecycle `runtime-compose`/`viewmodel-compose` 2.11.0 wired (artifact id is `lifecycle-runtime-compose`, not `runtime-compose` — fixed after first failed resolve).
- **Remaining** (next first):
  1. On-device QA of long-press-to-edit on chips (wrapper `combinedClickable` around the chip's tap-owning clickable — compiled and reviewed, but gesture behavior not yet verified on a real device).
  2. `roster` consumes `feedback-timeline`; `app-shell` wires the three routes — no code changes expected here unless a seam mismatch shows up.
- **Blocked on:** nothing.
- **Handoff note:** One `FeedbackViewModel` class serves three surfaces — always construct via `feedbackViewModel(key = ..., studentIdPreset = ...)` with distinct keys or instances collide. Notes store `dateEpochDay` (epoch-day Long); screens format with `epochDayToDisplayDate()`. Commit-on-tap must stay one tap (design rule 1) — don't add a Save button. `FeedbackScreen` is read-only by design; capture only on `QuickNoteScreen`.
- **Last checkpoint:** 2026-10-20T22:45:00+05:30
