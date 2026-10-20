package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.HomeworkDao
import com.example.helper.coredata.dao.HomeworkWithAttachments
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.AttachmentKind
import com.example.helper.coredata.entity.Homework
import com.example.helper.coredata.entity.SchoolClass
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Homework and Attachment rows (glossary terms).
 * Attachment bytes live on disk (homework's attachment-storage); this layer
 * only records rows — deleting an Attachment row returns the caller the row
 * so the module can delete the file itself.
 */
class HomeworkRepository(private val dao: HomeworkDao) {
    fun observeAll(classId: String? = null): Flow<List<HomeworkWithAttachments>> =
        dao.observeAll(classId)

    fun observeById(id: String): Flow<HomeworkWithAttachments?> = dao.observeById(id)

    fun observeAttachments(homeworkId: String): Flow<List<Attachment>> =
        dao.observeAttachments(homeworkId)

    suspend fun getById(id: String): HomeworkWithAttachments? = dao.getById(id)

    suspend fun getAttachments(homeworkId: String): List<Attachment> = dao.getAttachments(homeworkId)

    suspend fun create(
        schoolClass: SchoolClass,
        title: String,
        text: String? = null,
        dueDateEpochDay: Long? = null,
    ): Homework {
        val now = System.currentTimeMillis()
        val homework = Homework(
            id = UUID.randomUUID().toString(),
            classId = schoolClass.id,
            title = title.trim(),
            text = text?.trim()?.ifEmpty { null },
            dueDateEpochDay = dueDateEpochDay,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertHomework(homework)
        return homework
    }

    suspend fun upsertHomework(homework: Homework) =
        dao.upsertHomework(homework.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteHomework(homeworkId: String) {
        dao.deleteHomework(homeworkId) // cascades Attachment rows; caller deletes files
    }

    suspend fun addAttachment(
        homeworkId: String,
        kind: AttachmentKind,
        fileName: String,
        filePath: String,
        mimeType: String? = null,
    ): Attachment {
        val now = System.currentTimeMillis()
        val attachment = Attachment(
            id = UUID.randomUUID().toString(),
            homeworkId = homeworkId,
            kind = kind,
            fileName = fileName,
            filePath = filePath,
            mimeType = mimeType,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertAttachment(attachment)
        return attachment
    }

    suspend fun upsertAttachment(attachment: Attachment) =
        dao.upsertAttachment(attachment.copy(updatedAt = System.currentTimeMillis()))

    /** Removes the row; returns it so the caller can delete the stored file. */
    suspend fun deleteAttachment(attachmentId: String): Attachment? {
        val attachment = dao.getAttachmentById(attachmentId)
        dao.deleteAttachment(attachmentId)
        return attachment
    }
}
