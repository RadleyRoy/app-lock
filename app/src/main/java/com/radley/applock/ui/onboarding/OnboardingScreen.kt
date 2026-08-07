package com.radley.applock.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Bone
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Cocoa
import com.radley.applock.ui.theme.Slate
import com.radley.applock.ui.theme.Surface1

/**
 * Setup wizard.
 *
 * Every card states plainly why the grant is needed. A permission list this alarming —
 * accessibility, overlay, camera — earns suspicion, and the honest answer is more persuasive
 * than a vague one.
 */
@Composable
fun OnboardingScreen(
    hasPin: Boolean,
    onSetPin: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val status by rememberPermissionStatus()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Text("Set up AppLock", style = MaterialTheme.typography.displaySmall, color = Bone)
        Spacer(Modifier.height(8.dp))
        Text(
            "Four grants and a PIN. AppLock has no internet permission, so nothing you see here can leave the phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
        )

        Spacer(Modifier.height(28.dp))

        PermissionCard(
            title = "Detect when an app opens",
            body = "AppLock reads only the package name of the app being opened — it does not ask for permission to read screen content, so it cannot see what you type.",
            actionLabel = "Open accessibility settings",
            granted = status.accessibility,
            required = true,
            onClick = { context.startActivity(PermissionIntents.accessibility()) },
        )

        PermissionCard(
            title = "Display over other apps",
            body = "Covers the protected app instantly so none of it is visible before the lock screen appears. Locking cannot work without this one.",
            actionLabel = "Allow overlay",
            granted = status.overlay,
            required = true,
            onClick = { context.startActivity(PermissionIntents.overlay(context)) },
        )

        PermissionCard(
            title = "Usage access",
            body = "A backup detector, used only if One UI ever switches the accessibility service off. Optional, but without it a disabled service means silently no protection.",
            actionLabel = "Grant usage access",
            granted = status.usageAccess,
            required = false,
            onClick = { context.startActivity(PermissionIntents.usageAccess()) },
        )

        PermissionCard(
            title = "Unrestricted battery",
            body = "Samsung's battery manager will eventually kill the watcher and stop protection with no warning. In the page that opens: Battery → Unrestricted.",
            actionLabel = "Open app info",
            granted = status.unrestrictedBattery,
            required = false,
            onClick = { context.startActivity(PermissionIntents.appDetails(context)) },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "Notifications",
                body = "Android requires a permanent, silent notification while a background watcher is running. This grant just stops it being hidden.",
                actionLabel = "Allow notifications",
                granted = status.notifications,
                required = false,
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        PermissionCard(
            title = "Camera",
            body = "Only for intruder photos, after repeated wrong PINs. Android shows its green camera dot whenever this happens. Skip it if you would rather not have the feature.",
            actionLabel = "Allow camera",
            granted = status.camera,
            required = false,
            onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        )

        PermissionCard(
            title = "Backup PIN",
            body = "Used when your fingerprint is unavailable. Stored only as a salted hash — it cannot be read back off the device.",
            actionLabel = if (hasPin) "Change PIN" else "Set a PIN",
            granted = hasPin,
            required = true,
            onClick = onSetPin,
        )

        Spacer(Modifier.height(24.dp))

        val ready = status.canLock && hasPin
        Button(
            onClick = onFinish,
            enabled = ready,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Clay,
                contentColor = Bone,
                disabledContainerColor = Surface1,
                disabledContentColor = Slate,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(if (ready) "Start protecting" else "Grant the required steps above")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    actionLabel: String,
    granted: Boolean,
    required: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (granted) Clay else Cocoa.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Granted",
                        tint = Bone,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Bone,
                modifier = Modifier.weight(1f),
            )

            if (!granted && required) {
                Text("Required", style = MaterialTheme.typography.labelSmall, color = Clay)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = Ash)

        if (!granted || actionLabel.startsWith("Change")) {
            TextButton(
                onClick = onClick,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(actionLabel, color = Clay)
            }
        }
    }
}
