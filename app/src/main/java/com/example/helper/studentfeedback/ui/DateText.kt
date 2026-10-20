package com.example.helper.studentfeedback.ui

import java.time.LocalDate

/** UI-language English-only (project constraint): compact relative dates. */
internal fun Long.epochDayToDisplayDate(): String {
    val date = LocalDate.ofEpochDay(this)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    }
}
