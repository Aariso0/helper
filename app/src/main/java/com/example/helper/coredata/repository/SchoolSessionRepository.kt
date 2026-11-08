package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.SchoolSessionDao
import com.example.helper.coredata.entity.SchoolSession
import com.example.helper.coredata.session.AcademicYear
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The only door to session storage. "Current" is where new classes land;
 * which session is being *viewed* is a separate, app-wide choice kept by
 * `SessionScope`, never written here.
 */
class SchoolSessionRepository(private val dao: SchoolSessionDao) {
    fun observeAll(): Flow<List<SchoolSession>> = dao.observeAll()

    fun observeCurrent(): Flow<SchoolSession?> = dao.observeCurrent()

    suspend fun getAll(): List<SchoolSession> = dao.getAll()

    suspend fun getById(id: String): SchoolSession? = dao.getById(id)

    suspend fun getCurrent(): SchoolSession? = dao.getCurrent()

    /**
     * Guarantees at least one session and exactly one current row. A fresh
     * install has no session, so it gets the academic year of today; an old
     * database without a current marker (possible after a hand-edited restore)
     * gets its newest label marked current.
     */
    suspend fun ensureSession(): SchoolSession {
        dao.getCurrent()?.let { return it }
        val existing = dao.getAll()
        if (existing.isEmpty()) return create(label = AcademicYear.currentLabel(), makeCurrent = true)
        val newest = existing.first()
        dao.setCurrent(newest.id)
        return newest.copy(isCurrent = true)
    }

    suspend fun create(label: String, makeCurrent: Boolean = false): SchoolSession {
        val clean = label.trim()
        require(clean.isNotEmpty()) { "Session label is empty." }
        require(dao.getAll().none { it.label.equals(clean, ignoreCase = true) }) {
            "A session called \"$clean\" already exists."
        }
        val now = System.currentTimeMillis()
        val session = SchoolSession(
            id = UUID.randomUUID().toString(),
            label = clean,
            isCurrent = makeCurrent,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(session)
        if (makeCurrent) dao.setCurrent(session.id)
        return session
    }

    /** Marks [id] current; every other row stops being current in one UPDATE. */
    suspend fun setCurrent(id: String) = dao.setCurrent(id)
}
