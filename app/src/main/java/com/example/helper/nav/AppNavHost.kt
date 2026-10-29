package com.example.helper.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.helper.attendance.ui.AttendanceScreen
import com.example.helper.exportbackup.ui.ExportScreen
import com.example.helper.examsresults.examListViewModel
import com.example.helper.examsresults.examResultViewModel
import com.example.helper.examsresults.examSetupViewModel
import com.example.helper.examsresults.markEntryViewModel
import com.example.helper.examsresults.ui.ExamDetailScreen
import com.example.helper.examsresults.ui.ExamListScreen
import com.example.helper.examsresults.ui.ExamSetupScreen
import com.example.helper.examsresults.ui.MarkEntryScreen
import com.example.helper.homework.ui.HomeworkScreen
import com.example.helper.motion.MotionNav
import com.example.helper.motion.SharedMotionHost
import com.example.helper.parents.ui.ParentsScreen
import com.example.helper.roster.ui.classlist.ClassListScreen
import com.example.helper.roster.ui.studentlist.StudentListScreen
import com.example.helper.roster.ui.studentpage.StudentPageScreen
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    SharedMotionHost {
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = modifier,
            enterTransition = {
                MotionNav.enter(this, initialState, targetState)
            },
            exitTransition = {
                MotionNav.exit(this, initialState, targetState)
            },
            popEnterTransition = {
                MotionNav.popEnter(this, initialState, targetState)
            },
            popExitTransition = {
                MotionNav.popExit(this, initialState, targetState)
            },
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
                onOpenExams = { cid -> navController.navigate(Routes.examsList(cid)) },
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

        // ── Exams & Results ──────────────────────────────────────────────
        composable(
            route = Routes.EXAMS_LIST,
            arguments = listOf(navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType }),
        ) { entry ->
            val classId = entry.classId() ?: return@composable
            ExamListScreen(
                viewModel = examListViewModel(classId),
                onCreateExam = { navController.navigate(Routes.examCreate(classId)) },
                onOpenExam = { examId, examClassId -> navController.navigate(Routes.examDetail(examId, examClassId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EXAM_CREATE,
            arguments = listOf(navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType }),
        ) { entry ->
            val classId = entry.classId() ?: return@composable
            ExamSetupScreen(
                viewModel = examSetupViewModel(),
                classId = classId,
                onCreated = { examId ->
                    navController.navigate(Routes.examDetail(examId, classId)) {
                        popUpTo(Routes.EXAM_CREATE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EXAM_DETAIL,
            arguments = listOf(
                navArgument(Routes.Args.EXAM_ID) { type = NavType.StringType },
                navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val examId = entry.arguments?.getString(Routes.Args.EXAM_ID) ?: return@composable
            val classId = entry.classId() ?: return@composable
            ExamDetailScreen(
                viewModel = examResultViewModel(examId, classId),
                onEnterMarks = { subjectId ->
                    navController.navigate(Routes.examMarkEntry(examId, classId, subjectId))
                },
                onStudentClick = { studentId ->
                    navController.navigate(Routes.student(studentId))
                },
                onExamDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.EXAM_MARK_ENTRY,
            arguments = listOf(
                navArgument(Routes.Args.EXAM_ID) { type = NavType.StringType },
                navArgument(Routes.Args.CLASS_ID) { type = NavType.StringType },
                navArgument(Routes.Args.SUBJECT_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val examId = entry.arguments?.getString(Routes.Args.EXAM_ID) ?: return@composable
            val classId = entry.classId() ?: return@composable
            val subjectId = entry.subjectId() ?: return@composable
            MarkEntryScreen(
                viewModel = markEntryViewModel(examId, classId, subjectId),
                onBack = { navController.popBackStack() },
                onViewResults = { navController.popBackStack() },
                onSelectSubject = { newSubjectId ->
                    navController.navigate(Routes.examMarkEntry(examId, classId, newSubjectId)) {
                        popUpTo(Routes.EXAM_MARK_ENTRY) { inclusive = true }
                    }
                },
            )
        }

        // ── Not yet built: honest placeholders behind live routes ──────────
        composable(Routes.EXPORT) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
    }
}
}
