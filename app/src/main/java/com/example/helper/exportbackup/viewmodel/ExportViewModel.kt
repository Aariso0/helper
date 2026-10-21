package com.example.helper.exportbackup.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.exportbackup.backupio.BackupRestorer
import com.example.helper.exportbackup.backupio.BackupWriter
import com.example.helper.exportbackup.backupio.RestoreFailed
import com.example.helper.exportbackup.backupio.RestoreRejected
import com.example.helper.exportbackup.summarize.MonthlySummary
import com.example.helper.exportbackup.summarize.buildMonthlySummary
import com.example.helper.exportbackup.writers.SummaryCsvWriter
import com.example.helper.exportbackup.writers.SummaryExcelWriter
import com.example.helper.exportbackup.writers.SummaryPdfWriter
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The three report formats (card: `writers/`). MIME + extension live here. */
enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    CSV("CSV", "text/csv", "csv"),
    EXCEL("Excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("PDF", "application/pdf", "pdf"),
}

/** What is keeping the screen busy; the UI disables actions while not Idle. */
sealed interface Work {
    data object Idle : Work
    data class Exporting(val format: ExportFormat) : Work
    data object BackingUp : Work
    data object InspectingBackup : Work
    data object Restoring : Work
}

/** Everything the restore confirmation needs, read from the manifest only. */
data class RestorePreview(
    val uri: Uri,
    val createdAtLabel: String,
    val schemaVersion: Int,
    val rowCount: Int,
    val fileCount: Int,
    val tableCounts: List<Pair<String, Int>>,
)

/**
 * Result of a restore attempt. Both success and failure need a process
 * restart: success because the DB file was replaced, failure because the
 * database was already closed before the swap.
 */
data class RestoreOutcome(val success: Boolean, val message: String)

/**
 * Reports & backup screen state (`export-backup` card).
 *
 * Scope is month → class (the summary is always a month). Everything shown is
 * derived from repository rows through the one pure aggregation function, so
 * the screen and the three writers can never disagree. Exports and backup go
 * through SAF: this ViewModel only ever receives the destination [Uri].
 */
