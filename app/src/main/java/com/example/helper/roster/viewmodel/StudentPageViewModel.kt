package com.example.helper.roster.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.roster.photostorage.PhotoStorage
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Derived attendance summary for the student page (card: attendance %). */
data class AttendanceSummary(
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val excused: Int = 0,
) {
    val total: Int get() = present + absent + late + excused

    /** Present share of all recorded days; null until any record exists. */
    val percent: Int? get() = if (total == 0) null else (present * 100) / total
}

data class StudentPageUiState(
    val loaded: Boolean = false,
    val student: Student? = null,
    val schoolClass: SchoolClass? = null,
    val summary: AttendanceSummary = AttendanceSummary(),
    val recentHistory: List<AttendanceRecord> = emptyList(),
    val totalRecords: Int = 0,
)

/**
 * Student page state: profile + attendance %/history read through
 * `data-repositories` (roster never reaches into attendance internals),
 * photo import through [PhotoStorage]. The feedback timeline embedded on
 * this page owns its own ViewModel (student-feedback seam).
 */
class StudentPageViewModel(
    private val studentId: String,
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val _photoError = MutableStateFlow(false)

    /** True when the last photo import failed — screen shows it, then clears. */
    val photoError: StateFlow<Boolean> = _photoError

    val uiState: StateFlow<StudentPageUiState> = combine(
        studentRepository.observeById(studentId),
        classRepository.observeAll(),
        attendanceRepository.observeForStudent(studentId),
    ) { student, classes, records ->
        val summary = AttendanceSummary(
            present = records.count { it.status == AttendanceStatus.PRESENT },
            absent = records.count { it.status == AttendanceStatus.ABSENT },
            late = records.count { it.status == AttendanceStatus.LATE },
            excused = records.count { it.status == AttendanceStatus.EXCUSED },
        )
        StudentPageUiState(
            loaded = true,
            student = student,
            schoolClass = classes.firstOrNull { it.id == student?.classId },
            summary = summary,
            recentHistory = records
                .sortedByDescending { it.dateEpochDay }
                .take(HISTORY_LIMIT),
            totalRecords = records.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentPageUiState())

    fun updateProfile(name: String, rollNumber: String?, parentContact: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val student = uiState.value.student ?: return
        viewModelScope.launch {
            studentRepository.upsert(
                student.copy(
                    name = trimmed,
                    rollNumber = rollNumber?.trim()?.ifEmpty { null },
                    parentContact = parentContact?.trim()?.ifEmpty { null },
                )
            )
        }
    }

    /** Copies a picked photo into app-internal storage and stores its path. */
    fun setPhotoFrom(uri: Uri) {
        viewModelScope.launch {
            val path = photoStorage.importFrom(uri, studentId)
            if (path != null) {
                _photoError.value = false
                studentRepository.setPhotoPath(studentId, path)
            } else {
                _photoError.value = true
            }
        }
    }

    fun onPhotoErrorShown() {
        _photoError.value = false
    }

    fun removePhoto() {
        viewModelScope.launch {
            photoStorage.deleteFor(studentId)
            studentRepository.setPhotoPath(studentId, null)
        }
    }

    fun deleteStudent(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val student = uiState.value.student ?: return@launch
            photoStorage.deleteFor(student.id)
            studentRepository.delete(student)
            onDeleted()
        }
    }

    companion object {
        /** History rows shown on the page; the full set stays in export. */
        const val HISTORY_LIMIT = 10
    }
}

/** Epoch-day → short display date for roster's own history rows. */
fun Long.toDisplayDate(): String {
    val date = LocalDate.ofEpochDay(this)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
