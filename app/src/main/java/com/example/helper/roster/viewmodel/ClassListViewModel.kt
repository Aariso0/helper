package com.example.helper.roster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.roster.photostorage.PhotoStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One class row with its student count (class list). */
data class ClassRow(
    val schoolClass: SchoolClass,
    val studentCount: Int,
)

/** Class-list screen state; [loaded] is false until the first DB emission so
 *  the screen can show progress instead of a false "No classes yet". */
data class ClassListUiState(
    val loaded: Boolean = false,
    val classes: List<ClassRow> = emptyList(),
)

/**
 * Students-tab root state: class list with counts, create/rename/delete.
 * Deleting a class cascades to its students (FK) — stored photo files are
 * cleaned up here because the database cannot know about them.
 */
class ClassListViewModel(
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    val uiState: StateFlow<ClassListUiState> = combine(
        classRepository.observeAll(),
        studentRepository.observeAll(),
    ) { classes, students ->
        val counts = students.groupingBy { it.classId }.eachCount()
        ClassListUiState(
            loaded = true,
            classes = classes
                .map { ClassRow(it, counts[it.id] ?: 0) }
                .sortedBy { it.schoolClass.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClassListUiState())

    fun createClass(name: String, subject: String?, year: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { classRepository.create(trimmed, subject?.trim(), year?.trim()) }
    }

    fun renameClass(schoolClass: SchoolClass, name: String, subject: String?, year: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            classRepository.upsert(
                schoolClass.copy(
                    name = trimmed,
                    subject = subject?.trim()?.ifEmpty { null },
                    year = year?.trim()?.ifEmpty { null },
                )
            )
        }
    }

    fun deleteClass(schoolClass: SchoolClass) {
        viewModelScope.launch {
            // Photo files live outside the database — clear them before the
            // cascade removes the students we key them by.
            val students = studentRepository.observeInClass(schoolClass.id).first()
            students.forEach { photoStorage.deleteFor(it.id) }
            classRepository.delete(schoolClass)
        }
    }
}
