package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named teaching group (glossary: "Class"), e.g. "5-A".
 * Named SchoolClass in code because `Class` shadows java.lang.Class.
 */
@Entity(
    tableName = "classes",
    indices = [Index(value = ["name"], unique = true)]
)
data class SchoolClass(
    @PrimaryKey val id: String,
    val name: String,
    val subject: String? = null,
    val year: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
