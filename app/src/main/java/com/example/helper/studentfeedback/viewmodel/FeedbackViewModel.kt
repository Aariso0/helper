package com.example.helper.studentfeedback.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.helper.coredata.entity.FeedbackNote
import com.example.helper.coredata.entity.FeedbackTag
import com.example.helper.coredata.entity.Student
import com.example.helper.coredata.repository.FeedbackRepository
import com.example.helper.coredata.repository.StudentRepository
import com.example.helper.studentfeedback.seed.SeedTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedbackUiState(
    val notes: List<FeedbackNote> = emptyList(),
    val tags: List<FeedbackTag> = emptyList(),
    val studentsById: Map<String, Student> = emptyMap(),
    val query: String = "",
    val selectedTagId: String? = null,
    val dateFilter: FeedbackDateFilter = FeedbackDateFilter.ALL,
) {
    val isEmpty: Boolean get() = notes.isEmpty() && query.isBlank() && selectedTagId == null
}

/** Quick date ranges for the list's date filter (card: "date filters"). */
enum class FeedbackDateFilter(val label: String) {
    ALL("All time"),
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
}

/**
 * One ViewModel class for all three surfaces (card: single viewmodel/):
 * - quick-note instance ([studentIdPreset] set): commit-on-tap capture
 * - feedback-list instance: searchable/filterable list
 * - timeline instance ([studentIdPreset] set): read-only history for roster
 * Screens pass distinct `key`s to `viewModel {}` so instances never collide.
 */
class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    studentRepository: StudentRepository,
    private val studentIdPreset: String? = null,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedTagIdFlow = MutableStateFlow<String?>(null)
    private val dateFilterFlow = MutableStateFlow(FeedbackDateFilter.ALL)

    private data class Filters(
        val query: String,
        val selectedTagId: String?,
        val dateFilter: FeedbackDateFilter,
    )

    private val filtersFlow = combine(queryFlow, selectedTagIdFlow, dateFilterFlow) { q, tagId, date ->
        Filters(q, tagId, date)
    }

    val uiState: StateFlow<FeedbackUiState> = combine(
        feedbackRepository.observeNotes(),
        feedbackRepository.observeTags(),
        studentRepository.observeAll(),
        filtersFlow,
    ) { notes, tags, students, filters ->
        val studentsById = students.associateBy { it.id }
        val today = java.time.LocalDate.now().toEpochDay()
        val minEpochDay = when (filters.dateFilter) {
            FeedbackDateFilter.ALL -> Long.MIN_VALUE
            FeedbackDateFilter.TODAY -> today
            FeedbackDateFilter.WEEK -> today - 6
            FeedbackDateFilter.MONTH -> today - 29
        }
        val visible = notes
            .filter { studentIdPreset == null || it.studentId == studentIdPreset }
            .filter { filters.selectedTagId == null || it.tagId == filters.selectedTagId }
            .filter { it.dateEpochDay >= minEpochDay }
            .filter { note ->
                filters.query.isBlank() || note.matches(filters.query, studentsById[note.studentId]?.name)
            }
        FeedbackUiState(
            notes = visible,
            tags = tags,
            studentsById = studentsById,
            query = filters.query,
            selectedTagId = filters.selectedTagId,
            dateFilter = filters.dateFilter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedbackUiState())

    val draftText = MutableStateFlow("")

    init {
        viewModelScope.launch { feedbackRepository.ensureTags(SeedTags.ALL) }
    }

    fun onQueryChanged(value: String) {
        queryFlow.value = value
    }

    fun onTagFilterSelected(tagId: String?) {
        selectedTagIdFlow.value = if (selectedTagIdFlow.value == tagId) null else tagId
    }

    fun onDateFilterSelected(dateFilter: FeedbackDateFilter) {
        dateFilterFlow.value = dateFilter
    }

    /**
     * Commit-on-tap (card "Why this shape"): the tap IS the save; the caller
     * offers Undo via [undo] with the returned note.
     */
    suspend fun commitNote(tag: FeedbackTag, todayEpochDay: Long): FeedbackNote {
        val note = feedbackRepository.addNote(
            studentId = studentIdPreset ?: error("commitNote needs a studentId preset"),
            tag = tag,
            tagLabel = tag.label,
            text = draftText.value,
            dateEpochDay = todayEpochDay,
        )
        draftText.value = ""
        return note
    }

    suspend fun undo(noteId: String) = feedbackRepository.deleteNote(noteId)

    suspend fun addTag(label: String) {
        if (label.isNotBlank()) feedbackRepository.addTag(label)
    }

    suspend fun renameTag(tagId: String, label: String) = feedbackRepository.renameTag(tagId, label)

    suspend fun deleteTag(tagId: String) = feedbackRepository.deleteTag(tagId)

    private fun FeedbackNote.matches(query: String, studentName: String?): Boolean {
        val q = query.trim()
        return tagLabel.contains(q, ignoreCase = true) ||
            text.orEmpty().contains(q, ignoreCase = true) ||
            studentName.orEmpty().contains(q, ignoreCase = true)
    }
}
