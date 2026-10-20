package com.example.helper.coredata

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.helper.coredata.dao.AnnouncementDao
import com.example.helper.coredata.dao.AttendanceDao
import com.example.helper.coredata.dao.ContactLogDao
import com.example.helper.coredata.dao.FeedbackDao
import com.example.helper.coredata.dao.HomeworkDao
import com.example.helper.coredata.dao.SchoolClassDao
import com.example.helper.coredata.dao.StudentDao
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.ContactLogEntry
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
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "helper.db",
                )
                    .addMigrations(*Migrations.ALL)
                    .build()
                    .also { instance = it }
            }
    }
}
