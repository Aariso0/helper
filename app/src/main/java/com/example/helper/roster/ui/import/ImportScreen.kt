package com.example.helper.roster.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.roster.viewmodel.ImportPhase
import com.example.helper.roster.viewmodel.ImportUiState
import com.example.helper.roster.viewmodel.importViewModel

private val importMimeTypes = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream", // many file managers label .xlsx/.csv this way
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/zip", // .xlsx is a zip; some providers report exactly that
)

/**
 * Full-screen import takeover (card: import — file pick → parse →
 * column-mapping preview → commit). Hosted by [com.example.helper.roster.ui
 * .classlist.ClassListScreen]; reports its result message via [onDone] and
 * cancels via [onDismiss]. Nothing touches the database until the commit
 * button — the preview always shows exactly what will be written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onDone: (message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = importViewModel()
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    // Relay the result once, then reset the keyed VM for the next import.
    LaunchedEffect(state.doneMessage) {
        state.doneMessage?.let {
            onDone(it)
            viewModel.consumeDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import students") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.reset(); onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close import")
                    }
                },
            )
        },
        bottomBar = {
            if (state.phase == ImportPhase.MAPPING || state.phase == ImportPhase.COMMITTING) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = viewModel::commit,
                        enabled = state.canCommit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        if (state.phase == ImportPhase.COMMITTING) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Importing…")
                        } else {
                            val count = state.importableRows.size
                            Text("Import $count student${if (count == 1) "" else "s"}")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (state.phase) {
            ImportPhase.IDLE, ImportPhase.PARSING -> IdleContent(
                state = state,
                onChooseFile = { filePicker.launch(importMimeTypes) },
                onDismissError = viewModel::dismissError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            ImportPhase.MAPPING, ImportPhase.COMMITTING -> MappingContent(
                state = state,
                committing = state.phase == ImportPhase.COMMITTING,
                onTargetClass = viewModel::onTargetClass,
                onStartNewClass = viewModel::onStartNewClass,
                onNewClassName = viewModel::onNewClassName,
                onNameColumn = viewModel::onNameColumn,
                onRollColumn = viewModel::onRollColumn,
                onContactColumn = viewModel::onContactColumn,
                onChooseAnother = { filePicker.launch(importMimeTypes) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            ImportPhase.DONE -> {
                // LaunchedEffect above relays the message; brief progress.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun IdleContent(
    state: ImportUiState,
    onChooseFile: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.phase == ImportPhase.PARSING) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Reading ${state.fileName ?: "file"}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        state.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissError) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text(
            text = "Import a student list",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick a CSV or Excel (.xlsx) file with your students. " +
                "You’ll see exactly what will be imported before anything " +
                "is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChooseFile) { Text("Choose file") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingContent(
    state: ImportUiState,
    committing: Boolean,
    onTargetClass: (String) -> Unit,
    onStartNewClass: () -> Unit,
    onNewClassName: (String) -> Unit,
    onNameColumn: (Int) -> Unit,
    onRollColumn: (Int) -> Unit,
    onContactColumn: (Int) -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val table = state.table ?: return

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // --- Chosen file --------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.fileName ?: "File",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChooseAnother, enabled = !committing) {
                Text("Choose another")
            }
        }

        // --- Target class -------------------------------------------------
        Text(
            text = "Import into",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        if (state.creatingNewClass) {
            OutlinedTextField(
                value = state.newClassName,
                onValueChange = onNewClassName,
                label = { Text("New class name (e.g. 5-B)") },
                singleLine = true,
                enabled = !committing,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val targetLabel = state.targetName ?: "Choose a class"
            ClassTargetDropdown(
                label = targetLabel,
                classes = state.classes,
                enabled = !committing,
                onSelect = onTargetClass,
                onSelectNew = onStartNewClass,
            )
        }

        // --- Column mapping ----------------------------------------------
        Text(
            text = "Which columns hold…",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        ColumnDropdownField(
            label = "Student name (required)",
            options = table.headers,
            selected = state.nameColumn,
            required = true,
            enabled = !committing,
            onSelect = onNameColumn,
        )
        Spacer(Modifier.height(8.dp))
        ColumnDropdownField(
            label = "Roll number (optional)",
            options = table.headers,
            selected = state.rollColumn,
            required = false,
            enabled = !committing,
            onSelect = onRollColumn,
        )
        Spacer(Modifier.height(8.dp))
        ColumnDropdownField(
            label = "Parent contact (optional)",
            options = table.headers,
            selected = state.contactColumn,
            required = false,
            enabled = !committing,
            onSelect = onContactColumn,
        )

        // --- Preview ------------------------------------------------------
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        PreviewTable(state = state)

        val skipped = state.skippedCount
        if (skipped > 0) {
            Text(
                text = "$skipped row${if (skipped == 1) "" else "s"} without a " +
                    "name will be skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp)) // bottom-bar clearance
    }
}

@Composable
private fun PreviewTable(state: ImportUiState) {
    val rows = state.preview()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            PreviewCell("Name", Modifier.weight(2f), isHeader = true)
            PreviewCell("Roll no", Modifier.weight(1f), isHeader = true)
            PreviewCell("Contact", Modifier.weight(2f), isHeader = true)
        }
        rows.forEach { (name, roll, contact) ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                PreviewCell(name, Modifier.weight(2f))
                PreviewCell(roll, Modifier.weight(1f))
                PreviewCell(contact, Modifier.weight(2f))
            }
        }
        if (rows.size < state.importableRows.size) {
            Text(
                text = "…and ${state.importableRows.size - rows.size} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PreviewCell(text: String, modifier: Modifier, isHeader: Boolean = false) {
    Text(
        text = text.ifEmpty { "—" },
        style = if (isHeader) MaterialTheme.typography.labelMedium
        else MaterialTheme.typography.bodyMedium,
        color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassTargetDropdown(
    label: String,
    classes: List<com.example.helper.coredata.entity.SchoolClass>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSelectNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Class") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            classes.forEach { schoolClass ->
                DropdownMenuItem(
                    text = { Text(schoolClass.name) },
                    onClick = { expanded = false; onSelect(schoolClass.id) },
                )
            }
            DropdownMenuItem(
                text = { Text("Create new class…") },
                onClick = { expanded = false; onSelectNew() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdownField(
    label: String,
    options: List<String>,
    selected: Int,
    required: Boolean,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selected in options.indices -> options[selected]
        required -> "Choose a column"
        else -> "Not imported"
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (!required) {
                DropdownMenuItem(
                    text = { Text("Not imported", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false; onSelect(-1) },
                )
            }
            options.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = { expanded = false; onSelect(index) },
                )
            }
        }
    }
}
