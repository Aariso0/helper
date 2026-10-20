package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The four attendance statuses (README 4.1). Stored as name strings via Converters. */
enum class AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }

/**
 * One student's status for one class-day (glossary: "Attendance record").
 * Full-row storage: every student gets an explicit row every session
 * (see attendance card "Why this shape").
 * [dateEpochDay] is `LocalDate.toEpochDay()` — a calendar date with no
 * timezone attached; core-data never touches java.time itself.
 * [period] is reserved for a possible future period dimension (project
 * constraint); it stays null in the v0–v2 prototype.
 */
@Entity(
    tableName = "attendance_records",
    foreignKeys = [ForeignKey(
        entity = Student::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["studentId", "dateEpochDay"], unique = true),
        Index("classId"),
        Index("dateEpochDay"),
    ]
)
data class AttendanceRecord(
    @PrimaryKey val id: String,
    val classId: String,
    val studentId: String,
    val dateEpochDay: Long,
    val status: AttendanceStatus,
    val note: String? = null,
    val period: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
