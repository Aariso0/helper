package com.example.helper.studentfeedback.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication

/**
 * Builds a FeedbackViewModel from the manual AppContainer (core-data card).
 * [studentIdPreset] set → quick-note / timeline instance; null → the full
 * searchable list. Callers pass distinct [key]s so the three surfaces never
 * share one instance.
 */
@Composable
fun feedbackViewModel(
    key: String,
    studentIdPreset: String? = null,
): FeedbackViewModel {
    val container = (LocalContext.current.applicationContext as HelperApplication).container
    return viewModel(key = key) {
        FeedbackViewModel(
            feedbackRepository = container.feedbackRepository,
            studentRepository = container.studentRepository,
            studentIdPreset = studentIdPreset,
        )
    }
}
