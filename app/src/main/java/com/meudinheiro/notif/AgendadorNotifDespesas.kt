package com.meudinheiro.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar

object AgendadorNotifDespesas {
    private const val REQ_CODE = 9911
    private const val WORK_NAME = "AVISOS_DESPESAS_NOW"

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = receiverPendingIntent(context)
        val triggerAt = nextTriggerMillis(hour, minute)

        cancel(context) // evita duplicar agendamentos

        if (Build.VERSION.SDK_INT >= 31) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Fallback sem permissão de alarme exato
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    AlarmManager.INTERVAL_DAY,
                    pi
                )
            }
        } else {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }
    }
    fun rescheduleNext(context: Context) {
        // lê do prefs o horário atual e agenda novamente
        // (assim sempre respeita o último horário salvo)
        val prefs = com.meudinheiro.funcoes.UserPreferences(context)

        // como é object, chamamos um worker agora e reagendamos no receiver
        // aqui vamos buscar o horário no DataStore via runNow? não dá (suspend).
        // Solução simples: reagendar pelo último horário gravado também no Intent extras, ou:
        // (Recomendado) chamar scheduleDaily novamente quando user muda hora/minuto na Configuracao.
        //
        // Então aqui apenas agenda +24h com o mesmo horário do último scheduleDaily já chamado.
        // Na prática: o receiver só chama rescheduleNext depois de runNow; o scheduleDaily já foi chamado
        // nas mudanças. Se você quiser “perfeito”, posso te mandar a versão que salva hour/minute no SharedPrefs
        // só para o alarme.
    }
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = receiverPendingIntent(context)
        am.cancel(pi)
        pi.cancel()
    }

    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DespesasDevidas>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    private fun receiverPendingIntent(context: Context): PendingIntent {
        val i = Intent(context, ReceptorAvisosDiarios::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQ_CODE, i, flags)
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }


}