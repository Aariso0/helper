package com.example.helper.coredata

import android.content.Context
import com.example.helper.coredata.repository.AnnouncementRepository
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.ContactLogRepository
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.FeedbackRepository
import com.example.helper.coredata.repository.HomeworkRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import java.io.File

/**
 * Manual-DI container (core-data card): built once on the Application, owns
 * the database and the `data-repositories` every feature module consumes.
 * Feature ViewModels reach repositories through this — never Room directly.
 */
class AppContainer(private val context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.get(context) }

    val classRepository = SchoolClassRepository(database.schoolClassDao())
    val studentRepository = StudentRepository(database.studentDao())
    val attendanceRepository = AttendanceRepository(database.attendanceDao())
    val feedbackRepository = FeedbackRepository(database.feedbackDao())
    val announcementRepository = AnnouncementRepository(database.announcementDao())
    val contactLogRepository = ContactLogRepository(database.contactLogDao())
    val homeworkRepository = HomeworkRepository(database.homeworkDao())
    val examRepository = ExamRepository(database.examDao(), database.examSubjectDao(), database.examMarkDao())

    // ── `export-backup` seam (card setup step 2) ───────────────────────────
    // File-level backup needs exactly these three things and nothing else in
    // the app does. Everything else about backups stays in export-backup.

    /** Flushes the WAL; after this the DB file alone is a complete snapshot. */
    suspend fun checkpointForBackup() = AppDatabase.checkpoint()

    /** The single Room file the backup zip copies (restore replaces it). */
    fun databaseFile(): File = context.getDatabasePath(AppDatabase.DATABASE_NAME)

    /** Closes and forgets the DB instance so its file can be replaced. */
    fun closeDatabaseForRestore() = AppDatabase.closeForRestore()
}
