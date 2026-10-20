package com.example.helper.homework.attachmentstore

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.helper.coredata.entity.AttachmentKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * `attachment-storage` (homework card seam, documented for `export-backup`):
 * picked and captured files are copied verbatim into the app-internal
 * `<filesDir>/attachments/` directory the moment they arrive — never shared
 * storage, never MediaStore. Files are named `<epochMillis>_<safe-name>`;
 * camera captures are `capture_<epochMillis>.jpg` (created before launching
 * the system camera through the app's FileProvider). Byte-for-byte copies
 * keep EXIF orientation intact, so Coil thumbnails render correctly.
 */
class AttachmentStore(private val context: Context) {

    val directory: File
        get() = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }

    /** Copies a SAF content URI into internal storage; returns the new file. */
    fun importFrom(uri: Uri, displayName: String?): File {
        val target = File(directory, "${System.currentTimeMillis()}_${sanitize(displayName ?: "file")}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
        input.use { source ->
            FileOutputStream(target).use { targetStream ->
                source.copyTo(targetStream)
            }
        }
        return target
    }

    /** Fresh empty file for a `TakePicture` capture (FileProvider-served). */
    fun newCaptureTarget(): File =
        File(directory, "capture_${System.currentTimeMillis()}.jpg")

    /** Deletes a stored file; refuses paths outside the attachments dir. */
    fun delete(file: File) {
        if (file.path.startsWith(directory.path)) {
            file.delete()
        }
    }

    /** Display name of a SAF URI, for naming the copied file. */
    fun displayNameOf(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment
    }

    private fun sanitize(name: String): String {
        val cleaned = name
            .substringAfterLast('/')
            .map { if (it.isLetterOrDigit() || it in "._-") it else '_' }
            .joinToString("")
            .trim { it == '_' || it == '.' }
            .ifBlank { "file" }
        return cleaned.take(MAX_NAME_LENGTH)
    }

    companion object {
        const val ATTACHMENTS_DIR = "attachments"
        private const val MAX_NAME_LENGTH = 80

        /** Kind from extension; OTHER tolerates whatever SAF returns. */
        fun kindFor(fileName: String): AttachmentKind = when (extensionOf(fileName)) {
            "jpg", "jpeg", "png", "webp", "gif", "heic", "heif" -> AttachmentKind.IMAGE
            "pdf" -> AttachmentKind.PDF
            "ppt", "pptx" -> AttachmentKind.PPT
            else -> AttachmentKind.OTHER
        }

        /** Mime from extension; null = let the share sheet sniff. */
        fun mimeFor(fileName: String): String? = when (extensionOf(fileName)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "pdf" -> "application/pdf"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> null
        }

        private fun extensionOf(fileName: String): String =
            fileName.substringAfterLast('.', "").lowercase()
    }
}
