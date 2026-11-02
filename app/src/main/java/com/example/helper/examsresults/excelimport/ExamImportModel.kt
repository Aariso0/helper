package com.example.helper.examsresults.excelimport

import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.roster.ui.import.ParsedTable

/**
 * One student an imported sheet row can be matched against. A plain record
 * (not the Room entity) so the matching rules stay unit-testable.
 */
data class ImportCandidate(
    val id: String,
    val name: String,
    val rollNumber: String?,
)

/**
 * A subject column as the teacher sees it on the preview screen. Max and pass
 * marks are strings because they sit in text fields until commit.
 */
data class ExamImportSubjectDraft(
    val column: Int,
    val name: String,
    val maxMarks: String = "100",
    val passMarks: String = "35",
    val included: Boolean = true,
) {
    val maxValue: Int? get() = maxMarks.trim().toIntOrNull()?.takeIf { it > 0 }
    val passValue: Int? get() = passMarks.trim().toIntOrNull()

    val isValid: Boolean
        get() {
            val max = maxValue ?: return false
            val pass = passValue ?: return false
            return pass in 0..max
        }
}

/** Which column identifies the student and which columns are subjects. */
data class ExamImportPlan(
    val nameColumn: Int,
    val rollColumn: Int,
    val subjects: List<ExamImportSubjectDraft>,
) {
    /** True when the sheet at least says who each row is. */
    val canIdentify: Boolean get() = nameColumn >= 0 || rollColumn >= 0
}

/** One row that resolved to a real student, with the marks read for it. */
data class ExamImportRow(
    val studentId: String,
    val name: String,
    val roll: String,
    /** Column index → mark; `null` means the cell was blank or "Ab". */
    val marks: Map<Int, Double?>,
)

data class ExamImportRows(
    val rows: List<ExamImportRow> = emptyList(),
    val unmatched: Int = 0,
    val duplicates: Int = 0,
    /** Non-empty mark cells that were neither a number nor "Ab". */
    val unreadableMarks: Int = 0,
)

/** Everything a commit writes, built before a single database call. */
data class ExamImportWritePlan(
    val exam: Exam,
    val subjects: List<ExamSubject>,
    val marks: List<ExamMark>,
    val clampedMarks: Int,
)

/**
 * Reads an exam sheet: the header row names the subjects (optionally with
 * `(max)` or `(max/pass)`), one column identifies the student, and every other
 * numeric column is a subject.
 *
 * Pure functions only — the preview and the commit use the same rules, and the
 * rules are unit-tested without Room, a device or an Activity.
 */
object ExamImportModel {

    private val NORMALISE = Regex("""\s+""")

