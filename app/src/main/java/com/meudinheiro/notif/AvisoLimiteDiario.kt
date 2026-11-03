package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.domain.ProjecaoFinanceira
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * R51 — aviso "Hoje você já passou do limite", disparado pela ronda diária (mesmo agendamento e
 * mesma chave geral de notificações de [DespesasDevidas]). Tem chave própria para desligar
 * (Configurações → Motor de alertas). Só avisa quando o gasto de hoje estourou o limite diário
 * (não avisa em "mês no vermelho", que o card já mostra).
 */
object AvisoLimiteDiario {
    private const val TAG = "AvisoLimiteDiario"
    private const val ID_AVISO = 51051

    suspend fun verificarEExibir(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val prefs = UserPreferences(appContext)
        if (!(prefs.notifEnabledFlow.firstOrNull() ?: false)) return@withContext
        if (!(prefs.limiteDiarioAvisoFlow.firstOrNull() ?: true)) return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@withContext

        try {
            val repo = MainRepository(appContext)
            val contas = repo.getTodasContas().first().map { ProjecaoFinanceira.ContaRef(it.id, it.conta, it.banco) }
            val r = ProjecaoFinanceira.limiteDiarioDosDados(
                contas = contas,
                cartoes = repo.cartoesFlow().first(),
                despesas = repo.todasDespesasFlow.first(),
                fixas = repo.obterTodasRecorrencias(),
                agora = System.currentTimeMillis(),
                ateProximaReceita = prefs.limiteDiarioAteReceitaFlow.firstOrNull() ?: false
            )
            if (r.mesNoVermelho || r.gastoHojeCentavos <= r.limiteDiarioCentavos) return@withContext
            Notificacoes.mostrar(
                appContext,
                Aviso(
                    tipo = TipoAviso.ORCAMENTO_ESTOURADO,
                    titulo = "Limite diário ultrapassado",
                    resumo = r.mensagem,
                    id = ID_AVISO,
                    textoPublico = "Aviso de limite diário"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar o limite diário", e)
        }
    }
}
