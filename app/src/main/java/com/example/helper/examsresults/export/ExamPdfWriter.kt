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

    /** No table prints below this size; long cells wrap instead. */
    private const val MIN_FONT_SIZE = ExamPdfLayout.MIN_TEXT_SIZE
    private const val FONT_STEP = 0.5f

    private val BANNER_BLUE = Color.parseColor(ExportPalette.BANNER_BLUE)
    private val HEADER_YELLOW = Color.parseColor(ExportPalette.HEADER_YELLOW)
    private val SUBJECT_CYAN = Color.parseColor(ExportPalette.SUBJECT_CYAN)
    private val TITLE_CREAM = Color.parseColor(ExportPalette.TITLE_CREAM)
    private val RANK_1 = Color.parseColor(ExportPalette.RANK_1)
    private val RANK_2 = Color.parseColor(ExportPalette.RANK_2)
    private val RANK_3 = Color.parseColor(ExportPalette.RANK_3)
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
        month: String = "",
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
            month = month,
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
        val measured = sections.map { section ->
            measureSection(section, usableWidth(), fit.textSize, fit.rowHeight) { index -> index == 2 }
        }
        val pages = measured.flatMap { section ->
            val bodyHeight = availableBodyHeight() - (section.headerHeight - HEADER_HEIGHT)
            ExamPdfRowPaginator.paginate(section.rows, { it.height }, bodyHeight)
                .map { rows -> section to rows }
        }

        val plan = ExamPdfPagePlan.build(marksPageCount = pages.size, hasAnalysis = stats != null)

        pages.forEachIndexed { index, (section, rows) ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create(),
            )
            drawPage(
                canvas = page.canvas,
                section = section.section,
                rows = rows,
                widths = section.widths,
                textSize = section.textSize,
                headerHeight = section.headerHeight,
                banner = banner,
                minRowHeight = fit.rowHeight,
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
        rows: List<WrappedRow>,
        widths: FloatArray,
        textSize: Float,
        headerHeight: Float,
        banner: ExamPdfBanner,
        minRowHeight: Float,
        pageNumber: Int,
        pageCount: Int,
    ) {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        drawBanner(canvas, banner, left, right, MARGIN)

        val headerTop = MARGIN + BANNER_HEIGHT
        val headerBottom = headerTop + headerHeight
        drawTableHeader(canvas, section, widths, headerTop, headerBottom, headerHeight, textSize)

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
                headerBottom + minRowHeight,
                textPaint(textSize),
            )
            return
        }

        var top = headerBottom
        rows.forEach { row ->
            drawDataRow(canvas, section, row, widths, top, textSize)
            top += row.height
        }
        canvas.drawLine(left, top, right, top, BORDER_PAINT)
    }

    private fun drawBanner(
        canvas: Canvas,
        banner: ExamPdfBanner,
        left: Float,
        right: Float,
        top: Float,
        statementTitle: String = "CONSOLIDATED MARKS - STATEMENT",
    ) {
        val width = right - left

        // School name on banner blue.
        val row1Bottom = top + BANNER_ROW_1
        FILL.color = BANNER_BLUE
        canvas.drawRect(left, top, right, row1Bottom, FILL)
        val schoolName = banner.schoolName.trim()
        var schoolSize = 12f
        val schoolPaint = textPaint(schoolSize, bold = true)
        val schoolRoom = width - 2 * CELL_PADDING
        while (schoolSize > MIN_FONT_SIZE && schoolPaint.measureText(schoolName) > schoolRoom) {
            schoolSize = (schoolSize - FONT_STEP).coerceAtLeast(MIN_FONT_SIZE)
            schoolPaint.textSize = schoolSize
        }
        drawCentered(
            canvas,
            schoolName,
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
        val middle = listOf(effectiveMonthLabel(banner.month, banner.examDate), banner.sessionLabel.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        // The month + session block is centred on the page, not between the
        // exam name and the class, as the paper sheet has it.
        drawCentered(
            canvas,
            middle,
            left + CELL_PADDING,
            right - CELL_PADDING,
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
            statementTitle,
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
        drawBanner(canvas, banner, left, right, MARGIN, statementTitle = "")

        val headers = listOf(ExamSummarySheet.SUBJECT_HEADER) +
            ExamSummarySheet.METRIC_HEADERS + ExamSummarySheet.CLASS_WISE_HEADERS
        val cells = stats.subjects.map { subject ->
            val row = mutableListOf(
                subject.name,
                subject.strength.toString(),
                subject.appeared.toString(),
                subject.passed.toString(),
                percentLabel(subject.passShare),
                percentLabel(subject.averageShare),
            )
            subject.bands7.forEach { row.add(it.toString()) }
            repeat(ExamSummarySheet.CLASS_WISE_HEADERS.size) { row.add("") }
            row
        }.toMutableList()
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
        cells.add(overallCells)

        val section = ExamPdfSection(
            headers = headers,
            subHeaders = List(headers.size) { "" },
            kinds = List(headers.size) { null },
            rows = cells,
        )
        val measured = measureSection(
            section, right - left, 7.5f, ANALYSIS_ROW_HEIGHT,
            boldRow = { it == section.rows.lastIndex },
        ) { index -> index == 0 }
        val widths = measured.widths
        val titleTop = MARGIN + BANNER_HEIGHT
        val titleBottom = titleTop + ANALYSIS_TITLE_HEIGHT
        val headerTop = titleBottom
        val headerBottom = headerTop + measured.headerHeight
        val splitX = columnX(left, widths, ExamSummarySheet.CLASS_WISE_COLUMN - 1)

        drawTitleCell(canvas, "RESULT- ANALYSIS", left, splitX, titleTop, titleBottom)
        drawTitleCell(canvas, ExamSummarySheet.CLASS_WISE_TITLE, splitX, right, titleTop, titleBottom)

        val headerPaint = textPaint(measured.textSize, bold = true)
        drawAnalysisHeader(
            canvas, headers[0], left, columnX(left, widths, 1),
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
        measured.rows.forEachIndexed { index, row ->
            drawAnalysisRow(
                canvas, row, widths, left, top, summaryFills(), measured.textSize,
                bold = index == measured.rows.lastIndex,
            )
            top += row.height
        }

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
        drawBanner(canvas, banner, left, right, MARGIN, statementTitle = "")

        val headers = mutableListOf(ExamSubjectTeacherSheet.SUBJECT_HEADER)
        headers.addAll(ExamSubjectTeacherSheet.METRIC_HEADERS)
        headers.addAll(ExamSubjectTeacherSheet.BAND_LABELS)
        headers.addAll(
            listOf(HEADER_TITLE, HEADER_TEACHER_NAME, HEADER_DESIGNATION, HEADER_EMPLOYEE_TYPE),
        )
        val cells = stats.subjects.mapIndexed { index, subject ->
            subjectTeacherCells(
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
        }.toMutableList()
        val overall = stats.overall
        cells.add(
            subjectTeacherCells(
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
            ),
        )

        val section = ExamPdfSection(
            headers = headers,
            subHeaders = List(headers.size) { "" },
            kinds = List(headers.size) { null },
            rows = cells,
        )
        val measured = measureSection(
            section, right - left, 7.5f, ANALYSIS_ROW_HEIGHT,
            boldRow = { it == section.rows.lastIndex },
        ) { index -> index == 0 || index >= ExamSubjectTeacherSheet.TITLE_COLUMN - 1 }
        val widths = measured.widths
        val titleTop = MARGIN + BANNER_HEIGHT
        val titleBottom = titleTop + ANALYSIS_TITLE_HEIGHT
        val headerTop = titleBottom
        val headerBottom = headerTop + measured.headerHeight

        drawTitleCell(canvas, "RESULT- ANALYSIS", left, right, titleTop, titleBottom)

        val headerPaint = textPaint(measured.textSize, bold = true)
        headers.forEachIndexed { index, header ->
            drawAnalysisHeader(
                canvas, header, columnX(left, widths, index), columnX(left, widths, index + 1),
                headerTop, headerBottom, headerPaint, fill = null,
            )
        }

        var top = headerBottom
        measured.rows.forEachIndexed { index, row ->
            drawAnalysisRow(
                canvas, row, widths, left, top, subjectTeacherFills(), measured.textSize,
                bold = index == measured.rows.lastIndex,
            )
            top += row.height
        }

        drawClassWiseBlock(canvas, left, top + 10f, stats.overall)
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
        row: WrappedRow,
        widths: FloatArray,
        left: Float,
        top: Float,
        fills: List<Int?>,
        textSize: Float,
        bold: Boolean = false,
    ) {
        val bottom = top + row.height
        var x = left
        row.cells.indices.forEach { index ->
            val width = widths.getOrElse(index) { 0f }
            val right = x + width
            fills.getOrNull(index)?.let { colour ->
                FILL.color = colour
                canvas.drawRect(x, top, right, bottom, FILL)
            }
            val paint = textPaint(textSize, bold = bold)
            val lineHeight = paint.descent() - paint.ascent()
            var baseline = top + CELL_PADDING - paint.ascent()
            row.lines.getOrElse(index) { listOf("") }.forEach { line ->
                val drawX = if (isNumeric(line)) {
                    right - CELL_PADDING - paint.measureText(line)
                } else {
                    x + CELL_PADDING
                }
                canvas.drawText(line, drawX, baseline, paint)
                baseline += lineHeight
            }
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
        val valuePaint = textPaint(6.5f)
        val valueRow = WrappedRow(
            cells = values,
            lines = values.map { value ->
                ExamPdfTextWrap.wrap(
                    value, { valuePaint.measureText(it) },
                    (cellWidth - 2 * CELL_PADDING).coerceAtLeast(1f),
                )
            },
            height = ANALYSIS_ROW_HEIGHT,
        )
        drawAnalysisRow(
            canvas,
            valueRow,
            FloatArray(columns) { cellWidth },
            left,
            headerBottom,
            List(columns) { TITLE_CREAM },
            6.5f,
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

    private fun drawTableHeader(
        canvas: Canvas,
        section: ExamPdfSection,
        widths: FloatArray,
        top: Float,
        bottom: Float,
        headerHeight: Float,
        textSize: Float,
    ) {
        val middle = top + headerHeight / 2f
        val paint = textPaint(textSize, bold = true)
        var x = MARGIN
        section.headers.forEachIndexed { index, header ->
            val width = widths.getOrElse(index) { 0f }
            val right = x + width
            val identifying = index < EXAM_PDF_FIXED_COLUMNS
            val kind = section.kinds.getOrNull(index)
            val subject = !identifying && kind == null
            // S.No., Roll No., Student Name and every computed column span both
            // header rows; only the subject columns show their maximum beneath.
            val merged = index <= 2 || (!identifying && kind != null)

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
        row: WrappedRow,
        widths: FloatArray,
        top: Float,
        textSize: Float,
    ) {
        val bottom = top + row.height
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
            val raw = row.cells.getOrElse(index) { "" }
            if (kind == ExportColumnKind.RANK) {
                when (raw) {
                    "1" -> { FILL.color = RANK_1; canvas.drawRect(x, top, right, bottom, FILL) }
                    "2" -> { FILL.color = RANK_2; canvas.drawRect(x, top, right, bottom, FILL) }
                    "3" -> { FILL.color = RANK_3; canvas.drawRect(x, top, right, bottom, FILL) }
                }
            }
            val paint = cellTextPaint(textSize, raw)
            val lineHeight = paint.descent() - paint.ascent()
            var baseline = top + CELL_PADDING - paint.ascent()
            row.lines.getOrElse(index) { listOf("") }.forEach { line ->
                val drawX = if (isNumeric(line)) {
                    right - CELL_PADDING - paint.measureText(line)
                } else {
                    x + CELL_PADDING
                }
                canvas.drawText(line, drawX, baseline, paint)
                baseline += lineHeight
            }
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
        val lines = ExamPdfTextWrap.wrap(text, { paint.measureText(it) }, maxWidth)
        val lineHeight = paint.descent() - paint.ascent()
        var baseline = top + (bottom - top - lineHeight * lines.size) / 2f - paint.ascent()
        lines.forEach { line ->
            canvas.drawText(line, left + CELL_PADDING, baseline, paint)
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

    /** Matches the paint [drawDataRow] uses, so measuring cannot understate a "Fail" cell. */
    private fun cellTextPaint(size: Float, cell: String, bold: Boolean = false): TextPaint {
        val fail = isFail(cell)
        return textPaint(size, bold = bold || fail, italic = fail)
    }

    /**
     * Measures every header and cell with the font that will draw it, then
     * shares the page by the pure sizer. If even the widest words cannot fit,
     * the font steps down to the floor and the wrapper splits long words.
     */
    private fun measureSection(
        section: ExamPdfSection,
        availableWidth: Float,
        startSize: Float,
        minRowHeight: Float,
        boldRow: (Int) -> Boolean = { false },
        flexible: (Int) -> Boolean,
    ): MeasuredSection {
        var size = startSize
        lateinit var specs: List<ExamPdfColumnSpec>
        lateinit var layout: ExamPdfColumnLayout
        while (true) {
            specs = measureSpecs(section, size, boldRow, flexible)
            layout = ExamPdfColumnSizer.size(specs, availableWidth)
            if (!layout.needsSmallerFont || size <= MIN_FONT_SIZE) break
            size = (size - FONT_STEP).coerceAtLeast(MIN_FONT_SIZE)
        }

        val widths = layout.widths
        val rows = section.rows.mapIndexed { rowIndex, cells ->
            val lines = cells.mapIndexed { index, cell ->
                val inner = (widths.getOrElse(index) { 0f } - 2 * CELL_PADDING).coerceAtLeast(1f)
                val paint = cellTextPaint(size, cell, boldRow(rowIndex))
                ExamPdfTextWrap.wrap(cell, { paint.measureText(it) }, inner)
            }
            val paint = cellTextPaint(size, "", boldRow(rowIndex))
            val lineHeight = paint.descent() - paint.ascent()
            val height = (lines.maxOf { it.size } * lineHeight + CELL_PADDING)
                .coerceAtLeast(minRowHeight)
            WrappedRow(cells, lines, height)
        }

        val headerPaint = textPaint(size, bold = true)
        val headerLineHeight = headerPaint.descent() - headerPaint.ascent()
        var headerLines = 1
        section.headers.indices.forEach { index ->
            val inner = (widths.getOrElse(index) { 0f } - 2 * CELL_PADDING).coerceAtLeast(1f)
            val heading = ExamPdfTextWrap.wrap(
                section.headers.getOrElse(index) { "" }, { headerPaint.measureText(it) }, inner,
            ).size
            val sub = ExamPdfTextWrap.wrap(
                section.subHeaders.getOrElse(index) { "" }, { headerPaint.measureText(it) }, inner,
            ).size
            val identifying = index < EXAM_PDF_FIXED_COLUMNS
            val merged = index <= 2 || (!identifying && section.kinds.getOrNull(index) != null)
            headerLines = maxOf(headerLines, if (merged) maxOf(heading, sub) else heading + sub)
        }
        val headerHeight = maxOf(
            HEADER_HEIGHT,
            headerLines * headerLineHeight + 2 * CELL_PADDING,
        )
        return MeasuredSection(section, widths, size, rows, headerHeight)
    }

    private fun measureSpecs(
        section: ExamPdfSection,
        textSize: Float,
        boldRow: (Int) -> Boolean,
        flexible: (Int) -> Boolean,
    ): List<ExamPdfColumnSpec> {
        val headerPaint = textPaint(textSize, bold = true)
        return section.headers.indices.map { index ->
            val measured = mutableListOf<Pair<String, TextPaint>>()
            measured.add(section.headers.getOrElse(index) { "" } to headerPaint)
            measured.add(section.subHeaders.getOrElse(index) { "" } to headerPaint)
            section.rows.forEachIndexed { rowIndex, row ->
                val cell = row.getOrElse(index) { "" }
                measured.add(cell to cellTextPaint(textSize, cell, boldRow(rowIndex)))
            }
            val maxWidth = measured.maxOf { (text, paint) -> paint.measureText(text) } + 2 * CELL_PADDING
            val isFlexible = flexible(index)
            val minWidth = if (isFlexible) {
                (measured.flatMap { (text, paint) ->
                    text.split(' ').filter { it.isNotEmpty() }.map { paint.measureText(it) }
                }.maxOrNull() ?: 0f) + 2 * CELL_PADDING
            } else {
                maxWidth
            }
            ExamPdfColumnSpec(maxWidth, minWidth, isFlexible)
        }
    }

    /** Numbers and percentages line up on the right; words on the left. */
    private fun isNumeric(value: String): Boolean = NUMBER.matches(value)

    private fun isFail(value: String): Boolean = value.equals("Fail", ignoreCase = true)

    private fun availableBodyHeight(): Float {
        val bodyTop = MARGIN + BANNER_HEIGHT + HEADER_HEIGHT
        val bodyBottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        return (bodyBottom - bodyTop).coerceAtLeast(1f)
    }

    private fun usableWidth(): Float = PAGE_WIDTH - 2 * MARGIN

    private fun effectiveMonthLabel(month: String, examDate: Long?): String {
        val explicit = month.trim()
        val value = if (explicit.isNotEmpty()) {
            explicit
        } else if (examDate != null) {
            MONTH_FORMAT.get()?.format(Date(examDate)).orEmpty().uppercase(Locale.US)
        } else {
            ""
        }
        return if (value.isEmpty()) "" else "MONTH- $value"
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

/** A row already wrapped to its measured column widths, with its drawn height. */
private data class WrappedRow(
    val cells: List<String>,
    val lines: List<List<String>>,
    val height: Float,
)

/** A section measured with the font that will draw it, ready to paginate. */
private data class MeasuredSection(
    val section: ExamPdfSection,
    val widths: FloatArray,
    val textSize: Float,
    val rows: List<WrappedRow>,
    val headerHeight: Float,
)

/** The four printed banner rows, shared by every page of the PDF. */
private data class ExamPdfBanner(
    val examName: String,
    val className: String,
    val schoolName: String,
    val sessionLabel: String,
    val teacherName: String,
    val teacherDesignation: String,
    val examDate: Long?,
    val month: String = "",
)
