package com.example.helper.homework.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.helper.share.asShareableUri
import java.io.File

/**
 * Whiteboard capture (card: `CameraShortcut`): one tap launches the system
 * camera on a fresh FileProvider-served target file — no CameraX, no custom
 * UI. Returns the launch lambda; call it from a "Camera" button. On success
 * [onCaptured] receives the file (kept verbatim so EXIF survives), on
 * cancellation [onCaptureCancelled] receives it for deletion.
 */
@Composable
fun rememberCameraCapture(
    newTarget: () -> File,
    onCaptured: (File) -> Unit,
    onCaptureCancelled: (File) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val pendingTarget = remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val file = pendingTarget.value ?: return@rememberLauncherForActivityResult
        pendingTarget.value = null
        if (success) {
            onCaptured(file)
        } else {
            onCaptureCancelled(file)
        }
    }
    return {
        val file = newTarget()
        pendingTarget.value = file
        camera.launch(file.asShareableUri(context))
    }
}
