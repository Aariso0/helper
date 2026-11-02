package com.example.helper.examsresults.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The three things kexcel 0.1.2 cannot express as cell styles, applied as one
 * post-process over the encoded workbook:
 *
 * - `applyBorder="1"` on every cell xf that points at a real border. The
 *   library writes the border and the `borderId`, but leaves the flag off, and
 *   a viewer may then drop the thin print lines.
 * - `fullCalcOnLoad="1"` in `calcPr`, so Excel evaluates the Result and Total
 *   formulas as soon as the sheet opens instead of showing the empty cached
 *   value the library leaves behind.
 * - One `dxf` plus a `cellIs` rule for the Result range: "Fail" is bold
 *   italic, "Pass" keeps the plain font, and no fill is written, so the whole
 *   sheet stays white.
 */
internal object WorkbookDecoration {

    private const val SHEET_PART = "xl/worksheets/sheet1.xml"
    private const val STYLES_PART = "xl/styles.xml"
    private const val WORKBOOK_PART = "xl/workbook.xml"

    private val DXFS = Regex("""<dxfs[^>]*>.*?</dxfs>|<dxfs[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
    private val CELL_XFS = Regex("""<cellXfs[^>]*>.*?</cellXfs>""", RegexOption.DOT_MATCHES_ALL)
    private val XF = Regex("""<xf\b[^>]*>""")
    private val BORDER_ID = Regex("""borderId="(\d+)"""")
    private val CALC_PR = Regex("""<calcPr\b[^>]*/>""")

    /**
     * [failRuleSqref] is the A1 range that gets the bold-italic rule, e.g.
     * `H2:H31`; `null` when the layout has no Result column.
     */
    fun patch(workbook: ByteArray, failRuleSqref: String?): ByteArray {
        val entries = readEntries(workbook) ?: return workbook
        val styles = entries[STYLES_PART]?.decodeToString() ?: return workbook
        val sheet = entries[SHEET_PART]?.decodeToString() ?: return workbook

        var patchedStyles = withBorderFlags(styles)
        if (!failRuleSqref.isNullOrBlank() && sheet.contains("</sheetData>")) {
            patchedStyles = withFailRule(patchedStyles)
        }

        entries[STYLES_PART] = patchedStyles.encodeToByteArray()
        entries[WORKBOOK_PART]?.let { part ->
            entries[WORKBOOK_PART] = withRecalcOnLoad(part.decodeToString()).encodeToByteArray()
        }
        if (!failRuleSqref.isNullOrBlank()) {
            val patchedSheet = sheet.replace("</sheetData>", "</sheetData>${rules(failRuleSqref)}")
            entries[SHEET_PART] = patchedSheet.encodeToByteArray()
        }
        return writeEntries(entries)
    }

    private fun withBorderFlags(styles: String): String =
        CELL_XFS.replace(styles) { block ->
            XF.replace(block.value) { xf -> withApplyBorder(xf.value) }
        }

    private fun withApplyBorder(xf: String): String {
        val borderId = BORDER_ID.find(xf)?.groupValues?.get(1)?.toIntOrNull() ?: return xf
        if (borderId == 0 || xf.contains("applyBorder=")) return xf
        val closing = xf.lastIndexOf('>')
        if (closing < 0) return xf
        val insertAt = if (closing > 0 && xf[closing - 1] == '/') closing - 1 else closing
        return xf.substring(0, insertAt) + """ applyBorder="1"""" + xf.substring(insertAt)
    }

    private fun withRecalcOnLoad(workbookXml: String): String {
        if (workbookXml.contains("fullCalcOnLoad")) return workbookXml
        return if (CALC_PR.containsMatchIn(workbookXml)) {
            CALC_PR.replace(workbookXml) { match ->
                match.value.dropLast(2).trimEnd() + """ fullCalcOnLoad="1" />"""
            }
        } else {
            workbookXml.replace("</workbook>", """<calcPr fullCalcOnLoad="1" /></workbook>""")
        }
    }

    private fun withFailRule(styles: String): String {
        val dxfs = """<dxfs count="1"><dxf><font><b /><i /></font></dxf></dxfs>"""
        return if (DXFS.containsMatchIn(styles)) {
            DXFS.replace(styles, dxfs)
        } else {
            styles.replace("</styleSheet>", "$dxfs</styleSheet>")
        }
    }

    private fun rules(sqref: String): String = buildString {
        append("""<conditionalFormatting sqref="$sqref">""")
        append("""<cfRule type="cellIs" dxfId="0" priority="1" operator="equal"><formula>"Fail"</formula></cfRule>""")
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
