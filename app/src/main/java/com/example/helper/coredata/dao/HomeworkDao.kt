package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.helper.coredata.entity.Attachment
import com.example.helper.coredata.entity.Homework
import kotlinx.coroutines.flow.Flow

/** A Homework row with its Attachment rows — the unit every screen renders. */
data class HomeworkWithAttachments(
    @Embedded val homework: Homework,
    @Relation(parentColumn = "id", entityColumn = "homeworkId")
    val attachments: List<Attachment>,
)

@Dao
interface HomeworkDao {
    @Transaction
    @Query("SELECT * FROM homework WHERE (:classId IS NULL OR classId = :classId) ORDER BY createdAt DESC")
    fun observeAll(classId: String? = null): Flow<List<HomeworkWithAttachments>>

    @Transaction
    @Query("SELECT * FROM homework WHERE id = :id")
    fun observeById(id: String): Flow<HomeworkWithAttachments?>

    @Transaction
    @Query("SELECT * FROM homework WHERE id = :id")
    suspend fun getById(id: String): HomeworkWithAttachments?

    @Upsert
    suspend fun upsertHomework(homework: Homework)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun deleteHomework(id: String)

    @Query("SELECT * FROM attachments WHERE homeworkId = :homeworkId")
    fun observeAttachments(homeworkId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE homeworkId = :homeworkId")
    suspend fun getAttachments(homeworkId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachmentById(id: String): Attachment?

    @Upsert
    suspend fun upsertAttachment(attachment: Attachment)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)
}
