package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.R
import com.meudinheiro.funcoes.NotificacaoVIPHelper
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DespesasDevidas {

    suspend fun verificarEExibir(context: Context) = withContext(Dispatchers.IO) {
        val TAG = "BluMacaw_Despesas"
        val appContext = context.applicationContext

        val prefs = UserPreferences(appContext)

        // 1. Verificação de Permissões
        val enabled = prefs.notifEnabledFlow.firstOrNull() ?: true
        if (!enabled) return@withContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return@withContext
        }

        // 2. Definição da Janela de Tempo
        val daysAhead = prefs.notifDaysAheadFlow.firstOrNull() ?: 3
        val (inicio, fim) = buildWindowDates(daysAhead)

        // 3. Busca e Ordenação
        val repo = MainRepository(appContext)
        val pendentes = try {
            repo.getPendentesAVencer(inicio, fim, onlyCredit = false).sortedBy { it.data.time }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao acessar o cofre: ${e.message}")
            return@withContext
        }

        if (pendentes.isEmpty()) return@withContext

        val df = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        val total = pendentes.sumOf { it.valor }

        val primeiraDespesa = pendentes.first()
        val idParaAcao = primeiraDespesa.id

        // 5. 🚀 LÓGICA DO BADGE DINÂMICO (Calcula urgência)
        val hoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val diffDias = TimeUnit.MILLISECONDS.toDays(primeiraDespesa.data.time - hoje).toInt()

        val textoBadge = when {
            diffDias < 0 -> "ATRASADO!"
            diffDias == 0 -> "VENCE HOJE!"
            diffDias == 1 -> "VENCE AMANHÃ!"
            else -> "VENCE EM ${diffDias}D:"
        }

        // 6. Montagem do Conteúdo
        val tituloVIP = "Atenção: ${pendentes.size} pendência(s) na agulha"

        val mensagemCurta = "${formatarMoedaBR(total,false)} aguardando liberação."

        val detalhesBuilder = StringBuilder()
        detalhesBuilder.append("Resumo dos próximos $daysAhead dias:\n\n")

        pendentes.take(5).forEach { d ->
            detalhesBuilder.append("• ${df.format(d.data)} | ${d.descricao}: ${formatarMoedaBR(d.valor,false)}\n")
        }

        if (pendentes.size > 5) {
            detalhesBuilder.append("\n+ ${pendentes.size - 5} outras contas no sistema.")
        }

        // 7. 🚀 GERA A IMAGEM DE ALERTA (Para ficar do lado direito igual e-commerce)
        // Substitua R.drawable.ic_extrato pelo ícone que você quiser usar de destaque!
        val bitmapIcone = NotificacaoVIPHelper.converterVetorParaBitmap(
            appContext,
            R.drawable.warning, // Ícone de alerta
            Color.parseColor("#FF4B4B") // Pinta o ícone de vermelho alerta
        )

        // 8. Disparo VIP Premium
        try {
            NotificacaoVIPHelper.enviarAlertaVencimento(
                context = appContext,
                titulo = tituloVIP,
                textoBadge = textoBadge, // Passa o HTML vermelho
                mensagemCurta = mensagemCurta,
                detalhes = detalhesBuilder.toString(),
                notificacaoId = idParaAcao.toInt(),
                imagemDireita = bitmapIcone // Passa o ícone gigante
            )
            Log.d(TAG, "Notificação VIP enviada: ID $idParaAcao")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar notificação VIP", e)
        }
    }

    private fun buildWindowDates(daysAhead: Int): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        val inicio = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val fim = cal.apply {
            add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time
        return inicio to fim
    }
}