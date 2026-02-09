package com.meudinheiro.funcoes

import android.content.Context
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun formatarMoedaBR(valor: Double, isPrivate: Boolean): String {
    val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    return if (isPrivate) {
        "••••••"
    } else {
        return formato.format(valor)
    }
}


object DateUtils {
    fun formatarData(date: Date): String {
        val sdf = SimpleDateFormat("EEE - dd MMM yyyy", Locale("pt", "BR"))
        return sdf.format(date)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        // só as chaves aqui
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_LOGIN = stringPreferencesKey("user_login")
        private val KEY_USER_PASS = stringPreferencesKey("user_pass")
        private val KEY_USER_PHOTO = stringPreferencesKey("user_photo_uri")
        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        private val KEY_NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        private val KEY_NOTIF_DAYS_AHEAD = intPreferencesKey("notif_days_ahead")
        private val KEY_NOTIF_HOUR = intPreferencesKey("notif_hour")
        private val KEY_NOTIF_MINUTE = intPreferencesKey("notif_minute")
        private val KEY_NOTIF_ONLY_CREDIT = booleanPreferencesKey("notif_only_credit")
        private val KEY_NOTIF_LAST_DAY =
            longPreferencesKey("notif_last_day") // evita repetir no mesmo dia
        private val KEY_NOTIF_ONLY_PENDING = booleanPreferencesKey("notif_only_pending")
        private val PRIVATE_MODE_KEY = booleanPreferencesKey("private_mode")
    }

    // 2) flows que usam o context da instância
    val userNameFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_NAME].orEmpty() }

    val userLoginFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_LOGIN].orEmpty() }

    val userPassFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_PASS].orEmpty() }

    val userPhotoFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_PHOTO].orEmpty() }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_BIOMETRIC] ?: false }

    val notifEnabledFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_ENABLED] ?: false
    }

    val notifDaysAheadFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_DAYS_AHEAD] ?: 3
    }

    val notifHourFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_HOUR] ?: 9
    }

    val notifMinuteFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_MINUTE] ?: 0
    }

    val notifOnlyCreditFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_ONLY_CREDIT] ?: false
    }

    val notifLastDayFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_LAST_DAY] ?: 0L
    }
    val notifOnlyPendingFlow = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_ONLY_PENDING] ?: true
    }

    suspend fun saveNotifOnlyPending(v: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_ONLY_PENDING] = v }
    }

    suspend fun saveNotifEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_ENABLED] = v }
    }

    suspend fun saveNotifOnlyCredit(v: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_ONLY_CREDIT] = v }
    }

    suspend fun saveNotifLastDay(v: Long) {
        context.dataStore.edit { it[KEY_NOTIF_LAST_DAY] = v }
    }

    // 3) métodos de gravação que usam o context
    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun saveUserLogin(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOGIN] = name
        }
    }

    suspend fun saveUserPass(pass: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_PASS] = pass
        }
    }

    suspend fun saveUserPhoto(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_PHOTO] = uri
        }
    }

    suspend fun saveBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC] = enabled
        }
    }

    suspend fun saveNotifDaysAhead(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_DAYS_AHEAD] = days
        }
    }

    suspend fun saveNotifHour(hour: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_HOUR] = hour
        }
    }

    suspend fun saveNotifMinute(minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_MINUTE] = minute

        }
    }

    val privateModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PRIVATE_MODE_KEY] ?: false // Padrão é falso (visível)
        }

    suspend fun togglePrivateMode() {
        context.dataStore.edit { preferences ->
            val current = preferences[PRIVATE_MODE_KEY] ?: false
            preferences[PRIVATE_MODE_KEY] = !current
        }
    }
}