package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.ExportColumnDao
import com.example.helper.coredata.entity.ExportColumn
import kotlinx.coroutines.flow.Flow

/**
 * Stores the teacher's export-column layout. An empty table is meaningful:
 * the caller falls back to the code defaults (`ExportColumnDefaults`), so a
 * fresh or migrated database needs no seeding step.
 */
class ExportColumnRepository(private val dao: ExportColumnDao) {
    fun observeAll(): Flow<List<ExportColumn>> = dao.observeAll()

    suspend fun getAll(): List<ExportColumn> = dao.getAll()

    suspend fun replaceAll(columns: List<ExportColumn>) {
        dao.replaceAll(columns.sortedBy { it.sortOrder })
    }

    suspend fun upsert(column: ExportColumn) {
        dao.upsert(column)
    }
}
