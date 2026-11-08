package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.ExportColumn
import com.example.helper.coredata.entity.ExportColumnKind

/** One thing the teacher must fix before the sheet can be exported. */
data class ExportColumnIssue(
    val columnId: String,
    val columnLabel: String,
    val message: String,
)

/**
 * Resolves symbolic column formulas (`SUM({All Marks})`) into Excel formulas
 * for a concrete row, and checks a layout before export.
 *
 * Templates exist because A1 references move when columns are reordered:
 * `{Math}` becomes `C2` only once the final order is known. A formula that
 * starts with `=` is the advanced escape hatch and is written exactly as
 * pasted — its A1 references are the teacher's responsibility.
 */
internal object ExportFormulaEngine {
    /**
     * The first data row of the results sheet: four banner rows plus two
     * header rows sit above it. The editor previews formulas on this row.
     */
    const val PREVIEW_ROW = 7
    const val MAX_FORMULA_LENGTH = 500

    private val TOKEN = Regex("""\{([^{}]*)\}""")
    /** A1-style cell or range reference, used only for the soft notice. */
    private val CELL_REFERENCE = Regex("""(?<![A-Za-z0-9_])[$]?[A-Za-z]{1,3}[$]?[0-9]+""")
    private const val ALL_MARKS = "All Marks"
    private const val ALL_PASS_CHECKS = "All Pass Checks"
    private const val TOTAL_MAX = "Total Max"
    private const val LEGACY_TOTAL = "Total Obtained"

    private data class Layout(
        val subjects: List<ExamSubject>,
        val firstMarkCol: Int,
        val lastMarkCol: Int,
        val subjectIndexByName: Map<String, Int>,
        val columnIndexByLabel: Map<String, Int>,
        val columnIndexByCanonical: Map<String, Int>,
    )

    /**
     * [dataRows] is the absolute row range of the student rows on the results
     * sheet. Rank needs it to build `RANK(cell, $F$7:$F$46)`; the editor
     * previews on a single row when the real range is not known yet.
     */
    fun resolve(
        column: ExportColumn,
        columns: List<ExportColumn>,
        subjects: List<ExamSubject>,
        rowNum: Int = PREVIEW_ROW,
        dataRows: IntRange? = null,
    ): Result<String> {
        if (column.kind == ExportColumnKind.RANK) {
            return resolveRank(columns, subjects, rowNum, dataRows)
        }
        val template = ExportColumnDefaults.templateFor(column).trim()
        if (template.isEmpty()) return Result.failure(FormulaError("Formula is empty."))
        if (template.startsWith("=")) return Result.success(template)

        val layout = Layout(
            subjects = subjects,
            firstMarkCol = FIRST_MARK_COL,
            lastMarkCol = FIRST_MARK_COL + subjects.size - 1,
            subjectIndexByName = subjects.mapIndexed { index, subject ->
                subject.name.trim() to FIRST_MARK_COL + index
            }.toMap(),
            columnIndexByLabel = columns.mapIndexed { index, item -> item.label.trim() to computedColumnStart(subjects) + index }.toMap(),
            columnIndexByCanonical = columns.mapIndexedNotNull { index, item ->
                ExportColumnDefaults.canonicalField(item.kind)?.let { field ->
                    field to computedColumnStart(subjects) + index
                }
            }.toMap(),
        )

        var missingField: String? = null
        val resolved = TOKEN.replace(template) { match ->
            val token = match.groupValues[1].trim()
            val replacement = when {
                token.equals(ALL_MARKS, ignoreCase = true) -> {
                    if (subjects.isEmpty()) null
                    else "${columnLetter(layout.firstMarkCol)}$rowNum:${columnLetter(layout.lastMarkCol)}$rowNum"
                }
                token.equals(ALL_PASS_CHECKS, ignoreCase = true) -> {
                    if (subjects.isEmpty()) null else passChecks(subjects, layout.firstMarkCol, rowNum)
                }
                else -> {
                    val columnIndex = layout.subjectIndexByName[token]
                        ?: layout.columnIndexByLabel[token]
                        ?: layout.columnIndexByCanonical[token]
                    when {
                        columnIndex != null -> "${columnLetter(columnIndex)}$rowNum"
                        // Total Max is no longer a default column. A formula
                        // that still asks for it gets the exam's literal sum of
                        // subject maxima, so Percentage keeps working.
                        token.equals(TOTAL_MAX, ignoreCase = true) && subjects.isNotEmpty() ->
                            subjects.sumOf { it.maxMarks }.toString()
                        token.equals(LEGACY_TOTAL, ignoreCase = true) -> {
                            val totalIndex = columns.indexOfFirst { it.kind == ExportColumnKind.TOTAL_OBTAINED }
                            if (totalIndex >= 0) "${columnLetter(computedColumnStart(subjects) + totalIndex)}$rowNum" else null
                        }
                        else -> null
                    }
                }
            }
            if (replacement == null) {
                if (missingField == null) missingField = token
                match.value
            } else {
                replacement
            }
        }

        missingField?.let { return Result.failure(FormulaError("Unknown field {$it}.")) }
        return Result.success("=$resolved")
    }

