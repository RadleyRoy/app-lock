package com.radley.applock.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radley.applock.data.InstalledApp
import com.radley.applock.data.IntruderEvent
import com.radley.applock.data.SuggestedApps
import com.radley.applock.di.ServiceLocator
import com.radley.applock.lock.RelockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRow(
    val app: InstalledApp,
    val isLocked: Boolean,
)

data class AppListUiState(
    val suggested: List<AppRow> = emptyList(),
    val others: List<AppRow> = emptyList(),
    val loading: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = ServiceLocator.settings
    private val installedApps = ServiceLocator.installedApps

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _loading = MutableStateFlow(true)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showLockedOnly = MutableStateFlow(false)
    val showLockedOnly: StateFlow<Boolean> = _showLockedOnly.asStateFlow()

    val lockedPackages: StateFlow<Set<String>> = settings.lockedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val protectionEnabled: StateFlow<Boolean> = settings.protectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val relockPolicy: StateFlow<RelockPolicy> = settings.relockPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelockPolicy.DEFAULT)

    val intruderThreshold: StateFlow<Int> = settings.intruderThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val randomizeKeypad: StateFlow<Boolean> = settings.randomizeKeypad
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val onboardingComplete: StateFlow<Boolean> = settings.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val hasPin: StateFlow<Boolean> = settings.hasPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val intruderEvents: StateFlow<List<IntruderEvent>> = ServiceLocator.intruders.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appList: StateFlow<AppListUiState> =
        combine(allApps, lockedPackages, _query, _showLockedOnly, _loading) {
                apps, locked, query, lockedOnly, loading ->
            val filtered = apps
                .asSequence()
                .filter { !lockedOnly || it.packageName in locked }
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .map { AppRow(app = it, isLocked = it.packageName in locked) }
                .toList()

            val (suggested, others) = filtered.partition { SuggestedApps.isSuggested(it.app) }
            AppListUiState(suggested = suggested, others = others, loading = loading)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        refreshApps()
    }

    fun refreshApps() = viewModelScope.launch {
        _loading.value = true
        allApps.value = installedApps.loadApps()
        _loading.value = false
    }

    fun iconFor(packageName: String) = installedApps.iconFor(packageName)

    fun setQuery(value: String) { _query.value = value }

    fun setShowLockedOnly(value: Boolean) { _showLockedOnly.value = value }

    fun toggleLock(packageName: String, locked: Boolean) = viewModelScope.launch {
        settings.setLocked(packageName, locked)
    }

    fun setProtectionEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setProtectionEnabled(enabled)
    }

    fun setRelockPolicy(policy: RelockPolicy) = viewModelScope.launch {
        settings.setRelockPolicy(policy)
    }

    fun setIntruderThreshold(threshold: Int) = viewModelScope.launch {
        settings.setIntruderThreshold(threshold)
    }

    fun setRandomizeKeypad(value: Boolean) = viewModelScope.launch {
        settings.setRandomizeKeypad(value)
    }

    fun setPin(pin: String) = viewModelScope.launch {
        settings.setPin(pin)
    }

    fun completeOnboarding() = viewModelScope.launch {
        settings.setOnboardingComplete(true)
    }

    fun deleteIntruderEvent(id: String) = viewModelScope.launch {
        ServiceLocator.intruders.delete(id)
    }

    fun clearIntruderLog() = viewModelScope.launch {
        ServiceLocator.intruders.clearAll()
    }

    fun photoFile(fileName: String) = ServiceLocator.intruders.photoFile(fileName)
}
