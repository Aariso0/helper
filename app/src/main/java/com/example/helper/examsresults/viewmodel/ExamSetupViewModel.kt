package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ExamSetupUiState(
    val examName: String = "",
    val subjects: List<SubjectInput> = listOf(SubjectInput()),
    val saving: Boolean = false,
    val createdExamId: String? = null,
)

data class SubjectInput(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val maxMarks: String = "100",
    val passMarks: String = "35",
)

class ExamSetupViewModel(
    private val examRepository: ExamRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamSetupUiState())
    val uiState: StateFlow<ExamSetupUiState> = _uiState.asStateFlow()

    fun updateExamName(name: String) {
        _uiState.value = _uiState.value.copy(examName = name)
    }

    fun addSubject() {
        _uiState.value = _uiState.value.copy(
            subjects = _uiState.value.subjects + SubjectInput(),
        )
    }

    fun removeSubject(id: String) {
        if (_uiState.value.subjects.size <= 1) return
        _uiState.value = _uiState.value.copy(
            subjects = _uiState.value.subjects.filter { it.id != id },
        )
    }

    fun updateSubject(id: String, name: String, maxMarks: String, passMarks: String) {
        _uiState.value = _uiState.value.copy(
            subjects = _uiState.value.subjects.map {
                if (it.id == id) it.copy(name = name, maxMarks = maxMarks, passMarks = passMarks) else it
            },
        )
    }

    fun createExam(classId: String) {
        val state = _uiState.value
        if (state.examName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = state.copy(saving = true)
            val examId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            examRepository.createExam(
                Exam(
                    id = examId,
                    classId = classId,
                    name = state.examName.trim(),
                    examDate = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val subjects = state.subjects.filter { it.name.isNotBlank() }.mapIndexed { index, input ->
                val max = (input.maxMarks.toIntOrNull() ?: 100).coerceAtLeast(1)
                ExamSubject(
                    id = input.id,
                    examId = examId,
                    name = input.name.trim(),
                    maxMarks = max,
                    passMarks = (input.passMarks.toIntOrNull() ?: 35).coerceIn(0, max),
                    sortOrder = index,
                )
            }
            examRepository.upsertSubjects(subjects)
            _uiState.value = _uiState.value.copy(saving = false, createdExamId = examId)
        }
    }

    fun reset() {
        _uiState.value = ExamSetupUiState()
    }
}
