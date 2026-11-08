package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.util.AbsentPlanner
import com.example.helper.examsresults.util.StudentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bulk "mark remaining as absent" confirmation shows a count and then
 * writes exactly that many rows. These tests pin the difference between the
 * three mark states: a number, an absent row, and a blank.
 */
class AbsentPlannerTest {

    private val subjects = listOf(
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
        ExamSubject(id = "science", examId = "e1", name = "Science", maxMarks = 100, passMarks = 35),
    )

    private fun student(id: String, name: String) = Student(
        id = id,
        classId = "c1",
        name = name,
        rollNumber = id,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun result(
        id: String,
        marks: Map<String, Double>,
        absent: Set<String> = emptySet(),
    ) = StudentResult(
        student = student(id, "Student $id"),
        marksBySubject = marks,
        totalObtained = marks.values.sum(),
        totalMax = 200.0,
        percentage = 0.0,
        isPass = false,
        failedSubjects = emptyList(),
        absentSubjectIds = absent,
    )

    @Test
    fun `only pairs with no mark and no absent row are blank`() {
        val results = listOf(
            result("s1", mapOf("math" to 80.0), absent = setOf("science")),
            result("s2", mapOf("math" to 40.0)),
        )

        val blanks = AbsentPlanner.blankPairs(results, subjects)

        assertEquals(
            listOf("s2" to "science"),
            blanks.map { it.studentId to it.subjectId },
        )
    }

    @Test
    fun `a fully entered class has nothing to mark`() {
        val results = listOf(
            result("s1", mapOf("math" to 80.0, "science" to 70.0)),
            result("s2", mapOf("math" to 40.0, "science" to 30.0)),
        )

        assertTrue(AbsentPlanner.blankPairs(results, subjects).isEmpty())
    }

    @Test
    fun `every blank pair is listed once`() {
        val results = listOf(result("s1", emptyMap()), result("s2", emptyMap()))

        val blanks = AbsentPlanner.blankPairs(results, subjects)

        assertEquals(4, blanks.size)
        assertEquals(4, blanks.toSet().size)
    }
}
