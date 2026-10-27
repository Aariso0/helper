package com.example.helper.examsresults.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * kexcel 0.1.2 has no conditional-formatting API, so the Result column's
 * green/red encoding is added as a post-process: two `dxf` fills in
 * `styles.xml` plus a `cellIs` rule pair for the RESULT range. The rule reads
 * the PASS/FAIL the sheet's own `IF(...)` formula produces, so every student
 * row follows the same rule and the colour follows a recalculated result.
 */
internal object ResultColumnColors {

    private const val SHEET_PART = "xl/worksheets/sheet1.xml"
    private const val STYLES_PART = "xl/styles.xml"

    private val DXFS = Regex("""<dxfs[^>]*>.*?</dxfs>|<dxfs[^>]*/>""", RegexOption.DOT_MATCHES_ALL)

    /** [sqref] is the A1 range to colour, e.g. `H2:H31`. */
    fun patch(workbook: ByteArray, sqref: String, passArgb: String, failArgb: String): ByteArray {
        if (sqref.isBlank()) return workbook
        val entries = readEntries(workbook) ?: return workbook
        val styles = entries[STYLES_PART]?.decodeToString() ?: return workbook
        val sheet = entries[SHEET_PART]?.decodeToString() ?: return workbook
        if (!sheet.contains("</sheetData>")) return workbook

        val dxfs = buildString {
            append("""<dxfs count="2">""")
            append(dxf(passArgb))
            append(dxf(failArgb))
            append("</dxfs>")
        }
        val patchedStyles = if (DXFS.containsMatchIn(styles)) {
            DXFS.replace(styles, dxfs)
        } else {
            styles.replace("</styleSheet>", "$dxfs</styleSheet>")
        }

        val patchedSheet = sheet.replace("</sheetData>", "</sheetData>${rules(sqref)}")

        entries[STYLES_PART] = patchedStyles.encodeToByteArray()
        entries[SHEET_PART] = patchedSheet.encodeToByteArray()
        return writeEntries(entries)
    }

    private fun dxf(argb: String): String =
        """<dxf><fill><patternFill><bgColor rgb="$argb" /></patternFill></fill></dxf>"""

    private fun rules(sqref: String): String = buildString {
        append("""<conditionalFormatting sqref="$sqref">""")
        append("""<cfRule type="cellIs" dxfId="0" priority="1" operator="equal"><formula>"PASS"</formula></cfRule>""")
        append("""<cfRule type="cellIs" dxfId="1" priority="2" operator="equal"><formula>"FAIL"</formula></cfRule>""")
        append("</conditionalFormatting>")
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
