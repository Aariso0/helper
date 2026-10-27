package com.example.helper.exportbackup.summarize

import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import kotlin.math.roundToInt

/**
 * One month's attendance summary (export-backup card): per-student figures,
 * per-class totals, and the grand total — every writer and the on-screen
 * cards consume exactly this model, never recomputing anything (card build
 * note: "one summary model feeds all three writers").
 *
 * `presentPercentage` is **null** when nothing was tracked: "no records
 * tracked" and "0% present" are different states and must never collapse
 * (card build note).
 */
data class MonthlySummary(
    val year: Int,
    val month: Int,
    val classes: List<ClassMonthSummary>,
) {
    val trackedRecords: Int get() = classes.sumOf { it.trackedRecords }
    val present: Int get() = classes.sumOf { it.present }
    val absent: Int get() = classes.sumOf { it.absent }
    val late: Int get() = classes.sumOf { it.late }
    val excused: Int get() = classes.sumOf { it.excused }
    val trackedDays: Int get() = classes.sumOf { it.trackedDays }

    val presentPercentage: Int? get() = percentage(present + late, trackedRecords)
    val hasRecords: Boolean get() = trackedRecords > 0
}

data class ClassMonthSummary(
    val classId: String,
    val className: String,
    val students: List<StudentMonthSummary>,
    val present: Int,
    val absent: Int,
    val late: Int,
    val excused: Int,
    /** Distinct dates with at least one record for this class. */
    val trackedDays: Int,
    val trackedRecords: Int,
) {
    val presentPercentage: Int? get() = percentage(present + late, trackedRecords)
}

data class StudentMonthSummary(
    val studentId: String,
    val studentName: String,
    val rollNumber: String?,
    val present: Int,
    val absent: Int,
    val late: Int,
    val excused: Int,
    val trackedRecords: Int,
) {
    val presentPercentage: Int? get() = percentage(present + late, trackedRecords)
}

/**
 * The percentage rule, in exactly one place (card open question: Late as
 * present is a client decision; this constant is the one-line change and the
 * exported file header states it — see [PERCENTAGE_RULE]).
 */
private fun percentage(counted: Int, total: Int): Int? =
    if (total <= 0) null else (counted * 100.0 / total).roundToInt()

/** Stated in every exported file so the number is never ambiguous. */
const val PERCENTAGE_RULE = "Present % counts late arrivals as present."

/**
 * Register order for the exported sheet, matching `StudentDao.observeInClass`:
 * numeric rolls first in numeric order (1, 2, … 10, 11 — not text order), then
 * non-numeric rolls alphabetically, then students with no roll number.
 */
internal val ROLL_NUMBER_ORDER: Comparator<Student> =
    compareBy<Student> { it.rollNumber == null }
        .thenBy { it.rollNumber?.toIntOrNull() ?: Int.MAX_VALUE }
        .thenBy { it.rollNumber.orEmpty().lowercase() }
        .thenBy { it.name.lowercase() }

/**
 * Pure aggregation — no Room, no coroutines, no clock. The ViewModel loads
 * the rows for the month and hands them here; every figure is derived from
 * those same rows, so no second stats store can drift.
 *
 * [selectedClassId] filters to one class (null = all classes). Students with
 * no records are still listed with `presentPercentage == null` — the teacher
 * sees who was never marked, instead of silently dropping them.
 */
fun buildMonthlySummary(
    year: Int,
    month: Int,
    classes: List<SchoolClass>,
    students: List<Student>,
    records: List<AttendanceRecord>,
    selectedClassId: String?,
): MonthlySummary {
    val recordsByStudent = records.groupBy { it.studentId }
    val classSummaries = classes
        .filter { selectedClassId == null || it.id == selectedClassId }
        .map { schoolClass ->
            val classStudents = students
                .filter { it.classId == schoolClass.id }
                .sortedWith(ROLL_NUMBER_ORDER)
            val classRecords = records.filter { it.classId == schoolClass.id }
            ClassMonthSummary(
                classId = schoolClass.id,
                className = schoolClass.name,
                students = classStudents.map { student ->
                    val own = recordsByStudent[student.id].orEmpty()
                    StudentMonthSummary(
                        studentId = student.id,
                        studentName = student.name,
                        rollNumber = student.rollNumber,
                        present = own.count { it.status == AttendanceStatus.PRESENT },
                        absent = own.count { it.status == AttendanceStatus.ABSENT },
                        late = own.count { it.status == AttendanceStatus.LATE },
                        excused = own.count { it.status == AttendanceStatus.EXCUSED },
                        trackedRecords = own.size,
                    )
                },
                present = classRecords.count { it.status == AttendanceStatus.PRESENT },
                absent = classRecords.count { it.status == AttendanceStatus.ABSENT },
                late = classRecords.count { it.status == AttendanceStatus.LATE },
                excused = classRecords.count { it.status == AttendanceStatus.EXCUSED },
                trackedDays = classRecords.map { it.dateEpochDay }.distinct().size,
                trackedRecords = classRecords.size,
            )
        }
        .sortedBy { it.className.lowercase() }
    return MonthlySummary(year = year, month = month, classes = classSummaries)
}
