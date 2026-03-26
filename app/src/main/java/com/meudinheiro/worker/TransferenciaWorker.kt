package com.meudinheiro.worker // Ajuste para o seu pacote

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meudinheiro.funcoes.NotificacaoVIPHelper
import com.meudinheiro.funcoes.formatarMoedaBR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransferenciaWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        // Injeção manual do repositório
        val repository = (applicationContext as com.meudinheiro.MyApplication).repository

        // A "Caixa de Marchas" do Worker: Define o que ele vai fazer agora
        val tipoWork = inputData.getString("TIPO_WORK") ?: "DIARIO"

        try {
            when (tipoWork) {

                // ==========================================
                // MARCHA 1: ROTINA DIÁRIA (O que já funcionava)
                // ==========================================
                "DIARIO" -> {
                    val hoje = System.currentTimeMillis()

                    // 1. Busca a fila de trabalho: agendamentos D-0 ou atrasados
                    val agendamentos = repository.obterAgendamentosPendentesSync(hoje)

                    // Se o motor não tem combustível, desliga em paz para poupar bateria
                    if (agendamentos.isEmpty()) {
                        Log.d("BluMacaw_Worker", "Rotina Diária: Nenhuma transferência pendente para hoje.")
                        return@withContext Result.success()
                    }

                    Log.d("BluMacaw_Worker", "Rotina Diária: Processando ${agendamentos.size} agendamento(s).")

                    agendamentos.forEach { agendamento ->
                        // 2. Executa a movimentação no cofre
                        repository.transferirEntreContas(
                            origem = agendamento.contaOrigem,
                            destino = agendamento.contaDestino,
                            valor = agendamento.valor
                        )

                        // 3. Carimba como "Resolvido" para não duplicar envios
                        repository.marcarAgendamentoComoExecutado(agendamento.id)

                        // Formatação Premium
                        val valorFormatado = formatarMoedaBR(agendamento.valor,false)


                        // 4. Dispara o Feedback VIP no A56
                        NotificacaoVIPHelper.enviarAlertaVencimento(
                            context = applicationContext,
                            textoBadge = "SUCESSO",
                            titulo = "Transferência Concluída 🔄",
                            mensagemCurta = "$valorFormatado enviado com sucesso.",
                            detalhes = "A sua transferência automática para a conta '${agendamento.contaDestino}' foi realizada pelo motor Blu Macaw. Valor processado: $valorFormatado.",
                            notificacaoId = agendamento.id
                        )

                        Log.d("BluMacaw_Worker", "Sucesso Diário: $valorFormatado de ${agendamento.contaOrigem} para ${agendamento.contaDestino}")
                    }
                }

                // ==========================================
                // MARCHA 2: FEEDBACK IMEDIATO (Recibo rápido)
                // ==========================================
                "IMEDIATO" -> {
                    // Resgata os dados passados pelo ViewModel na hora do clique
                    val id = inputData.getInt("ID_TRANSACAO", (Math.random() * 1000).toInt())
                    val valor = inputData.getDouble("VALOR", 0.0)
                    val destino = inputData.getString("DESTINO") ?: "Cofre"

                    val valorFormatado = formatarMoedaBR(valor,false)


                    // Apenas dispara a notificação, pois o ViewModel já salvou no banco
                    NotificacaoVIPHelper.enviarAlertaVencimento(
                        context = applicationContext,
                        textoBadge = "REGISTRADO",
                        titulo = "Transação Salva ✅",
                        mensagemCurta = "$valorFormatado registrado.",
                        detalhes = "A sua transação no valor de $valorFormatado envolvendo '$destino' foi guardada com sucesso no app.",
                        notificacaoId = id
                    )

                    Log.d("BluMacaw_Worker", "Sucesso Imediato: Recibo disparado para a transação $id")
                }
            }

            // Missão cumprida em qualquer uma das marchas
            Result.success()

        } catch (e: Exception) {
            Log.e("BluMacaw_Worker", "Erro crítico no motor de transferências: ${e.message}", e)
            Result.retry()
        }
    }
}