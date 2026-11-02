package com.example.helper.examsresults.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.ExamRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.examsresults.excelimport.ExamImportModel
import com.example.helper.examsresults.excelimport.ExamImportPlan
import com.example.helper.examsresults.excelimport.ExamImportRows
import com.example.helper.examsresults.excelimport.ExamImportSubjectDraft
import com.example.helper.examsresults.excelimport.ImportCandidate
import com.example.helper.roster.ui.import.ImportFileException
import com.example.helper.roster.ui.import.ImportParsers
import com.example.helper.roster.ui.import.ImportRange
import com.example.helper.roster.ui.import.ParsedTable
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExamImportPhase { IDLE, PARSING, MAPPING, COMMITTING, DONE }

/**
 * State for "import an exam from a sheet" (exams card: file pick → sheet and
 * row window → subject/mark preview → commit). The exam itself is only created
 * on the commit button; the preview shows exactly which students matched and
 * which marks will be written.
 */
data class ExamImportUiState(
    val phase: ExamImportPhase = ExamImportPhase.IDLE,
    val fileName: String? = null,
    val table: ParsedTable? = null,
    val sheets: List<String> = emptyList(),
    val sheetName: String? = null,
    /** Row numbers as typed (1-based, the numbers Excel shows). */
    val startRow: String = "",
    val endRow: String = "",
    val rangeError: String? = null,
    val reloading: Boolean = false,
    val examName: String = "",
    val plan: ExamImportPlan? = null,
    val roster: List<Student> = emptyList(),
    val error: String? = null,
    val doneMessage: String? = null,
    val createdExamId: String? = null,
) {
    /** Rows resolved against the class roster, using the current mapping. */
    val matching: ExamImportRows
        get() {
            val table = table ?: return ExamImportRows()
            val plan = plan ?: return ExamImportRows()
            return ExamImportModel.matchRows(
                table = table,
                plan = plan,
                candidates = roster.map { ImportCandidate(it.id, it.name, it.rollNumber) },
            )
        }

    val mappingError: String?
        get() {
            val plan = plan ?: return null
            return if (plan.canIdentify) {
                null
            } else {
                "Pick the column that holds the student name or roll number."
            }
        }

    val subjectError: String?
        get() {
            val subjects = plan?.subjects ?: return null
            if (subjects.none { it.included }) return "Choose at least one subject column."
            val bad = subjects.firstOrNull { it.included && !it.isValid }
            return bad?.let { "Check the max and pass marks for “${it.name}”." }
        }

    val canCommit: Boolean
        get() = phase == ExamImportPhase.MAPPING &&
            examName.isNotBlank() &&
            rangeError == null &&
            !reloading &&
            mappingError == null &&
            subjectError == null &&
            matching.rows.isNotEmpty()
}

