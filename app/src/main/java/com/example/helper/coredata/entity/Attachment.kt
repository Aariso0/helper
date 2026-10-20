package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What kind of file an Attachment is (glossary: "Attachment": image, PDF, or
 * PPT). OTHER tolerates whatever SAF returns (homework card build notes).
 */
enum class AttachmentKind { IMAGE, PDF, PPT, OTHER }

/**
 * One file hung on a Homework, stored on device (glossary: "Attachment").
 * [filePath] is an app-internal path (homework's attachment-storage
 * convention); [fileName] and [mimeType] ride along for the share sheet.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = Homework::class,
        parentColumns = ["id"],
        childColumns = ["homeworkId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("homeworkId")]
)
data class Attachment(
    @PrimaryKey val id: String,
    val homeworkId: String,
    val kind: AttachmentKind,
    val fileName: String,
    val filePath: String,
    val mimeType: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
