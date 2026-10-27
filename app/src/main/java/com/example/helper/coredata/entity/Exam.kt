package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = SchoolClass::class,
            parentColumns = ["id"],
            childColumns = ["classId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("classId")],
)
data class Exam(
    @PrimaryKey val id: String,
    val classId: String,
    val name: String,
    val examDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
