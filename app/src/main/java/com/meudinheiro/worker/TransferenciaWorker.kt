package com.meudinheiro.worker // Ajuste para o seu pacote

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meudinheiro.notif.Aviso
import com.meudinheiro.notif.Notificacoes
import com.meudinheiro.notif.TipoAviso
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.ResultadoAgendamento
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
                        val valorFormatado = formatarMoedaBR(agendamento.valor, false)

                        // 2. Executa a transferência de verdade (R9): só é marcada como executada se
                        //    houve saldo; antes era carimbada como "resolvida" mesmo quando falhava.
                        when (val resultado = repository.executarAgendamento(agendamento)) {
                            is ResultadoAgendamento.Executado -> {
                                Notificacoes.mostrar(
                                    applicationContext,
                                    Aviso(
                                        tipo = TipoAviso.SUCESSO, titulo = "Transferência concluída",
                                        resumo = "$valorFormatado enviado para a conta ${agendamento.contaDestino}.",
                                        id = 300_000 + agendamento.id, textoPublico = "Transferência concluída"
                                    )
                                )
                                Log.d("BluMacaw_Worker", "Sucesso Diário: $valorFormatado de ${agendamento.contaOrigem} para ${agendamento.contaDestino}")
                            }

                            is ResultadoAgendamento.Falhou -> {
                                Notificacoes.mostrar(
                                    applicationContext,
                                    Aviso(
                                        tipo = TipoAviso.FALHA, titulo = "Transferência não realizada",
                                        resumo = "$valorFormatado não foi transferido.",
                                        linhas = listOf("${resultado.motivo} O agendamento continua pendente e será tentado novamente."),
                                        id = 300_000 + agendamento.id, textoPublico = "Transferência não realizada"
                                    )
                                )
                                Log.w("BluMacaw_Worker", "Agendamento ${agendamento.id} não executado: ${resultado.motivo}")
                            }
                        }
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
                    Notificacoes.mostrar(
                        applicationContext,
                        Aviso(
                            tipo = TipoAviso.SUCESSO, titulo = "Transação salva",
                            resumo = "$valorFormatado registrado em $destino.", id = 400_000 + id,
                            textoPublico = "Transação salva"
                        )
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