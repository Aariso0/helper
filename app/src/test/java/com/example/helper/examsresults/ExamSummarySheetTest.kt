package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.examsresults.export.ExamSummarySheet
import com.example.helper.examsresults.export.SummaryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary sheet replaces a block of hand-maintained numbers with live
 * formulas. These tests pin the ways that silently breaks: a range pointing at
 * the wrong rows, a band whose boundaries stop being adjacent, the configured
 * pass mark leaking back to the example's fixed 33 %, and a deleted layout
 * column turning the whole summary into blanks instead of a partial one.
 */
class ExamSummarySheetTest {

    private val subjects = listOf(
        ExamSubject(id = "hindi", examId = "e1", name = "Hindi", maxMarks = 40, passMarks = 14),
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
    )

    private fun rows(
        percentageColumn: String? = "H",
        resultColumn: String? = "I",
    ): List<SummaryRow> = ExamSummarySheet.rows(
        resultsSheet = "Results",
        firstRow = 2,
        lastRow = 11,
        nameColumn = "C",
        subjects = subjects,
        percentageColumn = percentageColumn,
        resultColumn = resultColumn,
    )

    private fun subject(index: Int = 0): SummaryRow = rows()[index]

    private fun overall(percentageColumn: String? = "H", resultColumn: String? = "I"): SummaryRow =
        rows(percentageColumn, resultColumn).last()

    @Test
    fun `one row per subject carries its name and max marks, then the overall row`() {
        val all = rows()

        assertEquals(listOf("Hindi (40)", "Math (100)", "OVER ALL CLASS"), all.map { it.label })
    }

    @Test
    fun `registered counts names and appeared counts entered marks`() {
        assertEquals("COUNTA('Results'!${'$'}C$2:${'$'}C$11)", subject().registered)
        assertEquals("""COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">=0")""", subject().appeared)
        assertEquals("""COUNTIF('Results'!${'$'}E$2:${'$'}E$11,">=0")""", subject(1).appeared)
    }

    @Test
    fun `passed uses the configured pass mark, not the example's 33 percent`() {
        assertEquals("""COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">=14")""", subject().passed)
        assertEquals("""COUNTIF('Results'!${'$'}E$2:${'$'}E$11,">=35")""", subject(1).passed)
        assertTrue("the 33 % band boundary is a different number", !subject().passed.orEmpty().contains("33"))
    }

    @Test
    fun `pass share and subject average are fractions with a zero guard`() {
        assertEquals("IF(C3=0,0,D3/C3)", subject().passPercent)
        assertEquals("IF(C3=0,0,SUM('Results'!${'$'}D$2:${'$'}D$11)/C3/40)", subject().average)
    }

    @Test
    fun `the bands keep the example's fixed percentages of the subject max`() {
        val bands = subject().bands
        assertEquals(
            listOf("Below 33%", "33-49%", "50-59%", "60-74%", "75-89%", "90-94%", "95% & Above"),
            ExamSummarySheet.BAND_LABELS,
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,"<"&INT(40*33/100))""",
            bands[0],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">="&40*33/100)-I3-J3-K3-L3-M3""",
            bands[1],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">="&40*95/100)""",
            bands[6],
        )
    }

    @Test
    fun `each band subtracts only the bands above it`() {
        val bands = subject().bands

        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">="&40*50/100)-J3-K3-L3-M3""",
            bands[2],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">="&40*75/100)-L3-M3""",
            bands[4],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">="&40*90/100)-M3""",
            bands[5],
        )
    }

    @Test
    fun `the overall row takes appeared as the max and the verdicts from the result column`() {
        val all = overall()

        assertEquals("MAX(C3:C4)", all.appeared)
        assertEquals("""COUNTIF('Results'!${'$'}I$2:${'$'}I$11,"Pass")""", all.passed)
        assertEquals("IF(C5=0,0,D5/C5)", all.passPercent)
        assertEquals("IF(C5=0,0,SUM('Results'!${'$'}H$2:${'$'}H$11)/C5)", all.average)
    }

    @Test
    fun `the overall bands count the percentage shares`() {
        val bands = overall().bands

        assertEquals("""COUNTIF('Results'!${'$'}H$2:${'$'}H$11,"<"&33%)""", bands[0])
        assertEquals(
            """COUNTIF('Results'!${'$'}H$2:${'$'}H$11,">="&33%)-I5-J5-K5-L5-M5""",
            bands[1],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}H$2:${'$'}H$11,">="&95%)""",
            bands[6],
        )
    }

    @Test
    fun `the class-wise block is one four-cell block on the overall row`() {
        val all = overall()

        assertEquals(listOf("C5", "D5", "E5", "F5"), all.classWise)
        assertTrue("only the overall row fills it", rows()[0].classWise.isEmpty())
        assertTrue("only the overall row fills it", rows()[1].classWise.isEmpty())
    }

    @Test
    fun `a missing result column blanks the verdict figures but keeps the marks analysis`() {
        val all = rows(resultColumn = null)

        assertEquals("""COUNTIF('Results'!${'$'}D$2:${'$'}D$11,">=14")""", all[0].passed)
        assertEquals("IF(C3=0,0,D3/C3)", all[0].passPercent)
        assertNull("overall passed needs the Result column", all.last().passed)
        assertNull("overall pass share needs the Result column", all.last().passPercent)
        assertEquals("the marks bands still work", listOf("C5", null, null, "F5"), all.last().classWise)
    }

    @Test
    fun `a missing percentage column blanks only the figures that read it`() {
        val all = rows(percentageColumn = null)

        assertEquals("IF(C3=0,0,SUM('Results'!${'$'}D$2:${'$'}D$11)/C3/40)", all[0].average)
        assertEquals("""COUNTIF('Results'!${'$'}I$2:${'$'}I$11,"Pass")""", all.last().passed)
        assertEquals("IF(C5=0,0,D5/C5)", all.last().passPercent)
        assertNull("overall average needs the Percentage column", all.last().average)
        assertTrue("overall bands need the Percentage column", all.last().bands.all { it == null })
        assertEquals(listOf("C5", "D5", "E5", null), all.last().classWise)
    }

    @Test
    fun `a sheet name with a quote is escaped rather than producing a broken formula`() {
        val quoted = ExamSummarySheet.rows(
            resultsSheet = "Asha's class",
            firstRow = 2,
            lastRow = 3,
            nameColumn = "C",
            subjects = subjects.take(1),
            percentageColumn = "H",
            resultColumn = "I",
        )

        assertTrue(quoted.first().registered.orEmpty().contains("'Asha''s class'!${'$'}C$2:${'$'}C$3"))
    }

    @Test
    fun `the last row moves with the student count`() {
        val single = ExamSummarySheet.rows(
            resultsSheet = "Results",
            firstRow = 2,
            lastRow = 2,
            nameColumn = "C",
            subjects = subjects.take(1),
            percentageColumn = "H",
            resultColumn = "I",
        )

        assertEquals("COUNTA('Results'!${'$'}C$2:${'$'}C$2)", single.first().registered)
    }
}
