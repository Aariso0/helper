package com.example.helper.coredata

import androidx.room.TypeConverter
import com.example.helper.coredata.entity.AttachmentKind
import com.example.helper.coredata.entity.AttendanceStatus
import com.example.helper.coredata.entity.ExportColumnKind

/** Enum <-> String converters so Room stores readable status/kind names. */
class Converters {
    @TypeConverter
    fun attendanceStatusToString(value: AttendanceStatus): String = value.name

    @TypeConverter
    fun stringToAttendanceStatus(value: String): AttendanceStatus =
        AttendanceStatus.valueOf(value)

    @TypeConverter
    fun attachmentKindToString(value: AttachmentKind): String = value.name

    @TypeConverter
    fun stringToAttachmentKind(value: String): AttachmentKind =
        AttachmentKind.valueOf(value)

    @TypeConverter
    fun exportColumnKindToString(value: ExportColumnKind): String = value.name

    @TypeConverter
    fun stringToExportColumnKind(value: String): ExportColumnKind =
        ExportColumnKind.valueOf(value)
}
