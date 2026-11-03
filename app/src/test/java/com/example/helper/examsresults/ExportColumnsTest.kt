package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamExcelWriter
import com.example.helper.examsresults.export.ExportColumnDefaults
import com.example.helper.examsresults.export.ExportFormulaEngine
import com.example.helper.examsresults.util.StudentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Formula columns are stored symbolically, so these tests pin the two things
 * that can silently break a teacher's sheet: the token → A1 mapping after a
 * reorder, and the validation that blocks an export instead of writing a
 * broken formula.
 */
class ExportColumnsTest {

    private val subjects = listOf(
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
        ExamSubject(id = "science", examId = "e1", name = "Science", maxMarks = 50, passMarks = 18),
    )

    private val student = Student(
        id = "s1",
        classId = "c1",
        name = "Asha",
        rollNumber = "1",
        createdAt = 0,
        updatedAt = 0,
    )

    private val result = StudentResult(
        student = student,
        marksBySubject = mapOf("math" to 80.0, "science" to 40.0),
        totalObtained = 120.0,
        totalMax = 150.0,
        percentage = 80.0,
        isPass = true,
        failedSubjects = emptyList(),
    )

    @Test
    fun `defaults keep total, max, percentage and result after the subjects`() {
        val all = workbookEntries(ExportColumnDefaults.columns())
        val text = all.values.joinToString("\n")

        assertTrue(text.contains("Total Obtained"))
        assertTrue(text.contains("Total Max"))
        assertTrue(text.contains("Percentage"))
        assertTrue(text.contains("Result"))
        assertTrue("total starts the computed block", text.contains("<f>SUM(D2:E2)</f>"))
        assertTrue("total max is a value", text.contains("<v>150</v>"))
    }

    @Test
    fun `an emptied layout stays empty instead of restoring the defaults`() {
        val stored = ExportColumnDefaults.forStorage(emptyList())

        assertTrue(ExportColumnDefaults.effective(stored).isEmpty())
    }

    @Test
    fun `deleting every computed column still exports the marks-only summary`() {
        val entries = workbookEntries(emptyList())
        val results = entries["xl/worksheets/sheet1.xml"].orEmpty() +
            entries["xl/sharedStrings.xml"].orEmpty()

        assertTrue(results.contains("Roll No."))
        assertTrue(results.contains("Student Name"))
        assertFalse(results.contains("Total Obtained"))
        assertFalse(results.contains("Result"))
        // The summary's per-subject analysis reads only names and marks, so it
        // is still worth writing; the verdict cells are the ones left blank.
        val summary = entries["xl/worksheets/sheet2.xml"].orEmpty()
        assertTrue(summary.contains("<f>COUNTA('Results'!${'$'}C$2:${'$'}C$2)</f>"))
        assertTrue(summary.contains("<f>MAX(C3:C4)</f>"))
        assertFalse(summary.contains(""","Pass")</f>"""))
    }

    @Test
    fun `a custom column references subjects by name`() {
        val custom = ExportColumn(
            id = "custom-x",
            label = "x",
            kind = ExportColumnKind.CUSTOM,
            formula = "{Math}+{Science}",
            sortOrder = 4,
        )
        val all = workbookEntries(ExportColumnDefaults.columns() + custom)
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue("custom header", all.values.joinToString("\n").contains("x"))
        assertTrue("custom formula", sheet.contains("<f>D2+E2</f>"))
    }

