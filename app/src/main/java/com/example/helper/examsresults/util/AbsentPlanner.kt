package com.example.helper.examsresults.util

import com.example.helper.coredata.entity.ExamSubject

/** A student/subject pair with neither a mark nor an absent row. */
data class BlankMark(
    val studentId: String,
    val subjectId: String,
)

/**
 * Finds the blanks the bulk "mark remaining as absent" action has to fill.
 * Kept pure so the count shown in the confirmation is exactly what gets
 * written.
 */
internal object AbsentPlanner {
    fun blankPairs(
        results: List<StudentResult>,
        subjects: List<ExamSubject>,
    ): List<BlankMark> = results.flatMap { result ->
        subjects.mapNotNull { subject ->
            val recorded = subject.id in result.marksBySubject ||
                subject.id in result.absentSubjectIds
            if (recorded) {
                null
            } else {
                BlankMark(studentId = result.student.id, subjectId = subject.id)
            }
        }
    }
}
