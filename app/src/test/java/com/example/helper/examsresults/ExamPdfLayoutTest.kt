package com.example.helper.examsresults

import com.example.helper.examsresults.export.ExamPdfLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A long class must print every student. The layout either shrinks the text and
 * rows to the readable floor or continues on another page with the same banner;
 * it never clips a row or drops a student. Pure arithmetic, so the boundary is
 * pinned without a device.
 */
class ExamPdfLayoutTest {

    @Test
    fun `a forty-student class shrinks slightly instead of printing a second page`() {
        val fit = ExamPdfLayout.fit(availableHeight = 700f, rowCount = 40)

        assertTrue("all forty fit on one page", fit.rowsPerPage >= 40)
        assertTrue("text stays inside the readable band", fit.textSize in ExamPdfLayout.MIN_TEXT_SIZE..ExamPdfLayout.BASE_TEXT_SIZE)
        assertTrue("rows shrink, never grow", fit.rowHeight <= ExamPdfLayout.BASE_ROW_HEIGHT)
    }

    @Test
    fun `a very long class continues on another page at the floor`() {
        val fit = ExamPdfLayout.fit(availableHeight = 700f, rowCount = 100)

        assertEquals(ExamPdfLayout.MIN_TEXT_SIZE, fit.textSize, 0.001f)
        assertEquals(ExamPdfLayout.MIN_NAME_TEXT_SIZE, fit.nameTextSize, 0.001f)
        assertEquals(ExamPdfLayout.MIN_ROW_HEIGHT, fit.rowHeight, 0.001f)
        assertTrue("the rest goes to a continuation page", fit.rowsPerPage < 100)
        assertTrue("at least one row still fits", fit.rowsPerPage >= 1)
    }

    @Test
    fun `an empty class still gets at least one row slot`() {
        assertTrue(ExamPdfLayout.fit(availableHeight = 700f, rowCount = 0).rowsPerPage >= 1)
    }

    @Test
    fun `a degenerate page height cannot produce zero rows`() {
        val fit = ExamPdfLayout.fit(availableHeight = 0f, rowCount = 10)

        assertEquals(1, fit.rowsPerPage)
        assertEquals(ExamPdfLayout.MIN_TEXT_SIZE, fit.textSize, 0.001f)
    }
}
