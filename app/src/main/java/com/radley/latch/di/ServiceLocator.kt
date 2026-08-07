package com.radley.latch.di

import android.content.Context
import com.radley.latch.data.InstalledAppsRepository
import com.radley.latch.data.IntruderRepository
import com.radley.latch.data.SettingsStore
import com.radley.latch.lock.IntruderPolicy
import com.radley.latch.lock.LockGate
import com.radley.latch.lock.RelockPolicy
import com.radley.latch.lock.SessionManager

/**
 * Hand-rolled DI. Hilt would buy little here: the graph is five singletons, and the awkward
 * consumers are an AccessibilityService and a Service, neither of which is ViewModel-scoped.
 *
 * The `@Volatile` snapshots exist because [LockGate] is consulted on the main thread from an
 * accessibility callback that fires on every window change. Reading DataStore there — even a
 * cached read — would put suspending work on the hot path of every single app switch, so
 * [com.radley.latch.LatchApp] mirrors the relevant flows into plain fields instead.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(appContext) }
    val intruders: IntruderRepository by lazy { IntruderRepository(appContext, settings) }
    val sessions: SessionManager by lazy { SessionManager() }

    val lockGate: LockGate by lazy {
        LockGate(
            sessions = sessions,
            ownPackage = appContext.packageName,
            protectedPackages = { lockedPackages },
            protectionEnabled = { protectionEnabled },
        )
    }

    @Volatile
    var lockedPackages: Set<String> = emptySet()

    @Volatile
    var protectionEnabled: Boolean = true

    @Volatile
    var relockPolicy: RelockPolicy = RelockPolicy.DEFAULT

    @Volatile
    var intruderThreshold: Int = IntruderPolicy.DEFAULT_THRESHOLD

    @Volatile
    var randomizeKeypad: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
