package com.radley.latch.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.radley.latch.R
import com.radley.latch.di.ServiceLocator
import com.radley.latch.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps protection alive and provides the fallback detector.
 *
 * Three jobs:
 *  1. Host the screen-off receiver. `ACTION_SCREEN_OFF` cannot be declared in the manifest, so
 *     something long-lived has to be registered to hear it — and it is what clears sessions.
 *  2. Watch whether the accessibility service is still enabled. One UI turns accessibility
 *     services off after updates and occasionally on its own, which would otherwise silently
 *     end all protection with no visible sign.
 *  3. Poll `UsageStatsManager` as a fallback while accessibility is off. Slower and less
 *     precise, but better than nothing.
 */
class LockWatchService : LifecycleService() {

    private val usageStatsManager: UsageStatsManager? by lazy {
        getSystemService(UsageStatsManager::class.java)
    }

    private val resolver by lazy {
        ForegroundAppResolver(ignoredPackages = setOf(packageName))
    }

    private var lastForegroundPackage: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Unconditional: every app re-locks, whatever its individual policy said.
                ServiceLocator.sessions.onScreenOff()
                lastForegroundPackage = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        startWatchLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Restart if the system kills us; protection silently stopping is the failure mode
        // this whole service exists to avoid.
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    private fun startWatchLoop() = lifecycleScope.launch {
        while (isActive) {
            val accessibilityOn = LatchPermissions.isAccessibilityEnabled(this@LockWatchService)
            when {
                accessibilityOn -> {
                    // The event-driven path is handling detection; just re-check occasionally.
                    lastForegroundPackage = null
                    delay(ACCESSIBILITY_HEALTHCHECK_MILLIS)
                }

                LatchPermissions.hasUsageAccess(this@LockWatchService) -> {
                    pollForegroundApp()
                    delay(POLL_INTERVAL_MILLIS)
                }

                else -> delay(NO_PERMISSION_BACKOFF_MILLIS)
            }
        }
    }

    private fun pollForegroundApp() {
        val manager = usageStatsManager ?: return
        val end = System.currentTimeMillis()
        val events = runCatching { manager.queryEvents(end - POLL_WINDOW_MILLIS, end) }
            .getOrNull() ?: return

        val foreground = buildList {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    add(ForegroundEvent(event.packageName, event.timeStamp))
                }
            }
        }

        val current = resolver.resolve(foreground) ?: return
        // The poll window overlaps between ticks, so the same event is seen several times.
        if (current == lastForegroundPackage) return
        lastForegroundPackage = current

        LockEnforcer.handle(this, current)
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watch_channel_name),
                // MIN so it collapses into the status bar without a persistent icon nagging.
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.watch_channel_description)
                setShowBadge(false)
            },
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watch_notification_title))
            .setContentText(getString(R.string.watch_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ refuses to start a foreground service without a declared type.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "latch_watch"
        private const val NOTIFICATION_ID = 1001

        private const val POLL_INTERVAL_MILLIS = 250L
        private const val POLL_WINDOW_MILLIS = 2_000L
        private const val ACCESSIBILITY_HEALTHCHECK_MILLIS = 30_000L
        private const val NO_PERMISSION_BACKOFF_MILLIS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, LockWatchService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LockWatchService::class.java)) }
        }
    }
}
