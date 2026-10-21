package com.example.helper.exportbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.exportbackup.summarize.ClassMonthSummary
import com.example.helper.exportbackup.summarize.MonthlySummary
import com.example.helper.exportbackup.summarize.PERCENTAGE_RULE
import com.example.helper.exportbackup.summarize.StudentMonthSummary

/**
 * On-screen monthly summary (card `SummaryCards`): the exact figures the
 * exports carry, because both read the same `MonthlySummary`. An untracked
 * student shows "No records" — never 0% (card build note).
 */
@Composable
fun OverallSummaryCard(summary: MonthlySummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = percentLabel(summary.presentPercentage),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "${summary.trackedRecords} marks tracked",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = countsLine(summary.present, summary.absent, summary.late, summary.excused),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${summary.trackedDays} days tracked · ${summary.classes.size} classes",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = PERCENTAGE_RULE,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun ClassSummaryCard(classSummary: ClassMonthSummary) {
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = classSummary.className,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = countsLine(
                            classSummary.present,
                            classSummary.absent,
                            classSummary.late,
                            classSummary.excused,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = percentLabel(classSummary.presentPercentage),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            classSummary.students.forEach { student ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                StudentSummaryRow(student)
            }
            if (classSummary.students.isEmpty()) {
                Text(
                    text = "No students in this class yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StudentSummaryRow(student: StudentMonthSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.studentName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (student.trackedRecords == 0) {
                    "No records"
                } else {
                    countsLine(student.present, student.absent, student.late, student.excused)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = percentLabel(student.presentPercentage),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (student.rollNumber != null) {
                Text(
                    text = "Roll ${student.rollNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Empty states stay distinguishable: nothing at all vs. nothing this month. */
@Composable
fun NoSummaryCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal fun percentLabel(percentage: Int?): String =
    if (percentage == null) "—" else "$percentage%"

internal fun countsLine(present: Int, absent: Int, late: Int, excused: Int): String =
    "P $present · A $absent · L $late · E $excused"
