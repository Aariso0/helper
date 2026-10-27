package com.example.helper.exportbackup.writers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Fixes the one thing kexcel 0.1.2 gets wrong on write: the worksheet's
 * `<dimension>` element is hard-coded to `A1` and never updated as rows are
 * appended. Values are all present, but a viewer that trusts the declared
 * range opens on an "empty" grid.
 *
 * The workbook is a zip, so after [com.gyanoba.kexcel.Excel.encode] returns we
 * rewrite the one sheet part with the real extent, read back from the cells
 * that are actually there. Nothing else in the archive changes; this is a
 * post-process, not a fork of the library (the card's stack stays as chosen).
 */
internal object XlsxDimension {

    private const val SHEET_PART = "xl/worksheets/sheet1.xml"
    private const val DIMENSION_OPEN = "<dimension"

    private val DIMENSION_ELEMENT = Regex("""<dimension\s+ref="[^"]*"\s*/>""")
    private val ROW = Regex("""<row[^>]*\sr="(\d+)"""")
    private val CELL = Regex("""<c[^>]*\sr="([A-Z]+)(\d+)"""")

    /** Returns [workbook] with the first sheet's `<dimension>` corrected. */
    fun patch(workbook: ByteArray): ByteArray {
        val entries = readEntries(workbook) ?: return workbook
        val sheetXml = entries[SHEET_PART]?.decodeToString() ?: return workbook
        if (!sheetXml.contains(DIMENSION_OPEN)) return workbook
        val patched = DIMENSION_ELEMENT.replace(sheetXml, """<dimension ref="${dimensionRef(sheetXml)}" />""")
        if (patched == sheetXml) return workbook
        entries[SHEET_PART] = patched.encodeToByteArray()
        return writeEntries(entries)
    }

    /**
     * The real occupied range of a worksheet part, e.g. `A1:I51`. Reads the
     * cell references so the answer is what is in the file, not a count the
     * writer expected to end up with.
     */
    internal fun dimensionRef(sheetXml: String): String {
        val lastRow = ROW.findAll(sheetXml).maxOfOrNull { it.groupValues[1].toInt() } ?: 1
        val lastColumn = CELL.findAll(sheetXml).maxOfOrNull { columnIndex(it.groupValues[1]) } ?: 1
        return if (lastRow == 1 && lastColumn == 1) "A1" else "A1:${columnName(lastColumn)}$lastRow"
    }

    /** "A" → 1, "Z" → 26, "AA" → 27. */
    private fun columnIndex(letters: String): Int =
        letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) }

    private fun columnName(index: Int): String {
        var remaining = index
        val letters = StringBuilder()
        while (remaining > 0) {
            val digit = (remaining - 1) % 26
            letters.insert(0, 'A' + digit)
            remaining = (remaining - 1) / 26
        }
        return letters.toString()
    }

    private fun readEntries(zipBytes: ByteArray): LinkedHashMap<String, ByteArray>? {
        val entries = LinkedHashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.onFailure { return null }
        return entries.takeIf { it.isNotEmpty() }
    }

    private fun writeEntries(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
