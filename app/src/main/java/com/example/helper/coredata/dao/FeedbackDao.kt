package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.FeedbackNote
import com.example.helper.coredata.entity.FeedbackTag
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback_tags ORDER BY sortOrder, label COLLATE NOCASE")
    fun observeTags(): Flow<List<FeedbackTag>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM feedback_tags")
    suspend fun maxSortOrder(): Int

    /** Case-insensitive lookup so "Great day" never becomes two chips. */
    @Query("SELECT * FROM feedback_tags WHERE label = :label COLLATE NOCASE LIMIT 1")
    suspend fun findTagByLabel(label: String): FeedbackTag?

    @Query("UPDATE feedback_tags SET label = :label, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun updateLabel(id: String, label: String, updatedAtMillis: Long)

    @Query("UPDATE feedback_tags SET sortOrder = :sortOrder, updatedAt = :updatedAtMillis WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAtMillis: Long)

    @Insert
    suspend fun insertTag(tag: FeedbackTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagsIfAbsent(tags: List<FeedbackTag>)

    @Query("DELETE FROM feedback_tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query(
        "SELECT * FROM feedback_notes " +
            "WHERE (:studentId IS NULL OR studentId = :studentId) " +
            "AND (:tagId IS NULL OR tagId = :tagId) " +
            "ORDER BY dateEpochDay DESC, createdAt DESC"
    )
    fun observeNotes(studentId: String? = null, tagId: String? = null): Flow<List<FeedbackNote>>

    @Query("SELECT * FROM feedback_notes WHERE id = :id")
    fun observeNote(id: String): Flow<FeedbackNote?>

    @Query("SELECT * FROM feedback_notes WHERE studentId = :studentId AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay DESC")
    suspend fun getNotesInRange(studentId: String, fromEpochDay: Long, toEpochDay: Long): List<FeedbackNote>

    @Insert
    suspend fun insertNote(note: FeedbackNote)

    @Upsert
    suspend fun upsertNote(note: FeedbackNote)

    @Query("DELETE FROM feedback_notes WHERE id = :id")
    suspend fun deleteNote(id: String)
}
