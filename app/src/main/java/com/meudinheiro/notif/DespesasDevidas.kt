package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DespesasDevidas {

    // Função direta que roda na Thread de IO (Banco de Dados)
    suspend fun verificarEExibir(context: Context) = withContext(Dispatchers.IO) {
        val TAG = "DespesasWorker"
        Log.d(TAG, "Iniciando verificação DIRETA de despesas...")

        val prefs = UserPreferences(context)

        val enabled = prefs.notifEnabledFlow.firstOrNull() ?: true
        if (!enabled) {
            Log.d(TAG, "Notificações desativadas.")
            return@withContext
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.d(TAG, "Sem permissão do Android.")
                return@withContext
            }
        }

        val daysAhead = prefs.notifDaysAheadFlow.firstOrNull() ?: 3
        val (inicio, fim) = buildWindowDates(daysAhead)

        val pendentes = try {
            val repo = MainRepository(context)
            repo.getPendentesAVencer(inicio, fim, onlyCredit = false)
        } catch (e: Exception) {
            Log.e(TAG, "Erro no banco", e)
            return@withContext
        }

        if (pendentes.isEmpty()) {
            Log.d(TAG, "Nenhuma despesa para os próximos $daysAhead dias.")
            return@withContext
        }

        val nf = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val df = SimpleDateFormat("dd/MM/yy", Locale("pt", "BR"))
        val total = pendentes.sumOf { it.valor }

        val title = "Despesas a vencer"
        val text = "${pendentes.size} pendente(s) nos próximos $daysAhead dia(s) — Total: ${nf.format(total)}"

        val lines = pendentes
            .sortedBy { it.data.time }
            .take(6)
            .joinToString("\n") { d ->
                "• ${d.descricao} — ${df.format(d.data)} — ${nf.format(d.valor)}"
            }

        val bigText = if (pendentes.size > 6) {
            "$lines\n\n+${pendentes.size - 6} item(ns) ocultos."
        } else lines

        try {
            ExpenseNotif.show(context, title, text, bigText)
            Log.d(TAG, "Notificação disparada com SUCESSO!")
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao exibir notificação", e)
        }
    }

    private fun buildWindowDates(daysAhead: Int): Pair<Date, Date> {
        val calIni = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calFim = Calendar.getInstance().apply {
            timeInMillis = calIni.timeInMillis
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Date(calIni.timeInMillis) to Date(calFim.timeInMillis)
    }
}