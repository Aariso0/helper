package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.util.ResultCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The percentage is the number the teacher actually reads, and it must agree
 * with the `Total: X/Y` label beside it and with the Excel formula. These
 * tests pin that agreement, including when subjects carry different max marks.
 */
class ResultCalculatorTest {

    private val student = Student(
        id = "s1",
        classId = "c1",
        name = "Asha",
        rollNumber = "1",
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `percentage is obtained over total max marks`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
            subject("english", max = 100, pass = 35),
        )
        val marks = listOf(
            mark("math", 80.0),
            mark("science", 70.0),
            mark("english", 60.0),
        )

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(210.0, result.totalObtained, 0.001)
        assertEquals(300.0, result.totalMax, 0.001)
        assertEquals(70.0, result.percentage, 0.001)
        assertTrue(result.isPass)
    }

    @Test
    fun `percentage respects different subject max marks`() {
        val subjects = listOf(
            subject("math", max = 80, pass = 28),
            subject("science", max = 50, pass = 18),
        )
        val marks = listOf(mark("math", 64.0), mark("science", 40.0))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(104.0, result.totalObtained, 0.001)
        assertEquals(130.0, result.totalMax, 0.001)
        assertEquals(80.0, result.percentage, 0.001)
    }

    @Test
    fun `missing marks do not fail when the available marks pass`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
        )
        val marks = listOf(mark("math", 90.0))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(mapOf("math" to 90.0), result.marksBySubject)
        assertTrue("a blank subject cannot fail", result.failedSubjects.isEmpty())
        assertTrue("all available marks pass, so the student passes", result.isPass)
        assertEquals(45.0, result.percentage, 0.001)
    }

    @Test
    fun `an entered failing mark still fails when another subject is blank`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
        )
        val marks = listOf(mark("math", 20.0))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(listOf("math"), result.failedSubjects)
        assertFalse("one failed mark fails the student even with blanks", result.isPass)
    }

    @Test
    fun `entered mark below the pass mark fails overall`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
        )
        val marks = listOf(mark("math", 90.0), mark("science", 20.0))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(55.0, result.percentage, 0.001)
        assertFalse(result.isPass)
        assertEquals(listOf("science"), result.failedSubjects)
    }

    @Test
    fun `no subjects means zero percent and fail`() {
        val result = ResultCalculator.calculate(student, emptyList(), emptyList())

        assertEquals(0.0, result.percentage, 0.001)
        assertEquals(0.0, result.totalMax, 0.001)
        assertFalse(result.isPass)
    }

    @Test
    fun `an absent subject adds zero, keeps the max and fails the subject`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
        )
        val marks = listOf(mark("math", 80.0), absent("science"))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(80.0, result.totalObtained, 0.001)
        assertEquals(200.0, result.totalMax, 0.001)
        assertEquals(40.0, result.percentage, 0.001)
        assertEquals(setOf("science"), result.absentSubjectIds)
        assertEquals(listOf("science"), result.failedSubjects)
        assertFalse(result.isPass)
        assertEquals("absent is not a zero mark", mapOf("math" to 80.0), result.marksBySubject)
    }

    @Test
    fun `only absent subjects still record a failing result`() {
        val subjects = listOf(subject("math", max = 100, pass = 35))
        val marks = listOf(absent("math"))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertTrue("nothing was entered", result.marksBySubject.isEmpty())
        assertEquals(0.0, result.totalObtained, 0.001)
        assertEquals(100.0, result.totalMax, 0.001)
        assertEquals(setOf("math"), result.absentSubjectIds)
        assertFalse(result.isPass)
    }

    @Test
    fun `a blank subject stays different from an absent one`() {
        val subjects = listOf(
            subject("math", max = 100, pass = 35),
            subject("science", max = 100, pass = 35),
        )
        // Math has no row at all (not entered), science is deliberately absent.
        val marks = listOf(absent("science"))

        val result = ResultCalculator.calculate(student, subjects, marks)

        assertEquals(setOf("science"), result.absentSubjectIds)
        assertTrue("a blank is not an absence", "math" !in result.absentSubjectIds)
        assertEquals(listOf("science"), result.failedSubjects)
        assertFalse("an absence is a recorded zero that fails", result.isPass)
    }

    private fun subject(id: String, max: Int, pass: Int) =
        ExamSubject(id = id, examId = "e1", name = id, maxMarks = max, passMarks = pass)

    private fun mark(subjectId: String, obtained: Double) =
        ExamMark(id = "m-$subjectId", examId = "e1", subjectId = subjectId, studentId = "s1", marksObtained = obtained)

    private fun absent(subjectId: String) =
        ExamMark(
            id = "m-$subjectId",
            examId = "e1",
            subjectId = subjectId,
            studentId = "s1",
            marksObtained = 0.0,
            isAbsent = true,
        )
}
