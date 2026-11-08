package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject

/**
 * One line of the page-3 subject-teacher sheet: the labels and live Excel
 * formulas for the subject columns, plus the three static teacher fields.
 * A `null` formula means the current layout cannot compute that figure.
 */
internal data class SubjectTeacherRow(
    val label: String,
    val strength: String?,
    val appeared: String?,
    val absent: String?,
    val passed: String?,
    val failed: String?,
    val total: String?,
    val passPercent: String?,
    val average: String?,
    val bands: List<String?>,
    val teacherTitle: String = "",
    val teacherName: String = "",
    val teacherDesignation: String = "",
    val employeeType: String = "",
) {
    /** `SMT. BHAVANA SONI`: the title is printed with the name, as page 3 does. */
    val teacherDisplayName: String
        get() = listOf(teacherTitle, teacherName).filter { it.isNotEmpty() }.joinToString(" ")

    fun metricCells(): List<String?> =
        listOf(strength, appeared, absent, passed, failed, total, passPercent, average) + bands
}

/**
 * The page-3 table: per-subject strength, attendance, verdicts, pass share,
 * average, the five score bands, and who teaches the subject.
 *
 * Every figure is a live formula over the results sheet. `Total` is
 * `Passed + Failed`; an absence has its own column and is not counted as a
 * failure there, so `Passed + Failed` equals `Appeared`. The overall row counts
 * students who failed the paper (including through an absence), which is why
 * its `Failed` can be larger than a single subject's.
 */
internal object ExamSubjectTeacherSheet {

    const val SHEET_NAME = "Subject Teacher"

    const val TITLE_ROW = 5
    const val HEADER_ROW = 6
    const val FIRST_DATA_ROW = 7

    const val SUBJECT_HEADER = "Subjects."
    const val OVERALL_LABEL = "OVER ALL CLASS"
    const val CLASS_WISE_TITLE = "CLASS-WISE"

    /** 1-based column of the five score bands (J..N). */
    const val FIRST_BAND_COLUMN = 10

    /** 1-based column of the per-subject pass share (H) and average (I). */
    const val PASS_PERCENT_COLUMN = 8
    const val AVERAGE_COLUMN = 9

    /** 1-based column of the teacher name, designation and employee type (O..Q). */
    const val TEACHER_COLUMN = 15
    const val LAST_COLUMN = 17

    /** 1-based column of the below-table class-wise block (A..E). */
    const val CLASS_WISE_COLUMN = 1

    val METRIC_HEADERS = listOf(
        "TOTAL Strength",
        "Appeared",
        "Absent",
        "Passed",
        "Failed",
        "Total",
        "Pass%",
        "Subject Average",
    )

    val BAND_LABELS = ExamSheetStats.FIVE_BAND_LABELS

    val CLASS_WISE_HEADERS = listOf(
        "Total Student Registered",
        "Total Student Appeared",
        "Passed",
        "Pass Percentage",
        "Class Average",
    )

    /**
     * Rows for every subject followed by the `OVER ALL CLASS` row.
     *
     * [percentageColumn] and [resultColumn] may be absent because the teacher
     * deleted them from the layout; the marks-only figures still work.
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
    ): List<SubjectTeacherRow> {
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
            SubjectTeacherRow(
                label = subject.name,
                strength = registered,
                appeared = countIf(marks, ">=0"),
                absent = countIf(marks, "ABT"),
                passed = countIf(marks, ">=${subject.passMarks}"),
                failed = "C$row-E$row",
                total = "E$row+F$row",
                passPercent = "IF(C$row=0,0,E$row/C$row)",
                average = "IF(C$row=0,0,SUM($marks)/C$row/$max)",
                bands = fiveBands(marks, max),
                teacherTitle = subject.title.orEmpty().trim(),
                teacherName = subject.teacherName.orEmpty().trim(),
                teacherDesignation = subject.teacherDesignation.orEmpty().trim(),
                employeeType = subject.employeeType.orEmpty().trim(),
            )
        }

        val overallRow = FIRST_DATA_ROW + subjects.size
        val firstSubjectRow = FIRST_DATA_ROW
        val lastSubjectRow = overallRow - 1
        val passed = results?.let { countIf(it, "Pass") }
        val failed = results?.let { countIf(it, "Fail") }
        val overallBands = if (percentages == null) {
            List(BAND_LABELS.size) { null }
        } else {
            listOf(
                """COUNTIF($percentages,"<"&33%)""",
                """COUNTIF($percentages,">="&33%)-COUNTIF($percentages,">="&60%)""",
                """COUNTIF($percentages,">="&60%)-COUNTIF($percentages,">="&75%)""",
                """COUNTIF($percentages,">="&75%)-COUNTIF($percentages,">="&90%)""",
                """COUNTIF($percentages,">="&90%)""",
            )
        }

        val overall = SubjectTeacherRow(
            label = OVERALL_LABEL,
            strength = registered,
            appeared = if (subjects.isEmpty()) null else "MAX(C$firstSubjectRow:C$lastSubjectRow)",
            absent = if (subjects.isEmpty()) null else "SUM(D$firstSubjectRow:D$lastSubjectRow)",
            passed = passed,
            failed = failed,
            total = if (passed != null && failed != null) "E$overallRow+F$overallRow" else null,
            passPercent = passed?.let { "IF(C$overallRow=0,0,E$overallRow/C$overallRow)" },
            average = percentages?.let { "IF(C$overallRow=0,0,SUM($it)/C$overallRow)" },
            bands = overallBands,
        )

        return subjectRows + overall
    }

    /**
     * The five page-3 bands. The first uses `INT(max*33/100)` and every later
     * band starts at the unrounded share, matching the example's mixed
     * rounding and [ExamSheetStats].
     */
    private fun fiveBands(marks: String, max: Int): List<String> = listOf(
        """COUNTIF($marks,"<"&INT($max*33/100))""",
        """COUNTIF($marks,">="&$max*33/100)-COUNTIF($marks,">="&$max*60/100)""",
        """COUNTIF($marks,">="&$max*60/100)-COUNTIF($marks,">="&$max*75/100)""",
        """COUNTIF($marks,">="&$max*75/100)-COUNTIF($marks,">="&$max*90/100)""",
        """COUNTIF($marks,">="&$max*90/100)""",
    )

    /** The five class-wide values on the overall row, in A..E column order. */
    fun classWiseValues(overallRow: Int): List<String?> = listOf(
        "B$overallRow",
        "C$overallRow",
        "E$overallRow",
        "H$overallRow",
        "I$overallRow",
    )
}
