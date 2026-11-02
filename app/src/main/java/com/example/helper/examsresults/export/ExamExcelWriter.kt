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
    ): File {
        val dir = File(context.cacheDir, "exams")
        if (!dir.exists()) dir.mkdirs()
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(file).use { out ->
            out.write(buildWorkbook(subjects, results, columns))
        }
        return file
    }

    /** The workbook bytes, without touching the filesystem (unit-tested). */
    internal fun buildWorkbook(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
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
        val resultIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.RESULT }
        val percentageIndex = ordered.indexOfFirst { it.kind == ExportColumnKind.PERCENTAGE }
        // The summary sheet is a set of live formulas over the results sheet, so
        // it needs the two columns whose cells it points at. A layout that
        // deleted either one still exports — just without a summary.
        if (results.isNotEmpty() && resultIndex >= 0 && percentageIndex >= 0) {
            writeSummarySheet(
                excel = excel,
                resultsSheet = RESULTS_SHEET_NAME,
                firstRow = startRow,
                lastRow = lastRow,
                percentageColumn = ExportFormulaEngine.columnLetter(computedStartCol + percentageIndex),
                resultColumn = ExportFormulaEngine.columnLetter(computedStartCol + resultIndex),
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
     * Writes the summary block on its own sheet: a title, a two-cell header,
     * the linked formulas, and the same thin border every results cell gets.
     */
    private fun writeSummarySheet(
        excel: Excel,
        resultsSheet: String,
        firstRow: Int,
        lastRow: Int,
        percentageColumn: String,
        resultColumn: String,
    ) {
        val rows = ExamSummarySheet.rows(
            resultsSheet = resultsSheet,
            firstRow = firstRow,
            lastRow = lastRow,
            nameColumn = EXAM_NAME_COLUMN,
            percentageColumn = percentageColumn,
            resultColumn = resultColumn,
        )
        val sheet = excel[ExamSummarySheet.SHEET_NAME]
        sheet.appendRow(listOf(TextCellValue(ExamSummarySheet.TITLE), null))
        sheet.appendRow(
            listOf(
                TextCellValue(ExamSummarySheet.HEADER_LABEL),
                TextCellValue(ExamSummarySheet.HEADER_VALUE),
            ),
        )
        rows.forEach { row ->
            sheet.appendRow(listOf(TextCellValue(row.label), FormulaCellValue(row.formula)))
        }
        sheet.setColumnWidth(0, 18.0)
        sheet.setColumnWidth(1, 12.0)
        applyBorders(sheet = sheet, lastRow = rows.size + 1, lastColumn = 1)
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
