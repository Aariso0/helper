package com.example.helper.examsresults.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind
import com.example.helper.examsresults.export.ExportColumnIssue
import com.example.helper.examsresults.export.ExportFormulaEngine
import com.example.helper.examsresults.viewmodel.ExportColumnsViewModel

private val FUNCTIONS = listOf(
    "SUM", "AVERAGE", "MIN", "MAX", "ROUND", "IF", "AND", "OR", "NOT", "COUNT", "COUNTA", "ABS",
)

/**
 * The formula builder: reorder the columns that follow the student's marks,
 * add a custom formula column, and see the resolved Excel formula before
 * exporting. The palette inserts `{Fields}` so reordering never breaks a
 * reference; pasting a raw A1 formula is the advanced escape hatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportColumnsScreen(
    viewModel: ExportColumnsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val issues = remember(state.columns, state.subjects) {
        ExportFormulaEngine.validate(state.columns.filter { it.enabled }, state.subjects)
    }
    val issuesByColumn: Map<String, List<ExportColumnIssue>> = issues.groupBy { it.columnId }

    var editing by remember { mutableStateOf<ExportColumn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ExportColumn?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export columns") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add column") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp),
        ) {
            Text(
                "These columns appear after the subjects on the results sheet. " +
                    "Move them up or down; formulas follow the order automatically.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            state.columns.forEachIndexed { index, column ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(column.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formulaSummary(column),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.move(column.id, up = true) },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move ${column.label} up",
                            )
                        }
                        IconButton(
                            onClick = { viewModel.move(column.id, up = false) },
                            enabled = index < state.columns.lastIndex,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move ${column.label} down",
                            )
                        }
                        IconButton(onClick = { editing = column }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit ${column.label}",
                            )
                        }
                        IconButton(
                            onClick = { deleting = column },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete ${column.label}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    issuesByColumn[column.id].orEmpty().forEach { issue ->
                        Text(
                            text = issue.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }

            Text(
                "Total Max and Result are generated from the exam; the other " +
                    "columns accept a formula.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (adding) {
        FormulaEditorDialog(
            title = "New column",
            initial = ExportColumn(
                id = "preview-new",
                label = "",
                kind = ExportColumnKind.CUSTOM,
                formula = "",
                sortOrder = state.columns.size,
            ),
            columns = state.columns,
            subjects = state.subjects,
            onSave = { label, formula ->
                viewModel.addCustom(label, formula)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    editing?.let { column ->
        FormulaEditorDialog(
            title = "Edit ${column.label}",
            initial = column,
            columns = state.columns,
            subjects = state.subjects,
            onSave = { label, formula ->
                viewModel.update(column.copy(label = label.trim(), formula = formula.trim()))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { column ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${column.label}?") },
            text = {
                Text(
                    "The column is removed from the exported sheet. Any formula " +
                        "that uses it will show an error until you update that formula."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(column)
                        deleting = null
                    },
                ) { Text("Delete column", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

private fun formulaSummary(column: ExportColumn): String = when (column.kind) {
    ExportColumnKind.TOTAL_MAX -> "Fixed number"
    ExportColumnKind.RESULT -> "Pass / Fail from the subject pass marks"
    else -> column.formula.ifBlank { "(empty formula)" }
}

@Composable
private fun FormulaEditorDialog(
    title: String,
    initial: ExportColumn,
    columns: List<ExportColumn>,
    subjects: List<ExamSubject>,
    onSave: (label: String, formula: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var formula by remember(initial.id) { mutableStateOf(TextFieldValue(initial.formula)) }
    var pasteMode by remember(initial.id) { mutableStateOf(initial.formula.trimStart().startsWith("=")) }

    val readOnlyFormula = initial.kind == ExportColumnKind.TOTAL_MAX ||
        initial.kind == ExportColumnKind.RESULT

    val previewColumn = initial.copy(label = label.trim().ifEmpty { initial.label }, formula = formula.text)
    val previewColumns = if (columns.any { it.id == previewColumn.id }) {
        columns.map { if (it.id == previewColumn.id) previewColumn else it }
    } else {
        columns + previewColumn
    }
    val preview = if (readOnlyFormula) {
        ExportFormulaEngine.resolve(initial.copy(), columns, subjects, ExportFormulaEngine.PREVIEW_ROW)
    } else {
        ExportFormulaEngine.resolve(previewColumn, previewColumns, subjects, ExportFormulaEngine.PREVIEW_ROW)
    }
    val blockingError = if (readOnlyFormula) {
        null
    } else {
        ExportFormulaEngine.blockingError(previewColumn, previewColumns, subjects)
    }
    val notice = if (readOnlyFormula) {
        null
    } else {
        ExportFormulaEngine.notice(previewColumn, previewColumns, subjects)
    }
    val canSave = label.isNotBlank() && (readOnlyFormula || formula.text.isNotBlank()) &&
        blockingError == null

    fun insertAtCursor(text: String) {
        val selection = formula.selection
        val updated = formula.text.replaceRange(selection.start, selection.end, text)
        formula = TextFieldValue(
            text = updated,
            selection = TextRange(selection.start + text.length),
        )
    }

    fun insertFunction(name: String) {
        val selection = formula.selection
        val text = "$name()"
        val updated = formula.text.replaceRange(selection.start, selection.end, text)
        formula = TextFieldValue(
            text = updated,
            selection = TextRange(selection.start + name.length + 1),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Column name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!readOnlyFormula) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Paste Excel formula", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Advanced: A1 references won’t follow reordering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = pasteMode,
                            onCheckedChange = { pasted ->
                                pasteMode = pasted
                                val text = formula.text.trimStart()
                                formula = when {
                                    pasted && !text.startsWith("=") ->
                                        TextFieldValue("=$text", selection = TextRange(text.length + 1))
                                    !pasted && text.startsWith("=") -> {
                                        val body = text.removePrefix("=")
                                        TextFieldValue(body, selection = TextRange(body.length))
                                    }
                                    else -> formula.copy(text = text)
                                }
                            },
                        )
                    }
                }

                if (readOnlyFormula) {
                    Text(
                        if (initial.kind == ExportColumnKind.TOTAL_MAX) {
                            "This column writes the exam’s total maximum marks."
                        } else {
                            "This column is generated from the subject pass marks and can’t be edited."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    OutlinedTextField(
                        value = formula,
                        onValueChange = { formula = it },
                        label = { Text("Formula") },
                        minLines = 3,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!pasteMode) {
                        Text("Fields", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            AssistChip(
                                onClick = { insertAtCursor("{All Marks}") },
                                label = { Text("All Marks") },
                            )
                            AssistChip(
                                onClick = { insertAtCursor("{All Pass Checks}") },
                                label = { Text("All Pass Checks") },
                            )
                            subjects.forEach { subject ->
                                AssistChip(
                                    onClick = { insertAtCursor("{${subject.name}}") },
                                    label = { Text(subject.name) },
                                )
                            }
                            columns.forEach { column ->
                                AssistChip(
                                    onClick = { insertAtCursor("{${column.label}}") },
                                    label = { Text(column.label) },
                                )
                            }
                        }

                        Text("Functions", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            FUNCTIONS.forEach { name ->
                                AssistChip(
                                    onClick = { insertFunction(name) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    } else {
                        Text(
                            "The formula is written exactly as pasted, including its A1 " +
                                "references. Reordering columns can point it at the wrong cells. " +
                                "Use {Fields} when you want it to follow the order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = preview.fold(
                            onSuccess = { "Preview: $it" },
                            onFailure = { "Preview: ${it.message}" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (preview.isSuccess) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )

                    if (blockingError != null) {
                        Text(
                            "Can’t save: $blockingError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (notice != null) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim(), formula.text.trim()) },
                enabled = canSave,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
