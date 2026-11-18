package com.meudinheiro.notif

import android.content.Context
import android.util.Log
import com.meudinheiro.domain.LembretesVencimento
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.flow.first

/**
 * R91 — lembretes de vencimento com antecedência escolhida (contas a pagar + faturas de cartão), em UMA notificação por
 * execução. Só roda com "Escolher antecedências" ligado (padrão desligado = [DespesasDevidas] de sempre).
 * A decisão do que avisar é de [LembretesVencimento] (pura); aqui só há leitura do banco, idempotência e notificação.
 */
object LembretesVencimentoNotif {
    private const val TAG = "LembretesVencimento"

    /** Id fixo: um novo resumo SUBSTITUI o anterior em vez de empilhar. */
    const val ID_NOTIFICACAO = 0x66000001

    suspend fun executar(context: Context, prefs: UserPreferences) {
        val app = context.applicationContext
        val config = LembretesVencimento.Config(
            antecedencias = prefs.notifAntecMarcadasFlow.first(),
            avisarAtrasadas = prefs.notifAntecAtrasadasFlow.first()
        )
        val agora = System.currentTimeMillis()
        val itens = try {
            val repo = MainRepository(app)
            LembretesVencimento.itens(repo.todasDespesasFlow.first(), repo.cartoesFlow().first())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler vencimentos: ${e.message}")
            return
        }

        // Seleciona e registra as chaves num passo só (trancado): duas execuções seguidas não repetem o aviso.
        val lembretes = LembretesStore.reservar(app, agora) { jaAvisados -> LembretesVencimento.selecionar(itens, agora, config, jaAvisados) }
        val privado = runCatching { prefs.privateModeFlow.first() }.getOrDefault(false)
        val n = LembretesVencimento.montar(lembretes, privado) ?: return

        try {
            Notificacoes.mostrar(
                app,
                Aviso(
                    tipo = if (n.atrasado) TipoAviso.ATRASADO else TipoAviso.VENCIMENTO,
                    titulo = n.titulo,
                    resumo = n.resumo,
                    linhas = n.linhas,
                    id = ID_NOTIFICACAO,
                    despesaId = n.contaParaPagar?.lancamentoId,
                    textoPublico = "Você tem contas a pagar"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao disparar notificação", e)
        }
    }
}

/** R91 — chaves já avisadas (SharedPreferences privado `lembretes_vencimento`, fora do backup; expurgo após 45 dias). */
object LembretesStore {
    const val ARQUIVO = "lembretes_vencimento"
    private const val K_AVISADOS = "avisados"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    /** Lê as chaves, deixa [selecionar] escolher e grava as escolhidas como avisadas, atomicamente. */
    @Synchronized
    fun reservar(
        ctx: Context, agora: Long,
        selecionar: (Set<String>) -> List<LembretesVencimento.Lembrete>
    ): List<LembretesVencimento.Lembrete> {
        val p = prefs(ctx)
        val atual = LembretesVencimento.expurgar(p.getStringSet(K_AVISADOS, emptySet()).orEmpty().toSet(), agora)
        val escolhidos = selecionar(atual)
        p.edit().putStringSet(K_AVISADOS, atual + escolhidos.map { it.chaveAviso }).apply()
        return escolhidos
    }

    @Synchronized
    fun limpar(ctx: Context) { prefs(ctx).edit().remove(K_AVISADOS).apply() }
}
