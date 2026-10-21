package com.example.helper.parents.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.ContactLogEntry
import com.example.helper.coredata.entity.Student
import com.example.helper.parents.viewmodel.ParentsViewModel.AnnouncementItem
import com.example.helper.parents.viewmodel.ParentsViewModel.EntryItem
import com.example.helper.parents.viewmodel.announcementText
import com.example.helper.parents.viewmodel.parentsViewModel
import com.example.helper.roster.picker.StudentPicker
import com.example.helper.share.shareText
import com.example.helper.theme.ShellDimens

private const val ANNOUNCEMENT_LIMIT = 10
private const val ENTRY_LIMIT = 15

/**
 * Parents tab root (card: `parents-screen`): two sections — announcements
 * history (tap a row to re-share it through the share sheet) and the
 * per-student parent contact log (add via the roster student picker). The
 * compose flow is hosted here as a full-screen takeover (homework pattern).
 * No parent-facing screens, no automatic sending — every delivery here is a
 * teacher-initiated share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentsScreen() {
    val viewModel = parentsViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    var showAnnouncementEdit by remember { mutableStateOf(false) }
    var showStudentPicker by remember { mutableStateOf(false) }
    var entryStudent by remember { mutableStateOf<Student?>(null) }
    var announcementToDelete by remember { mutableStateOf<Announcement?>(null) }
    var entryToDelete by remember { mutableStateOf<ContactLogEntry?>(null) }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    if (showAnnouncementEdit) {
        AnnouncementEdit(
            onDone = { showAnnouncementEdit = false },
            onNotice = { pendingMessage = it },
        )
        return
    }

    if (showStudentPicker) {
        StudentPicker(
            classId = null,
            onPick = { student ->
                showStudentPicker = false
                entryStudent = student
            },
            onDismiss = { showStudentPicker = false },
        )
    }

    entryStudent?.let { student ->
        AddContactEntryDialog(
            studentName = student.name,
            onAdd = { text, dateEpochDay ->
                viewModel.addEntry(student, text, dateEpochDay)
                entryStudent = null
            },
            onDismiss = { entryStudent = null },
        )
    }

    announcementToDelete?.let { announcement ->
        ConfirmDeleteDialog(
            title = "Delete announcement?",
            message = "“${announcement.title ?: announcement.body.lineSequence().first()}” " +
                "will be removed from the list. Anything already shared stays wherever it was sent.",
            onConfirm = {
                viewModel.deleteAnnouncement(announcement)
                announcementToDelete = null
            },
            onDismiss = { announcementToDelete = null },
        )
    }

    entryToDelete?.let { entry ->
        ConfirmDeleteDialog(
            title = "Delete contact log entry?",
            message = "The note about ${entry.studentName} will be removed.",
            onConfirm = {
                viewModel.deleteEntry(entry)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Parents") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAnnouncementEdit = true },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("New announcement") },
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

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                // ── Announcements ────────────────────────────────────────
                item(key = "header-announcements") {
                    SectionHeader(
                        title = "Announcements",
                        count = state.announcements.size,
                        countLabel = if (state.announcements.size == 1) "1 shared" else "${state.announcements.size} shared",
                    )
                }
                if (state.announcements.isEmpty()) {
                    item(key = "empty-announcements") {
                        EmptyHint(
                            text = "No announcements yet — compose one and share it " +
                                "to any parent group through WhatsApp, email, or " +
                                "anything else you use.",
                        )
                    }
                } else {
                    items(state.announcements.take(ANNOUNCEMENT_LIMIT), key = { it.announcement.id }) { item ->
                        AnnouncementRow(
                            item = item,
                            onClick = {
                                val announcement = item.announcement
                                val ok = shareText(
                                    context = context,
                                    text = announcementText(announcement.title, announcement.body),
                                    subject = announcement.title?.trim().orEmpty()
                                        .ifEmpty { "Announcement" },
                                )
                                if (!ok) pendingMessage = "No app available to handle sharing"
                            },
                            onDelete = { announcementToDelete = item.announcement },
                        )
                    }
                    if (state.announcements.size > ANNOUNCEMENT_LIMIT) {
                        item(key = "more-announcements") {
                            HonestyLine("Showing last $ANNOUNCEMENT_LIMIT of ${state.announcements.size}")
                        }
                    }
                }

                // ── Contact log ──────────────────────────────────────────
                item(key = "header-contacts") {
                    SectionHeader(
                        title = "Parent contact log",
                        count = state.entries.size,
                        countLabel = if (state.entries.size == 1) "1 logged" else "${state.entries.size} logged",
                        actionLabel = "Log a contact",
                        onAction = { showStudentPicker = true },
                    )
                }
                if (state.entries.isEmpty()) {
                    item(key = "empty-contacts") {
                        EmptyHint(
                            text = "No parent contacts logged yet — when a parent " +
                                "calls or messages you, log it here for later reference.",
                        )
                    }
                } else {
                    items(state.entries.take(ENTRY_LIMIT), key = { it.entry.id }) { item ->
                        ContactEntryRow(
                            item = item,
                            onDelete = { entryToDelete = item.entry },
                        )
                    }
                    if (state.entries.size > ENTRY_LIMIT) {
                        item(key = "more-contacts") {
                            HonestyLine("Showing last $ENTRY_LIMIT of ${state.entries.size}")
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) } // FAB clearance
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    countLabel: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ShellDimens.screenPadding, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (count > 0) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** One saved announcement: title (or body opening), class · date, row menu. */
@Composable
private fun AnnouncementRow(
    item: AnnouncementItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = ShellDimens.screenPadding, end = 4.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.announcement.title?.takeIf { it.isNotBlank() }
                    ?: item.announcement.body.lineSequence().first(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val caption = listOfNotNull(item.announcement.className, item.dateLabel)
                .joinToString(" · ")
            Text(
                text = if (caption.isEmpty()) item.dateLabel else caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Announcement options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** One logged contact: student + date caption, what was said, row menu. */
@Composable
private fun ContactEntryRow(
    item: EntryItem,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ShellDimens.screenPadding, end = 4.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ContactEntryRowContent(
            studentName = item.entry.studentName,
            text = item.entry.text,
            dateLabel = item.dateLabel,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Contact entry options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
    )
}

@Composable
private fun HonestyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ShellDimens.screenPadding, vertical = 8.dp),
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
