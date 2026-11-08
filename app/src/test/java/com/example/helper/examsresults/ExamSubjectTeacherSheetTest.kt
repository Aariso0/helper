package com.example.helper.examsresults

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.examsresults.export.ExamSubjectTeacherSheet
import com.example.helper.examsresults.export.SubjectTeacherRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page-3 sheet must be the same analysis as page 2 with the teacher fields
 * added, not a second hand-built table. These tests pin the formulas that are
 * easy to get subtly wrong: the absence count, the five band edges and the
 * overall row's verdict counts.
 */
class ExamSubjectTeacherSheetTest {

    private val subjects = listOf(
        ExamSubject(
            id = "hindi",
            examId = "e1",
            name = "Hindi",
            maxMarks = 40,
            passMarks = 14,
            title = "SMT.",
            teacherName = "Asha Verma",
            teacherDesignation = "TGT-Hindi",
            employeeType = "Regular",
        ),
        ExamSubject(id = "math", examId = "e1", name = "Math", maxMarks = 100, passMarks = 35),
    )

    private fun rows(
        percentageColumn: String? = "H",
        resultColumn: String? = "I",
    ): List<SubjectTeacherRow> = ExamSubjectTeacherSheet.rows(
        resultsSheet = "Results",
        firstRow = 7,
        lastRow = 16,
        nameColumn = "C",
        subjects = subjects,
        percentageColumn = percentageColumn,
        resultColumn = resultColumn,
    )

    @Test
    fun `one row per subject then the overall row`() {
        assertEquals(
            listOf("Hindi", "Math", "OVER ALL CLASS"),
            rows().map { it.label },
        )
    }

    @Test
    fun `strength appeared and absent are separate columns`() {
        val hindi = rows().first()

        assertEquals("COUNTA('Results'!${'$'}C$7:${'$'}C$16)", hindi.strength)
        assertEquals("""COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">=0")""", hindi.appeared)
        assertEquals("""COUNTIF('Results'!${'$'}D$7:${'$'}D$16,"ABT")""", hindi.absent)
    }

    @Test
    fun `passed uses the configured pass mark and failed is appeared minus passed`() {
        val hindi = rows().first()

        assertEquals("""COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">=14")""", hindi.passed)
        assertEquals("C7-E7", hindi.failed)
        assertEquals("E7+F7", hindi.total)
    }

    @Test
    fun `pass share and subject average are guarded fractions`() {
        val hindi = rows().first()

        assertEquals("IF(C7=0,0,E7/C7)", hindi.passPercent)
        assertEquals(
            "IF(C7=0,0,SUM('Results'!${'$'}D$7:${'$'}D$16)/C7/40)",
            hindi.average,
        )
    }

    @Test
    fun `page-three bands use the five ranges`() {
        val hindi = rows().first()

        assertEquals(
            listOf("Below 33%", "33-<60%", "60-<75%", "75-<90%", "90% & Above"),
            ExamSubjectTeacherSheet.BAND_LABELS,
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$7:${'$'}D$16,"<"&INT(40*33/100))""",
            hindi.bands[0],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">="&40*33/100)-COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">="&40*60/100)""",
            hindi.bands[1],
        )
        assertEquals(
            """COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">="&40*90/100)""",
            hindi.bands[4],
        )
    }

    @Test
    fun `the four teacher fields are carried from the subject`() {
        val hindi = rows().first()
        val math = rows()[1]

        assertEquals("SMT.", hindi.teacherTitle)
        assertEquals("Asha Verma", hindi.teacherName)
        assertEquals("SMT. Asha Verma", hindi.teacherDisplayName)
        assertEquals("TGT-Hindi", hindi.teacherDesignation)
        assertEquals("Regular", hindi.employeeType)
        assertEquals("", math.teacherTitle)
        assertEquals("", math.teacherName)
        assertEquals("", math.teacherDisplayName)
        assertEquals("", math.teacherDesignation)
        assertEquals("", math.employeeType)
    }

    @Test
    fun `overall appeared is the max, absent is the entry sum and verdicts come from Result`() {
        val overall = rows().last()

        assertEquals("COUNTA('Results'!${'$'}C$7:${'$'}C$16)", overall.strength)
        assertEquals("MAX(C7:C8)", overall.appeared)
        assertEquals("SUM(D7:D8)", overall.absent)
        assertEquals("""COUNTIF('Results'!${'$'}I$7:${'$'}I$16,"Pass")""", overall.passed)
        assertEquals("""COUNTIF('Results'!${'$'}I$7:${'$'}I$16,"Fail")""", overall.failed)
        assertEquals("E9+F9", overall.total)
        assertEquals("IF(C9=0,0,E9/C9)", overall.passPercent)
        assertEquals("IF(C9=0,0,SUM('Results'!${'$'}H$7:${'$'}H$16)/C9)", overall.average)
    }

    @Test
    fun `overall bands read the percentage shares`() {
        val bands = rows().last().bands

        assertEquals("""COUNTIF('Results'!${'$'}H$7:${'$'}H$16,"<"&33%)""", bands[0])
        assertEquals(
            """COUNTIF('Results'!${'$'}H$7:${'$'}H$16,">="&33%)-COUNTIF('Results'!${'$'}H$7:${'$'}H$16,">="&60%)""",
            bands[1],
        )
        assertEquals("""COUNTIF('Results'!${'$'}H$7:${'$'}H$16,">="&90%)""", bands[4])
    }

    @Test
    fun `the class-wise block points at the overall row`() {
        assertEquals(
            listOf("B9", "C9", "E9", "H9", "I9"),
            ExamSubjectTeacherSheet.classWiseValues(overallRow = 9),
        )
    }

    @Test
    fun `a missing result column blanks only the verdict figures`() {
        val all = rows(resultColumn = null)

        assertEquals("""COUNTIF('Results'!${'$'}D$7:${'$'}D$16,">=14")""", all[0].passed)
        assertNull(all.last().passed)
        assertNull(all.last().failed)
        assertNull(all.last().total)
        assertNull(all.last().passPercent)
        assertTrue("the marks bands still work", all.last().bands.all { it != null })
    }

    @Test
    fun `a missing percentage column blanks only the figures that read it`() {
        val all = rows(percentageColumn = null)

        assertEquals("""COUNTIF('Results'!${'$'}I$7:${'$'}I$16,"Pass")""", all.last().passed)
        assertNull(all.last().average)
        assertTrue(all.last().bands.all { it == null })
    }
}
