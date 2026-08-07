package com.radley.applock.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps BiometricPrompt for the unlock screen.
 *
 * ## Why BIOMETRIC_WEAK
 *
 * On Samsung devices face recognition is a Class 2 (WEAK) biometric; only the ultrasonic
 * fingerprint is Class 3 (STRONG). Android will not offer a WEAK modality to a prompt that
 * asked for BIOMETRIC_STRONG, so requesting STRONG would rule out face unlock outright.
 *
 * **Measured on a Galaxy S24+: face never appears anyway.** Samsung does not publish face
 * recognition to third-party BiometricPrompt at all — the system lock screen runs face and
 * fingerprint concurrently only because it has direct HAL access, which no normal app gets.
 * In practice this prompt offers fingerprint alone, and the UI copy says so rather than
 * promising something the hardware will not do.
 *
 * WEAK is still the right request: it costs nothing, and face works automatically on any device
 * that does expose it.
 *
 * The cost is that a WEAK authentication cannot unwrap a Keystore key, so there is no
 * CryptoObject. That is acceptable here because the biometric gates access to a screen rather
 * than releasing a decryption key, and the PIN is stored as a PBKDF2 hash either way.
 *
 * DEVICE_CREDENTIAL is deliberately not combined with WEAK: that pairing is rejected outright,
 * and the app has its own PIN as the fallback anyway.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    enum class Availability { AVAILABLE, NONE_ENROLLED, UNAVAILABLE }

    fun availability(): Availability =
        when (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    fun canAuthenticate(): Boolean = availability() == Availability.AVAILABLE

    /**
     * @param onFallback the user dismissed the sheet — hand them the PIN pad rather than
     *   closing the lock screen, which would drop them into the protected app.
     */
    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
        onFailedAttempt: () -> Unit = {},
    ) {
        if (!canAuthenticate()) {
            onFallback()
            return
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onSuccess()

            /** A rejected face/finger. The sheet stays up and lets them retry. */
            override fun onAuthenticationFailed() = onFailedAttempt()

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                when (code) {
                    // "Use PIN", back gesture, or the sheet being cancelled because another
                    // window took focus. All mean: show the pad, do not dismiss.
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    -> onFallback()

                    // Too many failures: the OS has locked the sensor out. The PIN is now the
                    // only way in, which is exactly what a backup PIN is for.
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                    -> onFallback()

                    else -> onFallback()
                }
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            callback,
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText("Use PIN")
                .setConfirmationRequired(false) // zero-tap face unlock
                .build(),
        )
    }
}
