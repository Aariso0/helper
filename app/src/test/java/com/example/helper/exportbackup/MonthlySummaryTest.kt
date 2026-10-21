package com.example.helper.exportbackup

import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.exportbackup.summarize.buildMonthlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monthly summary is the single source for the screen and all three
 * exports, so its arithmetic is what the teacher actually reads. These tests
 * pin the two rules that matter: Late counts as present, and "not tracked" is
 * never rendered as 0%.
 */
class MonthlySummaryTest {

    private val classA = SchoolClass(id = "c1", name = "5-A", createdAt = 0, updatedAt = 0)
    private val classB = SchoolClass(id = "c2", name = "5-B", createdAt = 0, updatedAt = 0)

    private val asha = Student(id = "s1", classId = "c1", name = "Asha", rollNumber = "2", createdAt = 0, updatedAt = 0)
    private val bina = Student(id = "s2", classId = "c1", name = "Bina", rollNumber = "1", createdAt = 0, updatedAt = 0)
    private val chetan = Student(id = "s3", classId = "c2", name = "Chetan", createdAt = 0, updatedAt = 0)
    private val dev = Student(id = "s4", classId = "c2", name = "Dev", rollNumber = "3", createdAt = 0, updatedAt = 0)

    @Test
    fun `late counts as present in the percentage`() {
        // 2 present + 1 late out of 4 records = 75%.
        val records = listOf(
            record("s1", "c1", day = 1, AttendanceStatus.PRESENT),
            record("s1", "c1", day = 2, AttendanceStatus.PRESENT),
            record("s1", "c1", day = 3, AttendanceStatus.LATE),
            record("s1", "c1", day = 4, AttendanceStatus.ABSENT),
        )
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA),
            students = listOf(asha),
            records = records,
            selectedClassId = null,
        )
        val student = summary.classes.single().students.single()
        assertEquals(2, student.present)
        assertEquals(1, student.late)
        assertEquals(1, student.absent)
        assertEquals(4, student.trackedRecords)
        assertEquals(75, student.presentPercentage)
    }

    @Test
    fun `untracked student has no percentage instead of zero percent`() {
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA),
            students = listOf(asha, bina),
            records = listOf(record("s1", "c1", day = 1, AttendanceStatus.ABSENT)),
            selectedClassId = null,
        )
        val classSummary = summary.classes.single()
        val binaSummary = classSummary.students.single { it.studentId == "s2" }
        assertEquals(0, binaSummary.trackedRecords)
        assertNull(binaSummary.presentPercentage)
        assertEquals(0, classSummary.presentPercentage) // real 0% — every mark is absent
    }

    @Test
    fun `class total counts distinct tracked days not marks`() {
        // Two students marked on the same three days: 6 records, 3 days.
        val records = listOf("s1", "s2").flatMap { studentId ->
            (1..3).map { day -> record(studentId, "c1", day.toLong(), AttendanceStatus.PRESENT) }
        }
        val summary = buildMonthlySummary(2026, 9, listOf(classA), listOf(asha, bina), records, null)
        val classSummary = summary.classes.single()
        assertEquals(6, classSummary.trackedRecords)
        assertEquals(3, classSummary.trackedDays)
        assertEquals(100, classSummary.presentPercentage)
    }

    @Test
    fun `grand totals add every class`() {
        val records = listOf(
            record("s1", "c1", day = 1, AttendanceStatus.PRESENT),
            record("s3", "c2", day = 1, AttendanceStatus.ABSENT),
        )
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA, classB),
            students = listOf(asha, bina, chetan, dev),
            records = records,
            selectedClassId = null,
        )
        assertEquals(2, summary.classes.size)
        assertEquals(1, summary.present)
        assertEquals(1, summary.absent)
        assertEquals(2, summary.trackedRecords)
        assertEquals(50, summary.presentPercentage)
        assertTrue(summary.hasRecords)
    }

    @Test
    fun `class filter keeps the other classes out`() {
        val records = listOf(
            record("s1", "c1", day = 1, AttendanceStatus.PRESENT),
            record("s3", "c2", day = 1, AttendanceStatus.PRESENT),
        )
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA, classB),
            students = listOf(asha, bina, chetan, dev),
            records = records,
            selectedClassId = "c2",
        )
        assertEquals(listOf("5-B"), summary.classes.map { it.className })
        assertEquals(1, summary.trackedRecords)
    }

    @Test
    fun `students sort by roll number with unnumbered students last`() {
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA),
            students = listOf(asha, bina),
            records = emptyList(),
            selectedClassId = null,
        )
        val names = summary.classes.single().students.map { it.studentName }
        // Bina has roll 1, Asha roll 2 — every student is listed even untracked.
        assertEquals(listOf("Bina", "Asha"), names)
    }

    @Test
    fun `empty month reports no records`() {
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(classA),
            students = listOf(asha),
            records = emptyList(),
            selectedClassId = null,
        )
        assertFalse(summary.hasRecords)
        assertNull(summary.presentPercentage)
    }

    private fun record(
        studentId: String,
        classId: String,
        day: Long,
        status: AttendanceStatus,
    ) = AttendanceRecord(
        id = "$studentId-$day",
        classId = classId,
        studentId = studentId,
        dateEpochDay = day,
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )
}
