package com.example.helper.examsresults.export

import com.example.helper.coredata.entity.ExamSubject
import com.example.helper.examsresults.util.StudentResult
import kotlin.math.floor

/**
 * The numbers printed on pages 2 and 3 of the paper sheet, computed once from
 * the same results the workbook exports. The PDF reads this model instead of
 * re-deriving anything, so a page-2 figure cannot drift from the page-1 marks.
 *
 * Per-subject "appeared" counts numeric marks; a deliberate absence is a
 * separate state and is reported in [SubjectSheetStats.absent]. Absence does
 * not enter "failed" (it has its own column), so `passed + failed == appeared`.
 * The overall row treats a student with any absent subject as failed, matching
 * the Result column, while its absent figure is the total number of absent
 * subject entries.
 */
internal data class SubjectSheetStats(
    val subjectId: String,
    val name: String,
    val maxMarks: Int,
    val passMarks: Int,
    val strength: Int,
    val registered: Int,
    val appeared: Int,
    val absent: Int,
    val passed: Int,
    val failed: Int,
    val total: Int,
    val passShare: Double?,
    val averageShare: Double?,
    val bands7: List<Int>,
    val bands5: List<Int>,
)

internal data class OverallSheetStats(
    val strength: Int,
    val registered: Int,
    val appeared: Int,
    val absent: Int,
    val passed: Int,
    val failed: Int,
    val total: Int,
    val passShare: Double?,
    val averageShare: Double?,
    val bands7: List<Int>,
    val bands5: List<Int>,
)

internal data class ExamSheetStats(
    val subjects: List<SubjectSheetStats>,
    val overall: OverallSheetStats,
) {
    companion object {
        private val SEVEN_THRESHOLDS = listOf(33, 50, 60, 75, 90, 95)
        private val FIVE_THRESHOLDS = listOf(33, 60, 75, 90)

        val SEVEN_BAND_LABELS = listOf(
            "Below 33%",
            "33-49%",
            "50-59%",
            "60-74%",
            "75-89%",
            "90-94%",
            "95% & Above",
        )

        val FIVE_BAND_LABELS = listOf(
            "Below 33%",
            "33-<60%",
            "60-<75%",
            "75-<90%",
            "90% & Above",
        )

        fun from(
            subjects: List<ExamSubject>,
            results: List<StudentResult>,
        ): ExamSheetStats {
            val subjectStats = subjects.map { subject ->
                val marks = results.mapNotNull { it.marksBySubject[subject.id] }
                val appeared = marks.size
                val absent = results.count { subject.id in it.absentSubjectIds }
                val passed = marks.count { it >= subject.passMarks }
                val failed = marks.count { it < subject.passMarks }
                SubjectSheetStats(
                    subjectId = subject.id,
                    name = subject.name,
                    maxMarks = subject.maxMarks,
                    passMarks = subject.passMarks,
                    strength = results.size,
                    registered = results.size,
                    appeared = appeared,
                    absent = absent,
                    passed = passed,
                    failed = failed,
                    total = passed + failed,
                    passShare = if (appeared == 0) null else passed.toDouble() / appeared,
                    averageShare = if (appeared == 0 || subject.maxMarks <= 0) {
                        null
                    } else {
                        marks.average() / subject.maxMarks
                    },
                    bands7 = subjectBands(marks, subject.maxMarks, SEVEN_THRESHOLDS),
                    bands5 = subjectBands(marks, subject.maxMarks, FIVE_THRESHOLDS),
                )
            }

            val shares = results.map { it.percentage / 100.0 }
            val overallAppeared = subjectStats.maxOfOrNull { it.appeared } ?: 0
            val passed = results.count { it.isPass }
            val failed = results.count { it.isRecorded() && !it.isPass }
            val overall = OverallSheetStats(
                strength = results.size,
                registered = results.size,
                appeared = overallAppeared,
                absent = subjectStats.sumOf { it.absent },
                passed = passed,
                failed = failed,
                total = passed + failed,
                passShare = if (overallAppeared == 0) null else passed.toDouble() / overallAppeared,
                averageShare = if (overallAppeared == 0) {
                    null
                } else {
                    shares.sum() / overallAppeared
                },
                bands7 = shareBands(shares, SEVEN_THRESHOLDS),
                bands5 = shareBands(shares, FIVE_THRESHOLDS),
            )

            return ExamSheetStats(subjects = subjectStats, overall = overall)
        }

        /**
         * Mirrors the workbook's mixed rounding: the first band uses
         * `INT(max*33/100)` as its boundary while every later band starts at
         * the unrounded share. A mark between the two therefore sits in no
         * band, exactly as the example's formulas behave.
         */
        private fun subjectBands(
            marks: List<Double>,
            maxMarks: Int,
            thresholds: List<Int>,
        ): List<Int> {
            val bands = MutableList(thresholds.size + 1) { 0 }
            if (maxMarks <= 0) return bands
            val belowBoundary = floor(maxMarks * thresholds.first() / 100.0)
            marks.forEach { mark ->
                if (mark < belowBoundary) {
                    bands[0]++
                    return@forEach
                }
                var index = -1
                for (band in 1 until thresholds.size) {
                    val lower = maxMarks * thresholds[band - 1] / 100.0
                    val upper = maxMarks * thresholds[band] / 100.0
                    if (mark >= lower && mark < upper) {
                        index = band
                        break
                    }
                }
                if (index == -1 && mark >= maxMarks * thresholds.last() / 100.0) {
                    index = thresholds.size
                }
                if (index >= 0) bands[index]++
            }
            return bands
        }

        /** Overall bands read the percentage shares directly, like the example. */
        private fun shareBands(
            shares: List<Double>,
            thresholds: List<Int>,
        ): List<Int> {
            val bands = MutableList(thresholds.size + 1) { 0 }
            shares.forEach { share ->
                var band = 0
                while (band < thresholds.size && share >= thresholds[band] / 100.0) {
                    band++
                }
                bands[band]++
            }
            return bands
        }

        private fun StudentResult.isRecorded(): Boolean =
            marksBySubject.isNotEmpty() || absentSubjectIds.isNotEmpty()
    }
}
