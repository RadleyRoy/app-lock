package com.radley.latch.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radley.latch.ui.theme.Ash
import com.radley.latch.ui.theme.Bone
import com.radley.latch.ui.theme.Clay
import com.radley.latch.ui.theme.KeypadDigitStyle
import com.radley.latch.ui.theme.Surface1

private const val KEY_SIZE_DP = 76

/**
 * The keypad, deliberately confined to the bottom of the screen.
 *
 * The S24+ is a 6.7" phone: anything interactive above roughly 55% of the screen height cannot
 * be reached with the thumb of the hand holding it. Every control here sits inside that arc.
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    showBiometric: Boolean,
    enabled: Boolean,
    randomized: Boolean,
    modifier: Modifier = Modifier,
) {
    // Shuffled once per mount, not per recomposition — a layout that reshuffled under the
    // finger between digits would be unusable rather than merely secure.
    val digits = remember(randomized) {
        if (randomized) ('0'..'9').shuffled() else ('1'..'9') + '0'
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in 0 until 3) {
            KeypadRow {
                for (column in 0 until 3) {
                    val digit = digits[row * 3 + column]
                    DigitKey(digit = digit, enabled = enabled, onClick = { onDigit(digit) })
                }
            }
        }

        KeypadRow {
            if (showBiometric) {
                IconKey(
                    icon = Icons.Filled.Fingerprint,
                    description = "Unlock with face or fingerprint",
                    tint = Clay,
                    enabled = true, // stays live during PIN cooldown
                    onClick = onBiometric,
                )
            } else {
                KeySpacer()
            }

            DigitKey(digit = digits[9], enabled = enabled, onClick = { onDigit(digits[9]) })

            IconKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                description = "Delete",
                tint = Ash,
                enabled = enabled,
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onClick: () -> Unit) {
    Key(enabled = enabled, description = digit.toString(), onClick = onClick) {
        Text(text = digit.toString(), style = KeypadDigitStyle, color = Bone)
    }
}

@Composable
private fun IconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Key(enabled = enabled, description = description, onClick = onClick, background = Color.Transparent) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Key(
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    background: Color = Surface1,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(KEY_SIZE_DP.dp)
            .clip(CircleShape)
            .background(background)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = (KEY_SIZE_DP / 2).dp, color = Clay),
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun KeySpacer() {
    Box(modifier = Modifier.size(KEY_SIZE_DP.dp))
}
