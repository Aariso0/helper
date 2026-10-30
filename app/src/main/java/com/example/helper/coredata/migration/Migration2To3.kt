package com.example.helper.coredata.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 adds the teacher's ordered export-column layout. Existing databases get
 * an empty table, which the export reads as "use the built-in defaults", so
 * nothing has to be seeded here.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `export_columns` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `formula` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
