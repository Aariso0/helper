package com.example.helper.homework.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttachmentKind
import java.io.File

/**
 * One attachment row (card: `AttachmentRow`): images render a Coil thumbnail
 * (EXIF-aware), PDFs open through a system viewer intent, PPT/other files
 * carry filename + badge only — attach-and-share never had a preview. Only
 * PDFs are tappable ([onOpen], wired by the edit screen); every row can be
 * removed.
 */
@Composable
fun AttachmentRow(
    attachment: Attachment,
    onOpen: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val clickableModifier = if (onOpen != null) {
        Modifier.clickable(onClick = onOpen)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> {
                val file = File(attachment.filePath)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    MissingBadge()
                }
            }

            else -> KindBadge(kind = attachment.kind)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (attachment.kind) {
                    AttachmentKind.IMAGE -> "Photo"
                    AttachmentKind.PDF -> "PDF · opens with a viewer app"
                    AttachmentKind.PPT -> "PowerPoint · shares, no preview"
                    AttachmentKind.OTHER -> attachment.fileName
                        .substringAfterLast('.', "")
                        .uppercase()
                        .ifEmpty { "File" }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${attachment.fileName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Extension badge for non-image files — honest about what can be opened. */
@Composable
private fun KindBadge(kind: AttachmentKind) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(48.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = when (kind) {
                    AttachmentKind.PDF -> "PDF"
                    AttachmentKind.PPT -> "PPT"
                    else -> "FILE"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Stored file went missing (deleted elsewhere) — degrade honestly. */
@Composable
private fun MissingBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(48.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
