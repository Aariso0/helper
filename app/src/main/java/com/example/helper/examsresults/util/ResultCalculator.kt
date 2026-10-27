package com.example.helper.examsresults.util

import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.coredata.entity.Student

data class StudentResult(
    val student: Student,
    val marksBySubject: Map<String, Double>,
    val totalObtained: Double,
    val totalMax: Double,
    val percentage: Double,
    val isPass: Boolean,
    val failedSubjects: List<String>,
)

object ResultCalculator {
    fun calculate(
        student: Student,
        subjects: List<ExamSubject>,
        marks: List<ExamMark>,
    ): StudentResult {
        val marksMap = marks.associateBy { it.subjectId }
        var totalObtained = 0.0
        var totalMax = 0.0
        val failed = mutableListOf<String>()
        val perSubject = mutableMapOf<String, Double>()

        subjects.forEach { subject ->
            val mark = marksMap[subject.id]
            // A missing mark is not a zero: it is not entered yet, so it cannot
            // fail the subject and is left out of `failedSubjects`. It still
            // counts toward the total max so the percentage is over the paper.
            if (mark != null) {
                perSubject[subject.id] = mark.marksObtained
                totalObtained += mark.marksObtained
                if (mark.marksObtained < subject.passMarks.toDouble()) {
                    failed.add(subject.id)
                }
            }
            totalMax += subject.maxMarks.toDouble()
        }

        val percentage = if (totalMax <= 0.0) 0.0 else totalObtained / totalMax * 100.0

        return StudentResult(
            student = student,
            marksBySubject = perSubject,
            totalObtained = totalObtained,
            totalMax = totalMax,
            percentage = percentage,
            // At least one mark must exist to judge; among the entered marks,
            // one failure fails the student, otherwise the student passes even
            // if other subjects are still unentered.
            isPass = perSubject.isNotEmpty() && failed.isEmpty(),
            failedSubjects = failed,
        )
    }
}