    @Test
    fun `reordering moves the A1 references with the columns`() {
        val reordered = listOf(
            ExportColumnDefaults.columns()[2].copy(sortOrder = 0), // Percentage first
            ExportColumnDefaults.columns()[0].copy(sortOrder = 1), // Total Obtained
            ExportColumnDefaults.columns()[1].copy(sortOrder = 2), // Total Max
            ExportColumnDefaults.columns()[3].copy(sortOrder = 3), // Result
        )
        val sheet = sheet(reordered)

        // F Percentage, G Total Obtained, H Total Max, I Result.
        assertTrue(
            "percentage now points at F and G",
            sheet.contains("<f>IF(H2=0,0,G2/H2)</f>"),
        )
        assertTrue("total moved to F", sheet.contains("<f>SUM(D2:E2)</f>"))
        assertTrue("the colour rule follows the Result column", sheet.contains("""sqref="I2:I2""""))
    }

    @Test
    fun `the colour rule follows the Result column when it moves`() {
        val columns = ExportColumnDefaults.columns()
        val reordered = listOf(
            columns[3].copy(sortOrder = 0), // Result first
            columns[2].copy(sortOrder = 1),
            columns[0].copy(sortOrder = 2),
            columns[1].copy(sortOrder = 3),
        )
        val sheet = sheet(reordered)

        assertTrue("Result is now F", sheet.contains("""sqref="F2:F2""""))
        assertTrue("and the pass rule is still there", sheet.contains("""<formula>"Fail"</formula>"""))
    }

    @Test
    fun `a custom column can reference another custom column by label`() {
        val columns = ExportColumnDefaults.columns() + listOf(
            ExportColumn(
                id = "custom-x",
                label = "x",
                kind = ExportColumnKind.CUSTOM,
                formula = "{Math}*2",
                sortOrder = 4,
            ),
            ExportColumn(
                id = "custom-y",
                label = "y",
                kind = ExportColumnKind.CUSTOM,
                formula = "{x}+1",
                sortOrder = 5,
            ),
        )
        val sheet = sheet(columns)

        assertTrue("x lands in J", sheet.contains("<f>D2*2</f>"))
        assertTrue("y references x's column", sheet.contains("<f>J2+1</f>"))
    }

    @Test
    fun `a pasted A1 formula is written verbatim`() {
        val pasted = ExportColumn(
            id = "custom-paste",
            label = "Pasted",
            kind = ExportColumnKind.CUSTOM,
            formula = "=SUM(C2:D2)",
            sortOrder = 4,
        )
        val sheet = sheet(ExportColumnDefaults.columns() + pasted)

        assertTrue(sheet.contains("<f>SUM(C2:D2)</f>"))
    }

    @Test
    fun `a disabled column is left off the sheet`() {
        val hidden = ExportColumn(
            id = "custom-hidden",
            label = "Hidden",
            kind = ExportColumnKind.CUSTOM,
            formula = "{Math}",
            sortOrder = 4,
            enabled = false,
        )
        val all = workbookEntries(ExportColumnDefaults.columns() + hidden)
        val text = all.values.joinToString("\n")

        assertFalse(text.contains("Hidden"))
    }

    @Test
    fun `an unknown field blocks the export and names the column`() {
        val bad = ExportColumn(
            id = "custom-bad",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "{Nope}+1",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertEquals(1, issues.size)
        assertEquals("Bonus", issues.single().columnLabel)
        assertTrue(issues.single().message.contains("{Nope}"))

        val thrown = runCatching { sheet(ExportColumnDefaults.columns() + bad) }.exceptionOrNull()
        assertTrue("writer blocks an invalid layout", thrown is IOException)
        assertTrue(thrown?.message.orEmpty().contains("Bonus"))
    }

    @Test
    fun `a formula loop is reported for every column in it`() {
        val loop = ExportColumnDefaults.columns() + listOf(
            ExportColumn(
                id = "custom-a",
                label = "a",
                kind = ExportColumnKind.CUSTOM,
                formula = "{b}",
                sortOrder = 4,
            ),
            ExportColumn(
                id = "custom-b",
                label = "b",
                kind = ExportColumnKind.CUSTOM,
                formula = "{a}",
                sortOrder = 5,
            ),
        )
        val issues = ExportFormulaEngine.validate(loop, subjects)

        assertEquals(setOf("a", "b"), issues.map { it.columnLabel }.toSet())
    }

    @Test
    fun `a column cannot use its own value`() {
        val self = ExportColumn(
            id = "custom-self",
            label = "x",
            kind = ExportColumnKind.CUSTOM,
            formula = "{x}+1",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + self, subjects)

        assertEquals("x", issues.single().columnLabel)
        assertTrue(issues.single().message.contains("own value"))
    }

    @Test
    fun `unbalanced parentheses are rejected`() {
        val bad = ExportColumn(
            id = "custom-parens",
            label = "Broken",
            kind = ExportColumnKind.CUSTOM,
            formula = "SUM({Math}",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertTrue(issues.single().message.contains("Parentheses"))
    }

    @Test
    fun `plain words are blocked before they reach the sheet`() {
        val bad = ExportColumn(
            id = "custom-words",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "total marks please",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertEquals("Bonus", issues.single().columnLabel)
        assertTrue(issues.single().message.contains("Unknown name"))
    }

    @Test
    fun `a pasted formula with a bare word is blocked`() {
        val bad = ExportColumn(
            id = "custom-pasted-word",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "=SUM(marks)",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertTrue(issues.single().message.contains("Unknown name"))
    }

    @Test
    fun `a pasted formula that still uses a field explains what to do`() {
        val bad = ExportColumn(
            id = "custom-pasted-field",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "=SUM({Math})",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        val message = issues.single().message
        assertTrue(message.contains("{Math}"))
        assertTrue(message.contains("Remove the “=”"))
    }

    @Test
    fun `an Excel error value is blocked`() {
        val bad = ExportColumn(
            id = "custom-error",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "=#REF!+1",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertTrue(issues.single().message.contains("error value"))
    }

    @Test
    fun `a missing function argument is blocked`() {
        val bad = ExportColumn(
            id = "custom-comma",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "SUM({Math},,{Science})",
            sortOrder = 4,
        )
        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + bad, subjects)

        assertTrue(issues.single().message.contains("argument is missing"))
    }

    @Test
    fun `a formula with no field still exports but gets a heads-up`() {
        val constant = ExportColumn(
            id = "custom-constant",
            label = "Grace",
            kind = ExportColumnKind.CUSTOM,
            formula = "5+5",
            sortOrder = 4,
        )

        val issues = ExportFormulaEngine.validate(ExportColumnDefaults.columns() + constant, subjects)

        assertTrue(issues.isEmpty())
        val notice = ExportFormulaEngine.notice(constant, ExportColumnDefaults.columns() + constant, subjects)
        assertTrue(notice.orEmpty().contains("same value"))
    }

    @Test
    fun `every default column passes the formula check`() {
        val columns = ExportColumnDefaults.columns()

        columns.forEach { column ->
            val error = ExportFormulaEngine.blockingError(column, columns, subjects)
            assertTrue("${column.label}: $error", error == null)
            assertTrue(ExportFormulaEngine.notice(column, columns, subjects) == null)
        }
    }

    @Test
    fun `a real pasted formula passes`() {
        val good = ExportColumn(
            id = "custom-pasted-good",
            label = "Bonus",
            kind = ExportColumnKind.CUSTOM,
            formula = "=IF(C2>=35,ROUND(C2*0.1,1),0)",
            sortOrder = 4,
        )

        assertTrue(ExportFormulaEngine.blockingError(good, ExportColumnDefaults.columns() + good, subjects) == null)
    }

    private fun sheet(columns: List<ExportColumn>): String =
        workbookEntries(columns)["xl/worksheets/sheet1.xml"].orEmpty()

    private fun workbookEntries(columns: List<ExportColumn>): Map<String, String> =
        entries(
            ExamExcelWriter.buildWorkbook(
                subjects = subjects,
                results = listOf(result),
                columns = columns,
            ),
        )

    private fun entries(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                map[entry.name] = zip.readBytes().decodeToString()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }
}
