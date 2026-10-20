package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A teacher-editable preset feedback tag (README 6 "FeedbackTag: label").
 * [sortOrder] gives the chip row a stable teacher-controlled order.
 */
@Entity(tableName = "feedback_tags")
data class FeedbackTag(
    @PrimaryKey val id: String,
    val label: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
