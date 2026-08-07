package com.radley.applock.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.radley.applock.di.ServiceLocator
import com.radley.applock.data.IntruderEvent
import com.radley.applock.lock.IntruderPolicy
import com.radley.applock.lock.OverlayShield
import com.radley.applock.security.IntruderCamera
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The lock screen.
 *
 * Hardening notes:
 *  - `FLAG_SECURE` keeps the PIN pad out of screenshots *and* out of the recents thumbnail.
 *  - `singleInstance` + `excludeFromRecents` (manifest) stop it stacking up or being swiped
 *    back into.
 *  - Back goes to the home screen. Finishing instead would reveal the protected app sitting
 *    underneath, which is the whole thing we are preventing.
 */
class LockActivity : FragmentActivity() {

    private lateinit var targetPackage: String
    private lateinit var biometrics: BiometricAuthenticator
    private lateinit var haptics: Haptics

    /** Shared across lock screens; see [ServiceLocator.intruderPolicy]. */
    private val intruderPolicy get() = ServiceLocator.intruderPolicy

    private var entry by mutableStateOf(PinEntry())
    private var cooldownSecondsLeft by mutableStateOf<Int?>(null)
    private var cooldownProgress by mutableStateOf(0f)
    private var intruderCaptured by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(false)

    /** Guards against re-firing the prompt every time the activity is resumed behind it. */
    private var biometricPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (targetPackage.isBlank()) {
            finish()
            return
        }

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        biometrics = BiometricAuthenticator(this)
        haptics = Haptics(this)
        biometricAvailable = biometrics.canAuthenticate()

        // A cooldown started by a previous lock screen still applies: the activity is finished
        // whenever the user leaves, so this is what stops HOME being used to skip the wait.
        val cooldownUntil = intruderPolicy.cooldownUntilMillis
        if (cooldownUntil > now()) runCooldown(cooldownUntil)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val label = ServiceLocator.installedApps.labelFor(targetPackage)
        val iconBitmap = runCatching {
            ServiceLocator.installedApps.iconFor(targetPackage)?.toBitmap()?.asImageBitmap()
        }.getOrNull()

