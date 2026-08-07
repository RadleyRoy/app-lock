package com.radley.latch.ui.lock

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Distinct vibration patterns for keypress, success and failure, so the outcome of an unlock
 * is legible through the hand before you look at the screen.
 *
 * Compose's LocalHapticFeedback only exposes a couple of generic constants, which is not
 * enough to tell success and failure apart.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val available = vibrator?.hasVibrator() == true

    /** A short, light tick under each digit. */
    fun keyPress() = play(VibrationEffect.createOneShot(12, 60))

    /** Double pulse: "you are in". */
    fun success() = playWaveform(longArrayOf(0, 18, 60, 26), intArrayOf(0, 90, 0, 130))

    /** One sharp, heavier buzz: distinctly not a success. */
    fun failure() = playWaveform(longArrayOf(0, 55, 70, 55), intArrayOf(0, 200, 0, 200))

    private fun play(effect: VibrationEffect) {
        if (!available) return
        runCatching { vibrator?.vibrate(effect) }
    }

    private fun playWaveform(timings: LongArray, amplitudes: IntArray) {
        if (!available) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }
}
