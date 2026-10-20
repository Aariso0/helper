package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.ContactLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactLogDao {
    @Query(
        "SELECT * FROM contact_log_entries " +
            "WHERE (:studentId IS NULL OR studentId = :studentId) " +
            "ORDER BY dateEpochDay DESC, createdAt DESC"
    )
    fun observeEntries(studentId: String? = null): Flow<List<ContactLogEntry>>

    @Query("SELECT * FROM contact_log_entries WHERE studentId = :studentId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay DESC")
    suspend fun getEntriesInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<ContactLogEntry>

    @Upsert
    suspend fun upsert(entry: ContactLogEntry)

    @Delete
    suspend fun delete(entry: ContactLogEntry)
}
