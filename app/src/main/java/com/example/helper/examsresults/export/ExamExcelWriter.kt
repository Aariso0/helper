package com.example.helper.examsresults.export

import android.content.Context
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import com.example.helper.exportbackup.writers.XlsxDimension
import com.gyanoba.kexcel.Excel
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
import com.gyanoba.kexcel.number_format.NumFormat
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
    ): File {
        val dir = File(context.cacheDir, "exams")
        if (!dir.exists()) dir.mkdirs()
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { out ->
            out.write(buildWorkbook(subjects, results, columns, examName, className))
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

        val startRow = 2
        // A is S.No., B Roll No., C Student Name, so the marks start at D.
        val firstMarkCol = 4
        val computedStartCol = firstMarkCol + subjects.size

        val headers = mutableListOf<CellValue?>()
        headers.add(TextCellValue("S.No."))
        headers.add(TextCellValue("Roll No."))
        headers.add(TextCellValue("Student Name"))
        subjects.forEach { subject ->
            headers.add(TextCellValue("${subject.name} (${subject.maxMarks})"))
        }
        ordered.forEach { column -> headers.add(TextCellValue(column.label)) }
        sheet.appendRow(headers)

        results.forEachIndexed { index, result ->
            val rowNum = startRow + index
            val row = mutableListOf<CellValue?>()
            // S.No. is a position, not data: it renumbers itself on every export.
            row.add(IntCellValue(index + 1))
            row.add(TextCellValue(result.student.rollNumber.orEmpty()))
            row.add(TextCellValue(result.student.name))

            subjects.forEach { subject ->
                // Unentered marks stay blank: no invented zero in the sheet.
                val obtained = result.marksBySubject[subject.id]
                row.add(obtained?.let(::numberCell))
            }

            ordered.forEach { column ->
                row.add(
                    when (column.kind) {
                        ExportColumnKind.TOTAL_MAX -> IntCellValue(result.totalMax.toInt())
                        else -> {
                            val formula = ExportFormulaEngine
                                .resolve(column, ordered, subjects, rowNum)
                                .getOrElse { error ->
                                    throw IOException("${column.label}: ${error.message}")
                                }
                            // The engine prints formulas with a leading "=" for
                            // the editor; the stored cell formula must not have
                            // one, or Excel rewrites/repairs it.
                            FormulaCellValue(formula.removePrefix("="))
                        }
                    },
                )
            }
            sheet.appendRow(row)
        }

        val resultIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.RESULT }
        val percentageIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.PERCENTAGE }

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
                    rowIndex = startRow - 1 + index,
                    format = NumFormat.standard_10,
                )
            }
        }

        sheet.setColumnWidth(0, 6.0)
        sheet.setColumnWidth(1, 10.0)
        sheet.setColumnWidth(2, 26.0)
        for (subjectIndex in subjects.indices) {
            sheet.setColumnWidth(subjectIndex + 3, 14.0)
        }
        ordered.forEachIndexed { index, column ->
            val width = when (column.kind) {
                ExportColumnKind.TOTAL_OBTAINED -> 15.0
                ExportColumnKind.TOTAL_MAX -> 11.0
                ExportColumnKind.PERCENTAGE -> 12.0
                ExportColumnKind.RESULT -> 10.0
                ExportColumnKind.CUSTOM -> 14.0
            }
            sheet.setColumnWidth(computedStartCol + index - 1, width)
        }

        // Clean print-out lines: a thin border on every used cell, including
        // blank marks, is a default of every exported sheet.
        applyBorders(
            sheet = sheet,
            lastRow = results.size,
            lastColumn = 2 + subjects.size + ordered.size,
        )

        val lastRow = if (results.isEmpty()) 0 else startRow + results.size - 1
        // The summary repeats marks, names and (when present) the computed
        // columns. A layout that deleted Percentage or Result still gets a
        // summary: the marks-only cells are written and the impossible ones
        // stay blank.
        if (results.isNotEmpty() && subjects.isNotEmpty()) {
            writeSummarySheet(
                excel = excel,
                examName = examName,
                className = className,
                resultsSheet = RESULTS_SHEET_NAME,
                firstRow = startRow,
                lastRow = lastRow,
                subjects = subjects,
                percentageColumn = if (percentageIndex >= 0) {
                    ExportFormulaEngine.columnLetter(computedStartCol + percentageIndex)
                } else {
                    null
                },
                resultColumn = if (resultIndex >= 0) {
                    ExportFormulaEngine.columnLetter(computedStartCol + resultIndex)
                } else {
                    null
                },
            )
        }

        var bytes: ByteArray = excel.encode() ?: throw IOException("Could not encode the workbook")
        bytes = XlsxDimension.patch(bytes)

        // Empty sheet (no students or no layout) still gets the border flags;
        // only the Result emphasis needs a range to point at.
        val failRuleSqref = if (resultIndex < 0 || lastRow < startRow) {
            null
        } else {
            val resultColumn = ExportFormulaEngine.columnLetter(computedStartCol + resultIndex)
            "$resultColumn$startRow:$resultColumn$lastRow"
        }
        return WorkbookDecoration.patch(bytes, failRuleSqref)
    }

    /**
     * Writes the horizontal analysis block on its own sheet: a title with the
     * exam and class, one row per subject, the `OVER ALL CLASS` row, and the
     * single four-cell `CLASS-WISE` block to its right. Every number is a live
     * formula over the results sheet; only the labels are text.
     */
    private fun writeSummarySheet(
        excel: Excel,
        examName: String,
        className: String,
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

        val title = MutableList<CellValue?>(ExamSummarySheet.LAST_COLUMN) { null }
        title[0] = TextCellValue(ExportHeading.title(examName, className))
        title[ExamSummarySheet.CLASS_WISE_COLUMN - 1] = TextCellValue(ExamSummarySheet.CLASS_WISE_TITLE)
        sheet.appendRow(title)

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

        sheet.setColumnWidth(0, 24.0)
        for (columnIndex in 1..ExamSummarySheet.FIRST_BAND_COLUMN - 2) {
            sheet.setColumnWidth(columnIndex, 15.0)
        }
        for (columnIndex in ExamSummarySheet.FIRST_BAND_COLUMN - 1 until ExamSummarySheet.LAST_COLUMN) {
            sheet.setColumnWidth(columnIndex, 14.0)
        }
        applyBorders(
            sheet = sheet,
            lastRow = rows.size + 1,
            lastColumn = ExamSummarySheet.LAST_COLUMN - 1,
        )
    }

    /** Applies a display format without disturbing borders already on the cell. */
    private fun setNumberFormat(
        sheet: Sheet,
        columnIndex: Int,
        rowIndex: Int,
        format: NumFormat = NumFormat.standard_10,
    ) {
        val data = sheet.cell(CellIndex.indexByColumnRow(columnIndex = columnIndex, rowIndex = rowIndex))
        data.cellStyle = (data.cellStyle ?: CellStyle()).copyWith(numberFormat = format)
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

    /** The "Student Name" column (A = S.No., B = Roll No., C = name). */
    private const val EXAM_NAME_COLUMN = "C"

    /** Workbook tab holding the results; the summary formulas point at it. */
    const val RESULTS_SHEET_NAME = "Results"

    private fun numberCell(value: Double): CellValue =
        if (value % 1.0 == 0.0) IntCellValue(value.toLong()) else DoubleCellValue(value)
}
