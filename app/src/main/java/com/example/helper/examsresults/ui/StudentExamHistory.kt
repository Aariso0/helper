package com.example.helper.examsresults.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.examsresults.studentExamHistoryViewModel
import com.example.helper.examsresults.util.ExamHistoryEntry
import com.example.helper.examsresults.util.ExamHistoryStatus
import com.example.helper.examsresults.util.ExamSubjectMark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exam performance for one student (card: `exams-results-screen`), embedded
 * by roster's student page. One card per exam, newest first; the subject chips
 * switch between all subjects and a single subject. Deliberately no inner
 * vertical scroll — the host page already scrolls.
 */
@Composable
fun StudentExamHistory(
    studentId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel = studentExamHistoryViewModel(studentId)
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Exams",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.entries.isNotEmpty()) {
                Text(
                    text = "Newest first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            !state.loaded -> CircularProgressIndicator(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(24.dp),
                strokeWidth = 2.dp,
            )

            state.entries.isEmpty() -> Text(
                text = "No exams for this class yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.entries.forEach { entry ->
                    key(entry.examId) {
                        ExamHistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamHistoryCard(entry: ExamHistoryEntry) {
    var selectedSubjectId by rememberSaveable(entry.examId) { mutableStateOf<String?>(null) }
    // An edited exam can drop a subject; fall back to "All" instead of an
    // empty selection.
    val selected = selectedSubjectId?.takeIf { id ->
        entry.subjectMarks.any { it.subjectId == id }
    }
    val statusColor = when (entry.status) {
        ExamHistoryStatus.PASS -> MaterialTheme.colorScheme.primary
        ExamHistoryStatus.FAIL -> MaterialTheme.colorScheme.error
        ExamHistoryStatus.NOT_ENTERED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column {
                Text(
                    text = entry.examName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.examDate?.let { epoch ->
                    Text(
                        text = formatExamDate(epoch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.status == ExamHistoryStatus.NOT_ENTERED) {
                Text(
                    text = "Not entered",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Total: ${formatMark(entry.result.totalObtained)}/" +
                        "${formatMark(entry.result.totalMax)}  ·  " +
                        "${formatPercentage(entry.result.percentage)}  ·  " +
                        entry.status.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
                if (entry.failedSubjectNames.isNotEmpty()) {
                    Text(
                        text = "Failed in: ${entry.failedSubjectNames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (entry.hasMarks && entry.subjectMarks.isNotEmpty()) {
                SubjectFilterRow(
                    subjects = entry.subjectMarks,
                    selectedSubjectId = selected,
                    onSelect = { selectedSubjectId = it },
                )
                val visible = entry.subjectMarks.filter { mark ->
                    selected == null || mark.subjectId == selected
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    visible.forEach { mark -> SubjectMarkRow(mark) }
                }
                Text(
                    text = "${entry.enteredCount} of ${entry.subjectMarks.size} subjects entered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubjectFilterRow(
    subjects: List<ExamSubjectMark>,
    selectedSubjectId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedSubjectId == null,
            onClick = { onSelect(null) },
            label = { Text("All subjects") },
        )
        subjects.forEach { subject ->
            FilterChip(
                selected = selectedSubjectId == subject.subjectId,
                onClick = {
                    onSelect(
                        if (selectedSubjectId == subject.subjectId) null else subject.subjectId
                    )
                },
                label = {
                    Text(
                        text = subject.subjectName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun SubjectMarkRow(mark: ExamSubjectMark) {
    val valueColor = when {
        mark.failed -> MaterialTheme.colorScheme.error
        mark.entered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mark.subjectName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Pass ${mark.passMarks} · Max ${mark.maxMarks}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = mark.obtained?.let(::formatMark) ?: "Not entered",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mark.failed) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
        )
    }
}

private fun ExamHistoryStatus.label(): String = when (this) {
    ExamHistoryStatus.PASS -> "PASS"
    ExamHistoryStatus.FAIL -> "FAIL"
    ExamHistoryStatus.NOT_ENTERED -> "Not entered"
}

private fun formatMark(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

private fun formatPercentage(value: Double): String =
    "${formatMark(value)}%"

private fun formatExamDate(epoch: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epoch))
