package com.example.helper.coredata.entity

import androidx.room.ColumnInfo
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
    /**
     * A deliberate "absent" for this subject. It behaves as 0 marks and fails
     * the subject, but the cell prints `ABT` instead of `0`. A mark that was
     * simply not entered has no row at all and is a different state.
     */
    @ColumnInfo(defaultValue = "0") val isAbsent: Boolean = false,
)
