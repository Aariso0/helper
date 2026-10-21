---
module: export-backup
status: drafted   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "class-student-picker", "share-helpers", "photo-storage", "attachment-storage"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["export-screen"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-21
---

# Export & backup

One-tap monthly attendance summaries per class and per student, shown in-app and exported to CSV/Excel and PDF so the client never feels locked in (README 3.6 + promise 10). One-file manual backup of everything on the device and a validated restore — the safeguard against device loss or reset (README 2.7).

## # In Use
- **SAF `CreateDocument` / `OpenDocument` (`androidx.activity`)** — every export and the backup archive are saved/opened through the system picker: zero permissions, the teacher chooses the destination, any storage provider works.
- **commons-csv + kexcel-android** — the same libraries `roster` already imports for the roster import, now writing instead of parsing; zero new dependencies.
- **Framework `PdfDocument`** — the same zero-dependency text→PDF choice `parents` made; one PDF approach app-wide.
- **`java.util.zip`** — the backup is one zip of the (checkpointed) Room DB file + `photo-storage` + `attachment-storage`: one file to move, one file to restore.
- **`class-student-picker` + `share-helpers`** — same picker for scope (month → class → student) and the optional "share this export" action via the system share sheet.

## # Excluded
- **Android-Room-Database-Backup (rafi0101)** — a third-party library wrapping exactly the close-and-copy dance, adding a trust surface around student data for nothing the platform can't do.
- **Row-level JSON dump via kotlinx-serialization** — a second serialization layer to maintain for every entity; every schema change becomes two fixes. Lost a close call to file-level copy.
- **Auto Backup / `BackupAgent` / `allowBackup`** — system-scheduled, cloud-tied infrastructure the teacher can neither invoke ("backup now, before I reset this phone") nor verify; restore isn't user-initiated.
- **Firebase / Drive / any cloud SDK, WorkManager auto-backup** — user-specified: no cloud, no internet permission, no automatic movement of student data. (SAF destinations are the teacher's manual choice through the system picker; the app itself has zero cloud code.)
- **MPAndroidChart or any charts library** — the README promises summary *numbers*, not charts; a charting dependency for a table of counts and percentages.
- **Per-class PDF templates / branded report layouts** — Phase 1 polish; the prototype's PDF is a clean titled page.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Monthly summaries | pure aggregation over `data-repositories` | `core-data` contract | no second stats store; numbers come from the same rows |
| CSV export | `commons-csv` | `1.14.1` | same library as roster's import; zero new deps |
| Excel export | `kexcel-android` | `0.1.2` | same library as roster's import; zero new deps |
| PDF export | `android.graphics.pdf.PdfDocument` | platform (API 19+) | same choice as `parents`; one PDF approach app-wide |
| Save/open destination | SAF `CreateDocument` / `OpenDocument` | `androidx.activity 1.13.0` | official picker; no storage permission; teacher picks the location |
| Backup archive | `java.util.zip` (DB + photo-storage + attachment-storage) | platform | one file to move; file copy beats row JSON |
| State | ViewModel + StateFlow | `2.11.0` (lifecycle) | month/class scope, summary rows, export-in-flight state |
| Share (optional) | `share-helpers` (file-capable) | `app-shell` contract | "send this export" through the same share sheet as everything else |

## File & folder layout
```
export-backup/
├─ viewmodel/          ExportViewModel: month/class scope, summary rows, export + backup/restore actions
├─ ui/
│  ├─ ExportScreen.kt    Reports & backup destination: summaries section + export actions + backup section
│  ├─ SummaryCards.kt    monthly per-class and per-student figures at a glance
│  └─ BackupSection.kt   Backup (zip → CreateDocument) and Restore (OpenDocument → validate → confirm)
├─ summarize/          pure aggregation of AttendanceRecord rows into one monthly summary model
├─ writers/            CSV, Excel, and PDF writers over that single summary model, streamed to a SAF Uri
└─ backupio/           zip internal dirs + checkpointed DB file; restore with marker validation
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. No new Gradle dependencies — confirm commons-csv `1.14.1` and kexcel-android `0.1.2` (from `roster`), `share-helpers` + `FileProvider` (`app-shell`), the `class-student-picker` (`roster`), and the `photo-storage` / `attachment-storage` directory conventions (`roster`, `homework`) are all in place.
2. Agree the backup seam with the `core-data` owner: a hook on `data-repositories` to checkpoint/close the Room database and expose the DB file path for copying — file-level backup needs this and nothing else in the app does.
3. Create `summarize/` — pure functions: (month, class) → per-student present/absent/late/excused counts + percentage, and per-class totals; the single summary model every writer consumes.
4. Create `writers/` — CSV, Excel, PDF over that model, each streaming into the `content://` Uri SAF's `CreateDocument` returns; optional share via `share-helpers`.
5. Create `backupio/` — backup: checkpoint → zip DB file + photo + attachment dirs → `CreateDocument`; restore: `OpenDocument` → validate marker → explicit confirm → close, replace, reopen.
6. Create `ui/` and hand `export-screen` to `app-shell`'s already-declared route; agree the Today-screen entry row with the `app-shell` owner (protocol: group chat).

## Why this shape
This module *is* the README's "never feels locked in" promise (line 49) and the answer to its own risk section (line 269: data lives on one phone, so manual export/backup). Every export format is a library the app already ships for roster import — one CSV lib, one Excel lib, one PDF approach (`PdfDocument`, matching `parents`) — so the export module adds zero dependencies and the app has exactly one way to do each format. Destinations go through SAF on purpose: no storage permissions, and the teacher picks where files land — including any provider their device offers — while the app itself keeps zero cloud code, which is precisely what "manual export for now" means. The backup is a single zip because the failure it guards against is *device loss or reset*: the only restore story that matches is "the whole thing, in one file the teacher saved somewhere else." File-level copy beat a row-level JSON dump despite both being dependency-free: JSON forces a second serialization layer onto every entity forever (each schema change becomes two fixes), while the file copy has exactly one known trap — Room's write-ahead log — and that trap is a documented one-line checkpoint, not a maintenance regime. Restore validates a marker (app id + schema version) and demands an explicit "this replaces everything" confirmation before touching a byte, because a restore tool that silently overwrites is worse than no restore tool. Summaries are computed on the fly from the same `AttendanceRecord` rows every other screen reads: no stats table to drift, no second source of truth.

## Connections
- **Consumes:** `data-repositories` from `core-data` — AttendanceRecord/Class/Student rows for summaries, plus the agreed checkpoint/DB-path hook for backup (seam: agree its exact shape with the `core-data` owner before coding); `class-student-picker` from `roster` — scope selection (class, and student within class views); `share-helpers` from `app-shell` — optional share of any export; `photo-storage` (`roster`) and `attachment-storage` (`homework`) — the directory conventions the backup zip must include, read-only.
- **Produces:** `export-screen` — the Reports & backup destination composable (typed params + callbacks); `app-shell` already declares the consuming edge in its nav table, so this card's produce simply completes it.
- **Talks to via:** in-process composables on `app-shell`'s route; files exchanged only through SAF `content://` Uris.

## Open questions
- None. (The exact percentage formula — e.g. whether Late counts as present — is a Phase 0 client question per the README's open questions; `summarize/` defines it in exactly one place and the exported file header states it, so whichever answer the client gives is a one-line change.)

## Build notes for the implementing agent
- **WAL checkpoint first, always:** copying Room's DB file while the write-ahead log is hot produces a corrupt restore. Use the `core-data` hook agreed in setup step 2 (checkpoint or close) before the DB bytes enter the zip; never snapshot files around an open transaction.
- **Restore is validate-then-confirm-then-replace:** check the marker (app id + schema version), refuse mismatched schema versions outright, show the destructive-confirm dialog, and only then close the DB and replace files. No partial restores.
- One summary model feeds all three writers — never recompute per format, and never let a writer format numbers its own way.
- Default the month scope to the last *full* month, so the one-tap summary is meaningful on the 1st of the month; current-month-to-date is one tap away.
- "No records tracked" and "0% present" are different states — never render an untracked month as zeros.
- Exports stream to the SAF Uri off the main thread (rule 6); large classes and multi-file zips must not jank the UI.
- No INTERNET permission, no MediaStore writes, no cloud code of any kind; SAF only.
- Deleting or re-exporting never touches database rows: this module reads data and writes files, and must stay incapable of changing what it reports on (restore is the sole, explicitly-confirmed exception).

## Build progress
- **Owner:** opencode (lead + builder) — claimed 2026-10-21, done 2026-10-21.
- **Coded:**
  - `coredata/AppDatabase.kt`, `AppContainer.kt`, `dao/AttendanceDao.kt`, `repository/AttendanceRepository.kt` — the agreed `data-repositories` seam: `checkpoint()` (`PRAGMA wal_checkpoint(FULL)`), `databaseFile()`, `closeForRestore()` (closes + forgets the singleton), `SCHEMA_VERSION` const (the `@Database` version reads it) and one `getAll()` attendance query for the full table dump. No schema change.
  - `summarize/MonthlySummary.kt` — one summary model (student → class → grand totals, distinct tracked days, nullable percentage = "no records", never 0%) + the pure `buildMonthlySummary(...)`; `PERCENTAGE_RULE` states Late-counts-as-present in every file header.
  - `writers/SummaryCsvWriter.kt` — commons-csv (the roster parser, now writing), comment header + per-class totals + grand total.
  - `writers/SummaryExcelWriter.kt` — real `.xlsx` via `kexcel-android` (`Excel.createExcel`, `appendRow`, `setColumnWidth`, `rename` to "Attendance", `encode`); same rows/columns as CSV.
  - `writers/SummaryPdfWriter.kt` — framework `PdfDocument` + `StaticLayout`; monospaced aligned table per class, per-class + grand totals, pagination with repeated class header and page-number footer.
  - `backupio/BackupManifest.kt` — `manifest.json` via platform `JSONObject` (no serialization dependency): marker, format version, app id, schema version, created-at, per-table counts, file count.
  - `backupio/BackupWriter.kt` — reads every repository row, checkpoints, then one zip: manifest + `database/helper.db` + `files/student_photos/**` + `files/attachments/**` + `csv/<table>.csv` for all 10 tables (the user's "any data entered must be exportable" requirement; the CSVs are readable outside the app, the DB is the restore payload).
  - `backupio/BackupRestorer.kt` — inspect (manifest-only, nothing touched) then restore: extract to `cacheDir/restore_staging` (zip-slip + size guards), re-validate marker/app id/schema, close DB, move current DB+WAL+SHM+both file stores into `restore_previous`, swap staged data in, rollback on any failure, delete the rollback copy on success.
  - `backupio/AppRestart.kt` — launch intent + short delay + `exitProcess`; needed because a file-level restore invalidates every repository.
  - `viewmodel/ExportViewModel.kt` — month (defaults to last full month, future capped) × class scope, summary reload, export/backup/inspect/restore state machine, SAF file-name suggestions, `NonCancellable` restore.
  - `ui/ExportScreen.kt`, `ui/SummaryCards.kt`, `ui/BackupSection.kt` — month stepper + "Show this month" + class picker; overall/class/student cards; three export buttons (one `CreateDocument` launcher per MIME); backup + restore with a preview dialog naming counts and the destructive warning; post-restore takeover (back blocked, auto-restart on success, manual restart on failure).
  - `today/TodayScreen.kt` — `LinkRow` "Reports & backup" under a new "Reports" section; `nav/AppNavHost.kt` — placeholder replaced by `ExportScreen`.
  - `app/src/test/java/com/example/helper/exportbackup/MonthlySummaryTest.kt` — 7 tests: late-as-present, untracked null %, class distinct-day totals, grand totals, class filter, roll-number ordering, empty month.
- **Remaining:** none. Optional card item not taken: the `share-helpers` "share this export" action (card says optional; SAF already lets the teacher pick any destination/server).
- **Blocked on:** nothing
- **Handoff note:**
  - Restore is deliberately **file-level + process restart** (card shape): it closes the DB, swaps files under a rollback copy, then the outcome screen restarts the app. Do not "optimise" this into an in-process reopen — every ViewModel holds repositories bound to the closed instance.
  - `files/student_photos/**` and `files/attachments/**` are the backup's file payload; `exports/` (announcement PDFs) is deliberately **not** backed up — it is derivable, and this module now owns sweeping it if that is ever needed.
  - Traps found: `kexcel`'s `TextCellValue` primary constructor takes a `TextSpan` (use the `String` secondary constructor; a `::TextCellValue` reference is ambiguous for `appendRow(List<CellValue?>)`); on this compile SDK `Typeface.SANS_SERIF` is a `Typeface`, not a `String` — use `Typeface.create("sans-serif", style)`; commons-csv `Builder.build()` is deprecated, `.get()` is the warning-free call.
  - Traps proven again: `SchoolClass`/`Student` need `createdAt`/`updatedAt`; `Modifier.padding(start, end, vertical)` invalid; `StaticLayout.Builder.setIncludeFontPadding` absent; compile-only passes hide runtime mistakes.
- **Last checkpoint:** 2026-10-21 — built; gate green with zero warnings (`:app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest`, 7 new unit tests passing).
