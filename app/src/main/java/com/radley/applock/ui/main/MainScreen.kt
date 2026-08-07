package com.radley.applock.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.applock.ui.apps.AppListScreen
import com.radley.applock.ui.intruders.IntruderLogScreen
import com.radley.applock.ui.onboarding.OnboardingScreen
import com.radley.applock.ui.onboarding.SetPinScreen
import com.radley.applock.ui.settings.SettingsScreen
import com.radley.applock.ui.theme.Ash
import com.radley.applock.ui.theme.Clay
import com.radley.applock.ui.theme.Cocoa
import com.radley.applock.ui.theme.Ink
import com.radley.applock.ui.theme.Surface1

private enum class Tab(val label: String, val icon: ImageVector) {
    Apps("Apps", Icons.Filled.Apps),
    Intruders("Log", Icons.Filled.PhotoCamera),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Top-level navigation.
 *
 * Three destinations and two modal screens do not justify a navigation library and its
 * back-stack semantics; plain state is easier to follow and has nothing to get out of sync.
 */
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()

    var settingPin by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Apps) }

    if (settingPin) {
        SetPinScreen(
            onPinSet = { pin ->
                viewModel.setPin(pin)
                settingPin = false
            },
            onCancel = { settingPin = false },
        )
        return
    }

    if (!onboardingComplete) {
        OnboardingScreen(
            hasPin = hasPin,
            onSetPin = { settingPin = true },
            onFinish = { viewModel.completeOnboarding() },
        )
        return
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Clay,
                            selectedTextColor = Clay,
                            indicatorColor = Cocoa.copy(alpha = 0.35f),
                            unselectedIconColor = Ash,
                            unselectedTextColor = Ash,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (tab) {
                Tab.Apps -> {
                    val state by viewModel.appList.collectAsState()
                    val query by viewModel.query.collectAsState()
                    val lockedOnly by viewModel.showLockedOnly.collectAsState()
                    val locked by viewModel.lockedPackages.collectAsState()

                    AppListScreen(
                        state = state,
                        query = query,
                        showLockedOnly = lockedOnly,
                        lockedCount = locked.size,
                        iconProvider = viewModel::iconFor,
                        onQueryChange = viewModel::setQuery,
                        onShowLockedOnlyChange = viewModel::setShowLockedOnly,
                        onToggleLock = viewModel::toggleLock,
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    )
                }

                Tab.Intruders -> {
                    val events by viewModel.intruderEvents.collectAsState()
                    IntruderLogScreen(
                        events = events,
                        photoFile = viewModel::photoFile,
                        onDelete = viewModel::deleteIntruderEvent,
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    )
                }

                Tab.Settings -> {
                    val protection by viewModel.protectionEnabled.collectAsState()
                    val policy by viewModel.relockPolicy.collectAsState()
                    val threshold by viewModel.intruderThreshold.collectAsState()
                    val randomize by viewModel.randomizeKeypad.collectAsState()
                    val events by viewModel.intruderEvents.collectAsState()

                    SettingsScreen(
                        protectionEnabled = protection,
                        relockPolicy = policy,
                        intruderThreshold = threshold,
                        randomizeKeypad = randomize,
                        intruderCount = events.size,
                        onProtectionEnabledChange = viewModel::setProtectionEnabled,
                        onRelockPolicyChange = viewModel::setRelockPolicy,
                        onIntruderThresholdChange = viewModel::setIntruderThreshold,
                        onRandomizeKeypadChange = viewModel::setRandomizeKeypad,
                        onChangePin = { settingPin = true },
                        onClearIntruderLog = viewModel::clearIntruderLog,
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                    )
                }
            }
        }
    }
}
