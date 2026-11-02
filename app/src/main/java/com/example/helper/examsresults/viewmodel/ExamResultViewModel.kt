package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.ExportColumnRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.examsresults.export.ExportColumnDefaults
import com.example.helper.examsresults.util.ResultCalculator
import com.example.helper.examsresults.util.StudentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class ExamResultUiState(
    val results: List<StudentResult> = emptyList(),
    val subjects: List<com.example.helper.coredata.entity.ExamSubject> = emptyList(),
    val examName: String = "",
    val exportColumns: List<ExportColumn> = ExportColumnDefaults.columns(),
    val loading: Boolean = true,
)

/**
 * The export needs no colour choice: a passed row stays plain and a failed
 * row is bold italic, both applied by the sheet's own conditional rule.
 */
class ExamResultViewModel(
    private val examId: String,
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRepository,
    private val classId: String,
    private val exportColumnRepository: ExportColumnRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamResultUiState())
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                examRepository.observeById(examId),
                studentRepository.observeInClass(classId),
                examRepository.observeSubjects(examId),
                examRepository.observeMarks(examId),
                exportColumnRepository.observeAll(),
            ) { exam, students, subjects, marks, storedColumns ->
                val results = students.map { student ->
                    val studentMarks = marks.filter { it.studentId == student.id }
                    ResultCalculator.calculate(student, subjects, studentMarks)
                }.sortedBy { it.student.rollNumber ?: it.student.name }
                ExamResultUiState(
                    examName = exam?.name ?: "",
                    subjects = subjects,
                    results = results,
                    exportColumns = ExportColumnDefaults.effective(storedColumns),
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Updates a subject's mark totals; existing marks above the new total are lowered. */
    fun updateSubject(subject: ExamSubject, maxMarks: String, passMarks: String) {
        val max = maxMarks.toIntOrNull() ?: return
        if (max <= 0) return
        val pass = (passMarks.toIntOrNull() ?: 0).coerceIn(0, max)
        viewModelScope.launch {
            examRepository.updateSubject(subject, max, pass)
        }
    }

    /** Adds a subject to an exam that already exists (post-creation edit). */
    fun addSubject(name: String, maxMarks: String, passMarks: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val max = maxMarks.toIntOrNull() ?: return
        if (max <= 0) return
        val pass = (passMarks.toIntOrNull() ?: 0).coerceIn(0, max)
        viewModelScope.launch {
            val sortOrder = (_uiState.value.subjects.maxOfOrNull { it.sortOrder } ?: -1) + 1
            examRepository.upsertSubject(
                ExamSubject(
                    id = UUID.randomUUID().toString(),
                    examId = examId,
                    name = trimmed,
                    maxMarks = max,
                    passMarks = pass,
                    sortOrder = sortOrder,
                ),
            )
        }
    }

    /** Removes a subject and, by FK cascade, every mark recorded for it. */
    fun deleteSubject(subject: ExamSubject) {
        viewModelScope.launch {
            examRepository.deleteSubject(subject)
        }
    }

    /** Removes the exam; subjects and marks cascade with it. */
    fun deleteExam(onDeleted: () -> Unit) {
        viewModelScope.launch {
            examRepository.getById(examId)?.let { examRepository.deleteExam(it) }
            onDeleted()
        }
    }
}
