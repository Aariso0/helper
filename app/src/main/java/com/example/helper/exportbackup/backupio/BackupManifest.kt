package com.example.helper.exportbackup.backupio

import com.example.helper.coredata.AppDatabase
import org.json.JSONException
import org.json.JSONObject

/**
 * The backup zip's marker file (`export-backup` card: restore validates a
 * marker before touching anything). Serialized with the platform's
 * [JSONObject] — no serialization dependency, no per-entity serializer.
 *
 * [tableCounts] lets the restore preview show exactly what is inside, and
 * [fileCount] proves the photo/attachment payload arrived.
 */
data class BackupManifest(
    val appId: String,
    val schemaVersion: Int,
    val createdAtMillis: Long,
    val tableCounts: Map<String, Int>,
    val fileCount: Int,
) {
    fun toJson(): String = JSONObject().apply {
        put(KEY_MARKER, MARKER)
        put(KEY_FORMAT_VERSION, FORMAT_VERSION)
        put(KEY_APP_ID, appId)
        put(KEY_SCHEMA_VERSION, schemaVersion)
        put(KEY_CREATED_AT, createdAtMillis)
        put(KEY_FILE_COUNT, fileCount)
        put(KEY_TABLES, JSONObject(tableCounts))
    }.toString(2)

    companion object {
        const val MARKER = "helper-backup"
        const val FORMAT_VERSION = 1

        /** Where the manifest sits inside the zip. */
        const val ENTRY_NAME = "manifest.json"

        /** Where the checkpointed Room file sits inside the zip. */
        val DATABASE_ENTRY = "database/${AppDatabase.DATABASE_NAME}"

        private const val KEY_MARKER = "marker"
        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_APP_ID = "appId"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_CREATED_AT = "createdAtMillis"
        private const val KEY_FILE_COUNT = "fileCount"
        private const val KEY_TABLES = "tableCounts"

        /** Null when the text is not a Helper backup manifest at all. */
        fun fromJson(text: String): BackupManifest? = try {
            val json = JSONObject(text)
            if (json.optString(KEY_MARKER) != MARKER) {
                null
            } else if (json.optInt(KEY_FORMAT_VERSION) != FORMAT_VERSION) {
                null
            } else {
                val tables = json.optJSONObject(KEY_TABLES)
                val counts = buildMap {
                    tables?.keys()?.forEach { key -> put(key, tables.optInt(key)) }
                }
                BackupManifest(
                    appId = json.optString(KEY_APP_ID),
                    schemaVersion = json.optInt(KEY_SCHEMA_VERSION),
                    createdAtMillis = json.optLong(KEY_CREATED_AT),
                    tableCounts = counts,
                    fileCount = json.optInt(KEY_FILE_COUNT),
                )
            }
        } catch (_: JSONException) {
            null
        }
    }
}
