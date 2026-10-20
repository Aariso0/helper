package com.example.helper.homework.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttachmentKind
import com.example.helper.homework.viewmodel.HomeworkEditViewModel
import com.example.helper.homework.viewmodel.homeworkEditViewModel
import com.example.helper.roster.picker.ClassPicker
import com.example.helper.share.asShareableUri
import com.example.helper.theme.ShellDimens
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Compose flow (card: `HomeworkEdit`): class → title → text → attachments →
 * due → Share. Draft commits as it's typed (rule 5); backing out deletes an
 * unshared draft, while a shared one stays in the list. Sharing hands
 * [HomeworkEditViewModel.buildShare]'s payload to the app-shell share seam —
 * the system share sheet, nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkEdit(
    viewModel: HomeworkEditViewModel = homeworkEditViewModel(),
    onDone: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClassPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    val launchCamera = rememberCameraCapture(
        newTarget = viewModel::newCaptureTarget,
        onCaptured = viewModel::onCaptured,
        onCaptureCancelled = viewModel::onCaptureCancelled,
    )

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.onFilesPicked(uris.toList()) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onFilesPicked(listOf(uri))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeworkEditViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Back/close discards an unshared draft (VM-owned lifecycle), keeps a shared one.
    fun leave() {
        viewModel.close()
        onDone()
    }
    BackHandler { leave() }

    if (showClassPicker) {
        ClassPicker(
            onPick = { schoolClass ->
                showClassPicker = false
                viewModel.onClassPicked(schoolClass)
            },
            onDismiss = { showClassPicker = false },
            selectedClassId = state.classId,
        )
    }

    if (showDuePicker) {
        HomeworkDuePicker(
            initialDateEpochDay = state.dueDateEpochDay,
            onDatePicked = {
                showDuePicker = false
                viewModel.onDueSelected(it)
            },
            onCleared = {
                showDuePicker = false
                viewModel.onDueCleared()
            },
            onDismiss = { showDuePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New homework") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val payload = viewModel.buildShare() ?: return@Button
                        if (sharePayload(context, payload)) {
                            viewModel.onShared()
                            leave()
                        } else {
                            onNotice("No app available to handle sharing")
                        }
                    },
                    enabled = state.canShare,
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
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ShellDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Class ─────────────────────────────────────────────────────
            FilledTonalButton(
                onClick = { showClassPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.className ?: "Choose a class",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Title + text ──────────────────────────────────────────────
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Title (e.g. Chapter 4 questions)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChanged,
                label = { Text("Details (optional)") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Due date ──────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { showDuePicker = true }) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.dueDateEpochDay
                            ?.let { "Due ${LocalDate.ofEpochDay(it).format(shareDateDisplay)}" }
                            ?: "Set a due date",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (state.dueDateEpochDay != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::onDueCleared) { Text("Clear") }
                }
            }

            // ── Attachments ───────────────────────────────────────────────
            Text(
                text = "Attachments",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = launchCamera,
                    enabled = !state.importing,
                ) { Text("Camera") }
                FilledTonalButton(
                    onClick = {
                        filePicker.launch(arrayOf("*/*"))
                    },
                    enabled = !state.importing,
                ) { Text("Files") }
                FilledTonalButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !state.importing,
                ) { Text("Photos") }
            }
            Text(
                text = "PDFs share most reliably — PowerPoint attaches and " +
                    "shares fine too, but can’t be previewed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = if (attachment.kind == AttachmentKind.PDF) {
                        {
                            if (!openAttachment(context, attachment)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No app found to open this PDF")
                                }
                            }
                        }
                    } else {
                        null
                    },
                    onRemove = { viewModel.onAttachmentRemoved(attachment) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Shared with the ViewModel's share text — one date format everywhere. */
private val shareDateDisplay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** PDF open through a system viewer (card: graceful false when none exists). */
private fun openAttachment(context: Context, attachment: Attachment): Boolean {
    val file = File(attachment.filePath)
    if (!file.exists()) return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.asShareableUri(context), attachment.mimeType ?: "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Due-date picker + a "no due date" escape (M3 DatePicker, UTC contract). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkDuePicker(
    initialDateEpochDay: Long?,
    onDatePicked: (Long) -> Unit,
    onCleared: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = (initialDateEpochDay ?: LocalDate.now().toEpochDay())
        .let { LocalDate.ofEpochDay(it) }
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
            Row {
                if (initialDateEpochDay != null) {
                    TextButton(onClick = onCleared) { Text("No due date") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
