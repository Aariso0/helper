package com.example.helper.examsresults.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.StudentRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * One subject at a time: the teacher picks a subject, then walks the class
 * roster entering that subject's mark per student. The export still contains
 * every subject.
 */
data class MarkEntryUiState(
    val students: List<Student> = emptyList(),
    val subjects: List<ExamSubject> = emptyList(),
    val subjectId: String = "",
    val marks: Map<String, String> = emptyMap(), // studentId -> entered mark
    val absent: Set<String> = emptySet(), // studentIds marked absent for this subject
    val loading: Boolean = true,
) {
    val subject: ExamSubject? get() = subjects.firstOrNull { it.id == subjectId }
}

class MarkEntryViewModel(
    private val examId: String,
    private val subjectId: String,
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRepository,
    classId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MarkEntryUiState(subjectId = subjectId))
    val uiState: StateFlow<MarkEntryUiState> = _uiState.asStateFlow()

    /** Fires once a "Save & View Results" write has actually landed. */
    private val _savedAll = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedAll: SharedFlow<Unit> = _savedAll.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                studentRepository.observeInClass(classId),
                examRepository.observeSubjects(examId),
                examRepository.observeMarks(examId),
            ) { students, subjects, marks ->
                val subjectMarks = marks.filter { it.subjectId == subjectId }
                val entered = subjectMarks
                    .filterNot { it.isAbsent }
                    .associate { mark -> mark.studentId to formatMark(mark.marksObtained) }
                MarkEntryUiState(
                    students = students,
                    subjects = subjects,
                    subjectId = subjectId,
                    marks = entered,
                    absent = subjectMarks.filter { it.isAbsent }.map { it.studentId }.toSet(),
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateMark(studentId: String, raw: String) {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } > 1) return
        val parsed = cleaned.toDoubleOrNull()
        val max = _uiState.value.subject?.maxMarks
        if (parsed != null && max != null && parsed > max) return
        // Typing a number is the third state winning: an absent row becomes a
        // normal mark when it is saved.
        _uiState.value = _uiState.value.copy(
            marks = _uiState.value.marks + (studentId to cleaned),
            absent = _uiState.value.absent - studentId,
        )
    }

    /**
     * Toggles the subject between present and absent for one student. Turning
     * it on saves a zero-mark absent row and clears any typed text; turning it
     * off removes that row entirely, which is different from a `0`.
     */
    fun toggleAbsent(studentId: String) {
        val state = _uiState.value
        val subject = state.subject ?: return
        val wasAbsent = studentId in state.absent
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = examRepository.getMarks(examId)
                    .firstOrNull { it.studentId == studentId && it.subjectId == subject.id }
                if (wasAbsent) {
                    existing?.let { examRepository.deleteMark(it) }
                } else {
                    examRepository.upsertMark(
                        ExamMark(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            examId = examId,
                            subjectId = subject.id,
                            studentId = studentId,
                            marksObtained = 0.0,
                            isAbsent = true,
                        ),
                    )
                }
            }
        }
        _uiState.value = state.copy(
            marks = if (wasAbsent) state.marks else state.marks - studentId,
            absent = if (wasAbsent) state.absent - studentId else state.absent + studentId,
        )
    }

    fun saveMark(studentId: String) {
        val state = _uiState.value
        val subject = state.subject ?: return
        val number = state.marks[studentId]?.toDoubleOrNull() ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = examRepository.getMarks(examId)
                    .firstOrNull { it.studentId == studentId && it.subjectId == subject.id }
                examRepository.upsertMark(
                    ExamMark(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        examId = examId,
                        subjectId = subject.id,
                        studentId = studentId,
                        marksObtained = number,
                        isAbsent = false,
                    ),
                )
            }
        }
    }

    /**
     * Saves every entered mark for this subject; untouched fields stay
     * untouched. When [thenFinish] is set, [savedAll] fires only after the
     * write completes, so the screen can leave without cancelling it.
     */
    fun saveAll(thenFinish: Boolean = false) {
        val state = _uiState.value
        val subject = state.subject ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = examRepository.getMarks(examId)
                val toSave = state.students.mapNotNull { student ->
                    val number = state.marks[student.id]?.toDoubleOrNull() ?: return@mapNotNull null
                    val current = existing.firstOrNull {
                        it.studentId == student.id && it.subjectId == subject.id
                    }
                    ExamMark(
                        id = current?.id ?: UUID.randomUUID().toString(),
                        examId = examId,
                        subjectId = subject.id,
                        studentId = student.id,
                        marksObtained = number,
                        isAbsent = false,
                    )
                }
                if (toSave.isNotEmpty()) {
                    examRepository.upsertMarks(toSave)
                }
            }
            if (thenFinish) _savedAll.emit(Unit)
        }
    }

    private fun formatMark(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
