package com.example.helper.examsresults.export

/**
 * How much of the page one sheet slice gets after shrinking. A class of 39
 * students prints at the comfortable base size; a longer class shrinks the
 * text and row height down to a readable floor and then continues on another
 * page rather than clipping a student or dropping one.
 *
 * Pure arithmetic so the fit is unit-tested without a device.
 */
internal data class ExamPdfFit(
    val textSize: Float,
    val nameTextSize: Float,
    val rowHeight: Float,
    val rowsPerPage: Int,
)

internal object ExamPdfLayout {
    const val BASE_TEXT_SIZE = 9f
    const val MIN_TEXT_SIZE = 6.5f
    const val MIN_NAME_TEXT_SIZE = 7f
    const val BASE_ROW_HEIGHT = 13.9f
    const val MIN_ROW_HEIGHT = 10f

    /**
     * Shrinks just enough that [rowCount] rows fit [availableHeight]. When the
     * floor is reached, [ExamPdfFit.rowsPerPage] is smaller than the class and
     * the caller continues on a new page with the same banner and header.
     */
    fun fit(availableHeight: Float, rowCount: Int): ExamPdfFit {
        if (availableHeight <= 0f) {
            return ExamPdfFit(MIN_TEXT_SIZE, MIN_NAME_TEXT_SIZE, MIN_ROW_HEIGHT, 1)
        }
        val heightPerRow = if (rowCount <= 0) {
            BASE_ROW_HEIGHT
        } else {
            (availableHeight / rowCount).coerceIn(MIN_ROW_HEIGHT, BASE_ROW_HEIGHT)
        }
        val fraction = (heightPerRow - MIN_ROW_HEIGHT) / (BASE_ROW_HEIGHT - MIN_ROW_HEIGHT)
        val textSize = MIN_TEXT_SIZE + (BASE_TEXT_SIZE - MIN_TEXT_SIZE) * fraction
        val nameTextSize = MIN_NAME_TEXT_SIZE + (BASE_TEXT_SIZE - MIN_NAME_TEXT_SIZE) * fraction
        val rowsPerPage = (availableHeight / heightPerRow).toInt().coerceAtLeast(1)
        return ExamPdfFit(
            textSize = textSize,
            nameTextSize = nameTextSize.coerceAtLeast(textSize),
            rowHeight = heightPerRow,
            rowsPerPage = rowsPerPage,
        )
    }
}
