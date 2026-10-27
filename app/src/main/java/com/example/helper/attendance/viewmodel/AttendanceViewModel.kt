package com.example.helper.attendance.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Attendance screen state (attendance card): a draft map of per-student
 * statuses seeded from (a) existing records for the class-day or (b) the
 * all-Present preset, with live counters derived from the draft. First
 * change (or Save) on a fresh session materializes the whole class-day via
 * [AttendanceRepository.commitSession]; later taps upsert single rows.
 * The date is part of the state — switching dates reloads that session.
 */
class AttendanceViewModel(
    private val classId: String,
    initialDateEpochDay: Long,
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    private val dateEpochDay = MutableStateFlow(initialDateEpochDay)

    /** False until the class-day's seed (records + preset) is loaded. */
    private val seeded = MutableStateFlow(false)

    /** The draft: every student's status, updated synchronously on each tap. */
    private val draft = MutableStateFlow<Map<String, AttendanceStatus>>(emptyMap())

    /** True once the class-day has rows in the DB (a fresh session doesn't). */
    private var sessionMaterialized = false

    /** Serializes writes so rapid taps can't interleave a materialize + upsert. */
    private val persistMutex = Mutex()

    /** Derived live counters ("Present 28 | Absent 2") — never stored. */
    data class Counters(val present: Int, val absent: Int, val late: Int, val excused: Int)

    data class UiState(
        val loading: Boolean = true,
        val className: String = "",
        val dateEpochDay: Long = 0L,
        val students: List<Student> = emptyList(),
        val statuses: Map<String, AttendanceStatus> = emptyMap(),
    ) {
        val counters: Counters
            get() = Counters(
                present = statuses.values.count { it == AttendanceStatus.PRESENT },
                absent = statuses.values.count { it == AttendanceStatus.ABSENT },
                late = statuses.values.count { it == AttendanceStatus.LATE },
                excused = statuses.values.count { it == AttendanceStatus.EXCUSED },
            )
    }

    val uiState: StateFlow<UiState> = combine(
        classRepository.observeById(classId),
        studentRepository.observeInClass(classId),
        dateEpochDay,
        draft,
        seeded,
    ) { schoolClass, students, date, statuses, isSeeded ->
        UiState(
            loading = !isSeeded,
            className = schoolClass?.name ?: "Attendance",
            dateEpochDay = date,
            students = students,
            statuses = statuses,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    init {
        viewModelScope.launch { seed(initialDateEpochDay) }
    }

    private suspend fun seed(date: Long) {
        val existing = attendanceRepository.getForClassOnDate(classId, date)
        val students = studentRepository.observeInClass(classId).first()
        sessionMaterialized = existing.isNotEmpty()
        val existingStatuses = existing.associate { it.studentId to it.status }
        draft.value = students.associate { student ->
            student.id to (existingStatuses[student.id] ?: AttendanceStatus.PRESENT)
        }
        seeded.value = true
    }

    /** Date switch (M3 DatePicker): reload that date's session through repositories. */
    fun onDateSelected(newDateEpochDay: Long) {
        if (newDateEpochDay == dateEpochDay.value) return
        dateEpochDay.value = newDateEpochDay
        seeded.value = false
        viewModelScope.launch { seed(newDateEpochDay) }
    }

    /** README §3.1: tap = mark absent; tap again = back to present. */
    /**
     * Tap = absent, tap again = present. Returns the status the row had before
     * the tap (read from the live draft, not from the last composition), so the
     * Undo snackbar can only ever revert the mark it is describing.
     */
    fun onToggleAbsent(studentId: String): AttendanceStatus? {
        val current = draft.value[studentId] ?: return null
        val next = if (current == AttendanceStatus.ABSENT) {
            AttendanceStatus.PRESENT
        } else {
            AttendanceStatus.ABSENT
        }
        onSetStatus(studentId, next)
        return current
    }

    /** Single status set (long-press sheet + undo share this path). */
    fun onSetStatus(studentId: String, status: AttendanceStatus) {
        if (draft.value[studentId] == status) return
        draft.value = draft.value + (studentId to status)
        viewModelScope.launch { persist(studentId) }
    }

    private suspend fun persist(studentId: String) = persistMutex.withLock {
        val status = draft.value[studentId] ?: return@withLock
        if (!sessionMaterialized) {
            // First change materializes the whole class-day (full draft).
            attendanceRepository.commitSession(classId, dateEpochDay.value, draft.value)
            sessionMaterialized = true
        } else {
            attendanceRepository.setStatus(classId, studentId, dateEpochDay.value, status)
        }
    }

    /**
     * Save = "done for this class" (card's autosave reconciliation): the
     * zero-tap common case still persists the all-Present preset; per-tap
     * writes already landed for a materialized session.
     * Returns the absent students' names for the post-save share shortcut.
     */
    suspend fun save(): List<String> = persistMutex.withLock {
        if (!sessionMaterialized) {
            attendanceRepository.commitSession(classId, dateEpochDay.value, draft.value)
            sessionMaterialized = true
        }
        val absentIds = draft.value.filterValues { it == AttendanceStatus.ABSENT }.keys
        uiState.value.students.filter { it.id in absentIds }.map { it.name }
    }
}

@Composable
fun attendanceViewModel(
    classId: String,
    dateEpochDay: Long,
    key: String = "attendance-$classId",
): AttendanceViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        AttendanceViewModel(
            classId = classId,
            initialDateEpochDay = dateEpochDay,
            classRepository = container.classRepository,
            studentRepository = container.studentRepository,
            attendanceRepository = container.attendanceRepository,
        )
    }
}
