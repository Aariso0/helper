package com.example.helper.exportbackup

import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.exportbackup.summarize.buildMonthlySummary
import com.example.helper.exportbackup.writers.SummaryExcelWriter
import com.example.helper.exportbackup.writers.XlsxDimension
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * kexcel 0.1.2 hard-codes `<dimension ref="A1"/>` on write. These tests pin
 * the correction: the declared range must be the range the sheet actually
 * holds, and the rewrite must leave every other zip part untouched.
 */
class XlsxDimensionTest {

    @Test
    fun `range is read from the cells in the sheet`() {
        val sheet = """
            <worksheet><dimension ref="A1" />
             <sheetData>
              <row r="1"><c r="A1" s="1" t="s"><v>0</v></c></row>
              <row r="6"><c r="A6"/><c r="I6" s="2"><v>4</v></c></row>
              <row r="9"><c r="C9"/></row>
             </sheetData>
            </worksheet>
        """.trimIndent()
        assertEquals("A1:I9", XlsxDimension.dimensionRef(sheet))
    }

    @Test
    fun `an empty sheet keeps the single cell range`() {
        assertEquals("A1", XlsxDimension.dimensionRef("<worksheet><dimension ref=\"A1\" />"))
    }

    @Test
    fun `patch corrects the dimension and keeps every other part`() {
        val sheet = "xl/worksheets/sheet1.xml"
        val workbook = zipOf(
            sheet to "<worksheet><dimension ref=\"A1\" /><sheetData>" +
                "<row r=\"1\"><c r=\"A1\"/></row><row r=\"4\"><c r=\"D4\"/></row>" +
                "</sheetData></worksheet>",
            "xl/workbook.xml" to "<workbook/>",
        )

        val patched = unzip(XlsxDimension.patch(workbook))

        assertTrue(patched.getValue(sheet).contains("""<dimension ref="A1:D4" />"""))
        assertEquals("<workbook/>", patched.getValue("xl/workbook.xml"))
    }

    @Test
    fun `a real export declares the range it contains`() {
        val class5A = SchoolClass(id = "c1", name = "5-A", createdAt = 0, updatedAt = 0)
        val students = listOf(
            Student(id = "s1", classId = "c1", name = "Asha", rollNumber = "1", createdAt = 0, updatedAt = 0),
            Student(id = "s2", classId = "c1", name = "Bina", rollNumber = "2", createdAt = 0, updatedAt = 0),
        )
        val records = students.map {
            AttendanceRecord(
                id = "r-${it.id}",
                classId = "c1",
                studentId = it.id,
                dateEpochDay = 100,
                status = AttendanceStatus.PRESENT,
                createdAt = 0,
                updatedAt = 0,
            )
        }
        val summary = buildMonthlySummary(
            year = 2026,
            month = 9,
            classes = listOf(class5A),
            students = students,
            records = records,
            selectedClassId = null,
        )

        val out = ByteArrayOutputStream()
        SummaryExcelWriter.write(summary, "September 2026", "All classes", out)
        val sheetXml = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")

        val declared = Regex("""<dimension ref="([^"]+)"""").find(sheetXml)?.groupValues?.get(1)
        assertEquals(XlsxDimension.dimensionRef(sheetXml), declared)
        assertNotEquals("A1", declared)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes().decodeToString()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
