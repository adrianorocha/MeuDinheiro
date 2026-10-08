package com.meudinheiro.funcoes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meudinheiro.storage.StorageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        private val BIOMETRIA_LANCAR = booleanPreferencesKey("biometria_lancar")

        private val BIOMETRIA_ENABLED = booleanPreferencesKey("biometria_enabled")
        private val KEY_STORAGE_MODE = stringPreferencesKey("storage_mode")
        private val KEY_LAST_CLOUD_UID = stringPreferencesKey("last_cloud_uid")
        private val KEY_CLOUD_ROOT_UID = stringPreferencesKey("cloud_root_uid")
        private val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        private val KEY_ALERTAS_ORCAMENTO = stringSetPreferencesKey("alertas_orcamento")
    }

    // ---- armazenamento de dados (local × Firebase) ----
    val storageModeFlow: Flow<StorageMode> = context.dataStore.data
        .map { prefs -> StorageMode.deNome(prefs[KEY_STORAGE_MODE]) }

    suspend fun saveStorageMode(modo: StorageMode) {
        context.dataStore.edit { it[KEY_STORAGE_MODE] = modo.name }
    }

    /** R32 — uid cujo dados estão em uso (vazio = os do próprio usuário). */
    val cloudRootUidFlow: Flow<String> = context.dataStore.data.map { it[KEY_CLOUD_ROOT_UID].orEmpty() }

    suspend fun saveCloudRootUid(uid: String) {
        context.dataStore.edit { it[KEY_CLOUD_ROOT_UID] = uid }
    }

    // ---- backup automático (R33)
    val autoBackupEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_BACKUP] ?: true }

    suspend fun saveAutoBackupEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_BACKUP] = v }
    }

    // ---- alertas de orçamento já enviados (R22): chaves `categoria|AAAA-MM|limiar`
    suspend fun alertasOrcamentoEnviados(): Set<String> = context.dataStore.data.first()[KEY_ALERTAS_ORCAMENTO].orEmpty()

    suspend fun marcarAlertasOrcamento(chaves: Set<String>) {
        context.dataStore.edit { prefs ->
            // mantém só os do mês corrente e do anterior para o conjunto não crescer sem limite
            prefs[KEY_ALERTAS_ORCAMENTO] = (prefs[KEY_ALERTAS_ORCAMENTO].orEmpty() + chaves).toList().takeLast(400).toSet()
        }
    }

    suspend fun lastCloudUid(): String = context.dataStore.data.first()[KEY_LAST_CLOUD_UID].orEmpty()

    suspend fun saveLastCloudUid(uid: String) {
        context.dataStore.edit { it[KEY_LAST_CLOUD_UID] = uid }
    }

    // ---- senha (nunca em texto puro) ----
    /** Confere a senha digitada; senhas antigas (texto puro) são aceitas uma vez e já regravadas como hash. */
    suspend fun verificarSenha(digitada: String): Boolean {
        val armazenada = context.dataStore.data.first()[KEY_USER_PASS].orEmpty()
        val ok = SenhaHasher.conferir(digitada, armazenada)
        if (ok && !SenhaHasher.ehHash(armazenada)) saveUserPass(digitada)
        return ok
    }

    // 2) flows que usam o context da instância
    val biometriaEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIA_ENABLED] ?: true
    }
    /** Pede biometria (ou PIN do aparelho) antes de gravar um lançamento — no app e no widget. Padrão: ligado. */
    val biometriaLancarFlow: Flow<Boolean> = context.dataStore.data.map { it[BIOMETRIA_LANCAR] ?: true }
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
        val armazenar = if (SenhaHasher.ehHash(pass)) pass else SenhaHasher.gerar(pass)
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_PASS] = armazenar
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

    suspend fun saveBiometriaLancar(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIA_LANCAR] = enabled }
    }

    suspend fun saveBiometriaEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIA_ENABLED] = enabled
        }
    }

}
