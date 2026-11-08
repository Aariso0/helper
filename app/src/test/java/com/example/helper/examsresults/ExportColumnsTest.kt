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
    fun `defaults keep total, percentage, result and rank after the subjects`() {
        val all = workbookEntries(ExportColumnDefaults.columns())
        val text = all.values.joinToString("\n")

        assertTrue(text.contains("Total (150)"))
        assertTrue(text.contains("Percentage"))
        assertTrue(text.contains("Result"))
        assertTrue(text.contains("Rank"))
        assertFalse("default uses the short name", text.contains("Total Obtained"))
        assertFalse("Total Max is printed inside the total, not as a column", text.contains("Total Max"))
        assertTrue("total starts the computed block", text.contains("<f>SUM(D7:E7)</f>"))
        assertTrue(
            "rank shares a place on ties",
            text.contains("<f>IF(F7=\"\",\"\",RANK(F7,"),
        )
    }

    @Test
    fun `an emptied layout stays empty instead of restoring the defaults`() {
        val stored = ExportColumnDefaults.forStorage(emptyList())

        assertTrue(ExportColumnDefaults.effective(stored).isEmpty())
    }

    @Test
    fun `a 1_2 layout loses the whole-number percentage and the Total Max column`() {
        val stored = listOf(
            ExportColumn(
                id = ExportColumnDefaults.TOTAL_OBTAINED_ID,
                label = "Total Obtained",
                kind = ExportColumnKind.TOTAL_OBTAINED,
                formula = ExportColumnDefaults.TOTAL_OBTAINED_TEMPLATE,
                sortOrder = 0,
            ),
            ExportColumn(
                id = ExportColumnDefaults.PERCENTAGE_ID,
                label = "Percentage",
                kind = ExportColumnKind.PERCENTAGE,
                formula = "IF({Total Max}=0,0,{Total Obtained}/{Total Max}*100)",
                sortOrder = 1,
            ),
            ExportColumn(
                id = ExportColumnDefaults.TOTAL_MAX_ID,
                label = "Total Max",
                kind = ExportColumnKind.TOTAL_MAX,
                formula = "",
                sortOrder = 2,
            ),
        )

        val effective = ExportColumnDefaults.effective(stored)

        assertEquals(
            listOf(ExportColumnKind.TOTAL_OBTAINED, ExportColumnKind.PERCENTAGE),
            effective.map { it.kind },
        )
        assertEquals(
            "the cell is formatted 0.00%, so the formula must produce a share",
            ExportColumnDefaults.PERCENTAGE_TEMPLATE,
            effective.first { it.kind == ExportColumnKind.PERCENTAGE }.formula,
        )

        val all = workbookEntries(effective)
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue(
            "the exported percentage is a share again",
            sheet.contains("<f>IF(150=0,0,F7/150)</f>"),
        )
        assertTrue(
            "the maximum is stated once in the header",
            all["xl/sharedStrings.xml"].orEmpty().contains("Total Obtained (150)"),
        )
        assertFalse(
            "the Total Max column is gone",
            all.values.joinToString("\n").contains("Total Max"),
        )
    }

    @Test
    fun `deleting every computed column still exports the marks-only summary`() {
        val entries = workbookEntries(emptyList())
        val results = entries["xl/worksheets/sheet1.xml"].orEmpty() +
            entries["xl/sharedStrings.xml"].orEmpty()

        assertTrue(results.contains("Roll No."))
        assertTrue(results.contains("Student Name"))
        assertFalse(results.contains("Total (150)"))
        assertFalse(results.contains("Total Obtained"))
        assertFalse(results.contains("Result"))
        // The summary's per-subject analysis reads only names and marks, so it
        // is still worth writing; the verdict cells are the ones left blank.
        val summary = entries["xl/worksheets/sheet2.xml"].orEmpty()
        assertTrue(summary.contains("<f>COUNTA('Results'!${'$'}C$7:${'$'}C$7)</f>"))
        assertTrue(summary.contains("<f>MAX(C7:C8)</f>"))
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
        assertTrue("custom formula", sheet.contains("<f>D7+E7</f>"))
    }

    @Test
    fun `reordering moves the A1 references with the columns`() {
        val defaults = ExportColumnDefaults.columns()
        fun of(kind: ExportColumnKind) = defaults.first { it.kind == kind }
        val totalMax = ExportColumn(
            id = ExportColumnDefaults.TOTAL_MAX_ID,
            label = "Total Max",
            kind = ExportColumnKind.TOTAL_MAX,
            formula = "",
            sortOrder = 2,
        )
        val reordered = listOf(
            of(ExportColumnKind.PERCENTAGE).copy(sortOrder = 0), // Percentage first
            of(ExportColumnKind.TOTAL_OBTAINED).copy(sortOrder = 1),
            totalMax,
            of(ExportColumnKind.RESULT).copy(sortOrder = 3),
        )
        val sheet = sheet(reordered)

        // F Percentage, G Total Obtained, H Total Max, I Result.
        assertTrue(
            "percentage now points at H and G",
            sheet.contains("<f>IF(H7=0,0,G7/H7)</f>"),
        )
        assertTrue("total moved to G", sheet.contains("<f>SUM(D7:E7)</f>"))
        assertTrue("the colour rule follows the Result column", sheet.contains("""sqref="I7:I7""""))
    }

    @Test
    fun `the colour rule follows the Result column when it moves`() {
        val columns = ExportColumnDefaults.columns()
        fun of(kind: ExportColumnKind) = columns.first { it.kind == kind }
        val reordered = listOf(
            of(ExportColumnKind.RESULT).copy(sortOrder = 0), // Result first
            of(ExportColumnKind.RANK).copy(sortOrder = 1),
            of(ExportColumnKind.TOTAL_OBTAINED).copy(sortOrder = 2),
            of(ExportColumnKind.PERCENTAGE).copy(sortOrder = 3),
        )
        val sheet = sheet(reordered)

        assertTrue("Result is now F", sheet.contains("""sqref="F7:F7""""))
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

        assertTrue("x lands in J", sheet.contains("<f>D7*2</f>"))
        assertTrue("y references x's column", sheet.contains("<f>J7+1</f>"))
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
    fun `rank needs a total column`() {
        val columns = ExportColumnDefaults.columns()
            .filterNot { it.kind == ExportColumnKind.TOTAL_OBTAINED }
        val issues = ExportFormulaEngine.validate(columns, subjects)

        assertTrue(
            issues.any {
                it.columnLabel == "Rank" && it.message.contains("Total column")
            },
        )
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
