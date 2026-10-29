package com.example.helper.examsresults.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helper.examsresults.viewmodel.ExamListViewModel
import com.example.helper.examsresults.viewmodel.ExamWithClass
import com.example.helper.motion.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListScreen(
    viewModel: ExamListViewModel,
    onCreateExam: () -> Unit,
    onOpenExam: (examId: String, classId: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var examBeingDeleted by remember { mutableStateOf<ExamWithClass?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exams & Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateExam) {
                Icon(Icons.Filled.Add, contentDescription = "Create Exam")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(state.exams) { examWithClass ->
                ExamRow(
                    examWithClass = examWithClass,
                    onClick = { onOpenExam(examWithClass.exam.id, examWithClass.exam.classId) },
                    onDelete = { examBeingDeleted = examWithClass },
                )
                HorizontalDivider()
            }
            if (state.exams.isEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(Motion.Effects.Default) + slideInVertically(Motion.Spatial.FastOffset) { it / 4 },
                        exit = fadeOut(Motion.Effects.Default),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No exams yet", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    examBeingDeleted?.let { exam ->
        AlertDialog(
            onDismissRequest = { examBeingDeleted = null },
            title = { Text("Delete ${exam.exam.name}?") },
            text = {
                Text(
                    "This deletes the exam, its subjects and every mark entered for it. " +
                        "This can’t be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        examBeingDeleted = null
                        viewModel.deleteExam(exam.exam)
                    }
                ) { Text("Delete exam", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { examBeingDeleted = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ExamRow(
    examWithClass: ExamWithClass,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp),
        ) {
            Text(examWithClass.exam.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${examWithClass.className} • ${formatDate(examWithClass.exam.examDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options for ${examWithClass.exam.name}",
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

private fun formatDate(epoch: Long?): String {
    if (epoch == null) return ""
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(epoch))
}
