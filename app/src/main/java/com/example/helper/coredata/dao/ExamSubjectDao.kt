package com.example.helper.coredata.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.helper.coredata.entity.ExamSubject
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExamSubjectDao {
    @Query("SELECT * FROM exam_subjects WHERE examId = :examId ORDER BY sortOrder ASC")
    abstract fun observeByExam(examId: String): Flow<List<ExamSubject>>

    @Query("SELECT * FROM exam_subjects WHERE examId = :examId ORDER BY sortOrder ASC")
    abstract suspend fun getByExam(examId: String): List<ExamSubject>

    @Query(
        """
        SELECT exam_subjects.* FROM exam_subjects
        INNER JOIN exams ON exams.id = exam_subjects.examId
        WHERE exams.classId = :classId
        ORDER BY exam_subjects.examId ASC, exam_subjects.sortOrder ASC
        """
    )
    abstract fun observeByClass(classId: String): Flow<List<ExamSubject>>

    @Upsert
    abstract suspend fun upsert(subject: ExamSubject)

    @Upsert
    abstract suspend fun upsertAll(subjects: List<ExamSubject>)

    @Delete
    abstract suspend fun delete(subject: ExamSubject)

    /**
     * Saves new totals and lowers every existing mark that is now above the
     * new maximum, so the subject and its marks never disagree. One
     * transaction: a failure leaves both untouched.
     */
    @Transaction
    open suspend fun updateWithClampedMarks(subject: ExamSubject) {
        upsert(subject)
        clampMarks(subject.id, subject.maxMarks.toDouble())
    }

    @Query(
        """
        UPDATE exam_marks SET marksObtained = :maxMarks
        WHERE subjectId = :subjectId AND marksObtained > :maxMarks
        """
    )
    abstract suspend fun clampMarks(subjectId: String, maxMarks: Double)
}
