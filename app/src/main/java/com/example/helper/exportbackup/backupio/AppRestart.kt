package com.example.helper.exportbackup.backupio

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlin.system.exitProcess

/**
 * Restart helper for the one operation that cannot be done in-process: a
 * restore replaces the Room DB file, so every repository a ViewModel holds
 * belongs to a closed database. The screen calls this from a user-visible
 * "Restore complete" state; the foreground activity launches the fresh task
 * and the old process exits.
 *
 * Deliberately no process-restart dependency (card excludes third-party
 * helpers around this dance): a launch intent, a short delay, then exit.
 */
object AppRestart {

    fun restart(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, RESTART_DELAY_MILLIS)
    }

    private const val RESTART_DELAY_MILLIS = 600L
}
