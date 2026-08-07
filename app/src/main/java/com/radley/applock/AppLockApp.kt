package com.radley.applock

import android.app.Application
import com.radley.applock.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppLockApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        mirrorSettingsIntoMemory()
    }

    /**
     * Keeps [ServiceLocator]'s volatile snapshots in step with DataStore.
     *
     * The accessibility callback has to answer "lock or not?" synchronously on the main thread
     * for every window change on the device, so it reads these fields rather than suspending
     * on a store lookup. This collector is the only writer.
     */
    private fun mirrorSettingsIntoMemory() = with(ServiceLocator) {
        settings.lockedPackages.onEach { lockedPackages = it }.launchIn(scope)
        settings.protectionEnabled.onEach { protectionEnabled = it }.launchIn(scope)
        settings.relockPolicy.onEach { relockPolicy = it }.launchIn(scope)
        settings.intruderThreshold.onEach { intruderThreshold = it }.launchIn(scope)
        settings.randomizeKeypad.onEach { randomizeKeypad = it }.launchIn(scope)

        // Prime the snapshots before the first window event can arrive, so a lock is never
        // missed in the window between process start and the first flow emission.
        scope.launch {
            lockedPackages = settings.currentLockedPackages()
        }
    }
}
