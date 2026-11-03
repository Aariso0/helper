package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject

/**
 * One line of the summary: the teacher-facing label plus the live Excel
 * formulas behind every number. A `null` cell means the current layout cannot
 * compute that figure (for example the Percentage column was deleted); it is
 * left blank rather than guessed.
 *
 * [metricCells] is B..M in sheet order — registered, appeared, passed, pass %,
 * subject average, then the seven score bands. [classWise] is N..Q and only the
 * `OVER ALL CLASS` row fills it.
 */
internal data class SummaryRow(
    val label: String,
    val registered: String?,
    val appeared: String?,
    val passed: String?,
    val passPercent: String?,
    val average: String?,
    val bands: List<String?>,
    val classWise: List<String?> = emptyList(),
) {
    fun metricCells(): List<String?> =
        listOf(registered, appeared, passed, passPercent, average) + bands
}

/**
 * The horizontal analysis block from the teacher's example workbook, moved to
 * its own `Summary` sheet and pointed at the results sheet instead of copied
 * numbers.
 *
 * One row per subject carries the subject's own max marks in its label
 * (`HINDI (40)`), so the live formulas bake the max at export time. Passed and
 * pass % use the subject's configured pass mark — deliberately not the
 * example's fixed 33 % — while the seven score bands keep the example's fixed
 * 33/50/60/75/90/95 % of the max.
 *
 * The formulas copy the example exactly, including its mixed rounding: the
 * "below" and "passed" boundaries use `INT(max*33/100)` while the band
 * thresholds stay unrounded. With decimal marks a student can therefore sit in
 * no band (26 of 80 is "passed" but below the unrounded 26.4 boundary); that is
 * the example's own behaviour and is reproduced rather than "fixed".
 *
 * Pure string building, so the exact formulas are unit-tested without a
 * workbook.
 */
internal object ExamSummarySheet {

    const val SHEET_NAME = "Summary"

    /** 1-based row of the column headers; the title sits in row 1. */
    const val HEADER_ROW = 2

    /** 1-based row of the first subject; the overall row follows the last. */
    const val FIRST_DATA_ROW = 3

    const val SUBJECT_HEADER = "Subjects."
    const val OVERALL_LABEL = "OVER ALL CLASS"
    const val CLASS_WISE_TITLE = "CLASS-WISE"

    /** 1-based column of `CLASS-WISE` and of the first class-wise value (N, O …). */
    const val CLASS_WISE_COLUMN = 14

    /** 1-based column of the "Below 33%" band (G). */
    const val FIRST_BAND_COLUMN = 7

    /** 1-based column of the per-subject pass share (E) and average (F). */
    const val PASS_PERCENT_COLUMN = 5
    const val AVERAGE_COLUMN = 6

    /** 1-based column of the class-wide pass share (P) and average (Q). */
    const val CLASS_PASS_PERCENT_COLUMN = CLASS_WISE_COLUMN + 2
    const val CLASS_AVERAGE_COLUMN = CLASS_WISE_COLUMN + 3

    /** Last used column (Q): the class-wise average. */
    const val LAST_COLUMN = 17

    val METRIC_HEADERS = listOf(
        "No. of Students Registered",
        "No. of Students Appeared",
        "Passed",
        "Pass %",
        "Subject Average",
        "Below 33%",
        "33-49%",
        "50-59%",
        "60-74%",
        "75-89%",
        "90-94%",
        "95% & Above",
    )

    /** Score bands, low to high. The first has no upper boundary of its own. */
    val BAND_FLOORS = listOf(33, 50, 60, 75, 90, 95)
    val BAND_LABELS = listOf(
        "Below 33%",
        "33-49%",
        "50-59%",
        "60-74%",
        "75-89%",
        "90-94%",
        "95% & Above",
    )

    /** The four cells of the single class-wise block, in column order N..Q. */
    val CLASS_WISE_HEADERS = listOf(
        "Total Student Appeared",
        "Passed",
        "Pass %",
        "Class Average",
    )

