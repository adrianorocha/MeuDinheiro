package com.meudinheiro.domain

import com.meudinheiro.data.TipoDespesa

/**
 * R90 — roteamento dos pontos de entrada do lançamento rápido (atalhos do launcher e botões do widget) para o modo do
 * painel `LancamentoRapidoActivity`. Puro: testado na JVM.
 */
object RoteamentoLancamento {
    /** Atalho "Novo lançamento": abre o painel com o seletor Despesa/Receita (começa em Despesa). */
    const val ACAO_NOVO_LANCAMENTO = "com.meudinheiro.action.NOVO_LANCAMENTO"

    /** Atalho "Nova receita": abre o painel já em Receita. */
    const val ACAO_NOVA_RECEITA = "com.meudinheiro.action.NOVA_RECEITA"

    enum class Modo { DESPESA, RECEITA }

    /**
     * Modo inicial do painel. A ação do atalho tem prioridade; senão vale o extra `tipo` (`CREDITO` = receita, como o widget
     * envia). Qualquer outra coisa (ausente, desconhecida, minúsculas) cai no padrão seguro: Despesa.
     */
    fun modoInicial(acao: String?, tipoExtra: String?): Modo = when {
        acao == ACAO_NOVA_RECEITA -> Modo.RECEITA
        acao == ACAO_NOVO_LANCAMENTO -> Modo.DESPESA
        tipoExtra == TipoDespesa.CREDITO.name -> Modo.RECEITA
        else -> Modo.DESPESA
    }

    /** Valor do extra `tipo` que o widget envia para cada modo. */
    fun tipoDoExtra(modo: Modo): String = if (modo == Modo.RECEITA) TipoDespesa.CREDITO.name else TipoDespesa.DEBITO.name

    /** Ações que o painel aceita vindas de atalho estático. */
    fun acaoConhecida(acao: String?): Boolean = acao == ACAO_NOVO_LANCAMENTO || acao == ACAO_NOVA_RECEITA
}
