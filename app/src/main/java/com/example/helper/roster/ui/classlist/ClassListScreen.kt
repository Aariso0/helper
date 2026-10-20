package com.example.helper.roster.ui.classlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.roster.ui.import.ImportScreen
import com.example.helper.roster.viewmodel.ClassRow
import com.example.helper.roster.viewmodel.classListViewModel

/**
 * Students-tab root (card: `roster-screens` → class list). Typed params only —
 * navigation lives in app-shell. The Excel/CSV import flow is hosted here as
 * a full-screen takeover (it starts from this list) and reports its result
 * back as a snackbar message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassListScreen(
    onOpenClass: (classId: String) -> Unit,
) {
    val viewModel = classListViewModel()
    val state by viewModel.uiState.collectAsState()
    val classes = state.classes
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var classBeingEdited by remember { mutableStateOf<SchoolClass?>(null) }
    var classBeingDeleted by remember { mutableStateOf<SchoolClass?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    // Predictive back closes the import takeover before leaving the screen.
    BackHandler(enabled = showImport) { showImport = false }

    if (showImport) {
        ImportScreen(
            onDone = { message ->
                pendingMessage = message
                showImport = false
            },
            onDismiss = { showImport = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Students") },
                actions = {
                    TextButton(onClick = { showImport = true }) { Text("Import") }
                },
            )
        },
        floatingActionButton = {
            if (classes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New class") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            !state.loaded -> LoadingBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            classes.isEmpty() -> EmptyClasses(
                onCreate = { showCreateDialog = true },
                onImport = { showImport = true },
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
                items(classes, key = { it.schoolClass.id }) { row ->
                    ClassRowItem(
                        row = row,
                        onClick = { onOpenClass(row.schoolClass.id) },
                        onEdit = { classBeingEdited = row.schoolClass },
                        onDelete = { classBeingDeleted = row.schoolClass },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) } // FAB clearance
            }
        }
    }

    if (showCreateDialog) {
        ClassEditDialog(
            title = "New class",
            confirmLabel = "Create",
            onConfirm = { name, subject, year ->
                viewModel.createClass(name, subject, year)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    classBeingEdited?.let { schoolClass ->
        ClassEditDialog(
            title = "Edit class",
            confirmLabel = "Save",
            initialName = schoolClass.name,
            initialSubject = schoolClass.subject,
            initialYear = schoolClass.year,
            onConfirm = { name, subject, year ->
                viewModel.renameClass(schoolClass, name, subject, year)
                classBeingEdited = null
            },
            onDismiss = { classBeingEdited = null },
        )
    }

    classBeingDeleted?.let { schoolClass ->
        val count = classes.firstOrNull { it.schoolClass.id == schoolClass.id }?.studentCount ?: 0
        AlertDialog(
            onDismissRequest = { classBeingDeleted = null },
            title = { Text("Delete ${schoolClass.name}?") },
            text = {
                Text(
                    if (count == 0) {
                        "This class has no students. Deleting it can’t be undone."
                    } else {
                        "This also deletes its $count student${if (count == 1) "" else "s"} " +
                            "and everything recorded for them — attendance and notes. " +
                            "This can’t be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteClass(schoolClass)
                        classBeingDeleted = null
                    }
                ) { Text("Delete class", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { classBeingDeleted = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ClassRowItem(
    row: ClassRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = row.schoolClass.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = buildString {
                listOfNotNull(row.schoolClass.subject, row.schoolClass.year)
                    .joinToString(" · ")
                    .takeIf { it.isNotEmpty() }
                    ?.let { append(it); append(" · ") }
                append(
                    when (val n = row.studentCount) {
                        0 -> "No students yet"
                        1 -> "1 student"
                        else -> "$n students"
                    }
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options for ${row.schoolClass.name}",
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyClasses(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No classes yet",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create a class to start adding students, or import the " +
                "list you already have in Excel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onImport) { Text("Import a file") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCreate) { Text("New class") }
    }
}

@Composable
private fun ClassEditDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, subject: String?, year: String?) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialSubject: String? = null,
    initialYear: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var subject by remember { mutableStateOf(initialSubject ?: "") }
    var year by remember { mutableStateOf(initialYear ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class name (e.g. 5-A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, subject.ifBlank { null }, year.ifBlank { null }) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
