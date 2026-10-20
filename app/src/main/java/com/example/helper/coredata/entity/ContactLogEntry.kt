package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One teacher-logged instance of parent communication (glossary: "Contact log
 * entry") — the teacher received the call/message, the app just records it.
 * [studentName] is denormalized at write time so renames don't rewrite history.
 */
@Entity(
    tableName = "contact_log_entries",
    foreignKeys = [ForeignKey(
        entity = Student::class,
        parentColumns = ["id"],
        childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("studentId")]
)
data class ContactLogEntry(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val text: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
