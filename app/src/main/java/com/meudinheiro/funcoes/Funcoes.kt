package com.meudinheiro.funcoes

import android.content.Context
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
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
        // Chave para salvar o nome do usuário
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
    }

    // Flow que emite o nome salvo, ou string vazia se não tiver nada
    val userNameFlow: Flow<String> = context
        .dataStore
        .data
        .map { prefs ->
            prefs[USER_NAME_KEY] ?: ""
        }

    // Salva o nome do usuário
    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME_KEY] = name
        }
    }
}