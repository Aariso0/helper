package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeAnnouncements(): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay DESC")
    suspend fun getAnnouncementsInRange(fromEpochDay: Long, toEpochDay: Long): List<Announcement>

    @Upsert
    suspend fun upsertAnnouncement(announcement: Announcement)

    @Delete
    suspend fun deleteAnnouncement(announcement: Announcement)

    @Query("SELECT * FROM announcement_templates ORDER BY (lastUsedAt IS NULL), lastUsedAt DESC, createdAt DESC")
    fun observeTemplates(): Flow<List<AnnouncementTemplate>>

    @Upsert
    suspend fun upsertTemplate(template: AnnouncementTemplate)

    @Delete
    suspend fun deleteTemplate(template: AnnouncementTemplate)

    @Query("UPDATE announcement_templates SET lastUsedAt = :usedAtMillis WHERE id = :id")
    suspend fun touchTemplate(id: String, usedAtMillis: Long)
}