class ExamImportViewModel(
    private val context: Context,
    private val classId: String,
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExamImportUiState())
    val state: StateFlow<ExamImportUiState> = _state

    /** The picked file, kept so a sheet or row change can re-read it. */
    private var bytes: ByteArray? = null
    private var parseJob: Job? = null

    init {
        viewModelScope.launch {
            studentRepository.observeInClass(classId).collect { students ->
                _state.update { it.copy(roster = students) }
            }
        }
    }

    fun onFilePicked(uri: Uri) {
        parseJob?.cancel()
        bytes = null
        _state.value = _state.value.copy(phase = ExamImportPhase.PARSING, error = null)
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw ImportFileException("We couldn’t open that file.")
                val displayName = withContext(Dispatchers.IO) { resolveDisplayName(uri) }
                val parsed = withContext(Dispatchers.Default) {
                    ImportParsers.parseWorkbook(displayName, data)
                }
                bytes = data
                val plan = ExamImportModel.plan(parsed.table)
                _state.update {
                    it.copy(
                        phase = ExamImportPhase.MAPPING,
                        fileName = displayName,
                        table = parsed.table,
                        sheets = parsed.sheets,
                        sheetName = parsed.sheetName,
                        startRow = "",
                        endRow = "",
                        rangeError = null,
                        reloading = false,
                        examName = defaultExamName(displayName),
                        plan = plan,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = ExamImportPhase.IDLE,
                        error = e.message ?: "We couldn’t read that file. Try another one.",
                    )
                }
            }
        }
    }

    fun onSheetSelected(name: String) {
        if (name == _state.value.sheetName) return
        _state.update { it.copy(sheetName = name) }
        reparse()
    }

    fun onStartRowChanged(text: String) {
        _state.update { it.copy(startRow = text.filter(Char::isDigit).take(7)) }
        reparse()
    }

    fun onEndRowChanged(text: String) {
        _state.update { it.copy(endRow = text.filter(Char::isDigit).take(7)) }
        reparse()
    }

    fun onExamName(name: String) = _state.update { it.copy(examName = name) }

    fun onNameColumn(index: Int) = _state.update { state ->
        val plan = state.plan ?: return@update state
        val roll = if (index == plan.rollColumn) -1 else plan.rollColumn
        state.copy(plan = plan.copy(nameColumn = index, rollColumn = roll))
    }

    fun onRollColumn(index: Int) = _state.update { state ->
        val plan = state.plan ?: return@update state
        val name = if (index == plan.nameColumn) -1 else plan.nameColumn
        state.copy(plan = plan.copy(rollColumn = index, nameColumn = name))
    }

    fun onSubjectIncluded(column: Int, included: Boolean) = updateSubject(column) {
        it.copy(included = included)
    }

    fun onSubjectName(column: Int, name: String) = updateSubject(column) {
        it.copy(name = name)
    }

    fun onSubjectMax(column: Int, maxMarks: String) = updateSubject(column) {
        it.copy(maxMarks = maxMarks.filter(Char::isDigit).take(4))
    }

    fun onSubjectPass(column: Int, passMarks: String) = updateSubject(column) {
        it.copy(passMarks = passMarks.filter(Char::isDigit).take(4))
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun reset() {
        parseJob?.cancel()
        bytes = null
        _state.value = ExamImportUiState()
    }

    /**
     * Re-reads the picked file with the current sheet and row window. The
     * mapping screen stays put: a bad range becomes an inline message, not a
     * trip back to the start. Any max/pass edits survive a re-read when the
     * subject header is still there.
     */
    private fun reparse() {
        val data = bytes ?: return
        val snapshot = _state.value
        val fileName = snapshot.fileName ?: return
        val start = snapshot.startRow.toIntOrNull()
        val end = snapshot.endRow.toIntOrNull()
        val error = when {
            snapshot.startRow.isNotBlank() && start == null -> "Start row must be a number."
            snapshot.endRow.isNotBlank() && end == null -> "End row must be a number."
            start != null && start < 1 -> "Start row starts at 1."
            end != null && end < 1 -> "End row must be 1 or more."
            start != null && end != null && end < start ->
                "End row must be at or after row $start."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(rangeError = error) }
            return
        }

        val previous = snapshot.plan?.subjects.orEmpty().associateBy { it.name.lowercase() }
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _state.update { it.copy(reloading = true, rangeError = null) }
            try {
                val parsed = withContext(Dispatchers.Default) {
                    ImportParsers.parseWorkbook(
                        fileName = fileName,
                        bytes = data,
                        range = ImportRange(
                            sheetName = snapshot.sheetName,
                            startRow = start,
                            endRow = end,
                        ),
                    )
                }
                val fresh = ExamImportModel.plan(parsed.table)
                val merged = fresh.copy(
                    subjects = fresh.subjects.map { draft ->
                        previous[draft.name.lowercase()]?.let { old ->
                            draft.copy(
                                maxMarks = old.maxMarks,
                                passMarks = old.passMarks,
                                included = old.included,
                            )
                        } ?: draft
                    },
                )
                _state.update {
                    it.copy(table = parsed.table, plan = merged, reloading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        reloading = false,
                        rangeError = e.message ?: "We couldn’t read those rows.",
                    )
                }
            }
        }
    }

    fun commit() {
        val snapshot = _state.value
        if (!snapshot.canCommit) return
        val table = snapshot.table ?: return
        val plan = snapshot.plan ?: return
        _state.value = snapshot.copy(phase = ExamImportPhase.COMMITTING)
        viewModelScope.launch {
            var examToUndo: com.example.helper.coredata.entity.Exam? = null
            try {
                val matching = withContext(Dispatchers.Default) {
                    ExamImportModel.matchRows(
                        table = table,
                        plan = plan,
                        candidates = snapshot.roster.map {
                            ImportCandidate(it.id, it.name, it.rollNumber)
                        },
                    )
                }
                val writes = withContext(Dispatchers.Default) {
                    ExamImportModel.writePlan(
                        classId = classId,
                        examName = snapshot.examName,
                        rows = matching,
                        subjects = plan.subjects,
                        now = System.currentTimeMillis(),
                        newId = { UUID.randomUUID().toString() },
                    )
                }
                examToUndo = writes.exam
                examRepository.createExam(writes.exam)
                examRepository.upsertSubjects(writes.subjects)
                examRepository.upsertMarks(writes.marks)

                val notes = buildList {
                    if (matching.unmatched > 0) {
                        add("${matching.unmatched} row${plural(matching.unmatched)} didn’t match a student")
                    }
                    if (matching.duplicates > 0) {
                        add("${matching.duplicates} duplicate row${plural(matching.duplicates)} skipped")
                    }
                    if (matching.unreadableMarks > 0) {
                        add("${matching.unreadableMarks} mark${plural(matching.unreadableMarks)} weren’t numbers")
                    }
                    if (writes.clampedMarks > 0) {
                        add("${writes.clampedMarks} mark${plural(writes.clampedMarks)} above the maximum were capped")
                    }
                }
                val base = "Imported “${writes.exam.name}” — " +
                    "${writes.subjects.size} subject${plural(writes.subjects.size)}, " +
                    "${matching.rows.size} student${plural(matching.rows.size)}"
                _state.value = _state.value.copy(
                    phase = ExamImportPhase.DONE,
                    createdExamId = writes.exam.id,
                    doneMessage = if (notes.isEmpty()) base else "$base. ${notes.joinToString(". ")}.",
                )
            } catch (_: Exception) {
                // The three writes are separate calls; if a later one failed,
                // deleting the exam cascades its subjects and marks away so the
                // teacher is never left with a half-imported exam.
                examToUndo?.let { runCatching { examRepository.deleteExam(it) } }
                _state.value = _state.value.copy(
                    phase = ExamImportPhase.MAPPING,
                    error = "The import didn’t finish. Nothing was saved — try again.",
                )
            }
        }
    }

    private fun updateSubject(
        column: Int,
        transform: (ExamImportSubjectDraft) -> ExamImportSubjectDraft,
    ) = _state.update { state ->
        val plan = state.plan ?: return@update state
        state.copy(
            plan = plan.copy(
                subjects = plan.subjects.map { subject ->
                    if (subject.column == column) transform(subject) else subject
                },
            ),
        )
    }

    private fun plural(count: Int) = if (count == 1) "" else "s"

    /** The file name without its extension becomes the exam's default name. */
    private fun defaultExamName(fileName: String): String {
        val stem = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        if (stem.isEmpty()) return "Imported exam"
        return stem.replaceFirstChar { it.uppercase() }
    }

    /** Human-readable file name from the SAF URI, for parse dispatch + UI. */
    private fun resolveDisplayName(uri: Uri): String = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull() ?: "file"
}
