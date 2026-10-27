package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_marks",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExamSubject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("examId"),
        Index("subjectId"),
        Index("studentId"),
        Index(value = ["examId", "studentId", "subjectId"], unique = true),
    ],
)
data class ExamMark(
    @PrimaryKey val id: String,
    val examId: String,
    val subjectId: String,
    val studentId: String,
    val marksObtained: Double = 0.0,
)
