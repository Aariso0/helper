package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.ExportColumnRepository
import com.example.helper.examsresults.export.ExportColumnDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class ExportColumnsUiState(
    val columns: List<ExportColumn> = ExportColumnDefaults.columns(),
    val subjects: List<ExamSubject> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Owns the teacher's ordered result-sheet columns. Reads fall back to the
 * built-in defaults when the table is empty; the first edit writes the whole
 * list, so the order the teacher sees is the order that gets saved.
 */
class ExportColumnsViewModel(
    private val examId: String,
    private val examRepository: ExamRepository,
    private val exportColumnRepository: ExportColumnRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportColumnsUiState())
    val uiState: StateFlow<ExportColumnsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                exportColumnRepository.observeAll(),
                examRepository.observeSubjects(examId),
            ) { stored, subjects ->
                ExportColumnsUiState(
                    columns = ExportColumnDefaults.effective(stored),
                    subjects = subjects,
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun move(columnId: String, up: Boolean) {
        val columns = _uiState.value.columns.toMutableList()
        val index = columns.indexOfFirst { it.id == columnId }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in columns.indices) return
        val moved = columns.removeAt(index)
        columns.add(target, moved)
        persist(columns)
    }

    fun addCustom(label: String, formula: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val columns = _uiState.value.columns
        val nextOrder = (columns.maxOfOrNull { it.sortOrder } ?: -1) + 1
        persist(
            columns + ExportColumn(
                id = UUID.randomUUID().toString(),
                label = trimmed,
                kind = ExportColumnKind.CUSTOM,
                formula = formula.trim(),
                sortOrder = nextOrder,
            ),
        )
    }

    fun update(column: ExportColumn) {
        persist(
            _uiState.value.columns.map { existing ->
                if (existing.id == column.id) column else existing
            },
        )
    }

    /** Only custom columns can be deleted; built-ins are reset, not removed. */
    fun delete(column: ExportColumn) {
        if (ExportColumnDefaults.isBuiltIn(column)) return
        persist(_uiState.value.columns.filterNot { it.id == column.id })
    }

    /** Empty table = defaults, so a reset is a delete-all. */
    fun resetToDefaults() {
        viewModelScope.launch { exportColumnRepository.reset() }
    }

    private fun persist(columns: List<ExportColumn>) {
        val ordered = columns.mapIndexed { index, column -> column.copy(sortOrder = index) }
        viewModelScope.launch { exportColumnRepository.replaceAll(ordered) }
    }
}
