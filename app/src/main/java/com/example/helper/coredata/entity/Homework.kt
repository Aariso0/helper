package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One assignment for a class (glossary: "Homework"). A class's homework goes
 * with the class (CASCADE) — unlike Announcements, untargeted homework has no
 * meaning. [dueDateEpochDay] is optional; the prototype assigns and shares, it
 * does not track per-student status (homework card open questions).
 */
@Entity(
    tableName = "homework",
    foreignKeys = [ForeignKey(
        entity = SchoolClass::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("classId")]
)
data class Homework(
    @PrimaryKey val id: String,
    val classId: String,
    val title: String,
    val text: String? = null,
    val dueDateEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
