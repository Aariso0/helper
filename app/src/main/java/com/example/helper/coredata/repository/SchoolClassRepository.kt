package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.SchoolClassDao
import com.example.helper.coredata.entity.SchoolClass
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Class rows (glossary: "Class"). The only door feature
 * modules have to class storage (core-data card: repository pattern).
 */
class SchoolClassRepository(private val dao: SchoolClassDao) {
    fun observeAll(): Flow<List<SchoolClass>> = dao.observeAll()

    fun observeById(id: String): Flow<SchoolClass?> = dao.observeById(id)

    suspend fun getById(id: String): SchoolClass? = dao.getById(id)

    suspend fun create(name: String, subject: String? = null, year: String? = null): SchoolClass {
        val now = System.currentTimeMillis()
        val schoolClass = SchoolClass(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            subject = subject?.trim()?.ifEmpty { null },
            year = year?.trim()?.ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(schoolClass)
        return schoolClass
    }

    suspend fun upsert(schoolClass: SchoolClass) =
        dao.upsert(schoolClass.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(schoolClass: SchoolClass) = dao.delete(schoolClass)
}
