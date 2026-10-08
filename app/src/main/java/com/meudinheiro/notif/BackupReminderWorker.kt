package com.meudinheiro.notif

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {

        enviarNotificacaoBackup()

        return Result.success()
    }

    private fun enviarNotificacaoBackup() {
        Notificacoes.mostrar(
            applicationContext,
            Aviso(
                tipo = TipoAviso.BACKUP,
                titulo = "Hora do backup semanal",
                resumo = "Já faz uma semana. Proteja seus dados em poucos toques.",
                id = 500_001,
                textoPublico = "Lembrete de backup"
            )
        )
    }
}
