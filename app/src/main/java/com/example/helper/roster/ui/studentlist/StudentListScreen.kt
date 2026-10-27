package com.example.helper.roster.ui.studentlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.helper.R
import com.example.helper.coredata.entity.Student
import com.example.helper.nav.Routes
import com.example.helper.roster.viewmodel.studentListViewModel
import java.io.File

/**
 * Searchable student list for one class (card: `roster-screens` → student
 * list). Typed params only — navigation lives in app-shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    classId: String,
    onOpenStudent: (studentId: String) -> Unit,
    onOpenExams: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val viewModel = studentListViewModel(classId)
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var studentBeingDeleted by remember { mutableStateOf<Student?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.schoolClass?.name ?: "Students",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = state.schoolClass?.let {
                            listOfNotNull(it.subject, it.year).joinToString(" · ")
                        }.orEmpty()
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
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
                    IconButton(onClick = { onOpenExams(classId) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_exam_results),
                            contentDescription = "Exams & Results",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.students.isNotEmpty() || query.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add student") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.students.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text("Search name or roll number") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
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
            }

            when {
                !state.loaded -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.students.isEmpty() -> EmptyStudents(
                    className = state.schoolClass?.name,
                    onAdd = { showAddDialog = true },
                    modifier = Modifier.fillMaxSize(),
                )

                state.visibleStudents.isEmpty() -> NoSearchResults(
                    query = query,
                    onClear = { viewModel.onQueryChanged("") },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.visibleStudents, key = { it.id }) { student ->
                        StudentRow(
                            student = student,
                            onClick = { onOpenStudent(student.id) },
                            onDelete = { studentBeingDeleted = student },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStudentDialog(
            onConfirm = { name, roll, contact ->
                viewModel.addStudent(name, roll, contact)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    studentBeingDeleted?.let { student ->
        AlertDialog(
            onDismissRequest = { studentBeingDeleted = null },
            title = { Text("Delete ${student.name}?") },
            text = {
                Text(
                    "This also deletes their attendance, feedback notes and exam marks. " +
                        "This can’t be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        studentBeingDeleted = null
                        viewModel.deleteStudent(student)
                    }
                ) { Text("Delete student", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { studentBeingDeleted = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StudentRow(
    student: Student,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudentAvatar(student = student, size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = student.rollNumber?.let { "Roll $it" }.orEmpty()
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options for ${student.name}",
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

/** Photo when stored, else initial-letter circle — used across roster. */
@Composable
fun StudentAvatar(student: Student, size: androidx.compose.ui.unit.Dp) {
    val photoPath = student.photoPath
    if (photoPath != null && File(photoPath).exists()) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = "Photo of ${student.name}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(size),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = student.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyStudents(
    className: String?,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (className != null) "No students in $className yet"
            else "No students yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add them one at a time, or import a whole list from " +
                "Excel on the Students tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onAdd) { Text("Add the first student") }
    }
}

@Composable
private fun NoSearchResults(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No students match “$query”",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) { Text("Clear search") }
    }
}

@Composable
private fun AddStudentDialog(
    onConfirm: (name: String, roll: String?, contact: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = roll,
                    onValueChange = { roll = it },
                    label = { Text("Roll number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Parent contact (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, roll.ifBlank { null }, contact.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
