package com.example.helper.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.example.helper.roster.picker.StudentPicker
import com.example.helper.theme.ShellDimens

/**
 * Today home screen (app-shell card): today's classes with the attendance
 * nudge / counts, homework due or overdue, and the quick-feedback capture
 * (README rules 3, 7, 8). The primary task — attendance — is one tap on a
 * class row. Typed params + callbacks only; navigation lives in `nav/`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenAttendance: (classId: String, dateEpochDay: Long) -> Unit,
    onQuickNote: (studentId: String) -> Unit,
    onOpenFeedback: () -> Unit,
    onGoToStudents: () -> Unit,
) {
    val viewModel = todayViewModel()
    val state by viewModel.uiState.collectAsState()
    var showStudentPicker by remember { mutableStateOf(false) }

    if (showStudentPicker) {
        StudentPicker(
            classId = null,
            onPick = { student ->
                showStudentPicker = false
                onQuickNote(student.id)
            },
            onDismiss = { showStudentPicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.todayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Quick capture (rule 7), thumb-reach at the bottom (rule 4):
            // pick a student → commit-on-tap quick note.
            ExtendedFloatingActionButton(
                text = { Text("Give feedback") },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { showStudentPicker = true },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Clear the floating action bar so the last row stays reachable.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { SectionHeader("Attendance") }
            if (state.classes.isEmpty()) {
                item { NoClassesYet(onGoToStudents = onGoToStudents) }
            } else {
                itemsIndexed(state.classes, key = { _, row -> row.classId }) { index, row ->
                    ClassRow(
                        row = row,
                        onClick = { onOpenAttendance(row.classId, viewModel.todayEpochDay) },
                    )
                    if (index < state.classes.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }

            if (state.dueHomework.isNotEmpty()) {
                item { SectionHeader("Homework due") }
                items(state.dueHomework, key = { it.homeworkId }) { row ->
                    HomeworkRow(row = row)
                }
            }

            item { SectionHeader("Feedback") }
            item {
                LinkRow(
                    title = "All feedback notes",
                    subtitle = "Search by student, tag or date",
                    onClick = onOpenFeedback,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = ShellDimens.screenPadding, end = ShellDimens.screenPadding, top = 20.dp, bottom = 8.dp),
    )
}

/** One class: name, subtitle, and the glanceable attendance state (rule 8). */
@Composable
private fun ClassRow(
    row: TodayViewModel.ClassRow,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShellDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = attendanceStatusLine(row),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !row.attendanceTaken -> MaterialTheme.colorScheme.tertiary
                    row.absent > 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun attendanceStatusLine(row: TodayViewModel.ClassRow): AnnotatedString =
    if (!row.attendanceTaken) {
        AnnotatedString("Attendance not taken yet")
    } else {
        buildAnnotatedString {
            append("Present ${row.present}")
            if (row.absent > 0) append(" · Absent ${row.absent}")
            if (row.late > 0) append(" · Late ${row.late}")
            if (row.excused > 0) append(" · Excused ${row.excused}")
        }
    }

/** One due/overdue homework item — informational (the homework module owns actions). */
@Composable
private fun HomeworkRow(row: TodayViewModel.DueHomeworkRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShellDimens.rowMinHeight)
            .padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${row.className} · ${row.dueLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Chevron link row used for secondary destinations off the Today screen. */
@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShellDimens.rowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** First-run state: no classes yet — point at the import path, not a blank page. */
@Composable
private fun NoClassesYet(onGoToStudents: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ShellDimens.screenPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No classes yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Add a class or import your Excel list from the Students tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TextButton(onClick = onGoToStudents) {
            Text("Go to Students")
        }
    }
}
