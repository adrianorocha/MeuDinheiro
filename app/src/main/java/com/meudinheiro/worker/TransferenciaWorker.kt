package com.meudinheiro.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransferenciaWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Aqui você precisa acessar seu repositório.
        // Se estiver usando injeção de dependência (Hilt/Koin), injete aqui.
        // Caso contrário, pegue da sua classe Application:
        val repository = (applicationContext as com.meudinheiro.MyApplication).repository

        try {
            val hoje = System.currentTimeMillis()

            // 1. Busca agendamentos pendentes para hoje ou datas passadas
            val agendamentos = repository.obterAgendamentosPendentesSync(hoje)

            if (agendamentos.isEmpty()) {
                return@withContext Result.success()
            }

            agendamentos.forEach { agendamento ->
                // 2. Executa a transferência entre as contas
                repository.transferirEntreContas(
                    origem = agendamento.contaOrigem,
                    destino = agendamento.contaDestino,
                    valor = agendamento.valor
                )

                // 3. Marca como executado para não repetir
                repository.marcarAgendamentoComoExecutado(agendamento.id)

                Log.d("TransferenciaWorker", "Sucesso: ${agendamento.valor} de ${agendamento.contaOrigem} para ${agendamento.contaDestino}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("TransferenciaWorker", "Erro ao processar transferências: ${e.message}")
            Result.retry() // Tenta novamente mais tarde se der erro de rede/banco
        }
    }
}