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
import com.radley.applock.lock.RelockRule
import com.radley.applock.lock.RelockTrigger
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
    relockRule: RelockRule,
    intruderThreshold: Int,
    randomizeKeypad: Boolean,
    intruderCount: Int,
    appVersion: String,
    onProtectionEnabledChange: (Boolean) -> Unit,
    onRelockPolicyChange: (RelockPolicy) -> Unit,
    onRelockTriggerChange: (RelockTrigger) -> Unit,
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
                "How long an unlock lasts before AppLock asks again.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            RelockPolicy.entries.forEach { policy ->
                ChoiceRow(
                    label = policy.displayName(),
                    selected = policy == relockRule.policy,
                    onClick = { onRelockPolicyChange(policy) },
                )
            }
        }

        // Only meaningful for a real countdown: with "Immediately" and "When the screen turns
        // off" both starting points behave identically, and offering a choice that changes
        // nothing is worse than not offering it.
        if (relockRule.policy.isTimed) {
            val duration = relockRule.policy.displayName().lowercase()

            SettingCard {
                Text(
                    "When the clock starts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Bone,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "AppLock never locks an app while it is on screen. Either way, you are only " +
                        "asked again the next time you open it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                TriggerRow(
                    label = "When I open the app",
                    detail = "The unlock expires $duration after you unlock, however long you stay in the app.",
                    selected = relockRule.trigger == RelockTrigger.ON_UNLOCK,
                    onClick = { onRelockTriggerChange(RelockTrigger.ON_UNLOCK) },
                )
                TriggerRow(
                    label = "When I leave the app",
                    detail = "The unlock expires $duration after you switch away. Staying in the app keeps it alive.",
                    selected = relockRule.trigger == RelockTrigger.ON_LEAVING,
                    onClick = { onRelockTriggerChange(RelockTrigger.ON_LEAVING) },
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

        Section("About")

        SettingCard {
            Text("AppLock", style = MaterialTheme.typography.titleLarge, color = Bone)
            Spacer(Modifier.height(2.dp))
            Text(
                "Version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "Created by Radley",
                style = MaterialTheme.typography.bodyLarge,
                color = Clay,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "AppLock has no internet permission. Nothing it records can leave this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "github.com/RadleyRoy/app-lock",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        }

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
    RelockPolicy.AFTER_15_MINUTES -> "After 15 minutes"
    RelockPolicy.UNTIL_SCREEN_OFF -> "When the screen turns off"
}

@Composable
private fun TriggerRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            modifier = Modifier.padding(top = 2.dp, end = 24.dp),
        )
    }
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

/**
 * Spacing lives on the card, not between call sites. Section() only supplies padding above a
 * *header*, so two cards with no header between them used to sit flush against each other —
 * fixing it here means the next adjacent pair cannot reintroduce the gap.
 */
@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
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
