package com.example.helper.coredata.session

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Session labels are user-visible and drive which classes the teacher sees, so
 * the April–March boundary and the "next session" suggestion are pinned here.
 */
class AcademicYearTest {

    @Test
    fun `April is the first month of a new session`() {
        assertEquals("2025-26", AcademicYear.currentLabel(LocalDate.of(2026, 3, 31)))
        assertEquals("2026-27", AcademicYear.currentLabel(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `the middle of the school year keeps its label`() {
        assertEquals("2026-27", AcademicYear.currentLabel(LocalDate.of(2026, 11, 7)))
        assertEquals("2026-27", AcademicYear.currentLabel(LocalDate.of(2027, 1, 15)))
    }

    @Test
    fun `next label rolls the decade over`() {
        assertEquals("2027-28", AcademicYear.nextLabel("2026-27"))
        assertEquals("2030-31", AcademicYear.nextLabel("2029-30"))
    }

    @Test
    fun `a label without a leading year has no suggestion`() {
        assertNull(AcademicYear.nextLabel("Batch A"))
    }
}
