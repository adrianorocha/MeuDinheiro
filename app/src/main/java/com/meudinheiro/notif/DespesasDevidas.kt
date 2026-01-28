package com.meudinheiro.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.data.AppDatabase // ajuste o package do seu AppDatabase
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DespesasDevidas(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = UserPreferences(context)

        val enabled = prefs.notifEnabledFlow.first()
        if (!enabled) return Result.success()

        val daysAhead = prefs.notifDaysAheadFlow.first()
        val hour = prefs.notifHourFlow.first()
        val minute = prefs.notifMinuteFlow.first()
        val onlyCredit = prefs.notifOnlyCreditFlow.first()

        // Evita notificar mais de 1x no mesmo dia
        val todayKey = dayKey(System.currentTimeMillis())
        val lastDay = prefs.notifLastDayFlow.first()
        if (lastDay == todayKey) return Result.success()

        val calStart = Calendar.getInstance().apply {
            // janela começa AGORA (se quiser, pode começar no início do dia)
            timeInMillis = System.currentTimeMillis()
        }

        val calEnd = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val inicio = Date(calStart.timeInMillis)
        val fim = Date(calEnd.timeInMillis)

        val db = AppDatabase.getInstance(context) // ajuste conforme seu singleton
        val dao = db.despesaDao()

        val onlyPending = prefs.notifOnlyPendingFlow.first() // default true

        val lista = dao.obterDespesasVencendo(inicio, fim)
            .let { all ->
                if (!onlyCredit) all
                else all.filter { it.tipo == TipoDespesa.CREDITO }
            }

        if (lista.isEmpty()) return Result.success()

        val df = SimpleDateFormat("dd/MM", Locale.getDefault())
        val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        val primeiraData = lista.minByOrNull { it.data.time }?.data
        val ate = primeiraData?.let { df.format(it) } ?: df.format(fim)

        val title = "Despesas a vencer"
        val text = "Você tem ${lista.size} despesa(s) para pagar nos próximos $daysAhead dia(s)."

        val detalhes = buildString {
            append("Vencimentos até $ate\n\n")
            lista.take(5).forEach { d ->
                append("• ${d.descricao} — ${moeda.format(d.valor)} — ${df.format(d.data)}\n")
            }
            if (lista.size > 5) append("\nE mais ${lista.size - 5}...")
        }

        ExpenseNotif.show(
            context = context,
            title = title,
            text = text,
            bigText = detalhes
        )

        prefs.saveNotifLastDay(todayKey)
        return Result.success()
    }

    private fun dayKey(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        // chave única por dia (yyyyMMdd)
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return (y * 10000 + m * 100 + d).toLong()
    }


}