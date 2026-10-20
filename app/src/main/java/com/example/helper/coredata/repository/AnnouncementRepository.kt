package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.AnnouncementDao
import com.example.helper.coredata.entity.Announcement
import com.example.helper.coredata.entity.AnnouncementTemplate
import com.example.helper.coredata.entity.SchoolClass
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Announcements and AnnouncementTemplates (glossary terms).
 * Deleting a template never touches past announcements and vice versa
 * (parents card build notes) — they are unrelated rows by design.
 */
class AnnouncementRepository(private val dao: AnnouncementDao) {
    fun observeAnnouncements(): Flow<List<Announcement>> = dao.observeAnnouncements()

    fun observeTemplates(): Flow<List<AnnouncementTemplate>> = dao.observeTemplates()

    suspend fun getAnnouncementsInRange(fromEpochDay: Long, toEpochDay: Long): List<Announcement> =
        dao.getAnnouncementsInRange(fromEpochDay, toEpochDay)

    suspend fun createAnnouncement(
        schoolClass: SchoolClass?,
        title: String?,
        body: String,
        dateEpochDay: Long,
    ): Announcement {
        val now = System.currentTimeMillis()
        val announcement = Announcement(
            id = UUID.randomUUID().toString(),
            classId = schoolClass?.id,
            className = schoolClass?.name,
            title = title?.trim()?.ifEmpty { null },
            body = body,
            dateEpochDay = dateEpochDay,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertAnnouncement(announcement)
        return announcement
    }

    suspend fun upsertAnnouncement(announcement: Announcement) =
        dao.upsertAnnouncement(announcement.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteAnnouncement(announcement: Announcement) = dao.deleteAnnouncement(announcement)

    suspend fun createTemplate(title: String?, body: String): AnnouncementTemplate {
        val now = System.currentTimeMillis()
        val template = AnnouncementTemplate(
            id = UUID.randomUUID().toString(),
            title = title?.trim()?.ifEmpty { null },
            body = body,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertTemplate(template)
        return template
    }

    suspend fun upsertTemplate(template: AnnouncementTemplate) =
        dao.upsertTemplate(template.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteTemplate(template: AnnouncementTemplate) = dao.deleteTemplate(template)

    /** Call when a template is applied to a draft — powers recent-first chips. */
    suspend fun markTemplateUsed(templateId: String) {
        dao.touchTemplate(templateId, System.currentTimeMillis())
    }
}
