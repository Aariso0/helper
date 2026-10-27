package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_subjects",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId")],
)
data class ExamSubject(
    @PrimaryKey val id: String,
    val examId: String,
    val name: String,
    val maxMarks: Int,
    val passMarks: Int,
    val sortOrder: Int = 0,
)
