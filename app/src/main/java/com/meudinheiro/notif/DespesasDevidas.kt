package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
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

        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) return Result.success()
        }

        val daysAhead = prefs.notifDaysAheadFlow.first()
        val onlyCredit = prefs.notifOnlyCreditFlow.first()

        val (inicio, fim) = buildWindowDates(daysAhead)

        val repo = MainRepository(context)
        val pendentes = repo.getPendentesAVencer(inicio, fim, onlyCredit)

        if (pendentes.isEmpty()) return Result.success()

        val nf = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val total = pendentes.sumOf { it.valor }

        val title = "Despesas a vencer"
        val text = "${pendentes.size} pendente(s) nos próximos $daysAhead dia(s) — Total: ${nf.format(total)}$ - Venc: ${inicio.toString()  }"

        val lines = pendentes
            .sortedBy { it.data.time }
            .take(6)
            .joinToString("\n") { d ->
                "• ${d.descricao} — ${nf.format(d.valor)}"
            }

        val bigText = if (pendentes.size > 6) {
            "$lines\n\n+${pendentes.size - 6} item(ns) não exibidos."
        } else lines

        ExpenseNotif.show(
            context = context,
            title = title,
            text = text,
            bigText = bigText
        )

        return Result.success()
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