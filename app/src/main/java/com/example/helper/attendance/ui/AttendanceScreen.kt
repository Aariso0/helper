package com.example.helper.attendance.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.helper.attendance.search.JumpToStudent
import com.example.helper.attendance.viewmodel.AttendanceViewModel.Counters
import com.example.helper.attendance.viewmodel.attendanceViewModel
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.Student
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.example.helper.share.shareText
import com.example.helper.motion.Motion
import com.example.helper.theme.ShellDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * The tap-absentees attendance screen (attendance card `attendance-screen`):
 * all-Present preset, tap = absent, long-press = Late/Excused, live
 * counters, date switch through the same screen, bottom Save with a
 * post-save absentee share shortcut. Typed params only — `classId` and
 * `dateEpochDay` arrive from app-shell's route; `onDone` navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    classId: String,
    dateEpochDay: Long,
    onDone: () -> Unit,
) {
    val viewModel = attendanceViewModel(classId = classId, dateEpochDay = dateEpochDay)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var statusSheetStudent by remember { mutableStateOf<Student?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    /**
     * One snackbar at a time. The old code queued one per tap, so with two
     * quick marks the snackbar on screen belonged to the *first* tap while the
     * second waited behind it — Undo then reverted the previous child. The
     * visible action is now always the newest one.
     */
    fun showUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val today = LocalDate.now()
    val displayDate = LocalDate.ofEpochDay(state.dateEpochDay)
    val isToday = state.dateEpochDay == today.toEpochDay()
    val shortDateFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy") }
    val plainDateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    if (showDatePicker) {
        AttendanceDatePicker(
            initialDateEpochDay = state.dateEpochDay,
            onDatePicked = {
                showDatePicker = false
                viewModel.onDateSelected(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }

    statusSheetStudent?.let { student ->
        val previousForSheet = state.statuses[student.id] ?: AttendanceStatus.PRESENT
        StatusSheet(
            current = previousForSheet,
            onStatusChosen = { status ->
                statusSheetStudent = null
                viewModel.onSetStatus(student.id, status)
                showUndo("Marked ${status.name.lowercase()}") {
                    viewModel.onSetStatus(student.id, previousForSheet)
                }
            },
            onDismiss = { statusSheetStudent = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.className, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SaveBar(onSave = {
                scope.launch {
                    val absentees = viewModel.save()
                    val dateLabel = displayDate.format(plainDateFormat)
                    if (absentees.isEmpty()) {
                        snackbarHostState.showSnackbar("Attendance saved — no absences")
                    } else {
                        val result = snackbarHostState.showSnackbar(
                            message = "Attendance saved · ${absentees.size} absent",
                            actionLabel = "Share",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val text = buildString {
                                append("Absent — ${state.className}, $dateLabel:")
                                absentees.forEach { append("\n• $it") }
                            }
                            shareText(context, text, subject = "Absentees ${state.className} $dateLabel")
                        }
                    }
                }
            })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ShellDimens.screenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isToday) {
                            "Today · ${displayDate.format(shortDateFormat)}"
                        } else {
                            displayDate.format(shortDateFormat)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                CounterText(state.counters)
            }

            JumpToStudent(
                query = query,
                onQueryChanged = { query = it },
                modifier = Modifier.padding(horizontal = ShellDimens.screenPadding),
            )

            val visibleStudents = if (query.isBlank()) {
                state.students
            } else {
                state.students.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.rollNumber?.contains(query, ignoreCase = true) == true
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (visibleStudents.isEmpty()) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(Motion.Effects.Default) + slideInVertically(Motion.Spatial.FastOffset) { it / 4 },
                            exit = fadeOut(Motion.Effects.Default) + slideOutVertically(Motion.Spatial.FastOffset) { it / 4 },
                        ) {
                            Text(
                                text = if (query.isBlank()) {
                                    "No students in this class yet"
                                } else {
                                    "No students match “$query”"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(ShellDimens.screenPadding),
                            )
                        }
                    }
                }
                itemsIndexed(visibleStudents, key = { _, student -> student.id }) { index, student ->
                    val previous = state.statuses[student.id] ?: AttendanceStatus.PRESENT
                    StudentRow(
                        student = student,
                        status = previous,
                        onClick = {
                            // The status the row actually had, read at tap time:
                            // Undo reverts this tap and nothing else.
                            val before = viewModel.onToggleAbsent(student.id)
                            if (before != null) {
                                val label = if (before == AttendanceStatus.ABSENT) {
                                    "Marked present"
                                } else {
                                    "Marked absent"
                                }
                                showUndo(label) { viewModel.onSetStatus(student.id, before) }
                            }
                        },
                        onLongClick = { statusSheetStudent = student },
                    )
                    if (index < visibleStudents.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

/** Live counter ("Present 28 | Absent 2") — absences stand out at a glance. */
@Composable
private fun CounterText(counters: Counters) {
    val p by animateIntAsState(counters.present, Motion.Spatial.FastInt, label = "p")
    val a by animateIntAsState(counters.absent, Motion.Spatial.FastInt, label = "a")
    val l by animateIntAsState(counters.late, Motion.Spatial.FastInt, label = "l")
    val e by animateIntAsState(counters.excused, Motion.Spatial.FastInt, label = "e")
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
            append("Present $p")
        }
        if (counters.absent > 0) {
            append(" · ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append("Absent $a")
            }
        }
        if (counters.late > 0) append(" · Late $l")
        if (counters.excused > 0) append(" · Excused $e")
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

/** Bottom-anchored Save (README rule 4): "done for this class". */
@Composable
private fun SaveBar(onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ShellDimens.screenPadding,
                    end = ShellDimens.screenPadding,
                    top = 8.dp,
                    bottom = 8.dp,
                )
                .height(ShellDimens.primaryActionHeight),
        ) {
            Text("Save", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One-job date switcher (M3 DatePicker, UTC epoch-millis contract). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceDatePicker(
    initialDateEpochDay: Long,
    onDatePicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = LocalDate.ofEpochDay(initialDateEpochDay)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis
                    if (selected != null) {
                        onDatePicked(
                            Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        )
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
