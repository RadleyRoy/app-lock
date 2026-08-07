package com.radley.latch.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.radley.latch.ui.lock.PinEntry
import com.radley.latch.ui.lock.PinKeypad
import com.radley.latch.ui.theme.Ash
import com.radley.latch.ui.theme.Bone
import com.radley.latch.ui.theme.Clay
import com.radley.latch.ui.theme.Ember
import com.radley.latch.ui.theme.Slate
import com.radley.latch.ui.theme.Surface1

/** Enter a PIN, then confirm it. Reuses the real keypad so setup feels like unlocking. */
@Composable
fun SetPinScreen(
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf(PinEntry()) }
    var mismatch by remember { mutableStateOf(false) }

    val confirming = first != null

    fun submit(pin: String) {
        if (!confirming) {
            first = pin
            entry = PinEntry()
            return
        }
        if (pin == first) {
            onPinSet(pin)
        } else {
            mismatch = true
            first = null
            entry = entry.withError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (confirming) "Confirm your PIN" else "Choose a PIN",
                style = MaterialTheme.typography.headlineMedium,
                color = Bone,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = when {
                    mismatch -> "Those did not match. Start again."
                    confirming -> "Enter it once more"
                    else -> "Used when face and fingerprint are not available"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (mismatch) Ember else Ash,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(entry.length) { index ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    entry.error -> Ember
                                    index < entry.digits.length -> Clay
                                    else -> Slate.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onCancel) { Text("Cancel", color = Ash) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Surface1.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            PinKeypad(
                onDigit = { digit ->
                    mismatch = false
                    entry = entry.append(digit, System.currentTimeMillis())
                    if (entry.isComplete) submit(entry.digits)
                },
                onBackspace = { entry = entry.backspace(System.currentTimeMillis()) },
                onBiometric = {},
                showBiometric = false,
                enabled = true,
                randomized = false,
            )
        }
    }
}
