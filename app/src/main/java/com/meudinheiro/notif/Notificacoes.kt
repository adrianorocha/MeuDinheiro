package com.meudinheiro.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meudinheiro.MainActivity
import com.meudinheiro.R
import com.meudinheiro.funcoes.PagarBoletoReceiver

/** Tipos de aviso: cada um define canal, cor de destaque, selo e o glifo do ícone grande. */
enum class TipoAviso(
    val canal: String,
    val rotulo: String,
    val cor: Int,
    val selo: String,
    val glifo: String
) {
    VENCIMENTO("avisos_vencimentos", "Vencimentos", 0xFFFFB74D.toInt(), "A VENCER", "R$"),
    ATRASADO("avisos_vencimentos", "Vencimentos", 0xFFFF5252.toInt(), "ATRASADA", "!"),
    ORCAMENTO("avisos_orcamento", "Orçamento", 0xFFFFB74D.toInt(), "ATENÇÃO", "%"),
    ORCAMENTO_ESTOURADO("avisos_orcamento", "Orçamento", 0xFFFF5252.toInt(), "ESTOURADO", "!"),
    SUCESSO("avisos_recibos", "Recibos", 0xFF1DB954.toInt(), "CONCLUÍDO", "OK"),
    FALHA("avisos_recibos", "Recibos", 0xFFFF5252.toInt(), "NÃO REALIZADO", "!"),
    BACKUP("avisos_backup", "Backup", 0xFF00B8D4.toInt(), "SEGURANÇA", "B")
}

/**
 * Um aviso ao usuário.
 * @param linhas detalhes (uma linha cada); vazio → só o [resumo].
 * @param despesaId se informado, a notificação ganha o botão "Pagar agora" para ESSA despesa.
 * @param textoPublico o que aparece na tela bloqueada (os valores nunca vazam por lá).
 */
data class Aviso(
    val tipo: TipoAviso,
    val titulo: String,
    val resumo: String,
    val id: Int,
    val linhas: List<String> = emptyList(),
    val despesaId: Long? = null,
    val textoPublico: String = "Você tem um aviso financeiro"
)

object Notificacoes {
    const val TAG = "aviso"
    private const val GRUPO_CANAIS = "meudinheiro"

    /** Canais antigos, substituídos pelos novos (o sistema não deixa mudar a importância de um canal existente). */
    private val CANAIS_ANTIGOS = listOf("blu_macaw_alerts", "despesas_vencimento", "backup_channel")

    fun criarCanais(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        CANAIS_ANTIGOS.forEach { mgr.deleteNotificationChannel(it) }
        mgr.createNotificationChannelGroup(android.app.NotificationChannelGroup(GRUPO_CANAIS, "MeuDinheiro"))

        fun canal(id: String, nome: String, desc: String, importancia: Int, cor: Int, vibrar: Boolean) {
            mgr.createNotificationChannel(NotificationChannel(id, nome, importancia).apply {
                description = desc
                group = GRUPO_CANAIS
                enableLights(true); lightColor = cor
                enableVibration(vibrar)
                if (vibrar) vibrationPattern = longArrayOf(0, 180, 120, 180)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
        canal("avisos_vencimentos", "Vencimentos", "Contas a pagar atrasadas e a vencer", NotificationManager.IMPORTANCE_HIGH, TipoAviso.VENCIMENTO.cor, true)
        canal("avisos_orcamento", "Orçamento", "Avisos quando uma categoria chega a 80% ou estoura", NotificationManager.IMPORTANCE_HIGH, TipoAviso.ORCAMENTO.cor, true)
        canal("avisos_recibos", "Recibos", "Confirmação de transferências e lançamentos", NotificationManager.IMPORTANCE_DEFAULT, TipoAviso.SUCESSO.cor, false)
        canal("avisos_backup", "Backup", "Lembrete semanal para guardar seus dados", NotificationManager.IMPORTANCE_LOW, TipoAviso.BACKUP.cor, false)
    }

    private fun podeNotificar(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Selo colorido em negrito + resumo: "ATRASADA  R$ 150,00 …". */
    private fun textoComSelo(selo: String, cor: Int, resumo: String): CharSequence {
        val s = SpannableString("$selo  $resumo")
        s.setSpan(ForegroundColorSpan(cor), 0, selo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, selo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** Ícone grande: círculo na cor do aviso com um glifo branco (nada de logotipo colorido na barra). */
    fun iconeCircular(tipo: TipoAviso, tamanho: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = tipo.cor
        c.drawCircle(tamanho / 2f, tamanho / 2f, tamanho / 2f, p)
        p.color = Color.WHITE
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        p.textSize = if (tipo.glifo.length > 1) tamanho * 0.40f else tamanho * 0.56f
        c.drawText(tipo.glifo, tamanho / 2f, tamanho / 2f - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }

    private fun abrirApp(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context, id,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun mostrar(context: Context, aviso: Aviso) {
        val app = context.applicationContext
        if (!podeNotificar(app)) return
        criarCanais(app)
        val t = aviso.tipo

        val publica = NotificationCompat.Builder(app, t.canal)
            .setSmallIcon(R.drawable.ic_stat_dinheiro).setColor(t.cor)
            .setContentTitle("MeuDinheiro").setContentText(aviso.textoPublico)
            .build()

        val b = NotificationCompat.Builder(app, t.canal)
            .setSmallIcon(R.drawable.ic_stat_dinheiro)
            .setColor(t.cor)
            .setLargeIcon(iconeCircular(t))
            .setContentTitle(aviso.titulo)
            .setContentText(textoComSelo(t.selo, t.cor, aviso.resumo))
            .setSubText(t.rotulo)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .setCategory(if (t.canal == "avisos_recibos") NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (t.canal == "avisos_vencimentos" || t.canal == "avisos_orcamento") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .setGroup("meudinheiro_${t.canal}")
            .setContentIntent(abrirApp(app, aviso.id))
            .setAutoCancel(true)

        b.setStyle(
            if (aviso.linhas.size > 1) NotificationCompat.InboxStyle().also { s ->
                aviso.linhas.forEach { s.addLine(it) }
                s.setBigContentTitle(aviso.titulo).setSummaryText(aviso.resumo)
            }
            else NotificationCompat.BigTextStyle()
                .bigText((aviso.linhas.firstOrNull() ?: aviso.resumo))
                .setBigContentTitle(aviso.titulo).setSummaryText(t.rotulo)
        )

        if (aviso.despesaId != null) {
            val pagar = PendingIntent.getBroadcast(
                app, aviso.id,
                Intent(app, PagarBoletoReceiver::class.java).apply {
                    action = "PAGAR_BOLETO"
                    putExtra("ID_BOLETO", aviso.despesaId.toInt())
                    putExtra("ID_DESPESA", aviso.despesaId)
                    putExtra("ID_NOTIF", aviso.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Mexe no seu dinheiro: com o aparelho bloqueado, o sistema exige desbloquear antes (Android 12+).
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_check, "Pagar agora", pagar).setAuthenticationRequired(true).build())
            b.addAction(R.drawable.ic_stat_dinheiro, "Abrir", abrirApp(app, aviso.id + 1))
        }

        try {
            NotificationManagerCompat.from(app).notify(TAG, aviso.id, b.build())
        } catch (_: SecurityException) {
            // permissão revogada entre a checagem e o envio
        }
    }
}
