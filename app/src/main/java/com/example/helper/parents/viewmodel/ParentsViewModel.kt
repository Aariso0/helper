package com.example.helper.parents.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.coredata.entity.ContactLogEntry
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.AnnouncementRepository
import com.example.helper.coredata.repository.ContactLogRepository
import com.example.helper.parents.pdfstore.AnnouncementPdfStore
import java.io.File
import java.io.IOException
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

/** One date format for every parent-facing label (parents card: dates shown). */
internal val parentsDateDisplay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

internal fun dateLabelFor(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(epochDay).format(parentsDateDisplay)
    }
}

/** Plain-text form of an announcement — share sheet AND clipboard both use it. */
internal fun announcementText(title: String?, body: String): String {
    val trimmedTitle = title?.trim().orEmpty()
    return if (trimmedTitle.isEmpty()) body else "$trimmedTitle\n\n$body"
}

// ── Tab root: announcements history + contact log ───────────────────────────

/**
 * Parents tab state (card: `ParentsViewModel`): recent-first announcements
 * and contact-log entries observed straight off the repositories, plus the
 * row actions (add entry, delete). Date labels are derived once here so the
 * UI never does date math.
 */
class ParentsViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val contactLogRepository: ContactLogRepository,
) : ViewModel() {

    data class AnnouncementItem(val announcement: Announcement, val dateLabel: String)

    data class EntryItem(val entry: ContactLogEntry, val dateLabel: String)

    data class UiState(
        val loading: Boolean = true,
        val announcements: List<AnnouncementItem> = emptyList(),
        val entries: List<EntryItem> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        announcementRepository.observeAnnouncements(),
        contactLogRepository.observeEntries(),
    ) { announcements, entries ->
        UiState(
            loading = false,
            // Repositories already sort recent-first; labels ride along.
            announcements = announcements.map { AnnouncementItem(it, dateLabelFor(it.dateEpochDay)) },
            entries = entries.map { EntryItem(it, dateLabelFor(it.dateEpochDay)) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    /** Teacher logs a call/message they already received (nothing is sent). */
    fun addEntry(student: Student, text: String, dateEpochDay: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            contactLogRepository.logEntry(student, trimmed, dateEpochDay)
        }
    }

    fun deleteEntry(entry: ContactLogEntry) {
        viewModelScope.launch { contactLogRepository.delete(entry) }
    }

    fun deleteAnnouncement(announcement: Announcement) {
        viewModelScope.launch { announcementRepository.deleteAnnouncement(announcement) }
    }
}

// ── Announcement compose/edit takeover ──────────────────────────────────────

/**
 * Announcement draft state (card: `ParentsViewModel` — announcement draft +
 * template edits). Same lifecycle as homework's compose flow (rule 5
 * precedent): the Announcement row is created lazily on the first non-blank
 * body and upserted on every later change; backing out without sharing
 * deletes the row, sharing keeps it as history. Templates are ordinary rows
 * — applying one fills the draft and touches [AnnouncementTemplate.lastUsedAt]
 * (the DAO's recent-first order); deleting one never touches announcements.
 */
class AnnouncementEditViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val pdfStore: AnnouncementPdfStore,
) : ViewModel() {

    data class UiState(
        val classId: String? = null,
        val className: String? = null,
        val title: String = "",
        val body: String = "",
        val renderingPdf: Boolean = false,
    ) {
        val canShare: Boolean get() = body.isNotBlank()
        val hasContent: Boolean get() = classId != null || title.isNotBlank() || body.isNotBlank()
    }

    sealed interface Event {
        /** Share sheet should receive this rendered PDF (+ caption text). */
        data class SharePdf(val file: File) : Event
        data class ShowError(val message: String) : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Recent-first template chips (DAO order: last-used first, never-used after). */
    val templates: StateFlow<List<AnnouncementTemplate>> = announcementRepository
        .observeTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private var announcementRow: Announcement? = null
    private var shared = false

    /** Serializes row create/update (same mutex pattern as homework). */
    private val mutex = Mutex()

    // ── Field edits (each persists immediately) ────────────────────────────
    fun onClassPicked(schoolClass: SchoolClass) {
        _uiState.update { it.copy(classId = schoolClass.id, className = schoolClass.name) }
        persistRow()
    }

    fun onClassCleared() {
        _uiState.update { it.copy(classId = null, className = null) }
        persistRow()
    }

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(title = value) }
        persistRow()
    }

    fun onBodyChanged(value: String) {
        _uiState.update { it.copy(body = value) }
        persistRow()
    }

    private fun persistRow() {
        val s = _uiState.value
        if (s.body.isBlank()) return
        viewModelScope.launch { mutex.withLock { ensureRowLocked() } }
    }

    /** Create-or-update inside the mutex; null while the body is blank. */
    private suspend fun ensureRowLocked(): Announcement? {
        val s = _uiState.value
        val body = s.body.trim()
        if (body.isEmpty()) return null
        val title = s.title.trim().ifEmpty { null }
        val existing = announcementRow
        return if (existing != null) {
            val className = s.className
            existing.copy(
                classId = s.classId,
                className = className,
                title = title,
                body = body,
            ).also {
                announcementRepository.upsertAnnouncement(it)
                announcementRow = it
            }
        } else {
            val schoolClass = s.classId?.let { id ->
                // The picker handed us the object once; row creation only
                // denormalizes its id + name (createAnnouncement copies those
                // two fields — it never writes the class row itself).
                val now = System.currentTimeMillis()
                SchoolClass(id = id, name = s.className.orEmpty(), createdAt = now, updatedAt = now)
            }
            announcementRepository.createAnnouncement(
                schoolClass = schoolClass,
                title = title,
                body = body,
                dateEpochDay = LocalDate.now().toEpochDay(),
            ).also { announcementRow = it }
        }
    }

    // ── Templates ──────────────────────────────────────────────────────────
    /** Fill the draft from a template (caller confirms overwrite when dirty). */
    fun onApplyTemplate(template: AnnouncementTemplate) {
        _uiState.update {
            it.copy(title = template.title.orEmpty(), body = template.body)
        }
        viewModelScope.launch { announcementRepository.markTemplateUsed(template.id) }
        persistRow()
    }

    /** Save the current draft as a reusable template (README 3.5). */
    fun onSaveAsTemplate(templateTitle: String?) {
        val s = _uiState.value
        if (s.body.isBlank()) return
        val title = templateTitle?.trim()?.ifEmpty { null } ?: s.title.trim().ifEmpty { null }
        viewModelScope.launch { announcementRepository.createTemplate(title, s.body.trim()) }
    }

    fun onUpdateTemplate(template: AnnouncementTemplate, title: String?, body: String) {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) return
        viewModelScope.launch {
            announcementRepository.upsertTemplate(
                template.copy(title = title?.trim()?.ifEmpty { null }, body = trimmedBody),
            )
        }
    }

    fun onDeleteTemplate(template: AnnouncementTemplate) {
        // Announcements are unrelated rows — deleting a template touches nothing else.
        viewModelScope.launch { announcementRepository.deleteTemplate(template) }
    }

    // ── Share / copy / PDF / close ─────────────────────────────────────────
    fun currentShareText(): String? {
        val s = _uiState.value
        if (!s.canShare) return null
        return announcementText(s.title, s.body.trim())
    }

    fun currentSubject(): String =
        _uiState.value.title.trim().ifEmpty { "Announcement" }

    fun onShared() {
        shared = true
    }

    /**
     * Render the draft to a PDF on the IO dispatcher; the result comes back
     * as [Event.SharePdf] for the UI to hand to the share sheet. Never blocks
     * the main thread, never crashes on IO failure.
     */
    fun shareAsPdf() {
        val s = _uiState.value
        if (!s.canShare || s.renderingPdf) return
        _uiState.update { it.copy(renderingPdf = true) }
        val title = s.title.trim().ifEmpty { null }
        val body = s.body.trim()
        val contextLine = listOfNotNull(
            s.className,
            LocalDate.now().format(parentsDateDisplay),
        ).joinToString(" · ").ifEmpty { null }
        viewModelScope.launch(Dispatchers.IO) {
            val file = try {
                pdfStore.render(title = title, body = body, contextLine = contextLine)
            } catch (_: IOException) {
                _uiState.update { it.copy(renderingPdf = false) }
                _events.tryEmit(Event.ShowError("Couldn't create the PDF"))
                return@launch
            } catch (_: RuntimeException) {
                // Malformed text can make platform layout throw — report, don't crash.
                _uiState.update { it.copy(renderingPdf = false) }
                _events.tryEmit(Event.ShowError("Couldn't create the PDF"))
                return@launch
            }
            _uiState.update { it.copy(renderingPdf = false) }
            _events.tryEmit(Event.SharePdf(file))
        }
    }

    /**
     * Leave the takeover: discard an unshared draft (row only — PDFs are
     * derivable artifacts, the announcement row is the source of truth), then
     * blank the state so the next "New announcement" starts clean.
     */
    fun close() {
        val row = announcementRow
        val wasShared = shared
        viewModelScope.launch {
            if (row != null && !wasShared) {
                announcementRepository.deleteAnnouncement(row)
            }
        }
        announcementRow = null
        shared = false
        _uiState.value = UiState()
    }
}

// ── Manual-DI providers (same pattern as the other modules) ────────────────

@Composable
fun parentsViewModel(key: String = "parents-tab"): ParentsViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        ParentsViewModel(container.announcementRepository, container.contactLogRepository)
    }
}

@Composable
fun announcementEditViewModel(key: String = "parents-announcement-edit"): AnnouncementEditViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        AnnouncementEditViewModel(
            announcementRepository = app.container.announcementRepository,
            pdfStore = AnnouncementPdfStore(app),
        )
    }
}
