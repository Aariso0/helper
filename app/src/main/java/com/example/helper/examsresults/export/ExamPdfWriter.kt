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

/**
 * Prints the results sheet as a PDF: portrait A4, the header repeated on every
 * page, a thin border around every cell, and "Fail" in bold italic — the same
 * reading a printed Excel export gives, without needing Excel.
 *
 * A class can have more subject columns than portrait A4 can show legibly, so
 * the table continues on later pages: S.No., Roll No. and Student Name are
 * repeated, then the next slice of subject columns. The computed Total / Total
 * Max / Percentage / Result columns keep their place at the end, so they land
 * on the final page. See [ExamPdfTable.sections].
 *
 * Uses the platform's `PdfDocument` like the announcement PDF store, so no
 * third-party renderer enters the build. Dependent columns the device cannot
 * evaluate (custom Excel formulas) are left off, see [ExamPdfTableBuilder].
 *
 * Files land in `<cacheDir>/exams/`, served by the app-shell FileProvider's
 * `exams_cache` path, and are shared through `File.asShareableUri`.
 */
object ExamPdfWriter {

    // A4 portrait in PostScript points (1/72"), the unit PdfDocument uses.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 24f
    private const val TITLE_BLOCK = 46f
    private const val HEADER_HEIGHT = 30f
    private const val ROW_HEIGHT = 18f
    private const val FOOTER_HEIGHT = 18f
    private const val CELL_PADDING = 4f

