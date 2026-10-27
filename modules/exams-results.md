---
module: exams-results
status: researching   # planned · researching · drafted · needs-redo
consumes: ["data-repositories", "class-student-picker", "share-helpers"]      # other module slugs / interfaces this module needs (use _shared/glossary.md terms)
produces: ["exams-results-screen", "exam-export"]      # what this module exposes to others (use _shared/glossary.md terms)
build: built   # not-started · in-progress · blocked · built · needs-rework — written by implementing agents, not the blueprint skill
last-updated: 2026-10-28
---

# Exams & Results

Exam creation with multiple subjects, fast mark entry per student/subject, auto-calculated totals/percentages, PASS/FAIL with per-subject pass thresholds, in-app result analysis per student, and Excel export with formulas and configurable cell colors so recalculations happen inside the spreadsheet. Most data rows in exports use default/neutral styling; only the Result column is color-coded (green for PASS, red for FAIL). Colors are encoded via `IF`/conditional logic in Excel when exporting (using passing/failing masks) so they remain consistent with formulas. Colors also apply consistently across students by the same formula rules for each cell.

## # In Use
- **Room (via `data-repositories`)** — persist exams, exam subjects, and exam marks against `SchoolClass`/`Student`.
- **kexcel-android 0.1.2** — write `.xlsx` with `FormulaCellValue`, `CellStyle`, and `ExcelColor` (already used by roster/export-backup).
- **Material 3** — number keyboards via `KeyboardOptions(keyboardType = KeyboardType.Number)`, lists, pickers, color pickers (simple swatches).
- **`class-student-picker`** — pick class when creating/viewing exams.
- **`share-helpers`** — share the exported `.xlsx` via system share sheet.

## # Excluded
- **Apache POI** — Android bloat, documented issues; kexcel is already the project choice.
- **Storing precomputed totals only** — totals/percentage must be formulas in Excel per user requirement (so changing marks in sheet recalculates). In-app we also compute for display.
- **Auto-grading curves / ranks** — keep simple (sum/avg/percentage, pass/fail by thresholds).
- **Cloud sync** — offline-only, consistent with project constraints.
- **Importing marks from Excel** — out of scope; entry is manual in-app for speed.

## Exact stack
| Concern | Choice | Version / tag | Why this one |
|---|---|---|---|
| Persistence | `Room` via `core-data` entities/DAOs/repositories | `2.8.5` (existing) | extend existing `data-repositories` seam, no new DB stack |
| Excel export with formulas/colors | `kexcel-android` | `0.1.2` (existing) | supports `FormulaCellValue`, `CellStyle`, `ExcelColor` per README; already in use |
| UI | Jetpack Compose + Material 3 | BOM `2026.09.00` (existing) | consistent with app |
| Mark entry | Numeric keyboard + focused flow | platform (existing) | `KeyboardOptions(keyboardType = KeyboardType.Number)` for efficiency |
| Picking | `class-student-picker` from `roster` | existing | reuse seam |

## File & folder layout
```
exams-results/
├─ entity/                Exam, ExamSubject, ExamMark (Room entities; added to core-data schema)
├─ dao/                   DAOs for exams/subjects/marks
├─ repository/            ExamRepository in data-repositories
├─ viewmodel/
│  ├─ ExamListViewModel.kt
│  ├─ ExamSetupViewModel.kt   // create exam + add subjects
│  ├─ MarkEntryViewModel.kt   // fast sequential entry
│  └─ ExamResultViewModel.kt  // analysis + export config
├─ ui/
│  ├─ ExamListScreen.kt
│  ├─ ExamSetupScreen.kt
│  ├─ MarkEntryScreen.kt      // numeric keyboard, next/prev, auto-focus
│  ├─ ExamDetailScreen.kt     // student list with totals/%, pass/fail, analysis
│  └─ ExportConfigDialog.kt   // color pickers for columns/rows/total/percent/result/header
├─ export/
│  └─ ExamExcelWriter.kt      // build sheet with formulas and styles
└─ util/
   └─ ResultCalculator.kt     // totals/percentage/pass-fail
```
Relative to the app module's main source set, under the project's root namespace.

