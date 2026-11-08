package com.example.helper.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.helper.HelperApplication
import com.example.helper.coredata.entity.SchoolSession
import com.example.helper.coredata.session.AcademicYear
import kotlinx.coroutines.launch

/**
 * App-wide session switcher, shown in the top bar of the four tab roots.
 *
 * Tapping a session *views* it (Today, Students, Homework and Parents all
 * follow through `SessionScope`); the star moves the `(curr.)` marker without
 * changing what is being viewed. "New session…" creates the next academic
 * year and switches to it, but never makes it current on its own.
 */
@Composable
fun SessionMenu(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as HelperApplication
    val container = app.container
    val sessions by container.sessionRepository.observeAll().collectAsState(initial = emptyList())
    val selectedId by container.sessionScope.selectedId.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    val selected = sessions.firstOrNull { it.id == selectedId }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(selected?.label ?: "Session", style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Choose session",
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { session ->
                DropdownMenuItem(
                    text = { Text(sessionLabel(session)) },
                    onClick = {
                        expanded = false
                        container.sessionScope.select(session.id)
                    },
                    trailingIcon = {
                        if (session.isCurrent) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Current session",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            IconButton(
                                onClick = { container.sessionScope.makeCurrent(session.id) },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Make ${session.label} current",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
            }
            if (sessions.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New session…") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    showNewSession = true
                },
            )
        }
    }

    if (showNewSession) {
        NewSessionDialog(
            sessions = sessions,
            onDismiss = { showNewSession = false },
        )
    }
}

private fun sessionLabel(session: SchoolSession): String =
    if (session.isCurrent) "${session.label} (curr.)" else session.label

/** Next label after the newest existing one; the academic year of today when empty. */
private fun suggestedLabel(sessions: List<SchoolSession>): String =
    sessions.firstOrNull()?.let { AcademicYear.nextLabel(it.label) }
        ?: AcademicYear.currentLabel()

@Composable
private fun NewSessionDialog(
    sessions: List<SchoolSession>,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as HelperApplication
    val container = app.container
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(suggestedLabel(sessions)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New session") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        error = null
                    },
                    label = { Text("Session label") },
                    singleLine = true,
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching { container.sessionRepository.create(label.trim()) }
                            .onSuccess { created ->
                                container.sessionScope.select(created.id)
                                saving = false
                                onDismiss()
                            }
                            .onFailure {
                                error = it.message ?: "Couldn't create the session."
                                saving = false
                            }
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}
