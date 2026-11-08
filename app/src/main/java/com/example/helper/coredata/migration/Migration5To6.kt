package com.example.helper.coredata.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v6 adds the user-entered month label printed on exported result sheets. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exams` ADD COLUMN `month` TEXT")
    }
}