class ExportViewModel(
    private val context: Context,
    private val classRepository: SchoolClassRepository,
    private val studentRepository: StudentRepository,
    private val attendanceRepository: AttendanceRepository,
    private val backupWriter: BackupWriter,
    private val backupRestorer: BackupRestorer,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val month: YearMonth = YearMonth.now().minusMonths(1),
        val monthLabel: String = "",
        val isCurrentMonth: Boolean = false,
        val selectedClassId: String? = null,
        val scopeLabel: String = "All classes",
        val classes: List<SchoolClass> = emptyList(),
        val summary: MonthlySummary? = null,
        val work: Work = Work.Idle,
        val preview: RestorePreview? = null,
        val outcome: RestoreOutcome? = null,
    ) {
        val canExport: Boolean get() = summary?.hasRecords == true && work == Work.Idle
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Transient notices; the screen shows them in a snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        reload(month = _state.value.month)
    }

    fun previousMonth() = showMonth(_state.value.month.minusMonths(1))

    fun nextMonth() = showMonth(_state.value.month.plusMonths(1))

    fun showThisMonth() = showMonth(YearMonth.now())

    fun selectClass(classId: String?) {
        reload(month = _state.value.month, classId = classId)
    }

    // ── Scope → summary ────────────────────────────────────────────────────

    private fun showMonth(month: YearMonth) {
        // Never let the teacher walk into the future — there is nothing there.
        val capped = if (month.isAfter(YearMonth.now())) YearMonth.now() else month
        reload(month = capped, classId = _state.value.selectedClassId)
    }

    private fun reload(month: YearMonth, classId: String? = _state.value.selectedClassId) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    month = month,
                    monthLabel = month.format(MONTH_FORMAT),
                    isCurrentMonth = month == YearMonth.now(),
                )
            }
            val fromEpochDay = month.atDay(1).toEpochDay()
            val toEpochDay = month.atEndOfMonth().toEpochDay()
            val classes = classRepository.observeAll().first()
            val students = studentRepository.observeAll().first()
            // A class deleted since it was picked falls back to all classes.
            val effectiveClassId = classId?.takeIf { id -> classes.any { it.id == id } }
            val records = classes
                .filter { effectiveClassId == null || it.id == effectiveClassId }
                .flatMap { attendanceRepository.getForClassInRange(it.id, fromEpochDay, toEpochDay) }
            val summary = buildMonthlySummary(
                year = month.year,
                month = month.monthValue,
                classes = classes,
                students = students,
                records = records,
                selectedClassId = effectiveClassId,
            )
            _state.update {
                it.copy(
                    loading = false,
                    selectedClassId = effectiveClassId,
                    scopeLabel = effectiveClassId
                        ?.let { id -> classes.firstOrNull { it.id == id }?.name }
                        ?: "All classes",
                    classes = classes,
                    summary = summary,
                )
            }
        }
    }

    // ── Exports ────────────────────────────────────────────────────────────

    fun suggestedExportFileName(format: ExportFormat): String {
        val state = _state.value
        val scopeSlug = state.scopeLabel
            .lowercase(Locale.ROOT)
            .replace(NON_FILENAME, "-")
            .trim('-')
            .ifBlank { "classes" }
        return "attendance_${state.month}_${scopeSlug}.${format.extension}"
    }

    fun suggestedBackupFileName(): String =
        "helper-backup-${java.time.LocalDateTime.now().format(BACKUP_FILE_FORMAT)}.zip"

    fun export(format: ExportFormat, uri: Uri) {
        val state = _state.value
        val summary = state.summary ?: return
        if (!summary.hasRecords) return
        val monthLabel = state.monthLabel
        val scopeLabel = state.scopeLabel
        viewModelScope.launch {
            _state.update { it.copy(work = Work.Exporting(format)) }
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("The chosen location could not be opened.")
                    output.use { stream ->
                        when (format) {
                            ExportFormat.CSV -> SummaryCsvWriter.write(summary, monthLabel, scopeLabel, stream)
                            ExportFormat.EXCEL -> SummaryExcelWriter.write(summary, monthLabel, scopeLabel, stream)
                            ExportFormat.PDF -> SummaryPdfWriter.write(summary, monthLabel, scopeLabel, stream)
                        }
                    }
                }
                _messages.send("${format.label} export saved.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _messages.send("Couldn't save the ${format.label} export. Try another location.")
            } finally {
                _state.update { it.copy(work = Work.Idle) }
            }
        }
    }

    // ── Backup ─────────────────────────────────────────────────────────────

    fun backup(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(work = Work.BackingUp) }
            try {
                val manifest = backupWriter.write(uri)
                val rows = manifest.tableCounts.values.sum()
                _messages.send("Backup saved — $rows records and ${manifest.fileCount} files.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _messages.send("Couldn't save the backup. Try another location.")
            } finally {
                _state.update { it.copy(work = Work.Idle) }
            }
        }
    }

    // ── Restore ────────────────────────────────────────────────────────────

    fun inspectBackup(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(work = Work.InspectingBackup) }
            try {
                val manifest = backupRestorer.inspect(uri)
                _state.update {
                    it.copy(
                        work = Work.Idle,
                        preview = RestorePreview(
                            uri = uri,
                            createdAtLabel = manifest.createdAtMillis
                                .let(java.time.Instant::ofEpochMilli)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(BACKUP_DATE_FORMAT),
                            schemaVersion = manifest.schemaVersion,
                            rowCount = manifest.tableCounts.values.sum(),
                            fileCount = manifest.fileCount,
                            tableCounts = manifest.tableCounts.toList(),
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: RestoreRejected) {
                _state.update { it.copy(work = Work.Idle) }
                _messages.send(rejected.message ?: "This backup can't be restored.")
            } catch (_: Exception) {
                _state.update { it.copy(work = Work.Idle) }
                _messages.send("Couldn't read that file. Choose a Helper backup (.zip).")
            }
        }
    }

    fun dismissPreview() {
        _state.update { it.copy(preview = null) }
    }

    fun confirmRestore() {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            _state.update { it.copy(preview = null, work = Work.Restoring) }
            try {
                // A restore must finish once the DB file is replaced; do not
                // let a cleared ViewModel cancel it halfway through the swap.
                withContext(NonCancellable) { backupRestorer.restore(preview.uri) }
                _state.update {
                    it.copy(
                        work = Work.Idle,
                        outcome = RestoreOutcome(
                            success = true,
                            message = "Your backup was restored. The app will restart with it.",
                        ),
                    )
                }
            } catch (rejected: RestoreRejected) {
                // Nothing was touched — the screen stays usable.
                _state.update { it.copy(work = Work.Idle) }
                _messages.send(rejected.message ?: "This backup can't be restored.")
            } catch (failed: RestoreFailed) {
                _state.update {
                    it.copy(
                        work = Work.Idle,
                        outcome = RestoreOutcome(
                            success = false,
                            message = failed.message ?: "Restore failed. Reopen the app to continue.",
                        ),
                    )
                }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        work = Work.Idle,
                        outcome = RestoreOutcome(
                            success = false,
                            message = "Restore failed. Reopen the app to continue.",
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        val BACKUP_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
        val BACKUP_FILE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
        val NON_FILENAME = Regex("[^a-z0-9]+")
    }
}

@Composable
fun exportViewModel(): ExportViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel {
        val container = app.container
        ExportViewModel(
            context = app,
            classRepository = container.classRepository,
            studentRepository = container.studentRepository,
            attendanceRepository = container.attendanceRepository,
            backupWriter = BackupWriter(app, container),
            backupRestorer = BackupRestorer(app, container),
        )
    }
}
