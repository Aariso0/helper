package com.example.helper.roster.ui.studentpage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.examsresults.ui.StudentExamHistory
import com.example.helper.roster.viewmodel.studentPageViewModel
import com.example.helper.roster.viewmodel.toDisplayDate
import com.example.helper.studentfeedback.ui.FeedbackTimeline
import java.io.File

/**
 * The student page (card: `roster-screens` → student page): photo, roll
 * number, parent contact, attendance % and history, plus the embedded
 * `exam history` (exams-results seam) and `feedback-timeline`. Typed params
 * only — navigation lives in app-shell; the quick-note button emits
 * [onQuickNote] for app-shell to route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPageScreen(
    studentId: String,
    onDone: () -> Unit,
    onQuickNote: (studentId: String) -> Unit,
) {
    val viewModel = studentPageViewModel(studentId)
    val state by viewModel.uiState.collectAsState()
    val photoError by viewModel.photoError.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.setPhotoFrom(uri)
    }

    LaunchedEffect(photoError) {
        if (photoError) {
            snackbarHostState.showSnackbar("Couldn’t read that photo — try another one.")
            viewModel.onPhotoErrorShown()
        }
    }

    val student = state.student
    if (!state.loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (student == null) {
        // Loaded but gone — deleted elsewhere or a stale id.
        onDone()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = student.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit student") },
                            onClick = { menuOpen = false; showEditDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete student", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; showDeleteDialog = true },
                        )
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
            // --- Header: photo + name + class -----------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    val photoPath = student.photoPath
                    if (photoPath != null && File(photoPath).exists()) {
                        AsyncImage(
                            model = File(photoPath),
                            contentDescription = "Photo of ${student.name}, tap to change",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .size(96.dp)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = student.name.trim().take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add or change photo",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.schoolClass?.let { schoolClass ->
                    val subtitle = listOfNotNull(schoolClass.subject, schoolClass.year)
                        .joinToString(" · ")
                        .ifEmpty { schoolClass.name }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- Details ---------------------------------------------------
            SectionHeader("Details")
            student.rollNumber?.let { roll ->
                DetailRow(label = "Roll number", value = roll)
            }
            student.parentContact?.let { contact ->
                DetailRow(
                    label = "Parent contact",
                    value = contact,
                    trailing = {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact"))
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "Call parent of ${student.name}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
            if (student.rollNumber == null && student.parentContact == null) {
                Text(
                    text = "No roll number or contact yet — tap Edit student to add.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // --- Attendance ------------------------------------------------
            Spacer(Modifier.height(16.dp))
            SectionHeader("Attendance")
            val summary = state.summary
            if (summary.total == 0) {
                Text(
                    text = "No attendance taken for ${student.name} yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Column {
                        Text(
                            text = "${summary.percent}%",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "present · ${summary.total} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        StatusCount("Present", summary.present)
                        StatusCount("Absent", summary.absent)
                        StatusCount("Late", summary.late)
                        StatusCount("Excused", summary.excused)
                    }
                }
                if (state.recentHistory.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.recentHistory.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = record.dateEpochDay.toDisplayDate(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = record.status.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (record.status) {
                                    AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primary
                                    AttendanceStatus.LATE -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    if (state.totalRecords > state.recentHistory.size) {
                        Text(
                            text = "Showing last ${state.recentHistory.size} of " +
                                "${state.totalRecords} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // --- Exam history (exams-results seam) ------------------------
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            StudentExamHistory(studentId = studentId)

            // --- Feedback timeline (student-feedback seam) -----------------
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            FeedbackTimeline(
                studentId = studentId,
                onQuickNote = { onQuickNote(studentId) },
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (showEditDialog) {
        EditStudentDialog(
            initialName = student.name,
            initialRoll = student.rollNumber,
            initialContact = student.parentContact,
            hasPhoto = student.photoPath != null,
            onConfirm = { name, roll, contact ->
                viewModel.updateProfile(name, roll, contact)
                showEditDialog = false
            },
            onRemovePhoto = {
                viewModel.removePhoto()
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
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
                        showDeleteDialog = false
                        viewModel.deleteStudent(onDeleted = onDone)
                    }
                ) { Text("Delete student", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
private fun StatusCount(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditStudentDialog(
    initialName: String,
    initialRoll: String?,
    initialContact: String?,
    hasPhoto: Boolean,
    onConfirm: (name: String, roll: String?, contact: String?) -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var roll by remember { mutableStateOf(initialRoll ?: "") }
    var contact by remember { mutableStateOf(initialContact ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit student") },
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
                if (hasPhoto) {
                    TextButton(onClick = onRemovePhoto) { Text("Remove photo") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, roll.ifBlank { null }, contact.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