    private val HEADER_MARKS = Regex(
        """[\(\[]\s*(\d{1,4})\s*(?:/\s*(\d{1,4}))?\s*(?:marks?|max)?\s*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    /** "Math - 100", "Math 100", "Math – 100/35" at the end of a header. */
    private val TRAILING_MARKS = Regex(
        """\s*(?:[-–—:]|\bmax\b)?\s*(\d{1,4})\s*(?:/\s*(\d{1,4}))?\s*(?:marks?)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val ABSENT = setOf("a", "ab", "abs", "absent", "-", "--", "—", "–")

    private val NAME_HINTS = listOf("student name", "name of student", "name")
    private val NAME_EXCLUDE = listOf("father", "mother", "parent", "guardian", "teacher")
    private val ROLL_HINTS = listOf("roll", "admission", "adm no", "adm.", "enrol", "enroll")
    private val SERIAL_HINTS = listOf("s.no", "s no", "sno", "serial", "sr no")

    private val COMPUTED_HINTS = listOf(
        "total", "percentage", "percent", "result", "grade", "rank", "average",
        "avg", "remark", "pass/fail", "pass or fail", "division",
    )

    /**
     * Works out the identity columns and subject columns. Columns that look
     * computed (Total, Percentage, Result …) are never treated as subjects, and
     * neither is a plain text column.
     */
    fun plan(table: ParsedTable): ExamImportPlan {
        val headers = table.headers.map { it.trim() }
        val nameColumn = headers.indexOfFirst { header ->
            val lower = header.lowercase()
            lower !in ABSENT && !isComputed(lower) &&
                NAME_EXCLUDE.none { lower.contains(it) } &&
                NAME_HINTS.any { lower.contains(it) }
        }
        var rollColumn = headers.indexOfFirst { header ->
            val lower = header.lowercase()
            !isComputed(lower) && ROLL_HINTS.any { lower.contains(it) }
        }
        if (rollColumn < 0) {
            // An "S.No." column is often the only identifier a hand-made list has.
            rollColumn = headers.indexOfFirst { header ->
                val lower = header.lowercase()
                !isComputed(lower) && SERIAL_HINTS.any { lower.contains(it) }
            }
        }

        val identity = setOf(nameColumn, rollColumn).filter { it >= 0 }.toSet()
        val subjects = headers.indices
            .filterNot { it in identity }
            .mapNotNull { column ->
                val header = headers[column]
                if (isComputed(header)) return@mapNotNull null
                val marks = headerMarks(header)
                val hasNumbers = table.rows.any { row -> parseMark(table.cellAt(row, column)) != null }
                if (marks == null && !hasNumbers) return@mapNotNull null
                val max = marks?.first ?: 100
                val pass = marks?.second ?: defaultPass(max)
                ExamImportSubjectDraft(
                    column = column,
                    name = headerName(header),
                    maxMarks = max.toString(),
                    passMarks = pass.toString(),
                )
            }

        return ExamImportPlan(
            nameColumn = nameColumn,
            rollColumn = rollColumn,
            subjects = subjects,
        )
    }

    /**
     * Resolves each row to one student: roll number first (a roll is what a
     * teacher edits least), then the name. Rows that match nobody, and second
     * rows for a student already seen, are reported instead of written.
     */
    fun matchRows(
        table: ParsedTable,
        plan: ExamImportPlan,
        candidates: List<ImportCandidate>,
    ): ExamImportRows {
        val byRoll = candidates
            .mapNotNull { candidate ->
                candidate.rollNumber?.let { rollKey(it).ifEmpty { null } }?.let { it to candidate }
            }
            .toMap()
        val byName = candidates
            .map { normalise(it.name) }
            .zip(candidates)
            .toMap()

        val subjectColumns = plan.subjects.map { it.column }.toSet()
        val seen = mutableSetOf<String>()
        var unmatched = 0
        var duplicates = 0
        var unreadable = 0
        val rows = mutableListOf<ExamImportRow>()

        table.rows.forEach { row ->
            val roll = if (plan.rollColumn >= 0) table.cellAt(row, plan.rollColumn) else ""
            val name = if (plan.nameColumn >= 0) table.cellAt(row, plan.nameColumn) else ""
            if (roll.isBlank() && name.isBlank()) {
                return@forEach
            }
            val student = rollKey(roll).let { byRoll[it] } ?: byName[normalise(name)]
            if (student == null) {
                unmatched++
                return@forEach
            }
            if (!seen.add(student.id)) {
                duplicates++
                return@forEach
            }
            val marks = subjectColumns.associateWith { column ->
                val text = table.cellAt(row, column)
                val value = parseMark(text)
                if (value == null && text.isNotBlank() && !isAbsent(text)) unreadable++
                value
            }
            rows.add(
                ExamImportRow(
                    studentId = student.id,
                    name = student.name,
                    roll = roll.ifBlank { student.rollNumber.orEmpty() },
                    marks = marks,
                ),
            )
        }

        return ExamImportRows(
            rows = rows,
            unmatched = unmatched,
            duplicates = duplicates,
            unreadableMarks = unreadable,
        )
    }

    /**
     * Builds the exam, its subjects and only the marks that were actually
     * entered. Blank mark cells stay absent so the app shows them as "not
     * entered"; no zero is invented.
     */
    fun writePlan(
        classId: String,
        examName: String,
        rows: ExamImportRows,
        subjects: List<ExamImportSubjectDraft>,
        now: Long,
        newId: () -> String,
    ): ExamImportWritePlan {
        val examId = newId()
        val chosen = subjects.filter { it.included && it.isValid }
        val subjectEntities = chosen.mapIndexed { index, draft ->
            val max = draft.maxValue ?: 0
            ExamSubject(
                id = newId(),
                examId = examId,
                name = draft.name.trim().ifEmpty { "Subject ${index + 1}" },
                maxMarks = max,
                passMarks = (draft.passValue ?: 0).coerceIn(0, max),
                sortOrder = index,
            )
        }
        val subjectByColumn = chosen.indices.associate { index ->
            chosen[index].column to subjectEntities[index]
        }

        var clamped = 0
        val marks = mutableListOf<ExamMark>()
        rows.rows.forEach { row ->
            subjectByColumn.forEach { (column, subject) ->
                val mark = row.marks[column] ?: return@forEach
                if (mark > subject.maxMarks) clamped++
                marks.add(
                    ExamMark(
                        id = newId(),
                        examId = examId,
                        subjectId = subject.id,
                        studentId = row.studentId,
                        marksObtained = mark.coerceIn(0.0, subject.maxMarks.toDouble()),
                    ),
                )
            }
        }

        return ExamImportWritePlan(
            exam = Exam(
                id = examId,
                classId = classId,
                name = examName.trim(),
                examDate = now,
                createdAt = now,
                updatedAt = now,
            ),
            subjects = subjectEntities,
            marks = marks,
            clampedMarks = clamped,
        )
    }

    /** A cell is a mark when it is a plain number; blank and "Ab" are no mark. */
    fun parseMark(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isAbsent(trimmed)) return null
        return trimmed.toDoubleOrNull()
    }

