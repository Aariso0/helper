---
module: roster
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "feedback-timeline"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["class-student-picker", "photo-storage", "roster-screens"]      # what this module exposes to others (use _shared/glossary.md terms)
build: not-started   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-20
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
{Left blank by the blueprint skill. Implementing agents fill and update this after every chunk of work.}
- **Owner:** {agent / teammate name, or none}
- **Coded:**
  - {`path/to/file` — what is done in it}
- **Remaining** (next first):
  1. {next piece of work}
- **Blocked on:** {nothing / what and who}
- **Handoff note:** {what the next agent should know — where you stopped, any trap}
- **Last checkpoint:** {ISO timestamp}
