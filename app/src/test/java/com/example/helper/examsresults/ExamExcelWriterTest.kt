package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamExcelWriter
import com.example.helper.examsresults.util.StudentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The export is the teacher's handoff to Excel, so it must be a real workbook:
 * live formulas, numeric cells, blank cells for unentered marks, a plain
 * pass / bold-italic fail Result column, and a thin border on every used cell
 * so a printed copy has clean lines.
 */
class ExamExcelWriterTest {

    private val student = Student(
        id = "s1",
        classId = "c1",
        name = "Asha",
        rollNumber = "1",
        createdAt = 0,
        updatedAt = 0,
    )

    private val subjects = listOf(
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
        ExamSubject(id = "science", examId = "e1", name = "Science", maxMarks = 50, passMarks = 18),
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
    fun `workbook carries live formulas and numeric cells`() {
        val sheet = entries(workbook())["xl/worksheets/sheet1.xml"].orEmpty()
        assertTrue("total formula", sheet.contains("<f>SUM(D2:E2)</f>"))
        assertTrue("percentage formula", sheet.contains("<f>IF(G2=0,0,F2/G2*100)</f>"))
        assertTrue("pass formula", sheet.contains(RESULT_FORMULA))
        assertTrue("numeric marks are not text", sheet.contains("<v>80</v>") && sheet.contains("<v>40</v>"))
        assertTrue("no stale empty dimension", !sheet.contains("""<dimension ref="A1" />"""))
        assertFalse("formulas carry no leading '='", sheet.contains("<f>="))
        assertTrue(
            "Excel recalculates on open, so computed cells are not blank",
            entries(workbook())["xl/workbook.xml"].orEmpty().contains("""fullCalcOnLoad="1""""),
        )
    }

    @Test
    fun `S No starts at one and runs before the roll number`() {
        val other = result.copy(
            student = result.student.copy(id = "s2", name = "Ravi", rollNumber = "2"),
        )
        val all = entries(
            ExamExcelWriter.buildWorkbook(subjects, listOf(result, other)),
        )
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()

        assertTrue("the header names the column", shared.contains("S.No."))
        assertTrue(
            "the first student is 1",
            Regex("""<c r="A2"[^>]*><v>1</v></c>""").containsMatchIn(sheet),
        )
        assertTrue(
            "the second student is 2",
            Regex("""<c r="A3"[^>]*><v>2</v></c>""").containsMatchIn(sheet),
        )
        assertTrue("roll numbers moved one column right", sheet.contains("""<c r="B2" """))
    }

    @Test
    fun `unentered marks stay blank but stay in the formulas`() {
        val partial = result.copy(
            marksBySubject = mapOf("math" to 80.0),
            totalObtained = 80.0,
            percentage = 53.333,
            isPass = true,
            failedSubjects = emptyList(),
        )
        val sheet = entries(workbook(partial))["xl/worksheets/sheet1.xml"].orEmpty()

        val scienceCell = Regex("""<c r="E2"[^>]*?(?:/>|>.*?</c>)""").find(sheet)?.value
        assertTrue(
            "the unentered mark must not fabricate a zero: $scienceCell",
            scienceCell == null || !scienceCell.contains("<v>"),
        )
        assertTrue("the blank mark must stay inside the total", sheet.contains("<f>SUM(D2:E2)</f>"))
        assertTrue("the blank mark must stay inside the result", sheet.contains("E2&gt;=18"))
        assertTrue(
            "a blank mark must pass the guard instead of failing",
            sheet.contains("""OR(D2="",D2&gt;=35)""") && sheet.contains("""OR(E2="",E2&gt;=18)"""),
        )
        assertTrue(
            "no marks at all leaves the result blank",
            sheet.contains("<f>IF(COUNT(D2:E2)=0,\"\","),
        )
    }

    @Test
    fun `pass stays plain and fail is bold italic with no colour fills`() {
        val all = entries(workbook())
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val styles = all["xl/styles.xml"].orEmpty()

        assertTrue("one dxf for the failure style", styles.contains("""<dxfs count="1">"""))
        assertTrue(
            "fail is bold italic",
            styles.contains("<dxf><font><b /><i /></font></dxf>"),
        )
        assertFalse("the old green is gone", styles.contains("4CAF50"))
        assertFalse("the old red is gone", styles.contains("F44336"))

        val formatting = Regex("""<conditionalFormatting[^>]*>.*?</conditionalFormatting>""")
            .find(sheet)?.value.orEmpty()
        assertTrue("rule targets the result column", formatting.contains("""sqref="I2:I2""""))
        assertTrue(
            "only fail triggers the emphasis",
            formatting.contains("""<cfRule type="cellIs" dxfId="0" priority="1" operator="equal"><formula>"Fail"</formula></cfRule>"""),
        )
        assertFalse("pass gets no rule", formatting.contains("\"Pass\""))
    }

    @Test
    fun `failing student gets the same rule and the fail emphasis`() {
        val failed = result.copy(
            isPass = false,
            marksBySubject = mapOf("math" to 20.0, "science" to 10.0),
            failedSubjects = listOf("math", "science"),
        )
        val workbook = workbook(failed)
        val styles = entries(workbook)["xl/styles.xml"].orEmpty()
        val sheet = entries(workbook)["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue("fail emphasis available", styles.contains("<dxf><font><b /><i /></font></dxf>"))
        assertTrue("the formula is row-relative", sheet.contains(RESULT_FORMULA))
        assertTrue("the same rule applies to this student", sheet.contains("""<formula>"Fail"</formula>"""))
    }

    @Test
    fun `every used cell carries a thin print border`() {
        val all = entries(workbook())
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val styles = all["xl/styles.xml"].orEmpty()

        assertTrue(
            "a border with thin lines on all four sides",
            Regex("""style="thin"""").findAll(styles).count() >= 4,
        )

        val cells = Regex("""<c [^>]*>""").findAll(sheet).map { it.value }.toList()
        assertTrue("cells exist", cells.isNotEmpty())
        assertTrue(
            "every used cell points at the bordered style",
            cells.all { it.contains(""" s="""") },
        )
        assertTrue(
            "bordered xfs flag applyBorder, or a viewer may drop the lines",
            Regex("""<xf [^>]*borderId="[1-9][0-9]*"[^>]*applyBorder="1"""").containsMatchIn(styles),
        )
    }

    @Test
    fun `the results tab is called Results and the summary comes after it`() {
        val all = entries(workbook())
        val book = all["xl/workbook.xml"].orEmpty()

        assertTrue("results tab", book.contains("""name="Results""""))
        assertTrue("summary tab", book.contains("""name="Summary""""))
        assertTrue(
            "results must stay the first tab",
            book.indexOf("""name="Results"""") < book.indexOf("""name="Summary""""),
        )
        assertTrue("the summary has its own worksheet part", all.containsKey("xl/worksheets/sheet2.xml"))
    }

    @Test
    fun `the summary sheet is a live formula copy of the results, not numbers`() {
        val summary = entries(workbook())["xl/worksheets/sheet2.xml"].orEmpty()
        val book = entries(workbook())["xl/sharedStrings.xml"].orEmpty()

        assertTrue("the row labels are there", book.contains("REGISTERED") && book.contains("95-ABOVE"))
        assertTrue("registered counts the names", summary.contains("<f>COUNTA('Results'!C2:C2)</f>"))
        assertTrue(
            "appeared is the rows with a verdict",
            summary.contains("""<f>COUNTIF('Results'!I2:I2,"Pass")+COUNTIF('Results'!I2:I2,"Fail")</f>"""),
        )
        assertTrue("passed", summary.contains("""<f>COUNTIF('Results'!I2:I2,"Pass")</f>"""))
        assertTrue("pass share reads the summary's own cells", summary.contains("<f>IF(B4=0,0,B5*100/B4)</f>"))
        assertTrue("average", summary.contains("<f>IF(B4=0,0,SUM('Results'!H2:H2)/B4)</f>"))
        assertTrue(
            "the low band stops at the first boundary",
            summary.contains("""<f>B4-COUNTIF('Results'!H2:H2,"&gt;=33")</f>"""),
        )
        assertTrue(
            "a middle band is a pair of thresholds",
            summary.contains(
                """<f>COUNTIF('Results'!H2:H2,"&gt;=60")-COUNTIF('Results'!H2:H2,"&gt;=75")</f>""",
            ),
        )
        assertTrue(
            "the top band is open ended",
            summary.contains("""<f>COUNTIF('Results'!H2:H2,"&gt;=95")</f>"""),
        )
        assertFalse(
            "no number is frozen into the summary",
            Regex("""<c [^>]*><f>[^<]*</f><v>[^<]+</v></c>""").containsMatchIn(summary),
        )
        assertTrue("a formula cell has an empty cached value", summary.contains("<f>COUNTA('Results'!C2:C2)</f><v></v>"))
        assertTrue("the declared extent covers the table", summary.contains("""<dimension ref="A1:B14" />"""))
    }

    @Test
    fun `the summary keeps the same thin borders and no fill`() {
        val all = entries(workbook())
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()

        val cells = Regex("""<c [^>]*>""").findAll(summary).map { it.value }.toList()
        assertTrue("the summary has cells", cells.isNotEmpty())
        assertTrue("every summary cell is bordered", cells.all { it.contains(""" s="""") })
        assertFalse("no fill anywhere", all["xl/styles.xml"].orEmpty().contains("solid"))
    }

    private val RESULT_FORMULA = """<f>IF(COUNT(D2:E2)=0,"",IF(AND(OR(D2="",D2&gt;=35),OR(E2="",E2&gt;=18)),"Pass","Fail"))</f>"""

    private fun workbook(result: StudentResult = this.result): ByteArray =
        ExamExcelWriter.buildWorkbook(subjects, listOf(result))

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
        assertEquals(true, map.isNotEmpty())
        return map
    }
}