    /**
     * Rows for every subject followed by the `OVER ALL CLASS` row.
     *
     * [percentageColumn] and [resultColumn] are the summary's own columns on
     * the results sheet. Either may be absent because the teacher deleted it
     * from the layout; the marks-only figures are still produced and the
     * impossible ones stay blank.
     */
    fun rows(
        resultsSheet: String,
        firstRow: Int,
        lastRow: Int,
        nameColumn: String,
        subjects: List<ExamSubject>,
        firstMarkColumn: Int = 4,
        percentageColumn: String? = null,
        resultColumn: String? = null,
    ): List<SummaryRow> {
        val sheet = resultsSheet.replace("'", "''")
        fun range(column: String) = "'$sheet'!\$$column\$$firstRow:\$$column\$$lastRow"
        fun countIf(range: String, criteria: String) = """COUNTIF($range,"$criteria")"""

        val names = range(nameColumn)
        val percentages = percentageColumn?.let(::range)
        val results = resultColumn?.let(::range)
        val registered = "COUNTA($names)"

        val subjectRows = subjects.mapIndexed { index, subject ->
            val row = FIRST_DATA_ROW + index
            val marks = range(ExportFormulaEngine.columnLetter(firstMarkColumn + index))
            val max = subject.maxMarks
            val appearedRef = "C$row"
            val passedRef = "D$row"

            val bandCells = BAND_LABELS.indices.map { bandIndex ->
                "${ExportFormulaEngine.columnLetter(FIRST_BAND_COLUMN + bandIndex)}$row"
            }
            val bands = BAND_LABELS.indices.map { bandIndex ->
                if (bandIndex == 0) {
                    // The example's rounded lower boundary: blanks are not
                    // counted, and a mark below the floor is "below 33%".
                    """COUNTIF($marks,"<"&INT($max*33/100))"""
                } else {
                    val floor = BAND_FLOORS[bandIndex - 1]
                    // Example: count(>=floor) minus every higher band's count.
                    val higher = bandCells.drop(bandIndex + 1).joinToString("-")
                    val count = """COUNTIF($marks,">="&$max*$floor/100)"""
                    if (higher.isEmpty()) count else "$count-$higher"
                }
            }

            SummaryRow(
                label = "${subject.name} ($max)",
                registered = registered,
                appeared = countIf(marks, ">=0"),
                passed = countIf(marks, ">=${subject.passMarks}"),
                passPercent = "IF($appearedRef=0,0,$passedRef/$appearedRef)",
                average = "IF($appearedRef=0,0,SUM($marks)/$appearedRef/$max)",
                bands = bands,
            )
        }

        val overallRow = FIRST_DATA_ROW + subjects.size
        val appeared = "MAX(C$FIRST_DATA_ROW:C${overallRow - 1})"
        val passed = results?.let { countIf(it, "Pass") }
        val passPercent = passed?.let { "IF(C$overallRow=0,0,D$overallRow/C$overallRow)" }
        val average = percentages?.let { "IF(C$overallRow=0,0,SUM($it)/C$overallRow)" }

        val overallBandCells = BAND_LABELS.indices.map { bandIndex ->
            "${ExportFormulaEngine.columnLetter(FIRST_BAND_COLUMN + bandIndex)}$overallRow"
        }
        val overallBands = if (percentages == null) {
            List(BAND_LABELS.size) { null }
        } else {
            BAND_LABELS.indices.map { bandIndex ->
                if (bandIndex == 0) {
                    // Overall bands read the percentage share, like the example:
                    // the criteria are fractions (`33%`), not mark counts.
                    """COUNTIF($percentages,"<"&33%)"""
                } else {
                    val floor = BAND_FLOORS[bandIndex - 1]
                    val higher = overallBandCells.drop(bandIndex + 1).joinToString("-")
                    val count = """COUNTIF($percentages,">="&$floor%)"""
                    if (higher.isEmpty()) count else "$count-$higher"
                }
            }
        }

        val overall = SummaryRow(
            label = OVERALL_LABEL,
            registered = registered,
            appeared = appeared,
            passed = passed,
            passPercent = passPercent,
            average = average,
            bands = overallBands,
            classWise = listOf(
                "C$overallRow",
                passed?.let { "D$overallRow" },
                passPercent?.let { "E$overallRow" },
                average?.let { "F$overallRow" },
            ),
        )

        return subjectRows + overall
    }
}
