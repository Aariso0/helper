package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A teacher-authored reusable announcement body (glossary:
 * "AnnouncementTemplate"; requested by the parents card, README 3.5).
 * [lastUsedAt] (epoch millis) powers the recent-first chip ordering; null
 * until first use.
 */
@Entity(tableName = "announcement_templates")
data class AnnouncementTemplate(
    @PrimaryKey val id: String,
    val title: String? = null,
    val body: String,
    val lastUsedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
