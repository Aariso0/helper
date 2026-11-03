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
- **Configurable formula columns (2026-10-30):**
  - Export no longer asks for colours: PASS is always green (`#4CAF50`), FAIL always red (`#F44336`). `ExportConfigDialog` is gone; `ExamDetailScreen` exports directly.
  - `coredata/entity/ExportColumn.kt`, `dao/ExportColumnDao.kt`, `repository/ExportColumnRepository.kt`, `migration/Migration2To3.kt` — one ordered `export_columns` table (empty = built-in defaults), `AppDatabase` v3, schema `app/schemas/.../3.json`.
  - `examsresults/export/ExportColumnDefaults.kt` — Total Obtained (`SUM({All Marks})`), Total Max (fixed number), Percentage (`IF({Total Max}=0,0,{Total Obtained}/{Total Max}*100)`), Result (generated PASS/FAIL from subject pass marks).
  - `examsresults/export/ExportFormulaEngine.kt` — symbolic `{Field}` templates resolved to A1 after the final order (`{Math}`, `{Total Obtained}`, `{All Marks}`, `{All Pass Checks}`, custom-column labels); raw `=...` pasted formulas are written verbatim; validation blocks unknown fields, empty/overlong formulas, unbalanced parentheses, self-references and loops, and names the column.
  - `examsresults/ui/ExportColumnsScreen.kt` + `viewmodel/ExportColumnsViewModel.kt` — in-app formula page: up/down reorder (TalkBack labels), add/edit/delete custom columns, fields/functions palette, live resolved-formula preview, advanced paste mode, reset to defaults. Reached from `ExamDetailScreen` ("Export columns" and the overflow menu); invalid layouts show the blocking message with a shortcut back to the page.
  - `ExamExcelWriter` now writes Roll No./Student Name/subjects followed by the saved columns in order and moves the Result conditional-format range with the Result column. No new dependency; kexcel stays `0.1.2`.
  - `ExportColumnsTest` — 11 tests pin defaults, custom subject references, reorder A1 remapping, custom-to-custom references, verbatim paste, disabled columns, unknown fields, loops, self-reference and parenthesis validation. Full gate green: 39 tests, zero warnings.
- **Sheet and layout fixes (2026-11-01):**
  - Result reads "Pass" / "Fail" from the sheet's own `IF()` formula (was `PASS`/`FAIL`). Green and red are gone: `ResultColumnColors.kt` and `ExportConfig` (passColor/failColor) are deleted, so no fill is written anywhere and the sheet stays white.
  - "Fail" is bold italic through one `dxf` plus a `cellIs equal "Fail"` rule over the Result range; "Pass" keeps the plain font. `WorkbookDecoration.kt` (post-process over the encoded zip) adds that rule, sets `applyBorder="1"` on every bordered xf (kexcel omits it, and a viewer may then drop the lines) and `fullCalcOnLoad="1"` in `calcPr` so Excel computes the formulas as soon as the file opens instead of showing the empty cached value.
  - Thin box borders on every used cell — header row, marks, blank marks, computed columns — are now a default of `ExamExcelWriter`, so a printed copy has clean lines.
  - Any column can be deleted, built-ins included: `isBuiltIn()` and the UI guard are gone. Deleting the last column persists `CLEARED_LAYOUT_ID`, so an intentionally empty layout does not fall back to the four defaults; a truly empty table still means "never configured".
  - The Reset action, its dialog, `resetToDefaults()` and `ExportColumnRepository.reset()` are removed from the export-columns page.
  - Formulas were being written as `<f>=SUM(...)</f>`; the stored cell formula must not carry the leading `=`, so the writer strips it.
  - `ExamExcelWriterTest` (5 tests) and `ExportColumnsTest` (21 tests) updated: Pass/Fail text, dxf/rule, thin borders, `applyBorder`, no leading `=`, erased-layout export. Full gate green, zero warnings.
