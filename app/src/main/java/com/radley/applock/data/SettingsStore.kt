package com.radley.applock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radley.applock.lock.IntruderPolicy
import com.radley.applock.lock.RelockPolicy
import com.radley.applock.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "applock")

/**
 * All persisted state. DataStore rather than a database: this is a set of package names and a
 * handful of scalars, and keeping it annotation-processor free means the repositories are
 * plain interfaces that fake cleanly in tests.
 *
 * The PIN is stored as a PBKDF2 hash, so there is nothing here that is sensitive at rest.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val LOCKED_PACKAGES = stringSetPreferencesKey("locked_packages")
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val RELOCK_POLICY = stringPreferencesKey("relock_policy")
        val INTRUDER_THRESHOLD = intPreferencesKey("intruder_threshold")
        val RANDOMIZE_KEYPAD = booleanPreferencesKey("randomize_keypad")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val INTRUDER_EVENTS = stringPreferencesKey("intruder_events")
    }

    val lockedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.LOCKED_PACKAGES] ?: emptySet() }

    val protectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PROTECTION_ENABLED] ?: true }

    val relockPolicy: Flow<RelockPolicy> =
        context.dataStore.data.map { RelockPolicy.fromNameOrDefault(it[Keys.RELOCK_POLICY]) }

    val intruderThreshold: Flow<Int> =
        context.dataStore.data.map { it[Keys.INTRUDER_THRESHOLD] ?: IntruderPolicy.DEFAULT_THRESHOLD }

    val randomizeKeypad: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.RANDOMIZE_KEYPAD] ?: false }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val hasPin: Flow<Boolean> =
        context.dataStore.data.map { !it[Keys.PIN_HASH].isNullOrBlank() }

    val intruderEventsJson: Flow<String> =
        context.dataStore.data.map { it[Keys.INTRUDER_EVENTS] ?: "[]" }

    suspend fun setLocked(packageName: String, locked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LOCKED_PACKAGES] ?: emptySet()
            prefs[Keys.LOCKED_PACKAGES] =
                if (locked) current + packageName else current - packageName
        }
    }

    suspend fun currentLockedPackages(): Set<String> = lockedPackages.first()

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROTECTION_ENABLED] = enabled }
    }

    suspend fun setRelockPolicy(policy: RelockPolicy) {
        context.dataStore.edit { it[Keys.RELOCK_POLICY] = policy.name }
    }

    suspend fun setIntruderThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.INTRUDER_THRESHOLD] = threshold }
    }

    suspend fun setRandomizeKeypad(randomize: Boolean) {
        context.dataStore.edit { it[Keys.RANDOMIZE_KEYPAD] = randomize }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setPin(pin: String) {
        val hashed = PinHasher.hash(pin)
        context.dataStore.edit { it[Keys.PIN_HASH] = hashed }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.dataStore.data.map { it[Keys.PIN_HASH] }.first()
        return PinHasher.verify(pin, stored)
    }

    suspend fun setIntruderEventsJson(json: String) {
        context.dataStore.edit { it[Keys.INTRUDER_EVENTS] = json }
    }
}
