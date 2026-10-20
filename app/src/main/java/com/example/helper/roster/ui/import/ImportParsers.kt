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

/** A parse failure the UI can show verbatim. */
class ImportFileException(message: String) : Exception(message)

object ImportParsers {
    private const val MAX_ROWS = 5_000

    fun parse(fileName: String, bytes: ByteArray): ParsedTable {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".xlsx") -> parseXlsx(bytes)
            lower.endsWith(".xls") -> throw ImportFileException(
                "This looks like an older .xls file. Open it in Excel or Google " +
                    "Sheets and save it as .xlsx or CSV, then try again."
            )
            lower.endsWith(".csv") || lower.endsWith(".txt") -> parseCsv(decodeText(bytes))
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

    fun parseCsv(text: String): ParsedTable {
        val records = runCatching {
            CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .get() // build() is deprecated since csv 1.13
                .parse(StringReader(text))
                .use { parser -> parser.map { it.toList() } }
        }.getOrElse {
            throw ImportFileException(
                "We couldn’t read that CSV. If the file opens in Excel, " +
                    "save it as “CSV UTF-8” and try again."
            )
        }
        return tableFromRows(records)
    }

    fun parseXlsx(bytes: ByteArray): ParsedTable {
        val excel = runCatching { Excel.decodeBytes(bytes) }.getOrElse { e ->
            throw ImportFileException(
                "We couldn’t read that spreadsheet (${e.message ?: "unsupported file"}). " +
                    "If it’s an old .xls file, save it as .xlsx or CSV and try again."
            )
        }
        val sheetName = excel.getDefaultSheet()
            ?: excel.getSheets().keys.firstOrNull()
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
        return tableFromRows(rows)
    }

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
