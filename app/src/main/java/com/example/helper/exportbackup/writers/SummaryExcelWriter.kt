package com.example.helper.exportbackup.writers

import com.example.helper.exportbackup.summarize.MonthlySummary
import com.example.helper.exportbackup.summarize.PERCENTAGE_RULE
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.CellValue
import com.gyanoba.kexcel.sheet.IntCellValue
import com.gyanoba.kexcel.sheet.TextCellValue
import java.io.IOException
import java.io.OutputStream

/**
 * Excel writer over the one summary model (`export-backup` card:
 * `kexcel-android`, the same library roster imports with — now writing).
 * Produces a real `.xlsx` with one "Attendance" sheet: title block, column
 * header, per-student rows, per-class totals, grand total.
 */
object SummaryExcelWriter {

    private val COLUMN_HEADER = listOf(
        "Class",
        "Student",
        "Roll no.",
        "Present",
        "Absent",
        "Late",
        "Excused",
        "Days tracked",
        "Present %",
    )

    fun write(summary: MonthlySummary, monthLabel: String, scopeLabel: String, output: OutputStream) {
        val excel = Excel.createExcel()
        val sheetName = excel.getDefaultSheet() ?: "Sheet1"
        val sheet = excel[sheetName]

        sheet.appendRow(listOf<CellValue?>(TextCellValue("Attendance summary — $monthLabel")))
        sheet.appendRow(listOf<CellValue?>(TextCellValue("Scope: $scopeLabel")))
        sheet.appendRow(listOf<CellValue?>(TextCellValue(PERCENTAGE_RULE)))
        sheet.appendRow(listOf<CellValue?>(TextCellValue("\"No records\" means attendance was never marked that month.")))
        sheet.appendRow(emptyList())

        val header: List<CellValue?> = COLUMN_HEADER.map { TextCellValue(it) }
        sheet.appendRow(header)
        summary.classes.forEach { classSummary ->
            classSummary.students.forEach { student ->
                sheet.appendRow(
                    listOf(
                        TextCellValue(classSummary.className),
                        TextCellValue(student.studentName),
                        TextCellValue(student.rollNumber.orEmpty()),
                        IntCellValue(student.present),
                        IntCellValue(student.absent),
                        IntCellValue(student.late),
                        IntCellValue(student.excused),
                        IntCellValue(student.trackedRecords),
                        TextCellValue(percentLabel(student.presentPercentage)),
                    ),
                )
            }
            sheet.appendRow(
                listOf(
                    TextCellValue(classSummary.className),
                    TextCellValue("CLASS TOTAL"),
                    TextCellValue(""),
                    IntCellValue(classSummary.present),
                    IntCellValue(classSummary.absent),
                    IntCellValue(classSummary.late),
                    IntCellValue(classSummary.excused),
                    IntCellValue(classSummary.trackedDays),
                    TextCellValue(percentLabel(classSummary.presentPercentage)),
                ),
            )
        }
        sheet.appendRow(
            listOf(
                TextCellValue("ALL CLASSES"),
                TextCellValue("TOTAL"),
                TextCellValue(""),
                IntCellValue(summary.present),
                IntCellValue(summary.absent),
                IntCellValue(summary.late),
                IntCellValue(summary.excused),
                IntCellValue(summary.trackedDays),
                TextCellValue(percentLabel(summary.presentPercentage)),
            ),
        )

        COLUMN_WIDTHS.forEachIndexed { index, width -> sheet.setColumnWidth(index, width) }
        excel.rename(sheetName, "Attendance")

        val bytes = excel.encode() ?: throw IOException("Could not encode the workbook")
        output.write(bytes)
    }

    private val COLUMN_WIDTHS = listOf(18.0, 26.0, 10.0, 9.0, 9.0, 8.0, 9.0, 13.0, 11.0)
}
