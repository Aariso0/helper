package com.example.helper.examsresults.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.examsresults.export.ExamExcelWriter
import com.example.helper.examsresults.viewmodel.ExamResultViewModel
import com.example.helper.examsresults.viewmodel.ExportConfig
import com.example.helper.share.asShareableUri
import com.example.helper.share.shareFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    viewModel: ExamResultViewModel,
    onEnterMarks: (subjectId: String) -> Unit,
    onStudentClick: (studentId: String) -> Unit,
    onExamDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val exportConfig by viewModel.exportConfig.collectAsState()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showSubjectPicker by remember { mutableStateOf(false) }
    var showAddSubject by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteExam by remember { mutableStateOf(false) }
    var subjectBeingDeleted by remember { mutableStateOf<ExamSubject?>(null) }
    var subjectBeingEdited by remember { mutableStateOf<ExamSubject?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.examName.ifEmpty { "Results" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete exam",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                showDeleteExam = true
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (state.subjects.isEmpty()) showAddSubject = true else showSubjectPicker = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Enter Marks")
                }
                OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Export to Excel")
                }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Analysis", style = MaterialTheme.typography.titleMedium)
                if (state.subjects.isEmpty()) {
                    Text(
                        "No subjects yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.subjects.forEach { subject ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subject.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "Pass ${subject.passMarks} · Max ${subject.maxMarks}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { subjectBeingEdited = subject }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit ${subject.name}",
                                    )
                                }
                                IconButton(onClick = { subjectBeingDeleted = subject }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${subject.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showAddSubject = true }) {
                    Text("Add subject")
                }
            }
            HorizontalDivider()

            state.results.forEach { result ->
                val failedNames = result.failedSubjects
                    .mapNotNull { id -> state.subjects.firstOrNull { it.id == id }?.name }
                    .ifEmpty { result.failedSubjects }
                val hasFailures = result.failedSubjects.isNotEmpty()
                // A missing mark is "not entered yet", not a fail. Among the
                // entered marks, any failure means FAIL; otherwise PASS even if
                // some subjects are still blank. No marks at all stays neutral.
                val status = when {
                    hasFailures -> "FAIL"
                    result.isPass -> "PASS"
                    else -> "—"
                }
                val statusColor = when {
                    hasFailures -> MaterialTheme.colorScheme.error
                    result.isPass -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Open ${result.student.name}") {
                            onStudentClick(result.student.id)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${result.student.rollNumber ?: ""} ${result.student.name}".trim(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Total: ${result.totalObtained.toInt()}/${result.totalMax.toInt()} | %: ${String.format("%.1f", result.percentage)} | $status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                        )
                        if (hasFailures) {
                            Text(
                                "Failed in: ${failedNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (showSubjectPicker) {
        SubjectPickerDialog(
            subjects = state.subjects,
            onPick = { subjectId ->
                showSubjectPicker = false
                onEnterMarks(subjectId)
            },
            onDismiss = { showSubjectPicker = false },
        )
    }

    if (showAddSubject) {
        AddSubjectDialog(
            onAdd = { name, max, pass -> viewModel.addSubject(name, max, pass) },
            onDismiss = { showAddSubject = false },
        )
    }

    subjectBeingEdited?.let { subject ->
        EditSubjectDialog(
            subject = subject,
            onSave = { max, pass -> viewModel.updateSubject(subject, max, pass) },
            onDismiss = { subjectBeingEdited = null },
        )
    }

    subjectBeingDeleted?.let { subject ->
        AlertDialog(
            onDismissRequest = { subjectBeingDeleted = null },
            title = { Text("Delete ${subject.name}?") },
            text = {
                Text(
                    "This also deletes every mark entered for this subject in this exam. " +
                        "This can’t be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        subjectBeingDeleted = null
                        viewModel.deleteSubject(subject)
                    }
                ) { Text("Delete subject", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { subjectBeingDeleted = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteExam) {
        AlertDialog(
            onDismissRequest = { showDeleteExam = false },
            title = { Text("Delete ${state.examName.ifEmpty { "this exam" }}?") },
            text = {
                Text(
                    "This deletes the exam, its subjects and every mark entered for it. " +
                        "This can’t be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteExam = false
                        viewModel.deleteExam(onExamDeleted)
                    }
                ) { Text("Delete exam", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteExam = false }) { Text("Cancel") }
            },
        )
    }

    if (showExportDialog) {
        ExportConfigDialog(
            config = exportConfig,
            onConfigChange = viewModel::updateExportConfig,
            onExport = {
                showExportDialog = false
                CoroutineScope(Dispatchers.Main).launch {
                    val file = withContext(Dispatchers.IO) {
                        ExamExcelWriter.write(
                            context = context,
                            examName = state.examName,
                            subjects = state.subjects,
                            results = state.results,
                            config = exportConfig,
                        )
                    }
                    val uri = file.asShareableUri(context)
                    shareFiles(context, listOf(uri), mimeTypes = listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

@Composable
private fun SubjectPickerDialog(
    subjects: List<ExamSubject>,
    onPick: (subjectId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a subject") },
        text = {
            Column {
                subjects.forEach { subject ->
                    Text(
                        text = "${subject.name} (out of ${subject.maxMarks})",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(subject.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddSubjectDialog(
    onAdd: (name: String, maxMarks: String, passMarks: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("100") }
    var passMarks by remember { mutableStateOf("35") }
    val maxValid = (maxMarks.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxMarks,
                        onValueChange = { maxMarks = it.filter(Char::isDigit) },
                        label = { Text("Max Marks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = passMarks,
                        onValueChange = { passMarks = it.filter(Char::isDigit) },
                        label = { Text("Pass Marks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(name, maxMarks, passMarks)
                    onDismiss()
                },
                enabled = name.isNotBlank() && maxValid,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditSubjectDialog(
    subject: ExamSubject,
    onSave: (maxMarks: String, passMarks: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var maxMarks by remember(subject.id) { mutableStateOf(subject.maxMarks.toString()) }
    var passMarks by remember(subject.id) { mutableStateOf(subject.passMarks.toString()) }
    val max = maxMarks.toIntOrNull()
    val pass = passMarks.toIntOrNull()
    val maxValid = max != null && max > 0
    val passTooHigh = max != null && pass != null && pass > max
    val canSave = maxValid && pass != null && !passTooHigh

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${subject.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxMarks,
                        onValueChange = { maxMarks = it.filter(Char::isDigit) },
                        label = { Text("Max Marks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = maxMarks.isNotEmpty() && !maxValid,
                    )
                    OutlinedTextField(
                        value = passMarks,
                        onValueChange = { passMarks = it.filter(Char::isDigit) },
                        label = { Text("Pass Marks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = passTooHigh,
                    )
                }
                if (maxMarks.isNotEmpty() && !maxValid) {
                    Text(
                        "Max marks must be at least 1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (passTooHigh) {
                    Text(
                        "Pass marks can’t be more than the total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Lowering the total lowers any student mark above it to the new total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(maxMarks, passMarks)
                    onDismiss()
                },
                enabled = canSave,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
