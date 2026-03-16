package com.meudinheiro.funcoes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meudinheiro.MainActivity
import com.meudinheiro.R

object NotificacaoVIPHelper {

    private const val CHANNEL_ID = "blu_macaw_alerts"
    private const val CHANNEL_NAME = "Alertas Financeiros VIP"

    // Inicializa o canal (Precisa ser chamado no onCreate da sua Application ou MainActivity)
    fun criarCanalDeNotificacao(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Avisos de vencimentos e limites"
                enableLights(true)
                lightColor = Color.parseColor("#00E5FF") // LED pulsando em Neon Cyan
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250) // Vibração estilo "alerta tático"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // A Notificação Rica (Com botões de ação e texto grande)
    fun enviarAlertaVencimento(
        context: Context,
        titulo: String,
        mensagemCurta: String,
        detalhes: String,
        notificacaoId: Int
    ) {
        // 1. Intent para abrir o app ao clicar no corpo da notificação
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ABRIR_ABA", "AGENDAMENTOS")
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Intent para o Botão "PAGAR AGORA" (Chama o BroadcastReceiver em background)
        val intentPagar = Intent(context, PagarBoletoReceiver::class.java).apply {
            action = "PAGAR_BOLETO"
            putExtra("ID_BOLETO", notificacaoId) // Passamos o ID para o Receiver saber quem pagar
        }

        val pendingIntentPagar = PendingIntent.getBroadcast(
            context,
            notificacaoId, // RequestCode único para não sobrescrever outras notificações
            intentPagar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Construção da Notificação VIP
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.meu_dinheiro)
            .setContentTitle(titulo)
            .setContentText(mensagemCurta)
            .setColor(Color.parseColor("#00E5FF")) // Neon Cyan
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detalhes)
            )
            // O BOTÃO DE AÇÃO DIRETA
            .addAction(
                R.drawable.ic_check, // Certifique-se de ter esse ícone no drawable
                "PAGAR AGORA",
                pendingIntentPagar
            )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificacaoId, builder.build())
    }
}