package com.radley.applock.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radley.applock.lock.IntruderPolicy
import com.radley.applock.lock.RelockPolicy
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Cocoa
import com.radley.applock.ui.theme.Ember
import com.radley.applock.ui.theme.Slate
import com.radley.applock.ui.theme.Surface1

@Composable
fun SettingsScreen(
    protectionEnabled: Boolean,
    relockPolicy: RelockPolicy,
    intruderThreshold: Int,
    randomizeKeypad: Boolean,
    intruderCount: Int,
    onProtectionEnabledChange: (Boolean) -> Unit,
    onRelockPolicyChange: (RelockPolicy) -> Unit,
    onIntruderThresholdChange: (Int) -> Unit,
    onRandomizeKeypadChange: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onClearIntruderLog: () -> Unit,
    contentPadding: PaddingValues,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Section("Protection")

        SettingCard {
            ToggleRow(
                title = "Lock protected apps",
                subtitle = if (protectionEnabled) {
                    "AppLock is guarding your selected apps"
                } else {
                    "Everything is unlocked until you turn this back on"
                },
                checked = protectionEnabled,
                onCheckedChange = onProtectionEnabledChange,
            )
        }

        Section("Re-lock")

        SettingCard {
            Text(
                "How soon a protected app locks again after you leave it. The clock starts when you switch away, never while you are still using it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RelockPolicy.entries.forEach { policy ->
                ChoiceRow(
                    label = policy.displayName(),
                    selected = policy == relockPolicy,
                    onClick = { onRelockPolicyChange(policy) },
                )
            }
        }

        Section("Intruder photos")

        SettingCard {
            Text(
                "After this many wrong PINs in a row, AppLock silently takes one front-camera photo and logs which app was targeted. Android shows its green camera indicator while this happens — that is the system, and no app can suppress it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            listOf(
                IntruderPolicy.DISABLED to "Off",
                3 to "After 3 wrong attempts",
                5 to "After 5 wrong attempts",
            ).forEach { (value, label) ->
                ChoiceRow(
                    label = label,
                    selected = value == intruderThreshold,
                    onClick = { onIntruderThresholdChange(value) },
                )
            }

            if (intruderCount > 0) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmClear = true }) {
                    Text("Delete all $intruderCount photos", color = Ember)
                }
            }
        }

        Section("Lock screen")

        SettingCard {
            ToggleRow(
                title = "Shuffle the keypad",
                subtitle = "Randomises the digit layout on every unlock, so smudges and shoulder-surfing give nothing away. Slower to use.",
                checked = randomizeKeypad,
                onCheckedChange = onRandomizeKeypadChange,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onChangePin) { Text("Change PIN", color = Clay) }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "AppLock has no internet permission. Nothing it records can leave this device.",
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
        )

        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Surface1,
            title = { Text("Delete all intruder photos?", color = Bone) },
            text = {
                Text(
                    "This permanently removes $intruderCount photo${if (intruderCount == 1) "" else "s"} and their log entries. It cannot be undone.",
                    color = Ash,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearIntruderLog()
                    confirmClear = false
                }) { Text("Delete", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Ash) }
            },
        )
    }
}

private fun RelockPolicy.displayName(): String = when (this) {
    RelockPolicy.IMMEDIATELY -> "Immediately"
    RelockPolicy.AFTER_10_SECONDS -> "After 10 seconds"
    RelockPolicy.AFTER_30_SECONDS -> "After 30 seconds"
    RelockPolicy.AFTER_1_MINUTE -> "After 1 minute"
    RelockPolicy.AFTER_5_MINUTES -> "After 5 minutes"
    RelockPolicy.UNTIL_SCREEN_OFF -> "When the screen turns off"
}

@Composable
private fun Section(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Clay,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Bone)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
        Spacer(Modifier.height(0.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bone,
                checkedTrackColor = Clay,
                uncheckedThumbColor = Slate,
                uncheckedTrackColor = Cocoa.copy(alpha = 0.4f),
                uncheckedBorderColor = Slate,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Bone else Ash,
        )
        if (selected) Text("✓", color = Clay)
    }
}
