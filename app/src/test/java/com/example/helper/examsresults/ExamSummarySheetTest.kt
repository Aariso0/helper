package com.example.helper.examsresults

import com.example.helper.examsresults.export.ExamSummarySheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The summary sheet replaces a block of hand-maintained numbers with live
 * formulas. These tests pin the two ways that silently breaks: a range that
 * points at the wrong rows, and a band whose boundaries stop being adjacent
 * (so the counts no longer add up to "appeared").
 */
class ExamSummarySheetTest {

    private fun rows(lastRow: Int = 11) = ExamSummarySheet.rows(
        resultsSheet = "Results",
        firstRow = 2,
        lastRow = lastRow,
        nameColumn = "C",
        percentageColumn = "H",
        resultColumn = "I",
    )

    private fun formulaOf(label: String): String =
        rows().first { it.label == label }.formula

    @Test
    fun `registered counts the name column across the student rows`() {
        assertEquals("COUNTA('Results'!C2:C11)", formulaOf("REGISTERED"))
    }

    @Test
    fun `appeared is the rows that already have a verdict`() {
        assertEquals(
            """COUNTIF('Results'!I2:I11,"Pass")+COUNTIF('Results'!I2:I11,"Fail")""",
            formulaOf("APPEARED"),
        )
    }

    @Test
    fun `pass share and average divide by the summary's own counts`() {
        assertEquals("IF(B4=0,0,B5*100/B4)", formulaOf("PASS %"))
        assertEquals("IF(B4=0,0,SUM('Results'!H2:H11)/B4)", formulaOf("AVERAGE %"))
    }

    @Test
    fun `the seven bands are labelled like the example mark slip`() {
        assertEquals(
            listOf("0-32", "33-49", "50-59", "60-74", "75-89", "90-94", "95-ABOVE"),
            ExamSummarySheet.BAND_LABELS,
        )
        assertTrue(rows().map { it.label }.containsAll(ExamSummarySheet.BAND_LABELS))
    }

    @Test
    fun `the low band subtracts from the appeared count so blank rows cannot leak in`() {
        assertEquals("B4-COUNTIF('Results'!H2:H11,\">=33\")", formulaOf("0-32"))
    }

    @Test
    fun `each middle band is the gap between two adjacent thresholds`() {
        assertEquals(
            """COUNTIF('Results'!H2:H11,">=33")-COUNTIF('Results'!H2:H11,">=50")""",
            formulaOf("33-49"),
        )
        assertEquals(
            """COUNTIF('Results'!H2:H11,">=90")-COUNTIF('Results'!H2:H11,">=95")""",
            formulaOf("90-94"),
        )
    }

    @Test
    fun `the top band has no upper bound`() {
        assertEquals("""COUNTIF('Results'!H2:H11,">=95")""", formulaOf("95-ABOVE"))
    }

    @Test
    fun `a sheet name with a quote is escaped rather than producing a broken formula`() {
        val quoted = ExamSummarySheet.rows(
            resultsSheet = "Asha's class",
            firstRow = 2,
            lastRow = 3,
            nameColumn = "C",
            percentageColumn = "H",
            resultColumn = "I",
        )
        assertTrue(quoted.first().formula.contains("'Asha''s class'!C2:C3"))
    }

    @Test
    fun `the last row moves with the student count`() {
        val single = ExamSummarySheet.rows(
            resultsSheet = "Results",
            firstRow = 2,
            lastRow = 2,
            nameColumn = "C",
            percentageColumn = "H",
            resultColumn = "I",
        )
        assertEquals("COUNTA('Results'!C2:C2)", single.first().formula)
    }
}
