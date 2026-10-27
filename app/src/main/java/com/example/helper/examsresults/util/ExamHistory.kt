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
) {
    val entered: Boolean get() = obtained != null
    val failed: Boolean get() = obtained != null && obtained < passMarks
}

enum class ExamHistoryStatus { PASS, FAIL, NOT_ENTERED }

/** One exam's result for a single student, plus the per-subject breakdown. */
data class ExamHistoryEntry(
    val examId: String,
    val examName: String,
    val examDate: Long?,
    val result: StudentResult,
    val subjectMarks: List<ExamSubjectMark>,
) {
    val hasMarks: Boolean get() = subjectMarks.any { it.entered }
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
 * Folds a class's exams, their subjects and one student's marks into the
 * newest-first history the student page shows. Kept separate from the
 * ViewModel so the PASS/FAIL/blank rules are unit-testable.
 */
object ExamHistory {
    fun build(
        exams: List<Exam>,
        subjects: List<ExamSubject>,
        marks: List<ExamMark>,
        student: Student,
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
                val examMarks = marksByExam[exam.id].orEmpty()
                    .filter { it.studentId == student.id }
                val marksBySubject = examMarks.associateBy { it.subjectId }

                ExamHistoryEntry(
                    examId = exam.id,
                    examName = exam.name,
                    examDate = exam.examDate,
                    result = ResultCalculator.calculate(student, examSubjects, examMarks),
                    subjectMarks = examSubjects.map { subject ->
                        ExamSubjectMark(
                            subjectId = subject.id,
                            subjectName = subject.name,
                            obtained = marksBySubject[subject.id]?.marksObtained,
                            maxMarks = subject.maxMarks,
                            passMarks = subject.passMarks,
                        )
                    },
                )
            }
    }
}
