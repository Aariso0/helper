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
import com.example.helper.coredata.entity.Student
import com.example.helper.roster.viewmodel.pickerViewModel

/**
 * `class-student-picker` — choose a Student (roster card seam).
 * [classId] narrows the list to one class; null lists every student (each row
 * then shows its class name). [onPick] receives the chosen Student.
 */
@Composable
fun StudentPicker(
    classId: String?,
    onPick: (Student) -> Unit,
    onDismiss: () -> Unit,
    selectedStudentId: String? = null,
) {
    val viewModel = pickerViewModel()
    val query by viewModel.query.collectAsState()
    val studentsByClassId by viewModel.studentsByClassId.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val classNameById = classes.associateBy({ it.id }, { it.name })

    val students = if (classId != null) {
        studentsByClassId[classId].orEmpty()
    } else {
        studentsByClassId.values.flatten()
    }

    PickerDialog(
        title = "Choose a student",
        query = query,
        onQueryChanged = viewModel::onQueryChanged,
        onDismiss = onDismiss,
    ) {
        if (students.isEmpty()) {
            Text(
                text = when {
                    query.isNotBlank() -> "No students match “$query”"
                    classId != null -> "No students in this class yet"
                    else -> "No students yet — add or import some first"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn {
                items(students, key = { it.id }) { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(student) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = buildString {
                                if (classId == null) {
                                    classNameById[student.classId]?.let { append(it) }
                                }
                                student.rollNumber?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append("Roll $it")
                                }
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (student.id == selectedStudentId) {
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
