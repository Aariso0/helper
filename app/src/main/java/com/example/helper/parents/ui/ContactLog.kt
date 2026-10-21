package com.example.helper.parents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helper.parents.viewmodel.dateLabelFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The add-entry flow and row for the per-student contact log (card:
 * `ContactLog`). The teacher logs a call/message they already received —
 * nothing here dials, texts, or sends. Student is chosen through the roster
 * picker on the tab screen; this dialog collects what was said + the date
 * (defaults to today, backdatable — yesterday's call is a real case).
 */
@Composable
fun AddContactEntryDialog(
    studentName: String,
    onAdd: (text: String, dateEpochDay: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var dateEpochDay by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        EntryDatePicker(
            initialDateEpochDay = dateEpochDay,
            onDatePicked = {
                dateEpochDay = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "For $studentName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = dateLabelFor(dateEpochDay),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What did the parent say?") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text, dateEpochDay) },
                enabled = text.isNotBlank(),
            ) { Text("Log it") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EntryDatePicker(
    initialDateEpochDay: Long,
    onDatePicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = LocalDate.ofEpochDay(initialDateEpochDay)
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
                            Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay(),
                        )
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** One logged contact: student, date, what was said (parents card list row). */
@Composable
fun ContactEntryRowContent(
    studentName: String,
    text: String,
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = studentName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}
