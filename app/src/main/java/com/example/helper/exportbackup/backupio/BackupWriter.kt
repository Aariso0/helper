package com.example.helper.exportbackup.backupio

import android.content.Context
import android.net.Uri
import com.example.helper.coredata.AppContainer
import com.example.helper.coredata.AppDatabase
import com.example.helper.coredata.dao.HomeworkWithAttachments
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttendanceRecord
import com.example.helper.coredata.entity.ContactLogEntry
import com.example.helper.coredata.entity.FeedbackNote
import com.example.helper.coredata.entity.FeedbackTag
import com.example.helper.coredata.entity.Homework
import com.example.helper.coredata.entity.SchoolClass
import com.example.helper.coredata.entity.Student
import com.example.helper.homework.attachmentstore.AttachmentStore
import com.example.helper.roster.photostorage.PhotoStorage
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

/**
 * Creates the single-file manual backup (`export-backup` card):
 *
 * ```
 * manifest.json                  marker, app id, schema version, counts
 * database/helper.db             checkpointed Room file (the restore payload)
 * files/student_photos/...       photo-storage, byte for byte
 * files/attachments/...          attachment-storage, byte for byte
 * csv/<table>.csv                every table, human-readable (one row per record)
 * ```
 *
 * The DB file is what restore uses; the CSVs exist so the teacher's data is
 * also usable outside the app (the user's standing requirement: *any* entered
 * data must be exportable/backed up). Nothing is uploaded anywhere — the
 * teacher picks the destination through SAF.
 */
class BackupWriter(
    private val context: Context,
    private val container: AppContainer,
) {

    suspend fun write(uri: Uri): BackupManifest = withContext(Dispatchers.IO) {
        val snapshot = readSnapshot()
        // Flush the WAL *before* the DB bytes enter the zip (card build note).
        container.checkpointForBackup()
        val databaseFile = container.databaseFile()
        if (!databaseFile.isFile) throw IOException("The database file is missing.")

        val fileGroups = listOf(PhotoStorage.DIRECTORY_NAME, AttachmentStore.ATTACHMENTS_DIR)
            .associateWith { dirName -> filesUnder(dirName) }
        val manifest = BackupManifest(
            appId = context.packageName,
            schemaVersion = AppDatabase.SCHEMA_VERSION,
            createdAtMillis = System.currentTimeMillis(),
            tableCounts = snapshot.tableCounts(),
            fileCount = fileGroups.values.sumOf { it.size },
        )

        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Couldn't open the chosen location for writing.")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putBytes(BackupManifest.ENTRY_NAME, manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.putFile(BackupManifest.DATABASE_ENTRY, databaseFile)
            fileGroups.forEach { (dirName, files) ->
                files.forEach { (relativePath, file) -> zip.putFile("files/$dirName/$relativePath", file) }
            }
            snapshot.csvTables().forEach { (table, bytes) -> zip.putBytes("csv/$table.csv", bytes) }
        }
        manifest
    }

    // ── Snapshot: every row, through the repositories (never Room directly) ─

    private suspend fun readSnapshot(): Snapshot = Snapshot(
        classes = container.classRepository.observeAll().first(),
        students = container.studentRepository.observeAll().first(),
        attendance = container.attendanceRepository.getAll(),
        tags = container.feedbackRepository.observeTags().first(),
        notes = container.feedbackRepository.observeNotes().first(),
        announcements = container.announcementRepository.observeAnnouncements().first(),
        templates = container.announcementRepository.observeTemplates().first(),
        contactLog = container.contactLogRepository.observeEntries().first(),
        homework = container.homeworkRepository.observeAll().first(),
    )

    private fun filesUnder(dirName: String): List<Pair<String, File>> {
        val root = File(context.filesDir, dirName)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path to it }
            .toList()
    }

    private fun ZipOutputStream.putBytes(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(this) }
        closeEntry()
    }
}

