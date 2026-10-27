package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamExcelWriter
import com.example.helper.examsresults.util.StudentResult
import com.example.helper.examsresults.viewmodel.ExportConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The export is the teacher's handoff to Excel, so it must be a real workbook:
 * live formulas, numeric cells, blank cells for unentered marks, and a
 * conditional rule that colours only the Result column.
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
        assertTrue("total formula", sheet.contains("<f>=SUM(C2:D2)</f>"))
        assertTrue("percentage formula", sheet.contains("<f>=IF(F2=0,0,E2/F2*100)</f>"))
        assertTrue("pass formula", sheet.contains(RESULT_FORMULA))
        assertTrue("numeric marks are not text", sheet.contains("<v>80</v>") && sheet.contains("<v>40</v>"))
        assertTrue("no stale empty dimension", !sheet.contains("""<dimension ref="A1" />"""))
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

        val scienceCell = Regex("""<c r="D2"[^>]*?(?:/>|>.*?</c>)""").find(sheet)?.value
        assertTrue(
            "the unentered mark must not fabricate a zero: $scienceCell",
            scienceCell == null || !scienceCell.contains("<v>"),
        )
        assertTrue("the blank mark must stay inside the total", sheet.contains("<f>=SUM(C2:D2)</f>"))
        assertTrue("the blank mark must stay inside the result", sheet.contains("D2&gt;=18"))
        assertTrue(
            "a blank mark must pass the guard instead of failing",
            sheet.contains("""OR(C2="",C2&gt;=35)""") && sheet.contains("""OR(D2="",D2&gt;=18)"""),
        )
        assertTrue(
            "no marks at all leaves the result blank",
            sheet.contains("<f>=IF(COUNT(C2:D2)=0,\"\","),
        )
    }

    @Test
    fun `only the result column carries the conditional colours`() {
        val all = entries(workbook())
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val styles = all["xl/styles.xml"].orEmpty()
        val config = ExportConfig()

        assertTrue("two dxf fills", styles.contains("""<dxfs count="2">"""))
        assertTrue("pass colour in dxf", styles.contains(argb(config.passColor)))
        assertTrue("fail colour in dxf", styles.contains(argb(config.failColor)))

        val formatting = Regex("""<conditionalFormatting[^>]*>.*?</conditionalFormatting>""")
            .find(sheet)?.value.orEmpty()
        assertTrue("rule targets the result column", formatting.contains("""sqref="H2:H2""""))
        assertTrue(
            "pass rule",
            formatting.contains("""<cfRule type="cellIs" dxfId="0" priority="1" operator="equal"><formula>"PASS"</formula></cfRule>"""),
        )
        assertTrue(
            "fail rule",
            formatting.contains("""<cfRule type="cellIs" dxfId="1" priority="2" operator="equal"><formula>"FAIL"</formula></cfRule>"""),
        )

        val outsideRules = styles.replace(Regex("""<dxfs.*?</dxfs>""", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(
            "colours live only in the conditional rules, not in per-row styles",
            !outsideRules.contains(argb(config.passColor)) && !outsideRules.contains(argb(config.failColor)),
        )
    }

    @Test
    fun `failing student gets the same rule and the fail colour`() {
        val failed = result.copy(
            isPass = false,
            marksBySubject = mapOf("math" to 20.0, "science" to 10.0),
            failedSubjects = listOf("math", "science"),
        )
        val workbook = workbook(failed)
        val styles = entries(workbook)["xl/styles.xml"].orEmpty()
        val sheet = entries(workbook)["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue("fail colour available", styles.contains(argb(ExportConfig().failColor)))
        assertTrue("the formula is row-relative", sheet.contains(RESULT_FORMULA))
        assertTrue("the same rule applies to this student", sheet.contains("""<formula>"FAIL"</formula>"""))
    }

    private val RESULT_FORMULA = """<f>=IF(COUNT(C2:D2)=0,"",IF(AND(OR(C2="",C2&gt;=35),OR(D2="",D2&gt;=18)),"PASS","FAIL"))</f>"""

    private fun workbook(result: StudentResult = this.result): ByteArray =
        ExamExcelWriter.buildWorkbook(subjects, listOf(result), ExportConfig())

    private fun argb(color: Long): String =
        color.toString(16).padStart(8, '0').uppercase()

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
