package com.example.helper.examsresults.export

/**
 * A column's measured width needs: [maxWidth] is the widest unwrapped cell,
 * [minWidth] the widest single word. Both already include horizontal padding.
 * A fixed column has equal values because it must never wrap.
 */
internal data class ExamPdfColumnSpec(
    val maxWidth: Float,
    val minWidth: Float,
    val flexible: Boolean,
)

internal data class ExamPdfColumnLayout(
    val widths: FloatArray,
    /** True when even every word side by side cannot fit at this font size. */
    val needsSmallerFont: Boolean,
)

/**
 * Pure page-width arithmetic for the printed tables. The caller measures every
 * header and cell with the font that will draw it; this only decides how the
 * page is shared. Kept free of Android imports so the allocation is testable.
 */
internal object ExamPdfColumnSizer {
    fun size(specs: List<ExamPdfColumnSpec>, available: Float): ExamPdfColumnLayout {
        if (specs.isEmpty()) return ExamPdfColumnLayout(FloatArray(0), false)
        val max = FloatArray(specs.size) { specs[it].maxWidth }
        val min = FloatArray(specs.size) { specs[it].minWidth }
        val maxSum = max.sum()
        val minSum = min.sum()
        if (available <= 0f) return ExamPdfColumnLayout(FloatArray(specs.size) { 0f }, true)

        if (maxSum <= available) {
            val widths = max.copyOf()
            val flexible = specs.indices.filter { specs[it].flexible }
            if (flexible.isNotEmpty()) {
                val share = (available - maxSum) / flexible.size
                flexible.forEach { widths[it] += share }
            }
            return ExamPdfColumnLayout(widths, false)
        }

        if (minSum >= available) {
            // Caller shrinks the font and re-measures; at the floor these scaled
            // widths stand and the wrapper breaks inside words as a last resort.
            val scale = available / minSum
            return ExamPdfColumnLayout(FloatArray(specs.size) { min[it] * scale }, true)
        }

        val room = available - minSum
        val growth = FloatArray(specs.size) { (max[it] - min[it]).coerceAtLeast(0f) }
        val growthSum = growth.sum()
        val widths = FloatArray(specs.size) { index ->
            if (growthSum <= 0f) {
                min[index] + room / specs.size
            } else {
                min[index] + room * growth[index] / growthSum
            }
        }
        return ExamPdfColumnLayout(widths, false)
    }
}

/** Places already-measured rows on pages; each page repeats the table header. */
internal object ExamPdfRowPaginator {
    fun <T> paginate(
        items: List<T>,
        heightOf: (T) -> Float,
        availableHeight: Float,
    ): List<List<T>> {
        val pages = mutableListOf<List<T>>()
        var current = mutableListOf<T>()
        var used = 0f
        items.forEach { item ->
            val height = heightOf(item)
            if (current.isNotEmpty() && used + height > availableHeight) {
                pages.add(current)
                current = mutableListOf()
                used = 0f
            }
            current.add(item)
            used += height
        }
        if (current.isNotEmpty()) pages.add(current)
        return pages.ifEmpty { listOf(emptyList()) }
    }
}

/**
 * Wraps with the caller's real measurement function (a `TextPaint.measureText`
 * in the writer), never a character-count guess. A word too wide on its own is
 * split by characters so no line can cross the cell border.
 */
internal object ExamPdfTextWrap {
    fun wrap(text: String, measure: (String) -> Float, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        if (measure(text) <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        var line = ""
        text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
            if (line.isEmpty()) {
                val pieces = splitLongWord(word, measure, maxWidth)
                lines.addAll(pieces.dropLast(1))
                line = pieces.last()
                return@forEach
            }
            if (measure("$line $word") <= maxWidth) {
                line = "$line $word"
            } else {
                lines.add(line)
                val pieces = splitLongWord(word, measure, maxWidth)
                lines.addAll(pieces.dropLast(1))
                line = pieces.last()
            }
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.ifEmpty { listOf("") }
    }

    private fun splitLongWord(word: String, measure: (String) -> Float, maxWidth: Float): List<String> {
        if (measure(word) <= maxWidth) return listOf(word)
        val pieces = mutableListOf<String>()
        var current = ""
        word.forEach { character ->
            val candidate = current + character
            if (current.isNotEmpty() && measure(candidate) > maxWidth) {
                pieces.add(current)
                current = character.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) pieces.add(current)
        return pieces.ifEmpty { listOf(word) }
    }
}
