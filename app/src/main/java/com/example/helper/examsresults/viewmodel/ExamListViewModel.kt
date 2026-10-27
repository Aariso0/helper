package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.entity.Exam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class ExamListUiState(
    val exams: List<ExamWithClass> = emptyList(),
    val loading: Boolean = true,
)

data class ExamWithClass(
    val exam: Exam,
    val className: String,
)

class ExamListViewModel(
    private val examRepository: ExamRepository,
    private val classRepository: SchoolClassRepository,
    private val classId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamListUiState())
    val uiState: StateFlow<ExamListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                examRepository.observeByClass(classId),
                classRepository.observeById(classId),
            ) { exams, schoolClass ->
                ExamListUiState(
                    exams = exams.map { exam ->
                        ExamWithClass(
                            exam = exam,
                            className = schoolClass?.name ?: "",
                        )
                    },
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            examRepository.deleteExam(exam)
        }
    }
}
