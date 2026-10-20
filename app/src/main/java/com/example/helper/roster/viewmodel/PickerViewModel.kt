package com.example.helper.roster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Backing state for `picker/` — the reusable class/student pickers
 * (`class-student-picker` seam) that attendance/homework/parents open as
 * dialogs. One VM serves both pickers; consumers never touch repositories.
 * One shared query filters whichever picker is open (never both at once).
 */
class PickerViewModel(
    classRepository: SchoolClassRepository,
    studentRepository: StudentRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val query: StateFlow<String> = queryFlow

    val classes: StateFlow<List<SchoolClass>> = combine(
        classRepository.observeAll(),
        queryFlow,
    ) { classes, query ->
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            classes
        } else {
            classes.filter {
                it.name.lowercase().contains(needle) ||
                    (it.subject ?: "").lowercase().contains(needle)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val studentsByClassId: StateFlow<Map<String, List<Student>>> = combine(
        studentRepository.observeAll(),
        queryFlow,
    ) { students, query ->
        val needle = query.trim().lowercase()
        students
            .filter {
                needle.isBlank() ||
                    it.name.lowercase().contains(needle) ||
                    (it.rollNumber ?: "").lowercase().contains(needle)
            }
            .groupBy { it.classId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onQueryChanged(value: String) {
        queryFlow.value = value
    }
}
