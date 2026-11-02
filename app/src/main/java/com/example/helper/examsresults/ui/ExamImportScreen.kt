package com.example.helper.examsresults.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.examsresults.excelimport.ExamImportRow
import com.example.helper.examsresults.excelimport.ExamImportSubjectDraft
import com.example.helper.examsresults.viewmodel.ExamImportPhase
import com.example.helper.examsresults.viewmodel.ExamImportUiState
import com.example.helper.examsresults.viewmodel.ExamImportViewModel

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
 * Full-screen "import an exam from Excel" takeover. The teacher picks a
 * workbook, says which sheet and rows hold the exam, checks the subject columns
 * and their max/pass marks, and sees exactly which students matched before the
 * exam is created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamImportScreen(
    viewModel: ExamImportViewModel,
    onImported: (examId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    fun close() {
        viewModel.reset()
        onDismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import exam") },
                navigationIcon = {
                    IconButton(onClick = { close() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close import")
                    }
                },
            )
        },
        bottomBar = {
            if (state.phase == ExamImportPhase.MAPPING || state.phase == ExamImportPhase.COMMITTING) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = viewModel::commit,
                        enabled = state.canCommit,
                        modifier = Modifier
                            // Custom bottom bar: Scaffold does not inset it, so
                            // keep the Import button above system navigation.
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        if (state.phase == ExamImportPhase.COMMITTING) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…")
                        } else {
                            val count = state.matching.rows.size
                            Text("Create exam with $count student${if (count == 1) "" else "s"}")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (state.phase) {
            ExamImportPhase.IDLE, ExamImportPhase.PARSING -> IdleContent(
                state = state,
                onChooseFile = { filePicker.launch(importMimeTypes) },
                onDismissError = viewModel::dismissError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            ExamImportPhase.MAPPING, ExamImportPhase.COMMITTING -> MappingContent(
                state = state,
                committing = state.phase == ExamImportPhase.COMMITTING,
                onChooseAnother = { filePicker.launch(importMimeTypes) },
                onExamName = viewModel::onExamName,
                onSheet = viewModel::onSheetSelected,
                onStartRow = viewModel::onStartRowChanged,
                onEndRow = viewModel::onEndRowChanged,
                onNameColumn = viewModel::onNameColumn,
                onRollColumn = viewModel::onRollColumn,
                onSubjectIncluded = viewModel::onSubjectIncluded,
                onSubjectName = viewModel::onSubjectName,
                onSubjectMax = viewModel::onSubjectMax,
                onSubjectPass = viewModel::onSubjectPass,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            ExamImportPhase.DONE -> DoneContent(
                state = state,
                onOpenExam = { id -> onImported(id) },
                onClose = ::close,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun IdleContent(
    state: ExamImportUiState,
    onChooseFile: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.phase == ExamImportPhase.PARSING) {
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
            text = "Import an exam",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick the Excel (.xlsx) sheet your marks are already in. " +
                "The header row names the subjects — “Math (100)” or " +
                "“Science (50/18)” also sets the max and pass marks. You’ll " +
                "check everything before the exam is created.",
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
    state: ExamImportUiState,
    committing: Boolean,
    onChooseAnother: () -> Unit,
    onExamName: (String) -> Unit,
    onSheet: (String) -> Unit,
    onStartRow: (String) -> Unit,
    onEndRow: (String) -> Unit,
    onNameColumn: (Int) -> Unit,
    onRollColumn: (Int) -> Unit,
    onSubjectIncluded: (Int, Boolean) -> Unit,
    onSubjectName: (Int, String) -> Unit,
    onSubjectMax: (Int, String) -> Unit,
    onSubjectPass: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val table = state.table ?: return
    val plan = state.plan ?: return
    val matching = state.matching

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

        // --- Sheet and rows -----------------------------------------------
        // An exam often lives in one block of a bigger workbook, so the
        // teacher says which tab and which rows instead of importing the lot.
        Text(
            text = "Sheet and rows",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        if (state.sheets.size > 1) {
            SheetDropdownField(
                sheets = state.sheets,
                selected = state.sheetName,
                enabled = !committing,
                onSelect = onSheet,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.startRow,
                onValueChange = onStartRow,
                label = { Text("First row") },
                placeholder = { Text("1") },
                singleLine = true,
                enabled = !committing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.endRow,
                onValueChange = onEndRow,
                label = { Text("Last row (optional)") },
                placeholder = { Text("to the end") },
                singleLine = true,
                enabled = !committing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Numbers are the rows Excel shows. The first row of the " +
                "range is the header; leave the last row empty to read down " +
                "to the last filled row.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        InlineError(state.rangeError)
        if (state.reloading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Reading those rows…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Exam name -----------------------------------------------------
        Text(
            text = "Exam",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.examName,
            onValueChange = onExamName,
            label = { Text("Exam name") },
            singleLine = true,
            enabled = !committing,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Student columns ----------------------------------------------
        Text(
            text = "Which columns identify the student",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        ImportColumnDropdown(
            label = "Student name (optional)",
            options = table.headers,
            selected = plan.nameColumn,
            enabled = !committing,
            onSelect = onNameColumn,
        )
        Spacer(Modifier.height(8.dp))
        ImportColumnDropdown(
            label = "Roll number (optional)",
            options = table.headers,
            selected = plan.rollColumn,
            enabled = !committing,
            onSelect = onRollColumn,
        )
        Text(
            text = "Rows are matched by roll number first, then by name.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        InlineError(state.mappingError)

        // --- Subjects ------------------------------------------------------
        Text(
            text = "Subjects and marks",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        if (plan.subjects.isEmpty()) {
            Text(
                text = "No subject columns found. Put subject names in the " +
                    "header row — “Math (100)” or “Science (50/18)” sets the " +
                    "max and pass marks too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Subject",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Max",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
                Text(
                    text = "Pass",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
            }
            plan.subjects.forEach { subject ->
                SubjectRow(
                    subject = subject,
                    enabled = !committing,
                    onIncluded = onSubjectIncluded,
                    onName = onSubjectName,
                    onMax = onSubjectMax,
                    onPass = onSubjectPass,
                )
            }
        }
        InlineError(state.subjectError)

        // --- Preview -------------------------------------------------------
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = "${matching.rows.size} of ${table.rows.size} rows matched a " +
                "student in this class.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewTable(state = state, rows = matching.rows)

        val notes = buildList {
            if (matching.unmatched > 0) {
                add("${matching.unmatched} row${if (matching.unmatched == 1) "" else "s"} didn’t match a student and won’t be imported.")
            }
            if (matching.duplicates > 0) {
                add("${matching.duplicates} second row${if (matching.duplicates == 1) "" else "s"} for the same student will be skipped.")
            }
            if (matching.unreadableMarks > 0) {
                add("${matching.unreadableMarks} mark cell${if (matching.unreadableMarks == 1) "" else "s"} weren’t numbers and will be left blank.")
            }
        }
        notes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp)) // bottom-bar clearance
    }
}

@Composable
private fun SubjectRow(
    subject: ExamImportSubjectDraft,
    enabled: Boolean,
    onIncluded: (Int, Boolean) -> Unit,
    onName: (Int, String) -> Unit,
    onMax: (Int, String) -> Unit,
    onPass: (Int, String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = subject.included,
            onCheckedChange = { onIncluded(subject.column, it) },
            enabled = enabled,
        )
        OutlinedTextField(
            value = subject.name,
            onValueChange = { onName(subject.column, it) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = subject.maxMarks,
            onValueChange = { onMax(subject.column, it) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(84.dp),
        )
        OutlinedTextField(
            value = subject.passMarks,
            onValueChange = { onPass(subject.column, it) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(84.dp),
        )
    }
}

@Composable
private fun PreviewTable(state: ExamImportUiState, rows: List<ExamImportRow>) {
    val subjects = state.plan?.subjects.orEmpty().filter { it.included }.take(3)
    if (rows.isEmpty() || subjects.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            PreviewCell("Name", Modifier.weight(2f), isHeader = true)
            PreviewCell("Roll", Modifier.weight(1f), isHeader = true)
            subjects.forEach { subject ->
                PreviewCell(subject.name, Modifier.weight(1f), isHeader = true)
            }
        }
        rows.take(5).forEach { row ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                PreviewCell(row.name, Modifier.weight(2f))
                PreviewCell(row.roll, Modifier.weight(1f))
                subjects.forEach { subject ->
                    PreviewCell(markText(row.marks[subject.column]), Modifier.weight(1f))
                }
            }
        }
        if (rows.size > 5) {
            Text(
                text = "…and ${rows.size - 5} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun markText(mark: Double?): String = when {
    mark == null -> ""
    mark == mark.toLong().toDouble() -> mark.toLong().toString()
    else -> mark.toString()
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

@Composable
private fun InlineError(message: String?) {
    if (message == null) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DoneContent(
    state: ExamImportUiState,
    onOpenExam: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val examId = state.createdExamId
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Exam imported",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.doneMessage.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (examId != null) {
            Button(onClick = { onOpenExam(examId) }) { Text("Open exam") }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onClose) { Text("Done") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDropdownField(
    sheets: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected ?: sheets.firstOrNull().orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Sheet") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sheets.forEach { sheet ->
                DropdownMenuItem(
                    text = { Text(sheet) },
                    onClick = { expanded = false; onSelect(sheet) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportColumnDropdown(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (selected in options.indices) options[selected] else "Not used"
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
            DropdownMenuItem(
                text = {
                    Text(
                        "Not used",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { expanded = false; onSelect(-1) },
            )
            options.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = { expanded = false; onSelect(index) },
                )
            }
        }
    }
}
