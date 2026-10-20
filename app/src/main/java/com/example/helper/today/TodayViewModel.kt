package com.example.helper.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.helper.HelperApplication
import com.example.helper.coredata.repository.AttendanceRepository
import com.example.helper.coredata.repository.HomeworkRepository
import com.example.helper.coredata.repository.SchoolClassRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Today screen state (app-shell card): today's classes with their attendance
 * status (the "not taken" nudge + counts, README rules 3 & 8), homework due
 * today/overdue, and the date header. Reads `data-repositories` only — no
 * feature-module internals.
 */
class TodayViewModel(
    private val classRepository: SchoolClassRepository,
    private val attendanceRepository: AttendanceRepository,
    private val homeworkRepository: HomeworkRepository,
) : ViewModel() {

    /** The day this VM instance is scoped to (fixed at creation, like a screen). */
    private val today: LocalDate = LocalDate.now()
    val todayEpochDay: Long = today.toEpochDay()

    private val todayLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val dueDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

    /** One class row: glanceable attendance state for today. */
    data class ClassRow(
        val classId: String,
        val name: String,
        val subtitle: String,
        val attendanceTaken: Boolean,
        val present: Int,
        val absent: Int,
        val late: Int,
        val excused: Int,
    )

    /** One homework row that needs attention today (due today or overdue). */
    data class DueHomeworkRow(
        val homeworkId: String,
        val title: String,
        val className: String,
        val dueLabel: String,
        val overdue: Boolean,
    )

    data class UiState(
        val loading: Boolean = true,
        val todayLabel: String = "",
        val classes: List<ClassRow> = emptyList(),
        val dueHomework: List<DueHomeworkRow> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        classRepository.observeAll(),
        attendanceRepository.observeClassDayCounts(todayEpochDay),
        homeworkRepository.observeAll(),
    ) { classes, dayCounts, homework ->
        val countsByClassId = dayCounts.associateBy { it.classId }
        val classNamesById = classes.associate { it.id to it.name }

        val rows = classes.map { schoolClass ->
            val counts = countsByClassId[schoolClass.id]
            ClassRow(
                classId = schoolClass.id,
                name = schoolClass.name,
                subtitle = listOfNotNull(schoolClass.subject, schoolClass.year).joinToString(" · "),
                attendanceTaken = counts != null,
                present = counts?.present ?: 0,
                absent = counts?.absent ?: 0,
                late = counts?.late ?: 0,
                excused = counts?.excused ?: 0,
            )
        }

        // Attention list: due today or already overdue, most overdue first.
        val dueRows = homework
            .mapNotNull { withAttachments ->
                val hw = withAttachments.homework
                val due = hw.dueDateEpochDay ?: return@mapNotNull null
                if (due > todayEpochDay) return@mapNotNull null
                val row = DueHomeworkRow(
                    homeworkId = hw.id,
                    title = hw.title,
                    className = classNamesById[hw.classId] ?: "Class",
                    dueLabel = if (due == todayEpochDay) {
                        "Due today"
                    } else {
                        "Overdue · ${LocalDate.ofEpochDay(due).format(dueDateFormat)}"
                    },
                    overdue = due < todayEpochDay,
                )
                due to row
            }
            .sortedWith(compareBy({ it.first }, { it.second.title }))
            .map { it.second }

        UiState(
            loading = false,
            todayLabel = today.format(todayLabelFormat),
            classes = rows,
            dueHomework = dueRows,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )
}

@Composable
fun todayViewModel(key: String = "appshell-today"): TodayViewModel {
    val app = LocalContext.current.applicationContext as HelperApplication
    return viewModel(key = key) {
        val container = app.container
        TodayViewModel(
            classRepository = container.classRepository,
            attendanceRepository = container.attendanceRepository,
            homeworkRepository = container.homeworkRepository,
        )
    }
}
