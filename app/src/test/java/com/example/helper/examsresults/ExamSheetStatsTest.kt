package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamSheetStats
import com.example.helper.examsresults.util.StudentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pages 2 and 3 must say the same numbers as the formulas in the workbook.
 * These tests pin the two places that drift silently: an absence being counted
 * as "appeared", and a band edge moving because of rounding.
 */
class ExamSheetStatsTest {

    private val subjects = listOf(
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
        ExamSubject(id = "science", examId = "e1", name = "Science", maxMarks = 50, passMarks = 18),
    )

    private fun student(id: String, name: String) = Student(
        id = id,
        classId = "c1",
        name = name,
        rollNumber = id,
        createdAt = 0,
        updatedAt = 0,
    )

    private val asha = StudentResult(
        student = student("1", "Asha"),
        marksBySubject = mapOf("math" to 80.0, "science" to 40.0),
        totalObtained = 120.0,
        totalMax = 150.0,
        percentage = 80.0,
        isPass = true,
        failedSubjects = emptyList(),
    )

    private val ravi = StudentResult(
        student = student("2", "Ravi"),
        marksBySubject = mapOf("math" to 30.0, "science" to 10.0),
        totalObtained = 40.0,
        totalMax = 150.0,
        percentage = 26.6667,
        isPass = false,
        failedSubjects = listOf("math", "science"),
    )

    private val nila = StudentResult(
        student = student("3", "Nila"),
        marksBySubject = mapOf("science" to 25.0),
        totalObtained = 25.0,
        totalMax = 150.0,
        percentage = 16.6667,
        isPass = false,
        failedSubjects = listOf("math"),
        absentSubjectIds = setOf("math"),
    )

    private val kiran = StudentResult(
        student = student("4", "Kiran"),
        marksBySubject = emptyMap(),
        totalObtained = 0.0,
        totalMax = 150.0,
        percentage = 0.0,
        isPass = false,
        failedSubjects = emptyList(),
    )

    private fun stats() = ExamSheetStats.from(subjects, listOf(asha, ravi, nila, kiran))

    @Test
    fun `a subject separates appeared from an absence`() {
        val math = stats().subjects.first()

        assertEquals(4, math.strength)
        assertEquals(4, math.registered)
        assertEquals("only numeric marks appeared", 2, math.appeared)
        assertEquals("the absent row is its own state", 1, math.absent)
        assertEquals(1, math.passed)
        assertEquals(1, math.failed)
        assertEquals("passed + failed is appeared", 2, math.total)
    }

    @Test
    fun `the absent subject still lowers the subject pass share`() {
        val math = stats().subjects.first()

        assertEquals(0.5, math.passShare ?: 0.0, 0.0001)
        assertEquals("average of 80 and 30 out of 100", 0.55, math.averageShare ?: 0.0, 0.0001)
    }

    @Test
    fun `page-three bands use the five ranges and keep the example split`() {
        val science = stats().subjects.last()

        assertEquals(
            listOf("Below 33%", "33-<60%", "60-<75%", "75-<90%", "90% & Above"),
            ExamSheetStats.FIVE_BAND_LABELS,
        )
        assertEquals(listOf(1, 1, 0, 1, 0), science.bands5)
    }

    @Test
    fun `page-two bands keep the seven ranges and the INT below boundary`() {
        val math = stats().subjects.first()

        assertEquals(
            listOf("Below 33%", "33-49%", "50-59%", "60-74%", "75-89%", "90-94%", "95% & Above"),
            ExamSheetStats.SEVEN_BAND_LABELS,
        )
        assertEquals(listOf(1, 0, 0, 0, 1, 0, 0), math.bands7)
    }

    @Test
    fun `overall counts verdicts, not only marks, and reports absent entries`() {
        val overall = stats().overall

        assertEquals(4, overall.strength)
        assertEquals("the widest subject sets appeared", 3, overall.appeared)
        assertEquals(1, overall.absent)
        assertEquals("only Asha passed", 1, overall.passed)
        assertEquals("Ravi failed and Nila failed through absence", 2, overall.failed)
        assertEquals(3, overall.total)
        assertEquals(1.0 / 3.0, overall.passShare ?: 0.0, 0.0001)
        assertEquals(
            "mean share of 80, 26.67, 16.67 and 0 over appeared",
            (0.80 + 0.266667 + 0.166667 + 0.0) / 3.0,
            overall.averageShare ?: 0.0,
            0.001,
        )
    }

    @Test
    fun `overall bands read the percentage shares and include an unentered zero`() {
        val overall = stats().overall

        assertEquals(listOf(3, 0, 0, 0, 1, 0, 0), overall.bands7)
        assertEquals(listOf(3, 0, 0, 1, 0), overall.bands5)
    }

    @Test
    fun `an empty class has zero counts and no shares`() {
        val empty = ExamSheetStats.from(subjects, emptyList())

        assertEquals(listOf(0, 0), empty.subjects.map { it.appeared })
        assertEquals(0, empty.overall.strength)
        assertNull(empty.overall.passShare)
        assertNull(empty.overall.averageShare)
        assertEquals(List(7) { 0 }, empty.overall.bands7)
        assertEquals(List(5) { 0 }, empty.overall.bands5)
    }

    @Test
    fun `a subject with no numeric marks has no share, not a fake zero`() {
        val absentOnly = nila.copy(
            marksBySubject = emptyMap(),
            absentSubjectIds = setOf("math", "science"),
            failedSubjects = listOf("math", "science"),
        )
        val stats = ExamSheetStats.from(subjects, listOf(absentOnly))
        val math = stats.subjects.first()

        assertEquals(0, math.appeared)
        assertEquals(1, math.absent)
        assertNull(math.passShare)
        assertNull(math.averageShare)
    }
}
