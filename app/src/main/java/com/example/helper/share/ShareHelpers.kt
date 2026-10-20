package com.example.helper.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * `share-helpers` (app-shell seam): system share-sheet builders. The prototype
 * sends nothing anywhere else — no uploads, no messaging SDKs — so every
 * "Share" in the app (attendance absences, homework, announcements, exports)
 * resolves to one of these two calls.
 */

/** Authority of the app's single FileProvider (`<applicationId>.fileprovider`). */
fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

/**
 * Content URI for an app-internal file (photo-storage / attachment-storage /
 * export dirs), for sharing or opening through the provider declared in the
 * manifest. Throws [IllegalArgumentException] if the file sits outside the
 * paths listed in `res/xml/file_paths.xml` — fail loudly, never share a path
 * the provider cannot serve.
 */
fun File.asShareableUri(context: Context): Uri =
    FileProvider.getUriForFile(context, fileProviderAuthority(context), this)

/** Plain-text share (ACTION_SEND). Returns false if no handler exists. */
fun shareText(
    context: Context,
    text: String,
    subject: String? = null,
): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(context, send, subject)
}

/**
 * File-capable share: content URIs + their mime types (one per URI, or null
 * meaning "any type"), optional caption text riding along. Uses
 * ACTION_SEND_MULTIPLE for more than one file. Returns false if no handler
 * exists.
 */
fun shareFiles(
    context: Context,
    uris: List<Uri>,
    mimeTypes: List<String?>? = null,
    text: String? = null,
    subject: String? = null,
): Boolean {
    require(uris.isNotEmpty()) { "shareFiles needs at least one Uri" }
    val multiple = uris.size > 1
    val type = when {
        multiple -> "*/*"
        else -> mimeTypes?.firstOrNull() ?: "*/*"
    }
    val intent = Intent(if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
        this.type = type
        if (multiple) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        } else {
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
        // Grant every receiver read access — content URIs from our FileProvider.
        clipData = ClipData(
            subject ?: "share",
            arrayOf(type),
            ClipData.Item(uris.first()),
        ).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(context, intent, subject)
}

private fun startChooser(context: Context, intent: Intent, subject: String?): Boolean {
    val chooser = Intent.createChooser(intent, subject)
    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