        setContent {
            com.radley.applock.ui.theme.AppLockTheme {
                LockScreen(
                    state = LockScreenState(
                        appLabel = label,
                        appIcon = iconBitmap,
                        entry = entry,
                        attemptsRemaining = attemptsRemaining(),
                        cooldownSecondsLeft = cooldownSecondsLeft,
                        cooldownProgress = cooldownProgress,
                        biometricAvailable = biometricAvailable,
                        intruderCaptured = intruderCaptured,
                    ),
                    onDigit = ::onDigit,
                    onBackspace = { entry = entry.backspace(now()) },
                    onBiometric = ::promptBiometric,
                    onClose = ::goHome,
                    randomizeKeypad = ServiceLocator.randomizeKeypad,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Posted rather than called directly: onResume runs *before* the first frame is drawn,
        // so dropping the shield here would reopen the very gap it exists to cover. The post
        // lands after the first traversal, when this window is actually painting.
        window.decorView.post { OverlayShield.hide() }

        if (!biometricPromptShowing && biometricAvailable) promptBiometric()
    }

    /**
     * The safety net for the v1.0.0 lockup.
     *
     * Pressing HOME stops this activity without finishing it. Previously nothing ran here, so
     * the overlay stayed up over a screen with no lock UI on it and the phone had to be
     * rebooted. Now the shield always comes down, and the activity finishes rather than
     * lingering as a stale singleInstance task — reopening the protected app simply re-triggers
     * the lock, because no session was ever granted.
     */
    override fun onStop() {
        super.onStop()
        OverlayShield.hide()
        if (!isChangingConfigurations && !isFinishing) {
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        OverlayShield.hide()
        super.onDestroy()
    }

    private fun promptBiometric() {
        if (biometricPromptShowing) return
        biometricPromptShowing = true
        biometrics.authenticate(
            title = "Unlock ${ServiceLocator.installedApps.labelFor(targetPackage)}",
            // Not "face or fingerprint": Samsung does not publish face recognition to
            // third-party BiometricPrompt, so on this hardware only fingerprint ever appears.
            subtitle = "Use your fingerprint",
            onSuccess = {
                biometricPromptShowing = false
                unlock()
            },
            onFallback = { biometricPromptShowing = false },
        )
    }

    private fun onDigit(digit: Char) {
        if (entry.isLockedOut(now())) return
        haptics.keyPress()
        entry = entry.append(digit, now())
        // No OK button: the final digit submits.
        if (entry.isComplete) verifyPin(entry.digits)
    }

    private fun verifyPin(pin: String) = lifecycleScope.launch {
        if (ServiceLocator.settings.verifyPin(pin)) {
            haptics.success()
            intruderPolicy.onSuccess()
            unlock()
        } else {
            haptics.failure()
            entry = entry.withError()
            onFailedAttempt()
        }
    }

    private fun onFailedAttempt() {
        if (intruderPolicy.onFailure()) captureIntruder()

        if (intruderPolicy.failureCount >= IntruderPolicy.COOLDOWN_AFTER) {
            startCooldown()
        }
    }

    private fun startCooldown() {
        val until = now() + IntruderPolicy.COOLDOWN_MILLIS
        intruderPolicy.startCooldown(until)
        runCooldown(until)
    }

    private fun runCooldown(until: Long) {
        entry = entry.lockedOut(until)

        lifecycleScope.launch {
            while (true) {
                val remaining = until - now()
                if (remaining <= 0) break
                cooldownSecondsLeft = ((remaining + 999) / 1000).toInt()
                cooldownProgress = remaining.toFloat() / IntruderPolicy.COOLDOWN_MILLIS
                delay(200)
            }
            cooldownSecondsLeft = null
            cooldownProgress = 0f
            entry = entry.cleared()
        }
    }

    private fun captureIntruder() = lifecycleScope.launch {
        val camera = IntruderCamera(this@LockActivity)
        if (!camera.hasPermission()) return@launch

        val file = ServiceLocator.intruders.newPhotoFile()
        if (!camera.capture(this@LockActivity, file)) return@launch

        ServiceLocator.intruders.record(
            IntruderEvent(
                id = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                targetPackage = targetPackage,
                targetLabel = ServiceLocator.installedApps.labelFor(targetPackage),
                photoFileName = file.name,
                failedAttempts = intruderPolicy.failureCount,
            ),
        )

        intruderCaptured = true
        delay(3_000)
        intruderCaptured = false
    }

    private fun attemptsRemaining(): Int? {
        if (!entry.error) return null
        val left = IntruderPolicy.COOLDOWN_AFTER - intruderPolicy.failureCount
        return left.takeIf { it in 1 until IntruderPolicy.COOLDOWN_AFTER }
    }

    private fun unlock() {
        ServiceLocator.sessions.grant(targetPackage, ServiceLocator.relockRule)
        OverlayShield.hide()
        // No success screen: dismiss the moment auth returns. Theme.AppLock.Lock nulls the
        // window animation, so there is nothing to override here.
        finish()
    }

    private fun goHome() {
        OverlayShield.hide()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        // finishAndRemoveTask, not finish: finishing a singleInstance activity hands control
        // back to the task underneath — the protected app — which immediately re-triggers the
        // lock and reads as "back did nothing".
        finishAndRemoveTask()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val EXTRA_PACKAGE = "com.radley.applock.extra.PACKAGE"

        /**
         * No `FLAG_ACTIVITY_CLEAR_TASK`: `singleInstance` plus an empty `taskAffinity` already
         * give this activity a task of its own, and CLEAR_TASK on top of that interacts badly
         * with singleInstance — it was part of why back left a stale lock screen behind.
         */
        fun intent(context: Context, packageName: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
    }
}