- **S.No. column, PDF export, summary sheet, exam import (2026-11-02):**
  - **S.No.** is now the first column of the results sheet (A), before Roll No.: `ExamExcelWriter` writes a running 1…n, and `ExportFormulaEngine.FIRST_MARK_COL` moved 3 → 4 so `{Total Obtained}`/`{Percentage}`/`Result` still point at the marks, not the serial. With two subjects the layout is A S.No., B Roll, C Name, D/E marks, F Total, G Total Max, H Percentage, I Result.
  - **PDF export.** `export/ExamPdfTable.kt` (pure: stacks header + rows into pages at a font/row budget, repeats the header on every page, formats marks and trims trailing empties) and `export/ExamPdfWriter.kt` (platform `PdfDocument`, landscape A4, thin rules, "Fail" bold italic, footer `Page x of y`, cache file in `<cacheDir>/exams/` shared as `application/pdf`). Reached from the results overflow menu as "Export as PDF". Custom (`ExportColumnKind.CUSTOM`) columns are left out of the PDF — a custom formula has no value to print — and the omission is stated in the action's supporting text. `ExamPdfTableTest` pins page stacking, header repetition and the Fail emphasis decision.
  - **Summary sheet.** `export/ExamSummarySheet.kt` builds the metric rows and formulas from the example mark-slip, and `ExamExcelWriter.writeSummarySheet` appends a second sheet named `Summary` (`Metrics`/`Value` header, `REGISTERED`, `APPEARED`, `PASSED`, `PASS %`, `AVERAGE %`, bands `0-32`…`95-ABOVE`). Ranges point at the Results sheet's Percentage/Result columns, so the summary recalculates with the marks; bands are cumulative `COUNTIF` thresholds and the lowest band subtracts the rest so blank rows cannot leak in. Written only when both a RESULT and a PERCENTAGE column exist and at least one result row does. The `Absent` row from the example is deliberately omitted — the app has no absence tracking. The default sheet is renamed `Sheet1` → `Results` and `XlsxDimension.patch` fixed to rewrite `<dimension>` on every worksheet, not just sheet 1. `ExamSummarySheetTest` (9) + `ExamExcelWriterTest` (3 new) green; workbook inspected with openpyxl (sheets `Results`/`Summary`, `A1:B14`, live formulas, thin borders, `cellIs "Fail"` rule). The formula set was then replayed against the example mark-slip's own numbers in a spreadsheet: 39 registered / 39 appeared / 39 passed / 100 % / 80.77 % average and bands 0, 0, 1, 8, 22, 8, 0 — identical to the example, and a range containing two markless rows leaves both out of the bands (no blank row leaks into `0-32`).
  - **Exam import.** New full-screen route `exam/import/{classId}`, opened from `ExamListScreen`'s "Import" action. `excelimport/ExamImportModel.kt` is pure: `plan()` finds the name/roll columns (S.No. accepted as a roll fallback; father/mother/parent/guardian/teacher names excluded), treats every other numeric-or-marked column as a subject, skips Total/Percentage/Result/Grade/Remarks, and reads `(max/pass)` or a trailing max off the header (`Math (100)`, `Science (50/18)`, `Hindi - 80`), defaulting pass to 35 %. `matchRows()` matches roll first (`01` = `1`) then name, and counts unmatched, duplicate and unreadable cells instead of writing them. `writePlan()` builds exam → subjects → only the marks actually entered (blank/`Ab` stays absent, no zero invented), capping marks above a subject's max and counting them. `viewmodel/ExamImportViewModel.kt` re-reads the picked file whenever the sheet or row window changes (max/pass edits survive when the header is unchanged), commits the three writes and deletes the exam if a later call fails, so a partial import is never left behind. `ui/ExamImportScreen.kt` shows the file, sheet + row window, exam name, identity dropdowns, subject checkboxes with editable max/pass, and a matched-student preview before the pinned "Create exam with N students" button. `ExamImportModelTest` — 16 tests.
  - Full gate green: `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --console=plain` → 95 tests, 0 failures, 0 warnings.
- **Horizontal summary, fraction percentages, portrait PDF (2026-11-03):**
  - **Fractions.** `ExportColumnDefaults.PERCENTAGE_TEMPLATE` is now `IF({Total Max}=0,0,{Total Obtained}/{Total Max})`; old saved layouts that still end in `*100` are deliberately left alone. `StudentResult.percentage` stays 0–100 for the app and PDF, so only workbook values changed. Every workbook percentage (Results `Percentage`, summary `Pass %`, subject average, class pass %, class average) carries `NumFormat.standard_10` (`0.00%`), asserted by `numFmtId="10"` in the written zip.
  - **Summary replaces the vertical sheet.** `ExamSummarySheet` now mirrors `.my_folder/UPDATED-FINAL RESULT SHEET.xlsx`: row 1 title (`Exam — Class X`) with `CLASS-WISE` in N1, row 2 metrics, one row per subject labelled `HINDI (40)`, the `OVER ALL CLASS` row last, and the single four-cell class-wise block in N–Q of that row. Passed/pass % use each subject's configured pass mark; the seven bands keep the example's fixed percentages of the subject max, including its mixed `INT`/unrounded boundaries (a decimal mark can therefore sit in no band — reproduced, not "fixed"). Class-wide appeared is `MAX` of the per-subject appeared counts, and division is guarded with `IF(appeared=0,0,…)`.
  - **Partial layouts.** The summary is written whenever students and subjects exist. If `Percentage` or `Result` was deleted from the layout, the marks-only cells still compute and the impossible ones stay blank instead of dropping the whole sheet.
  - **PDF.** `ExamPdfWriter` is portrait A4 with `Exam — Class` in the heading. `ExamPdfTable.sections()` repeats S.No./Roll No./Student Name on every column page and keeps computed columns last, so Total/Total Max/Percentage/Result trail the subjects onto the final page. Name is always the widest column, data headers wrap to two lines, and the footer is `Page x of y`.
  - **Wiring.** `ExamResultViewModel` observes `SchoolClassRepository` for `className`; `ExamDetailScreen` passes it to both writers; `ExportHeading` is the one place the exam + class title is assembled.
  - **Tests.** 105 tests green, zero warnings: `ExamSummarySheetTest` (block shape, baked pass marks, band chain, class-wise MAX, guards, missing columns), `ExamExcelWriterTest` (fraction formula, `numFmtId="10"`, horizontal summary, partial summary, `A1:Q5`), `ExamPdfTableTest` (repeated identity columns, computed columns last, class heading), `ExportColumnsTest` (no `*100`, marks-only summary).
- **Last checkpoint:** 2026-11-03
