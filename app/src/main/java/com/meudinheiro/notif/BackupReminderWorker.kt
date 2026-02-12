package com.meudinheiro.notif

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meudinheiro.MainActivity
import com.meudinheiro.R

class BackupReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {

        enviarNotificacaoBackup()

        return Result.success()
    }

    private fun enviarNotificacaoBackup() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "backup_channel")
            .setSmallIcon(R.drawable.ic_backup) // Certifique-se de ter este ícone
            .setContentTitle("Segurança em primeiro lugar! 🛡️")
            .setContentText("Já faz uma semana que não faz backup. Vamos proteger os seus dados?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
    }
}