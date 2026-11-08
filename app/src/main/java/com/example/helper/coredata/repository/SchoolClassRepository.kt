package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.SchoolClassDao
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.session.SessionScope
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Repository for Class rows (glossary: "Class"). The only door feature
 * modules have to class storage (core-data card: repository pattern).
 *
 * Every class belongs to a session, so [observeAll] follows the app-wide
 * viewing session held by [sessionScope]. That one line is why Today, the
 * roster, homework, the pickers and the importer all switch session together
 * without each ViewModel filtering by hand.
 */
class SchoolClassRepository(
    private val dao: SchoolClassDao,
    private val sessionScope: SessionScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<SchoolClass>> =
        sessionScope.selectedId.flatMapLatest { sessionId ->
            if (sessionId == null) flowOf(emptyList()) else observeAll(sessionId)
        }

    fun observeAll(sessionId: String): Flow<List<SchoolClass>> = dao.observeAll(sessionId)

    fun observeById(id: String): Flow<SchoolClass?> = dao.observeById(id)

    suspend fun getById(id: String): SchoolClass? = dao.getById(id)

    suspend fun getAll(): List<SchoolClass> = dao.getAll()

    suspend fun create(
        name: String,
        subject: String? = null,
        schoolName: String? = null,
        teacherName: String? = null,
        teacherDesignation: String? = null,
    ): SchoolClass {
        val now = System.currentTimeMillis()
        val schoolClass = SchoolClass(
            id = UUID.randomUUID().toString(),
            sessionId = sessionScope.requireSelectedId(),
            name = name.trim(),
            subject = subject?.trim()?.ifEmpty { null },
            schoolName = schoolName?.trim()?.ifEmpty { null },
            teacherName = teacherName?.trim()?.ifEmpty { null },
            teacherDesignation = teacherDesignation?.trim()?.ifEmpty { null },
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
