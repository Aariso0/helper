package com.example.helper.coredata.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exams` (
                `id` TEXT NOT NULL,
                `classId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `examDate` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`classId`) REFERENCES `classes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_classId` ON `exams` (`classId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exam_subjects` (
                `id` TEXT NOT NULL,
                `examId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `maxMarks` INTEGER NOT NULL,
                `passMarks` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`examId`) REFERENCES `exams`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_subjects_examId` ON `exam_subjects` (`examId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exam_marks` (
                `id` TEXT NOT NULL,
                `examId` TEXT NOT NULL,
                `subjectId` TEXT NOT NULL,
                `studentId` TEXT NOT NULL,
                `marksObtained` REAL NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`examId`) REFERENCES `exams`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`subjectId`) REFERENCES `exam_subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_marks_examId` ON `exam_marks` (`examId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_marks_subjectId` ON `exam_marks` (`subjectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_marks_studentId` ON `exam_marks` (`studentId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_marks_examId_studentId_subjectId` ON `exam_marks` (`examId`, `studentId`, `subjectId`)")
    }
}
