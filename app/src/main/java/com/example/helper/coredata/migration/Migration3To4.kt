package com.example.helper.coredata.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.coredata.session.AcademicYear
import java.util.UUID

/**
 * v4 introduces school sessions and the printed-sheet fields.
 *
 * Existing data is not guessed at: every distinct `classes.year` becomes a
 * session label, and rows without a year land in the academic year of today.
 * The class table is rebuilt because its unique index and foreign key change
 * (`name` alone → `(sessionId, name)`), and the `year` column is dropped once
 * its value lives in `sessions`.
 *
 * Foreign-key enforcement is off during Room migrations (the app never enables
 * it), so dropping the old `classes` table cannot cascade into students/exams;
 * its rows are copied into `classes_new` first.
 */
/** The marker row that means "the teacher deleted every column". */
private const val CLEARED_LAYOUT_ID = "builtin-layout-cleared"

private data class OldClass(
    val id: String,
    val name: String,
    val subject: String?,
    val year: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `isCurrent` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sessions_label` ON `sessions` (`label`)"
        )

        val oldClasses = mutableListOf<OldClass>()
        db.query(
            "SELECT `id`, `name`, `subject`, `year`, `createdAt`, `updatedAt` FROM `classes`"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                oldClasses += OldClass(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    subject = if (cursor.isNull(2)) null else cursor.getString(2),
                    year = if (cursor.isNull(3)) null else cursor.getString(3),
                    createdAt = cursor.getLong(4),
                    updatedAt = cursor.getLong(5),
                )
            }
        }

        val fallbackLabel = AcademicYear.currentLabel()
        fun labelOf(old: OldClass): String =
            old.year?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackLabel

        val labels = oldClasses.map(::labelOf).distinct()
        val currentLabel = when {
            labels.contains(fallbackLabel) -> fallbackLabel
            else -> labels.maxWithOrNull(compareBy { it.lowercase() })
        }
        val idByLabel = labels.associateWith { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()

        labels.forEach { label ->
            db.execSQL(
                "INSERT INTO `sessions` (`id`, `label`, `isCurrent`, `createdAt`, `updatedAt`) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    idByLabel.getValue(label), label, if (label == currentLabel) 1 else 0, now, now,
                ),
            )
        }

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `classes_new` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `subject` TEXT,
                `schoolName` TEXT,
                `teacherName` TEXT,
                `teacherDesignation` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        oldClasses.forEach { old ->
            db.execSQL(
                "INSERT INTO `classes_new` " +
                    "(`id`, `sessionId`, `name`, `subject`, `schoolName`, `teacherName`, " +
                    "`teacherDesignation`, `createdAt`, `updatedAt`) " +
                    "VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?, ?)",
                arrayOf<Any?>(
                    old.id,
                    idByLabel.getValue(labelOf(old)),
                    old.name,
                    old.subject,
                    old.createdAt,
                    old.updatedAt,
                ),
            )
        }

        db.execSQL("DROP INDEX IF EXISTS `index_classes_name`")
        db.execSQL("DROP TABLE `classes`")
        db.execSQL("ALTER TABLE `classes_new` RENAME TO `classes`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_classes_sessionId` ON `classes` (`sessionId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_classes_sessionId_name` " +
                "ON `classes` (`sessionId`, `name`)"
        )

        db.execSQL("ALTER TABLE `exam_subjects` ADD COLUMN `teacherName` TEXT")
        db.execSQL("ALTER TABLE `exam_subjects` ADD COLUMN `teacherDesignation` TEXT")
        db.execSQL("ALTER TABLE `exam_subjects` ADD COLUMN `employeeType` TEXT")
        db.execSQL(
            "ALTER TABLE `exam_marks` ADD COLUMN `isAbsent` INTEGER NOT NULL DEFAULT 0"
        )

    }
}
