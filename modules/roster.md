---
module: roster
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "feedback-timeline"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["class-student-picker", "photo-storage", "roster-screens"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-28
---

# Roster

Classes and students: lists, the student page with attendance % and history, and Excel/CSV import with preview. The foundational picker every feature that names a Class or Student consumes.

## # In Use
- **commons-csv** — parses the teacher's CSV exports; Apache-maintained, one small dependency.
- **kexcel (kexcel-android)** — reads .xlsx natively on Android in pure Kotlin, no Apache POI.
- **Storage Access Framework + photo picker** — system document/photo pickers grant URI access; zero storage permissions.
- **Coil 3** — Compose-first image loading for student photos, usable with no network stack at all.
- **Preview-before-commit import** — column-mapping preview screen (TeachVault pattern); the teacher confirms rows before anything is written.

## # Excluded
- **Apache POI** — heavy dex/method-count footprint on Android, documented runtime issues; the standard desktop answer that doesn't fit mobile.
- **droidxls** — capable Android xlsx reader, but paid commercial license ($99/year) — an unusual restriction the prototype must not inherit.
- **DroidOffice** — paid commercial library; same license concern.
- **ExcelPro** — Apache POI wrapper; inherits POI's Android bloat.
- **Hand-rolled zip/XML xlsx reader** — shared strings, cell references, and type handling are exactly the fragile logic a library exists for.
- **univocity-parsers** — fastest Java CSV parser in benchmarks, but releases stopped in 2021; maintenance beat raw speed.
- **opencsv** — stale line and fewer fixes than commons-csv; lost on maintenance.
- **Jackson CSV** — actively maintained runner-up, close call; lost because it drags the jackson-core dependency graph in for trivial roster files.
- **Glide / Fresco** — Coil is Compose-first and runs without any network library; Glide is second-class in Compose, Fresco is view-era.
- **Legacy `.xls` (BIFF) support** — kexcel is .xlsx-only; legacy files are rare, Phase 0 reveals what the client actually has, and CSV covers conversion. Adding a second reader later is a redo, not a patch.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| CSV parsing | `commons-csv` | `1.14.1` | actively maintained Apache lib, single small dependency |
| XLSX reading | `kexcel-android` | `0.1.2` | pure Kotlin, MIT, Android-native; no POI dex bloat |
| File & photo picking | SAF `ActivityResultContracts` + `PickVisualMedia` (`androidx.activity`) | `1.13.0` | system pickers, no READ/WRITE storage permissions |
| Image display | `coil-compose` (Coil 3) | `3.6.3` | Compose-first; can be used without any network dependency |
| Import flow | preview + column mapping, then commit via repositories | — (pattern) | decouples import from the client's unknown sheet layout |

## File & folder layout
```
roster/
├─ ui/
│  ├─ classlist/          Students tab: class list
│  ├─ studentlist/        searchable student list with counts
│  ├─ studentpage/        photo, roll no, contact, attendance %, timeline
│  └─ import/             file pick → parse → column-mapping preview → commit
├─ picker/                class-student-picker composables consumed by other modules
├─ viewmodel/             roster ViewModels, wired to AppContainer repositories
└─ photostorage/          copy picked photos into app-internal storage, path bookkeeping
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. Ensure `core-data` is built first — this module compiles against `data-repositories` from `AppContainer`.
2. Add dependencies: `implementation("org.apache.commons:commons-csv:1.14.1")`, `implementation("com.gyanoba.kexcel:kexcel-android:0.1.2")`, `implementation("io.coil-kt.coil3:coil-compose:3.6.3")`, `implementation("androidx.activity:activity-compose:1.13.0")`.
3. Create the `viewmodel/` + `ui/` files for class list, student list, and student page, reading/writing only through `data-repositories`.
4. Create `ui/import/`: document picker (SAF `OpenDocument`) accepting text/csv and spreadsheet mime types, parser dispatch by file type, mapping preview, then commit through repositories.
5. Create `picker/` — the reusable `class-student-picker` composables — and `photostorage/` (PickVisualMedia → copy bytes into app-internal files → store path on the Student record).
6. Register roster screens/ViewModels with the `AppContainer` graph.

## Why this shape
Import is the module's riskiest decision, so it was decided against the client's unknowns deliberately: the mapping-preview flow means the actual column layout of the client's Excel sheet (still unknown until Phase 0) never has to be guessed — the teacher assigns name/roll/contact columns once per import and the preview shows exactly what will be written. CSV parsing went to commons-csv rather than the benchmark-winning univocity (dead since 2021) because maintenance matters more than raw speed on 40-row files. For .xlsx, every heavyweight door was closed: Apache POI is the textbook answer with a documented Android bloat problem, its Android wrappers inherit that, and the clean native alternatives (droidxls, DroidOffice) carry paid licenses — kexcel is the only pure-Kotlin, MIT, Android-capable reader, so it wins; the residual risk (0.1.x line, single maintainer) is mitigated by the Phase 0 check in Build notes rather than by hedging on the stack. Picking happens through the system SAF and photo picker, so the app never requests a storage permission — consistent with the offline/privacy posture (data stays in app-internal storage, photos copied in, nothing in shared galleries). Coil 3 loads those local photos in Compose and, critically, can run with no network library at all, honoring the "no internet" constraint at the dependency level. The student page reads attendance aggregates through `data-repositories` like everything else — roster never reaches into attendance's internals.

## Connections
- **Consumes:** `data-repositories` from `core-data` — writes Class/Student (and photo paths), reads AttendanceRecord/Feedback aggregates for the student page's attendance % and history; `feedback-timeline` from `student-feedback` — the chronological notes timeline embedded on the student page (its + button emits a callback, never navigates).
- **Produces:** `roster-screens` — the Students-tab root composables (class list, student list, student page) wired into `app-shell`'s navigation graph; `class-student-picker` — reusable Compose UI to choose a Class or Student; `photo-storage` — the app-internal directory convention where student photos are copied after picking (documented for `export-backup`).
- **Talks to via:** in-process Compose components and `AppContainer` repositories; import commits through repositories, no side channels.

## Open questions
- The client's actual sheet format (xlsx vs csv vs legacy .xls, column layout, encoding) is unknown until Phase 0 delivers their file. Non-blocking by design: the mapping preview handles layout, and a legacy-.xls file gets an in-app "save as .xlsx or CSV" message rather than a second reader.

## Build notes for the implementing agent
- `core-data` must be `build: built` (or at least its `data-repositories` contract stable) before this module starts.
- Strip the UTF-8 BOM before parsing CSV — Excel writes it; parse failures must show a friendly message, never crash.
- Validate kexcel against the client's real file during Phase 0. If it fails or the file is legacy .xls: message the lead — swapping readers is a user-level stack decision (redo), not a quiet edit.
- Coil must stay in network-free configuration: photos are local files only (project bans internet permission).
- Copy picked photo bytes immediately after `PickVisualMedia` returns — URI grants are temporary; never store the content:// URI on the Student record.

## Build progress
- **Owner:** builder (single-agent build)
- **Toolchain change (recorded, not a card deviation):** project Kotlin bumped 2.2.10 → 2.3.21. Required because kexcel-android 0.1.2 (card-pinned; every 0.x release) is compiled with Kotlin 2.4 — metadata 2.4.0, unreadable by the 2.2.10 compiler (it drags kotlin-stdlib 2.4.10 onto the classpath too). Kotlin 2.4 itself is out of reach: KSP 2.3.12 (required by Room) supports KGP 2.2.10–2.3.x only. Compilers read one metadata version ahead, so 2.3.21 reads kexcel's 2.4.0 metadata cleanly — verified green, no `-Xskip-metadata-version-check` suppression, no card pin touched (no card pins Kotlin).
- **Coded:**
  - `viewmodel/ClassListViewModel.kt` — class list + per-class counts from two repository flows; create/rename/delete; deletes stored photo files before the FK cascade.
  - `viewmodel/StudentListViewModel.kt` — per-class students + search (name/roll), roll-numeric-then-name sort, add/delete with photo cleanup.
  - `viewmodel/StudentPageViewModel.kt` — profile (observeById ⋈ classes), attendance summary + recent history computed client-side from `observeForStudent`, photo import via PhotoStorage, edit/delete. `toDisplayDate()` top-level helper for history rows (roster-local; does not reach into student-feedback internals).
  - `viewmodel/ImportViewModel.kt` — state machine IDLE→PARSING→MAPPING→COMMITTING→DONE; column auto-guess by header hints; dedup on commit (roll first, then name — preserves ids/photo paths on re-import, reports "(n updated)"); friendly typed errors; display-name resolution from the SAF URI.
  - `viewmodel/PickerViewModel.kt` + `viewmodel/RosterViewModelProviders.kt` — one shared query-filtered VM for both pickers; manual-DI provider composables (same pattern as student-feedback) with per-surface keys (`roster-studentlist-<classId>`, `roster-studentpage-<studentId>`, `roster-import`, `roster-picker`).
  - `photostorage/PhotoStorage.kt` — **the `photo-storage` convention for export-backup:** `<filesDir>/student_photos/`, files named `<studentId>_<epochMillis>.<ext>`, `Student.photoPath` holds the absolute path; copy-then-delete order so a failed read never destroys the existing photo; extension whitelist, jpg fallback.
  - `picker/PickerDialog.kt`, `picker/ClassPicker.kt`, `picker/StudentPicker.kt` — `class-student-picker` seam: searchable dialogs that encapsulate their own data loading; consumers only wire `onPick`/`onDismiss` (+ optional preselected id). `StudentPicker(classId = null)` lists all students with class-name subtitles.
  - `ui/import/ImportParsers.kt` — CSV via commons-csv (BOM strip + windows-1252 fallback on U+FFFD), XLSX via kexcel (`Excel.decodeBytes` → `getDefaultSheet()` → `rows`, `CellValue.toString()` source-verified as raw text incl. TextSpan); row-0 = header; blank rows dropped; 5 000-row cap; .xls gets the card's "save as .xlsx or CSV" message.
  - `ui/import/ImportScreen.kt` — full-screen takeover: file pick (OpenDocument, mime list incl. octet-stream/zip mislabels) → parsing spinner → mapping form (target class dropdown or inline new-class field, three column dropdowns with auto-guess, first-5 preview, skipped-rows note) → pinned commit button "Import N students". Nothing touches the DB before commit.
  - `ui/classlist/ClassListScreen.kt` — Students-tab root: counts, per-row Edit/Delete menu, delete confirm states cascade honestly, import takeover hosted here with result snackbar, loaded-state (no false "No classes yet" flash), empty state with both CTAs.
  - `ui/studentlist/StudentListScreen.kt` — class name/subject header, search with no-results state, photo-or-initial avatar (`StudentAvatar` reused by the page), add-student dialog, loaded-state.
  - `ui/studentpage/StudentPageScreen.kt` — photo (tap = PickVisualMedia, badge affordance, photo-error snackbar), details with dial-in call action, attendance hero % + breakdown + last-10 history (`HISTORY_LIMIT`, "showing last n of m" honesty line), edit dialog (incl. remove photo), delete confirm, `FeedbackTimeline` embed wired to `onQuickNote(studentId)`.
- **Remaining** (next first):
  1. `app-shell` wires the three roster routes + the quick-note route; on-device visual QA of every screen then (nothing rendered on a device yet — app-shell didn't exist during this build).
  2. Phase 0: validate kexcel + the CSV decoder against the client's real file (card's standing check — swap-readers remains a user-level redo decision).
  3. Attendance/homework/parents consume `ClassPicker`/`StudentPicker` — no roster changes expected unless a seam complaint surfaces.
- **Blocked on:** nothing.
- **Handoff note:** Screens are typed-param composables only (`onOpenClass`/`onOpenStudent`/`onDone`/`onQuickNote`) — navigation stays in app-shell. The import VM is keyed `roster-import` and resets itself on dismiss/done, so re-entering the takeover always starts clean. All three main screens expose an explicit `loaded` flag — never infer "deleted" or "empty" from the pre-first-emission initial state (the student page will otherwise pop itself on first frame). `ClassPicker`/`StudentPicker` encapsulate repositories — consumers must not add their own data plumbing for picking. Roll numbers sort numerically when parseable; import dedup matches roll first, then name.
- **Student page exam history (2026-10-28):** `ui/studentpage/StudentPageScreen.kt` now embeds `StudentExamHistory(studentId)` (exams-results seam, owns its ViewModel) between Attendance and Feedback — roster added no exam data plumbing. Exam rows in Exams & Results open this same page through app-shell's `Routes.student`, so there is one student page instead of a second detail screen.
- **Delete access (2026-10-28):** student-list rows now carry an overflow `Delete` with a confirmation naming attendance, feedback notes and exam marks (the same action as the student page); the student page's copy now includes exam marks. Class deletion copy now names the exams and marks its cascade removes.
- **Last checkpoint:** 2026-10-28T01:00:17+05:30
