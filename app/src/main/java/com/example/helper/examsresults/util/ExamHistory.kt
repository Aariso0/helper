package com.example.helper.examsresults.util

import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student

/** One subject's mark inside an exam, including the "not entered" case. */
data class ExamSubjectMark(
    val subjectId: String,
    val subjectName: String,
    val obtained: Double?,
    val maxMarks: Int,
    val passMarks: Int,
    val isAbsent: Boolean = false,
) {
    val entered: Boolean get() = !isAbsent && obtained != null
    val failed: Boolean get() = isAbsent || (obtained != null && obtained < passMarks)
}

enum class ExamHistoryStatus { PASS, FAIL, NOT_ENTERED }

/** One exam's result for a single student, plus the per-subject breakdown. */
data class ExamHistoryEntry(
    val examId: String,
    val examName: String,
    val examDate: Long?,
    val result: StudentResult,
    val subjectMarks: List<ExamSubjectMark>,
    /** Position in the class for this exam, shared on ties; null if not entered. */
    val rank: Int? = null,
) {
    val hasMarks: Boolean get() = subjectMarks.any { it.entered || it.isAbsent }
    val enteredCount: Int get() = subjectMarks.count { it.entered }
    val failedSubjectNames: List<String> get() = subjectMarks.filter { it.failed }.map { it.subjectName }

    val status: ExamHistoryStatus
        get() = when {
            failedSubjectNames.isNotEmpty() -> ExamHistoryStatus.FAIL
            hasMarks -> ExamHistoryStatus.PASS
            else -> ExamHistoryStatus.NOT_ENTERED
        }
}

/**
 * Folds a class's exams, their subjects and every student's marks into the
 * newest-first history the student page shows. [marks] and [classmates] cover
 * the whole class so [student]'s rank can be worked out per exam; only this
 * student's marks are shown in the card. Kept separate from the ViewModel so
 * the PASS/FAIL/blank and rank rules are unit-testable.
 */
object ExamHistory {
    fun build(
        exams: List<Exam>,
        subjects: List<ExamSubject>,
        marks: List<ExamMark>,
        student: Student,
        classmates: List<Student>,
    ): List<ExamHistoryEntry> {
        val subjectsByExam = subjects.groupBy { it.examId }
        val marksByExam = marks.groupBy { it.examId }

        return exams
            .sortedWith(
                compareByDescending<Exam> { it.examDate ?: Long.MIN_VALUE }
                    .thenByDescending { it.createdAt },
            )
            .map { exam ->
                val examSubjects = subjectsByExam[exam.id].orEmpty()
                    .sortedBy { it.sortOrder }
                val classMarks = marksByExam[exam.id].orEmpty()
                val examMarks = classMarks.filter { it.studentId == student.id }
                val marksBySubject = examMarks.associateBy { it.subjectId }
                val result = ResultCalculator.calculate(student, examSubjects, examMarks)

                ExamHistoryEntry(
                    examId = exam.id,
                    examName = exam.name,
                    examDate = exam.examDate,
                    result = result,
                    subjectMarks = examSubjects.map { subject ->
                        ExamSubjectMark(
                            subjectId = subject.id,
                            subjectName = subject.name,
                            obtained = marksBySubject[subject.id]?.marksObtained,
                            maxMarks = subject.maxMarks,
                            passMarks = subject.passMarks,
                            isAbsent = marksBySubject[subject.id]?.isAbsent == true,
                        )
                    },
                    rank = rankOf(result, classmates, examSubjects, classMarks),
                )
            }
    }

    /** `1 + count(greater totals)` — the same competition rank the export prints. */
    private fun rankOf(
        result: StudentResult,
        classmates: List<Student>,
        subjects: List<ExamSubject>,
        marks: List<ExamMark>,
    ): Int? {
        val recorded = result.marksBySubject.isNotEmpty() || result.absentSubjectIds.isNotEmpty()
        if (!recorded) return null
        val totals = classmates.map { classmate ->
            ResultCalculator.calculate(
                classmate,
                subjects,
                marks.filter { it.studentId == classmate.id },
            ).totalObtained
        }
        return 1 + totals.count { it > result.totalObtained }
    }
}
