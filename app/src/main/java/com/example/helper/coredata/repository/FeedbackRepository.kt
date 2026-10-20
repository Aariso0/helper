package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.FeedbackDao
import com.example.helper.coredata.entity.FeedbackNote
import com.example.helper.coredata.entity.FeedbackTag
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for FeedbackNotes and FeedbackTags (glossary: "Feedback note").
 * The commit-on-tap path: [addNote] writes immediately; the Undo snackbar
 * calls [deleteNote] with the returned id.
 */
class FeedbackRepository(private val dao: FeedbackDao) {
    fun observeTags(): Flow<List<FeedbackTag>> = dao.observeTags()

    fun observeNotes(studentId: String? = null, tagId: String? = null): Flow<List<FeedbackNote>> =
        dao.observeNotes(studentId, tagId)

    fun observeNote(id: String): Flow<FeedbackNote?> = dao.observeNote(id)

    suspend fun getNotesInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<FeedbackNote> =
        dao.getNotesInRange(studentId, fromEpochDay, toEpochDay)

    suspend fun addTag(label: String): FeedbackTag {
        val now = System.currentTimeMillis()
        val tag = FeedbackTag(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            sortOrder = dao.maxSortOrder() + 1,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertTag(tag)
        return tag
    }

    /**
     * Idempotent seeding: inserts only rows whose id is unknown, so teacher
     * renames/deletions of seeded tags survive re-seeding.
     */
    suspend fun ensureTags(tags: List<FeedbackTag>) = dao.insertTagsIfAbsent(tags)

    suspend fun renameTag(tagId: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        dao.updateLabel(tagId, trimmed, System.currentTimeMillis())
    }

    suspend fun reorderTag(tagId: String, sortOrder: Int) {
        dao.updateSortOrder(tagId, sortOrder, System.currentTimeMillis())
    }

    suspend fun deleteTag(tagId: String) = dao.deleteTag(tagId)

    /**
     * Write a note immediately (commit-on-tap). [tag] may be null when the
     * teacher typed a free-form label; [tagLabel] is denormalized onto the
     * row so history survives tag deletion.
     */
    suspend fun addNote(
        studentId: String,
        tag: FeedbackTag?,
        tagLabel: String,
        text: String? = null,
        dateEpochDay: Long,
    ): FeedbackNote {
        val now = System.currentTimeMillis()
        val note = FeedbackNote(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            tagId = tag?.id,
            tagLabel = tag?.label ?: tagLabel.trim(),
            text = text?.trim()?.ifEmpty { null },
            dateEpochDay = dateEpochDay,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertNote(note)
        return note
    }

    suspend fun upsertNote(note: FeedbackNote) =
        dao.upsertNote(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteNote(id: String) = dao.deleteNote(id)
}
