package com.example.helper.examsresults

import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.util.ExamHistory
import com.example.helper.examsresults.util.ExamHistoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The student page must tell "not entered" apart from "failed", show the
 * newest exam first, and never mix one exam's or one student's marks into
 * another's card.
 */
class ExamHistoryTest {

    private val student = Student(
        id = "s1",
        classId = "c1",
        name = "Asha",
        rollNumber = "1",
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `one entry per exam newest first with per-subject marks`() {
        val older = exam(id = "e1", name = "Unit test", date = 1_000L)
        val newer = exam(id = "e2", name = "Midterm", date = 2_000L)
        val subjects = listOf(
            subject(id = "m1", examId = "e1", name = "Math", max = 100, pass = 35),
            subject(id = "m2", examId = "e2", name = "Math", max = 100, pass = 35),
            subject(id = "s2", examId = "e2", name = "Science", max = 100, pass = 35),
        )
        val marks = listOf(
            mark(examId = "e1", subjectId = "m1", obtained = 80.0),
            mark(examId = "e2", subjectId = "m2", obtained = 90.0),
            mark(examId = "e2", subjectId = "s2", obtained = 20.0),
        )

        val entries = ExamHistory.build(listOf(older, newer), subjects, marks, student)

        assertEquals(listOf("Midterm", "Unit test"), entries.map { it.examName })
        assertEquals(110.0, entries[0].result.totalObtained, 0.001)
        assertEquals(200.0, entries[0].result.totalMax, 0.001)
        assertEquals(listOf("Science"), entries[0].failedSubjectNames)
        assertEquals(ExamHistoryStatus.FAIL, entries[0].status)
        assertEquals(listOf(90.0, 20.0), entries[0].subjectMarks.map { it.obtained })
        assertEquals(ExamHistoryStatus.PASS, entries[1].status)
        assertEquals(1, entries[1].enteredCount)
    }

    @Test
    fun `an exam with no marks is not entered rather than failed`() {
        val exam = exam(id = "e1", name = "Midterm", date = 1_000L)
        val subject = subject(id = "m1", examId = "e1", name = "Math", max = 100, pass = 35)

        val entry = ExamHistory.build(listOf(exam), listOf(subject), emptyList(), student).single()

        assertFalse(entry.hasMarks)
        assertEquals(ExamHistoryStatus.NOT_ENTERED, entry.status)
        assertTrue(entry.failedSubjectNames.isEmpty())
        assertEquals(0, entry.enteredCount)
        assertEquals(100.0, entry.result.totalMax, 0.001)
        assertNull(entry.subjectMarks.single().obtained)
    }

    @Test
    fun `marks from other students and other exams are not mixed in`() {
        val first = exam(id = "e1", name = "Unit test", date = 1_000L)
        val second = exam(id = "e2", name = "Midterm", date = 2_000L)
        val subjects = listOf(
            subject(id = "m1", examId = "e1", name = "Math", max = 100, pass = 35),
            subject(id = "m2", examId = "e2", name = "Math", max = 100, pass = 35),
        )
        val marks = listOf(
            mark(examId = "e1", subjectId = "m1", obtained = 80.0),
            mark(examId = "e1", subjectId = "m1", obtained = 10.0, studentId = "s2"),
            mark(examId = "e2", subjectId = "m2", obtained = 70.0),
        )

        val entries = ExamHistory.build(listOf(first, second), subjects, marks, student)

        assertEquals(listOf(70.0, 80.0), entries.map { it.subjectMarks.single().obtained })
        assertTrue(entries.all { it.failedSubjectNames.isEmpty() })
    }

    private fun exam(id: String, name: String, date: Long) = Exam(
        id = id,
        classId = "c1",
        name = name,
        examDate = date,
        createdAt = date,
        updatedAt = date,
    )

    private fun subject(id: String, examId: String, name: String, max: Int, pass: Int) =
        ExamSubject(
            id = id,
            examId = examId,
            name = name,
            maxMarks = max,
            passMarks = pass,
        )

    private fun mark(
        examId: String,
        subjectId: String,
        obtained: Double,
        studentId: String = "s1",
    ) = ExamMark(
        id = "m-$examId-$subjectId-$studentId",
        examId = examId,
        subjectId = subjectId,
        studentId = studentId,
        marksObtained = obtained,
    )
}
