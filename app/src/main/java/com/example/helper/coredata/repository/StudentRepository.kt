package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.StudentDao
import com.example.helper.coredata.entity.Student
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Student rows (glossary: "Student"). Import (roster) commits
 * through [upsertAll]; photo paths are app-internal file paths only.
 */
class StudentRepository(private val dao: StudentDao) {
    fun observeInClass(classId: String): Flow<List<Student>> = dao.observeInClass(classId)

    fun observeAll(): Flow<List<Student>> = dao.observeAll()

    fun observeById(id: String): Flow<Student?> = dao.observeById(id)

    suspend fun getById(id: String): Student? = dao.getById(id)

    suspend fun create(
        classId: String,
        name: String,
        rollNumber: String? = null,
        parentContact: String? = null,
        photoPath: String? = null,
    ): Student {
        val now = System.currentTimeMillis()
        val student = Student(
            id = UUID.randomUUID().toString(),
            classId = classId,
            name = name.trim(),
            rollNumber = rollNumber?.trim()?.ifEmpty { null },
            parentContact = parentContact?.trim()?.ifEmpty { null },
            photoPath = photoPath,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(student)
        return student
    }

    suspend fun upsert(student: Student) =
        dao.upsert(student.copy(updatedAt = System.currentTimeMillis()))

    suspend fun upsertAll(students: List<Student>) {
        val now = System.currentTimeMillis()
        dao.upsertAll(students.map { it.copy(updatedAt = now) })
    }

    suspend fun setPhotoPath(studentId: String, photoPath: String?) {
        val student = dao.getById(studentId) ?: return
        dao.upsert(student.copy(photoPath = photoPath, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(student: Student) = dao.delete(student)
}
