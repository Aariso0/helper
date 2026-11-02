package com.example.helper.roster

import com.example.helper.roster.ui.import.ImportFileException
import com.example.helper.roster.ui.import.ImportParsers
import com.example.helper.roster.ui.import.ImportRange
import com.gyanoba.kexcel.Excel
import com.gyanoba.kexcel.sheet.TextCellValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A class list usually lives in one block of a bigger workbook, so the import
 * must read exactly the tab and the row window the teacher points at — and must
 * keep reading past an empty row when they leave the end open.
 */
class ImportRangeTest {

    /** Rows as a spreadsheet shows them: 1 title, 2 blank, 3 header, 4-6 list, 7-8 blank, 9 late. */
    private fun workbook(): ByteArray {
        val excel = Excel.createExcel()
        val sheet = excel[excel.getDefaultSheet() ?: "Sheet1"]
        sheet.appendRow(listOf(TextCellValue("CLASS 5-B")))
        // A truly empty but present row, the way Excel keeps it in the grid.
        sheet.appendRow(listOf(TextCellValue(""), TextCellValue("")))
        sheet.appendRow(listOf(TextCellValue("Roll No"), TextCellValue("Name")))
        sheet.appendRow(listOf(TextCellValue("1"), TextCellValue("Asha")))
        sheet.appendRow(listOf(TextCellValue("2"), TextCellValue("Ravi")))
        sheet.appendRow(listOf(TextCellValue("3"), TextCellValue("Meera")))
        sheet.appendRow(listOf(TextCellValue(""), TextCellValue("")))
        sheet.appendRow(listOf(TextCellValue(""), TextCellValue("")))
        sheet.appendRow(listOf(TextCellValue("9"), TextCellValue("Latecomer")))

        val other = excel["Other"]
        other.appendRow(listOf(TextCellValue("Roll"), TextCellValue("Name")))
        other.appendRow(listOf(TextCellValue("7"), TextCellValue("Chand")))
        return excel.encode() ?: error("encode failed")
    }

    private fun parse(range: ImportRange) =
        ImportParsers.parse("class.xlsx", workbook(), range)

    @Test
    fun `a row window starts at the header and takes only those rows`() {
        val table = parse(ImportRange(startRow = 3, endRow = 5))

        assertEquals(listOf("Roll No", "Name"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("1", "Asha"), table.rows[0])
        assertEquals(listOf("2", "Ravi"), table.rows[1])
    }

    @Test
    fun `a typed last row is taken literally`() {
        // Rows 7-8 are blank and row 9 has a student: "to row 8" must stop at
        // 8 instead of quietly growing past what the teacher asked for.
        val table = parse(ImportRange(startRow = 3, endRow = 8))

        assertEquals(3, table.rows.size)
        assertEquals("Meera", table.rows.last()[1])
    }

    @Test
    fun `no end row keeps reading past empty rows to the last filled one`() {
        // "from row 3 till empty cells": rows 7-8 are holes, row 9 is not.
        val table = parse(ImportRange(startRow = 3))

        assertEquals(4, table.rows.size)
        assertEquals("Latecomer", table.rows.last()[1])
    }

    @Test
    fun `a last row past the sheet is clamped instead of crashing`() {
        val table = parse(ImportRange(startRow = 3, endRow = 999))

        assertEquals(4, table.rows.size)
    }

    @Test
    fun `a start row past the sheet explains itself`() {
        val error = runCatching { parse(ImportRange(startRow = 90)) }.exceptionOrNull()

        assertTrue(error is ImportFileException)
        assertTrue(error!!.message.orEmpty().contains("past the end"))
    }

    @Test
    fun `an empty window explains itself`() {
        val error = runCatching { parse(ImportRange(startRow = 7, endRow = 8)) }
            .exceptionOrNull()

        assertTrue(error is ImportFileException)
        assertTrue(error!!.message.orEmpty().contains("empty"))
    }

    @Test
    fun `the chosen sheet is the one that is read`() {
        val table = parse(ImportRange(sheetName = "Other"))

        assertEquals(listOf("Roll", "Name"), table.headers)
        assertEquals(listOf(listOf("7", "Chand")), table.rows)
    }

    @Test
    fun `the workbook reports its sheets for the picker`() {
        val parsed = ImportParsers.parseWorkbook("class.xlsx", workbook())

        assertTrue(parsed.sheets.containsAll(listOf("Sheet1", "Other")))
        assertEquals("Sheet1", parsed.sheetName)
        assertEquals(5, parsed.table.rows.size)
    }

    @Test
    fun `a CSV honours the same row window`() {
        val csv = "Class\n\nRoll,Name\n1,Asha\n2,Ravi\n\n\n9,Latecomer\n".toByteArray()

        val table = ImportParsers.parse("class.csv", csv, ImportRange(startRow = 3))

        assertEquals(listOf("Roll", "Name"), table.headers)
        assertEquals(3, table.rows.size)
        assertEquals("Latecomer", table.rows.last()[1])
    }
}
