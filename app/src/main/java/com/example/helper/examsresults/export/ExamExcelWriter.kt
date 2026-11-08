package com.example.helper.examsresults.export

import android.content.Context
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import com.example.helper.exportbackup.writers.XlsxDimension
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.number_format.NumFormat
import com.gyanoba.kexcel.sheet.Border
import com.gyanoba.kexcel.sheet.BorderStyle
import com.gyanoba.kexcel.sheet.CellIndex
import com.gyanoba.kexcel.sheet.CellStyle
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.DoubleCellValue
import com.gyanoba.kexcel.sheet.FormulaCellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.Sheet
import com.gyanoba.kexcel.sheet.TextCellValue
import com.gyanoba.kexcel.utils.ExcelColor
import com.gyanoba.kexcel.utils.HorizontalAlign
import com.gyanoba.kexcel.utils.VerticalAlign
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ExamExcelWriter {
    fun write(
        context: Context,
        examName: String,
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
        className: String = "",
        schoolName: String = "",
        sessionLabel: String = "",
        teacherName: String = "",
        teacherDesignation: String = "",
    ): File {
        val dir = File(context.cacheDir, "exams")
        if (!dir.exists()) dir.mkdirs()
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { out ->
            out.write(
                buildWorkbook(
                    subjects = subjects,
                    results = results,
                    columns = columns,
                    examName = examName,
                    className = className,
                    schoolName = schoolName,
                    sessionLabel = sessionLabel,
                    teacherName = teacherName,
                    teacherDesignation = teacherDesignation,
                ),
            )
        }
        return file
    }

    /** The workbook bytes, without touching the filesystem (unit-tested). */
    internal fun buildWorkbook(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
        examName: String = "",
        className: String = "",
        schoolName: String = "",
        sessionLabel: String = "",
        teacherName: String = "",
        teacherDesignation: String = "",
    ): ByteArray {
        // The caller passes the saved layout; an empty list means the teacher
        // deleted every computed column and is exported as-is.
        val ordered = columns.filter { it.enabled }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val issues = ExportFormulaEngine.validate(ordered, subjects)
        if (issues.isNotEmpty()) {
            throw IOException(
                issues.joinToString(" ") { issue -> "${issue.columnLabel}: ${issue.message}" },
            )
        }

        val excel = Excel.createExcel()
        // The template's only sheet is called "Sheet1"; a teacher reading the
        // workbook deserves the same word the app uses for the tab.
        excel.getDefaultSheet()?.let { default ->
            if (default != RESULTS_SHEET_NAME) excel.rename(default, RESULTS_SHEET_NAME)
        }
        val sheet = excel[RESULTS_SHEET_NAME]

        val totalColumns = 3 + subjects.size + ordered.size
        val lastColumn = totalColumns - 1
        // A is S.No., B Roll No., C Student Name, so the marks start at D.
        val firstMarkCol = 4
        val computedStartCol = firstMarkCol + subjects.size

        writeBanner(
            sheet = sheet,
            totalColumns = totalColumns,
            examName = examName,
            className = className,
            schoolName = schoolName,
            sessionLabel = sessionLabel,
            teacherName = teacherName,
            teacherDesignation = teacherDesignation,
        )

        val headers = MutableList<CellValue?>(totalColumns) { null }
        headers[0] = TextCellValue("S.No.")
        headers[1] = TextCellValue("Roll No.")
        headers[2] = TextCellValue("Student Name")
        subjects.forEachIndexed { index, subject ->
            headers[3 + index] = TextCellValue(subject.name)
        }
        ordered.forEachIndexed { index, column ->
            headers[computedStartCol - 1 + index] = TextCellValue(column.label)
        }
        sheet.appendRow(headers)

        val maxMarksRow = MutableList<CellValue?>(totalColumns) { null }
        maxMarksRow[2] = TextCellValue("/Max.Marks")
        subjects.forEachIndexed { index, subject ->
            maxMarksRow[3 + index] = IntCellValue(subject.maxMarks.toLong())
        }
        sheet.appendRow(maxMarksRow)

        // Two header rows: S.No. and Roll No. span both, the name column keeps
        // "/Max.Marks" beneath it, and every computed column spans both.
        mergeHeader(sheet, column = 0)
        mergeHeader(sheet, column = 1)
        ordered.indices.forEach { index -> mergeHeader(sheet, column = computedStartCol - 1 + index) }

        subjects.forEachIndexed { index, _ ->
            setStyle(sheet, columnIndex = 3 + index, rowIndex = HEADER_ROW - 1) {
                it.copyWith(backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.SUBJECT_CYAN))
            }
            setStyle(sheet, columnIndex = 3 + index, rowIndex = MAX_MARKS_ROW - 1) {
                it.copyWith(backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.HEADER_YELLOW))
            }
        }
        setStyle(sheet, columnIndex = 2, rowIndex = MAX_MARKS_ROW - 1) {
            it.copyWith(boldVal = true, horizontalAlignVal = HorizontalAlign.Center)
        }

        val lastRow = if (results.isEmpty()) 0 else START_ROW + results.size - 1
        val dataRows = if (results.isEmpty()) null else START_ROW..lastRow

        results.forEachIndexed { index, result ->
            val rowNum = START_ROW + index
            val row = MutableList<CellValue?>(totalColumns) { null }
            // S.No. is a position, not data: it renumbers itself on every export.
            row[0] = IntCellValue(index + 1)
            row[1] = TextCellValue(result.student.rollNumber.orEmpty())
            row[2] = TextCellValue(result.student.name)

            subjects.forEachIndexed { subjectIndex, subject ->
                // Unentered marks stay blank: no invented zero in the sheet.
                // An absent subject is its own state and prints `ABT`, which
                // SUM/COUNTIF/RANK ignore as text.
                val obtained = result.marksBySubject[subject.id]
                row[3 + subjectIndex] = when {
                    subject.id in result.absentSubjectIds -> TextCellValue(ABSENT_LABEL)
                    else -> obtained?.let(::numberCell)
                }
            }

            ordered.forEachIndexed { columnIndex, column ->
                row[computedStartCol - 1 + columnIndex] = when (column.kind) {
                    ExportColumnKind.TOTAL_MAX -> IntCellValue(result.totalMax.toInt())
                    else -> {
                        val formula = ExportFormulaEngine
                            .resolve(column, ordered, subjects, rowNum, dataRows)
                            .getOrElse { error ->
                                throw IOException("${column.label}: ${error.message}")
                            }
                        // The engine prints formulas with a leading "=" for
                        // the editor; the stored cell formula must not have
                        // one, or Excel rewrites/repairs it.
                        FormulaCellValue(formula.removePrefix("="))
                    }
                }
            }
            sheet.appendRow(row)
        }

        val totalMax = subjects.sumOf { it.maxMarks }
        val resultIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.RESULT }
        val percentageIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.PERCENTAGE }
        val totalObtainedIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.TOTAL_OBTAINED }

        // Every percentage in the workbook is a share (`0.8`), so the cell
        // format is what turns it into `80.00%`. A viewer that ignores the
        // format still gets a usable number, and the band formulas compare
        // against `33%` instead of `33`.
        if (percentageIndex >= 0) {
            val percentageCellColumn = computedStartCol + percentageIndex - 1
            results.indices.forEach { index ->
                setNumberFormat(
                    sheet = sheet,
                    columnIndex = percentageCellColumn,
                    rowIndex = START_ROW - 1 + index,
                    format = NumFormat.standard_10,
                )
            }
        }

        // The total keeps its real number for every formula; the custom format
        // only makes it read like the printed `205(400)`.
        if (totalObtainedIndex >= 0 && results.isNotEmpty()) {
            val totalCellColumn = computedStartCol + totalObtainedIndex - 1
            val format = NumFormat.custom("0.##\"($totalMax)\"")
            results.indices.forEach { index ->
                setNumberFormat(
                    sheet = sheet,
                    columnIndex = totalCellColumn,
                    rowIndex = START_ROW - 1 + index,
                    format = format,
                )
            }
        }

        // Marks are the cells a teacher edits: yellow, like the printed sheet.
        if (results.isNotEmpty()) {
            for (row in START_ROW - 1..lastRow - 1) {
                for (subjectIndex in subjects.indices) {
                    setStyle(sheet, columnIndex = 3 + subjectIndex, rowIndex = row) {
                        it.copyWith(backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.HEADER_YELLOW))
                    }
                }
            }
        }

        sheet.setColumnWidth(0, 5.0)
        sheet.setColumnWidth(1, 8.0)
        sheet.setColumnWidth(2, 28.0)
        for (subjectIndex in subjects.indices) {
            sheet.setColumnWidth(subjectIndex + 3, 9.0)
        }
        ordered.forEachIndexed { index, column ->
            val width = when (column.kind) {
                ExportColumnKind.TOTAL_OBTAINED -> 11.0
                ExportColumnKind.TOTAL_MAX -> 11.0
                ExportColumnKind.PERCENTAGE -> 9.0
                ExportColumnKind.RESULT -> 9.0
                ExportColumnKind.RANK -> 6.0
                ExportColumnKind.CUSTOM -> 14.0
            }
            sheet.setColumnWidth(computedStartCol + index - 1, width)
        }

        // Clean print-out lines: a thin border on every used cell, including
        // blank marks, is a default of every exported sheet.
        applyBorders(
            sheet = sheet,
            lastRow = maxOf(MAX_MARKS_ROW - 1, lastRow - 1),
            lastColumn = lastColumn,
        )

        // The summary repeats marks, names and (when present) the computed
        // columns. A layout that deleted Percentage or Result still gets a
        // summary: the marks-only cells are written and the impossible ones
        // stay blank.
        val percentageColumnName = if (percentageIndex >= 0) {
            ExportFormulaEngine.columnLetter(computedStartCol + percentageIndex)
        } else {
            null
        }
        val resultColumnName = if (resultIndex >= 0) {
            ExportFormulaEngine.columnLetter(computedStartCol + resultIndex)
        } else {
            null
        }

        if (results.isNotEmpty() && subjects.isNotEmpty()) {
            writeSummarySheet(
                excel = excel,
                examName = examName,
                className = className,
                schoolName = schoolName,
                sessionLabel = sessionLabel,
                teacherName = teacherName,
                teacherDesignation = teacherDesignation,
                resultsSheet = RESULTS_SHEET_NAME,
                firstRow = START_ROW,
                lastRow = lastRow,
                subjects = subjects,
                percentageColumn = percentageColumnName,
                resultColumn = resultColumnName,
            )
            writeSubjectTeacherSheet(
                excel = excel,
                examName = examName,
                className = className,
                schoolName = schoolName,
                sessionLabel = sessionLabel,
                teacherName = teacherName,
                teacherDesignation = teacherDesignation,
                resultsSheet = RESULTS_SHEET_NAME,
                firstRow = START_ROW,
                lastRow = lastRow,
                subjects = subjects,
                percentageColumn = percentageColumnName,
                resultColumn = resultColumnName,
            )
        }

        var bytes: ByteArray = excel.encode() ?: throw IOException("Could not encode the workbook")
        bytes = XlsxDimension.patch(bytes)

        // Empty sheet (no students or no layout) still gets the border flags;
        // only the Result emphasis needs a range to point at.
        val failRuleSqref = if (resultIndex < 0 || lastRow < START_ROW) {
            null
        } else {
            val resultColumn = ExportFormulaEngine.columnLetter(computedStartCol + resultIndex)
            "$resultColumn$START_ROW:$resultColumn$lastRow"
        }
        return WorkbookDecoration.patch(bytes, failRuleSqref)
    }

    /**
     * Rows 1–4 carry the school stamp the teacher's paper sheet has: school
     * name on blue, exam/session/class on yellow, the sheet title, and the
     * class teacher. Blank fields leave their cell empty instead of printing
     * brackets or `null`.
     */
    private fun writeBanner(
        sheet: Sheet,
        totalColumns: Int,
        examName: String,
        className: String,
        schoolName: String,
        sessionLabel: String,
        teacherName: String,
        teacherDesignation: String,
    ) {
        val lastColumn = totalColumns - 1

        val schoolRow = MutableList<CellValue?>(totalColumns) { null }
        schoolRow[0] = TextCellValue(schoolName.trim())
        sheet.appendRow(schoolRow)
        mergeRow(sheet, row = BANNER_ROW - 1, startColumn = 0, endColumn = lastColumn)
        setStyle(sheet, 0, BANNER_ROW - 1) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.BANNER_BLUE),
                boldVal = true,
                fontSizeVal = 14,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }

        val examRow = MutableList<CellValue?>(totalColumns) { null }
        val sessionStart = (lastColumn - 2).coerceIn(1, lastColumn)
        val sessionEnd = (lastColumn - 1).coerceIn(sessionStart, lastColumn)
        examRow[0] = TextCellValue(examName.trim())
        examRow[sessionStart] = TextCellValue(sessionLabel.trim())
        examRow[lastColumn] = TextCellValue(
            className.trim().takeIf { it.isNotEmpty() }?.let { "CLASS-$it" }.orEmpty(),
        )
        sheet.appendRow(examRow)
        mergeRow(sheet, BANNER_ROW, 0, sessionStart - 1)
        mergeRow(sheet, BANNER_ROW, sessionStart, sessionEnd)
        mergeRow(sheet, BANNER_ROW, lastColumn, lastColumn)
        setStyle(sheet, 0, BANNER_ROW) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.HEADER_YELLOW),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }
        setStyle(sheet, sessionStart, BANNER_ROW) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.HEADER_YELLOW),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }
        setStyle(sheet, lastColumn, BANNER_ROW) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.HEADER_YELLOW),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }

        val titleRow = MutableList<CellValue?>(totalColumns) { null }
        titleRow[0] = TextCellValue("CONSOLIDATED MARKS - STATEMENT")
        sheet.appendRow(titleRow)
        mergeRow(sheet, BANNER_ROW + 1, 0, lastColumn)
        setStyle(sheet, 0, BANNER_ROW + 1) {
            it.copyWith(
                boldVal = true,
                fontSizeVal = 11,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }

        val teacherLine = teacherName.trim().let { name ->
            if (name.isEmpty()) {
                ""
            } else {
                val designation = teacherDesignation.trim()
                if (designation.isEmpty()) {
                    "Name of Class Teacher:- $name"
                } else {
                    "Name of Class Teacher:- $name ($designation)"
                }
            }
        }
        val teacherRow = MutableList<CellValue?>(totalColumns) { null }
        teacherRow[0] = TextCellValue(teacherLine)
        sheet.appendRow(teacherRow)
        mergeRow(sheet, BANNER_ROW + 2, 0, lastColumn)
        setStyle(sheet, 0, BANNER_ROW + 2) {
            it.copyWith(verticalAlignVal = VerticalAlign.Center)
        }
    }

    /** Merges an identifying or computed header cell across the two header rows. */
    private fun mergeHeader(sheet: Sheet, column: Int) {
        mergeRow(sheet, row = HEADER_ROW - 1, startColumn = column, endColumn = column, rowEnd = MAX_MARKS_ROW - 1)
        setStyle(sheet, column, HEADER_ROW - 1) {
            it.copyWith(boldVal = true, horizontalAlignVal = HorizontalAlign.Center)
        }
    }

    private fun mergeRow(
        sheet: Sheet,
        row: Int,
        startColumn: Int,
        endColumn: Int,
        rowEnd: Int = row,
    ) {
        if (startColumn > endColumn) return
        if (startColumn == endColumn && row == rowEnd) return
        sheet.merge(
            CellIndex.indexByColumnRow(columnIndex = startColumn, rowIndex = row),
            CellIndex.indexByColumnRow(columnIndex = endColumn, rowIndex = rowEnd),
        )
    }

    /**
     * Writes the horizontal analysis block on its own sheet: the same banner as
     * page 1, a cream `RESULT- ANALYSIS` title, one row per subject, the
     * `OVER ALL CLASS` row, and the four-cell `CLASS-WISE` block to its right.
     * Every number is a live formula over the results sheet; only the labels
     * are text.
     */
    private fun writeSummarySheet(
        excel: Excel,
        examName: String,
        className: String,
        schoolName: String,
        sessionLabel: String,
        teacherName: String,
        teacherDesignation: String,
        resultsSheet: String,
        firstRow: Int,
        lastRow: Int,
        subjects: List<ExamSubject>,
        percentageColumn: String?,
        resultColumn: String?,
    ) {
        val rows = ExamSummarySheet.rows(
            resultsSheet = resultsSheet,
            firstRow = firstRow,
            lastRow = lastRow,
            nameColumn = EXAM_NAME_COLUMN,
            subjects = subjects,
            percentageColumn = percentageColumn,
            resultColumn = resultColumn,
        )
        val sheet = excel[ExamSummarySheet.SHEET_NAME]

        writeBanner(
            sheet = sheet,
            totalColumns = ExamSummarySheet.LAST_COLUMN,
            examName = examName,
            className = className,
            schoolName = schoolName,
            sessionLabel = sessionLabel,
            teacherName = teacherName,
            teacherDesignation = teacherDesignation,
        )

        val title = MutableList<CellValue?>(ExamSummarySheet.LAST_COLUMN) { null }
        title[0] = TextCellValue(RESULT_ANALYSIS_TITLE)
        title[ExamSummarySheet.CLASS_WISE_COLUMN - 1] = TextCellValue(ExamSummarySheet.CLASS_WISE_TITLE)
        sheet.appendRow(title)
        mergeRow(sheet, ExamSummarySheet.TITLE_ROW - 1, 0, ExamSummarySheet.CLASS_WISE_COLUMN - 2)
        mergeRow(
            sheet,
            ExamSummarySheet.TITLE_ROW - 1,
            ExamSummarySheet.CLASS_WISE_COLUMN - 1,
            ExamSummarySheet.LAST_COLUMN - 1,
        )
        setStyle(sheet, 0, ExamSummarySheet.TITLE_ROW - 1) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.TITLE_CREAM),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }
        setStyle(sheet, ExamSummarySheet.CLASS_WISE_COLUMN - 1, ExamSummarySheet.TITLE_ROW - 1) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.TITLE_CREAM),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }

        val header = mutableListOf<CellValue?>()
        header.add(TextCellValue(ExamSummarySheet.SUBJECT_HEADER))
        ExamSummarySheet.METRIC_HEADERS.forEach { header.add(TextCellValue(it)) }
        ExamSummarySheet.CLASS_WISE_HEADERS.forEach { header.add(TextCellValue(it)) }
        sheet.appendRow(header)

        rows.forEach { row ->
            val cells = mutableListOf<CellValue?>()
            cells.add(TextCellValue(row.label))
            row.metricCells().forEach { formula ->
                cells.add(formula?.let { FormulaCellValue(it) })
            }
            row.classWise.forEach { formula ->
                cells.add(formula?.let { FormulaCellValue(it) })
            }
            sheet.appendRow(cells)
        }

        val firstDataIndex = ExamSummarySheet.FIRST_DATA_ROW - 1
        rows.indices.forEach { index ->
            setNumberFormat(sheet, ExamSummarySheet.PASS_PERCENT_COLUMN - 1, firstDataIndex + index)
            setNumberFormat(sheet, ExamSummarySheet.AVERAGE_COLUMN - 1, firstDataIndex + index)
        }
        val overallIndex = firstDataIndex + rows.lastIndex
        setNumberFormat(sheet, ExamSummarySheet.CLASS_PASS_PERCENT_COLUMN - 1, overallIndex)
        setNumberFormat(sheet, ExamSummarySheet.CLASS_AVERAGE_COLUMN - 1, overallIndex)

        // The student-count column and every band cell carry the paper sheet's
        // own colours. The class-wise block is cream, title and values alike.
        fillColumn(
            sheet = sheet,
            columnIndex = 1,
            firstRow = firstDataIndex,
            lastRow = overallIndex,
            colour = ExportPalette.HEADER_YELLOW,
        )
        for (rowIndex in firstDataIndex..overallIndex) {
            fillBandCells(
                sheet = sheet,
                rowIndex = rowIndex,
                firstColumn = ExamSummarySheet.FIRST_BAND_COLUMN - 1,
                colours = SEVEN_BAND_COLOURS,
            )
        }
        for (rowIndex in ExamSummarySheet.HEADER_ROW - 1..overallIndex) {
            fillRow(
                sheet = sheet,
                rowIndex = rowIndex,
                firstColumn = ExamSummarySheet.CLASS_WISE_COLUMN - 1,
                lastColumn = ExamSummarySheet.LAST_COLUMN - 1,
                colour = ExportPalette.TITLE_CREAM,
            )
        }

        sheet.setColumnWidth(0, 24.0)
        for (columnIndex in 1..ExamSummarySheet.FIRST_BAND_COLUMN - 2) {
            sheet.setColumnWidth(columnIndex, 15.0)
        }
        for (columnIndex in ExamSummarySheet.FIRST_BAND_COLUMN - 1 until ExamSummarySheet.LAST_COLUMN) {
            sheet.setColumnWidth(columnIndex, 14.0)
        }
        applyBorders(
            sheet = sheet,
            lastRow = rows.size + ExamSummarySheet.TITLE_ROW,
            lastColumn = ExamSummarySheet.LAST_COLUMN - 1,
        )
    }

    /**
     * The page-3 sheet: the same banner and analysis rows, the five subject
     * bands, the subject teacher's name / designation / employee type, and the
     * class-wise block below the table. Every number is a live formula.
     */
    private fun writeSubjectTeacherSheet(
        excel: Excel,
        examName: String,
        className: String,
        schoolName: String,
        sessionLabel: String,
        teacherName: String,
        teacherDesignation: String,
        resultsSheet: String,
        firstRow: Int,
        lastRow: Int,
        subjects: List<ExamSubject>,
        percentageColumn: String?,
        resultColumn: String?,
    ) {
        val rows = ExamSubjectTeacherSheet.rows(
            resultsSheet = resultsSheet,
            firstRow = firstRow,
            lastRow = lastRow,
            nameColumn = EXAM_NAME_COLUMN,
            subjects = subjects,
            percentageColumn = percentageColumn,
            resultColumn = resultColumn,
        )
        val sheet = excel[ExamSubjectTeacherSheet.SHEET_NAME]

        writeBanner(
            sheet = sheet,
            totalColumns = ExamSubjectTeacherSheet.LAST_COLUMN,
            examName = examName,
            className = className,
            schoolName = schoolName,
            sessionLabel = sessionLabel,
            teacherName = teacherName,
            teacherDesignation = teacherDesignation,
        )

        val title = MutableList<CellValue?>(ExamSubjectTeacherSheet.LAST_COLUMN) { null }
        title[ExamSubjectTeacherSheet.CLASS_WISE_COLUMN - 1] = TextCellValue(RESULT_ANALYSIS_TITLE)
        sheet.appendRow(title)
        mergeRow(
            sheet,
            ExamSubjectTeacherSheet.TITLE_ROW - 1,
            ExamSubjectTeacherSheet.CLASS_WISE_COLUMN - 1,
            ExamSubjectTeacherSheet.LAST_COLUMN - 1,
        )
        setStyle(sheet, ExamSubjectTeacherSheet.CLASS_WISE_COLUMN - 1, ExamSubjectTeacherSheet.TITLE_ROW - 1) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.TITLE_CREAM),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }

        val header = mutableListOf<CellValue?>()
        header.add(TextCellValue(ExamSubjectTeacherSheet.SUBJECT_HEADER))
        ExamSubjectTeacherSheet.METRIC_HEADERS.forEach { header.add(TextCellValue(it)) }
        ExamSubjectTeacherSheet.BAND_LABELS.forEach { header.add(TextCellValue(it)) }
        header.add(TextCellValue("Name of Subject Teacher"))
        header.add(TextCellValue("Designation"))
        header.add(TextCellValue("Employee Type"))
        sheet.appendRow(header)

        rows.forEach { row ->
            val cells = mutableListOf<CellValue?>()
            cells.add(TextCellValue(row.label))
            row.metricCells().forEach { formula ->
                cells.add(formula?.let { FormulaCellValue(it) })
            }
            cells.add(TextCellValue(row.teacherName))
            cells.add(TextCellValue(row.teacherDesignation))
            cells.add(TextCellValue(row.employeeType))
            sheet.appendRow(cells)
        }

        val firstDataIndex = ExamSubjectTeacherSheet.FIRST_DATA_ROW - 1
        rows.indices.forEach { index ->
            setNumberFormat(sheet, ExamSubjectTeacherSheet.PASS_PERCENT_COLUMN - 1, firstDataIndex + index)
            setNumberFormat(sheet, ExamSubjectTeacherSheet.AVERAGE_COLUMN - 1, firstDataIndex + index)
        }
        val overallIndex = firstDataIndex + rows.lastIndex

        fillColumn(
            sheet = sheet,
            columnIndex = 1,
            firstRow = firstDataIndex,
            lastRow = overallIndex,
            colour = ExportPalette.HEADER_YELLOW,
        )
        for (rowIndex in firstDataIndex..overallIndex) {
            fillBandCells(
                sheet = sheet,
                rowIndex = rowIndex,
                firstColumn = ExamSubjectTeacherSheet.FIRST_BAND_COLUMN - 1,
                colours = FIVE_BAND_COLOURS,
            )
        }

        // The class-wise block sits below the table, as in the example.
        val classTitleRow = ExamSubjectTeacherSheet.FIRST_DATA_ROW + rows.size
        val classHeaderRow = classTitleRow + 1
        val classValueRow = classTitleRow + 2
        val classTitle = MutableList<CellValue?>(ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size) { null }
        classTitle[0] = TextCellValue(ExamSubjectTeacherSheet.CLASS_WISE_TITLE)
        sheet.appendRow(classTitle)
        mergeRow(sheet, classTitleRow - 1, 0, ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size - 1)
        setStyle(sheet, 0, classTitleRow - 1) {
            it.copyWith(
                backgroundColorHexVal = ExcelColor.fromHexString(ExportPalette.TITLE_CREAM),
                boldVal = true,
                horizontalAlignVal = HorizontalAlign.Center,
                verticalAlignVal = VerticalAlign.Center,
            )
        }
        val classHeader = MutableList<CellValue?>(ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size) { null }
        ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.forEachIndexed { index, text ->
            classHeader[index] = TextCellValue(text)
        }
        sheet.appendRow(classHeader)
        fillRow(
            sheet = sheet,
            rowIndex = classHeaderRow - 1,
            firstColumn = 0,
            lastColumn = ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size - 1,
            colour = ExportPalette.TITLE_CREAM,
        )
        val classValues = ExamSubjectTeacherSheet.classWiseValues(overallRow = overallIndex + 1)
            .map { formula -> formula?.let { FormulaCellValue(it) } }
        sheet.appendRow(classValues)
        fillRow(
            sheet = sheet,
            rowIndex = classValueRow - 1,
            firstColumn = 0,
            lastColumn = ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size - 1,
            colour = ExportPalette.TITLE_CREAM,
        )
        setNumberFormat(sheet, 3, classValueRow - 1)
        setNumberFormat(sheet, 4, classValueRow - 1)

        sheet.setColumnWidth(0, 24.0)
        sheet.setColumnWidth(1, 9.0)
        sheet.setColumnWidth(2, 9.0)
        sheet.setColumnWidth(3, 8.0)
        sheet.setColumnWidth(4, 8.0)
        sheet.setColumnWidth(5, 8.0)
        sheet.setColumnWidth(6, 8.0)
        sheet.setColumnWidth(7, 9.0)
        sheet.setColumnWidth(8, 10.0)
        for (columnIndex in ExamSubjectTeacherSheet.FIRST_BAND_COLUMN - 1 until
            ExamSubjectTeacherSheet.TEACHER_COLUMN - 1) {
            sheet.setColumnWidth(columnIndex, 9.0)
        }
        sheet.setColumnWidth(ExamSubjectTeacherSheet.TEACHER_COLUMN - 1, 22.0)
        sheet.setColumnWidth(ExamSubjectTeacherSheet.TEACHER_COLUMN, 16.0)
        sheet.setColumnWidth(ExamSubjectTeacherSheet.TEACHER_COLUMN + 1, 13.0)

        applyBorders(
            sheet = sheet,
            lastRow = overallIndex,
            lastColumn = ExamSubjectTeacherSheet.LAST_COLUMN - 1,
        )
        applyBorders(
            sheet = sheet,
            lastRow = classValueRow - 1,
            lastColumn = ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size - 1,
        )
    }

    /** Fills one cell without disturbing its value or number format. */
    private fun fillCell(sheet: Sheet, columnIndex: Int, rowIndex: Int, colour: String) {
        setStyle(sheet, columnIndex, rowIndex) {
            it.copyWith(backgroundColorHexVal = ExcelColor.fromHexString(colour))
        }
    }

    private fun fillRow(
        sheet: Sheet,
        rowIndex: Int,
        firstColumn: Int,
        lastColumn: Int,
        colour: String,
    ) {
        for (column in firstColumn..lastColumn) {
            fillCell(sheet, column, rowIndex, colour)
        }
    }

    private fun fillColumn(
        sheet: Sheet,
        columnIndex: Int,
        firstRow: Int,
        lastRow: Int,
        colour: String,
    ) {
        for (row in firstRow..lastRow) {
            fillCell(sheet, columnIndex, row, colour)
        }
    }

    private fun fillBandCells(
        sheet: Sheet,
        rowIndex: Int,
        firstColumn: Int,
        colours: List<String?>,
    ) {
        colours.forEachIndexed { index, colour ->
            if (colour != null) fillCell(sheet, firstColumn + index, rowIndex, colour)
        }
    }

    /** Applies a display format without disturbing borders already on the cell. */
    private fun setNumberFormat(
        sheet: Sheet,
        columnIndex: Int,
        rowIndex: Int,
        format: NumFormat = NumFormat.standard_10,
    ) {
        setStyle(sheet, columnIndex, rowIndex) { it.copyWith(numberFormat = format) }
    }

    private fun setStyle(
        sheet: Sheet,
        columnIndex: Int,
        rowIndex: Int,
        transform: (CellStyle) -> CellStyle,
    ) {
        val data = sheet.cell(
            CellIndex.indexByColumnRow(columnIndex = columnIndex, rowIndex = rowIndex),
        )
        data.cellStyle = transform(data.cellStyle ?: CellStyle())
    }

    /** Thin box border on all four sides, keeping each cell's number format. */
    private fun applyBorders(sheet: Sheet, lastRow: Int, lastColumn: Int) {
        for (row in 0..lastRow) {
            for (column in 0..lastColumn) {
                val data = sheet.cell(CellIndex.indexByColumnRow(columnIndex = column, rowIndex = row))
                data.cellStyle = (data.cellStyle ?: CellStyle()).copyWith(
                    leftBorderVal = THIN_BORDER,
                    rightBorderVal = THIN_BORDER,
                    topBorderVal = THIN_BORDER,
                    bottomBorderVal = THIN_BORDER,
                )
            }
        }
    }

    private val THIN_BORDER = Border(borderStyle = BorderStyle.Thin)

    private const val RESULT_ANALYSIS_TITLE = "RESULT- ANALYSIS"

    /** Band colours low to high; the lowest band keeps the plain fill. */
    private val SEVEN_BAND_COLOURS: List<String?> = listOf(
        null,
        ExportPalette.BAND_BLUE,
        ExportPalette.BAND_BLUE,
        ExportPalette.BAND_GREY,
        ExportPalette.BAND_GREEN,
        ExportPalette.BAND_AMBER,
        ExportPalette.BAND_AMBER,
    )

    private val FIVE_BAND_COLOURS: List<String?> = listOf(
        null,
        ExportPalette.BAND_BLUE,
        ExportPalette.BAND_GREY,
        ExportPalette.BAND_GREEN,
        ExportPalette.BAND_AMBER,
    )

    /** The "Student Name" column (A = S.No., B = Roll No., C = name). */
    private const val EXAM_NAME_COLUMN = "C"

    /** Workbook tab holding the results; the summary formulas point at it. */
    const val RESULTS_SHEET_NAME = "Results"

    /** 1-based rows: four banner rows and two header rows precede the data. */
    const val START_ROW = 7
    private const val HEADER_ROW = 5
    private const val MAX_MARKS_ROW = 6
    private const val BANNER_ROW = 1

    private fun numberCell(value: Double): CellValue =
        if (value % 1.0 == 0.0) IntCellValue(value.toLong()) else DoubleCellValue(value)
}
