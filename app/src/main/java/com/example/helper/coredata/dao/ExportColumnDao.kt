package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.helper.coredata.entity.ExportColumn
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExportColumnDao {
    @Query("SELECT * FROM export_columns ORDER BY sortOrder ASC, id ASC")
    abstract fun observeAll(): Flow<List<ExportColumn>>

    @Query("SELECT * FROM export_columns ORDER BY sortOrder ASC, id ASC")
    abstract suspend fun getAll(): List<ExportColumn>

    @Upsert
    abstract suspend fun upsert(column: ExportColumn)

    @Upsert
    abstract suspend fun upsertAll(columns: List<ExportColumn>)

    @Delete
    abstract suspend fun delete(column: ExportColumn)

    @Query("DELETE FROM export_columns")
    abstract suspend fun deleteAll()

    /**
     * Replaces the whole ordered list in one transaction: reordering swaps
     * `sortOrder` on every row, so a partial write would leave duplicates.
     */
    @Transaction
    open suspend fun replaceAll(columns: List<ExportColumn>) {
        deleteAll()
        upsertAll(columns)
    }
}
