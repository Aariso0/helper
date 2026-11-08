package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.ExportColumnRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.SchoolSessionRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.examsresults.export.ExportColumnDefaults
import com.example.helper.examsresults.util.AbsentPlanner
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
    val className: String = "",
    val exportColumns: List<ExportColumn> = ExportColumnDefaults.columns(),
    val loading: Boolean = true,
    // Banner fields, printed on the export: who and where the sheet belongs to.
    val schoolName: String = "",
    val sessionLabel: String = "",
    val teacherName: String = "",
    val teacherDesignation: String = "",
    val examDate: Long? = null,
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
    private val classRepository: SchoolClassRepository,
    private val sessionRepository: SchoolSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamResultUiState())
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(
                    examRepository.observeById(examId),
                    classRepository.observeById(classId),
                    sessionRepository.observeAll(),
                ) { exam, schoolClass, sessions ->
                    Triple(exam, schoolClass, sessions)
                },
                studentRepository.observeInClass(classId),
                examRepository.observeSubjects(examId),
                examRepository.observeMarks(examId),
                exportColumnRepository.observeAll(),
            ) { (exam, schoolClass, sessions), students, subjects, marks, storedColumns ->
                val results = students.map { student ->
                    val studentMarks = marks.filter { it.studentId == student.id }
                    ResultCalculator.calculate(student, subjects, studentMarks)
                }.sortedBy { it.student.rollNumber ?: it.student.name }
                ExamResultUiState(
                    examName = exam?.name ?: "",
                    className = schoolClass?.name ?: "",
                    subjects = subjects,
                    results = results,
                    exportColumns = ExportColumnDefaults.effective(storedColumns),
                    loading = false,
                    schoolName = schoolClass?.schoolName.orEmpty(),
                    sessionLabel = sessions.firstOrNull { it.id == schoolClass?.sessionId }
                        ?.label.orEmpty(),
                    teacherName = schoolClass?.teacherName.orEmpty(),
                    teacherDesignation = schoolClass?.teacherDesignation.orEmpty(),
                    examDate = exam?.examDate,
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
            examRepository.updateSubject(
                subject.copy(
                    title = subject.title?.trim()?.ifEmpty { null },
                    teacherName = subject.teacherName?.trim()?.ifEmpty { null },
                    teacherDesignation = subject.teacherDesignation?.trim()?.ifEmpty { null },
                    employeeType = subject.employeeType?.trim()?.ifEmpty { null },
                ),
                max,
                pass,
            )
        }
    }

    /** Adds a subject to an exam that already exists (post-creation edit). */
    fun addSubject(input: SubjectInput) {
        val trimmed = input.name.trim()
        if (trimmed.isEmpty()) return
        val max = input.maxMarks.toIntOrNull() ?: return
        if (max <= 0) return
        val pass = (input.passMarks.toIntOrNull() ?: 0).coerceIn(0, max)
        viewModelScope.launch {
            val sortOrder = (_uiState.value.subjects.maxOfOrNull { it.sortOrder } ?: -1) + 1
            examRepository.upsertSubject(
                ExamSubject(
                    id = input.id,
                    examId = examId,
                    name = trimmed,
                    maxMarks = max,
                    passMarks = pass,
                    sortOrder = sortOrder,
                    title = input.title.trim().ifEmpty { null },
                    teacherName = input.teacherName.trim().ifEmpty { null },
                    teacherDesignation = input.teacherDesignation.trim().ifEmpty { null },
                    employeeType = input.employeeType.trim().ifEmpty { null },
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

    /**
     * Marks every student/subject pair that still has no mark as absent. The
     * count is the same list [AbsentPlanner.blankPairs] shows in the
     * confirmation, so the teacher never writes more rows than promised.
     */
    fun markRemainingAbsent() {
        val state = _uiState.value
        val blanks = AbsentPlanner.blankPairs(state.results, state.subjects)
        if (blanks.isEmpty()) return
        viewModelScope.launch {
            examRepository.upsertMarks(
                blanks.map { blank ->
                    ExamMark(
                        id = UUID.randomUUID().toString(),
                        examId = examId,
                        subjectId = blank.subjectId,
                        studentId = blank.studentId,
                        marksObtained = 0.0,
                        isAbsent = true,
                    )
                },
            )
        }
    }

    /** Removes every absent row in the exam; entered marks stay untouched. */
    fun clearAllAbsent() {
        viewModelScope.launch {
            examRepository.deleteAbsentMarks(examId)
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
