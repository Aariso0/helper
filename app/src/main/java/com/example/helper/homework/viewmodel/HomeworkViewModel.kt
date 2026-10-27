package com.example.helper.homework.viewmodel

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.coredata.dao.HomeworkWithAttachments
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.Homework
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.repository.HomeworkRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import com.example.helper.homework.attachmentstore.AttachmentStore
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ── Shared share payload (seam agreed with app-shell) ─────────────────────

private val shareDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** What the share sheet receives: caption text + content-URI attachments. */
data class SharePayload(
    val subject: String,
    val text: String,
    val attachments: List<Attachment>,
)

internal fun buildShareText(
    title: String,
    body: String?,
    dueDateEpochDay: Long?,
): String = buildString {
    append(title)
    if (!body.isNullOrBlank()) append("\n\n$body")
    if (dueDateEpochDay != null) {
        append("\nDue: ${LocalDate.ofEpochDay(dueDateEpochDay).format(shareDateFormat)}")
    }
}

// ── Tab list ───────────────────────────────────────────────────────────────

/**
 * Homework tab state: assignments grouped per class (due soonest first,
 * undated last), labels derived once so the UI never does date math.
 */
class HomeworkListViewModel(
    classRepository: SchoolClassRepository,
    homeworkRepository: HomeworkRepository,
) : ViewModel() {

    data class Item(
        val hw: HomeworkWithAttachments,
        val dueLabel: String,
        val overdue: Boolean,
    )

    data class Section(
        val classId: String,
        val className: String,
        val items: List<Item>,
    )

    data class UiState(
        val loading: Boolean = true,
        val sections: List<Section> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        classRepository.observeAll(),
        homeworkRepository.observeAll(),
    ) { classes, all ->
        val classNames = classes.associate { it.id to it.name }
        val today = LocalDate.now().toEpochDay()
        val byClass = all.groupBy { it.homework.classId }
        UiState(
            loading = false,
            sections = byClass.mapNotNull { (classId, list) ->
                val className = classNames[classId] ?: return@mapNotNull null
                Section(
                    classId = classId,
                    className = className,
                    items = list
                        .map { it.toItem(today) }
                        .sortedWith(
                            compareBy(
                                { it.hw.homework.dueDateEpochDay == null },
                                { it.hw.homework.dueDateEpochDay ?: Long.MAX_VALUE },
                                { it.hw.homework.title },
                            ),
                        ),
                )
            }.sortedBy { it.className },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    private fun HomeworkWithAttachments.toItem(today: Long): Item {
        val due = homework.dueDateEpochDay
        return Item(
            hw = this,
            dueLabel = when {
                due == null -> "No due date"
                due == today -> "Due today"
                due < today -> "Overdue · ${LocalDate.ofEpochDay(due).format(shareDateFormat)}"
                else -> "Due ${LocalDate.ofEpochDay(due).format(shareDateFormat)}"
            },
            overdue = due != null && due < today,
        )
    }

    /** Re-share payload for a saved assignment (row tap on the list). */
    fun sharePayloadFor(item: Item, className: String): SharePayload = SharePayload(
        subject = "Homework · $className",
        text = buildShareText(
            title = item.hw.homework.title,
            body = item.hw.homework.text,
            dueDateEpochDay = item.hw.homework.dueDateEpochDay,
        ),
        attachments = item.hw.attachments,
    )
}

// ── Compose/edit takeover ──────────────────────────────────────────────────

/**
 * Homework draft state (rule 5: commits as it's typed). The Homework row is
 * created lazily on the first class+title combination; every later change
 * upserts it. Attachment bytes are copied immediately and their rows added
 * through the repository. Abandoning an unshared draft deletes the row
 * (cascading attachment rows) and the copied files — the only lifecycle this
 * module owns.
 */
class HomeworkEditViewModel(
    private val classRepository: SchoolClassRepository,
    private val homeworkRepository: HomeworkRepository,
    private val attachmentStore: AttachmentStore,
) : ViewModel() {

    data class UiState(
        val classId: String? = null,
        val className: String? = null,
        val title: String = "",
        val text: String = "",
        val dueDateEpochDay: Long? = null,
        val attachments: List<Attachment> = emptyList(),
        val importing: Boolean = false,
    ) {
        val canShare: Boolean get() = classId != null && title.isNotBlank()
        val hasContent: Boolean
            get() = classId != null || title.isNotBlank() || text.isNotBlank() || attachments.isNotEmpty()
    }

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var homeworkRow: Homework? = null
    private var shared = false

    /** Serializes row create/update + attachment add/remove. */
    private val mutex = Mutex()

    // ── Field edits (each persists immediately) ────────────────────────────
    fun onClassPicked(schoolClass: SchoolClass) {
        _uiState.update { it.copy(classId = schoolClass.id, className = schoolClass.name) }
        persistRow()
    }

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(title = value) }
        persistRow()
    }

    fun onTextChanged(value: String) {
        _uiState.update { it.copy(text = value) }
        persistRow()
    }

    fun onDueSelected(dateEpochDay: Long) {
        _uiState.update { it.copy(dueDateEpochDay = dateEpochDay) }
        persistRow()
    }

    fun onDueCleared() {
        _uiState.update { it.copy(dueDateEpochDay = null) }
        persistRow()
    }

    private fun persistRow() {
        val s = _uiState.value
        if (s.classId == null || (s.title.isBlank() && homeworkRow == null)) return
        viewModelScope.launch { mutex.withLock { ensureRowLocked() } }
    }

    /** Create-or-update inside the mutex; returns null while incomplete. */
    private suspend fun ensureRowLocked(): Homework? {
        val s = _uiState.value
        val classId = s.classId ?: return null
        if (s.title.isBlank()) return null
        val title = s.title.trim()
        val body = s.text.trim().ifEmpty { null }
        val existing = homeworkRow
        return if (existing != null) {
            existing.copy(title = title, text = body, dueDateEpochDay = s.dueDateEpochDay)
                .also {
                    homeworkRepository.upsertHomework(it)
                    homeworkRow = it
                }
        } else {
            val schoolClass = classRepository.getById(classId) ?: return null
            homeworkRepository.create(schoolClass, title, body, s.dueDateEpochDay)
                .also { homeworkRow = it }
        }
    }

    // ── Attachments ────────────────────────────────────────────────────────
    /** Fresh FileProvider-served target for the system camera (TakePicture). */
    fun newCaptureTarget(): File = attachmentStore.newCaptureTarget()

    fun onCaptured(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            // Verbatim capture bytes keep EXIF; Coil renders the orientation.
            addAttachmentRow(fileName = file.name, file = file, forcedMime = "image/jpeg")
        }
    }

    fun onCaptureCancelled(file: File) {
        attachmentStore.delete(file)
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                val name = attachmentStore.displayNameOf(uri) ?: "file"
                val file = try {
                    attachmentStore.importFrom(uri, name)
                } catch (_: Exception) {
                    // Not just IOException: a picker result whose URI grant was
                    // dropped throws SecurityException. Either way the teacher
                    // must see that the file did not attach.
                    _events.tryEmit(Event.ShowError("Couldn’t attach “$name” — try picking it again."))
                    return@forEach
                }
                addAttachmentRow(fileName = name, file = file)
            }
            _uiState.update { it.copy(importing = false) }
        }
    }

    fun onAttachmentRemoved(attachment: Attachment) {
        viewModelScope.launch {
            val removed = homeworkRepository.deleteAttachment(attachment.id)
            _uiState.update { s -> s.copy(attachments = s.attachments.filterNot { it.id == attachment.id }) }
            removed?.let { attachmentStore.delete(File(it.filePath)) }
        }
    }

    private suspend fun addAttachmentRow(fileName: String, file: File, forcedMime: String? = null) {
        val row = mutex.withLock {
            val hw = ensureRowLocked()
            if (hw == null) {
                _events.tryEmit(Event.ShowError("Type the homework title first"))
                return@withLock null
            }
            homeworkRepository.addAttachment(
                homeworkId = hw.id,
                kind = AttachmentStore.kindFor(fileName),
                fileName = fileName,
                filePath = file.absolutePath,
                mimeType = forcedMime ?: AttachmentStore.mimeFor(fileName),
            )
        } ?: run {
            attachmentStore.delete(file)
            return
        }
        _uiState.update { it.copy(attachments = it.attachments + row) }
    }

    // ── Share / abandon ────────────────────────────────────────────────────
    fun buildShare(): SharePayload? {
        val s = _uiState.value
        if (!s.canShare) return null
        return SharePayload(
            subject = "Homework · ${s.className}",
            text = buildShareText(s.title.trim(), s.text.trim().ifEmpty { null }, s.dueDateEpochDay),
            attachments = s.attachments,
        )
    }

    fun onShared() {
        shared = true
    }

    /**
     * Leave the takeover: discard an unshared draft (row + copied files),
     * then blank the state so the next "New homework" starts clean.
     */
    fun close() {
        val row = homeworkRow
        val attachments = _uiState.value.attachments
        val wasShared = shared
        viewModelScope.launch {
            if (row != null && !wasShared) {
                homeworkRepository.deleteHomework(row.id)
                attachments.forEach { attachmentStore.delete(File(it.filePath)) }
            }
        }
        homeworkRow = null
        shared = false
        _uiState.value = UiState()
    }
}

// ── Manual-DI providers (same pattern as the other modules) ────────────────

@Composable
fun homeworkListViewModel(key: String = "homework-list"): HomeworkListViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        HomeworkListViewModel(container.classRepository, container.homeworkRepository)
    }
}

@Composable
fun homeworkEditViewModel(key: String = "homework-edit"): HomeworkEditViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        HomeworkEditViewModel(
            classRepository = container.classRepository,
            homeworkRepository = container.homeworkRepository,
            attachmentStore = AttachmentStore(app),
        )
    }
}
