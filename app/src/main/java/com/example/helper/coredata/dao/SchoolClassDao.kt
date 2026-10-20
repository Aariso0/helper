package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.SchoolClass
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolClassDao {
    @Query("SELECT * FROM classes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SchoolClass>>

    @Query("SELECT * FROM classes WHERE id = :id")
    fun observeById(id: String): Flow<SchoolClass?>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getById(id: String): SchoolClass?

    @Upsert
    suspend fun upsert(schoolClass: SchoolClass)

    @Upsert
    suspend fun upsertAll(schoolClasses: List<SchoolClass>)

    @Delete
    suspend fun delete(schoolClass: SchoolClass)
}
