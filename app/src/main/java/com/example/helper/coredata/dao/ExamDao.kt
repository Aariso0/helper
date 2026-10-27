package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.Exam
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE classId = :classId ORDER BY examDate DESC, createdAt DESC")
    fun observeByClass(classId: String): Flow<List<Exam>>

    @Query("SELECT * FROM exams ORDER BY examDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id")
    fun observeById(id: String): Flow<Exam?>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun getById(id: String): Exam?

    @Upsert
    suspend fun upsert(exam: Exam)

    @Delete
    suspend fun delete(exam: Exam)
}
