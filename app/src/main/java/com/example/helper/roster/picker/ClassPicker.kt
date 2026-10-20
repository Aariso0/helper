package com.example.helper.roster.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.roster.viewmodel.pickerViewModel

/**
 * `class-student-picker` — choose a Class (roster card seam). Encapsulates its
 * own data loading: consumers only wire [onPick] / [onDismiss].
 * Pass [selectedClassId] to show the current choice checked.
 */
@Composable
fun ClassPicker(
    onPick: (SchoolClass) -> Unit,
    onDismiss: () -> Unit,
    selectedClassId: String? = null,
) {
    val viewModel = pickerViewModel()
    val query by viewModel.query.collectAsState()
    val classes by viewModel.classes.collectAsState()

    PickerDialog(
        title = "Choose a class",
        query = query,
        onQueryChanged = viewModel::onQueryChanged,
        onDismiss = onDismiss,
    ) {
        if (classes.isEmpty()) {
            Text(
                text = if (query.isBlank()) "No classes yet — create one first."
                else "No classes match “$query”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(classes, key = { it.id }) { schoolClass ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(schoolClass) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = schoolClass.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = listOfNotNull(
                                schoolClass.subject,
                                schoolClass.year,
                            ).joinToString(" · ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (schoolClass.id == selectedClassId) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
