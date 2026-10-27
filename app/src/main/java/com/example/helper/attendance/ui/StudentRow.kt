package com.example.helper.attendance.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.Student
import com.example.helper.motion.Motion
import com.example.helper.theme.ShellDimens

/**
 * One attendance row (attendance card `ui/StudentRow.kt`): the whole row is
 * one big tap target — tap = present/absent toggle, long-press = Late /
 * Excused sheet. No checkboxes, no per-row chrome (README rules 1 & 4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentRow(
    student: Student,
    status: AttendanceStatus,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = Motion.Spatial.Fast,
        label = "rowScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShellDimens.rowMinHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = ShellDimens.screenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!student.rollNumber.isNullOrBlank()) {
                Text(
                    text = student.rollNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (status) {
            AttendanceStatus.PRESENT -> Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Present",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AttendanceStatus.ABSENT -> StatusLabel("Absent", MaterialTheme.colorScheme.error)
            AttendanceStatus.LATE -> StatusLabel("Late", MaterialTheme.colorScheme.tertiary)
            AttendanceStatus.EXCUSED -> StatusLabel("Excused", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = TextAlign.End,
    )
}
