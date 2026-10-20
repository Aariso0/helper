package com.example.helper.roster.photostorage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-internal student-photo storage (roster card: `photo-storage` seam).
 *
 * Convention — documented here for `export-backup`:
 *   directory: `<filesDir>/student_photos/`
 *   file name: `<studentId>_<epochMillis>.<ext>`
 *   Student.photoPath holds the absolute path of the current file.
 *
 * Picked photos are copied in immediately (URI grants are temporary — the
 * content:// URI is never stored on the Student row). Photos are local files
 * only: Coil loads them without any network component.
 */
class PhotoStorage(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val resolver = context.applicationContext.contentResolver

    /**
     * Copies the bytes behind [uri] into app-internal storage and returns the
     * absolute file path, or null when the URI could not be read. Any previous
     * photos of [studentId] are removed so the directory holds one file per
     * student.
     */
    suspend fun importFrom(uri: Uri, studentId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val extension = extensionFor(uri)
            val target = File(directory, "${studentId}_${System.currentTimeMillis()}.$extension")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            deleteFor(studentId) // drop stale photos only after the copy succeeded
            target.absolutePath
        }.getOrNull()
    }

    /** Removes every stored photo of [studentId] (call when the student is deleted). */
    fun deleteFor(studentId: String) {
        directory.listFiles { file ->
            file.isFile && file.name.startsWith("${studentId}_")
        }?.forEach { it.delete() }
    }

    /** True when [path] points at an existing stored photo. */
    fun exists(path: String?): Boolean = !path.isNullOrBlank() && File(path).exists()

    private fun extensionFor(uri: Uri): String = runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(index) ?: ""
                name.substringAfterLast('.', "").lowercase()
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
            } else null
        }
    }.getOrNull() ?: "jpg"

    companion object {
        const val DIRECTORY_NAME = "student_photos"
    }
}
