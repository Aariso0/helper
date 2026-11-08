package com.example.helper.coredata.session

import java.time.LocalDate

/**
 * Academic sessions in this app run April → March and are labelled by their
 * starting year: November 2026 belongs to "2026-27". One function so the
 * migration, the first-run seed and the "new session" suggestion agree.
 */
object AcademicYear {
    fun currentLabel(today: LocalDate = LocalDate.now()): String = labelForStart(startYear(today))

    /** The suggestion shown in the new-session dialog, e.g. 2026-27 → 2027-28. */
    fun nextLabel(label: String): String? {
        val start = label.substringBefore('-').toIntOrNull() ?: return null
        return labelForStart(start + 1)
    }

    private fun startYear(today: LocalDate): Int =
        if (today.monthValue >= 4) today.year else today.year - 1

    private fun labelForStart(start: Int): String =
        "$start-" + ((start + 1) % 100).toString().padStart(2, '0')
}
