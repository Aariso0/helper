package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY (rollNumber IS NULL), rollNumber, name COLLATE NOCASE")
    fun observeInClass(classId: String): Flow<List<Student>>

    @Query("SELECT * FROM students ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: String): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: String): Student?

    @Upsert
    suspend fun upsert(student: Student)

    @Upsert
    suspend fun upsertAll(students: List<Student>)

    @Delete
    suspend fun delete(student: Student)
}
