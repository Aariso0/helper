package com.example.helper.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.helper.attendance.ui.AttendanceScreen
import com.example.helper.homework.ui.HomeworkScreen
import com.example.helper.roster.ui.classlist.ClassListScreen
import com.example.helper.roster.ui.studentlist.StudentListScreen
import com.example.helper.roster.ui.studentpage.StudentPageScreen
import com.example.helper.parents.ui.ParentsScreen
import com.example.helper.studentfeedback.ui.FeedbackScreen
import com.example.helper.studentfeedback.ui.QuickNoteScreen
import com.example.helper.today.TodayScreen
import java.time.LocalDate

/**
 * The route table + feature wiring (app-shell card). Every route string and
 * every argument parse lives in [Routes]; feature screens only ever receive
 * typed values. Modules not yet built (export) sit behind honest
 * placeholders until they hand their root composable over (build protocol:
 * message the app-shell owner).
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        modifier = modifier,
    ) {
        // ── Bottom-bar tabs ────────────────────────────────────────────────
        composable(Routes.TODAY) {
            TodayScreen(
                onOpenAttendance = { classId, dateEpochDay ->
                    navController.navigate(Routes.attendance(classId, dateEpochDay))
                },
                onQuickNote = { studentId ->
                    navController.navigate(Routes.quickNote(studentId))
                },
                onOpenFeedback = { navController.navigate(Routes.feedback()) },
                onGoToStudents = { navController.navigateToTab(Routes.STUDENTS) },
            )
        }
        composable(Routes.STUDENTS) {
            ClassListScreen(
                onOpenClass = { classId -> navController.navigate(Routes.classStudents(classId)) },
            )
        }
        composable(Routes.HOMEWORK) {
            HomeworkScreen(
                onOpenClass = { classId -> navController.navigate(Routes.classStudents(classId)) },
            )
        }
        composable(Routes.PARENTS) {
            ParentsScreen()
        }

        // ── Roster (built) ─────────────────────────────────────────────────
        composable(
            route = Routes.CLASS_STUDENTS,
            arguments = listOf(navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType }),
        ) { entry ->
            val classId = entry.classId() ?: return@composable
            StudentListScreen(
                classId = classId,
                onOpenStudent = { studentId -> navController.navigate(Routes.student(studentId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.STUDENT,
            arguments = listOf(navArgument(Routes.Args.STUDENT_ID) { type = NavType.StringType }),
        ) { entry ->
            val studentId = entry.studentId() ?: return@composable
            StudentPageScreen(
                studentId = studentId,
                onDone = { navController.popBackStack() },
                onQuickNote = { id -> navController.navigate(Routes.quickNote(id)) },
            )
        }

        // ── Student feedback (built) ───────────────────────────────────────
        composable(
            route = Routes.QUICK_NOTE,
            arguments = listOf(navArgument(Routes.Args.STUDENT_ID) { type = NavType.StringType }),
        ) { entry ->
            val studentId = entry.studentId() ?: return@composable
            QuickNoteScreen(
                studentId = studentId,
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.FEEDBACK,
            arguments = listOf(
                navArgument(Routes.Args.STUDENT_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            FeedbackScreen(
                onDone = { navController.popBackStack() },
                studentIdPreset = entry.studentId(),
            )
        }

        // ── Attendance (built) ─────────────────────────────────────────────
        composable(
            route = Routes.ATTENDANCE,
            arguments = listOf(
                navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType },
                navArgument(Routes.Args.DATE_EPOCH_DAY) {
                    type = NavType.LongType
                    defaultValue = LocalDate.now().toEpochDay()
                },
            ),
        ) { entry ->
            val classId = entry.classId() ?: return@composable
            val date = entry.dateEpochDay() ?: LocalDate.now().toEpochDay()
            AttendanceScreen(
                classId = classId,
                dateEpochDay = date,
                onDone = { navController.popBackStack() },
            )
        }

        // ── Not yet built: honest placeholders behind live routes ──────────
        composable(Routes.EXPORT) {
            ModulePlaceholderScreen(
                title = "Reports & backup",
                moduleName = "export-backup",
                whatItWillDo = "Monthly summaries, CSV/Excel/PDF export, and the manual backup/restore zip.",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
