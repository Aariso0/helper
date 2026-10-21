package com.example.helper.exportbackup.writers

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.example.helper.exportbackup.summarize.ClassMonthSummary
import com.example.helper.exportbackup.summarize.MonthlySummary
import com.example.helper.exportbackup.summarize.PERCENTAGE_RULE
import com.example.helper.exportbackup.summarize.StudentMonthSummary
import java.io.OutputStream
import java.util.Locale

/**
 * PDF writer over the one summary model (`export-backup` card: framework
 * `PdfDocument`, the same zero-dependency approach `parents` used). A clean
 * titled page — title, month, scope, the percentage rule, then a monospaced
 * table per class so columns actually line up, with per-class totals and a
 * grand total. Long classes paginate; the class header repeats when a table
 * continues on a new page.
 */
object SummaryPdfWriter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val PAGE_BOTTOM = PAGE_HEIGHT - 56f

    fun write(summary: MonthlySummary, monthLabel: String, scopeLabel: String, output: OutputStream) {
        val document = PdfDocument()
        try {
            val pages = Pages(document)
            val title = paint("sans-serif", Typeface.BOLD, 18f, Color.rgb(32, 33, 36))
            val subtitle = paint("sans-serif", Typeface.NORMAL, 11f, Color.rgb(95, 99, 104))
            val note = paint("sans-serif", Typeface.NORMAL, 9f, Color.rgb(95, 99, 104))
            val classHead = paint("sans-serif", Typeface.BOLD, 12.5f, Color.rgb(32, 33, 36))
            val mono = paint("monospace", Typeface.NORMAL, 8.5f, Color.rgb(32, 33, 36))
            val monoBold = paint("monospace", Typeface.BOLD, 8.5f, Color.rgb(32, 33, 36))

            pages.draw(layout("Attendance summary", title), gap = 6f)
            pages.draw(layout(monthLabel, subtitle), gap = 2f)
            pages.draw(layout("Scope: $scopeLabel", subtitle), gap = 2f)
            pages.draw(layout(PERCENTAGE_RULE, note), gap = 2f)
            pages.draw(layout("\"No records\" means attendance was never marked that month.", note), gap = 18f)

            summary.classes.forEach { classSummary ->
                val head = layout(classSummary.className, classHead)
                val classTotals = layout(classTotalsLine(classSummary), mono)
                val header = layout(COLUMN_HEADER, monoBold)
                pages.draw(head, gap = 4f)
                pages.draw(classTotals, gap = 2f)
                pages.draw(header, gap = 2f)
                classSummary.students.forEach { student ->
                    pages.draw(
                        layout(studentLine(student), mono),
                        repeatBefore = listOf(head, classTotals, header),
                    )
                }
                pages.draw(layout("", mono), gap = 12f)
            }

            pages.draw(layout(grandTotalLine(summary), monoBold), gap = 0f)
            pages.finish()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun paint(family: String, style: Int, size: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(family, style)
            textSize = size
            this.color = color
        }

    private fun layout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, CONTENT_WIDTH.toInt())
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .build()

    private const val NAME_WIDTH = 40
    private const val ROW_FORMAT = "%-40.40s%4d%4d%4d%4d%6d%8s"
    private val COLUMN_HEADER = String.format(
        Locale.US,
        "%-${NAME_WIDTH}s%4s%4s%4s%4s%6s%8s",
        "Student", "P", "A", "L", "E", "Days", "%",
    )

    private fun studentLine(student: StudentMonthSummary): String {
        val label = student.rollNumber?.takeIf { it.isNotBlank() }
            ?.let { "${student.studentName} ($it)" }
            ?: student.studentName
        return String.format(
            Locale.US,
            ROW_FORMAT,
            label,
            student.present,
            student.absent,
            student.late,
            student.excused,
            student.trackedRecords,
            percentLabel(student.presentPercentage),
        )
    }

    private fun classTotalsLine(classSummary: ClassMonthSummary): String = String.format(
        Locale.US,
        ROW_FORMAT,
        "CLASS TOTAL",
        classSummary.present,
        classSummary.absent,
        classSummary.late,
        classSummary.excused,
        classSummary.trackedDays,
        percentLabel(classSummary.presentPercentage),
    )

    private fun grandTotalLine(summary: MonthlySummary): String = String.format(
        Locale.US,
        ROW_FORMAT,
        "ALL CLASSES TOTAL",
        summary.present,
        summary.absent,
        summary.late,
        summary.excused,
        summary.trackedDays,
        percentLabel(summary.presentPercentage),
    )

    /** Sequential block layout with pagination and a page-number footer. */
    private class Pages(private val document: PdfDocument) {
        private var number = 1
        private var page = document.startPage(info(number))
        private var y = MARGIN

        fun draw(layout: StaticLayout, gap: Float = 1.5f, repeatBefore: List<StaticLayout> = emptyList()) {
            if (y > MARGIN && y + layout.height > PAGE_BOTTOM) {
                finishCurrent()
                number += 1
                page = document.startPage(info(number))
                y = MARGIN
                repeatBefore.forEach { draw(it) }
            }
            page.canvas.save()
            page.canvas.translate(MARGIN, y)
            layout.draw(page.canvas)
            page.canvas.restore()
            y += layout.height + gap
        }

        fun finish() = finishCurrent()

        private fun finishCurrent() {
            val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize = 8f
                color = Color.rgb(128, 132, 138)
            }
            val label = number.toString()
            page.canvas.drawText(
                label,
                PAGE_WIDTH - MARGIN - footer.measureText(label),
                PAGE_HEIGHT - 28f,
                footer,
            )
            document.finishPage(page)
        }
    }

    private fun info(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
}
