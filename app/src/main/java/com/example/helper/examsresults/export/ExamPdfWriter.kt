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
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.util.StudentResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prints the results sheet as a PDF: portrait A4, the printed banner repeated
 * on every page, a thin border around every cell, and "Fail" in bold italic —
 * the same reading a printed Excel export gives, without needing Excel.
 *
 * A class can have more subject columns than portrait A4 can show legibly, so
 * the table continues on later pages: S.No., Roll No. and Student Name are
 * repeated, then the next slice of subject columns. A long class first shrinks
 * to a readable floor (name 7 pt, marks 6.5 pt) and then continues on a new
 * page with the same banner and header, so no student is ever clipped.
 *
 * Uses the platform's `PdfDocument` like the announcement PDF store, so no
 * third-party renderer enters the build. Dependent columns the device cannot
 * evaluate (custom Excel formulas) are left off, see [ExamPdfTableBuilder].
 */
object ExamPdfWriter {

    // A4 portrait in PostScript points (1/72"), the unit PdfDocument uses.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    // The analysis pages are landscape A4.
    private const val LANDSCAPE_WIDTH = 842
    private const val LANDSCAPE_HEIGHT = 595
    private const val MARGIN = 24f
    private const val BANNER_ROW_1 = 18f
    private const val BANNER_ROW_2 = 18f
    private const val BANNER_ROW_3 = 14f
    private const val BANNER_ROW_4 = 14f
    private const val BANNER_HEIGHT = BANNER_ROW_1 + BANNER_ROW_2 + BANNER_ROW_3 + BANNER_ROW_4
    private const val HEADER_HEIGHT = 40f
    private const val FOOTER_HEIGHT = 16f
    private const val CELL_PADDING = 3.5f

    private const val ANALYSIS_TITLE_HEIGHT = 18f
    private const val ANALYSIS_HEADER_HEIGHT = 34f
    private const val ANALYSIS_ROW_HEIGHT = 15f
    private const val SIGNATURE_GAP = 10f

    // S.No. and Roll stay narrow, the name is the widest column, and the data
    // columns share what is left. The name grows into any unused space.
    private const val SERIAL_WIDTH = ExamPdfPagePlan.SERIAL_WIDTH
    private const val ROLL_WIDTH = ExamPdfPagePlan.ROLL_WIDTH
    private const val MIN_NAME_WIDTH = ExamPdfPagePlan.MIN_NAME_WIDTH
    private const val MIN_DATA_WIDTH = ExamPdfPagePlan.MIN_DATA_WIDTH
    private const val SUBJECT_WIDTH = 48f
    /** Fits `Total Obtained` over `(400)` when the header wraps to two lines. */
    private const val TOTAL_WIDTH = 66f
    private const val PERCENT_WIDTH = 40f
    private const val RESULT_WIDTH = 40f
    private const val RANK_WIDTH = 26f

    private val BANNER_BLUE = Color.parseColor(ExportPalette.BANNER_BLUE)
    private val HEADER_YELLOW = Color.parseColor(ExportPalette.HEADER_YELLOW)
    private val SUBJECT_CYAN = Color.parseColor(ExportPalette.SUBJECT_CYAN)
    private val TITLE_CREAM = Color.parseColor(ExportPalette.TITLE_CREAM)
    private val BAND_BLUE = Color.parseColor(ExportPalette.BAND_BLUE)
    private val BAND_GREY = Color.parseColor(ExportPalette.BAND_GREY)
    private val BAND_GREEN = Color.parseColor(ExportPalette.BAND_GREEN)
    private val BAND_AMBER = Color.parseColor(ExportPalette.BAND_AMBER)

    /** Band fills low to high; the lowest band keeps the page colour. */
    private val SEVEN_BAND_FILLS: List<Int?> = listOf(
        null,
        BAND_BLUE,
        BAND_BLUE,
        BAND_GREY,
        BAND_GREEN,
        BAND_AMBER,
        BAND_AMBER,
    )

    private val FIVE_BAND_FILLS: List<Int?> = listOf(
        null,
        BAND_BLUE,
        BAND_GREY,
        BAND_GREEN,
        BAND_AMBER,
    )

