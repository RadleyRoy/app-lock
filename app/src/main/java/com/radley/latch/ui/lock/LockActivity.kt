package com.radley.latch.ui.lock

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
import com.radley.latch.di.ServiceLocator
import com.radley.latch.data.IntruderEvent
import com.radley.latch.lock.IntruderPolicy
import com.radley.latch.lock.OverlayShield
import com.radley.latch.security.IntruderCamera
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

    private val intruderPolicy by lazy { IntruderPolicy(ServiceLocator.intruderThreshold) }

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val label = ServiceLocator.installedApps.labelFor(targetPackage)
        val iconBitmap = runCatching {
            ServiceLocator.installedApps.iconFor(targetPackage)?.toBitmap()?.asImageBitmap()
        }.getOrNull()

        setContent {
            com.radley.latch.ui.theme.LatchTheme {
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
        // Zero-tap path: face unlock usually resolves before the user has touched anything.
        if (!biometricPromptShowing && biometricAvailable) promptBiometric()
    }

    private fun promptBiometric() {
        if (biometricPromptShowing) return
        biometricPromptShowing = true
        biometrics.authenticate(
            title = "Unlock ${ServiceLocator.installedApps.labelFor(targetPackage)}",
            subtitle = "Use your face or fingerprint",
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
        ServiceLocator.sessions.grant(targetPackage, ServiceLocator.relockPolicy)
        OverlayShield.hide()
        // No success screen: dismiss the moment auth returns. Theme.Latch.Lock nulls the
        // window animation, so there is nothing to override here.
        finish()
    }

    private fun goHome() {
        OverlayShield.hide()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val EXTRA_PACKAGE = "com.radley.latch.extra.PACKAGE"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
    }
}
