package com.example.helper.nav

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/**
 * The route table — the single owned file where route strings and argument
 * parsing live (app-shell card: features never see a route, only typed
 * values passed through callbacks). Add every route here first, even while
 * its module is still unbuilt.
 *
 * Parsing helpers read typed values back out of a [NavBackStackEntry]; the
 * NavHost is the only caller.
 */
object Routes {

    // ── Bottom-bar tabs (exactly four, README rule 3) ─────────────────────
    const val TODAY = "today"
    const val STUDENTS = "students"
    const val HOMEWORK = "homework"
    const val PARENTS = "parents"

    val tabs: List<String> = listOf(TODAY, STUDENTS, HOMEWORK, PARENTS)

    /** True when the bottom bar belongs on [route] (tab roots only). */
    fun isTabRoot(route: String?): Boolean = route in tabs

    // ── Detail routes ─────────────────────────────────────────────────────
    /** Students tab → one class's student list (roster). */
    const val CLASS_STUDENTS = "class/{classId}"
    fun classStudents(classId: String) = "class/$classId"

    /** Student page (roster). */
    const val STUDENT = "student/{studentId}"
    fun student(studentId: String) = "student/$studentId"

    /** Commit-on-tap quick note for one student (student-feedback). */
    const val QUICK_NOTE = "student/{studentId}/quick-note"
    fun quickNote(studentId: String) = "student/$studentId/quick-note"

    /** Full searchable feedback list; [studentId] optionally presets it. */
    const val FEEDBACK = "feedback?studentId={studentId}"
    fun feedback(studentId: String? = null) =
        if (studentId != null) "feedback?studentId=$studentId" else "feedback"

    /**
     * Attendance screen for one class-day (attendance module). The date rides
     * as epoch-day Long (the repositories' date currency); defaults to today
     * so a route without a date still lands on the smart default.
     */
    const val ATTENDANCE = "attendance/{classId}?dateEpochDay={dateEpochDay}"
    fun attendance(classId: String, dateEpochDay: Long) = "attendance/$classId?dateEpochDay=$dateEpochDay"

    /** Reports & backup destination (export-backup module). */
    const val EXPORT = "export"

    // ── Exams & Results ──────────────────────────────────────────────────
    /** Exam list for one class; the class rides along so the FAB can create. */
    const val EXAMS_LIST = "exams/{classId}"
    fun examsList(classId: String) = "exams/$classId"
    const val EXAM_CREATE = "exam/create/{classId}"
    fun examCreate(classId: String) = "exam/create/$classId"
    const val EXAM_DETAIL = "exam/{examId}/class/{classId}"
    fun examDetail(examId: String, classId: String) = "exam/$examId/class/$classId"
    const val EXAM_MARK_ENTRY = "exam/{examId}/class/{classId}/marks/{subjectId}"
    fun examMarkEntry(examId: String, classId: String, subjectId: String) =
        "exam/$examId/class/$classId/marks/$subjectId"

    // ── Argument names + parsing (NavHost-only) ───────────────────────────
    object Args {
        const val CLASS_ID = "classId"
        const val STUDENT_ID = "studentId"
        const val DATE_EPOCH_DAY = "dateEpochDay"
        const val EXAM_ID = "examId"
        const val SUBJECT_ID = "subjectId"
    }
}

/** Tab-safe navigate: one instance per tab (launchSingleTop), tab roots
 * stay alive under the back stack (saveState/restoreState), Back walks
 * the last-visited tab first (popUpTo start destination). */
fun NavHostController.navigateToTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Routes.TODAY) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// Argument parsing — callers are the NavHost composable lambdas only.
fun NavBackStackEntry.classId(): String? = arguments?.getString(Routes.Args.CLASS_ID)
fun NavBackStackEntry.studentId(): String? = arguments?.getString(Routes.Args.STUDENT_ID)
fun NavBackStackEntry.subjectId(): String? = arguments?.getString(Routes.Args.SUBJECT_ID)
fun NavBackStackEntry.dateEpochDay(): Long? = arguments?.let { args ->
    if (args.containsKey(Routes.Args.DATE_EPOCH_DAY)) args.getLong(Routes.Args.DATE_EPOCH_DAY) else null
}
