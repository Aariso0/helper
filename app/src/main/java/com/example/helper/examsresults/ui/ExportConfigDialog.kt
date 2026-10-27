package com.example.helper.examsresults.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.helper.examsresults.viewmodel.ExportConfig

/**
 * Only the Result column is colour-coded (PASS/FAIL); every other row stays
 * neutral, so the dialog offers just those two choices.
 */
@Composable
fun ExportConfigDialog(
    config: ExportConfig,
    onConfigChange: (ExportConfig) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = listOf(
        0xFF4CAF50L to "Green",
        0xFF2E7D32L to "Dark Green",
        0xFFF44336L to "Red",
        0xFFB71C1CL to "Dark Red",
        0xFFFF9800L to "Orange",
        0xFF9C27B0L to "Purple",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Colours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Only the Result column is coloured — all other rows stay neutral. " +
                        "The colour follows the PASS/FAIL formula, so it updates when marks change.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("PASS", style = MaterialTheme.typography.labelLarge)
                ColorRow(colors = colors, selected = config.passColor) {
                    onConfigChange(config.copy(passColor = it))
                }
                Text("FAIL", style = MaterialTheme.typography.labelLarge)
                ColorRow(colors = colors, selected = config.failColor) {
                    onConfigChange(config.copy(failColor = it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport) {
                Text("Export & Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ColorRow(
    colors: List<Pair<Long, String>>,
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { (color, _) ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selected == color) 2.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}
