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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun formatarMoedaBR(valor: Double): String {
    val formato = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    return formato.format(valor)
}

object DateUtils {
    fun formatarData(date: Date): String {
        val sdf = SimpleDateFormat("EEE - dd MMM yyyy", Locale("pt", "BR"))
        return sdf.format(date)
    }
   fun formatarData(millis: Long): String {
        return formatarData(Date(millis))
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        // só as chaves aqui
        private val KEY_USER_NAME     = stringPreferencesKey("user_name")
        private val KEY_LOGIN        = stringPreferencesKey("user_login")
        private val KEY_USER_PASS     = stringPreferencesKey("user_pass")
        private val KEY_USER_PHOTO    = stringPreferencesKey("user_photo_uri")
        private val KEY_BIOMETRIC     = booleanPreferencesKey("biometric_enabled")

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

    // 3) métodos de gravação que usam o context
    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun saveUserLogin (name: String) {
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
}