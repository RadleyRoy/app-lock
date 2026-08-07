package com.radley.latch.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radley.latch.ui.theme.Ash
import com.radley.latch.ui.theme.Bone
import com.radley.latch.ui.theme.Clay
import com.radley.latch.ui.theme.Cocoa
import com.radley.latch.ui.theme.Ember
import com.radley.latch.ui.theme.Ink
import com.radley.latch.ui.theme.Slate
import com.radley.latch.ui.theme.Surface1

/** Everything the lock screen needs to render, so it can be previewed without a real lock. */
data class LockScreenState(
    val appLabel: String,
    val appIcon: ImageBitmap?,
    val entry: PinEntry,
    val attemptsRemaining: Int?,
    val cooldownSecondsLeft: Int?,
    val cooldownProgress: Float,
    val biometricAvailable: Boolean,
    val intruderCaptured: Boolean,
)

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
                    cooldownProgress = if (inCooldown) state.cooldownProgress else 0f,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = state.appLabel,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = Bone,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = promptFor(state),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = if (state.entry.error || inCooldown) Ember else Ash,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                PinDots(
                    length = state.entry.length,
                    filled = state.entry.digits.length,
                    error = state.entry.error,
                )

                AnimatedVisibility(
                    visible = state.intruderCaptured,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "Photo saved",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Slate,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Surface1.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
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
    state.cooldownSecondsLeft != null ->
        "Too many attempts · wait ${state.cooldownSecondsLeft}s, or use face or fingerprint"

    state.entry.error && state.attemptsRemaining != null ->
        "Wrong PIN · ${state.attemptsRemaining} ${if (state.attemptsRemaining == 1) "attempt" else "attempts"} left"

    state.entry.error -> "Wrong PIN"
    else -> "Enter PIN to open"
}

/** App icon on a clay disc, with the cooldown countdown drawn as a ring around it. */
@Composable
private fun AppBadge(icon: ImageBitmap?, cooldownProgress: Float) {
    val progress by animateFloatAsState(targetValue = cooldownProgress, label = "cooldown")

    Box(contentAlignment = Alignment.Center) {
        if (progress > 0f) {
            Canvas(modifier = Modifier.size(108.dp)) {
                drawArc(
                    color = Ember,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(88.dp)
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

@Composable
private fun PinDots(length: Int, filled: Int, error: Boolean) {
    val shake = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error) {
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    0f at 0
                    -14f at 50
                    14f at 110
                    -9f at 170
                    9f at 230
                    0f at 320
                },
            )
        } else {
            shake.snapTo(0f)
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.graphicsLayer { translationX = shake.value },
    ) {
        repeat(length) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error && isFilled -> Ember
                            isFilled -> Clay
                            else -> Slate.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}
