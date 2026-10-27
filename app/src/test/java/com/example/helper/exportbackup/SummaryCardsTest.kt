package com.example.helper.exportbackup

import com.example.helper.exportbackup.ui.plural
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Counts are read as words. "2 classs" reached the Reports screen once, so the
 * plural form is pinned here rather than left to the default "+s".
 */
class SummaryCardsTest {

    @Test
    fun `default plural appends s for counts above one`() {
        assertEquals("day", plural(1, "day"))
        assertEquals("days", plural(2, "day"))
        assertEquals("days", plural(0, "day"))
        assertEquals("mark", plural(1, "mark"))
        assertEquals("marks", plural(31, "mark"))
    }

    @Test
    fun `a word with its own plural uses it`() {
        assertEquals("class", plural(1, "class", "classes"))
        assertEquals("classes", plural(2, "class", "classes"))
    }
}
