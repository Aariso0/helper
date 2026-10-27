package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.examsresults.util.ExamHistory
import com.example.helper.examsresults.util.ExamHistoryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StudentExamHistoryUiState(
    val loaded: Boolean = false,
    val entries: List<ExamHistoryEntry> = emptyList(),
)

/**
 * Exam history for one student, newest first. The class comes from the
 * student, so an edit that moves the student to another class re-subscribes
 * to that class's exams; marks stay scoped to this student.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentExamHistoryViewModel(
    studentId: String,
    private val studentRepository: StudentRepository,
    private val examRepository: ExamRepository,
) : ViewModel() {

    private val studentFlow = studentRepository.observeById(studentId)

    val uiState: StateFlow<StudentExamHistoryUiState> = studentFlow
        .map { it?.classId }
        .distinctUntilChanged()
        .flatMapLatest { classId ->
            if (classId == null) {
                flowOf(StudentExamHistoryUiState(loaded = true))
            } else {
                combine(
                    studentFlow,
                    examRepository.observeByClass(classId),
                    examRepository.observeSubjectsForClass(classId),
                    examRepository.observeMarksForStudent(studentId),
                ) { student, exams, subjects, marks ->
                    if (student == null) {
                        StudentExamHistoryUiState(loaded = true)
                    } else {
                        StudentExamHistoryUiState(
                            loaded = true,
                            entries = ExamHistory.build(exams, subjects, marks, student),
                        )
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StudentExamHistoryUiState(),
        )
}
