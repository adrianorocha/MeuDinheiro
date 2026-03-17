package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.NotificacaoVIPHelper
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DespesasDevidas {

    suspend fun verificarEExibir(context: Context) = withContext(Dispatchers.IO) {
        val TAG = "BluMacaw_Despesas"
        val appContext = context.applicationContext // Evita memory leaks

        val prefs = UserPreferences(appContext)

        // 1. Verificação de Configurações e Permissões
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

        // 3. Busca e Ordenação de Dados
        val repo = MainRepository(appContext)
        val pendentes = try {
            // Buscamos e já ordenamos pela data mais próxima
            repo.getPendentesAVencer(inicio, fim, onlyCredit = false)
                .sortedBy { it.data.time }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao acessar o cofre: ${e.message}")
            return@withContext
        }

        if (pendentes.isEmpty()) {
            Log.d(TAG, "Cofre em dia. Nenhuma despesa para os próximos $daysAhead dias.")
            return@withContext
        }

        // 4. Formatação de Moeda "Samsung-Safe" (Anti-bug de letras sumindo)
        fun formatarSeguro(valor: Double): String {
            return "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor)
        }
        val df = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        val total = pendentes.sumOf { it.valor }

        // 5. Lógica do ID Dinâmico
        // Pegamos o ID da conta mais urgente para o botão "Pagar Agora"
        val primeiraDespesa = pendentes.first()
        val idParaAcao = primeiraDespesa.id

        // 6. Montagem do Conteúdo
        val tituloVIP = "Ronda Financeira: ${pendentes.size} Pendências"
        val mensagemCurta = "Total de ${formatarSeguro(total)} vencendo em breve."

        val detalhesBuilder = StringBuilder()
        detalhesBuilder.append("Resumo dos próximos $daysAhead dias:\n\n")

        pendentes.take(5).forEach { d ->
            detalhesBuilder.append("• ${df.format(d.data)} | ${d.descricao}: ${formatarSeguro(d.valor)}\n")
        }

        if (pendentes.size > 5) {
            detalhesBuilder.append("\n+ ${pendentes.size - 5} outras contas no app.")
        }

        // 7. Disparo VIP
        try {
            NotificacaoVIPHelper.enviarAlertaVencimento(
                context = appContext,
                titulo = tituloVIP,
                mensagemCurta = mensagemCurta,
                detalhes = detalhesBuilder.toString(),
                notificacaoId = idParaAcao.toInt() // <-- AGORA ENVIA O ID REAL (Ex: 42), NÃO MAIS 999
            )
            Log.d(TAG, "Notificação enviada para a despesa ID: $idParaAcao")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar notificação", e)
        }
    }

    private fun buildWindowDates(daysAhead: Int): Pair<Date, Date> {
        val cal = Calendar.getInstance()

        val inicio = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val fim = cal.apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        return inicio to fim
    }
}