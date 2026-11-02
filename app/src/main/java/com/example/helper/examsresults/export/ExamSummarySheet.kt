package com.example.helper.examsresults.export

/**
 * One line of the printable summary: the teacher-facing label and the live
 * Excel formula behind the number. Formulas carry no leading `=`, the same
 * convention [ExportFormulaEngine] uses — the library writes the text into
 * `<f>` verbatim.
 */
internal data class SummaryRow(val label: String, val formula: String)

/**
 * The summary block that sits at the bottom of the example mark-slip, moved to
 * its own `Summary` sheet and pointed at the results sheet instead of copied
 * numbers: registered / appeared / passed, the pass share, the average, and the
 * same seven score bands (0-32 … 95-ABOVE).
 *
 * Pure string building, so the exact formulas are unit-tested without a
 * workbook. The bands are cumulative differences of `COUNTIF` thresholds, the
 * way the example file does it, which keeps every band adjacent to its
 * neighbour and makes the seven counts add up to "appeared".
 *
 * The bands are read off the percentage column, not raw marks: an exam's total
 * maximum changes per exam, so a band boundary must be relative, and the
 * example's `E8*33/100` is exactly that relative boundary.
 */
internal object ExamSummarySheet {

    const val SHEET_NAME = "Summary"
    const val TITLE = "Summary"
    const val HEADER_LABEL = "Metric"
    const val HEADER_VALUE = "Value"

    /** The first row of the summary block's body (1-based, after title+header). */
    const val FIRST_ROW = 3

    /** Score bands, low to high; each one starts where the previous ends. */
    val BAND_FLOORS = listOf(33, 50, 60, 75, 90, 95)
    val BAND_LABELS = listOf("0-32", "33-49", "50-59", "60-74", "75-89", "90-94", "95-ABOVE")

    fun rows(
        resultsSheet: String,
        firstRow: Int,
        lastRow: Int,
        nameColumn: String,
        percentageColumn: String,
        resultColumn: String,
    ): List<SummaryRow> {
        val sheet = resultsSheet.replace("'", "''")
        fun range(column: String) = "'$sheet'!$column$firstRow:$column$lastRow"
        fun countIf(column: String, criteria: String) =
            """COUNTIF(${range(column)},"$criteria")"""

        val names = range(nameColumn)
        val percentages = range(percentageColumn)
        val results = range(resultColumn)

        val registered = "B${FIRST_ROW}"
        val appeared = "B${FIRST_ROW + 1}"
        val passed = "B${FIRST_ROW + 2}"

        val rows = mutableListOf(
            SummaryRow("REGISTERED", "COUNTA($names)"),
            SummaryRow(
                "APPEARED",
                // A row has a verdict only once marks are entered; the Result
                // cell is "" until then, so pass+fail is the appeared count.
                "${countIf(resultColumn, "Pass")}+${countIf(resultColumn, "Fail")}",
            ),
            SummaryRow("PASSED", countIf(resultColumn, "Pass")),
            SummaryRow("PASS %", "IF($appeared=0,0,$passed*100/$appeared)"),
            SummaryRow("AVERAGE %", "IF($appeared=0,0,SUM($percentages)/$appeared)"),
        )

        // 0-32 counts the appeared rows below the first boundary: subtracting
        // from the count keeps out rows that have no marks (a blank row's
        // percentage is a formula-computed 0, which COUNTIF would otherwise see).
        rows.add(SummaryRow(BAND_LABELS[0], "$appeared-${countIf(percentageColumn, ">=33")}"))
        BAND_LABELS.drop(1).forEachIndexed { index, label ->
            val floor = BAND_FLOORS[index]
            val next = BAND_FLOORS.getOrNull(index + 1)
            rows.add(
                SummaryRow(
                    label,
                    if (next == null) {
                        countIf(percentageColumn, ">=$floor")
                    } else {
                        "${countIf(percentageColumn, ">=$floor")}-${countIf(percentageColumn, ">=$next")}"
                    },
                ),
            )
        }
        return rows
    }
}
