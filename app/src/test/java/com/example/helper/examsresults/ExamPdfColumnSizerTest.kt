package com.example.helper.examsresults

import com.example.helper.examsresults.export.ExamPdfColumnLayout
import com.example.helper.examsresults.export.ExamPdfColumnSizer
import com.example.helper.examsresults.export.ExamPdfColumnSpec
import com.example.helper.examsresults.export.ExamPdfTextWrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Printed columns must be shared from measured content, not a fixed guess. The
 * measurement itself is a TextPaint on the device; these tests feed a fake
 * measure so the arithmetic and the wrapping are pinned without a device.
 */
class ExamPdfColumnSizerTest {

    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun `spare width goes only to flexible columns`() {
        val layout = size(
            available = 500f,
            spec(60f, 60f, false),
            spec(60f, 30f, true),
            spec(60f, 60f, false),
        )

        assertFalse(layout.needsSmallerFont)
        assertEquals(60f, layout.widths[0], 0.001f)
        assertEquals(380f, layout.widths[1], 0.001f)
        assertEquals(60f, layout.widths[2], 0.001f)
        assertEquals(500f, layout.widths.sum(), 0.001f)
    }

    @Test
    fun `in-between widths grow with headroom and fixed columns keep their max`() {
        val layout = size(
            available = 300f,
            spec(100f, 100f, false),
            spec(200f, 40f, true),
            spec(100f, 100f, false),
        )

        // min sum 240, room 60; only the flexible column has headroom.
        assertFalse(layout.needsSmallerFont)
        assertEquals(100f, layout.widths[0], 0.001f)
        assertEquals(100f, layout.widths[2], 0.001f)
        assertEquals(300f, layout.widths.sum(), 0.001f)
        assertTrue("flexible column gets the room", layout.widths[1] > 40f)
    }

    @Test
    fun `multiple flexible columns share room in proportion to their headroom`() {
        val layout = size(
            available = 300f,
            spec(150f, 50f, true),
            spec(150f, 100f, true),
            spec(50f, 50f, false),
        )

        // min sum 200, room 100, headroom 100:50 -> 66.67 and 33.33.
        assertEquals(116.67f, layout.widths[0], 0.02f)
        assertEquals(133.33f, layout.widths[1], 0.02f)
        assertEquals(50f, layout.widths[2], 0.001f)
    }

    @Test
    fun `content wider than the page asks for a smaller font and still fits`() {
        val layout = size(
            available = 200f,
            spec(300f, 120f, true),
            spec(300f, 120f, true),
        )

        assertTrue(layout.needsSmallerFont)
        assertEquals(200f, layout.widths.sum(), 0.001f)
        assertTrue(layout.widths.all { it > 0f })
    }

    @Test
    fun `a missing or empty value occupies a column without inflating it`() {
        val layout = size(
            available = 200f,
            spec(20f, 20f, false),
            spec(20f, 20f, true),
        )

        assertFalse(layout.needsSmallerFont)
        assertEquals(20f, layout.widths[0], 0.001f)
        assertEquals(180f, layout.widths[1], 0.001f)
    }

    @Test
    fun `wrapping breaks on spaces`() {
        val lines = ExamPdfTextWrap.wrap("Rajesh Kumar Sharma", measure, 100f)

        assertEquals(listOf("Rajesh", "Kumar", "Sharma"), lines)
        assertTrue(lines.all { measure(it) <= 100f })
    }

    @Test
    fun `a word too wide for any cell is split by characters`() {
        val lines = ExamPdfTextWrap.wrap("ANEXTREMELYLONGINSTITUTIONNAME", measure, 50f)

        assertEquals(6, lines.size)
        assertTrue(lines.all { measure(it) <= 50f })
        assertEquals(
            "ANEXTREMELYLONGINSTITUTIONNAME",
            lines.joinToString(""),
        )
    }

    @Test
    fun `short and empty text stay on one line`() {
        assertEquals(listOf("Asha"), ExamPdfTextWrap.wrap("Asha", measure, 100f))
        assertEquals(listOf(""), ExamPdfTextWrap.wrap("", measure, 100f))
    }

    @Test
    fun `a mixed line wraps words then keeps the remainder`() {
        val lines = ExamPdfTextWrap.wrap("Asha Kumar", measure, 60f)

        assertEquals(listOf("Asha", "Kumar"), lines)
        assertTrue(lines.all { measure(it) <= 60f })
    }

    private fun size(available: Float, vararg specs: ExamPdfColumnSpec): ExamPdfColumnLayout =
        ExamPdfColumnSizer.size(specs.toList(), available)

    private fun spec(max: Float, min: Float, flexible: Boolean) =
        ExamPdfColumnSpec(max, min, flexible)
}
