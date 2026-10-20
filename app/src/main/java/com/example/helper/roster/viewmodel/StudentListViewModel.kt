package com.example.helper.roster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.roster.photostorage.PhotoStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Searchable student list for one class (card: studentlist). */
class StudentListViewModel(
    private val classId: String,
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val query: StateFlow<String> = queryFlow

    val uiState: StateFlow<StudentListUiState> = combine(
        classRepository.observeById(classId),
        studentRepository.observeInClass(classId),
        queryFlow,
    ) { schoolClass, students, query ->
        val visible = if (query.isBlank()) {
            students
        } else {
            val needle = query.trim().lowercase()
            students.filter {
                it.name.lowercase().contains(needle) ||
                    (it.rollNumber ?: "").lowercase().contains(needle)
            }
        }
        StudentListUiState(
            loaded = true,
            schoolClass = schoolClass,
            students = students.sortedWith(rollThenName),
            visibleStudents = visible.sortedWith(rollThenName),
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentListUiState())

    fun onQueryChanged(value: String) {
        queryFlow.value = value
    }

    fun addStudent(name: String, rollNumber: String?, parentContact: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { studentRepository.create(classId, trimmed, rollNumber, parentContact) }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            photoStorage.deleteFor(student.id)
            studentRepository.delete(student)
        }
    }
}

data class StudentListUiState(
    val loaded: Boolean = false,
    val schoolClass: SchoolClass? = null,
    val students: List<Student> = emptyList(),
    val visibleStudents: List<Student> = emptyList(),
    val query: String = "",
)

/** Roll number numerically when present, then name — for the class list. */
private val rollThenName: Comparator<Student> =
    compareBy<Student, Int?>(nullsFirst()) { it.rollNumber?.toIntOrNull() }
        .thenBy { it.name.lowercase() }
