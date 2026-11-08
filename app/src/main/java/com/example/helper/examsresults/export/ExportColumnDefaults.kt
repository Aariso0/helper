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
    const val RANK_ID = "builtin-rank"

    /**
     * Marker row written when the teacher deletes the last column. A truly
     * empty table means "never configured" (old databases), so without this
     * row the defaults would come back after the last delete. It is hidden
     * from the UI and never exported.
     */
    const val CLEARED_LAYOUT_ID = "builtin-layout-cleared"

    const val TOTAL_OBTAINED_TEMPLATE = "SUM({All Marks})"

    /**
     * A share, not a percentage out of 100: the exported cell is formatted
     * `0.00%`, so `0.8` reads as `80.00%` in Excel and keeps working in band
     * formulas (`>=33%`) and class averages.
     */
    const val PERCENTAGE_TEMPLATE =
        "IF({Total Max}=0,0,{Total}/{Total Max})"

    /** v1.2.0's default; it produced `6125` for `61.25%` in a `0.00%` cell. */
    private const val LEGACY_PERCENTAGE_TEMPLATE =
        "IF({Total Max}=0,0,{Total Obtained}/{Total Max}*100)"
    /** v1.3.x template before the "Total" rename. */
    private const val LEGACY_TOTAL_OBTAINED_PERCENTAGE_TEMPLATE =
        "IF({Total Max}=0,0,{Total Obtained}/{Total Max})"
    const val RESULT_TEMPLATE =
        "IF(AND(COUNT({All Marks})=0,COUNTIF({All Marks},\"ABT\")=0),\"\"," +
            "IF(AND({All Pass Checks}),\"Pass\",\"Fail\"))"

    fun columns(): List<ExportColumn> = listOf(
        ExportColumn(
            id = TOTAL_OBTAINED_ID,
            label = "Total",
            kind = ExportColumnKind.TOTAL_OBTAINED,
            formula = TOTAL_OBTAINED_TEMPLATE,
            sortOrder = 0,
        ),
        ExportColumn(
            id = PERCENTAGE_ID,
            label = "Percentage",
            kind = ExportColumnKind.PERCENTAGE,
            formula = PERCENTAGE_TEMPLATE,
            sortOrder = 1,
        ),
        ExportColumn(
            id = RESULT_ID,
            label = "Result",
            kind = ExportColumnKind.RESULT,
            formula = "",
            sortOrder = 2,
        ),
        ExportColumn(
            id = RANK_ID,
            label = "Rank",
            kind = ExportColumnKind.RANK,
            formula = "",
            sortOrder = 3,
        ),
    )

    /** Stored layout if any, otherwise the built-in defaults. */
    fun effective(stored: List<ExportColumn>): List<ExportColumn> =
        if (stored.isEmpty()) {
            columns()
        } else {
            stored.filterNot { it.id == CLEARED_LAYOUT_ID || it.kind == ExportColumnKind.TOTAL_MAX }
                .map { column ->
                    // A layout saved on 1.2.0 keeps the old whole-number
                    // percentage while the cell is formatted `0.00%`. Layouts
                    // from before the "Total" rename keep `{Total Obtained}`.
                    if (
                        column.kind == ExportColumnKind.PERCENTAGE &&
                        (column.formula == LEGACY_PERCENTAGE_TEMPLATE ||
                            column.formula == LEGACY_TOTAL_OBTAINED_PERCENTAGE_TEMPLATE)
                    ) {
                        column.copy(formula = PERCENTAGE_TEMPLATE)
                    } else {
                        column
                    }
                }
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
        ExportColumnKind.RANK -> ""
        ExportColumnKind.TOTAL_MAX -> ""
        ExportColumnKind.CUSTOM -> column.formula
    }

    /** Field name that keeps resolving after a built-in column is renamed. */
    fun canonicalField(kind: ExportColumnKind): String? = when (kind) {
        ExportColumnKind.TOTAL_OBTAINED -> "Total"
        ExportColumnKind.TOTAL_MAX -> "Total Max"
        ExportColumnKind.PERCENTAGE -> "Percentage"
        ExportColumnKind.RESULT -> "Result"
        ExportColumnKind.RANK -> "Rank"
        ExportColumnKind.CUSTOM -> null
    }
}
