package com.example.helper.exportbackup.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.helper.exportbackup.backupio.AppRestart
import com.example.helper.exportbackup.viewmodel.ExportFormat
import com.example.helper.exportbackup.viewmodel.ExportViewModel
import com.example.helper.exportbackup.viewmodel.RestoreOutcome
import com.example.helper.exportbackup.viewmodel.RestorePreview
import com.example.helper.exportbackup.viewmodel.Work
import com.example.helper.exportbackup.viewmodel.exportViewModel
import com.example.helper.roster.picker.ClassPicker
import com.example.helper.theme.ShellDimens
import kotlinx.coroutines.delay

/**
 * Reports & backup destination (export-backup card): pick a month → optionally
 * a class, read the summary, export it as CSV/Excel/PDF through SAF, and take
 * or restore the one-file backup of everything. No permission, no upload — the
 * teacher chooses the file's destination every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val viewModel = exportViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClassPicker by remember { mutableStateOf(false) }

    // Notices live in the screen state, not in a one-shot channel: a message
    // produced while the file picker has the activity keeps until this
    // collector is back, so a rejected restore can never be silent.
    LaunchedEffect(state.pendingMessage) {
        val message = state.pendingMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    // One CreateDocument launcher per format: the MIME type decides the
    // extension the system picker appends, so a generic launcher would
    // produce files other apps can't open.
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
    ) { uri -> uri?.let { viewModel.export(ExportFormat.CSV, it) } }
    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.EXCEL.mimeType),
    ) { uri -> uri?.let { viewModel.export(ExportFormat.EXCEL, it) } }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType),
    ) { uri -> uri?.let { viewModel.export(ExportFormat.PDF, it) } }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> uri?.let { viewModel.backup(it) } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.inspectBackup(it) } }

    state.outcome?.let { outcome ->
        RestoreOutcomeScreen(outcome = outcome, onRestart = { AppRestart.restart(context) })
        return
    }

    if (showClassPicker) {
        ClassPicker(
            onPick = { schoolClass ->
                showClassPicker = false
                viewModel.selectClass(schoolClass.id)
            },
            onDismiss = { showClassPicker = false },
            selectedClassId = state.selectedClassId,
        )
    }

    state.preview?.let { preview ->
        RestoreConfirmDialog(
            preview = preview,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissPreview,
        )
    }

    if (state.work == Work.Restoring) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Restoring your backup…")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.work != Work.Idle) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ShellDimens.screenPadding,
                    end = ShellDimens.screenPadding,
                    top = 12.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthScopeCard(
                        state = state,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onThisMonth = viewModel::showThisMonth,
                        onPickClass = { showClassPicker = true },
                        onClearClass = { viewModel.selectClass(null) },
                    )
                }

                item { SectionTitle("Summary") }

                val summary = state.summary
                when {
                    state.loading -> item { LoadingRow("Building the summary…") }
                    summary == null || summary.classes.isEmpty() -> item {
                        NoSummaryCard(
                            title = "No classes yet",
                            body = "Add a class and its students from the Students tab, then come back here.",
                        )
                    }

                    !summary.hasRecords -> item {
                        NoSummaryCard(
                            title = "No attendance recorded",
                            body = "Nothing was marked in ${state.monthLabel} for ${state.scopeLabel}. " +
                                "Pick another month or class — exports need at least one mark.",
                        )
                    }

                    else -> {
                        item { OverallSummaryCard(summary) }
                        items(summary.classes, key = { it.classId }) { classSummary ->
                            ClassSummaryCard(classSummary)
                        }
                    }
                }

                item {
                    ExportSection(
                        canExport = state.canExport,
                        work = state.work,
                        onExport = { format ->
                            val name = viewModel.suggestedExportFileName(format)
                            when (format) {
                                ExportFormat.CSV -> csvLauncher.launch(name)
                                ExportFormat.EXCEL -> excelLauncher.launch(name)
                                ExportFormat.PDF -> pdfLauncher.launch(name)
                            }
                        },
                    )
                }

                item { SectionTitle("Backup & restore") }

                item {
                    BackupSection(
                        work = state.work,
                        onBackup = {
                            backupLauncher.launch(viewModel.suggestedBackupFileName())
                        },
                        onRestore = {
                            restoreLauncher.launch(arrayOf(BACKUP_MIME_TYPE, OCTET_STREAM_MIME_TYPE))
                        },
                    )
                }
            }
        }
    }
}

private const val BACKUP_MIME_TYPE = "application/zip"
private const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MonthScopeCard(
    state: ExportViewModel.UiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisMonth: () -> Unit,
    onPickClass: () -> Unit,
    onClearClass: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.monthLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.isCurrentMonth) "This month to date" else "Full month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, enabled = !state.isCurrentMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPickClass,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.scopeLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.selectedClassId != null) {
                    TextButton(onClick = onClearClass) { Text("All classes") }
                }
            }
            if (!state.isCurrentMonth) {
                TextButton(
                    onClick = onThisMonth,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text("Show this month") }
            }
        }
    }
}

@Composable
private fun ExportSection(
    canExport: Boolean,
    work: Work,
    onExport: (ExportFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { format ->
                val busy = (work as? Work.Exporting)?.format == format
                OutlinedButton(
                    onClick = { onExport(format) },
                    enabled = canExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (busy) "Saving…" else format.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = "Saves wherever you choose — Files, Drive, email. Nothing leaves the phone " +
                "unless you pick a destination.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    preview: RestorePreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Taken ${preview.createdAtLabel} · ${preview.rowCount} records · " +
                        "${preview.fileCount} files",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = preview.tableCounts.joinToString(", ") { (table, count) ->
                        "${table.replace('_', ' ')} $count"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "This replaces everything currently in the app on this device — " +
                        "classes, students, photos, attendance, notes, homework, attachments, " +
                        "announcements and the contact log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Replace and restore", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Post-restore takeover. Both outcomes need a restart (success: the DB file
 * was replaced; failure: the DB was closed before the swap), so back is
 * blocked and the only action is the restart. Success restarts on its own.
 */
@Composable
private fun RestoreOutcomeScreen(outcome: RestoreOutcome, onRestart: () -> Unit) {
    BackHandler(enabled = true) {}
    LaunchedEffect(outcome.success) {
        if (outcome.success) {
            delay(RESTART_DELAY_MILLIS)
            onRestart()
        }
    }
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (outcome.success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (outcome.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = if (outcome.success) "Restore complete" else "Restore didn't finish",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = outcome.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .height(ShellDimens.primaryActionHeight),
            ) {
                Text("Restart the app")
            }
            if (outcome.success) {
                Text(
                    text = "Restarting automatically…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private const val RESTART_DELAY_MILLIS = 1_500L
