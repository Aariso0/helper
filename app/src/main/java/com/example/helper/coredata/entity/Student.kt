package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One child record (glossary: "Student") belonging to exactly one Class.
 * [photoPath] is an app-internal file path (roster's photo-storage convention),
 * never a content:// URI.
 */
@Entity(
    tableName = "students",
    foreignKeys = [ForeignKey(
        entity = SchoolClass::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("classId"), Index(value = ["classId", "rollNumber"])]
)
data class Student(
    @PrimaryKey val id: String,
    val classId: String,
    val name: String,
    val rollNumber: String? = null,
    val parentContact: String? = null,
    val photoPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
