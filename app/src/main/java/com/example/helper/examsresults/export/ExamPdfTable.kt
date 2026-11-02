package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import java.util.Locale

/** A plain printable table: one header row and one row per student. */
internal data class ExamPdfTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

/**
 * Turns the results into the strings a PDF sheet shows. Kept free of Android
 * imports so the numbering, the Pass/Fail words and the column choice are
 * unit-tested without a device.
 *
 * Only columns whose value the app can compute are printed: built-in kinds
 * carry their number already, while a custom column is an Excel formula the
 * device does not evaluate. A custom column is therefore left out of the PDF
 * instead of printing an empty cell that would read like a zero.
 */
internal object ExamPdfTableBuilder {

    private const val S_NO = "S.No."
    private const val ROLL = "Roll No."
    private const val NAME = "Student Name"

    fun build(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    ): ExamPdfTable {
        val printable = columns.filter { it.enabled && it.kind != ExportColumnKind.CUSTOM }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

        val headers = mutableListOf(S_NO, ROLL, NAME)
        subjects.forEach { subject -> headers.add("${subject.name} (${subject.maxMarks})") }
        printable.forEach { column -> headers.add(column.label) }

        val rows = results.mapIndexed { index, result ->
            val row = mutableListOf(
                (index + 1).toString(),
                result.student.rollNumber.orEmpty(),
                result.student.name,
            )
            subjects.forEach { subject ->
                row.add(result.marksBySubject[subject.id]?.let(::formatNumber).orEmpty())
            }
            printable.forEach { column -> row.add(valueOf(column, result)) }
            row
        }

        return ExamPdfTable(headers = headers, rows = rows)
    }

    private fun valueOf(column: ExportColumn, result: StudentResult): String =
        when (column.kind) {
            ExportColumnKind.TOTAL_OBTAINED -> formatNumber(result.totalObtained)
            ExportColumnKind.TOTAL_MAX -> formatNumber(result.totalMax)
            ExportColumnKind.PERCENTAGE -> "${formatNumber(result.percentage)}%"
            // A row with no marks has no verdict yet — same rule as the sheet.
            ExportColumnKind.RESULT -> when {
                result.marksBySubject.isEmpty() -> ""
                result.isPass -> "Pass"
                else -> "Fail"
            }
            ExportColumnKind.CUSTOM -> ""
        }

    /** `80`, `53.33`, never `80.0`: a printed mark should read like a mark. */
    fun formatNumber(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}
