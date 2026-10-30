package com.example.helper.coredata.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What a configured export column means. The built-in kinds are the columns
 * the exam sheet has always had; [CUSTOM] is a teacher-defined formula.
 * [RESULT] is generated per exam (it needs the subject pass marks) and is
 * therefore not editable.
 */
enum class ExportColumnKind {
    TOTAL_OBTAINED,
    TOTAL_MAX,
    PERCENTAGE,
    RESULT,
    CUSTOM,
}

/**
 * One configurable column on the exported results sheet, in sheet order.
 *
 * [formula] is a symbolic template (`SUM({All Marks})`), not an A1 formula:
 * A1 references depend on the final column order, so the writer resolves the
 * tokens to cells after ordering. A value starting with `=` is treated as an
 * advanced raw paste and written verbatim.
 *
 * An empty `export_columns` table means "use the code defaults", which keeps
 * the feature working for databases created before this table existed.
 */
@Entity(tableName = "export_columns")
data class ExportColumn(
    @PrimaryKey val id: String,
    val label: String,
    val kind: ExportColumnKind,
    val formula: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)
