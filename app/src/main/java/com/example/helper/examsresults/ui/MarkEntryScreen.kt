package com.example.helper.examsresults.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student
import com.example.helper.examsresults.viewmodel.MarkEntryViewModel
import kotlinx.coroutines.launch

/**
 * One subject, one column of marks. The focused row is scrolled clear of the
 * keyboard and its student name is echoed in a pinned bar, so the teacher
 * always knows whose mark is being typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkEntryScreen(
    viewModel: MarkEntryViewModel,
    onBack: () -> Unit,
    onViewResults: () -> Unit,
    onSelectSubject: (subjectId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var subjectMenuOpen by remember { mutableStateOf(false) }
    var focusedStudentId by remember { mutableStateOf<String?>(null) }
    val focusedStudent = state.students.firstOrNull { it.id == focusedStudentId }

    LaunchedEffect(viewModel) {
        viewModel.savedAll.collect { onViewResults() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Enter Marks")
                        state.subject?.let { subject ->
                            Text(
                                "${subject.name} · out of ${subject.maxMarks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { subjectMenuOpen = true }) {
                            Text(state.subject?.name ?: "Subject")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch subject")
                        }
                        DropdownMenu(
                            expanded = subjectMenuOpen,
                            onDismissRequest = { subjectMenuOpen = false },
                        ) {
                            state.subjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text("${subject.name} (${subject.maxMarks})") },
                                    onClick = {
                                        subjectMenuOpen = false
                                        if (subject.id != state.subjectId) onSelectSubject(subject.id)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.saveAll(thenFinish = true)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & View Results")
                }
                OutlinedButton(
                    onClick = { viewModel.saveAll() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save All")
                }
            }

            if (focusedStudent != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "Editing: ${studentLabel(focusedStudent)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.students, key = { it.id }) { student ->
                    StudentMarkRow(
                        student = student,
                        subject = state.subject,
                        value = state.marks[student.id].orEmpty(),
                        onValueChange = { viewModel.updateMark(student.id, it) },
                        onSave = { viewModel.saveMark(student.id) },
                        onFocused = { focusedStudentId = student.id },
                    )
                    HorizontalDivider()
                }
                if (state.students.isEmpty() && !state.loading) {
                    item {
                        Text(
                            text = "No students in this class yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentMarkRow(
    student: Student,
    subject: ExamSubject?,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onFocused: () -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val outOfRange = value.toDoubleOrNull()?.let { subject != null && it > subject.maxMarks } == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(studentLabel(student), style = MaterialTheme.typography.titleMedium)
            subject?.let {
                Text(
                    "out of ${it.maxMarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Marks") },
            isError = outOfRange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    onSave()
                    focusManager.moveFocus(FocusDirection.Down)
                },
                onDone = {
                    onSave()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier
                .width(120.dp)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocused()
                        scope.launch { bringIntoView.bringIntoView() }
                    }
                },
            singleLine = true,
        )
    }
}

private fun studentLabel(student: Student): String =
    "${student.rollNumber ?: ""} ${student.name}".trim()
