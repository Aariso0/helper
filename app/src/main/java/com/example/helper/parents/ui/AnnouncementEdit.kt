package com.example.helper.parents.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.parents.viewmodel.AnnouncementEditViewModel
import com.example.helper.parents.viewmodel.announcementEditViewModel
import com.example.helper.roster.picker.ClassPicker
import com.example.helper.share.asShareableUri
import com.example.helper.share.shareFiles
import com.example.helper.share.shareText
import com.example.helper.theme.ShellDimens

/**
 * Compose flow (card: `AnnouncementEdit`): class (optional) → title → body →
 * templates → Share / Share as PDF / Copy. Draft commits as it's typed
 * (homework precedent); backing out deletes an unshared draft, a shared one
 * stays in the tab's history. All three delivery paths are teacher-initiated
 * and hit only the app-shell share seam or the clipboard — this module never
 * sends anything by itself (parents card: no messaging SDK, no INTERNET).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementEdit(
    viewModel: AnnouncementEditViewModel = announcementEditViewModel(),
    onDone: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClassPicker by remember { mutableStateOf(false) }
    var templateToOverwrite by remember { mutableStateOf<AnnouncementTemplate?>(null) }

    // Back/close discards an unshared draft (VM-owned lifecycle).
    fun leave() {
        viewModel.close()
        onDone()
    }
    BackHandler { leave() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AnnouncementEditViewModel.Event.SharePdf -> {
                    val ok = shareFiles(
                        context = context,
                        uris = listOf(event.file.asShareableUri(context)),
                        mimeTypes = listOf("application/pdf"),
                        text = viewModel.currentShareText(),
                        subject = viewModel.currentSubject(),
                    )
                    if (ok) {
                        viewModel.onShared()
                        leave()
                    } else {
                        onNotice("No app available to handle sharing")
                    }
                }
                is AnnouncementEditViewModel.Event.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

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

    templateToOverwrite?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToOverwrite = null },
            title = { Text("Replace this draft?") },
            text = { Text("“${template.title ?: "Template"}” will overwrite the announcement you're writing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        templateToOverwrite = null
                        viewModel.onApplyTemplate(template)
                    },
                ) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { templateToOverwrite = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New announcement") },
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
                        val text = viewModel.currentShareText() ?: return@Button
                        if (shareText(context, text, subject = viewModel.currentSubject())) {
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

            // ── Class (optional — an announcement may target everyone) ────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = { showClassPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.className ?: "Choose a class (optional)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.classId != null) {
                    TextButton(onClick = viewModel::onClassCleared) { Text("Clear") }
                }
            }

            // ── Title + body ─────────────────────────────────────────────
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::onBodyChanged,
                label = { Text("Announcement") },
                placeholder = { Text("e.g. School reopens on Monday. Books to bring…") },
                minLines = 5,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── PDF + copy (the card's three delivery paths) ─────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = viewModel::shareAsPdf,
                    enabled = state.canShare && !state.renderingPdf,
                ) { Text("Share as PDF") }
                OutlinedButton(
                    onClick = {
                        val text = viewModel.currentShareText() ?: return@OutlinedButton
                        copyToClipboard(context, text, viewModel.currentSubject())
                        onNotice("Copied to clipboard")
                    },
                    enabled = state.canShare,
                ) { Text("Copy text") }
            }
            if (state.renderingPdf) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Templates ────────────────────────────────────────────────
            Text(
                text = "Templates",
                style = MaterialTheme.typography.titleSmall,
            )
            TemplateChipRow(
                templates = templates,
                onApply = { template ->
                    val draftIsTemplateContent =
                        state.title.trim().ifEmpty { null } == template.title &&
                            state.body.trim() == template.body
                    if (state.hasContent && !draftIsTemplateContent) {
                        templateToOverwrite = template
                    } else {
                        viewModel.onApplyTemplate(template)
                    }
                },
                onUpdate = { template, title, body ->
                    viewModel.onUpdateTemplate(template, title, body)
                },
                onDelete = viewModel::onDeleteTemplate,
                onSaveAs = { title ->
                    // The VM no-ops on a blank body — don't claim "saved" then.
                    if (state.body.isNotBlank()) {
                        viewModel.onSaveAsTemplate(title)
                        onNotice("Template saved")
                    }
                },
                saveAsInitialTitle = state.title,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Plain-text clipboard copy — no permission, no PDF fallback (card note). */
private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