    private val FILL = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val BORDER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = Color.rgb(90, 96, 102)
    }

    /** Renders the sheet and returns the written file; throws on a write error. */
    fun write(
        context: Context,
        examName: String,
        subjects: List<ExamSubject>,
        results: List<StudentResult>,
        columns: List<ExportColumn> = ExportColumnDefaults.columns(),
        className: String = "",
        schoolName: String = "",
        sessionLabel: String = "",
        teacherName: String = "",
        teacherDesignation: String = "",
        examDate: Long? = null,
    ): File {
        val table = ExamPdfTableBuilder.build(subjects, results, columns)
        val banner = ExamPdfBanner(
            examName = examName,
            className = className,
            schoolName = schoolName,
            sessionLabel = sessionLabel,
            teacherName = teacherName,
            teacherDesignation = teacherDesignation,
            examDate = examDate,
        )
        val dir = File(context.cacheDir, "exams").apply { mkdirs() }
        val safeName = examName.replace("[^A-Za-z0-9]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")

        // Pages 2 and 3 only exist when there is an analysis to print.
        val stats = if (results.isNotEmpty() && subjects.isNotEmpty()) {
            ExamSheetStats.from(subjects, results)
        } else {
            null
        }

        val document = PdfDocument()
        try {
            render(document, table, banner, stats, subjects)
            FileOutputStream(file).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }
        return file
    }

    private fun render(
        document: PdfDocument,
        table: ExamPdfTable,
        banner: ExamPdfBanner,
        stats: ExamSheetStats?,
        subjects: List<ExamSubject>,
    ) {
        // Without students there is nothing to split; one page carries the
        // message and the identifying headers.
        val sections = if (table.rows.isEmpty()) {
            listOf(
                ExamPdfSection(
                    headers = table.headers.take(EXAM_PDF_FIXED_COLUMNS),
                    subHeaders = table.subHeaders.take(EXAM_PDF_FIXED_COLUMNS),
                    kinds = table.kinds.take(EXAM_PDF_FIXED_COLUMNS),
                    rows = emptyList(),
                ),
            )
        } else {
            table.sections(ExamPdfPagePlan.portraitDataColumns())
        }

        val fit = ExamPdfLayout.fit(availableBodyHeight(), table.rows.size)
        val pages = sections.flatMap { section ->
            if (section.rows.isEmpty()) {
                listOf(section to emptyList())
            } else {
                section.rows.chunked(fit.rowsPerPage).map { rows -> section to rows }
            }
        }

        val plan = ExamPdfPagePlan.build(marksPageCount = pages.size, hasAnalysis = stats != null)

        pages.forEachIndexed { index, (section, rows) ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create(),
            )
            drawPage(
                canvas = page.canvas,
                section = section,
                rows = rows,
                widths = columnWidths(section),
                banner = banner,
                fit = fit,
                pageNumber = index + 1,
                pageCount = plan.size,
            )
            document.finishPage(page)
        }

        if (stats == null) return

        val summaryNumber = pages.size + 1
        val summaryPage = document.startPage(
            PdfDocument.PageInfo.Builder(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, summaryNumber).create(),
        )
        drawSummaryPage(
            canvas = summaryPage.canvas,
            stats = stats,
            banner = banner,
            pageNumber = summaryNumber,
            pageCount = plan.size,
        )
        document.finishPage(summaryPage)

        val teacherNumber = pages.size + 2
        val teacherPage = document.startPage(
            PdfDocument.PageInfo.Builder(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, teacherNumber).create(),
        )
        drawSubjectTeacherPage(
            canvas = teacherPage.canvas,
            stats = stats,
            subjects = subjects,
            banner = banner,
            pageNumber = teacherNumber,
            pageCount = plan.size,
        )
        document.finishPage(teacherPage)
    }

    private fun drawPage(
        canvas: Canvas,
        section: ExamPdfSection,
        rows: List<List<String>>,
        widths: FloatArray,
        banner: ExamPdfBanner,
        fit: ExamPdfFit,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        drawBanner(canvas, banner, left, right, MARGIN)

        val headerTop = MARGIN + BANNER_HEIGHT
        val headerBottom = headerTop + HEADER_HEIGHT
        drawTableHeader(canvas, section, widths, headerTop, headerBottom, fit)

        val footerText = "Page $pageNumber of $pageCount"
        val footerPaint = textPaint(7f)
        val footerWidth = footerPaint.measureText(footerText)
        canvas.drawText(
            footerText,
            right - footerWidth,
            PAGE_HEIGHT - MARGIN + 6f,
            footerPaint,
        )

        if (rows.isEmpty()) {
            canvas.drawText(
                "No students with marks yet.",
                left + CELL_PADDING,
                headerBottom + fit.rowHeight,
                textPaint(fit.textSize),
            )
            return
        }

        var top = headerBottom
        rows.forEach { row ->
            val bottom = top + fit.rowHeight
            drawDataRow(canvas, section, row, widths, top, bottom, fit)
            top = bottom
        }
        canvas.drawLine(left, top, right, top, BORDER_PAINT)
    }

    private fun drawBanner(
        canvas: Canvas,
        banner: ExamPdfBanner,
        left: Float,
        right: Float,
        top: Float,
    ) {
        val width = right - left

        // School name on banner blue.
        val row1Bottom = top + BANNER_ROW_1
        FILL.color = BANNER_BLUE
        canvas.drawRect(left, top, right, row1Bottom, FILL)
        val schoolPaint = textPaint(12f, bold = true)
        drawCentered(
            canvas,
            banner.schoolName.trim(),
            left + CELL_PADDING,
            right - CELL_PADDING,
            top,
            row1Bottom,
            schoolPaint,
        )

        // Exam | month + session | class on yellow.
        val row2Bottom = row1Bottom + BANNER_ROW_2
        FILL.color = HEADER_YELLOW
        canvas.drawRect(left, row1Bottom, right, row2Bottom, FILL)
        val examRight = left + width * 0.42f
        val sessionRight = left + width * 0.86f
        val rowPaint = textPaint(9.5f, bold = true)
        drawLeft(
            canvas,
            banner.examName.trim(),
            left + CELL_PADDING,
            examRight - CELL_PADDING,
            row1Bottom,
            row2Bottom,
            rowPaint,
        )
        val middle = listOf(monthLabel(banner.examDate), banner.sessionLabel.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        drawCentered(
            canvas,
            middle,
            examRight,
            sessionRight,
            row1Bottom,
            row2Bottom,
            rowPaint,
        )
        drawRight(
            canvas,
            banner.className.trim().takeIf { it.isNotEmpty() }?.let { "CLASS-$it" }.orEmpty(),
            sessionRight,
            right - CELL_PADDING,
            row1Bottom,
            row2Bottom,
            rowPaint,
        )

        // Sheet title.
        val row3Bottom = row2Bottom + BANNER_ROW_3
        drawCentered(
            canvas,
            "CONSOLIDATED MARKS - STATEMENT",
            left + CELL_PADDING,
            right - CELL_PADDING,
            row2Bottom,
            row3Bottom,
            textPaint(10f, bold = true),
        )

        // Class teacher.
        val row4Bottom = row3Bottom + BANNER_ROW_4
        drawLeft(
            canvas,
            classTeacherLine(banner.teacherName, banner.teacherDesignation),
            left + CELL_PADDING,
            right - CELL_PADDING,
            row3Bottom,
            row4Bottom,
            textPaint(8.5f),
        )
    }

    /** Landscape page 2: the summary table plus the four-cell CLASS-WISE block. */
    private fun drawSummaryPage(
        canvas: Canvas,
        stats: ExamSheetStats,
        banner: ExamPdfBanner,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = LANDSCAPE_WIDTH - MARGIN
        drawBanner(canvas, banner, left, right, MARGIN)

        val widths = summaryColumnWidths()
        val titleTop = MARGIN + BANNER_HEIGHT
        val titleBottom = titleTop + ANALYSIS_TITLE_HEIGHT
        val headerTop = titleBottom
        val headerBottom = headerTop + ANALYSIS_HEADER_HEIGHT
        val splitX = columnX(left, widths, ExamSummarySheet.CLASS_WISE_COLUMN - 1)

        drawTitleCell(canvas, "RESULT- ANALYSIS", left, splitX, titleTop, titleBottom)
        drawTitleCell(canvas, ExamSummarySheet.CLASS_WISE_TITLE, splitX, right, titleTop, titleBottom)

        val headerPaint = textPaint(7f, bold = true)
        drawAnalysisHeader(
            canvas, ExamSummarySheet.SUBJECT_HEADER, left, columnX(left, widths, 1),
            headerTop, headerBottom, headerPaint, fill = null,
        )
        ExamSummarySheet.METRIC_HEADERS.forEachIndexed { index, header ->
            drawAnalysisHeader(
                canvas, header, columnX(left, widths, index + 1), columnX(left, widths, index + 2),
                headerTop, headerBottom, headerPaint, fill = null,
            )
        }
        ExamSummarySheet.CLASS_WISE_HEADERS.forEachIndexed { index, header ->
            drawAnalysisHeader(
                canvas, header,
                columnX(left, widths, ExamSummarySheet.CLASS_WISE_COLUMN - 1 + index),
                columnX(left, widths, ExamSummarySheet.CLASS_WISE_COLUMN + index),
                headerTop, headerBottom, headerPaint, fill = TITLE_CREAM,
            )
        }

        var top = headerBottom
        stats.subjects.forEach { subject ->
            val bottom = top + ANALYSIS_ROW_HEIGHT
            val cells = mutableListOf(
                subject.name,
                subject.strength.toString(),
                subject.appeared.toString(),
                subject.passed.toString(),
                percentLabel(subject.passShare),
                percentLabel(subject.averageShare),
            )
            subject.bands7.forEach { cells.add(it.toString()) }
            repeat(ExamSummarySheet.CLASS_WISE_HEADERS.size) { cells.add("") }
            drawAnalysisRow(canvas, cells, widths, left, top, bottom, summaryFills())
            top = bottom
        }

        val overall = stats.overall
        val overallCells = mutableListOf(
            ExamSummarySheet.OVERALL_LABEL,
            overall.strength.toString(),
            overall.appeared.toString(),
            overall.passed.toString(),
            percentLabel(overall.passShare),
            percentLabel(overall.averageShare),
        )
        overall.bands7.forEach { overallCells.add(it.toString()) }
        overallCells.add(overall.appeared.toString())
        overallCells.add(overall.passed.toString())
        overallCells.add(percentLabel(overall.passShare))
        overallCells.add(percentLabel(overall.averageShare))
        val overallBottom = top + ANALYSIS_ROW_HEIGHT
        drawAnalysisRow(
            canvas, overallCells, widths, left, top, overallBottom,
            summaryFills(), bold = true,
        )

        drawSignatures(canvas, LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT)
        drawPageNumber(canvas, right, LANDSCAPE_HEIGHT, pageNumber, pageCount)
    }

    /** Landscape page 3: the subject-teacher table and its class-wise block. */
    private fun drawSubjectTeacherPage(
        canvas: Canvas,
        stats: ExamSheetStats,
        subjects: List<ExamSubject>,
        banner: ExamPdfBanner,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = LANDSCAPE_WIDTH - MARGIN
        drawBanner(canvas, banner, left, right, MARGIN)

        val widths = subjectTeacherColumnWidths()
        val titleTop = MARGIN + BANNER_HEIGHT
        val titleBottom = titleTop + ANALYSIS_TITLE_HEIGHT
        val headerTop = titleBottom
        val headerBottom = headerTop + ANALYSIS_HEADER_HEIGHT

        drawTitleCell(canvas, "RESULT- ANALYSIS", left, right, titleTop, titleBottom)

        val headers = mutableListOf(ExamSubjectTeacherSheet.SUBJECT_HEADER)
        headers.addAll(ExamSubjectTeacherSheet.METRIC_HEADERS)
        headers.addAll(ExamSubjectTeacherSheet.BAND_LABELS)
        headers.add(HEADER_TITLE)
        headers.add(HEADER_TEACHER_NAME)
        headers.add(HEADER_DESIGNATION)
        headers.add(HEADER_EMPLOYEE_TYPE)
        val headerPaint = textPaint(6.5f, bold = true)
        headers.forEachIndexed { index, header ->
            drawAnalysisHeader(
                canvas, header, columnX(left, widths, index), columnX(left, widths, index + 1),
                headerTop, headerBottom, headerPaint, fill = null,
            )
        }

        var top = headerBottom
        stats.subjects.forEachIndexed { index, subject ->
            val bottom = top + ANALYSIS_ROW_HEIGHT
            val cells = subjectTeacherCells(
                label = subject.name,
                strength = subject.strength,
                appeared = subject.appeared,
                absent = subject.absent,
                passed = subject.passed,
                failed = subject.failed,
                total = subject.total,
                passShare = subject.passShare,
                averageShare = subject.averageShare,
                bands = subject.bands5,
                teacher = subjectTeacherFields(subjects.getOrElse(index) { null }),
            )
            drawAnalysisRow(
                canvas, cells, widths, left, top, bottom, subjectTeacherFills(),
            )
            top = bottom
        }

        val overall = stats.overall
        val overallCells = subjectTeacherCells(
            label = ExamSubjectTeacherSheet.OVERALL_LABEL,
            strength = overall.strength,
            appeared = overall.appeared,
            absent = overall.absent,
            passed = overall.passed,
            failed = overall.failed,
            total = overall.total,
            passShare = overall.passShare,
            averageShare = overall.averageShare,
            bands = overall.bands5,
            teacher = emptyList(),
        )
        val overallBottom = top + ANALYSIS_ROW_HEIGHT
        drawAnalysisRow(
            canvas, overallCells, widths, left, top, overallBottom,
            subjectTeacherFills(), bold = true,
        )

        drawClassWiseBlock(canvas, left, overallBottom + 10f, stats.overall)
        drawSignatures(canvas, LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT)
        drawPageNumber(canvas, right, LANDSCAPE_HEIGHT, pageNumber, pageCount)
    }

    private fun subjectTeacherCells(
        label: String,
        strength: Int,
        appeared: Int,
        absent: Int,
        passed: Int,
        failed: Int,
        total: Int,
        passShare: Double?,
        averageShare: Double?,
        bands: List<Int>,
        teacher: List<String>,
    ): List<String> {
        val cells = mutableListOf(
            label,
            strength.toString(),
            appeared.toString(),
            absent.toString(),
            passed.toString(),
            failed.toString(),
            total.toString(),
            percentLabel(passShare),
            percentLabel(averageShare),
        )
        bands.forEach { cells.add(it.toString()) }
        repeat(4) { index -> cells.add(teacher.getOrElse(index) { "" }) }
        return cells
    }

    /** The four teacher fields for a subject, in O..R order. */
    private fun subjectTeacherFields(subject: ExamSubject?): List<String> {
        if (subject == null) return emptyList()
        return listOf(
            subject.title.orEmpty().trim(),
            subject.teacherName.orEmpty().trim(),
            subject.teacherDesignation.orEmpty().trim(),
            subject.employeeType.orEmpty().trim(),
        )
    }

    private fun drawAnalysisHeader(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: TextPaint,
        fill: Int?,
    ) {
        if (fill != null) {
            FILL.color = fill
            canvas.drawRect(left, top, right, bottom, FILL)
        }
        drawHeaderText(canvas, text, left, top, right, bottom, paint)
        canvas.drawRect(left, top, right, bottom, BORDER_PAINT)
    }

    private fun drawTitleCell(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        FILL.color = TITLE_CREAM
        canvas.drawRect(left, top, right, bottom, FILL)
        drawCentered(canvas, text, left, right, top, bottom, textPaint(8f, bold = true))
        canvas.drawRect(left, top, right, bottom, BORDER_PAINT)
    }

    private fun drawAnalysisRow(
        canvas: Canvas,
        cells: List<String>,
        widths: FloatArray,
        left: Float,
        top: Float,
        bottom: Float,
        fills: List<Int?>,
        bold: Boolean = false,
    ) {
        var x = left
        cells.forEachIndexed { index, raw ->
            val width = widths.getOrElse(index) { 0f }
            val right = x + width
            fills.getOrNull(index)?.let { colour ->
                FILL.color = colour
                canvas.drawRect(x, top, right, bottom, FILL)
            }
            val paint = textPaint(7.5f, bold = bold)
            val maxWidth = (width - 2 * CELL_PADDING).coerceAtLeast(1f)
            val text = TextUtils.ellipsize(raw, paint, maxWidth, TextUtils.TruncateAt.END)
            val drawX = if (isNumeric(raw)) {
                right - CELL_PADDING - paint.measureText(text, 0, text.length)
            } else {
                x + CELL_PADDING
            }
            canvas.drawText(text, 0, text.length, drawX, centeredBaseline(top, bottom, paint), paint)
            canvas.drawRect(x, top, right, bottom, BORDER_PAINT)
            x = right
        }
    }

    private fun summaryFills(): List<Int?> = List(ExamSummarySheet.LAST_COLUMN) { index ->
        when {
            index == 1 -> HEADER_YELLOW
            index in ExamSummarySheet.FIRST_BAND_COLUMN - 1 until ExamSummarySheet.CLASS_WISE_COLUMN - 1 ->
                SEVEN_BAND_FILLS[index - (ExamSummarySheet.FIRST_BAND_COLUMN - 1)]
            index >= ExamSummarySheet.CLASS_WISE_COLUMN - 1 -> TITLE_CREAM
            else -> null
        }
    }

    private fun subjectTeacherFills(): List<Int?> =
        List(ExamSubjectTeacherSheet.LAST_COLUMN) { index ->
            when {
                index == 1 -> HEADER_YELLOW
                index in ExamSubjectTeacherSheet.FIRST_BAND_COLUMN - 1 until
                    ExamSubjectTeacherSheet.TITLE_COLUMN - 1 ->
                    FIVE_BAND_FILLS[index - (ExamSubjectTeacherSheet.FIRST_BAND_COLUMN - 1)]
                else -> null
            }
        }

    private fun drawClassWiseBlock(
        canvas: Canvas,
        left: Float,
        top: Float,
        overall: OverallSheetStats,
    ): Float {
        val columns = ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.size
        val width = 340f
        val cellWidth = width / columns
        val titleBottom = top + ANALYSIS_TITLE_HEIGHT
        val headerBottom = titleBottom + ANALYSIS_HEADER_HEIGHT
        val valueBottom = headerBottom + ANALYSIS_ROW_HEIGHT

        drawTitleCell(
            canvas,
            ExamSubjectTeacherSheet.CLASS_WISE_TITLE,
            left,
            left + width,
            top,
            titleBottom,
        )
        val paint = textPaint(6.5f, bold = true)
        ExamSubjectTeacherSheet.CLASS_WISE_HEADERS.forEachIndexed { index, header ->
            drawAnalysisHeader(
                canvas, header, left + index * cellWidth, left + (index + 1) * cellWidth,
                titleBottom, headerBottom, paint, fill = TITLE_CREAM,
            )
        }
        val values = listOf(
            overall.strength.toString(),
            overall.appeared.toString(),
            overall.passed.toString(),
            percentLabel(overall.passShare),
            percentLabel(overall.averageShare),
        )
        drawAnalysisRow(
            canvas,
            values,
            FloatArray(columns) { cellWidth },
            left,
            headerBottom,
            valueBottom,
            List(columns) { TITLE_CREAM },
        )
        return valueBottom
    }

    private fun drawSignatures(canvas: Canvas, pageWidth: Int, pageHeight: Int) {
        val paint = textPaint(8f, bold = true)
        val baseline = pageHeight - MARGIN - SIGNATURE_GAP
        canvas.drawText("SIGN OF CLASS TEACHER", MARGIN, baseline, paint)
        val right = "PRINCIPAL"
        canvas.drawText(
            right,
            pageWidth - MARGIN - paint.measureText(right),
            baseline,
            paint,
        )
    }

    private fun drawPageNumber(
        canvas: Canvas,
        right: Float,
        pageHeight: Int,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val footerText = "Page $pageNumber of $pageCount"
        val footerPaint = textPaint(7f)
        canvas.drawText(
            footerText,
            right - footerPaint.measureText(footerText),
            pageHeight - MARGIN + 6f,
            footerPaint,
        )
    }

    private fun percentLabel(share: Double?): String =
        share?.let { String.format(Locale.US, "%.2f%%", it * 100) } ?: ""

    private fun columnX(left: Float, widths: FloatArray, index: Int): Float {
        var x = left
        for (column in 0 until index.coerceAtMost(widths.size)) {
            x += widths[column]
        }
        return x
    }

    /** A..M the summary metrics, N..Q the four class-wise cells. */
    private fun summaryColumnWidths(): FloatArray {
        val usable = LANDSCAPE_WIDTH - 2 * MARGIN
        val name = 112f
        val classWise = 50f
        val metric = (usable - name - classWise * 4) / 12f
        return FloatArray(ExamSummarySheet.LAST_COLUMN) { index ->
            when {
                index == 0 -> name
                index <= ExamSummarySheet.CLASS_WISE_COLUMN - 2 -> metric
                else -> classWise
            }
        }
    }

    /** A subjects, B..I metrics, J..N bands, O..R the four teacher fields. */
    private fun subjectTeacherColumnWidths(): FloatArray {
        val widths = mutableListOf(96f)
        repeat(8) { widths.add(40f) }
        repeat(5) { widths.add(38f) }
        widths.addAll(listOf(46f, 86f, 62f, 52f))
        val usable = LANDSCAPE_WIDTH - 2 * MARGIN
        val scale = usable / widths.sum()
        return FloatArray(widths.size) { index -> widths[index] * scale }
    }

    private fun drawTableHeader(
        canvas: Canvas,
        section: ExamPdfSection,
        widths: FloatArray,
        top: Float,
        bottom: Float,
        fit: ExamPdfFit,
    ) {
        val middle = top + HEADER_HEIGHT / 2f
        val paint = textPaint(fit.textSize.coerceAtLeast(6f), bold = true)
        var x = MARGIN
        section.headers.forEachIndexed { index, header ->
            val width = widths.getOrElse(index) { 0f }
            val right = x + width
            val identifying = index < EXAM_PDF_FIXED_COLUMNS
            val kind = section.kinds.getOrNull(index)
            val subject = !identifying && kind == null
            // S.No. and Roll No. span both rows, as does every computed
            // column. The name column keeps "/Max.Marks" beneath it.
            val merged = index == 0 || index == 1 || (!identifying && kind != null)

            when {
                subject -> {
                    FILL.color = SUBJECT_CYAN
                    canvas.drawRect(x, top, right, middle, FILL)
                    FILL.color = HEADER_YELLOW
                    canvas.drawRect(x, middle, right, bottom, FILL)
                    drawHeaderText(canvas, header, x, top, right, middle, paint)
                    drawHeaderText(
                        canvas,
                        section.subHeaders.getOrElse(index) { "" },
                        x,
                        middle,
                        right,
                        bottom,
                        paint,
                    )
                }
                merged -> {
                    drawHeaderText(canvas, header, x, top, right, bottom, paint)
                }
                else -> {
                    drawHeaderText(canvas, header, x, top, right, middle, paint)
                    drawHeaderText(
                        canvas,
                        section.subHeaders.getOrElse(index) { "" },
                        x,
                        middle,
                        right,
                        bottom,
                        paint,
                    )
                }
            }
            canvas.drawRect(x, top, right, bottom, BORDER_PAINT)
            x = right
        }
    }

    private fun drawDataRow(
        canvas: Canvas,
        section: ExamPdfSection,
        row: List<String>,
        widths: FloatArray,
        top: Float,
        bottom: Float,
        fit: ExamPdfFit,
    ) {
        var x = MARGIN
        section.headers.indices.forEach { index ->
            val width = widths.getOrElse(index) { 0f }
            val right = x + width
            val kind = section.kinds.getOrNull(index)
            val subject = index >= EXAM_PDF_FIXED_COLUMNS && kind == null
            if (subject) {
                FILL.color = HEADER_YELLOW
                canvas.drawRect(x, top, right, bottom, FILL)
            }

            val raw = row.getOrElse(index) { "" }
            val paint = if (isFail(raw)) {
                textPaint(if (index == 2) fit.nameTextSize else fit.textSize, bold = true, italic = true)
            } else {
                textPaint(if (index == 2) fit.nameTextSize else fit.textSize)
            }
            val maxWidth = (width - 2 * CELL_PADDING).coerceAtLeast(1f)
            val text = TextUtils.ellipsize(raw, paint, maxWidth, TextUtils.TruncateAt.END)
            val baseline = centeredBaseline(top, bottom, paint)
            val drawX = if (isNumeric(raw)) {
                right - CELL_PADDING - paint.measureText(text, 0, text.length)
            } else {
                x + CELL_PADDING
            }
            canvas.drawText(text, 0, text.length, drawX, baseline, paint)
            canvas.drawRect(x, top, right, bottom, BORDER_PAINT)
            x = right
        }
    }

    private fun drawHeaderText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        paint: TextPaint,
    ) {
        val maxWidth = (right - left - 2 * CELL_PADDING).coerceAtLeast(1f)
        val lines = if (paint.measureText(text) <= maxWidth) {
            listOf(text)
        } else {
            wrapTwoLines(text, paint, maxWidth)
        }
        val lineHeight = paint.descent() - paint.ascent()
        var baseline = top + (bottom - top - lineHeight * lines.size) / 2f - paint.ascent()
        lines.forEach { line ->
            val clipped = TextUtils.ellipsize(line, paint, maxWidth, TextUtils.TruncateAt.END)
            canvas.drawText(clipped, 0, clipped.length, left + CELL_PADDING, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun drawCentered(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: TextPaint,
    ) {
        if (text.isEmpty()) return
        val maxWidth = (right - left).coerceAtLeast(1f)
        val clipped = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END)
        val width = paint.measureText(clipped, 0, clipped.length)
        val x = left + (maxWidth - width) / 2f
        canvas.drawText(clipped, 0, clipped.length, x, centeredBaseline(top, bottom, paint), paint)
    }

    private fun drawLeft(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: TextPaint,
    ) {
        if (text.isEmpty()) return
        val maxWidth = (right - left).coerceAtLeast(1f)
        val clipped = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END)
        canvas.drawText(clipped, 0, clipped.length, left, centeredBaseline(top, bottom, paint), paint)
    }

    private fun drawRight(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        paint: TextPaint,
    ) {
        if (text.isEmpty()) return
        val maxWidth = (right - left).coerceAtLeast(1f)
        val clipped = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END)
        val width = paint.measureText(clipped, 0, clipped.length)
        canvas.drawText(
            clipped,
            0,
            clipped.length,
            right - width,
            centeredBaseline(top, bottom, paint),
            paint,
        )
    }

    private fun centeredBaseline(top: Float, bottom: Float, paint: TextPaint): Float =
        top + (bottom - top - (paint.descent() + paint.ascent())) / 2f

    private fun textPaint(
        size: Float,
        bold: Boolean = false,
        italic: Boolean = false,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        textSize = size
        color = Color.rgb(32, 33, 36)
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
     * S.No. is narrow, the name is the widest column, and the data columns get
     * their preferred width, scaled down together when the page is full.
     * Leftover space goes to the name, which is what a teacher reads first.
     */
    private fun columnWidths(section: ExamPdfSection): FloatArray {
        val usable = usableWidth()
        val dataCount = (section.headers.size - EXAM_PDF_FIXED_COLUMNS).coerceAtLeast(0)
        val availableForData = usable - SERIAL_WIDTH - ROLL_WIDTH - MIN_NAME_WIDTH
        val preferred = FloatArray(dataCount) { index ->
            when (section.kinds.getOrNull(index + EXAM_PDF_FIXED_COLUMNS)) {
                null -> SUBJECT_WIDTH
                ExportColumnKind.TOTAL_OBTAINED -> TOTAL_WIDTH
                ExportColumnKind.PERCENTAGE -> PERCENT_WIDTH
                ExportColumnKind.RESULT -> RESULT_WIDTH
                ExportColumnKind.RANK -> RANK_WIDTH
                else -> SUBJECT_WIDTH
            }
        }
        val preferredSum = preferred.sum()
        val dataScale = if (preferredSum > availableForData && preferredSum > 0f) {
            availableForData / preferredSum
        } else {
            1f
        }
        val dataWidths = FloatArray(dataCount) { index -> preferred[index] * dataScale }
        val nameWidth = (usable - SERIAL_WIDTH - ROLL_WIDTH - dataWidths.sum())
            .coerceAtLeast(MIN_NAME_WIDTH)

        val widths = FloatArray(section.headers.size) { index ->
            when {
                index == 0 -> SERIAL_WIDTH
                index == 1 -> ROLL_WIDTH
                index == 2 -> nameWidth
                else -> dataWidths.getOrElse(index - EXAM_PDF_FIXED_COLUMNS) { 0f }
            }
        }

        val total = widths.sum()
        if (total > usable && total > 0f) {
            val scale = usable / total
            for (index in widths.indices) widths[index] *= scale
        }
        return widths
    }

    private fun availableBodyHeight(): Float {
        val bodyTop = MARGIN + BANNER_HEIGHT + HEADER_HEIGHT
        val bodyBottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        return (bodyBottom - bodyTop).coerceAtLeast(1f)
    }

    private fun usableWidth(): Float = PAGE_WIDTH - 2 * MARGIN

    private fun monthLabel(examDate: Long?): String {
        if (examDate == null) return ""
        val month = MONTH_FORMAT.get()?.format(Date(examDate)).orEmpty().uppercase(Locale.US)
        return "MONTH- $month"
    }

    private fun classTeacherLine(name: String, designation: String): String {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return ""
        val cleanDesignation = designation.trim()
        return if (cleanDesignation.isEmpty()) {
            "Name of Class Teacher:- $cleanName"
        } else {
            "Name of Class Teacher:- $cleanName ($cleanDesignation)"
        }
    }

    private const val HEADER_TITLE = "Title"
    private const val HEADER_TEACHER_NAME = "Name of Subject Teacher"
    private const val HEADER_DESIGNATION = "Designation"
    private const val HEADER_EMPLOYEE_TYPE = "Employee Type"

    private val MONTH_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMMM", Locale.US)
    }

    private val NUMBER = Regex("""-?\d+(\.\d+)?%?|\d+\(\d+\)""")
}

/** The four printed banner rows, shared by every page of the PDF. */
private data class ExamPdfBanner(
    val examName: String,
    val className: String,
    val schoolName: String,
    val sessionLabel: String,
    val teacherName: String,
    val teacherDesignation: String,
    val examDate: Long?,
)