    /**
     * Rank is derived, not edited: it needs the Total Obtained cell and the
     * absolute range of every student's total, which only the writer knows.
     * Excel's `RANK` shares a rank on ties and skips the next (1, 2, 2, 4).
     */
    private fun resolveRank(
        columns: List<ExportColumn>,
        subjects: List<ExamSubject>,
        rowNum: Int,
        dataRows: IntRange?,
    ): Result<String> {
        val totalIndex = columns.indexOfFirst { it.kind == ExportColumnKind.TOTAL_OBTAINED }
        if (totalIndex < 0) {
            return Result.failure(FormulaError("Rank needs a Total column."))
        }
        val column = columnLetter(computedColumnStart(subjects) + totalIndex)
        val firstRow = dataRows?.first ?: rowNum
        val lastRow = dataRows?.last ?: rowNum
        val cell = "$column$rowNum"
        return Result.success("=IF($cell=\"\",\"\",RANK($cell,\$$column\$$firstRow:\$$column\$$lastRow))")
    }

    /**
     * The one blocking error for a single column, or `null` when the formula
     * can be written. Used by the editor (live, while typing) and by [validate].
     */
    fun blockingError(
        column: ExportColumn,
        columns: List<ExportColumn>,
        subjects: List<ExamSubject>,
    ): String? {
        if (column.kind == ExportColumnKind.RANK) {
            return if (columns.none { it.kind == ExportColumnKind.TOTAL_OBTAINED }) {
                "Rank needs a Total column."
            } else {
                null
            }
        }
        if (column.kind == ExportColumnKind.TOTAL_MAX || column.kind == ExportColumnKind.RESULT) {
            return null
        }
        val formula = ExportColumnDefaults.templateFor(column).trim()
        if (formula.isEmpty()) return "Formula is empty."
        val resolved = resolve(column, columns, subjects).getOrElse { error ->
            return error.message ?: "Formula is invalid."
        }
        val pasted = formula.startsWith("=")
        return FormulaSyntaxValidator.error(resolved.removePrefix("="), allowAnyFunction = pasted)
    }

    /**
     * A friendly heads-up that does not block the export. The common beginner
     * mistake is a formula with no field in it, which writes the same answer
     * into every student's row.
     */
    fun notice(
        column: ExportColumn,
        columns: List<ExportColumn>,
        subjects: List<ExamSubject>,
    ): String? {
        if (column.kind == ExportColumnKind.TOTAL_MAX ||
            column.kind == ExportColumnKind.RESULT ||
            column.kind == ExportColumnKind.RANK
        ) {
            return null
        }
        val formula = ExportColumnDefaults.templateFor(column).trim()
        if (formula.isEmpty() || blockingError(column, columns, subjects) != null) return null
        if (!formula.startsWith("=")) {
            if (!TOKEN.containsMatchIn(formula)) {
                return "No field in this formula, so every student gets the same value."
            }
            return null
        }
        val body = formula.removePrefix("=").trim()
        if (CELL_REFERENCE.containsMatchIn(body) || body.any { it in "({[" }) {
            return null
        }
        if (body.toDoubleOrNull() != null) {
            return "This formula is just a number; every student gets the same value."
        }
        return "This formula doesn’t point at any cell, so every student gets the same value."
    }

    /** All issues that should block the export, in column order. */
    fun validate(
        columns: List<ExportColumn>,
        subjects: List<ExamSubject>,
    ): List<ExportColumnIssue> {
        val issues = mutableListOf<ExportColumnIssue>()

        val labelOwners = mutableMapOf<String, ExportColumn>()
        columns.forEach { column ->
            val label = column.label.trim()
            if (label.isEmpty()) {
                issues += ExportColumnIssue(column.id, "(unnamed)", "Column name is empty.")
            } else {
                val key = label.lowercase()
                val owner = labelOwners.putIfAbsent(key, column)
                if (owner != null && owner.id != column.id) {
                    issues += ExportColumnIssue(column.id, label, "Another column already uses this name.")
                }
            }
        }

        if (columns.any { it.kind == ExportColumnKind.RANK } &&
            columns.none { it.kind == ExportColumnKind.TOTAL_OBTAINED }
        ) {
            columns.filter { it.kind == ExportColumnKind.RANK }.forEach { column ->
                issues += ExportColumnIssue(
                    column.id,
                    displayLabel(column),
                    "Rank needs a Total column.",
                )
            }
        }

        columns.forEach { column ->
            // Total Max is a fixed number, Result is generated from the
            // subject pass marks, and Rank reads Total Obtained, so none of
            // them stores an editable formula.
            if (column.kind == ExportColumnKind.TOTAL_MAX ||
                column.kind == ExportColumnKind.RESULT ||
                column.kind == ExportColumnKind.RANK
            ) {
                return@forEach
            }
            val formula = ExportColumnDefaults.templateFor(column).trim()
            if (formula.isEmpty()) {
                issues += ExportColumnIssue(column.id, displayLabel(column), "Formula is empty.")
                return@forEach
            }
            if (formula.length > MAX_FORMULA_LENGTH) {
                issues += ExportColumnIssue(
                    column.id,
                    displayLabel(column),
                    "Formula is longer than $MAX_FORMULA_LENGTH characters.",
                )
                return@forEach
            }
            if (formula.any { it.isISOControl() }) {
                issues += ExportColumnIssue(
                    column.id,
                    displayLabel(column),
                    "Formula contains a control character.",
                )
                return@forEach
            }
            if (!hasBalancedParentheses(formula)) {
                issues += ExportColumnIssue(
                    column.id,
                    displayLabel(column),
                    "Parentheses are not balanced.",
                )
                return@forEach
            }
            if (formula.startsWith("=")) {
                blockingError(column, columns, subjects)?.let { message ->
                    issues += ExportColumnIssue(column.id, displayLabel(column), message)
                }
                return@forEach
            }

            val fields = TOKEN.findAll(formula).map { it.groupValues[1].trim() }.toList()
            val selfReferences = fields.any { field ->
                field.equals(column.label.trim(), ignoreCase = true) ||
                    ExportColumnDefaults.canonicalField(column.kind)
                        ?.equals(field, ignoreCase = true) == true
            }
            if (selfReferences) {
                issues += ExportColumnIssue(
                    column.id,
                    displayLabel(column),
                    "A column can’t use its own value.",
                )
                return@forEach
            }

            blockingError(column, columns, subjects)?.let { message ->
                issues += ExportColumnIssue(column.id, displayLabel(column), message)
            }
        }

        if (issues.isEmpty()) issues += cycleIssues(columns)
        return issues
    }

