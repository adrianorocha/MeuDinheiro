package com.meudinheiro.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meudinheiro.R

object ExpenseNotif {
    const val CHANNEL_ID = "despesas_vencimento"
    private const val CHANNEL_NAME = "Avisos de Despesas"
    private const val CHANNEL_DESC = "Notificações de despesas a vencer"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_DESC }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun show(
        context: Context,
        title: String,
        text: String,
        bigText: String? = null
    ) {
        ensureChannel(context)

        if (!canPostNotifications(context)) {
            // Sem permissão no Android 13+, não tenta notificar
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        } catch (se: SecurityException) {
            // Caso raro: permissão revogada no meio / comportamento OEM
        }
    }
}
