package com.example.helper.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.helper.attendance.ui.AttendanceScreen
import com.example.helper.exportbackup.ui.ExportScreen
import com.example.helper.homework.ui.HomeworkScreen
import com.example.helper.roster.ui.classlist.ClassListScreen
import com.example.helper.roster.ui.studentlist.StudentListScreen
import com.example.helper.roster.ui.studentpage.StudentPageScreen
import com.example.helper.parents.ui.ParentsScreen
import com.example.helper.studentfeedback.ui.FeedbackScreen
import com.example.helper.studentfeedback.ui.QuickNoteScreen
import com.example.helper.today.TodayScreen
import com.example.helper.theme.Motion
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
        // Navigation motion (see Motion): tab-root ↔ tab-root is a peer
        // switch → quick M3 fade-through; anything else is a push/pop →
        // directional M3 shared axis (forward slides from the right, back
        // from the left, mirrors automatically through slideIntoContainer).
        enterTransition = { enterMotion(initialState, targetState) },
        exitTransition = { exitMotion(initialState, targetState) },
        popEnterTransition = { popEnterMotion(initialState, targetState) },
        popExitTransition = { popExitMotion(initialState, targetState) },
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
                onOpenExport = { navController.navigate(Routes.EXPORT) },
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
            ExportScreen(onBack = { navController.popBackStack() })
        }
    }
}

// ── Navigation motion ───────────────────────────────────────────────────────

private fun NavBackStackEntry.isTabRoot(): Boolean = Routes.isTabRoot(destination.route)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterMotion(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): EnterTransition =
    if (from.isTabRoot() && to.isTabRoot()) {
        // M3 fade-through: the outgoing tab fades fast, the incoming one
        // fades in after a short delay — quick and directionless on purpose,
        // since peer tabs have no hierarchy between them.
        fadeIn(tween(Motion.Duration.Medium, delayMillis = Motion.Duration.Short, easing = Motion.Easing.Decelerate))
    } else {
        // M3 shared axis, forward: new screen slides in from the right.
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(Motion.Duration.Long, easing = Motion.Easing.Standard)) +
            fadeIn(tween(Motion.Duration.Long, easing = Motion.Easing.Decelerate))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitMotion(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): ExitTransition =
    if (from.isTabRoot() && to.isTabRoot()) {
        fadeOut(tween(Motion.Duration.Short, easing = Motion.Easing.Accelerate))
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(Motion.Duration.Long, easing = Motion.Easing.Standard)) +
            fadeOut(tween(Motion.Duration.Medium, easing = Motion.Easing.Accelerate))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterMotion(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): EnterTransition =
    if (from.isTabRoot() && to.isTabRoot()) {
        fadeIn(tween(Motion.Duration.Medium, delayMillis = Motion.Duration.Short, easing = Motion.Easing.Decelerate))
    } else {
        // M3 shared axis, back: the restored screen slides in from the left.
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(Motion.Duration.Long, easing = Motion.Easing.Standard)) +
            fadeIn(tween(Motion.Duration.Long, easing = Motion.Easing.Decelerate))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitMotion(
    from: NavBackStackEntry,
    to: NavBackStackEntry,
): ExitTransition =
    if (from.isTabRoot() && to.isTabRoot()) {
        fadeOut(tween(Motion.Duration.Short, easing = Motion.Easing.Accelerate))
    } else {
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(Motion.Duration.Long, easing = Motion.Easing.Standard)) +
            fadeOut(tween(Motion.Duration.Medium, easing = Motion.Easing.Accelerate))
    }
