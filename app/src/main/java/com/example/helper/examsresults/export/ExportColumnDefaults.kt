package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind

/**
 * The formula columns every results sheet starts with. They are ordinary
 * rows in `export_columns` once the teacher edits the layout; an empty table
 * means "use these", which is why migrating an old database needs no seeding.
 */
object ExportColumnDefaults {
    const val TOTAL_OBTAINED_ID = "builtin-total-obtained"
    const val TOTAL_MAX_ID = "builtin-total-max"
    const val PERCENTAGE_ID = "builtin-percentage"
    const val RESULT_ID = "builtin-result"

    const val TOTAL_OBTAINED_TEMPLATE = "SUM({All Marks})"
    const val PERCENTAGE_TEMPLATE =
        "IF({Total Max}=0,0,{Total Obtained}/{Total Max}*100)"
    const val RESULT_TEMPLATE =
        "IF(COUNT({All Marks})=0,\"\",IF(AND({All Pass Checks}),\"PASS\",\"FAIL\"))"

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
        (stored.ifEmpty { columns() }).sortedWith(compareBy({ it.sortOrder }, { it.id }))

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

    /** Built-in columns stay on the sheet; only custom columns can be deleted. */
    fun isBuiltIn(column: ExportColumn): Boolean =
        column.id in setOf(TOTAL_OBTAINED_ID, TOTAL_MAX_ID, PERCENTAGE_ID, RESULT_ID)
}
