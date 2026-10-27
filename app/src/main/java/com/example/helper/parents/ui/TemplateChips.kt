package com.example.helper.parents.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.motion.Motion
import com.example.helper.scaffold.onLongPress

/**
 * The teacher-editable template row (card: `TemplateChips`). Recent-first
 * (the DAO orders by last-used). Tap applies a template to the draft — the
 * caller confirms before overwriting a dirty draft. Long-press edits the
 * template in place (name + message, delete); "+" saves the current draft as
 * a new template. No settings screen, no setup wall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateChipRow(
    templates: List<AnnouncementTemplate>,
    onApply: (AnnouncementTemplate) -> Unit,
    onUpdate: (AnnouncementTemplate, String?, String) -> Unit,
    onDelete: (AnnouncementTemplate) -> Unit,
    onSaveAs: (String?) -> Unit,
    saveAsInitialTitle: String,
    modifier: Modifier = Modifier,
) {
    var templateBeingEdited by remember { mutableStateOf<AnnouncementTemplate?>(null) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (templates.isEmpty()) {
            Text(
                text = "No templates yet — write an announcement and tap " +
                    "“Save as template” to reuse it later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        templates.forEach { template ->
            // Same fix as feedback's TagChipRow: the long-press sits on the
            // chip's own modifier, because a wrapper around the chip never
            // received the press.
            val scale by animateFloatAsState(1f, Motion.Spatial.Pop, label = "tmplScale")
            FilterChip(
                selected = false,
                onClick = { onApply(template) },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onLongPress { templateBeingEdited = template },
                label = {
                    Text(
                        text = template.title?.takeIf { it.isNotBlank() }
                            ?: template.body.lineSequence().first().take(CHIP_LABEL_MAX),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        AssistChip(
            onClick = { showSaveAsDialog = true },
            label = { Text("Save as template") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = "Save as template")
            },
        )
    }

    templateBeingEdited?.let { template ->
        EditTemplateDialog(
            template = template,
            onUpdate = { title, body ->
                onUpdate(template, title, body)
                templateBeingEdited = null
            },
            onDelete = {
                onDelete(template)
                templateBeingEdited = null
            },
            onDismiss = { templateBeingEdited = null },
        )
    }

    if (showSaveAsDialog) {
        SaveAsTemplateDialog(
            initialTitle = saveAsInitialTitle,
            onSave = { title ->
                onSaveAs(title)
                showSaveAsDialog = false
            },
            onDismiss = { showSaveAsDialog = false },
        )
    }

    Text(
        text = "Tap a template to use it · long-press to edit or delete",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EditTemplateDialog(
    template: AnnouncementTemplate,
    onUpdate: (String?, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(template.title.orEmpty()) }
    var body by remember { mutableStateOf(template.body) }

    val changed = title.trim().ifEmpty { null } != template.title || body.trim() != template.body

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Template name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(title, body) },
                enabled = body.isNotBlank() && changed,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("Delete")
            }
        },
    )
}

@Composable
private fun SaveAsTemplateDialog(
    initialTitle: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as template") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Template name (optional)") },
                supportingText = { Text("Recurring messages become one tap.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val CHIP_LABEL_MAX = 40
