package com.radley.applock.di

import android.content.Context
import com.radley.applock.data.InstalledAppsRepository
import com.radley.applock.data.IntruderRepository
import com.radley.applock.data.SettingsStore
import com.radley.applock.lock.IntruderPolicy
import com.radley.applock.lock.LockGate
import com.radley.applock.lock.RelockRule
import com.radley.applock.lock.SessionManager

/**
 * Hand-rolled DI. Hilt would buy little here: the graph is five singletons, and the awkward
 * consumers are an AccessibilityService and a Service, neither of which is ViewModel-scoped.
 *
 * The `@Volatile` snapshots exist because [LockGate] is consulted on the main thread from an
 * accessibility callback that fires on every window change. Reading DataStore there — even a
 * cached read — would put suspending work on the hot path of every single app switch, so
 * [com.radley.applock.AppLockApp] mirrors the relevant flows into plain fields instead.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(appContext) }
    val intruders: IntruderRepository by lazy { IntruderRepository(appContext, settings) }
    val sessions: SessionManager by lazy { SessionManager() }

    /**
     * Process-scoped, not per-lock-screen: the lock activity is finished whenever the user
     * leaves it, so a per-activity counter would let anyone reset the cooldown and the failed
     * attempt count by tapping HOME between guesses.
     */
    val intruderPolicy: IntruderPolicy by lazy { IntruderPolicy { intruderThreshold } }

    val lockGate: LockGate by lazy {
        LockGate(
            sessions = sessions,
            protectedPackages = { lockedPackages },
            protectionEnabled = { protectionEnabled },
        )
    }

    /**
     * True while the lock screen is on screen. Read by [com.radley.applock.lock.LockEnforcer]
     * to refuse starting a lock over a lock — which matters now that AppLock can protect
     * itself, and also stops the two detectors racing to launch the same lock twice.
     */
    @Volatile
    var lockScreenVisible: Boolean = false

    @Volatile
    var lockedPackages: Set<String> = emptySet()

    @Volatile
    var protectionEnabled: Boolean = true

    @Volatile
    var relockRule: RelockRule = RelockRule.DEFAULT

    @Volatile
    var intruderThreshold: Int = IntruderPolicy.DEFAULT_THRESHOLD

    @Volatile
    var randomizeKeypad: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
