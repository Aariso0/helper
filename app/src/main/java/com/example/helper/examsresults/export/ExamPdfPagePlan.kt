package com.example.helper.examsresults.export

/** Orientation of one printed page; `PdfDocument` pages may differ. */
internal enum class ExamPdfOrientation { PORTRAIT, LANDSCAPE }

/** What one printed page carries. */
internal enum class ExamPdfPageKind { MARKS, SUMMARY, SUBJECT_TEACHER }

internal data class ExamPdfPageSpec(
    val kind: ExamPdfPageKind,
    val orientation: ExamPdfOrientation,
)

/**
 * The page order of the three-page result export: every portrait marks page
 * first (a long class may continue), then the landscape analysis pages.
 *
 * The analysis pages only exist when there is something to analyse — a class
 * with no students or an exam with no subjects prints just the marks page and
 * no empty `RESULT- ANALYSIS` sheet.
 */
internal object ExamPdfPagePlan {
    const val PORTRAIT_WIDTH = 595f
    const val PORTRAIT_MARGIN = 24f

    // S.No. and Roll stay narrow, the name is the widest column, and a data
    // column may shrink to fit six subjects plus the four computed columns.
    const val SERIAL_WIDTH = 22f
    const val ROLL_WIDTH = 34f
    const val MIN_NAME_WIDTH = 130f
    const val MIN_DATA_WIDTH = 32f

    /** How many data columns share one portrait page without a second slice. */
    fun portraitDataColumns(): Int {
        val usable = PORTRAIT_WIDTH - 2 * PORTRAIT_MARGIN
        val availableForData = usable - SERIAL_WIDTH - ROLL_WIDTH - MIN_NAME_WIDTH
        return (availableForData / MIN_DATA_WIDTH).toInt().coerceAtLeast(1)
    }

    fun build(marksPageCount: Int, hasAnalysis: Boolean): List<ExamPdfPageSpec> {
        val marks = List(marksPageCount.coerceAtLeast(1)) {
            ExamPdfPageSpec(ExamPdfPageKind.MARKS, ExamPdfOrientation.PORTRAIT)
        }
        if (!hasAnalysis) return marks
        return marks + listOf(
            ExamPdfPageSpec(ExamPdfPageKind.SUMMARY, ExamPdfOrientation.LANDSCAPE),
            ExamPdfPageSpec(ExamPdfPageKind.SUBJECT_TEACHER, ExamPdfOrientation.LANDSCAPE),
        )
    }
}