/** All repository rows at backup time — the source of the CSV copies. */
private data class Snapshot(
    val classes: List<SchoolClass>,
    val students: List<Student>,
    val attendance: List<AttendanceRecord>,
    val tags: List<FeedbackTag>,
    val notes: List<FeedbackNote>,
    val announcements: List<Announcement>,
    val templates: List<AnnouncementTemplate>,
    val contactLog: List<ContactLogEntry>,
    val homework: List<HomeworkWithAttachments>,
) {
    private val attachments: List<Attachment> get() = homework.flatMap { it.attachments }

    fun tableCounts(): Map<String, Int> = linkedMapOf(
        "classes" to classes.size,
        "students" to students.size,
        "attendance_records" to attendance.size,
        "feedback_tags" to tags.size,
        "feedback_notes" to notes.size,
        "announcements" to announcements.size,
        "announcement_templates" to templates.size,
        "contact_log_entries" to contactLog.size,
        "homework" to homework.size,
        "attachments" to attachments.size,
    )

    /** One CSV per table: every column, human-readable dates, UTF-8. */
    fun csvTables(): Map<String, ByteArray> = linkedMapOf(
        "classes" to csv(
            listOf("id", "name", "subject", "year", "createdAt", "updatedAt"),
            classes.map { listOf(it.id, it.name, it.subject.orEmpty(), it.year.orEmpty(), isoMillis(it.createdAt), isoMillis(it.updatedAt)) },
        ),
        "students" to csv(
            listOf("id", "classId", "name", "rollNumber", "parentContact", "photoPath", "createdAt", "updatedAt"),
            students.map {
                listOf(it.id, it.classId, it.name, it.rollNumber.orEmpty(), it.parentContact.orEmpty(), it.photoPath.orEmpty(), isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "attendance_records" to csv(
            listOf("id", "classId", "studentId", "date", "dateEpochDay", "status", "note", "period", "createdAt", "updatedAt"),
            attendance.map {
                listOf(it.id, it.classId, it.studentId, isoDate(it.dateEpochDay), it.dateEpochDay, it.status.name, it.note.orEmpty(), it.period.orEmpty(), isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "feedback_tags" to csv(
            listOf("id", "label", "sortOrder", "createdAt", "updatedAt"),
            tags.map { listOf(it.id, it.label, it.sortOrder, isoMillis(it.createdAt), isoMillis(it.updatedAt)) },
        ),
        "feedback_notes" to csv(
            listOf("id", "studentId", "tagId", "tagLabel", "text", "date", "dateEpochDay", "createdAt", "updatedAt"),
            notes.map {
                listOf(it.id, it.studentId, it.tagId.orEmpty(), it.tagLabel, it.text.orEmpty(), isoDate(it.dateEpochDay), it.dateEpochDay, isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "announcements" to csv(
            listOf("id", "classId", "className", "title", "body", "date", "dateEpochDay", "createdAt", "updatedAt"),
            announcements.map {
                listOf(it.id, it.classId.orEmpty(), it.className.orEmpty(), it.title.orEmpty(), it.body, isoDate(it.dateEpochDay), it.dateEpochDay, isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "announcement_templates" to csv(
            listOf("id", "title", "body", "lastUsedAt", "createdAt", "updatedAt"),
            templates.map {
                listOf(it.id, it.title.orEmpty(), it.body, it.lastUsedAt?.let(::isoMillis).orEmpty(), isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "contact_log_entries" to csv(
            listOf("id", "studentId", "studentName", "text", "date", "dateEpochDay", "createdAt", "updatedAt"),
            contactLog.map {
                listOf(it.id, it.studentId, it.studentName, it.text, isoDate(it.dateEpochDay), it.dateEpochDay, isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
        "homework" to csv(
            listOf("id", "classId", "title", "text", "dueDate", "dueDateEpochDay", "createdAt", "updatedAt"),
            homework.map { row ->
                val item: Homework = row.homework
                listOf(
                    item.id,
                    item.classId,
                    item.title,
                    item.text.orEmpty(),
                    item.dueDateEpochDay?.let(::isoDate).orEmpty(),
                    item.dueDateEpochDay?.toString().orEmpty(),
                    isoMillis(item.createdAt),
                    isoMillis(item.updatedAt),
                )
            },
        ),
        "attachments" to csv(
            listOf("id", "homeworkId", "kind", "fileName", "filePath", "mimeType", "createdAt", "updatedAt"),
            attachments.map {
                listOf(it.id, it.homeworkId, it.kind.name, it.fileName, it.filePath, it.mimeType.orEmpty(), isoMillis(it.createdAt), isoMillis(it.updatedAt))
            },
        ),
    )

    private fun csv(header: List<String>, rows: List<List<Any?>>): ByteArray {
        val buffer = ByteArrayOutputStream()
        val writer = OutputStreamWriter(buffer, Charsets.UTF_8)
        CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(*header.toTypedArray()).get()).use { printer ->
            rows.forEach { row -> printer.printRecord(row) }
        }
        return buffer.toByteArray()
    }

    private fun isoDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    private fun isoMillis(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
