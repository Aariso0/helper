package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One school session (glossary: "Session"), e.g. "2026-27". Every class — and
 * therefore every student, attendance record, homework item and exam — belongs
 * to exactly one session, so switching sessions switches the whole working set.
 *
 * [isCurrent] marks where **new** classes and exams belong; it is a separate
 * flag from whichever session the teacher is currently *viewing*. Only one row
 * may be current; the repository enforces that in a single UPDATE.
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["label"], unique = true)]
)
data class SchoolSession(
    @PrimaryKey val id: String,
    val label: String,
    val isCurrent: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
