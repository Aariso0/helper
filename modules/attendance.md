---
module: attendance
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "share-helpers"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["attendance-screen"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-21
---

# Attendance

The tap-absentees attendance screen: everyone preset to Present, one tap marks absent, live counters, and editing any past date with the same screen. Produces attendance records and per-student statistics.

## # In Use
- **Material 3 built-ins** — DatePicker for date switching, ModalBottomSheet for long-press statuses, Snackbar for undo; no component library needed.
- **ViewModel + StateFlow** — draft statuses and derived live counters ("Present 28 | Absent 2") as unidirectional state (official Compose UDF pattern).
- **`data-repositories`** — reads the class's Students and reads/writes AttendanceRecords; never touches Room directly.
- **`share-helpers`** — post-save "Share absentee list" shortcut opens the system share sheet.
- **Full-row storage** — one AttendanceRecord per student per class-day, explicit status every time.

## # Excluded
- **Period-picker-first UI** — user decided per class per day (project constraint); the `period` DB field stays untouched, no picker on screen.
- **Exception-only storage (session marker + absent rows only)** — cleverer and smaller, but makes "days the class met" and every per-student % an inference from absences; full rows make both trivial queries. Lost a close call.
- **Third-party date pickers (e.g. compose-material3-datetime-pickers)** — official Material 3 DatePicker covers the one-job need (tap header, pick date).
- **RecyclerView / View-system lists** — Compose-only project; `LazyColumn` is the list.
- **Saving only on process death (SavedStateHandle drafts)** — overkill next to the autosave contract below; draft state lives in the ViewModel and is persisted per change.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| UI components | Material 3 via Compose BOM | `2026.09.00` | DatePicker, ModalBottomSheet, Snackbar built in |
| State | ViewModel + StateFlow | `2.11.0` (lifecycle) | official UDF pattern; counters derived from draft state |
| Persistence | `data-repositories` | `core-data` contract | one atomic session write + per-toggle upserts |
| Date & time | `java.time.LocalDate` (desugared) | `desugar_jdk_libs 2.1.5` | default-today, date switch, past-date edit on any min SDK |
| Sharing | `share-helpers` | `app-shell` contract | absentee list via system share sheet, user-initiated |

## File & folder layout
```
attendance/
├─ viewmodel/            AttendanceViewModel: draft statuses, derived counters, commit calls
├─ ui/
│  ├─ AttendanceScreen.kt   header (class, date, live counter), list, bottom Save
│  ├─ StudentRow.kt         tap = present/absent toggle, long-press = Late/Excused sheet
│  └─ StatusSheet.kt        Late / Excused / Absent / Present picker (ModalBottomSheet)
└─ search/                jump-to-student field for large classes
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. Confirm `core-data` (`data-repositories`) and `app-shell` (`share-helpers`, ambient `app-theme`) are available; this module adds no new Gradle dependencies beyond the project's existing Compose/desugaring setup.
2. Create `viewmodel/` — draft map of student statuses seeded from (a) existing records for the class+date, or (b) all-Present preset when no session exists yet; counters as derived StateFlow.
3. Create `ui/` — header with class name, date (defaults to `LocalDate.now()`, M3 DatePicker on tap), live counter; `LazyColumn` of `StudentRow`s; bottom-anchored Save; post-save share shortcut.
4. Create `search/` (jump-to-student) and `StatusSheet` for long-press Late/Excused.
5. Hand `attendance-screen` (root composable, typed `(classId, date)` params) to `app-shell` for route wiring.

## Why this shape
The screen is the product's thesis ("faster than Excel"), so every choice serves tap count: all-Present preset means the common case is zero taps beyond opening, a single tap flips a student to Absent, and the rare statuses (Late/Excused) hide behind long-press so they never crowd the fast path — that ordering is the direct application of design rule 1. The README's two seemingly conflicting rules (§3.1's big Save button vs. design rule 5's autosave) are reconciled as: the first change to a fresh session materializes the whole class-day in one transaction, later taps upsert single rows, and Save means "done for this class" — it confirms, offers the absentee share shortcut, and navigates back; work is never sitting only in memory. Full-row-per-student storage beat the tempting exception-only scheme because the app's other features all ask positive questions — attendance % per student, monthly summaries, "was attendance taken today" — and full rows answer all three with plain queries while exception-only storage forces inference (a zero-absence day and a forgotten day become indistinguishable without a second table). Live counters are computed state over the draft, not stored values, so they cannot drift. Editing a past date is the identical screen with a different `date` argument — no second implementation, which is what keeps the 15-second-fix target honest. Everything renders under the shell's ambient `app-theme`, and sharing goes through `share-helpers`, so this module contains zero navigation, theme, or intent code.

## Connections
- **Consumes:** `data-repositories` from `core-data` — Student list for a class, AttendanceRecord reads/writes (including "was today taken?" which `app-shell`'s Today nudge also derives); `share-helpers` from `app-shell` — post-save absentee sharing.
- **Produces:** `attendance-screen` — root composable with typed `(classId, date)` parameters and an `onDone` callback; no routes, no navigation dependency.
- **Talks to via:** in-process composable import; state flows one way (ViewModel `StateFlow` → screen, events → ViewModel → repositories).

## Open questions
- None. (Whether the school requires a per-period format remains a client question at project level; the storage field is already there if it ever becomes a yes.)

## Build notes for the implementing agent
- `classId` and `date` arrive as typed parameters from `app-shell` — never parse routes here.
- Default date is the device's today (`LocalDate.now()`); switching dates reloads that date's session through repositories.
- Preserve the preset semantics: opening a fresh session shows all Present; the first commit writes every student's row, later taps update single rows.
- Every status change gets a Snackbar undo (design rule 5); undo restores the previous status through the same commit path.
- The post-save share action appears only after commit and builds the absentee list itself, then calls `share-helpers`.
- No period picker, no extra tabs, no per-row checkboxes — keep the row a single big tap target (rule 4).
- Class sizes are 40–60 (project scale): `LazyColumn` + derived counters are comfortably sufficient; do not add pagination or search indexing.

## Build progress
- **Owner:** `opencode` (lead+teammate). DONE 2026-10-21T00:27+05:30.
- **Coded:**
  - `viewmodel/AttendanceViewModel.kt` — draft status map seeded from existing class-day records or the all-Present preset; live counters derived from the draft (`UiState.counters`, never stored); first change / Save on a fresh session materializes the whole class-day via `commitSession`, later taps `setStatus` single rows; `Mutex` serializes writes so rapid taps can't interleave; date switch re-seeds through repositories; `save()` returns absent names for the share shortcut; `attendanceViewModel(classId, dateEpochDay)` provider keyed per class
  - `ui/AttendanceScreen.kt` — top bar (class name, back); date button ("Today · Fri, 21 Oct 2026") → M3 DatePicker (UTC epoch-millis ↔ desugared LocalDate); live counter "Present 28 · Absent 2" (absent in error color, late/excused appended when nonzero); LazyColumn of rows; bottom-anchored 56dp Save; Save → snackbar: "no absences" plain, else "N absent" + **Share** action building "Absent — Class, date: • names" through `shareText`; per-tap + per-sheet-change **Undo** snackbar (restores previous status through the same commit path); empty/no-match states
  - `ui/StudentRow.kt` — one big 64dp tap target (`combinedClickable`): tap = present/absent toggle, long-press = status sheet; trailing status (muted check / Absent in error / Late in tertiary / Excused in secondary); roll number subtitle; no checkboxes
  - `ui/StatusSheet.kt` — ModalBottomSheet Present/Absent/Late/Excused with current checkmark
  - `search/JumpToStudent.kt` — filter field (name or roll number); counters always reflect the whole class
  - `nav/AppNavHost.kt` — ATTENDANCE route placeholder replaced with the real screen (`classId` + `dateEpochDay` parsed in nav; `LocalDate.now()` default); doc comment updated
- **Remaining** (next first):
  1. On-device QA (no device here): long-press sheet gesture, snackbar undo timing on rapid taps, DatePicker on small screens, Save bar vs. IME/nav insets.
- **Blocked on:** nothing.
- **Handoff note:** Save on a *fresh, untouched* session deliberately writes the all-Present preset — the zero-tap common case must clear the Today nudge; do not "optimize" it away. `period` field untouched (no period picker, user decision). The absentee share covers ABSENT only — Late/Excused are not absentees.
- **Last checkpoint:** 2026-10-21T00:27+05:30
