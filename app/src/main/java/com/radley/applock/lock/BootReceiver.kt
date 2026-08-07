package com.radley.applock.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the watcher back after a reboot, so protection resumes without the user having to
 * remember to open the app. An app lock that quietly stops working after a restart is worse
 * than no app lock, because you believe you are covered.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"

        if (!relevant) return
        if (!AppLockPermissions.canLock(context)) return

        LockWatchService.start(context)
    }
}
