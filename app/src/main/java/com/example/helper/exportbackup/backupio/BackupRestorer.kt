package com.example.helper.exportbackup.backupio

import android.content.Context
import android.net.Uri
import com.example.helper.coredata.AppContainer
import com.example.helper.coredata.AppDatabase
import com.example.helper.homework.attachmentstore.AttachmentStore
import com.example.helper.roster.photostorage.PhotoStorage
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The chosen file is not a usable Helper backup — nothing was touched. */
class RestoreRejected(message: String) : Exception(message)

/** The swap failed; the previous data was rolled back (a restart is still needed). */
class RestoreFailed(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Validated restore (`export-backup` card: validate → confirm → replace).
 *
 * [inspect] only reads the manifest (nothing is modified) so the UI can show
 * what is inside and ask for the destructive confirmation. [restore] then:
 *
 * 1. extracts the whole zip into `cacheDir/restore_staging` first — every
 *    failure up to here leaves the live data untouched;
 * 2. re-validates the staged manifest (app id + schema version);
 * 3. closes the Room database, moves the current DB + both file stores into
 *    `filesDir/restore_previous` (a rollback point), then moves the staged
 *    DB and directories into place;
 * 4. on any failure, moves the previous data back and reports [RestoreFailed];
 * 5. deletes the rollback copy on success.
 *
 * Because the database instance is closed, the caller restarts the process
 * ([AppRestart]) — every ViewModel's repository belongs to the closed
 * instance.
 */
class BackupRestorer(
    private val context: Context,
    private val container: AppContainer,
) {

    suspend fun inspect(uri: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val scan = scan(uri)
        val manifest = scan.manifestText
            ?.let(BackupManifest::fromJson)
            ?: throw RestoreRejected("This file isn't a Helper backup.")
        validate(manifest)
        if (!scan.hasDatabase) {
            throw RestoreRejected("This backup is incomplete — its database is missing.")
        }
        manifest
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extract(uri, staging)

            val manifest = File(staging, BackupManifest.ENTRY_NAME)
                .takeIf { it.isFile }
                ?.readText(Charsets.UTF_8)
                ?.let(BackupManifest::fromJson)
                ?: throw RestoreRejected("This file isn't a Helper backup.")
            validate(manifest)

            val stagedDatabase = File(staging, BackupManifest.DATABASE_ENTRY)
            if (!stagedDatabase.isFile) {
                throw RestoreRejected("This backup is incomplete — its database is missing.")
            }

            // Last moment before anything live is touched.
            container.closeDatabaseForRestore()
            swap(staging, stagedDatabase)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun validate(manifest: BackupManifest) {
        if (manifest.appId != context.packageName) {
            throw RestoreRejected(
                "This backup was made by a different app (${manifest.appId}), so it can't be restored here.",
            )
        }
        if (manifest.schemaVersion != AppDatabase.SCHEMA_VERSION) {
            throw RestoreRejected(
                "This backup uses data version ${manifest.schemaVersion}, but this app uses " +
                    "${AppDatabase.SCHEMA_VERSION}. Update the app, then restore again.",
            )
        }
    }

    private data class Scan(val manifestText: String?, val hasDatabase: Boolean)

    /** Reads only the zip's central entries — no extraction. */
    private fun scan(uri: Uri): Scan {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw RestoreRejected("Couldn't read the chosen file.")
        var manifestText: String? = null
        var hasDatabase = false
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupManifest.ENTRY_NAME -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                    BackupManifest.DATABASE_ENTRY -> hasDatabase = true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Scan(manifestText, hasDatabase)
    }

    private fun extract(uri: Uri, target: File) {
        val root = target.canonicalFile
        val input = context.contentResolver.openInputStream(uri)
            ?: throw RestoreRejected("Couldn't read the chosen file.")
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outputFile = File(target, entry.name).canonicalFile
                    // Zip-slip guard: entries must stay inside the staging dir.
                    if (!outputFile.path.startsWith(root.path + File.separator)) {
                        throw RestoreRejected("This backup contains an unsafe file path.")
                    }
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output -> zip.copyTo(output) }
                    totalBytes += outputFile.length()
                    if (totalBytes > MAX_RESTORE_BYTES) {
                        throw RestoreRejected("This backup is too large to restore.")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun swap(staging: File, stagedDatabase: File) {
        val databaseFile = container.databaseFile()
        val filesDir = context.filesDir
        val previous = File(filesDir, PREVIOUS_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val photoDir = File(filesDir, PhotoStorage.DIRECTORY_NAME)
        val attachmentDir = File(filesDir, AttachmentStore.ATTACHMENTS_DIR)
        val stagedPhotos = File(staging, "files/${PhotoStorage.DIRECTORY_NAME}")
        val stagedAttachments = File(staging, "files/${AttachmentStore.ATTACHMENTS_DIR}")

        try {
            databaseFile.parentFile?.mkdirs()
            move(databaseFile, File(previous, databaseFile.name))
            move(File("${databaseFile.path}-wal"), File(previous, "${databaseFile.name}-wal"))
            move(File("${databaseFile.path}-shm"), File(previous, "${databaseFile.name}-shm"))
            move(photoDir, File(previous, PhotoStorage.DIRECTORY_NAME))
            move(attachmentDir, File(previous, AttachmentStore.ATTACHMENTS_DIR))

            stagedDatabase.copyTo(databaseFile, overwrite = true)
            replaceDirectory(stagedPhotos, photoDir)
            replaceDirectory(stagedAttachments, attachmentDir)

            // The old WAL/SHM belong to the old file and must never attach to the new one.
            File("${databaseFile.path}-wal").delete()
            File("${databaseFile.path}-shm").delete()
        } catch (failure: Throwable) {
            rollback(previous, databaseFile, photoDir, attachmentDir)
            throw RestoreFailed(
                "Restore failed, so your previous data was kept. Reopen the app to continue.",
                failure,
            )
        }
        previous.deleteRecursively()
    }

    private fun rollback(previous: File, databaseFile: File, photoDir: File, attachmentDir: File) {
        runCatching {
            databaseFile.delete()
            File("${databaseFile.path}-wal").delete()
            File("${databaseFile.path}-shm").delete()
            photoDir.deleteRecursively()
            attachmentDir.deleteRecursively()
            move(File(previous, databaseFile.name), databaseFile)
            move(File(previous, "${databaseFile.name}-wal"), File("${databaseFile.path}-wal"))
            move(File(previous, "${databaseFile.name}-shm"), File("${databaseFile.path}-shm"))
            move(File(previous, PhotoStorage.DIRECTORY_NAME), photoDir)
            move(File(previous, AttachmentStore.ATTACHMENTS_DIR), attachmentDir)
        }
        previous.deleteRecursively()
    }

    /** Moves [source] over [destination]; no-op when the source is absent. */
    private fun move(source: File, destination: File) {
        if (!source.exists()) return
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.deleteRecursively()
        if (!source.renameTo(destination)) {
            source.copyRecursively(destination, overwrite = true)
            source.deleteRecursively()
        }
    }

    private fun replaceDirectory(staged: File, destination: File) {
        destination.deleteRecursively()
        if (staged.exists()) {
            if (!staged.renameTo(destination)) {
                staged.copyRecursively(destination, overwrite = true)
                staged.deleteRecursively()
            }
        } else {
            destination.mkdirs()
        }
    }

    private companion object {
        const val STAGING_DIR = "restore_staging"
        const val PREVIOUS_DIR = "restore_previous"

        /** 512 MB — far above any realistic classroom backup, a hard stop on a hostile zip. */
        const val MAX_RESTORE_BYTES = 512L * 1024 * 1024
    }
}
