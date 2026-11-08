package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.export.ExamExcelWriter
import com.example.helper.examsresults.export.ExportColumnDefaults
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
 * pass / bold-italic fail Result column, a thin border on every used cell, and
 * a horizontal analysis block whose percentages are stored as shares and
 * displayed with a real `0.00%` number format.
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
        assertTrue("total formula", sheet.contains("<f>SUM(D7:E7)</f>"))
        assertTrue("percentage is a share, not out of 100", sheet.contains("<f>IF(150=0,0,F7/150)</f>"))
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
            Regex("""<c r="A7"[^>]*><v>1</v></c>""").containsMatchIn(sheet),
        )
        assertTrue(
            "the second student is 2",
            Regex("""<c r="A8"[^>]*><v>2</v></c>""").containsMatchIn(sheet),
        )
        assertTrue("roll numbers moved one column right", sheet.contains("""<c r="B7" """))
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

        val scienceCell = Regex("""<c r="E7"[^>]*?(?:/>|>.*?</c>)""").find(sheet)?.value
        assertTrue(
            "the unentered mark must not fabricate a zero: $scienceCell",
            scienceCell == null || !scienceCell.contains("<v>"),
        )
        assertTrue("the blank mark must stay inside the total", sheet.contains("<f>SUM(D7:E7)</f>"))
        assertTrue("the blank mark must stay inside the result", sheet.contains("N(E7)&gt;=18"))
        assertTrue(
            "a blank mark must pass the guard instead of failing",
            sheet.contains("""OR(ISBLANK(D7),N(D7)&gt;=35)""") &&
                sheet.contains("""OR(ISBLANK(E7),N(E7)&gt;=18)"""),
        )
        assertTrue(
            "no marks at all leaves the result blank",
            sheet.contains("""IF(AND(COUNT(D7:E7)=0,COUNTIF(D7:E7,"ABT")=0),"","""),
        )
    }

    @Test
    fun `an absent subject prints ABT and is not counted as appeared`() {
        val absent = result.copy(
            marksBySubject = mapOf("math" to 80.0),
            totalObtained = 80.0,
            percentage = 53.333,
            isPass = false,
            failedSubjects = listOf("science"),
            absentSubjectIds = setOf("science"),
        )
        val all = entries(workbook(absent))
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()

        assertTrue("ABT is written as text, never as a zero", shared.contains(">ABT<"))
        val scienceCell = Regex("""<c r="E7"[^>]*?(?:/>|>.*?</c>)""").find(sheet)?.value.orEmpty()
        assertTrue("the absent cell is a shared string: $scienceCell", scienceCell.contains(""" t="s""""))
        assertFalse("the absent cell is not numeric zero", scienceCell.contains("<v>0</v>"))
        assertTrue(
            "Excel still treats no numeric mark as appeared",
            summary.contains("""COUNTIF('Results'!${'$'}E$7:${'$'}E$7,"&gt;=0")"""),
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
        assertTrue("rule targets the result column", formatting.contains("""sqref="H7:H7""""))
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
    fun `the summary is a horizontal live block with a subject row per subject`() {
        val all = entries(workbook())
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()

        assertTrue("the banner names the exam", shared.contains("Mid Term"))
        assertTrue("the banner stamps the class", shared.contains("CLASS-5-A"))
        assertTrue("subject rows carry name and max", shared.contains("Math (100)") && shared.contains("Science (50)"))
        assertTrue("overall row", shared.contains("OVER ALL CLASS"))
        assertTrue("class-wise block", shared.contains("CLASS-WISE"))
        assertTrue("registered counts names", summary.contains("<f>COUNTA('Results'!${'$'}C$7:${'$'}C$7)</f>"))
        assertTrue(
            "appeared counts entered marks",
            summary.contains("""<f>COUNTIF('Results'!${'$'}D$7:${'$'}D$7,"&gt;=0")</f>"""),
        )
        assertTrue(
            "passed uses the configured pass mark",
            summary.contains("""<f>COUNTIF('Results'!${'$'}D$7:${'$'}D$7,"&gt;=35")</f>"""),
        )
        assertTrue("pass share is a fraction", summary.contains("<f>IF(C7=0,0,D7/C7)</f>"))
        assertTrue(
            "subject average is a fraction of the max",
            summary.contains("<f>IF(C7=0,0,SUM('Results'!${'$'}D$7:${'$'}D$7)/C7/100)</f>"),
        )
        assertTrue(
            "the low band keeps the example's INT boundary",
            summary.contains("""<f>COUNTIF('Results'!${'$'}D$7:${'$'}D$7,"&lt;"&amp;INT(100*33/100))</f>"""),
        )
        assertTrue(
            "a band subtracts only the bands above it",
            summary.contains(
                """<f>COUNTIF('Results'!${'$'}D$7:${'$'}D$7,"&gt;="&amp;100*33/100)-I7-J7-K7-L7-M7</f>""",
            ),
        )
        assertTrue("overall appeared is the max", summary.contains("<f>MAX(C7:C8)</f>"))
        assertTrue(
            "overall passed counts the verdict",
            summary.contains("""<f>COUNTIF('Results'!${'$'}H$7:${'$'}H$7,"Pass")</f>"""),
        )
        assertTrue(
            "the class-wise block points at the overall row",
            summary.contains("<f>F9</f>"),
        )
        assertFalse(
            "no number is frozen into the summary",
            Regex("""<c [^>]*><f>[^<]*</f><v>[^<]+</v></c>""").containsMatchIn(summary),
        )
        assertTrue("a formula cell has an empty cached value", summary.contains("<f>COUNTA('Results'!${'$'}C$7:${'$'}C$7)</f><v></v>"))
        assertTrue("the declared extent covers the block", summary.contains("""<dimension ref="A1:Q9" />"""))
    }

    @Test
    fun `percentage cells carry a real percent number format in the zip`() {
        val all = entries(workbook())

        assertEquals("results percentage", "10", numberFormatId(all, "xl/worksheets/sheet1.xml", "G7"))
        assertEquals("subject pass share", "10", numberFormatId(all, "xl/worksheets/sheet2.xml", "E7"))
        assertEquals("subject average", "10", numberFormatId(all, "xl/worksheets/sheet2.xml", "F7"))
        assertEquals("class pass share", "10", numberFormatId(all, "xl/worksheets/sheet2.xml", "P9"))
        assertEquals("class average", "10", numberFormatId(all, "xl/worksheets/sheet2.xml", "Q9"))
    }

    @Test
    fun `the summary keeps the same thin borders`() {
        val all = entries(workbook())
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()

        val cells = Regex("""<c [^>]*>""").findAll(summary).map { it.value }.toList()
        assertTrue("the summary has cells", cells.isNotEmpty())
        assertTrue("every summary cell is bordered", cells.all { it.contains(""" s="""") })
        assertTrue("the class-wise block is last", summary.contains("""<c r="Q9""""))
    }

    @Test
    fun `deleting the percentage column still writes the marks summary`() {
        val columns = ExportColumnDefaults.columns()
            .filterNot { it.kind == ExportColumnKind.PERCENTAGE }
        val all = entries(
            ExamExcelWriter.buildWorkbook(subjects, listOf(result), columns, "Mid Term", "5-A"),
        )
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()

        assertTrue("the sheet is still there", all.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue("marks analysis still works", summary.contains("<f>MAX(C7:C8)</f>"))
        assertTrue(
            "the verdict moved to the Result column and still counts",
            summary.contains("""<f>COUNTIF('Results'!${'$'}G$7:${'$'}G$7,"Pass")</f>"""),
        )
        assertFalse(
            "the overall average has no percentage column to read",
            summary.contains("<f>IF(C9=0,0,SUM('Results'!${'$'}G$7:${'$'}G$7)/C9)</f>"),
        )
    }

    @Test
    fun `deleting the result column leaves the verdict cells blank, not the whole summary`() {
        val columns = ExportColumnDefaults.columns()
            .filterNot { it.kind == ExportColumnKind.RESULT }
        val all = entries(
            ExamExcelWriter.buildWorkbook(subjects, listOf(result), columns, "Mid Term", "5-A"),
        )
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()

        assertTrue("the sheet is still there", all.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue("overall appeared is still the max", summary.contains("<f>MAX(C7:C8)</f>"))
        assertTrue("the share block still has its average", summary.contains("<f>F9</f>"))
        assertFalse("no verdict column means no verdict count", summary.contains(""","Pass")</f>"""))
    }

    @Test
    fun `the summary carries the printed banner and the cream analysis blocks`() {
        val all = entries(workbook(schoolName = "Sunrise Public School", sessionLabel = "2026-27"))
        val summary = all["xl/worksheets/sheet2.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()
        val styles = all["xl/styles.xml"].orEmpty()

        assertTrue("the banner repeats on the summary", summary.contains("""ref="A1:Q1""""))
        assertTrue("the cream title", shared.contains("RESULT- ANALYSIS"))
        assertTrue("the title block spans the metrics", summary.contains("""ref="A5:M5""""))
        assertTrue("the class-wise title sits to its right", summary.contains("""ref="N5:Q5""""))
        assertTrue("cream fill", styles.contains("FFFFF2CC"))
        assertTrue("band blue", styles.contains("FFDDEBF7"))
        assertTrue("band grey", styles.contains("FFDBDBDB"))
        assertTrue("band green", styles.contains("FFC6E0B4"))
        assertTrue("band amber", styles.contains("FFFFE699"))
        assertTrue("the student-count column is yellow", styles.contains("FFFFFF00"))
    }

    @Test
    fun `the workbook has a third Subject Teacher sheet after the summary`() {
        val all = entries(workbook())
        val book = all["xl/workbook.xml"].orEmpty()

        assertTrue("subject teacher tab", book.contains("""name="Subject Teacher""""))
        assertTrue(
            "the teacher sheet follows the summary",
            book.indexOf("""name="Summary"""") < book.indexOf("""name="Subject Teacher""""),
        )
        assertTrue("its own worksheet part", all.containsKey("xl/worksheets/sheet3.xml"))
    }

    @Test
    fun `the Subject Teacher sheet carries the metrics, five bands and the teacher fields`() {
        val withTeachers = listOf(
            subjects[0].copy(
                title = "SMT.",
                teacherName = "Asha Verma",
                teacherDesignation = "TGT-Math",
                employeeType = "Regular",
            ),
            subjects[1].copy(
                teacherName = "Ravi Kumar",
                teacherDesignation = "TGT-Science",
                employeeType = "Contract",
            ),
        )
        val all = entries(ExamExcelWriter.buildWorkbook(withTeachers, listOf(result)))
        val sheet = all["xl/worksheets/sheet3.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()

        assertTrue("the banner repeats", sheet.contains("""ref="A1:Q1""""))
        assertTrue("the cream title is shared", shared.contains("RESULT- ANALYSIS"))
        assertTrue("all eight metrics", shared.contains("TOTAL Strength") && shared.contains("Subject Average"))
        assertTrue("the five page-three bands", shared.contains("33-&lt;60%") && shared.contains("90% &amp; Above"))
        assertTrue(
            "the teacher columns",
            shared.contains("Name of Subject Teacher") && shared.contains("Designation") &&
                shared.contains("Employee Type"),
        )
        assertTrue("the title is printed with the teacher", shared.contains("SMT. Asha Verma"))
        assertTrue("designation", shared.contains("TGT-Science"))
        assertTrue("employee type", shared.contains("Contract"))

        assertTrue("absent has its own count", sheet.contains("""<f>COUNTIF('Results'!${'$'}D$7:${'$'}D$7,"ABT")</f>"""))
        assertTrue("failed is appeared minus passed", sheet.contains("<f>C7-E7</f>"))
        assertTrue("total is passed plus failed", sheet.contains("<f>E7+F7</f>"))
        assertTrue("overall appeared is the max", sheet.contains("<f>MAX(C7:C8)</f>"))
        assertTrue("overall absent is the entry sum", sheet.contains("<f>SUM(D7:D8)</f>"))
        assertTrue(
            "the class-wise block points at the overall row",
            sheet.contains("<f>B9</f>") && sheet.contains("<f>I9</f>"),
        )
        assertTrue("the block is below the table", sheet.contains("""<dimension ref="A1:Q12" />"""))
    }

    private val RESULT_FORMULA =
        """<f>IF(AND(COUNT(D7:E7)=0,COUNTIF(D7:E7,"ABT")=0),"",IF(AND(OR(ISBLANK(D7),N(D7)&gt;=35),OR(ISBLANK(E7),N(E7)&gt;=18)),"Pass","Fail"))</f>"""

    private fun workbook(
        result: StudentResult = this.result,
        className: String = "5-A",
        schoolName: String = "",
        sessionLabel: String = "",
        teacherName: String = "",
        teacherDesignation: String = "",
    ): ByteArray = ExamExcelWriter.buildWorkbook(
        subjects = subjects,
        results = listOf(result),
        examName = "Mid Term",
        className = className,
        schoolName = schoolName,
        sessionLabel = sessionLabel,
        teacherName = teacherName,
        teacherDesignation = teacherDesignation,
    )

    @Test
    fun `the banner prints the printed-sheet colours and the school stamp`() {
        val all = entries(
            workbook(
                schoolName = "Sunrise Public School",
                sessionLabel = "2026-27",
                teacherName = "Meera Nair",
                teacherDesignation = "PGT",
            ),
        )
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()
        val shared = all["xl/sharedStrings.xml"].orEmpty()
        val styles = all["xl/styles.xml"].orEmpty()

        assertTrue("school name on row one", shared.contains("Sunrise Public School"))
        assertTrue("exam and session in the second banner row", shared.contains("2026-27"))
        assertTrue("class stamp", shared.contains("CLASS-5-A"))
        assertTrue("sheet title", shared.contains("CONSOLIDATED MARKS - STATEMENT"))
        assertTrue(
            "class teacher with designation",
            shared.contains("Name of Class Teacher:- Meera Nair (PGT)"),
        )
        assertTrue("school row on banner blue", styles.contains("FFD9E1F2"))
        assertTrue("exam row on yellow", styles.contains("FFFF00"))
        assertTrue("subject header on cyan", styles.contains("FF66FFFF"))
        assertTrue("banner spans the table", sheet.contains("""ref="A1:I1""""))
        assertTrue("title spans the table", sheet.contains("""ref="A3:I3""""))
    }

    @Test
    fun `the total max is stated once in the header, not in every cell`() {
        val all = entries(workbook())
        val shared = all["xl/sharedStrings.xml"].orEmpty()

        assertTrue(
            "the header carries the exam maximum",
            shared.contains("Total Obtained (150)"),
        )
        assertFalse(
            "the per-cell bracket format is gone",
            all["xl/styles.xml"].orEmpty().contains("""&quot;(150)&quot;"""),
        )
        assertTrue(
            "the total cell stays a real number",
            (numberFormatId(all, "xl/worksheets/sheet1.xml", "F7")?.toIntOrNull() ?: 0) < 164,
        )
    }

    @Test
    fun `rank uses the whole student range and follows ties`() {
        val other = result.copy(
            student = result.student.copy(id = "s2", name = "Ravi", rollNumber = "2"),
            totalObtained = 100.0,
        )
        val all = entries(
            ExamExcelWriter.buildWorkbook(subjects, listOf(result, other)),
        )
        val sheet = all["xl/worksheets/sheet1.xml"].orEmpty()

        assertTrue(
            "the range is absolute so every row ranks against every student",
            sheet.contains("""<f>IF(F7="","",RANK(F7,${'$'}F${'$'}7:${'$'}F${'$'}8))</f>"""),
        )
        assertTrue(
            "the second student ranks in her own row",
            sheet.contains("""<f>IF(F8="","",RANK(F8,${'$'}F${'$'}7:${'$'}F${'$'}8))</f>"""),
        )
    }

    /** The `numFmtId` behind a cell's style, read from the written zip. */
    private fun numberFormatId(entries: Map<String, String>, sheetPart: String, cell: String): String? {
        val sheet = entries[sheetPart].orEmpty()
        val cellTag = Regex("""<c r="$cell"[^>]*>""").find(sheet)?.value ?: return null
        val styleIndex = Regex("""\bs="(\d+)"""")
            .find(cellTag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cellXfs = Regex("""<cellXfs[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL)
            .find(entries["xl/styles.xml"].orEmpty())?.groupValues?.get(1) ?: return null
        val styles = Regex("""<xf\b[^>]*>""").findAll(cellXfs).map { it.value }.toList()
        val xf = styles.getOrNull(styleIndex) ?: return null
        return Regex("""numFmtId="(\d+)"""").find(xf)?.groupValues?.get(1)
    }

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
