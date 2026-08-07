package com.radley.applock.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Cocoa
import com.radley.applock.ui.theme.Ember
import com.radley.applock.ui.theme.Ink
import com.radley.applock.ui.theme.Slate
import com.radley.applock.ui.theme.Surface1
import com.radley.applock.ui.theme.Taupe
import kotlinx.coroutines.delay

/** Everything the lock screen needs to render, so it can be previewed without a real lock. */
data class LockScreenState(
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val entry: PinEntry,
    val attemptsRemaining: Int?,
    val cooldownSecondsLeft: Int?,
    val cooldownProgress: Float,
    val biometricAvailable: Boolean,
)

/**
 * The lock screen — "Grace".
 *
 * The success path is deliberately unremarkable and unchanged. What this design reworks is
 * **failure**, on the argument that a security app earns or loses trust there: shaking the
 * screen at someone who mistyped their own PIN treats a slip like a break-in.
 *
 * So: the keypad settles rather than shakes, the dots dissolve rather than flash red, the
 * message stays warm rather than turning to error colour, and the lockout is an ember cooling
 * instead of a countdown scolding you. The fingerprint stays visibly live throughout, which is
 * the single most useful fact during a lockout.
 *
 * The one thing deliberately *not* softened is the failure haptic in [Haptics.failure] — with
 * no shake, that sharp double buzz is what tells you the entry failed even if you were not
 * looking at the screen.
 */
@Composable
fun LockScreen(
    state: LockScreenState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    onClose: () -> Unit,
    randomizeKeypad: Boolean,
) {
    val inCooldown = state.cooldownSecondsLeft != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .background(
                // Warm wash behind the icon, so the top of the screen is not a flat void.
                Brush.radialGradient(
                    colors = listOf(Cocoa.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(0.5f, 0.28f) * 1000f,
                    radius = 900f,
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close and go to the home screen",
                        tint = Ash,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppBadge(
                    icon = state.appIcon,
                    cooldownSecondsLeft = state.cooldownSecondsLeft,
                    cooldownProgress = state.cooldownProgress,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = state.appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = Bone,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = promptFor(state),
                    style = MaterialTheme.typography.bodyMedium,
                    // Taupe rather than an error colour: a mistyped PIN is a slip, not an alarm.
                    color = if (state.entry.error || inCooldown) Taupe else Ash,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                PinDots(
                    length = state.entry.length,
                    filled = state.entry.digits.length,
                    error = state.entry.error,
                )
            }

            SettlingSheet(settleOn = state.entry.error) {
                PinKeypad(
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                    onBiometric = onBiometric,
                    showBiometric = state.biometricAvailable,
                    enabled = !inCooldown,
                    randomized = randomizeKeypad,
                )
            }
        }
    }
}

private fun promptFor(state: LockScreenState): String = when {
    state.cooldownSecondsLeft != null -> "Taking a breath. Your fingerprint still opens it."

    // The last attempt before the cooldown gets a softer warning than a countdown of failures.
    state.entry.error && state.attemptsRemaining == 1 -> "Let's pause for a moment."

    state.entry.error -> "That wasn't it. Fingerprint still works."

    else -> "Enter PIN to open"
}

/**
 * The keypad sheet. On a wrong PIN it drops a notch and comes back — a drawer that would not
 * latch — rather than shaking, which reads as an accusation.
 */
@Composable
private fun SettlingSheet(settleOn: Boolean, content: @Composable () -> Unit) {
    val settle = remember { Animatable(0f) }

    LaunchedEffect(settleOn) {
        if (!settleOn) {
            settle.snapTo(0f)
            return@LaunchedEffect
        }
        settle.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 500
                0f at 0
                9f at 200
                0f at 500
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = settle.value * density }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Surface1.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) { content() }
}

/**
 * App icon on a clay disc. During a lockout the icon gives way to an ember that **cools** as
 * the countdown runs — desaturating toward slate rather than pulsing red, so the wait reads as
 * a pause rather than a punishment.
 */
@Composable
private fun AppBadge(icon: ImageBitmap?, cooldownSecondsLeft: Int?, cooldownProgress: Float) {
    val inCooldown = cooldownSecondsLeft != null
    val progress by animateFloatAsState(
        targetValue = if (inCooldown) cooldownProgress else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "cooldown",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (inCooldown) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "iconFade",
    )

    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {

        if (inCooldown) {
            // Cools from ember toward slate as `progress` falls to zero.
            val core = lerp(Slate, Ember, progress.coerceIn(0f, 1f))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(core, Cocoa.copy(alpha = 0.55f), Color.Transparent),
                            radius = 150f,
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cooldownSecondsLeft.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Bone,
                )
            }
        }

        if (iconAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .alpha(iconAlpha)
                    .clip(CircleShape)
                    .background(Clay.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Clay,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

/**
 * On failure the filled dots go out one at a time, 90ms apart, instead of turning red — the
 * attempt visibly unwinds rather than being rejected.
 */
@Composable
private fun PinDots(length: Int, filled: Int, error: Boolean) {
    var dissolvedTo by remember { mutableIntStateOf(filled) }

    LaunchedEffect(error, filled) {
        if (!error) {
            dissolvedTo = filled
            return@LaunchedEffect
        }
        dissolvedTo = filled
        for (remaining in filled - 1 downTo 0) {
            delay(90)
            dissolvedTo = remaining
        }
    }

    val shown = if (error) dissolvedTo else filled

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < shown) Clay else Slate.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}
