package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.AttendanceDao
import com.example.helper.coredata.dao.ClassDayCount
import com.example.helper.coredata.dao.StudentStatusCount
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for AttendanceRecords (glossary: "Attendance record").
 * Dates are epoch-day Longs (`LocalDate.toEpochDay()`) chosen by the caller —
 * this layer never touches java.time.
 *
 * Write model (attendance card): the first change to a fresh session
 * materializes the whole class-day via [commitSession]; later taps upsert one
 * row via [setStatus]. Undo is just [setStatus] with the previous status.
 */
class AttendanceRepository(private val dao: AttendanceDao) {
    fun observeForClassOnDate(classId: String, dateEpochDay: Long): Flow<List<AttendanceRecord>> =
        dao.observeForClassOnDate(classId, dateEpochDay)

    fun observeForStudent(studentId: String): Flow<List<AttendanceRecord>> =
        dao.observeForStudent(studentId)

    fun observeForStudentOnDate(studentId: String, dateEpochDay: Long): Flow<AttendanceRecord?> =
        dao.observeForStudentOnDate(studentId, dateEpochDay)

    suspend fun getForClassOnDate(classId: String, dateEpochDay: Long): List<AttendanceRecord> =
        dao.getForClassOnDate(classId, dateEpochDay)

    suspend fun getForStudentInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<AttendanceRecord> =
        dao.getForStudentInRange(studentId, fromEpochDay, toEpochDay)

    suspend fun getForClassInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): List<AttendanceRecord> =
        dao.getForClassInRange(classId, fromEpochDay, toEpochDay)

    /** Every row — the export-backup full table dump. */
    suspend fun getAll(): List<AttendanceRecord> = dao.getAll()

    /** "Was attendance taken for this class-day?" — Today screen nudge. */
    suspend fun isTaken(classId: String, dateEpochDay: Long): Boolean =
        dao.isTaken(classId, dateEpochDay)

    fun observeTakenDatesInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>> =
        dao.observeTakenDatesInRange(classId, fromEpochDay, toEpochDay)

    fun observeClassDayCounts(dateEpochDay: Long): Flow<List<ClassDayCount>> =
        dao.observeClassDayCounts(dateEpochDay)

    fun observeStudentStatusCountsInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<StudentStatusCount>> =
        dao.observeStudentStatusCountsInRange(classId, fromEpochDay, toEpochDay)

    fun observeStudentStatusCounts(studentId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<StudentStatusCount>> =
        dao.observeStudentStatusCounts(studentId, fromEpochDay, toEpochDay)

    /**
     * One student's status toggle / undo — upserts the single row.
     * Never touches [AttendanceRecord.note]; use [setNote] for that.
     */
    suspend fun setStatus(
        classId: String,
        studentId: String,
        dateEpochDay: Long,
        status: AttendanceStatus,
        period: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.getForClassOnDate(classId, dateEpochDay)
            .firstOrNull { it.studentId == studentId }
        dao.upsert(
            (existing ?: AttendanceRecord(
                id = UUID.randomUUID().toString(),
                classId = classId,
                studentId = studentId,
                dateEpochDay = dateEpochDay,
                status = status,
                period = period,
                createdAt = now,
                updatedAt = now,
            )).copy(status = status, period = period ?: existing?.period, updatedAt = now)
        )
    }

    /** Optional per-absence note (README 4.1); creates the row if missing. */
    suspend fun setNote(
        classId: String,
        studentId: String,
        dateEpochDay: Long,
        note: String?,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.getForClassOnDate(classId, dateEpochDay)
            .firstOrNull { it.studentId == studentId }
        dao.upsert(
            (existing ?: AttendanceRecord(
                id = UUID.randomUUID().toString(),
                classId = classId,
                studentId = studentId,
                dateEpochDay = dateEpochDay,
                status = AttendanceStatus.PRESENT,
                note = note,
                createdAt = now,
                updatedAt = now,
            )).copy(note = note?.trim()?.ifEmpty { null }, updatedAt = now)
        )
    }

    /**
     * First change of a fresh session: materialize the whole class-day in one
     * call (one transaction at the call site if atomicity is wanted).
     * [statusesByStudentId] must cover every student in the class.
     */
    suspend fun commitSession(
        classId: String,
        dateEpochDay: Long,
        statusesByStudentId: Map<String, AttendanceStatus>,
        period: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val existingByStudent = dao.getForClassOnDate(classId, dateEpochDay)
            .associateBy { it.studentId }
        dao.upsertAll(
            statusesByStudentId.map { (studentId, status) ->
                val existing = existingByStudent[studentId]
                if (existing != null) {
                    existing.copy(status = status, updatedAt = now)
                } else {
                    AttendanceRecord(
                        id = UUID.randomUUID().toString(),
                        classId = classId,
                        studentId = studentId,
                        dateEpochDay = dateEpochDay,
                        status = status,
                        period = period,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            }
        )
    }
}
