package com.example.helper.studentfeedback.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.FeedbackTag

/**
 * The teacher-editable preset tag row (card: TagChips).
 * Tap selects/commits via [onTagClick]; long-press edits in place (rename /
 * delete dialog); "+" adds — no settings screen, no setup wall.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TagChipRow(
    tags: List<FeedbackTag>,
    onTagClick: (FeedbackTag) -> Unit,
    onAddTag: (String) -> Unit,
    onRenameTag: (FeedbackTag, String) -> Unit,
    onDeleteTag: (FeedbackTag) -> Unit,
    modifier: Modifier = Modifier,
    selectedTagId: String? = null,
    emptyHint: String = "No tags yet — add one with +",
) {
    var tagBeingEdited by remember { mutableStateOf<FeedbackTag?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tags.isEmpty()) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        tags.forEach { tag ->
            // Long-press detection lives on a wrapper *around* the chip, not on
            // the chip itself: the chip's own clickable owns taps (so a tap
            // can never fire twice or get swallowed), and the wrapper's
            // onClick is an empty no-op that only exists so combinedClickable
            // can host onLongClick.
            Box(modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = { tagBeingEdited = tag },
            )) {
                FilterChip(
                    selected = tag.id == selectedTagId,
                    onClick = { onTagClick(tag) },
                    label = {
                        Text(tag.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        androidx.compose.material3.AssistChip(
            onClick = { showAddDialog = true },
            label = { Text("Add") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            },
        )
    }

    tagBeingEdited?.let { tag ->
        EditTagDialog(
            tag = tag,
            onRename = { newLabel -> onRenameTag(tag, newLabel); tagBeingEdited = null },
            onDelete = { onDeleteTag(tag); tagBeingEdited = null },
            onDismiss = { tagBeingEdited = null },
        )
    }

    if (showAddDialog) {
        AddTagDialog(
            onAdd = { label -> onAddTag(label); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun EditTagDialog(
    tag: FeedbackTag,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(tag.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tag") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Tag name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onRename(label) },
                enabled = label.isNotBlank() && label.trim() != tag.label,
            ) { Text("Rename") }
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
private fun AddTagDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Tag name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank()) onAdd(label) },
                enabled = label.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
