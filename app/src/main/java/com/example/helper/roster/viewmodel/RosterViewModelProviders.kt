package com.example.helper.roster.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.roster.photostorage.PhotoStorage

/**
 * Manual-DI factories for roster's ViewModels (same pattern as
 * student-feedback's provider): repositories + [PhotoStorage] come from the
 * application's [AppContainer]; each screen passes its own [key] so instances
 * never collide across list/page/picker surfaces.
 */
@Composable
fun classListViewModel(key: String = "roster-classlist"): ClassListViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        ClassListViewModel(container.classRepository, container.studentRepository, PhotoStorage(app))
    }
}

@Composable
fun studentListViewModel(classId: String, key: String = "roster-studentlist-$classId"): StudentListViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        StudentListViewModel(
            classId = classId,
            classRepository = container.classRepository,
            studentRepository = container.studentRepository,
            photoStorage = PhotoStorage(app),
        )
    }
}

@Composable
fun studentPageViewModel(studentId: String, key: String = "roster-studentpage-$studentId"): StudentPageViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        StudentPageViewModel(
            studentId = studentId,
            classRepository = container.classRepository,
            studentRepository = container.studentRepository,
            attendanceRepository = container.attendanceRepository,
            photoStorage = PhotoStorage(app),
        )
    }
}

@Composable
fun importViewModel(key: String = "roster-import"): ImportViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        ImportViewModel(app, container.classRepository, container.studentRepository)
    }
}

@Composable
fun pickerViewModel(key: String = "roster-picker"): PickerViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        PickerViewModel(container.classRepository, container.studentRepository)
    }
}
