package com.example.helper.roster.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.roster.ui.import.ImportFileException
import com.example.helper.roster.ui.import.ImportParsers
import com.example.helper.roster.ui.import.ImportRange
import com.example.helper.roster.ui.import.ParsedTable
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportPhase { IDLE, PARSING, MAPPING, COMMITTING, DONE }

/**
 * Import flow state (roster card: file pick → parse → column-mapping
 * preview → commit). The teacher always sees exactly what will be written
 * before anything touches the database (preview-before-commit pattern).
 */
data class ImportUiState(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String? = null,
    val table: ParsedTable? = null,
    /** Sheets the file offers; empty for a CSV, which has no tabs. */
    val sheets: List<String> = emptyList(),
    val sheetName: String? = null,
    /** Row numbers as typed (1-based, the numbers Excel shows). */
    val startRow: String = "",
    val endRow: String = "",
    val rangeError: String? = null,
    val reloading: Boolean = false,
    val nameColumn: Int = -1,
    val rollColumn: Int = -1,
    val contactColumn: Int = -1,
    val classes: List<SchoolClass> = emptyList(),
    val targetClassId: String? = null,
    val newClassName: String = "",
    val creatingNewClass: Boolean = false,
    val error: String? = null,
    val doneMessage: String? = null,
) {
    /** Rows that will actually import (blank names are skipped, shown greyed). */
    val importableRows: List<List<String>>
        get() {
            val table = table ?: return emptyList()
            if (nameColumn < 0) return emptyList()
            return table.rows.filter { table.cellAt(it, nameColumn).isNotEmpty() }
        }

    val skippedCount: Int
        get() = (table?.rows.orEmpty().size) - importableRows.size

    val targetName: String?
        get() = if (creatingNewClass) {
            newClassName.trim().ifEmpty { null }
        } else {
            classes.firstOrNull { it.id == targetClassId }?.name
        }

    val canCommit: Boolean
        get() {
            val table = table ?: return false
            return phase == ImportPhase.MAPPING &&
                nameColumn in table.headers.indices &&
                targetName != null &&
                // Never import a window the teacher has not successfully loaded.
                rangeError == null &&
                !reloading
        }

    /** First [limit] importable rows mapped for the preview table. */
    fun preview(limit: Int = 5): List<Triple<String, String, String>> {
        val table = table ?: return emptyList()
        if (nameColumn < 0) return emptyList()
        return importableRows.take(limit).map { row ->
            Triple(
                table.cellAt(row, nameColumn),
                if (rollColumn >= 0) table.cellAt(row, rollColumn) else "",
                if (contactColumn >= 0) table.cellAt(row, contactColumn) else "",
            )
        }
    }
}

