package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One teacher-composed message intended for a parent group (glossary:
 * "Announcement"), shared manually via the share sheet.
 * The Class link is SET NULL rather than CASCADE because an announcement is a
 * message that was already shared — its history outlives the class list.
 * [className] is denormalized at write time so the list stays readable after
 * the class is gone.
 */
@Entity(
    tableName = "announcements",
    foreignKeys = [ForeignKey(
        entity = SchoolClass::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("classId")]
)
data class Announcement(
    @PrimaryKey val id: String,
    val classId: String? = null,
    val className: String? = null,
    val title: String? = null,
    val body: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
