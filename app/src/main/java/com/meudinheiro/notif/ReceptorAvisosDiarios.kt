package com.meudinheiro.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ReceptorAvisosDiarios : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync() // Trava o processo para não morrer

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. CHAMA DIRETO! Pula o WorkManager.
                DespesasDevidas.verificarEExibir(context)

                // 2. Reagenda para amanhã
                val userPrefs = UserPreferences(context)
                val horaSalva = userPrefs.notifHourFlow.firstOrNull() ?: 9
                val minutoSalvo = userPrefs.notifMinuteFlow.firstOrNull() ?: 0
                AgendadorNotifDespesas.scheduleDaily(context, horaSalva, minutoSalvo)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish() // Libera o Android
            }
        }
    }
}