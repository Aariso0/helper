package com.example.helper.coredata.repository

import com.example.helper.coredata.dao.ExamDao
import com.example.helper.coredata.dao.ExamMarkDao
import com.example.helper.coredata.dao.ExamSubjectDao
import com.example.helper.coredata.entity.Exam
import com.example.helper.coredata.entity.ExamMark
import com.example.helper.coredata.entity.ExamSubject
import kotlinx.coroutines.flow.Flow

class ExamRepository(
    private val examDao: ExamDao,
    private val subjectDao: ExamSubjectDao,
    private val markDao: ExamMarkDao,
) {
    fun observeByClass(classId: String): Flow<List<Exam>> = examDao.observeByClass(classId)
    fun observeAll(): Flow<List<Exam>> = examDao.observeAll()
    fun observeById(id: String): Flow<Exam?> = examDao.observeById(id)

    suspend fun getById(id: String): Exam? = examDao.getById(id)

    suspend fun createExam(exam: Exam) {
        examDao.upsert(exam)
    }

    suspend fun deleteExam(exam: Exam) {
        examDao.delete(exam)
    }

    fun observeSubjects(examId: String): Flow<List<ExamSubject>> = subjectDao.observeByExam(examId)
    fun observeSubjectsForClass(classId: String): Flow<List<ExamSubject>> = subjectDao.observeByClass(classId)
    suspend fun getSubjects(examId: String): List<ExamSubject> = subjectDao.getByExam(examId)

    suspend fun upsertSubject(subject: ExamSubject) {
        subjectDao.upsert(subject)
    }

    suspend fun upsertSubjects(subjects: List<ExamSubject>) {
        subjectDao.upsertAll(subjects)
    }

    suspend fun deleteSubject(subject: ExamSubject) {
        subjectDao.delete(subject)
    }

    /**
     * Updates an existing subject's totals. Passing the total down also lowers
     * any mark already entered above the new total to that total (same
     * transaction); pass marks are held within `0..maxMarks`.
     */
    suspend fun updateSubject(subject: ExamSubject, maxMarks: Int, passMarks: Int) {
        if (maxMarks <= 0) return
        subjectDao.updateWithClampedMarks(
            subject.copy(
                maxMarks = maxMarks,
                passMarks = passMarks.coerceIn(0, maxMarks),
            ),
        )
    }

    fun observeMarks(examId: String): Flow<List<ExamMark>> = markDao.observeByExam(examId)
    fun observeMarksForStudent(examId: String, studentId: String): Flow<List<ExamMark>> =
        markDao.observeForStudent(examId, studentId)

    fun observeMarksForStudent(studentId: String): Flow<List<ExamMark>> =
        markDao.observeByStudent(studentId)

    suspend fun getMarks(examId: String): List<ExamMark> = markDao.getByExam(examId)

    suspend fun upsertMark(mark: ExamMark) {
        markDao.upsert(mark)
    }

    suspend fun upsertMarks(marks: List<ExamMark>) {
        markDao.upsertAll(marks)
    }

    suspend fun deleteMark(mark: ExamMark) {
        markDao.delete(mark)
    }

    suspend fun deleteMarksForStudent(examId: String, studentId: String) {
        markDao.deleteForStudent(examId, studentId)
    }
}
