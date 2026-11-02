package com.example.helper.examsresults

import com.example.helper.examsresults.excelimport.ExamImportModel
import com.example.helper.examsresults.excelimport.ExamImportSubjectDraft
import com.example.helper.examsresults.excelimport.ImportCandidate
import com.example.helper.roster.ui.import.ParsedTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exam sheet is read by pure functions so the preview and the commit can
 * never disagree. These tests pin the two things that silently corrupt an
 * import: a header misread as a subject (or a max mark), and a row matched to
 * the wrong student.
 */
class ExamImportModelTest {

    private val candidates = listOf(
        ImportCandidate("s1", "Asha", "1"),
        ImportCandidate("s2", "Ravi Kumar", "2"),
        ImportCandidate("s3", "Meera", "03"),
    )

    private fun table(
        headers: List<String> = listOf(
            "Roll No.",
            "Student Name",
            "Math (100)",
            "Science (50/18)",
            "Total",
            "Result",
            "Remarks",
        ),
        rows: List<List<String>> = listOf(
            listOf("1", "Asha", "80", "40", "120", "Pass", "Good"),
            listOf("2", "Ravi", "30", "30", "60", "Fail", "Needs work"),
        ),
    ) = ParsedTable(headers, rows)

    private fun plan(table: ParsedTable = table()) = ExamImportModel.plan(table)

    @Test
    fun `student columns are found and computed columns are not subjects`() {
        val plan = plan()

        assertEquals(1, plan.nameColumn)
        assertEquals(0, plan.rollColumn)
        assertEquals(listOf("Math", "Science"), plan.subjects.map { it.name })
        assertEquals(listOf("100", "50"), plan.subjects.map { it.maxMarks })
        assertEquals(listOf("35", "18"), plan.subjects.map { it.passMarks })
    }

    @Test
    fun `a max mark and pass mark can come from the header`() {
        assertEquals(100 to 35, ExamImportModel.headerMarks("Math (100)"))
        assertEquals(50 to 18, ExamImportModel.headerMarks("Science (50/18)"))
        assertEquals(80 to 28, ExamImportModel.headerMarks("Hindi - 80"))
        assertEquals(40 to 40, ExamImportModel.headerMarks("Art [40/40]"))
    }

    @Test
    fun `a subject name that merely contains a digit is not given a max`() {
        assertNull(ExamImportModel.headerMarks("Hindi 2nd Language"))
        assertNull(ExamImportModel.headerMarks("Class 2"))
        assertNull(ExamImportModel.headerMarks("Student Name"))
    }

    @Test
    fun `the display name drops the marks suffix`() {
        assertEquals("Math", ExamImportModel.headerName("Math (100)"))
        assertEquals("Science", ExamImportModel.headerName("Science (50/18)"))
        assertEquals("Hindi", ExamImportModel.headerName("Hindi - 80"))
        assertEquals("Hindi 2nd Language", ExamImportModel.headerName("Hindi 2nd Language"))
    }

    @Test
    fun `a numeric column without marks in the header defaults to 100 and 35`() {
        val plan = plan(
            table(
                headers = listOf("Roll No.", "Name", "Computer"),
                rows = listOf(listOf("1", "Asha", "77")),
            ),
        )

        assertEquals(1, plan.subjects.size)
        assertEquals("Computer", plan.subjects[0].name)
        assertEquals("100", plan.subjects[0].maxMarks)
        assertEquals("35", plan.subjects[0].passMarks)
    }

    @Test
    fun `a text-only column is not turned into a subject`() {
        val plan = plan(
            table(
                headers = listOf("Roll No.", "Name", "Notes", "Math (100)"),
                rows = listOf(listOf("1", "Asha", "Very good", "80")),
            ),
        )

        assertEquals(listOf("Math"), plan.subjects.map { it.name })
    }

    @Test
    fun `rolls match first even when the name is spelled differently`() {
        val rows = ExamImportModel.matchRows(
            table = table(
                rows = listOf(listOf("01", "Asha Sharma", "80", "40", "120", "Pass", "")),
            ),
            plan = plan(),
            candidates = candidates,
        )

        assertEquals("s1", rows.rows.single().studentId)
        assertEquals(0, rows.unmatched)
    }

    @Test
    fun `a missing roll falls back to the student name`() {
        val rows = ExamImportModel.matchRows(
            table = table(
                rows = listOf(listOf("", "  ravi   kumar ", "30", "30", "60", "Fail", "")),
            ),
            plan = plan(),
            candidates = candidates,
        )

        assertEquals("s2", rows.rows.single().studentId)
    }

    @Test
    fun `rows that match nobody are counted, never written`() {
        val rows = ExamImportModel.matchRows(
            table = table(
                rows = listOf(
                    listOf("1", "Asha", "80", "40", "120", "Pass", ""),
                    listOf("99", "Ghost", "10", "10", "20", "Fail", ""),
                ),
            ),
            plan = plan(),
            candidates = candidates,
        )

        assertEquals(listOf("s1"), rows.rows.map { it.studentId })
        assertEquals(1, rows.unmatched)
    }

