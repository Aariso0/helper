package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.ExamMark
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamMarkDao {
    @Query("SELECT * FROM exam_marks WHERE examId = :examId")
    fun observeByExam(examId: String): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND studentId = :studentId")
    fun observeForStudent(examId: String, studentId: String): Flow<List<ExamMark>>

    @Query(
        "SELECT m.* FROM exam_marks AS m INNER JOIN exams AS e ON m.examId = e.id " +
            "WHERE e.classId = :classId",
    )
    fun observeByClass(classId: String): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND subjectId = :subjectId")
    fun observeForSubject(examId: String, subjectId: String): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId")
    suspend fun getByExam(examId: String): List<ExamMark>

    @Upsert
    suspend fun upsert(mark: ExamMark)

    @Upsert
    suspend fun upsertAll(marks: List<ExamMark>)

    @Delete
    suspend fun delete(mark: ExamMark)

    @Query("DELETE FROM exam_marks WHERE examId = :examId AND studentId = :studentId")
    suspend fun deleteForStudent(examId: String, studentId: String)

    @Query("DELETE FROM exam_marks WHERE examId = :examId AND isAbsent = 1")
    suspend fun deleteAbsentForExam(examId: String)
}
