package com.example.helper.studentfeedback.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.helper.studentfeedback.viewmodel.feedbackViewModel
import kotlinx.coroutines.launch

/**
 * The preset-tag capture flow (card: QuickNoteScreen).
 * Typed params: studentId + onDone. Tapping a preset tag IS committing the
 * note (with whatever one-liner is typed); an Undo snackbar covers mis-taps.
 * No Save button — that would add a tap to every note (design rule 1).
 * Tags are edited in place on the same row: long-press to rename/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteScreen(
    studentId: String,
    onDone: () -> Unit,
) {
    val viewModel = feedbackViewModel(key = "quick-note-$studentId", studentIdPreset = studentId)
    val state by viewModel.uiState.collectAsState()
    val draftText by viewModel.draftText.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val studentName = state.studentsById[studentId]?.name
    val today = java.time.LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quick note", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = studentName ?: "Student",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Tap a tag to save the note. Type a line first if you want one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            TagChipRow(
                tags = state.tags,
                onTagClick = { tag ->
                    scope.launch {
                        val note = viewModel.commitNote(tag, today.toEpochDay())
                        val result = snackbarHostState.showSnackbar(
                            message = "Note saved for ${studentName ?: "student"}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        // Undo deletes through the same commit path.
                        if (result == SnackbarResult.ActionPerformed) viewModel.undo(note.id)
                    }
                },
                onAddTag = { label -> scope.launch { viewModel.addTag(label) } },
                onRenameTag = { tag, label -> scope.launch { viewModel.renameTag(tag.id, label) } },
                onDeleteTag = { tag -> scope.launch { viewModel.deleteTag(tag.id) } },
                emptyHint = "No tags yet — add the first one below",
            )

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = draftText,
                onValueChange = { viewModel.draftText.value = it },
                label = { Text("Optional one-liner") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
