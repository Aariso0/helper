package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import java.util.Locale

/**
 * A plain printable table: two header rows (subject names and their maxima)
 * and one row per student. [kinds] is parallel to [headers]; it is `null` for
 * the identifying columns and subject columns, and the computed column kind
 * otherwise, so the PDF can merge just the headers that deserve it.
 */
internal data class ExamPdfTable(
    val headers: List<String>,
    val subHeaders: List<String>,
    val kinds: List<ExportColumnKind?>,
    val rows: List<List<String>>,
)

/**
 * One readable column page: the three identifying columns plus a slice of the
 * data columns. Together the sections carry every column exactly once.
 */
internal data class ExamPdfSection(
    val headers: List<String>,
    val subHeaders: List<String>,
    val kinds: List<ExportColumnKind?>,
    val rows: List<List<String>>,
)

/** S.No., Roll No. and Student Name; repeated on every column page. */
internal const val EXAM_PDF_FIXED_COLUMNS = 3

/** Printed in place of a mark for a deliberately absent subject. */
internal const val ABSENT_LABEL = "ABT"

/**
 * Splits a wide table into portrait-friendly column pages. The identifying
 * columns are repeated so a continuation page still says whose marks it shows,
 * and the data columns keep their order — the computed Total / Percentage /
 * Result / Rank columns therefore end up on the last page.
 */
internal fun ExamPdfTable.sections(columnsPerPage: Int): List<ExamPdfSection> {
    val dataColumns = headers.indices.drop(EXAM_PDF_FIXED_COLUMNS)
    if (dataColumns.isEmpty()) {
        return listOf(
            ExamPdfSection(headers = headers, subHeaders = subHeaders, kinds = kinds, rows = rows),
        )
    }

    val pageSize = columnsPerPage.coerceAtLeast(1)
    return dataColumns.chunked(pageSize).map { chunk ->
        val indices = (0 until EXAM_PDF_FIXED_COLUMNS) + chunk
        ExamPdfSection(
            headers = indices.map { headers[it] },
            subHeaders = indices.map { subHeaders[it] },
            kinds = indices.map { kinds[it] },
            rows = rows.map { row -> indices.map { row[it] } },
        )
    }
}

/**
 * Turns the results into the strings a PDF sheet shows. Kept free of Android
 * imports so the numbering, the Pass/Fail words and the column choice are
 * unit-tested without a device.
 *
 * Only columns whose value the app can compute are printed: built-in kinds
 * carry their number already, while a custom column is an Excel formula the
 * device does not evaluate. A custom column is therefore left out of the PDF
 * instead of printing an empty cell that would read like a zero. The exam
 * maximum is stated once, in the Total Obtained header (`Total Obtained
 * (400)`), not in every student's cell.
 */
internal object ExamPdfTableBuilder {

    private const val S_NO = "S.No."
    private const val ROLL = "Roll No."
    private const val NAME = "Student Name"
    private const val MAX_MARKS = "/Max.Marks"

    fun build(
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    ): ExamPdfTable {
        val printable = columns.filter {
            it.enabled && it.kind != ExportColumnKind.CUSTOM && it.kind != ExportColumnKind.TOTAL_MAX
        }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val totalMax = subjects.sumOf { it.maxMarks }

        val headers = mutableListOf(S_NO, ROLL, NAME)
        subjects.forEach { subject -> headers.add(subject.name) }
        printable.forEach { column ->
            headers.add(
                if (column.kind == ExportColumnKind.TOTAL_OBTAINED) {
                    "${column.label} ($totalMax)"
                } else {
                    column.label
                },
            )
        }

        val subHeaders = mutableListOf("", "", MAX_MARKS)
        subjects.forEach { subject -> subHeaders.add(subject.maxMarks.toString()) }
        printable.forEach { subHeaders.add("") }

        val kinds = mutableListOf<ExportColumnKind?>()
        repeat(EXAM_PDF_FIXED_COLUMNS + subjects.size) { kinds.add(null) }
        printable.forEach { column -> kinds.add(column.kind) }

        val ranks = rankLabels(results)
        val rows = results.mapIndexed { index, result ->
            val row = mutableListOf(
                (index + 1).toString(),
                result.student.rollNumber.orEmpty(),
                result.student.name,
            )
            subjects.forEach { subject ->
                row.add(
                    if (subject.id in result.absentSubjectIds) {
                        ABSENT_LABEL
                    } else {
                        result.marksBySubject[subject.id]?.let(::formatNumber).orEmpty()
                    },
                )
            }
            printable.forEach { column ->
                row.add(valueOf(column, result, ranks[result.student.id].orEmpty()))
            }
            row
        }

        return ExamPdfTable(
            headers = headers,
            subHeaders = subHeaders,
            kinds = kinds,
            rows = rows,
        )
    }

    /**
     * `1 + count(greater totals)`, exactly what Excel's `RANK` computes, so a
     * tie prints 1, 2, 2, 4. A student with nothing recorded gets no rank.
     */
    private fun rankLabels(results: List<StudentResult>): Map<String, String> =
        results.associate { result ->
            val recorded = result.marksBySubject.isNotEmpty() || result.absentSubjectIds.isNotEmpty()
            result.student.id to if (recorded) {
                (1 + results.count { it.totalObtained > result.totalObtained }).toString()
            } else {
                ""
            }
        }

    private fun valueOf(column: ExportColumn, result: StudentResult, rank: String): String =
        when (column.kind) {
            ExportColumnKind.TOTAL_OBTAINED -> formatNumber(result.totalObtained)
            ExportColumnKind.TOTAL_MAX -> formatNumber(result.totalMax)
            ExportColumnKind.PERCENTAGE -> "${formatNumber(result.percentage)}%"
            // A row with no marks has no verdict yet — same rule as the sheet.
            // Any absence is a recorded state, so it does produce a verdict.
            ExportColumnKind.RESULT -> when {
                result.marksBySubject.isEmpty() && result.absentSubjectIds.isEmpty() -> ""
                result.isPass -> "Pass"
                else -> "Fail"
            }
            ExportColumnKind.RANK -> rank
            ExportColumnKind.CUSTOM -> ""
        }

    /** `80`, `53.33`, never `80.0`: a printed mark should read like a mark. */
    fun formatNumber(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}
