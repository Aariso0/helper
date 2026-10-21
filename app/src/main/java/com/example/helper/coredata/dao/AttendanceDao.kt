package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import kotlinx.coroutines.flow.Flow

/** Per-class-per-day present count etc. for the Today screen's nudges. */
data class ClassDayCount(
    val classId: String,
    val present: Int,
    val absent: Int,
    val late: Int,
    val excused: Int,
)

/** One (student, status, count) bucket in a range — export & summaries. */
data class StudentStatusCount(
    val studentId: String,
    val status: AttendanceStatus,
    val count: Int,
)

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND dateEpochDay = :dateEpochDay")
    fun observeForClassOnDate(classId: String, dateEpochDay: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY dateEpochDay DESC")
    fun observeForStudent(studentId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND dateEpochDay = :dateEpochDay")
    fun observeForStudentOnDate(studentId: String, dateEpochDay: Long): Flow<AttendanceRecord?>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND dateEpochDay = :dateEpochDay")
    suspend fun getForClassOnDate(classId: String, dateEpochDay: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun getForStudentInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun getForClassInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): List<AttendanceRecord>

    /** Every row — the export-backup full table dump (no schema change). */
    @Query("SELECT * FROM attendance_records")
    suspend fun getAll(): List<AttendanceRecord>

    @Query("SELECT COUNT(*) > 0 FROM attendance_records WHERE classId = :classId AND dateEpochDay = :dateEpochDay")
    suspend fun isTaken(classId: String, dateEpochDay: Long): Boolean

    @Query("SELECT DISTINCT dateEpochDay FROM attendance_records WHERE classId = :classId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeTakenDatesInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>

    @Query(
        "SELECT classId, " +
            "SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS present, " +
            "SUM(CASE WHEN status = 'ABSENT' THEN 1 ELSE 0 END) AS absent, " +
            "SUM(CASE WHEN status = 'LATE' THEN 1 ELSE 0 END) AS late, " +
            "SUM(CASE WHEN status = 'EXCUSED' THEN 1 ELSE 0 END) AS excused " +
            "FROM attendance_records WHERE dateEpochDay = :dateEpochDay GROUP BY classId"
    )
    fun observeClassDayCounts(dateEpochDay: Long): Flow<List<ClassDayCount>>

    @Query(
        "SELECT studentId, status, COUNT(*) AS count FROM attendance_records " +
            "WHERE classId = :classId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "GROUP BY studentId, status"
    )
    fun observeStudentStatusCountsInRange(classId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<StudentStatusCount>>

    @Query(
        "SELECT studentId, status, COUNT(*) AS count FROM attendance_records " +
            "WHERE studentId = :studentId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "GROUP BY studentId, status"
    )
    fun observeStudentStatusCounts(studentId: String, fromEpochDay: Long, toEpochDay: Long): Flow<List<StudentStatusCount>>

    @Upsert
    suspend fun upsert(record: AttendanceRecord)

    @Upsert
    suspend fun upsertAll(records: List<AttendanceRecord>)
}
