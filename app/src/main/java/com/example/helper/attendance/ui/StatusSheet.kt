package com.example.helper.attendance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.motion.rememberThrottledClick
import com.example.helper.theme.ShellDimens

/**
 * Long-press status picker (attendance card `ui/StatusSheet.kt`): the rare
 * statuses (Late / Excused) live here so they never crowd the tap fast path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSheet(
    current: AttendanceStatus,
    onStatusChosen: (AttendanceStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Set status",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ShellDimens.screenPadding, vertical = 8.dp),
        )
        val throttle = rememberThrottledClick(300L)
        AttendanceStatus.entries.forEach { status ->
            val onClick = throttle { onStatusChosen(status) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShellDimens.rowMinHeight)
                    .clickable(onClick = onClick)
                    .padding(horizontal = ShellDimens.screenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (status == current) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Current",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun statusLabel(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.PRESENT -> "Present"
    AttendanceStatus.ABSENT -> "Absent"
    AttendanceStatus.LATE -> "Late"
    AttendanceStatus.EXCUSED -> "Excused"
}
