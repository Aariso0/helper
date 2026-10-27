package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    /**
     * Register order: roll numbers sort **numerically** (1, 2, … 10, 11) so a
     * printed sheet matches the class register. A roll that is not a plain
     * number (e.g. "3-A") follows the numeric ones, alphabetically; students
     * without a roll come last. Stored as TEXT, hence the CAST.
     */
    @Query(
        "SELECT * FROM students WHERE classId = :classId " +
            "ORDER BY (rollNumber IS NULL), " +
            "CASE WHEN rollNumber GLOB '[0-9]*' AND rollNumber NOT GLOB '*[^0-9]*' THEN 0 ELSE 1 END, " +
            "CASE WHEN rollNumber GLOB '[0-9]*' AND rollNumber NOT GLOB '*[^0-9]*' " +
            "THEN CAST(rollNumber AS INTEGER) ELSE NULL END, " +
            "rollNumber COLLATE NOCASE, name COLLATE NOCASE",
    )
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
