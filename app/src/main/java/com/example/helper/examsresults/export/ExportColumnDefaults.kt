package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind

/**
 * The formula columns every results sheet starts with. They are ordinary
 * rows in `export_columns` once the teacher edits the layout; an empty table
 * still means "never configured", so migrating an old database needs no
 * seeding. Deleting the last column stores [CLEARED_LAYOUT_ID] instead, so an
 * intentionally empty layout does not fall back to these defaults.
 */
object ExportColumnDefaults {
    const val TOTAL_OBTAINED_ID = "builtin-total-obtained"
    const val TOTAL_MAX_ID = "builtin-total-max"
    const val PERCENTAGE_ID = "builtin-percentage"
    const val RESULT_ID = "builtin-result"

    /**
     * Marker row written when the teacher deletes the last column. A truly
     * empty table means "never configured" (old databases), so without this
     * row the defaults would come back after the last delete. It is hidden
     * from the UI and never exported.
     */
    const val CLEARED_LAYOUT_ID = "builtin-layout-cleared"

    const val TOTAL_OBTAINED_TEMPLATE = "SUM({All Marks})"
    const val PERCENTAGE_TEMPLATE =
        "IF({Total Max}=0,0,{Total Obtained}/{Total Max}*100)"
    const val RESULT_TEMPLATE =
        "IF(COUNT({All Marks})=0,\"\",IF(AND({All Pass Checks}),\"Pass\",\"Fail\"))"

    fun columns(): List<ExportColumn> = listOf(
        ExportColumn(
            id = TOTAL_OBTAINED_ID,
            label = "Total Obtained",
            kind = ExportColumnKind.TOTAL_OBTAINED,
            formula = TOTAL_OBTAINED_TEMPLATE,
            sortOrder = 0,
        ),
        ExportColumn(
            id = TOTAL_MAX_ID,
            label = "Total Max",
            kind = ExportColumnKind.TOTAL_MAX,
            formula = "",
            sortOrder = 1,
        ),
        ExportColumn(
            id = PERCENTAGE_ID,
            label = "Percentage",
            kind = ExportColumnKind.PERCENTAGE,
            formula = PERCENTAGE_TEMPLATE,
            sortOrder = 2,
        ),
        ExportColumn(
            id = RESULT_ID,
            label = "Result",
            kind = ExportColumnKind.RESULT,
            formula = "",
            sortOrder = 3,
        ),
    )

    /** Stored layout if any, otherwise the built-in defaults. */
    fun effective(stored: List<ExportColumn>): List<ExportColumn> =
        if (stored.isEmpty()) {
            columns()
        } else {
            stored.filterNot { it.id == CLEARED_LAYOUT_ID }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }

    /** Rows to persist: an emptied layout keeps its marker so it stays empty. */
    fun forStorage(columns: List<ExportColumn>): List<ExportColumn> =
        columns.ifEmpty {
            listOf(
                ExportColumn(
                    id = CLEARED_LAYOUT_ID,
                    label = "",
                    kind = ExportColumnKind.CUSTOM,
                    formula = "",
                    sortOrder = 0,
                    enabled = false,
                ),
            )
        }

    /** The formula actually written for a built-in kind. */
    fun templateFor(column: ExportColumn): String = when (column.kind) {
        ExportColumnKind.TOTAL_OBTAINED -> column.formula.ifBlank { TOTAL_OBTAINED_TEMPLATE }
        ExportColumnKind.PERCENTAGE -> column.formula.ifBlank { PERCENTAGE_TEMPLATE }
        ExportColumnKind.RESULT -> RESULT_TEMPLATE
        ExportColumnKind.TOTAL_MAX -> ""
        ExportColumnKind.CUSTOM -> column.formula
    }

    /** Field name that keeps resolving after a built-in column is renamed. */
    fun canonicalField(kind: ExportColumnKind): String? = when (kind) {
        ExportColumnKind.TOTAL_OBTAINED -> "Total Obtained"
        ExportColumnKind.TOTAL_MAX -> "Total Max"
        ExportColumnKind.PERCENTAGE -> "Percentage"
        ExportColumnKind.RESULT -> "Result"
        ExportColumnKind.CUSTOM -> null
    }
}
