package com.example.helper.examsresults

import com.example.helper.examsresults.export.ExamPdfOrientation
import com.example.helper.examsresults.export.ExamPdfPageKind
import com.example.helper.examsresults.export.ExamPdfPagePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is three pages only when there is an analysis to print, and the
 * two analysis pages are always landscape. These tests pin that decision so a
 * later layout change cannot silently print an empty page 2 or turn page 3
 * upright.
 */
class ExamPdfPagePlanTest {

    @Test
    fun `marks, summary and subject teacher in order`() {
        val plan = ExamPdfPagePlan.build(marksPageCount = 1, hasAnalysis = true)

        assertEquals(
            listOf(ExamPdfPageKind.MARKS, ExamPdfPageKind.SUMMARY, ExamPdfPageKind.SUBJECT_TEACHER),
            plan.map { it.kind },
        )
        assertEquals(ExamPdfOrientation.PORTRAIT, plan[0].orientation)
        assertEquals(ExamPdfOrientation.LANDSCAPE, plan[1].orientation)
        assertEquals(ExamPdfOrientation.LANDSCAPE, plan[2].orientation)
    }

    @Test
    fun `a long class adds portrait continuation pages before the analysis`() {
        val plan = ExamPdfPagePlan.build(marksPageCount = 3, hasAnalysis = true)

        assertEquals(
            listOf(
                ExamPdfPageKind.MARKS,
                ExamPdfPageKind.MARKS,
                ExamPdfPageKind.MARKS,
                ExamPdfPageKind.SUMMARY,
                ExamPdfPageKind.SUBJECT_TEACHER,
            ),
            plan.map { it.kind },
        )
        assertEquals(
            listOf(
                ExamPdfOrientation.PORTRAIT,
                ExamPdfOrientation.PORTRAIT,
                ExamPdfOrientation.PORTRAIT,
                ExamPdfOrientation.LANDSCAPE,
                ExamPdfOrientation.LANDSCAPE,
            ),
            plan.map { it.orientation },
        )
    }

    @Test
    fun `without an analysis there are only marks pages`() {
        val plan = ExamPdfPagePlan.build(marksPageCount = 1, hasAnalysis = false)

        assertEquals(listOf(ExamPdfPageKind.MARKS), plan.map { it.kind })
    }

    @Test
    fun `at least one page is always planned`() {
        assertEquals(1, ExamPdfPagePlan.build(marksPageCount = 0, hasAnalysis = false).size)
    }

    @Test
    fun `a six-subject class keeps all of page one on one page`() {
        // Six subjects plus Total / Percentage / Result / Rank. The paper
        // example is a 6-subject class and must not split page 1 in two.
        assertTrue(
            "10 data columns must fit a portrait page",
            ExamPdfPagePlan.portraitDataColumns() >= 10,
        )
    }
}