    fun isAbsent(text: String): Boolean = text.trim().lowercase() in ABSENT

    /** True for headers that are results of the marks, not new subjects. */
    fun isComputed(header: String): Boolean {
        val lower = header.trim().lowercase()
        return COMPUTED_HINTS.any { lower.contains(it) } || lower.trim() in setOf("%", "pass", "fail")
    }

    /** `Math (100)`, `Science (50/18)`, `Math - 100` → max/pass, else null. */
    fun headerMarks(header: String): Pair<Int, Int>? {
        val bracketed = HEADER_MARKS.find(header)
        if (bracketed != null) {
            val max = bracketed.groupValues[1].toIntOrNull() ?: return null
            val pass = bracketed.groupValues[2].toIntOrNull() ?: defaultPass(max)
            return max to pass
        }
        if (header.any(Char::isDigit)) {
            val trailing = TRAILING_MARKS.find(header) ?: return null
            val max = trailing.groupValues[1].toIntOrNull() ?: return null
            // "Hindi 2" has a digit but no marks; require a pass figure or a
            // number big enough to be a maximum, so a subject name is not
            // mistaken for one.
            val looksLikeMarks = trailing.groupValues[2].isNotEmpty() || max >= 10
            if (!looksLikeMarks) return null
            val pass = trailing.groupValues[2].toIntOrNull() ?: defaultPass(max)
            return max to pass
        }
        return null
    }

    /** The subject's display name with any marks suffix removed. */
    fun headerName(header: String): String {
        val withoutBracket = if (HEADER_MARKS.containsMatchIn(header)) {
            header.replace(HEADER_MARKS, " ")
        } else {
            header
        }
        val withoutTrailing = if (headerMarks(header) != null) {
            withoutBracket.replace(TRAILING_MARKS, " ")
        } else {
            withoutBracket
        }
        return withoutTrailing.trim().trim('-', '–', '—', ':', ',').trim()
            .ifEmpty { header.trim() }
    }

    /** 35% of the maximum, rounded up, the same pass line the app defaults to. */
    fun defaultPass(max: Int): Int = ((max * 35) + 99) / 100

    private fun normalise(name: String): String =
        name.trim().lowercase().replace(NORMALISE, " ")

    /** "01" and "1" are the same roll; names keep their text. */
    private fun rollKey(roll: String): String {
        val key = roll.trim().lowercase().replace(NORMALISE, " ")
        return key.toIntOrNull()?.toString() ?: key
    }
}
