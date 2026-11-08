package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named teaching group (glossary: "Class"), e.g. "5-A".
 * Named SchoolClass in code because `Class` shadows java.lang.Class.
 *
 * A class belongs to one [SchoolSession]; the same name may exist once per
 * session (10-B in 2025-26 and again in 2026-27). School name and class teacher
 * live here because the printed result sheet header is a property of the class.
 */
@Entity(
    tableName = "classes",
    foreignKeys = [ForeignKey(
        entity = SchoolSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "name"], unique = true),
    ]
)
data class SchoolClass(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val subject: String? = null,
    val schoolName: String? = null,
    val teacherName: String? = null,
    val teacherDesignation: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
