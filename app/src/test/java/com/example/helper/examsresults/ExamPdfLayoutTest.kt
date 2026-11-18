package com.example.helper.examsresults

import com.example.helper.examsresults.export.ExamPdfLayout
import com.example.helper.examsresults.export.ExamPdfRowPaginator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A long class must print every student. The layout starts at the readable font
 * for the class size and [ExamPdfRowPaginator] continues on another page when
 * wrapped rows no longer fit. Pure arithmetic, so the boundary is pinned
 * without a device.
 */
class ExamPdfLayoutTest {

    @Test
    fun `a forty-student class starts at the comfortable font`() {
        val fit = ExamPdfLayout.fit(availableHeight = 700f, rowCount = 40)

        assertEquals(ExamPdfLayout.BASE_TEXT_SIZE, fit.textSize, 0.001f)
        assertTrue("rows never grow", fit.rowHeight <= ExamPdfLayout.BASE_ROW_HEIGHT)
    }

    @Test
    fun `a very long class starts at the floor`() {
        val fit = ExamPdfLayout.fit(availableHeight = 700f, rowCount = 100)

        assertEquals(ExamPdfLayout.MIN_TEXT_SIZE, fit.textSize, 0.001f)
        assertEquals(ExamPdfLayout.MIN_NAME_TEXT_SIZE, fit.nameTextSize, 0.001f)
        assertEquals(ExamPdfLayout.MIN_ROW_HEIGHT, fit.rowHeight, 0.001f)
    }

    @Test
    fun `an empty class still gets a full-height row slot`() {
        assertEquals(
            ExamPdfLayout.BASE_ROW_HEIGHT,
            ExamPdfLayout.fit(availableHeight = 700f, rowCount = 0).rowHeight,
            0.001f,
        )
    }

    @Test
    fun `a degenerate page height cannot produce a tiny font`() {
        val fit = ExamPdfLayout.fit(availableHeight = 0f, rowCount = 10)

        assertEquals(ExamPdfLayout.MIN_TEXT_SIZE, fit.textSize, 0.001f)
        assertEquals(ExamPdfLayout.MIN_ROW_HEIGHT, fit.rowHeight, 0.001f)
    }

    @Test
    fun `thirty-plus wrapped rows continue on later pages`() {
        val rows = List(35) { it }

        val pages = ExamPdfRowPaginator.paginate(rows, { 20f }, availableHeight = 100f)

        assertEquals(7, pages.size)
        assertTrue(pages.all { it.size == 5 })
        assertEquals(rows, pages.flatten())
    }

    @Test
    fun `a row taller than the page gets a page of its own`() {
        val rows = listOf("short", "very tall", "short")

        val pages = ExamPdfRowPaginator.paginate(rows, {
            if (it == "very tall") 150f else 20f
        }, availableHeight = 100f)

        assertEquals(listOf(listOf("short"), listOf("very tall"), listOf("short")), pages)
    }

    @Test
    fun `no rows still produce one empty page for the header`() {
        assertEquals(
            listOf(emptyList<String>()),
            ExamPdfRowPaginator.paginate(emptyList<String>(), { 10f }, 100f),
        )
    }
}