    private fun cycleIssues(columns: List<ExportColumn>): List<ExportColumnIssue> {
        val byLabel = columns.associateBy { it.label.trim().lowercase() }
        val byCanonical = columns.mapNotNull { column ->
            ExportColumnDefaults.canonicalField(column.kind)?.lowercase()?.let { it to column }
        }.toMap()
        fun target(field: String): ExportColumn? =
            byLabel[field.lowercase()] ?: byCanonical[field.lowercase()]

        val adjacency: Map<String, List<String>> = columns.associate { column ->
            val template = ExportColumnDefaults.templateFor(column)
            val targets: List<String> = if (template.startsWith("=")) {
                emptyList()
            } else {
                val result = mutableListOf<String>()
                TOKEN.findAll(template).forEach { match ->
                    target(match.groupValues[1].trim())?.let { result += it.id }
                }
                result
            }
            column.id to targets
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val cyclic = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            if (!visiting.add(id)) {
                cyclic += id
                return
            }
            adjacency[id].orEmpty().forEach { next ->
                if (next in visiting) cyclic += next
                visit(next)
            }
            visiting.remove(id)
            visited += id
        }
        columns.forEach { visit(it.id) }

        // Every column that points into a cycle is blocked too: its formula
        // would still be circular once Excel opens the sheet.
        var changed = true
        while (changed) {
            changed = false
            columns.forEach { column ->
                if (column.id !in cyclic && adjacency[column.id].orEmpty().any { it in cyclic }) {
                    cyclic += column.id
                    changed = true
                }
            }
        }

        return columns.filter { it.id in cyclic }.map { column ->
            ExportColumnIssue(
                column.id,
                displayLabel(column),
                "Formula loops back through another column.",
            )
        }
    }

    fun hasBalancedParentheses(formula: String): Boolean {
        var depth = 0
        var inQuotes = false
        var index = 0
        while (index < formula.length) {
            val char = formula[index]
            if (char == '"') {
                if (inQuotes && index + 1 < formula.length && formula[index + 1] == '"') {
                    index += 2
                    continue
                }
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                when (char) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth < 0) return false
                    }
                }
            }
            index++
        }
        return depth == 0 && !inQuotes
    }

    fun columnLetter(column: Int): String {
        var result = ""
        var num = column
        while (num > 0) {
            val mod = (num - 1) % 26
            result = ('A' + mod) + result
            num = (num - 1) / 26
        }
        return result
    }

    private fun passChecks(
        subjects: List<ExamSubject>,
        firstMarkCol: Int,
        rowNum: Int,
    ): String = subjects.mapIndexed { index, subject ->
        val cell = "${columnLetter(firstMarkCol + index)}$rowNum"
        // A blank cell is "not entered" and ignored. `N()` turns the "ABT"
        // text of an absent subject into 0, which fails the paper mark; a
        // plain text comparison would treat "ABT" as greater than a number.
        "OR(ISBLANK($cell),N($cell)>=${subject.passMarks})"
    }.joinToString(",")

    /**
     * A1 column of the first subject mark: A is S.No., B Roll No., C Student
     * Name, so the marks start at D.
     */
    private const val FIRST_MARK_COL = 4

    private fun computedColumnStart(subjects: List<ExamSubject>): Int =
        FIRST_MARK_COL + subjects.size

    private fun displayLabel(column: ExportColumn): String =
        column.label.trim().ifEmpty { "(unnamed)" }
}

private class FormulaError(message: String) : Exception(message)
