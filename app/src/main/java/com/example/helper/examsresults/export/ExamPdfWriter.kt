package com.example.helper.examsresults.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.examsresults.util.StudentResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prints the results sheet as a PDF: landscape A4, the header repeated on
 * every page, a thin border around every cell, and "Fail" in bold italic — the
 * same reading a printed Excel export gives, without needing Excel.
 *
 * Uses the platform's `PdfDocument` like the announcement PDF store, so no
 * third-party renderer enters the build. Dependent columns the device cannot
 * evaluate (custom Excel formulas) are left off, see [ExamPdfTableBuilder].
 *
 * Files land in `<cacheDir>/exams/`, served by the app-shell FileProvider's
 * `exams_cache` path, and are shared through `File.asShareableUri`.
 */
object ExamPdfWriter {

    // A4 in PostScript points (1/72"), the unit PdfDocument uses.
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 28f
    private const val TITLE_BLOCK = 46f
    private const val HEADER_HEIGHT = 22f
    private const val ROW_HEIGHT = 20f
    private const val FOOTER_HEIGHT = 18f
    private const val CELL_PADDING = 4f
    private const val MIN_DATA_WIDTH = 42f

    private val TITLE_PAINT = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 15f
        color = Color.rgb(32, 33, 36)
    }
    private val SUBTITLE_PAINT = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = 9f
        color = Color.rgb(95, 99, 104)
    }
    private val HEADER_PAINT = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 9f
        color = Color.rgb(32, 33, 36)
    }
    private val CELL_PAINT = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textSize = 9f
        color = Color.rgb(32, 33, 36)
    }
    private val FAIL_PAINT = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        textSize = 9f
        color = Color.rgb(32, 33, 36)
    }
    private val BORDER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.rgb(154, 160, 166)
    }

    /** Renders the sheet and returns the written file; throws on a write error. */
    fun write(
        context: Context,
        examName: String,
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    ): File {
        val table = ExamPdfTableBuilder.build(subjects, results, columns)
        val dir = File(context.cacheDir, "exams").apply { mkdirs() }
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        try {
            render(document, table, examName)
            FileOutputStream(file).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }
        return file
    }

    private fun render(document: PdfDocument, table: ExamPdfTable, examName: String) {
        val widths = columnWidths(table.headers.size)
        val bodyTop = MARGIN + TITLE_BLOCK + HEADER_HEIGHT
        val bodyBottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        val rowsPerPage = ((bodyBottom - bodyTop) / ROW_HEIGHT).toInt().coerceAtLeast(1)
        val pages = if (table.rows.isEmpty()) listOf(emptyList()) else table.rows.chunked(rowsPerPage)

        pages.forEachIndexed { index, rows ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create(),
            )
            drawPage(page.canvas, table, rows, widths, examName, index + 1, pages.size)
            document.finishPage(page)
        }
    }

    private fun drawPage(
        canvas: Canvas,
        table: ExamPdfTable,
        rows: List<List<String>>,
        widths: FloatArray,
        examName: String,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        val title = TextUtils.ellipsize(examName, TITLE_PAINT, right - left, TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, left, MARGIN + 14f, TITLE_PAINT)
        val subtitle = "Results · ${table.rows.size} student${if (table.rows.size == 1) "" else "s"}" +
            " · ${today()}"
        val subtitleText = TextUtils.ellipsize(subtitle, SUBTITLE_PAINT, right - left, TextUtils.TruncateAt.END)
        canvas.drawText(subtitleText, 0, subtitleText.length, left, MARGIN + 32f, SUBTITLE_PAINT)

        var top = MARGIN + TITLE_BLOCK
        val headerBottom = top + HEADER_HEIGHT
        drawRow(canvas, table.headers, widths, top, headerBottom, header = true)

        val footerText = "Page $pageNumber of $pageCount"
        val footerWidth = SUBTITLE_PAINT.measureText(footerText)
        canvas.drawText(
            footerText,
            right - footerWidth,
            PAGE_HEIGHT - MARGIN + 6f,
            SUBTITLE_PAINT,
        )

        if (rows.isEmpty()) {
            val message = "No students with marks yet."
            canvas.drawText(message, left + CELL_PADDING, headerBottom + ROW_HEIGHT / 2f, SUBTITLE_PAINT)
            return
        }

        top = headerBottom
        rows.forEachIndexed { rowIndex, row ->
            val bottom = top + ROW_HEIGHT
            drawRow(canvas, row, widths, top, bottom, header = false)
            top = bottom
            if (rowIndex == rows.lastIndex) {
                // close the table's bottom edge
                canvas.drawLine(left, bottom, right, bottom, BORDER_PAINT)
            }
        }
    }

    private fun drawRow(
        canvas: Canvas,
        cells: List<String>,
        widths: FloatArray,
        top: Float,
        bottom: Float,
        header: Boolean,
    ) {
        var left = MARGIN
        cells.forEachIndexed { index, raw ->
            val width = widths.getOrElse(index) { 0f }
            val right = left + width
            canvas.drawRect(left, top, right, bottom, BORDER_PAINT)

            val paint = when {
                header -> HEADER_PAINT
                isFail(raw) -> FAIL_PAINT
                else -> CELL_PAINT
            }
            val text = TextUtils.ellipsize(
                raw,
                paint,
                (width - 2 * CELL_PADDING).coerceAtLeast(1f),
                TextUtils.TruncateAt.END,
            )
            val baseline = top + (bottom - top - (paint.descent() + paint.ascent())) / 2f
            val x = if (header || !isNumeric(raw)) {
                left + CELL_PADDING
            } else {
                right - CELL_PADDING - paint.measureText(text, 0, text.length)
            }
            canvas.drawText(text, 0, text.length, x, baseline, paint)
            left = right
        }
    }

    /** Numbers and percentages line up on the right; words on the left. */
    private fun isNumeric(value: String): Boolean = NUMBER.matches(value)

    private fun isFail(value: String): Boolean = value.equals("Fail", ignoreCase = true)

    /**
     * S.No. is narrow, the name is wide, and the subject/computed columns share
     * what is left; if that leaves them too narrow the name gives ground first.
     * A sheet with many subjects is scaled down rather than overflowing.
     */
    private fun columnWidths(columnCount: Int): FloatArray {
        val usable = PAGE_WIDTH - 2 * MARGIN
        val fixedRoll = 46f
        val fixedSerial = 34f
        var nameWidth = 150f
        val dataCount = (columnCount - 3).coerceAtLeast(0)

        if (dataCount > 0) {
            val spare = usable - fixedSerial - fixedRoll - nameWidth
            if (spare / dataCount < MIN_DATA_WIDTH) {
                nameWidth = (usable - fixedSerial - fixedRoll - MIN_DATA_WIDTH * dataCount)
                    .coerceIn(90f, nameWidth)
            }
        }

        val dataWidth = if (dataCount == 0) {
            0f
        } else {
            ((usable - fixedSerial - fixedRoll - nameWidth) / dataCount).coerceAtLeast(20f)
        }

        val widths = FloatArray(columnCount) { index ->
            when (index) {
                0 -> fixedSerial
                1 -> fixedRoll
                2 -> nameWidth
                else -> dataWidth
            }
        }

        val total = widths.sum()
        if (total > usable && total > 0f) {
            val scale = usable / total
            for (index in widths.indices) widths[index] *= scale
        }
        return widths
    }

    private fun today(): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date())

    private val NUMBER = Regex("""-?\d+(\.\d+)?%?""")
}
