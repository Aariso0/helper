package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamPdfTableBuilder
import com.example.helper.examsresults.export.ExportColumnDefaults
import com.example.helper.examsresults.export.ExportHeading
import com.example.helper.examsresults.export.sections
import com.example.helper.examsresults.util.StudentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PDF is the copy the teacher prints, so its table must say the same thing
 * as the sheet: S.No. from 1, Pass/Fail in words, no invented zeros, and no
 * column whose value the device cannot compute.
 */
class ExamPdfTableTest {

    private val subjects = listOf(
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
        ExamSubject(id = "science", examId = "e1", name = "Science", maxMarks = 50, passMarks = 18),
    )

    private fun student(id: String, name: String, roll: String) = Student(
        id = id,
        classId = "c1",
        name = name,
        rollNumber = roll,
        createdAt = 0,
        updatedAt = 0,
    )

    private val asha = StudentResult(
        student = student("s1", "Asha", "1"),
        marksBySubject = mapOf("math" to 80.0, "science" to 40.0),
        totalObtained = 120.0,
        totalMax = 150.0,
        percentage = 80.0,
        isPass = true,
        failedSubjects = emptyList(),
    )

    @Test
    fun `headers are S No, roll, name, subjects, then the computed columns`() {
        val table = ExamPdfTableBuilder.build(subjects, listOf(asha))

        assertEquals(
            listOf(
                "S.No.", "Roll No.", "Student Name", "Math (100)", "Science (50)",
                "Total Obtained", "Total Max", "Percentage", "Result",
            ),
            table.headers,
        )
    }

    @Test
    fun `S No numbers each row from one`() {
        val ravi = asha.copy(
            student = student("s2", "Ravi", "2"),
            marksBySubject = mapOf("math" to 20.0, "science" to 10.0),
            isPass = false,
        )
        val table = ExamPdfTableBuilder.build(subjects, listOf(asha, ravi))

        assertEquals(listOf("1", "2"), table.rows.map { it.first() })
        assertEquals(listOf("1", "2"), table.rows.map { it[1] })
        assertEquals(listOf("Asha", "Ravi"), table.rows.map { it[2] })
    }

    @Test
    fun `a pass is plain and a fail is the word Fail`() {
        val ravi = asha.copy(
            student = student("s2", "Ravi", "2"),
            marksBySubject = mapOf("math" to 20.0),
            isPass = false,
        )
        val table = ExamPdfTableBuilder.build(subjects, listOf(asha, ravi))
        val resultIndex = table.headers.indexOf("Result")

        assertEquals("Pass", table.rows[0][resultIndex])
        assertEquals("Fail", table.rows[1][resultIndex])
    }

    @Test
    fun `a row with no marks has no verdict and no invented zero`() {
        val blank = asha.copy(
            student = student("s3", "Nila", "3"),
            marksBySubject = emptyMap(),
            totalObtained = 0.0,
            percentage = 0.0,
            isPass = true,
        )
        val table = ExamPdfTableBuilder.build(subjects, listOf(blank))

        assertEquals("", table.rows.single()[table.headers.indexOf("Math (100)")])
        assertEquals("", table.rows.single()[table.headers.indexOf("Result")])
    }

    @Test
    fun `percentage carries a percent sign and no trailing zeros`() {
        val partial = asha.copy(percentage = 53.333, totalObtained = 80.0)
        val table = ExamPdfTableBuilder.build(subjects, listOf(partial))

        assertEquals("53.33%", table.rows.single()[table.headers.indexOf("Percentage")])
        assertEquals("80", table.rows.single()[table.headers.indexOf("Total Obtained")])
    }

    @Test
    fun `a custom Excel formula is left off because the device cannot compute it`() {
        val grade = ExportColumn(
            id = "custom-grade",
            label = "Grade",
            kind = ExportColumnKind.CUSTOM,
            formula = "IF({Percentage}>=90,\"A\",\"B\")",
            sortOrder = 4,
        )
        val table = ExamPdfTableBuilder.build(
            subjects,
            listOf(asha),
            ExportColumnDefaults.columns() + grade,
        )

        assertFalse(table.headers.contains("Grade"))
        assertEquals(9, table.headers.size)
    }

    @Test
    fun `a disabled column is left off`() {
        val columns = ExportColumnDefaults.columns().map { column ->
            if (column.kind == ExportColumnKind.PERCENTAGE) column.copy(enabled = false) else column
        }
        val table = ExamPdfTableBuilder.build(subjects, listOf(asha), columns)

        assertFalse(table.headers.contains("Percentage"))
    }

    @Test
    fun `column pages repeat S No, roll and name and keep the computed columns last`() {
        val sixSubjects = (1..6).map { index ->
            ExamSubject(
                id = "s$index",
                examId = "e1",
                name = "Subject $index",
                maxMarks = 100,
                passMarks = 33,
            )
        }
        val table = ExamPdfTableBuilder.build(sixSubjects, listOf(asha))

        // S.No. / Roll No. / Name + six subjects + four computed columns.
        assertEquals(13, table.headers.size)
        val pages = table.sections(columnsPerPage = 6)

        assertEquals(2, pages.size)
        pages.forEach { page ->
            assertEquals(listOf("S.No.", "Roll No.", "Student Name"), page.headers.take(3))
            assertTrue("the name is repeated", page.headers.contains("Student Name"))
            assertEquals(table.rows.size, page.rows.size)
            assertEquals("Asha", page.rows.single()[2])
        }
        assertEquals(
            listOf("Total Obtained", "Total Max", "Percentage", "Result"),
            pages.last().headers.drop(3),
        )
    }

    @Test
    fun `a table with no data columns is one page`() {
        val table = ExamPdfTableBuilder.build(emptyList(), listOf(asha), emptyList())
        val pages = table.sections(columnsPerPage = 6)

        assertEquals(1, pages.size)
        assertEquals(listOf("S.No.", "Roll No.", "Student Name"), pages.single().headers)
    }

    @Test
    fun `the pdf heading names the exam and the class`() {
        assertEquals("Half Yearly — Class 5-A", ExportHeading.title("Half Yearly", "5-A"))
        assertEquals("Half Yearly", ExportHeading.title("Half Yearly", ""))
        assertEquals("Class 5-A", ExportHeading.title("", "5-A"))
    }
}
