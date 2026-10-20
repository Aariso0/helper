package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One teacher-authored observation on a student (glossary: "Feedback note"):
 * exactly one tag + optional one-line text + date (student-feedback card).
 * [tagLabel] is denormalized from the tag at write time so the note's history
 * survives deleting or renaming the tag ("never lose work").
 */
@Entity(
    tableName = "feedback_notes",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FeedbackTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("studentId"), Index("tagId"), Index("dateEpochDay")]
)
data class FeedbackNote(
    @PrimaryKey val id: String,
    val studentId: String,
    val tagId: String? = null,
    val tagLabel: String,
    val text: String? = null,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
