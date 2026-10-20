package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.ContactLogDao
import com.example.helper.coredata.entity.ContactLogEntry
import com.example.helper.coredata.entity.Student
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for ContactLogEntries (glossary: "Contact log entry") — the
 * teacher logs a call/message they already received; nothing is sent.
 */
class ContactLogRepository(private val dao: ContactLogDao) {
    fun observeEntries(studentId: String? = null): Flow<List<ContactLogEntry>> =
        dao.observeEntries(studentId)

    suspend fun getEntriesInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<ContactLogEntry> =
        dao.getEntriesInRange(studentId, fromEpochDay, toEpochDay)

    suspend fun logEntry(student: Student, text: String, dateEpochDay: Long): ContactLogEntry {
        val now = System.currentTimeMillis()
        val entry = ContactLogEntry(
            id = UUID.randomUUID().toString(),
            studentId = student.id,
            studentName = student.name,
            text = text.trim(),
            dateEpochDay = dateEpochDay,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entry)
        return entry
    }

    suspend fun upsert(entry: ContactLogEntry) =
        dao.upsert(entry.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(entry: ContactLogEntry) = dao.delete(entry)
}
