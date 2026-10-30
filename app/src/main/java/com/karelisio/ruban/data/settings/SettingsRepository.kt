package com.karelisio.ruban.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val onboardingDone: Boolean = false,
    val dynamicColor: Boolean = true,
    val reminderOffsets: List<Int> = listOf(30, 7, 1),
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val neutralNotifications: Boolean = false,
    val biometricLock: Boolean = false,
    val secureScreen: Boolean = false,
    val priceAlerts: Boolean = true,
    val autoBackup: Boolean = false,
    val autoBackupFolder: String? = null,
    /** Backup password, encrypted with an Android Keystore key (see SecretBox). */
    val autoBackupSecret: String? = null,
    val lastAutoBackupAt: Long = 0,
    val lastUpdateCheckAt: Long = 0,
    val dismissedUpdate: String? = null,
    val suggestionsVersion: Int = 0,
    val holidaysSeeded: Boolean = false,
    val currency: String = "EUR",
    val lastReminderAt: Long = 0,
    val lastPriceCheckAt: Long = 0,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val reminderOffsets = stringPreferencesKey("reminder_offsets")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val neutralNotifications = booleanPreferencesKey("neutral_notifications")
        val biometricLock = booleanPreferencesKey("biometric_lock")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val priceAlerts = booleanPreferencesKey("price_alerts")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val autoBackupFolder = stringPreferencesKey("auto_backup_folder")
        val autoBackupSecret = stringPreferencesKey("auto_backup_secret")
        val lastAutoBackupAt = longPreferencesKey("last_auto_backup_at")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val dismissedUpdate = stringPreferencesKey("dismissed_update")
        val suggestionsVersion = intPreferencesKey("suggestions_version")
        val holidaysSeeded = booleanPreferencesKey("holidays_seeded")
        val currency = stringPreferencesKey("currency")
        val lastReminderAt = longPreferencesKey("last_reminder_at")
        val lastPriceCheckAt = longPreferencesKey("last_price_check_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            reminderOffsets = p[Keys.reminderOffsets]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: listOf(30, 7, 1),
            reminderHour = p[Keys.reminderHour] ?: 9,
            reminderMinute = p[Keys.reminderMinute] ?: 0,
            neutralNotifications = p[Keys.neutralNotifications] ?: false,
            biometricLock = p[Keys.biometricLock] ?: false,
            secureScreen = p[Keys.secureScreen] ?: false,
            priceAlerts = p[Keys.priceAlerts] ?: true,
            autoBackup = p[Keys.autoBackup] ?: false,
            autoBackupFolder = p[Keys.autoBackupFolder],
            autoBackupSecret = p[Keys.autoBackupSecret],
            lastAutoBackupAt = p[Keys.lastAutoBackupAt] ?: 0,
            lastUpdateCheckAt = p[Keys.lastUpdateCheckAt] ?: 0,
            dismissedUpdate = p[Keys.dismissedUpdate],
            suggestionsVersion = p[Keys.suggestionsVersion] ?: 0,
            holidaysSeeded = p[Keys.holidaysSeeded] ?: false,
            currency = p[Keys.currency] ?: "EUR",
            lastReminderAt = p[Keys.lastReminderAt] ?: 0,
            lastPriceCheckAt = p[Keys.lastPriceCheckAt] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.onboardingDone] = done }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setReminderOffsets(value: List<Int>) =
        edit { it[Keys.reminderOffsets] = value.distinct().sortedDescending().joinToString(",") }
    suspend fun setReminderTime(hour: Int, minute: Int) = edit {
        it[Keys.reminderHour] = hour
        it[Keys.reminderMinute] = minute
    }
    suspend fun setNeutralNotifications(value: Boolean) = edit { it[Keys.neutralNotifications] = value }
    suspend fun setBiometricLock(value: Boolean) = edit { it[Keys.biometricLock] = value }
    suspend fun setSecureScreen(value: Boolean) = edit { it[Keys.secureScreen] = value }
    suspend fun setPriceAlerts(value: Boolean) = edit { it[Keys.priceAlerts] = value }
    suspend fun setAutoBackup(enabled: Boolean, folder: String?, secret: String?) = edit {
        it[Keys.autoBackup] = enabled
        if (folder != null) it[Keys.autoBackupFolder] = folder else it.remove(Keys.autoBackupFolder)
        if (secret != null) it[Keys.autoBackupSecret] = secret else it.remove(Keys.autoBackupSecret)
    }
    suspend fun setLastAutoBackupAt(value: Long) = edit { it[Keys.lastAutoBackupAt] = value }
    suspend fun setLastUpdateCheckAt(value: Long) = edit { it[Keys.lastUpdateCheckAt] = value }
    suspend fun setDismissedUpdate(value: String?) =
        edit { if (value != null) it[Keys.dismissedUpdate] = value else it.remove(Keys.dismissedUpdate) }
    suspend fun setSuggestionsVersion(value: Int) = edit { it[Keys.suggestionsVersion] = value }
    suspend fun setHolidaysSeeded(value: Boolean) = edit { it[Keys.holidaysSeeded] = value }
    suspend fun setCurrency(value: String) = edit { it[Keys.currency] = value }
    suspend fun setLastReminderAt(value: Long) = edit { it[Keys.lastReminderAt] = value }
    suspend fun setLastPriceCheckAt(value: Long) = edit { it[Keys.lastPriceCheckAt] = value }
}
