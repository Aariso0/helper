package com.example.helper.examsresults.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.helper.examsresults.viewmodel.ExamSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSetupScreen(
    viewModel: ExamSetupViewModel,
    classId: String,
    onCreated: (examId: String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdExamId) {
        val examId = state.createdExamId ?: return@LaunchedEffect
        viewModel.reset()
        onCreated(examId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Exam") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.examName,
                onValueChange = viewModel::updateExamName,
                label = { Text("Exam Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Subjects", style = MaterialTheme.typography.titleMedium)

            state.subjects.forEach { subject ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = subject.name,
                            onValueChange = { viewModel.updateSubject(subject.id, it, subject.maxMarks, subject.passMarks) },
                            label = { Text("Subject") },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeSubject(subject.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = subject.maxMarks,
                            onValueChange = { viewModel.updateSubject(subject.id, subject.name, it, subject.passMarks) },
                            label = { Text("Max Marks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = subject.passMarks,
                            onValueChange = { viewModel.updateSubject(subject.id, subject.name, subject.maxMarks, it) },
                            label = { Text("Pass Marks") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::addSubject,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add Subject")
            }

            Button(
                onClick = { viewModel.createExam(classId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving && state.examName.isNotBlank(),
            ) {
                Text(if (state.saving) "Saving..." else "Create Exam")
            }
        }
    }
}
