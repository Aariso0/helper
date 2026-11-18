package com.example.helper.examsresults.export

/**
 * Starting text size and minimum row height for a sheet slice. A class of 39
 * students prints at the comfortable base size; a longer class starts at the
 * readable floor. Content-based widths and wrapping may make a row taller than
 * this, and [ExamPdfRowPaginator] then continues on another page.
 *
 * Pure arithmetic so the fit is unit-tested without a device.
 */
internal data class ExamPdfFit(
    val textSize: Float,
    val nameTextSize: Float,
    val rowHeight: Float,
)

internal object ExamPdfLayout {
    const val BASE_TEXT_SIZE = 9f
    const val MIN_TEXT_SIZE = 6.5f
    const val MIN_NAME_TEXT_SIZE = 7f
    const val BASE_ROW_HEIGHT = 13.9f
    const val MIN_ROW_HEIGHT = 10f

    /** Shrinks just enough that [rowCount] plain rows fit [availableHeight]. */
    fun fit(availableHeight: Float, rowCount: Int): ExamPdfFit {
        if (availableHeight <= 0f) {
            return ExamPdfFit(MIN_TEXT_SIZE, MIN_NAME_TEXT_SIZE, MIN_ROW_HEIGHT)
        }
        val heightPerRow = if (rowCount <= 0) {
            BASE_ROW_HEIGHT
        } else {
            (availableHeight / rowCount).coerceIn(MIN_ROW_HEIGHT, BASE_ROW_HEIGHT)
        }
        val fraction = (heightPerRow - MIN_ROW_HEIGHT) / (BASE_ROW_HEIGHT - MIN_ROW_HEIGHT)
        val textSize = MIN_TEXT_SIZE + (BASE_TEXT_SIZE - MIN_TEXT_SIZE) * fraction
        val nameTextSize = MIN_NAME_TEXT_SIZE + (BASE_TEXT_SIZE - MIN_NAME_TEXT_SIZE) * fraction
        return ExamPdfFit(
            textSize = textSize,
            nameTextSize = nameTextSize.coerceAtLeast(textSize),
            rowHeight = heightPerRow,
        )
    }
}
