package com.example.helper.coredata

import android.content.Context
import com.example.helper.coredata.repository.AnnouncementRepository
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.ContactLogRepository
import com.example.helper.coredata.repository.FeedbackRepository
import com.example.helper.coredata.repository.HomeworkRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository

/**
 * Manual-DI container (core-data card): built once on the Application, owns
 * the database and the `data-repositories` every feature module consumes.
 * Feature ViewModels reach repositories through this — never Room directly.
 */
class AppContainer(context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.get(context) }

    val classRepository = SchoolClassRepository(database.schoolClassDao())
    val studentRepository = StudentRepository(database.studentDao())
    val attendanceRepository = AttendanceRepository(database.attendanceDao())
    val feedbackRepository = FeedbackRepository(database.feedbackDao())
    val announcementRepository = AnnouncementRepository(database.announcementDao())
    val contactLogRepository = ContactLogRepository(database.contactLogDao())
    val homeworkRepository = HomeworkRepository(database.homeworkDao())
}
