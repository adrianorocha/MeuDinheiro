package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.funcoes.NotificacaoVIPHelper // Importando nosso Helper VIP
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object DespesasDevidas {

    suspend fun verificarEExibir(context: Context) = withContext(Dispatchers.IO) {
        val TAG = "BluMacaw_Despesas"
        Log.d(TAG, "Iniciando ronda financeira...")

        val prefs = UserPreferences(context)

        // 1. Verifica se as notificações estão ligadas nas configs
        val enabled = prefs.notifEnabledFlow.firstOrNull() ?: true
        if (!enabled) return@withContext

        // 2. Checa permissão do Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return@withContext
        }

        val daysAhead = prefs.notifDaysAheadFlow.firstOrNull() ?: 3
        val (inicio, fim) = buildWindowDates(daysAhead)

        // 3. Busca no banco de dados
        val pendentes = try {
            val repo = MainRepository(context)
            repo.getPendentesAVencer(inicio, fim, onlyCredit = false)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao acessar o cofre", e)
            return@withContext
        }

        if (pendentes.isEmpty()) {
            Log.d(TAG, "Cofre em dia. Nenhuma despesa para os próximos $daysAhead dias.")
            return@withContext
        }

// 4. Formatação Limpa (Sem o NumberFormat que buga a Samsung)
        val df = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        val total = pendentes.sumOf { it.valor }

        // Criamos uma função local para formatar a moeda com espaço normal
        fun formatarSeguro(valor: Double): String {
            return "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor)
        }

        // 5. Montagem da Notificação VIP
        val tituloVIP = "Ronda Financeira: ${pendentes.size} Pendências"
        val mensagemCurta = "Total de ${formatarSeguro(total)} vencendo em breve."

        // Criamos a lista detalhada para o "BigText" (estilo expansível)
        val detalhesBuilder = StringBuilder()
        detalhesBuilder.append("Resumo dos próximos $daysAhead dias:\n\n")

        pendentes.sortedBy { it.data.time }.take(5).forEach { d ->
            detalhesBuilder.append("• ${df.format(d.data)} | ${d.descricao}: ${formatarSeguro(d.valor)}\n")
        }

        if (pendentes.size > 5) {
            detalhesBuilder.append("\n+ ${pendentes.size - 5} outras contas no app.")
        }

        // 6. DISPARO USANDO O NOSSO HELPER
        try {
            NotificacaoVIPHelper.enviarAlertaVencimento(
                context = context,
                titulo = tituloVIP,
                mensagemCurta = mensagemCurta,
                detalhes = detalhesBuilder.toString(),
                notificacaoId = 999
            )
            Log.d(TAG, "Notificação VIP enviada com sucesso!")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar notificação", e)
        }
    }
    private fun buildWindowDates(daysAhead: Int): Pair<Date, Date> {
        val calIni = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calFim = Calendar.getInstance().apply {
            timeInMillis = calIni.timeInMillis
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Date(calIni.timeInMillis) to Date(calFim.timeInMillis)
    }
}