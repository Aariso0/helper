package com.example.helper.coredata

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.helper.coredata.dao.AnnouncementDao
import com.example.helper.coredata.dao.AttendanceDao
import com.example.helper.coredata.dao.ContactLogDao
import com.example.helper.coredata.dao.ExamDao
import com.example.helper.coredata.dao.ExamMarkDao
import com.example.helper.coredata.dao.ExamSubjectDao
import com.example.helper.coredata.dao.FeedbackDao
import com.example.helper.coredata.dao.HomeworkDao
import com.example.helper.coredata.dao.SchoolClassDao
import com.example.helper.coredata.dao.StudentDao
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.ContactLogEntry
import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.FeedbackNote
import com.example.helper.coredata.entity.FeedbackTag
import com.example.helper.coredata.entity.Homework
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.migration.Migrations

/**
 * The one Room database (core-data card). Version 1 is the pre-built baseline:
 * every entity exists from day one so no v0->v1 migration is ever needed.
 * Schema JSON is exported to app/schemas/ and must be committed — it is the
 * input for every future Migration. Destructive fallbacks are banned.
 */
@Database(
    entities = [
        SchoolClass::class,
        Student::class,
        AttendanceRecord::class,
        FeedbackTag::class,
        FeedbackNote::class,
        Announcement::class,
        AnnouncementTemplate::class,
        ContactLogEntry::class,
        Homework::class,
        Attachment::class,
        Exam::class,
        ExamSubject::class,
        ExamMark::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolClassDao(): SchoolClassDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun contactLogDao(): ContactLogDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun examDao(): ExamDao
    abstract fun examSubjectDao(): ExamSubjectDao
    abstract fun examMarkDao(): ExamMarkDao

    companion object {
        /** Name of the single Room database file (export-backup copies it). */
        const val DATABASE_NAME = "helper.db"

        /** Schema version — @Database reads this; backup manifests record it. */
        const val SCHEMA_VERSION = 2

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { instance = it }
            }

        /**
         * `export-backup` seam (card setup step 2): flush the write-ahead log
         * so the `.db` file alone is a complete snapshot. Called before the
         * file is copied into a backup zip; copying a hot WAL produces a
         * corrupt restore (card build note).
         */
        suspend fun checkpoint() {
            val database = instance ?: return
            database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        }

        /**
         * `export-backup` seam: close and forget the singleton so the DB file
         * can be replaced. The app restarts afterwards — every repository the
         * ViewModels hold belongs to this now-closed instance.
         */
        fun closeForRestore() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
