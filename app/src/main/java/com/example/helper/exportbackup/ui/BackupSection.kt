package com.example.helper.exportbackup.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helper.exportbackup.viewmodel.Work

/**
 * The manual backup block (`export-backup` card): one zip of everything the
 * app knows, and a validated restore. Wording is deliberately blunt about
 * what restore does — replacing all local data is not undoable on this
 * device.
 */
@Composable
fun BackupSection(
    work: Work,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val enabled = work == Work.Idle
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Back up everything", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One file with every class, student, photo, attendance mark, feedback note, " +
                    "homework, attachment, announcement, template and contact-log entry — plus a " +
                    "readable CSV copy of every table. Keep it somewhere else: Drive, a computer, " +
                    "another phone. A backup left on this phone won't survive this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBackup, enabled = enabled) {
                    Text(if (work == Work.BackingUp) "Backing up…" else "Save a backup")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "Restore from a backup", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Brings back everything from a backup file — after a reinstall, a reset, " +
                    "or on a new phone. This replaces everything currently in the app on this " +
                    "device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRestore, enabled = enabled) {
                Text(if (work == Work.InspectingBackup) "Checking…" else "Restore from a file")
            }
        }
    }
}
