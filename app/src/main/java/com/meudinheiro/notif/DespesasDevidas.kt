package com.meudinheiro.notif

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.meudinheiro.domain.AvisosVencimento
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

        // 2. Atrasadas + a vencer na janela (a lista já vem ordenada pela data)
        val daysAhead = prefs.notifDaysAheadFlow.firstOrNull() ?: 3
        val repo = MainRepository(appContext)
        val pendentes = try {
            repo.listarPendencias(daysAhead, onlyCredit = false)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao acessar o cofre: ${e.message}")
            return@withContext
        }

        val resumo = AvisosVencimento.montar(pendentes, System.currentTimeMillis())
        if (resumo == null) {
            Log.d(TAG, "Cofre em dia. Nenhuma despesa para os próximos $daysAhead dias.")
            return@withContext
        }

        // 3. Um aviso só; o botão "Pagar agora" quita a conta mais urgente
        try {
            Notificacoes.mostrar(
                appContext,
                Aviso(
                    tipo = if (resumo.atrasado) TipoAviso.ATRASADO else TipoAviso.VENCIMENTO,
                    titulo = resumo.titulo,
                    resumo = resumo.resumo,
                    linhas = resumo.linhas,
                    id = resumo.maisUrgente.id.toInt(),
                    despesaId = resumo.maisUrgente.id,
                    textoPublico = "Você tem contas a pagar"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar notificação", e)
        }
    }
}
