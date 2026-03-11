package com.meudinheiro.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import java.util.Calendar

object AgendadorNotifDespesas {
    private const val REQ_CODE = 9911
    private const val WORK_NAME = "AVISOS_DESPESAS_NOW"

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = receiverPendingIntent(context)
        val triggerAt = nextTriggerMillis(hour, minute)

        cancel(context) // Limpa agendamentos antigos para evitar duplicação

        // A partir do Android 6 (Marshmallow), o Doze Mode bloqueia alarmes normais.
        // O setExactAndAllowWhileIdle "acorda" o sistema no horário exato.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Fallback de segurança caso o usuário revogue a permissão de alarme exato
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6 ao 11
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else { // Android 5 ou inferior
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    // Como usamos setExactAndAllowWhileIdle (que dispara apenas uma vez),
    // é OBRIGATÓRIO reagendar para o dia seguinte assim que a notificação for disparada.
    fun rescheduleNext(context: Context, hour: Int, minute: Int) {
        scheduleDaily(context, hour, minute)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = receiverPendingIntent(context)
        am.cancel(pi)
        pi.cancel()

        // Garante que cancelamos qualquer processo de background preso na fila
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    // OTMIZADO: Força execução imediata ignorando a economia de bateria
    /*fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DespesasDevidas>()
            // Diz ao Android: "Isso foi engatilhado pelo usuário agora, execute imediatamente!"
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Derruba a tarefa anterior se o usuário clicar duas vezes rápido
            req
        )
    }*/

    private fun receiverPendingIntent(context: Context): PendingIntent {
        val i = Intent(context, ReceptorAvisosDiarios::class.java)
        // FLAG_IMMUTABLE é obrigatória a partir do Android 12 para segurança
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

        // Se a hora já passou hoje, agenda para amanhã no mesmo horário
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}