class ImportViewModel(
    private val context: Context,
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state

    /** The picked file, kept so a sheet or row change can re-read it. */
    private var bytes: ByteArray? = null
    private var parseJob: Job? = null

    private val existingClasses = classRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Mirror the class list into the state the mapping screen renders.
        viewModelScope.launch {
            existingClasses.collect { classes ->
                _state.update { it.copy(classes = classes) }
            }
        }
    }

    fun onFilePicked(uri: Uri) {
        parseJob?.cancel()
        bytes = null
        _state.value = _state.value.copy(phase = ImportPhase.PARSING, error = null)
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
                _state.value = _state.value.copy(
                    phase = ImportPhase.MAPPING,
                    fileName = displayName,
                    table = parsed.table,
                    sheets = parsed.sheets,
                    sheetName = parsed.sheetName,
                    startRow = "",
                    endRow = "",
                    rangeError = null,
                    reloading = false,
                    nameColumn = guessColumn(parsed.table, NAME_HINTS),
                    rollColumn = guessColumn(parsed.table, ROLL_HINTS),
                    contactColumn = guessColumn(parsed.table, CONTACT_HINTS),
                    targetClassId = existingClasses.value.firstOrNull()?.id,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    phase = ImportPhase.IDLE,
                    error = e.message ?: "We couldn’t read that file. Try another one.",
                )
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

    /**
     * Re-reads the picked file with the current sheet and row window. The
     * mapping screen stays put: a bad range becomes an inline message, not a
     * trip back to the start.
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
                _state.update {
                    it.copy(
                        table = parsed.table,
                        reloading = false,
                        nameColumn = guessColumn(parsed.table, NAME_HINTS),
                        rollColumn = guessColumn(parsed.table, ROLL_HINTS),
                        contactColumn = guessColumn(parsed.table, CONTACT_HINTS),
                    )
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

    fun onNameColumn(index: Int) = _state.update { it.copy(nameColumn = index) }
    fun onRollColumn(index: Int) = _state.update { it.copy(rollColumn = index) }
    fun onContactColumn(index: Int) = _state.update { it.copy(contactColumn = index) }
    fun onTargetClass(id: String) =
        _state.update { it.copy(targetClassId = id, creatingNewClass = false) }
    fun onStartNewClass() =
        _state.update { it.copy(creatingNewClass = true, targetClassId = null) }
    fun onNewClassName(name: String) = _state.update { it.copy(newClassName = name) }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun reset() {
        parseJob?.cancel()
        bytes = null
        _state.value = ImportUiState()
    }

    /** Called by the screen after it relayed [ImportUiState.doneMessage]. */
    fun consumeDone() {
        parseJob?.cancel()
        bytes = null
        _state.value = ImportUiState()
    }

    fun commit() {
        val snapshot = _state.value
        if (!snapshot.canCommit) return
        _state.value = snapshot.copy(phase = ImportPhase.COMMITTING)
        viewModelScope.launch {
            try {
                val table = snapshot.table ?: throw ImportFileException("Nothing to import.")
                val targetClass = resolveTarget(snapshot)
                val existing = studentRepository.observeInClass(targetClass.id).first()
                val byRoll = existing
                    .mapNotNull { s -> s.rollNumber?.trim()?.lowercase()?.let { it to s } }
                    .toMap()
                val byName = existing.associate { it.name.trim().lowercase() to it }

                var updated = 0
                val now = System.currentTimeMillis()
                val students = snapshot.importableRows.map { row ->
                    val name = table.cellAt(row, snapshot.nameColumn)
                    val roll = snapshot.rollColumn.takeIf { it >= 0 }
                        ?.let { table.cellAt(row, it).ifEmpty { null } }
                    val contact = snapshot.contactColumn.takeIf { it >= 0 }
                        ?.let { table.cellAt(row, it).ifEmpty { null } }
                    val match = roll?.trim()?.lowercase()?.let { byRoll[it] }
                        ?: byName[name.trim().lowercase()]
                    if (match != null) updated++
                    Student(
                        id = match?.id ?: UUID.randomUUID().toString(),
                        classId = targetClass.id,
                        name = name,
                        rollNumber = roll,
                        parentContact = contact,
                        photoPath = match?.photoPath,
                        createdAt = match?.createdAt ?: now,
                        updatedAt = now,
                    )
                }
                studentRepository.upsertAll(students)
                val updatedNote = if (updated > 0) " ($updated updated)" else ""
                val countLabel = if (students.size == 1) "1 student" else "${students.size} students"
                _state.value = _state.value.copy(
                    phase = ImportPhase.DONE,
                    doneMessage = "Imported $countLabel into ${targetClass.name}$updatedNote",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    phase = ImportPhase.MAPPING,
                    error = e.message ?: "The import didn’t finish. Nothing was saved — try again.",
                )
            }
        }
    }

    private suspend fun resolveTarget(snapshot: ImportUiState): SchoolClass =
        if (snapshot.creatingNewClass) {
            val name = snapshot.newClassName.trim()
            if (name.isEmpty()) throw ImportFileException("Give the new class a name first.")
            runCatching { classRepository.create(name) }
                .getOrElse { throw ImportFileException("A class named “$name” already exists.") }
        } else {
            val id = snapshot.targetClassId
                ?: throw ImportFileException("Choose which class to import into.")
            classRepository.getById(id)
                ?: throw ImportFileException("That class no longer exists — pick another.")
        }

    private fun guessColumn(table: ParsedTable, hints: List<String>): Int =
        table.headers.indexOfFirst { header ->
            val h = header.trim().lowercase()
            hints.any { h.contains(it) }
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

    companion object {
        private val NAME_HINTS = listOf("name", "student")
        private val ROLL_HINTS = listOf("roll", "adm", "no.")
        private val CONTACT_HINTS = listOf("contact", "phone", "mobile", "parent", "father", "mother", "tel")
    }
}
