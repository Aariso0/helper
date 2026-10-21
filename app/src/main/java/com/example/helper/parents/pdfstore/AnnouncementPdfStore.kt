package com.example.helper.parents.pdfstore

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * `pdfstore` (parents card): renders an announcement into a paginated PDF via
 * the platform's `PdfDocument` — zero dependencies, no license surface (the
 * card's `# Excluded` closed every third-party PDF writer). Text is laid out
 * with [StaticLayout], so Hindi and other complex scripts wrap correctly —
 * announcement *content* is whatever the teacher types, even though the UI
 * itself is English-only.
 *
 * Files land in the app-internal `<filesDir>/exports/` directory, already
 * served by the app-shell's single FileProvider (`res/xml/file_paths.xml`,
 * "exports" entry) — shared through `File.asShareableUri`, never `file://`.
 * PDFs are derivable artifacts (the announcement row is the source of truth),
 * so regeneration overwrites nothing and old files may accumulate; the
 * export-backup module owns any sweeping of this directory.
 */
class AnnouncementPdfStore(private val context: Context) {

    val directory: File
        get() = File(context.filesDir, EXPORTS_DIR).apply { mkdirs() }

    /**
     * Renders one announcement page-set and returns the written PDF file.
     * [contextLine] is the small header line under the title (class · date).
     * Throws [IOException] if the file cannot be written — callers surface it
     * as a snackbar, never a crash.
     */
    fun render(title: String?, body: String, contextLine: String?): File {
        val document = PdfDocument()
        try {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = TITLE_SIZE
                color = Color.rgb(32, 33, 36)
            }
            val contextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SANS_SERIF
                textSize = CONTEXT_SIZE
                color = Color.rgb(95, 99, 104)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SANS_SERIF
                textSize = BODY_SIZE
                color = Color.rgb(32, 33, 36)
            }
            val dividerPaint = Paint().apply {
                color = Color.rgb(218, 220, 224)
                strokeWidth = DIVIDER_STROKE
            }

            var pageNumber = 1
            var page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            var y = MARGIN

            // ── Header: title, context line, divider (page 1 only) ────────
            val trimmedTitle = title?.trim().orEmpty()
            if (trimmedTitle.isNotEmpty()) {
                y = drawBlock(page, layout(trimmedTitle, titlePaint, CONTENT_WIDTH), y)
                y += TITLE_GAP
            }
            if (!contextLine.isNullOrBlank()) {
                y = drawBlock(page, layout(contextLine, contextPaint, CONTENT_WIDTH), y)
                y += CONTEXT_GAP
            }
            if (trimmedTitle.isNotEmpty() || !contextLine.isNullOrBlank()) {
                page.canvas.drawLine(
                    MARGIN.toFloat(),
                    y.toFloat(),
                    (PAGE_WIDTH - MARGIN).toFloat(),
                    y.toFloat(),
                    dividerPaint,
                )
                y += DIVIDER_GAP
            }

            // ── Body: one layout, drawn line-band by line-band across pages ─
            val bodyLayout = layout(body, bodyPaint, CONTENT_WIDTH)
            var firstLine = 0
            while (firstLine < bodyLayout.lineCount) {
                val available = PAGE_HEIGHT - MARGIN - y
                val lineTop = bodyLayout.getLineTop(firstLine)
                var lastLine = firstLine
                while (lastLine < bodyLayout.lineCount &&
                    bodyLayout.getLineBottom(lastLine) - lineTop <= available
                ) {
                    lastLine++
                }
                if (lastLine == firstLine) {
                    // Nothing (not even one line) fits the remaining space —
                    // or a single line is taller than a whole page (guard
                    // against an infinite loop on pathological input).
                    if (y > MARGIN) {
                        document.finishPage(page)
                        page = document.startPage(
                            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create(),
                        )
                        y = MARGIN
                        continue
                    }
                    lastLine = firstLine + 1 // force progress on a fresh page
                }
                page.canvas.save()
                page.canvas.clipRect(
                    MARGIN.toFloat(),
                    y.toFloat(),
                    (PAGE_WIDTH - MARGIN).toFloat(),
                    (PAGE_HEIGHT - MARGIN).toFloat(),
                )
                page.canvas.translate(MARGIN.toFloat(), y - lineTop.toFloat())
                bodyLayout.draw(page.canvas)
                page.canvas.restore()
                y += (bodyLayout.getLineBottom(lastLine - 1) - lineTop)
                firstLine = lastLine
                if (firstLine < bodyLayout.lineCount) {
                    document.finishPage(page)
                    page = document.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create(),
                    )
                    y = MARGIN
                }
            }

            document.finishPage(page)
            val file = File(directory, "announcement_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            return file
        } finally {
            document.close()
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, BODY_LINE_SPACING)
            .build()

    /** Draws a short block at [top]; returns the y below it. */
    private fun drawBlock(page: PdfDocument.Page, block: StaticLayout, top: Int): Int {
        page.canvas.save()
        page.canvas.translate(MARGIN.toFloat(), top.toFloat())
        block.draw(page.canvas)
        page.canvas.restore()
        return top + block.height
    }

    companion object {
        const val EXPORTS_DIR = "exports"

        /** A4 at 72 dpi (PDF user units are points). */
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 48
        private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

        private const val TITLE_SIZE = 18f
        private const val CONTEXT_SIZE = 11f
        private const val BODY_SIZE = 13f
        private const val DIVIDER_STROKE = 1f

        private const val TITLE_GAP = 8
        private const val CONTEXT_GAP = 10
        private const val DIVIDER_GAP = 18
        private const val BODY_LINE_SPACING = 1.2f
    }
}
