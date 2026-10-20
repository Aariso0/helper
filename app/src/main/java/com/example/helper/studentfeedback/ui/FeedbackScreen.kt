package com.example.helper.studentfeedback.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.studentfeedback.viewmodel.FeedbackDateFilter
import com.example.helper.studentfeedback.viewmodel.feedbackViewModel
import kotlinx.coroutines.launch

/**
 * The searchable/filterable note list (card: FeedbackScreen).
 * Typed params: optional [studentIdPreset] + onDone. Filters: free-text
 * search (student name, tag, text), tag chips, and quick date ranges.
 * Read-only — capture happens on QuickNoteScreen (commit-on-tap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onDone: () -> Unit,
    studentIdPreset: String? = null,
) {
    val viewModel = feedbackViewModel(key = "feedback-list-$studentIdPreset")
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val presetName = studentIdPreset?.let { state.studentsById[it]?.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (presetName != null) "Notes · $presetName" else "Feedback") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text("Search name, tag, or text") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            TagChipRow(
                tags = state.tags,
                onTagClick = { tag -> viewModel.onTagFilterSelected(tag.id) },
                onAddTag = { label -> scope.launch { viewModel.addTag(label) } },
                onRenameTag = { tag, label -> scope.launch { viewModel.renameTag(tag.id, label) } },
                onDeleteTag = { tag -> scope.launch { viewModel.deleteTag(tag.id) } },
                selectedTagId = state.selectedTagId,
                modifier = Modifier.padding(horizontal = 16.dp),
                emptyHint = "No tags yet",
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedbackDateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.dateFilter == filter,
                        onClick = { viewModel.onDateFilterSelected(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            HorizontalDivider()

            if (state.isEmpty) {
                EmptyState(query = state.query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    val grouped = state.notes.groupBy { it.dateEpochDay }
                    grouped.forEach { (epochDay, notes) ->
                        item(key = "header-$epochDay") {
                            DateHeader(epochDay)
                        }
                        items(notes, key = { it.id }) { note ->
                            NoteRow(
                                note = note,
                                studentName = if (studentIdPreset == null) {
                                    state.studentsById[note.studentId]?.name
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(epochDay: Long) {
    Text(
        text = epochDay.epochDayToDisplayDate(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoteRow(
    note: com.example.helper.coredata.entity.FeedbackNote,
    studentName: String?,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.tagLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (studentName != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = studentName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!note.text.isNullOrBlank()) {
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (query.isBlank()) "No feedback notes yet" else "No notes match “$query”",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