    @Test
    fun `a second row for the same student is skipped as a duplicate`() {
        val rows = ExamImportModel.matchRows(
            table = table(
                rows = listOf(
                    listOf("1", "Asha", "80", "40", "120", "Pass", ""),
                    listOf("01", "Asha", "70", "30", "100", "Pass", ""),
                ),
            ),
            plan = plan(),
            candidates = candidates,
        )

        assertEquals(1, rows.rows.size)
        assertEquals(1, rows.duplicates)
    }

    @Test
    fun `blank and absent marks stay empty while text marks are flagged`() {
        val rows = ExamImportModel.matchRows(
            table = table(
                headers = listOf("Roll No.", "Name", "Math (100)", "Science (50)", "Remarks"),
                rows = listOf(listOf("1", "Asha", "Ab", "", "Good")),
            ),
            plan = plan(
                table(
                    headers = listOf("Roll No.", "Name", "Math (100)", "Science (50)", "Remarks"),
                    rows = listOf(listOf("1", "Asha", "Ab", "", "Good")),
                ),
            ),
            candidates = candidates,
        )

        val row = rows.rows.single()
        assertNull(row.marks[2])
        assertNull(row.marks[3])
        assertEquals(0, rows.unreadableMarks)
    }

    @Test
    fun `a mark that is neither a number nor absent is reported`() {
        val source = table(
            headers = listOf("Roll No.", "Name", "Math (100)"),
            rows = listOf(listOf("1", "Asha", "eighty")),
        )
        val rows = ExamImportModel.matchRows(source, plan(source), candidates)

        assertEquals(1, rows.unreadableMarks)
        assertNull(rows.rows.single().marks[2])
    }

    @Test
    fun `the write plan creates the exam, its subjects and only entered marks`() {
        val source = table(
            rows = listOf(
                listOf("1", "Asha", "80", "", "80", "Pass", ""),
                listOf("2", "Ravi", "30", "30", "60", "Fail", ""),
            ),
        )
        val startingPlan = plan(source)
        val rows = ExamImportModel.matchRows(source, startingPlan, candidates)

        val writes = ExamImportModel.writePlan(
            classId = "class-5b",
            examName = "  Unit Test  ",
            rows = rows,
            subjects = startingPlan.subjects,
            now = 1_000L,
            newId = ids(),
        )

        assertEquals("Unit Test", writes.exam.name)
        assertEquals("class-5b", writes.exam.classId)
        assertEquals(1_000L, writes.exam.examDate)
        assertEquals(listOf("Math", "Science"), writes.subjects.map { it.name })
        assertEquals(listOf(0, 1), writes.subjects.map { it.sortOrder })
        // Asha has no Science mark, so only three marks exist in total.
        assertEquals(3, writes.marks.size)
        assertTrue(writes.marks.all { it.examId == writes.exam.id })
        assertTrue(writes.marks.all { it.studentId in setOf("s1", "s2") })
        assertEquals(0, writes.clampedMarks)
    }

    @Test
    fun `a mark above the subject maximum is capped and counted`() {
        val source = table(
            headers = listOf("Roll No.", "Name", "Math (100)"),
            rows = listOf(listOf("1", "Asha", "120")),
        )
        val startingPlan = plan(source)
        val maths = startingPlan.subjects.single().copy(maxMarks = "50", passMarks = "18")

        val writes = ExamImportModel.writePlan(
            classId = "c1",
            examName = "Test",
            rows = ExamImportModel.matchRows(source, startingPlan, candidates),
            subjects = listOf(maths),
            now = 1L,
            newId = ids(),
        )

        assertEquals(50.0, writes.marks.single().marksObtained, 0.0)
        assertEquals(1, writes.clampedMarks)
    }

    @Test
    fun `an unchecked subject column is not written at all`() {
        val source = table()
        val startingPlan = plan(source)
        val kept = startingPlan.subjects.map { subject ->
            subject.copy(included = subject.name == "Math")
        }

        val writes = ExamImportModel.writePlan(
            classId = "c1",
            examName = "Test",
            rows = ExamImportModel.matchRows(source, startingPlan, candidates),
            subjects = kept,
            now = 1L,
            newId = ids(),
        )

        assertEquals(listOf("Math"), writes.subjects.map { it.name })
        assertTrue(writes.marks.all { mark -> writes.subjects.any { it.id == mark.subjectId } })
        assertEquals(2, writes.marks.size)
    }

    @Test
    fun `a subject with a bad maximum cannot be committed`() {
        val draft = ExamImportSubjectDraft(
            column = 2,
            name = "Math",
            maxMarks = "0",
            passMarks = "35",
        )

        assertFalse(draft.isValid)
        assertFalse(draft.copy(maxMarks = "100", passMarks = "101").isValid)
        assertTrue(draft.copy(maxMarks = "100", passMarks = "35").isValid)
    }

    private fun ids(): () -> String {
        var next = 0
        return { "id-${next++}" }
    }
}