    // The name is the column a teacher scans, so it starts wide and grows into
    // whatever the data columns do not need. Data columns cap at 120 pt, so the
    // name is always the widest column on the page.
    private const val SERIAL_WIDTH = 28f
    private const val ROLL_WIDTH = 40f
    private const val MIN_NAME_WIDTH = 170f
    private const val MIN_DATA_WIDTH = 50f
    private const val MAX_DATA_WIDTH = 120f

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
        textSize = 8.5f
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
        className: String = "",
    ): File {
        val table = ExamPdfTableBuilder.build(subjects, results, columns)
        val dir = File(context.cacheDir, "exams").apply { mkdirs() }
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        try {
            render(document, table, examName, className)
            FileOutputStream(file).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }
        return file
    }

    private fun render(
        document: PdfDocument,
        table: ExamPdfTable,
        examName: String,
        className: String,
    ) {
        // Without students there is nothing to split; one page carries the
        // message and the identifying headers.
        val sections = if (table.rows.isEmpty()) {
            listOf(
                ExamPdfSection(
                    headers = table.headers.take(EXAM_PDF_FIXED_COLUMNS),
                    rows = emptyList(),
                ),
            )
        } else {
            table.sections(columnsPerPage())
        }

        val rowsPerPage = rowsPerPage()
        val pages = sections.flatMap { section ->
            if (section.rows.isEmpty()) {
                listOf(section to emptyList())
            } else {
                section.rows.chunked(rowsPerPage).map { rows -> section to rows }
            }
        }

        pages.forEachIndexed { index, (section, rows) ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create(),
            )
            drawPage(
                canvas = page.canvas,
                section = section,
                rows = rows,
                widths = columnWidths(section.headers.size),
                examName = examName,
                className = className,
                studentCount = table.rows.size,
                pageNumber = index + 1,
                pageCount = pages.size,
            )
            document.finishPage(page)
        }
    }

    private fun drawPage(
        canvas: Canvas,
        section: ExamPdfSection,
        rows: List<List<String>>,
        widths: FloatArray,
        examName: String,
        className: String,
        studentCount: Int,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        val heading = ExportHeading.title(examName, className)
        val title = TextUtils.ellipsize(heading, TITLE_PAINT, right - left, TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, left, MARGIN + 14f, TITLE_PAINT)
        val subtitle = "Results · $studentCount student${if (studentCount == 1) "" else "s"}"
        val subtitleText = TextUtils.ellipsize(subtitle, SUBTITLE_PAINT, right - left, TextUtils.TruncateAt.END)
        canvas.drawText(subtitleText, 0, subtitleText.length, left, MARGIN + 32f, SUBTITLE_PAINT)

        val headerTop = MARGIN + TITLE_BLOCK
        val headerBottom = headerTop + HEADER_HEIGHT
        drawRow(canvas, section.headers, widths, headerTop, headerBottom, header = true)

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

        var top = headerBottom
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

            if (header) {
                drawHeaderCell(canvas, raw, left, top, bottom, width)
            } else {
                val paint = if (isFail(raw)) FAIL_PAINT else CELL_PAINT
                val text = TextUtils.ellipsize(
                    raw,
                    paint,
                    (width - 2 * CELL_PADDING).coerceAtLeast(1f),
                    TextUtils.TruncateAt.END,
                )
                val baseline = top + (bottom - top - (paint.descent() + paint.ascent())) / 2f
                val x = if (isNumeric(raw)) {
                    right - CELL_PADDING - paint.measureText(text, 0, text.length)
                } else {
                    left + CELL_PADDING
                }
                canvas.drawText(text, 0, text.length, x, baseline, paint)
            }
            left = right
        }
    }

    /**
     * A narrow subject column still has to say "Total Obtained": the header
     * wraps onto a second line instead of being squeezed into an ellipsis.
     */
    private fun drawHeaderCell(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        bottom: Float,
        width: Float,
    ) {
        val maxWidth = (width - 2 * CELL_PADDING).coerceAtLeast(1f)
        val lines = if (HEADER_PAINT.measureText(text) <= maxWidth) {
            listOf(text)
        } else {
            wrapTwoLines(text, HEADER_PAINT, maxWidth)
        }
        val lineHeight = HEADER_PAINT.descent() - HEADER_PAINT.ascent()
        var baseline = top + (bottom - top - lineHeight * lines.size) / 2f - HEADER_PAINT.ascent()
        lines.forEach { line ->
            val clipped = TextUtils.ellipsize(line, HEADER_PAINT, maxWidth, TextUtils.TruncateAt.END)
            canvas.drawText(clipped, 0, clipped.length, left + CELL_PADDING, baseline, HEADER_PAINT)
            baseline += lineHeight
        }
    }

    private fun wrapTwoLines(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        var firstLine = ""
        var index = 0
        while (index < words.size) {
            val candidate = if (firstLine.isEmpty()) words[index] else "$firstLine ${words[index]}"
            if (firstLine.isNotEmpty() && paint.measureText(candidate) > maxWidth) break
            firstLine = candidate
            index++
        }
        val rest = words.drop(index).joinToString(" ")
        return if (rest.isEmpty()) listOf(firstLine) else listOf(firstLine, rest)
    }

    /** Numbers and percentages line up on the right; words on the left. */
    private fun isNumeric(value: String): Boolean = NUMBER.matches(value)

    private fun isFail(value: String): Boolean = value.equals("Fail", ignoreCase = true)

    /**
     * How many data columns share one portrait page before the next slice needs
     * its own page. The identifying columns and a wide name always stay.
     */
    private fun columnsPerPage(): Int {
        val availableForData = usableWidth() - SERIAL_WIDTH - ROLL_WIDTH - MIN_NAME_WIDTH
        return (availableForData / MIN_DATA_WIDTH).toInt().coerceAtLeast(1)
    }

    /**
     * S.No. is narrow, the name is the widest column, and the data columns get
     * the rest, between [MIN_DATA_WIDTH] and [MAX_DATA_WIDTH]. Leftover space
     * goes to the name, which is what a teacher reads first.
     */
    private fun columnWidths(columnCount: Int): FloatArray {
        val usable = usableWidth()
        val dataCount = (columnCount - EXAM_PDF_FIXED_COLUMNS).coerceAtLeast(0)
        val availableForData = usable - SERIAL_WIDTH - ROLL_WIDTH - MIN_NAME_WIDTH
        val dataWidth = if (dataCount == 0) {
            0f
        } else {
            (availableForData / dataCount).coerceIn(MIN_DATA_WIDTH, MAX_DATA_WIDTH)
        }
        val nameWidth = (usable - SERIAL_WIDTH - ROLL_WIDTH - dataWidth * dataCount)
            .coerceAtLeast(MIN_NAME_WIDTH)

        val widths = FloatArray(columnCount) { index ->
            when (index) {
                0 -> SERIAL_WIDTH
                1 -> ROLL_WIDTH
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

    private fun rowsPerPage(): Int {
        val bodyTop = MARGIN + TITLE_BLOCK + HEADER_HEIGHT
        val bodyBottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        return ((bodyBottom - bodyTop) / ROW_HEIGHT).toInt().coerceAtLeast(1)
    }

    private fun usableWidth(): Float = PAGE_WIDTH - 2 * MARGIN

    private val NUMBER = Regex("""-?\d+(\.\d+)?%?""")
}