## Setup sequence
1. **core-data**: add entities `Exam`, `ExamSubject`, `ExamMark` (with FKs to `SchoolClass`/`Student`, indices). Add DAOs, repositories, register in `AppDatabase` (schema bump + migration if needed). Export schema. Support adding subjects to an existing exam (post-creation) via `ExamSetup`/exam editing.
2. Add module package `com.example.helper.examsresults` following the layout above.
3. Create UI flows: Exam list → Create exam (name, class, date) → Add subjects (name, max marks, pass marks) → Enter marks (fast flow) → View results/analysis → Export to Excel with config. Allow adding subjects after an exam is created.
4. Implement `ExamExcelWriter` using `kexcel-android`: write header row with minimal/default styling (or configurable); student rows mostly uncolored except result column. Result column MUST be colored by formula logic (e.g. `IF` checking passing mask) — green for PASS, red for FAIL. Apply the same color rules/formulas to all students. Use `FormulaCellValue` for totals/percentage/result; do not pre-fill numeric zeros for unentered marks — leave cells empty in export but include them in formulas (treat missing/blank as not present; formulas should handle blanks safely). When entering marks, user may enter marks for a single subject at a time (from Exam & Result tab, choose exam then choose subject to enter marks). Exported Excel will still contain all subjects.
5. Wire screens into `app-shell` navigation and add entry points (change class entry icon from `+` to a file/download icon; fetch appropriate icon if not present).
6. Ensure mark entry uses numeric keyboard and efficient sequential navigation. Student name/roll should remain visible while typing (auto-scroll to keep the current student/field visible so the keyboard doesn't cover names). When entering marks, allow selecting subject context while in mark entry flow.

## Why this shape
The core need is Excel compatibility: formulas must remain in the sheet so edits outside the app update totals/percentages/results. kexcel 0.1.2 already supports `FormulaCellValue` and cell styling/colors (confirmed from README/source), so no new Excel library needed. Data model mirrors exam structure (exam has subjects, marks per (exam,subject,student)). Fast entry is critical per user requirement - use numeric keyboard, large tap targets, auto-advance to next student/subject with minimal taps. Color configuration is app-side state persisted (DataStore/Room) or just passed at export time; export-time config keeps it simple. Analysis in module card shows per-student totals/%, pass/fail per subject and overall. All local/offline, no server deps.

## Connections
- **Consumes:** `data-repositories` from `core-data` (CRUD for exams/subjects/marks, read students/classes); `class-student-picker` from `roster` (select class); `share-helpers` from `app-shell` (share .xlsx).
- **Produces:** `exams-results-screen` — root composable(s) for exam list/setup/entry/results; `exam-export` — Excel export capability.
- **Talks to via:** in-process composables, repositories via `AppContainer`; files via SAF/share sheet.

## Open questions
- ~~Exact formula for percentage~~ **Resolved (2026-10-27, re-test finding):** `totalObtained / totalMax * 100`. With every subject out of 100 this is the user's "total / total no of subjects" as a percentage (320 across 4 subjects of 100 = 320/400 = 80%); with mixed max marks it stays a true percentage and matches the `Total: X/Y` label and the Excel formula. The earlier `total / subjectCount * 100` produced 7000% for 80+70+60 across 3 subjects.
- PASS/FAIL formula in Excel: can be `IF(...)` based on failing marks defined per subject (user input). Need to support per-subject thresholds and overall rule.
- Where to store color preferences (export config) - in-memory for export dialog is fine for prototype.
- Migration strategy for new entities in core-data.

## Build notes for the implementing agent
- Use `FormulaCellValue("=SUM(...)")` for totals; use `FormulaCellValue("=B2/C2")` style references (A1 notation). Ensure cell references are correct when building ranges.
- Excel export styling: most rows default/no color. ONLY Result column is colored by formula/conditional logic (green if PASS, red if FAIL). Use IF/conditional rules encoded in formulas/masks so the same formula applies to all student cells. Leave unentered marks blank in export (do not write 0) but include cells in SUM/percentage/result formulas so calculations still work correctly. Ensure formulas handle blank cells (e.g. treat blank as missing but SUM ignores blanks; or include all subject columns in range consistently).
- Mark entry: `KeyboardOptions(keyboardType = KeyboardType.Number)`, `IMEAction.Next`, focus management to move to next field efficiently. Consider a grid/list where tapping goes to next student. Support entering marks for a single subject at a time from the Exam & Result tab (choose exam, then choose subject) while still exporting all subjects. Keep student name visible while typing (auto-scroll / bring current row into view when keyboard opens/focus moves). 
- Allow adding subjects to an existing exam after creation.
- In the class list/student list entry to Exams & Results, use a file/download icon instead of `+` (download one if not found locally).
- Validate marks don't exceed max marks per subject (configurable).
- All data local; no network.

## Build progress
- **Owner:** opencode
- **Coded:**
  - `app/src/main/java/com/example/helper/coredata/entity/Exam.kt`, `app/src/main/java/com/example/helper/coredata/entity/ExamSubject.kt`, `app/src/main/java/com/example/helper/coredata/entity/ExamMark.kt` — entities added
  - `app/src/main/java/com/example/helper/coredata/dao/ExamDao.kt`, `app/src/main/java/com/example/helper/coredata/dao/ExamSubjectDao.kt`, `app/src/main/java/com/example/helper/coredata/dao/ExamMarkDao.kt` — DAOs
  - `app/src/main/java/com/example/helper/coredata/migration/Migration1To2.kt`, `app/src/main/java/com/example/helper/coredata/migration/Migrations.kt` — migration to v2
  - `app/src/main/java/com/example/helper/coredata/AppDatabase.kt` — registered entities/DAOs, bumped to v2
  - `app/src/main/java/com/example/helper/coredata/repository/ExamRepository.kt`, `app/src/main/java/com/example/helper/coredata/AppContainer.kt` — repository and container wiring
  - `app/src/main/java/com/example/helper/examsresults/util/ResultCalculator.kt` — totals/percentage/pass-fail
  - `app/src/main/java/com/example/helper/examsresults/viewmodel/*` — ViewModels for list/setup/entry/results
  - `app/src/main/java/com/example/helper/examsresults/export/ExamExcelWriter.kt` — Excel export with formulas
  - `app/src/main/java/com/example/helper/examsresults/ui/*` — screens and export config dialog
  - `app/src/main/java/com/example/helper/examsresults/ExamsViewModelProviders.kt` — ViewModel providers
  - `app/src/main/java/com/example/helper/nav/Routes.kt`, `app/src/main/java/com/example/helper/nav/AppNavHost.kt` — routes and navigation
  - `app/src/main/res/xml/file_paths.xml` — added exams cache path
- **Remaining** (next first):
  1. none
- **Blocked on:** nothing
- **Re-test fixes (2026-10-27):**
  - Create-exam flow is now reachable: the Exams list is `exams/{classId}` (class carried from the student list), its `+` opens `ExamSetupScreen` for that class, and a saved exam lands on its results screen.
  - Percentage is `totalObtained / totalMax * 100` in `ResultCalculator`, on screen, and in the Excel formula, so the `Total: X/Y` label and `%` agree (different subject max marks included). 4 new unit tests in `app/src/test/java/com/example/helper/examsresults/ResultCalculatorTest.kt`.
  - Mark upsert reuses the existing `ExamMark.id` for the `(examId, studentId, subjectId)` unique index (verified `saveMark`/`saveAll`).
  - Excel export now writes numeric cells (`IntCellValue`/`DoubleCellValue`), live `IF(...)` PASS/FAIL formulas, the configured cell colours, column widths, and the corrected `<dimension>` range (`XlsxDimension`). 3 archive-reading tests in `app/src/test/java/com/example/helper/examsresults/ExamExcelWriterTest.kt` pin formulas, numeric cells and colours.
- **Requirements update (2026-10-27):**
  - Export is neutral except the Result column: `ResultColumnColors.kt` post-processes the archive with green/red `dxf` fills and a `cellIs` conditional rule over `Result`, so the PASS/FAIL rule is the same for every student and recolours if a mark changes. `ExportConfig` now carries only the PASS/FAIL colours and the dialog offers just those.
  - Unentered marks export as blank cells (no fabricated `0`), while staying inside the `SUM`, percentage and PASS/FAIL formulas (a blank compares as 0, so it still fails the subject).
  - Mark entry is one subject at a time: `ExamDetailScreen` picks exam → subject, `MarkEntryScreen`/`MarkEntryViewModel` show a single mark column, and the subject can be switched from the mark-entry top bar without leaving the flow. Export still contains all subjects.
  - The focused row is scrolled clear of the keyboard (`BringIntoViewRequester` + `imePadding`) and a pinned `Editing: <name>` bar keeps the student's name visible while typing.
  - Subjects can be added after creation from `ExamDetailScreen` (`Add Subject` → name/max/pass), with results and export picking them up immediately.
  - Student list's Exams & Results action uses the new `res/drawable/ic_exam_results.xml` file/download icon instead of `+`.
  - `ResultCalculator` records only entered marks (`marksBySubject`), so the writer can tell "blank" from "zero".
  - `ExamExcelWriterTest` now has 4 tests (formulas/numeric cells, blank unentered marks, Result-only conditional colours, failing row rule); `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain` is green with zero warnings (23 tests).
- **Follow-up (2026-10-28):**
  - Analysis now lists the failed subjects by name (`Failed in: Science, English`) instead of a count.
  - A blank mark is no longer a zero that fails: `StudentResult` only fails entered marks below their pass mark. Status is FAIL if any entered mark fails, PASS if the available marks all pass (other subjects may still be blank), and `—` only when no marks exist at all. The Excel Result formula ignores blanks in its pass checks and stays blank only when the row has no marks; an entered failing mark still produces FAIL. `ResultCalculatorTest` covers partial-pass, partial-fail and no-data cases (6 tests).
  - The Exams & Results action uses a document/file icon (`ic_exam_results.xml`, Material `description`) rather than `+` or a download glyph.
- **Handoff note:** Added exams-results module with core-data entities/migration, UI flows, and kexcel export with formulas and conditional Result colours. Re-test findings fixed; requirements update landed; missing marks are neutral; all tests pass; app compiles with zero warnings.
- **Student page history (2026-10-28):**
  - `app/src/main/java/com/example/helper/examsresults/ui/StudentExamHistory.kt` + `viewmodel/StudentExamHistoryViewModel.kt` + provider — embedded on roster's student page. One card per exam, newest first: exam name/date, `Total`, `%`, PASS/FAIL, failed subject names, and subject chips (`All subjects` or one subject). Exams with no marks show `Not entered`. No inner vertical scroll; subject chips scroll horizontally.
  - `app/src/main/java/com/example/helper/examsresults/util/ExamHistory.kt` — folds a class's exams, their subjects and one student's marks; `ExamMarkDao.observeByStudent` + `ExamSubjectDao.observeByClass` with repository wrappers supply the flows.
  - `ExamDetailScreen` rows are tappable and open the same student page (`onStudentClick` → `Routes.student` in `AppNavHost`), with a chevron affordance.
  - `app/src/test/java/com/example/helper/examsresults/ExamHistoryTest.kt` — 3 tests pin newest-first order, `Not entered` vs failed, and per-student/per-exam isolation. Full gate green: 28 tests, zero warnings.
- **Class-scoped exam list (2026-10-28):** `ExamListViewModel` now takes the class id and observes `ExamRepository.observeByClass` (class name from `SchoolClassRepository.observeById`) instead of every exam; the provider is keyed by class. Opening the file icon from a class now lists only that class's exams.
- **Deletion (2026-10-28):** `ExamDetailScreen` has a top-bar overflow → `Delete exam` and a trash action on each subject row; `ExamListScreen` rows have a `Delete` menu. Every action asks first and names the cascade (exam → subjects and all marks; subject → its marks). `ExamResultViewModel.deleteSubject` / `deleteExam(onDeleted)` and `ExamListViewModel.deleteExam` rely on the existing foreign-key cascade; deleting from the detail screen pops back to the class's exam list.
- **Edit subject totals (2026-10-28):** each subject row now has a pencil → edit dialog for max and pass marks. Pass must stay within `0..max`; saving calls `ExamRepository.updateSubject`, whose `ExamSubjectDao.updateWithClampedMarks` transaction updates the subject and lowers every existing mark above the new max to that max (e.g. 120/100 → 100/100), so results, percentages and export always agree. Exam creation now enforces the same pass ≤ max bound. Subject rows also keep the delete action.
- **Last checkpoint:** 2026-10-28T01:13:19+05:30
