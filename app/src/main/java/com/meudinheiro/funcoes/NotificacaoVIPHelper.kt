package com.meudinheiro.funcoes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
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
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🚀 BÔNUS: Transforma seus ícones vetoriais normais em imagens para a notificação
    fun converterVetorParaBitmap(context: Context, drawableId: Int, corTint: Int? = null): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        corTint?.let { drawable.setTint(it) }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(100), // Garante um tamanho bom
            drawable.intrinsicHeight.coerceAtLeast(100),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // A Notificação Rica Cyberpunk (Com HTML e Imagem Lateral)
    fun enviarAlertaVencimento(
        context: Context,
        titulo: String,
        textoBadge: String, // 🚀 NOVO: O texto de urgência vermelho
        mensagemCurta: String,
        detalhes: String,
        notificacaoId: Int,
        imagemDireita: Bitmap? = null // 🚀 NOVO: Imagem grande
    ) {
        // 1. Intent para abrir o app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ABRIR_ABA", "AGENDAMENTOS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Intent para o Botão "PAGAR AGORA"
        val intentPagar = Intent(context, PagarBoletoReceiver::class.java).apply {
            action = "PAGAR_BOLETO"
            putExtra("ID_BOLETO", notificacaoId)
        }
        val pendingIntentPagar = PendingIntent.getBroadcast(
            context, notificacaoId, intentPagar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 🚀 MÁGICA DO HTML: Fica vermelho e negrito direto na One UI da Samsung!
        val textoFormatado = HtmlCompat.fromHtml(
            "<font color='#FF4B4B'><b>$textoBadge</b></font> $mensagemCurta",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // 4. Construção da Notificação VIP
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.sim_chip_2) // ⚠️ DICA: Use um ícone com fundo transparente aqui!
            .setColor(Color.parseColor("#00E5FF")) // Neon Cyan
            .setContentTitle(titulo)
            .setContentText(textoFormatado)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detalhes))
            .addAction(R.drawable.ic_check, "PAGAR AGORA", pendingIntentPagar)

        // Adiciona a imagem no lado direito, se você enviou
        imagemDireita?.let { builder.setLargeIcon(it) }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificacaoId, builder.build())
    }
}