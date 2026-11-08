package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.SchoolSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolSessionDao {
    /** Newest label first — the dropdown reads that way. */
    @Query("SELECT * FROM sessions ORDER BY label COLLATE NOCASE DESC")
    fun observeAll(): Flow<List<SchoolSession>>

    @Query("SELECT * FROM sessions WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<SchoolSession?>

    @Query("SELECT * FROM sessions ORDER BY label COLLATE NOCASE DESC")
    suspend fun getAll(): List<SchoolSession>

    @Query("SELECT * FROM sessions WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): SchoolSession?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SchoolSession?

    /**
     * One statement sets exactly one current row: `isCurrent = (id = :id)` is
     * 1 for the target and 0 for every other row, atomically.
     */
    @Query("UPDATE sessions SET isCurrent = (id = :id)")
    suspend fun setCurrent(id: String)

    @Upsert
    suspend fun upsert(session: SchoolSession)
}
