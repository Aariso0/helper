package com.example.helper.examsresults.export

/**
 * The one line a teacher reads at the top of an export. Both the workbook's
 * summary sheet and the printable PDF say the same thing, so a sheet and a
 * print-out of the same exam can be matched without opening either.
 */
internal object ExportHeading {
    fun title(examName: String, className: String): String {
        val exam = examName.trim()
        val classNameValue = className.trim()
        return when {
            exam.isEmpty() && classNameValue.isEmpty() -> "Summary"
            classNameValue.isEmpty() -> exam
            exam.isEmpty() -> "Class $classNameValue"
            else -> "$exam — Class $classNameValue"
        }
    }
}
