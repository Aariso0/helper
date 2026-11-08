package com.example.helper.coredata.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v5 adds the honorific the printed subject-teacher sheet prints (`SMT.`, `SHRI`). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exam_subjects` ADD COLUMN `title` TEXT")
    }
}
