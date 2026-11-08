package com.example.helper.examsresults

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.examsresults.viewmodel.ExamListViewModel
import com.example.helper.examsresults.viewmodel.ExamImportViewModel
import com.example.helper.examsresults.viewmodel.ExamResultViewModel
import com.example.helper.examsresults.viewmodel.ExamSetupViewModel
import com.example.helper.examsresults.viewmodel.ExportColumnsViewModel
import com.example.helper.examsresults.viewmodel.MarkEntryViewModel
import com.example.helper.examsresults.viewmodel.StudentExamHistoryViewModel

@Composable
fun examListViewModel(classId: String): ExamListViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "exam-list-$classId") {
        ExamListViewModel(app.container.examRepository, app.container.classRepository, classId)
    }
}

@Composable
fun examSetupViewModel(): ExamSetupViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel {
        ExamSetupViewModel(app.container.examRepository)
    }
}

@Composable
fun examImportViewModel(classId: String): ExamImportViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "exam-import-$classId") {
        ExamImportViewModel(
            context = app,
            classId = classId,
            examRepository = app.container.examRepository,
            studentRepository = app.container.studentRepository,
        )
    }
}

@Composable
fun markEntryViewModel(examId: String, classId: String, subjectId: String): MarkEntryViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "mark-entry-$examId-$classId-$subjectId") {
        MarkEntryViewModel(examId, subjectId, app.container.examRepository, app.container.studentRepository, classId)
    }
}

@Composable
fun examResultViewModel(examId: String, classId: String): ExamResultViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "exam-result-$examId-$classId") {
        ExamResultViewModel(
            examId,
            app.container.examRepository,
            app.container.studentRepository,
            classId,
            app.container.exportColumnRepository,
            app.container.classRepository,
            app.container.sessionRepository,
        )
    }
}

@Composable
fun exportColumnsViewModel(examId: String): ExportColumnsViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "export-columns-$examId") {
        ExportColumnsViewModel(
            examId = examId,
            examRepository = app.container.examRepository,
            exportColumnRepository = app.container.exportColumnRepository,
        )
    }
}

@Composable
fun studentExamHistoryViewModel(studentId: String): StudentExamHistoryViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = "student-exam-history-$studentId") {
        StudentExamHistoryViewModel(
            studentId = studentId,
            studentRepository = app.container.studentRepository,
            examRepository = app.container.examRepository,
        )
    }
}
