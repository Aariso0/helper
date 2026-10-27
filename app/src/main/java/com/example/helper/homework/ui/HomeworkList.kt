package com.example.helper.homework.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.homework.viewmodel.HomeworkListViewModel
import com.example.helper.homework.viewmodel.SharePayload
import com.example.helper.homework.viewmodel.homeworkListViewModel
import com.example.helper.homework.viewmodel.homeworkEditViewModel
import com.example.helper.share.asShareableUri
import com.example.helper.share.shareFiles
import com.example.helper.share.shareText
import com.example.helper.theme.ShellDimens
import java.io.File

/**
 * Homework tab root (card: `homework-screen`). Assignments grouped per class,
 * soonest-due first; tapping a row re-shares it through the system share
 * sheet, tapping a class header opens that class's student list (typed
 * [onOpenClass] callback — app-shell owns navigation). The compose flow is
 * hosted here as a full-screen takeover (roster's import pattern) because it
 * starts from this list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkScreen(
    onOpenClass: (classId: String) -> Unit,
) {
    val listViewModel = homeworkListViewModel()
    val editViewModel = homeworkEditViewModel()
    val state by listViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    // The edit takeover owns its own back handling (discard-on-back lives
    // with the draft lifecycle in HomeworkEdit).
    if (showEdit) {
        HomeworkEdit(
            viewModel = editViewModel,
            onDone = { showEdit = false },
            onNotice = { pendingMessage = it },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Homework") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEdit = true },
                modifier = Modifier.semantics { contentDescription = "New homework" },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New homework") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.sections.isEmpty() -> EmptyHomework(
                onCompose = { showEdit = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                state.sections.forEach { section ->
                    item(key = "header-${section.classId}") {
                        ClassHeader(
                            className = section.className,
                            count = section.items.size,
                            onClick = { onOpenClass(section.classId) },
                        )
                    }
                    items(section.items, key = { it.hw.homework.id }) { item ->
                        HomeworkRow(
                            item = item,
                            onClick = {
                                val payload = listViewModel.sharePayloadFor(item, section.className)
                                if (!sharePayload(context, payload)) {
                                    pendingMessage = "No app available to handle sharing"
                                }
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(72.dp)) } // FAB clearance
            }
        }
    }
}

/**
 * One share action for both the list's re-share and the compose flow's Share:
 * content URIs for stored files (seam shape agreed with app-shell), plain
 * text when there is nothing attached. Returns false when no handler exists.
 */
internal fun sharePayload(context: Context, payload: SharePayload): Boolean {
    val stored = payload.attachments.mapNotNull { attachment ->
        File(attachment.filePath).takeIf { it.exists() }?.let { it to attachment.mimeType }
    }
    return if (stored.isEmpty()) {
        shareText(context, payload.text, subject = payload.subject)
    } else {
        shareFiles(
            context = context,
            uris = stored.map { (file, _) -> file.asShareableUri(context) },
            mimeTypes = stored.map { (_, mime) -> mime },
            text = payload.text,
            subject = payload.subject,
        )
    }
}

/** Tappable class header — opens the class's student list (roster route). */
@Composable
private fun ClassHeader(
    className: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = ShellDimens.screenPadding, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = className,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (count) {
                    1 -> "1 assignment"
                    else -> "$count assignments"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open $className",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One saved assignment: title, due label (overdue stands out), file count. */
@Composable
private fun HomeworkRow(
    item: HomeworkListViewModel.Item,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.hw.homework.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val fileCount = item.hw.attachments.size
            val caption = buildString {
                append(item.dueLabel)
                if (fileCount > 0) {
                    append(" · ")
                    append(if (fileCount == 1) "1 file" else "$fileCount files")
                }
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun EmptyHomework(
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No homework yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Compose an assignment for a class, attach a photo or a " +
                "PDF, and share it through any app — WhatsApp, email, or " +
                "anything else you use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onCompose) { Text("New homework") }
    }
}
