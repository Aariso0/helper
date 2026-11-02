package com.example.helper.roster.ui.import

import com.gyanoba.kexcel.Excel
import java.io.StringReader
import org.apache.commons.csv.CSVFormat

/**
 * File → `ParsedTable` for the import preview (roster card: file pick →
 * parse → column-mapping preview → commit). Every failure path throws
 * [ImportFileException] with teacher-readable copy — parsers never crash
 * the app (card build note).
 */
data class ParsedTable(
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    val columnCount: Int get() = headers.size

    fun cellAt(row: List<String>, column: Int): String =
        if (column in row.indices) row[column].trim() else ""

    fun columnLabel(index: Int): String {
        val header = headers.getOrNull(index)?.trim().orEmpty()
        return if (header.isEmpty()) "Column ${index + 1}" else header
    }
}

/**
 * Where to read inside the file. Rows are 1-based and inclusive, matching the
 * row numbers Excel shows: a teacher who means "row 5 to 25" types 5 and 25.
 *
 * A blank [endRow] — or an end row that turned out empty — reads to the last
 * filled row, so a list that grew past the number they typed still imports
 * whole instead of stopping at a hole.
 */
data class ImportRange(
    val sheetName: String? = null,
    val startRow: Int? = null,
    val endRow: Int? = null,
)

/** The parsed table plus the sheets the file offered, for the picker. */
data class ParsedWorkbook(
    val sheets: List<String>,
    val sheetName: String?,
    val table: ParsedTable,
)

/** A parse failure the UI can show verbatim. */
class ImportFileException(message: String) : Exception(message)

object ImportParsers {
    private const val MAX_ROWS = 5_000

    fun parse(
        fileName: String,
        bytes: ByteArray,
        range: ImportRange = ImportRange(),
    ): ParsedTable = parseWorkbook(fileName, bytes, range).table

    /**
     * Parses [bytes] and reports which sheets it has, so the mapping screen can
     * offer a sheet picker without decoding the workbook twice.
     */
    fun parseWorkbook(
        fileName: String,
        bytes: ByteArray,
        range: ImportRange = ImportRange(),
    ): ParsedWorkbook {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".xlsx") -> parseXlsx(bytes, range)
            lower.endsWith(".xls") -> throw ImportFileException(
                "This looks like an older .xls file. Open it in Excel or Google " +
                    "Sheets and save it as .xlsx or CSV, then try again."
            )
            lower.endsWith(".csv") || lower.endsWith(".txt") ->
                ParsedWorkbook(
                    sheets = emptyList(),
                    sheetName = null,
                    table = parseCsv(decodeText(bytes), range),
                )
            else -> throw ImportFileException(
                "“$fileName” isn’t a file type we can read. Export the list " +
                    "as .csv or .xlsx and try again."
            )
        }
    }

    /** Strips the Excel UTF-8 BOM, with an ANSI fallback for legacy exports. */
    fun decodeText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
        // Excel writes ANSI CSVs in some locales — decode sanity check.
        return if (utf8.contains('\uFFFD')) {
            runCatching { bytes.toString(Charsets.ISO_8859_1).removePrefix("﻿") }
                .getOrDefault(utf8)
        } else {
            utf8
        }
    }

    fun parseCsv(text: String, range: ImportRange = ImportRange()): ParsedTable {
        val records = runCatching {
            CSVFormat.DEFAULT.builder()
                // Blank lines are kept so the row numbers the teacher reads in
                // Excel still point at the same rows; tableFromRows drops them.
                .setIgnoreEmptyLines(false)
                .get() // build() is deprecated since csv 1.13
                .parse(StringReader(text))
                .use { parser -> parser.map { it.toList() } }
        }.getOrElse {
            throw ImportFileException(
                "We couldn’t read that CSV. If the file opens in Excel, " +
                    "save it as “CSV UTF-8” and try again."
            )
        }
        return tableFromRows(rowsInRange(records, range, "that file"))
    }

    fun parseXlsx(bytes: ByteArray, range: ImportRange = ImportRange()): ParsedWorkbook {
        val excel = runCatching { Excel.decodeBytes(bytes) }.getOrElse { e ->
            throw ImportFileException(
                "We couldn’t read that spreadsheet (${e.message ?: "unsupported file"}). " +
                    "If it’s an old .xls file, save it as .xlsx or CSV and try again."
            )
        }
        val sheets = excel.getSheets().keys.toList()
        val sheetName = range.sheetName?.takeIf { it in sheets }
            ?: excel.getDefaultSheet()
            ?: sheets.firstOrNull()
            ?: throw ImportFileException("That spreadsheet has no sheets in it.")
        val sheet = excel[sheetName]
        if (sheet.maxRows > MAX_ROWS) {
            throw ImportFileException(
                "That sheet has more than $MAX_ROWS rows. Import one class’s " +
                    "list at a time."
            )
        }
        val rows = (0 until sheet.maxRows).map { rowIndex ->
            val cells = sheet.row(rowIndex)
            (0 until sheet.maxColumns).map { col ->
                cells.getOrNull(col)?.value?.toString()?.trim().orEmpty()
            }
        }
        return ParsedWorkbook(
            sheets = sheets,
            sheetName = sheetName,
            table = tableFromRows(rowsInRange(rows, range, "“$sheetName”")),
        )
    }

    /**
     * Cuts the requested 1-based row window out of [rows].
     *
     * An end row the teacher typed is honoured literally (clamped to the
     * sheet). Leaving it blank means "down to the last filled row", which is
     * how a list that grew past a remembered row count still imports whole.
     */
    private fun rowsInRange(
        rows: List<List<String>>,
        range: ImportRange,
        source: String,
    ): List<List<String>> {
        if (rows.isEmpty()) throw ImportFileException("We couldn’t find any rows in $source.")
        val start = (range.startRow ?: 1).coerceAtLeast(1)
        if (start > rows.size) {
            throw ImportFileException(
                "Row $start is past the end of $source, which has ${rows.size} rows."
            )
        }
        val end = range.endRow
            ?.coerceIn(start, rows.size)
            ?: lastFilledRow(rows, start, rows.size)
            ?: throw ImportFileException("Rows from $start on are empty in $source — nothing to import.")
        if (rows.subList(start - 1, end).all { row -> row.all { it.isBlank() } }) {
            throw ImportFileException(
                "Rows $start to $end of $source are empty — nothing to import."
            )
        }
        return rows.subList(start - 1, end)
    }

    /** 1-based index of the last row in `start..end` with any content. */
    private fun lastFilledRow(rows: List<List<String>>, start: Int, end: Int): Int? =
        (end downTo start).firstOrNull { index -> rows[index - 1].any { it.isNotBlank() } }

    /** Row 0 becomes the header; blank rows are dropped; rows pad to width. */
    private fun tableFromRows(rows: List<List<String>>): ParsedTable {
        val nonBlank = rows.filter { row -> row.any { it.isNotBlank() } }
        if (nonBlank.isEmpty()) {
            throw ImportFileException("We couldn’t find any rows in that file.")
        }
        val width = nonBlank.maxOf { it.size }
        val headerRow = nonBlank.first()
        val headers = List(width) { i ->
            headerRow.getOrNull(i)?.trim().orEmpty().ifEmpty { "Column ${i + 1}" }
        }
        val data = nonBlank.drop(1).map { row ->
            List(width) { i -> row.getOrNull(i)?.trim().orEmpty() }
        }
        if (data.isEmpty()) {
            throw ImportFileException(
                "That file only has a header row — no students in it."
            )
        }
        return ParsedTable(headers = headers, rows = data)
    }
}
