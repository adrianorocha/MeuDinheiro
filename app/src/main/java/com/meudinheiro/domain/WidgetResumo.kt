package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import java.util.Date

/** Uma meta já pronta para o widget (percentual 0..100, sem divisão por zero). */
data class MetaWidget(val nome: String, val percentual: Int)

/** Próxima conta a pagar (a mais antiga em aberto: atrasadas vêm primeiro). */
data class ContaWidget(val descricao: String, val data: Date, val valor: Double, val atrasada: Boolean)

/** Orçamento do mês mais próximo de estourar. */
data class OrcamentoWidget(val categoria: String, val percentual: Int, val gasto: Double, val limite: Double)

/** Lançamento que o usuário repete com frequência: vira um botão de 1 toque no widget. */
data class AtalhoWidget(
    val descricao: String,
    val valor: Double,
    val categoria: String,
    val pic: String,
    val conta: String,
    val cartaoId: Int?,
    val vezes: Int
)

/**
 * Tudo o que o widget mostra, calculado de uma vez a partir do banco (fonte única da verdade).
 * Função pura: não toca em Android, por isso é testada sem emulador.
 */
data class WidgetResumo(
    val saldoTotal: Double,
    val metas: List<MetaWidget>,
    /** Contas a pagar atrasadas. */
    val atrasadas: Int,
    /** Contas a pagar que vencem hoje ou nos próximos dias (janela escolhida por quem monta). */
    val aVencer: Int,
    /** Valor somado de atrasadas + a vencer. */
    val valorPendente: Double,
    val receitasMes: Double = 0.0,
    val despesasMes: Double = 0.0,
    val gastoHoje: Double = 0.0,
    val proximaConta: ContaWidget? = null,
    val orcamento: OrcamentoWidget? = null,
    val atalhos: List<AtalhoWidget> = emptyList(),
) {
    /** Resultado do mês (receitas realizadas − despesas). */
    val resultadoMes: Double get() = Dinheiro.reais(Dinheiro.centavos(receitasMes) - Dinheiro.centavos(despesasMes))

    companion object {
        const val MAX_METAS = 3
        const val MAX_ATALHOS = 2
        private const val DIAS_ATALHO = 90
        private const val DIA_MS = 86_400_000L

        fun percentual(valorGuardado: Double, valorObjetivo: Double): Int =
            if (valorObjetivo <= 0.0) 0 else ((valorGuardado / valorObjetivo) * 100).toInt().coerceIn(0, 100)

        /**
         * @param saldos saldo de cada conta (R3: já derivado do extrato).
         * @param pendencias contas a pagar em aberto (atrasadas + próximas), sem cartão de crédito.
         * @param historico todos os lançamentos (receitas/despesas do mês, gasto de hoje, orçamentos e atalhos).
         */
        fun montar(
            saldos: List<Double>,
            metas: List<Meta>,
            pendencias: List<Despesa>,
            agora: Long,
            historico: List<Despesa> = emptyList(),
            orcamentos: List<Orcamento> = emptyList(),
        ): WidgetResumo {
            val hoje = Financas.inicioDoDia(agora)
            val emAberto = pendencias.filter { !it.pago }.sortedBy { it.data.time }
            val atrasadas = emAberto.count { it.data.time < hoje }
            // Metas em andamento primeiro, das mais adiantadas para as mais distantes; concluídas só se sobrar espaço.
            val ordenadas = metas
                .map { MetaWidget(it.nome, percentual(it.valorGuardado, it.valorObjetivo)) }
                .sortedWith(compareBy<MetaWidget> { it.percentual >= 100 }.thenByDescending { it.percentual }.thenBy { it.nome })

            val mes = Financas.mesDe(agora)
            val ano = Financas.anoDe(agora)
            val inicioMes = Financas.inicioDoMes(mes, ano)
            val fimMes = Financas.fimDoMes(mes, ano)
            val kpisMes = Financas.kpisPeriodo(historico, inicioMes, fimMes)
            val kpisHoje = Financas.kpisPeriodo(historico, hoje, Financas.fimDoDia(hoje))

            return WidgetResumo(
                saldoTotal = Dinheiro.somar(saldos),
                metas = ordenadas.take(MAX_METAS),
                atrasadas = atrasadas,
                aVencer = emAberto.size - atrasadas,
                valorPendente = Dinheiro.somar(emAberto.map { it.valor }),
                receitasMes = kpisMes.receitasRealizadas,
                despesasMes = kpisMes.despesasTotal,
                gastoHoje = kpisHoje.despesasTotal,
                proximaConta = emAberto.firstOrNull()?.let { ContaWidget(it.descricao, it.data, it.valor, it.data.time < hoje) },
                orcamento = orcamentoMaisCritico(orcamentos, historico, inicioMes, fimMes),
                atalhos = atalhos(historico, agora),
            )
        }

        private fun orcamentoMaisCritico(orcamentos: List<Orcamento>, historico: List<Despesa>, inicio: Long, fim: Long): OrcamentoWidget? =
            orcamentos
                .filter { it.valorLimite > 0 }
                .map { it to Financas.progressoOrcamento(it.categoria, it.valorLimite, historico, inicio, fim) }
                .maxByOrNull { it.second.percentual }
                ?.let { (o, p) -> OrcamentoWidget(o.categoria, (p.percentual * 100).toInt(), p.gasto, p.limite) }

        /**
         * Despesas comuns (conta ou cartão) que se repetem — mesma descrição, valor e origem — nos últimos 90 dias.
         * Os mais frequentes viram atalhos; em empate, o mais recente.
         */
        fun atalhos(historico: List<Despesa>, agora: Long): List<AtalhoWidget> {
            val desde = agora - DIAS_ATALHO * DIA_MS
            return historico
                .filter { it.natureza == Natureza.NORMAL && it.tipo == TipoDespesa.DEBITO && it.data.time in desde..agora && !Financas.ehGrupoDeLancamentos(it.grupoId) }
                .groupBy { Triple(Texto.normalizar(it.descricao), Dinheiro.centavos(it.valor), it.cartaoId ?: it.conta) }
                .filterValues { it.size >= 2 }
                .values
                .sortedWith(compareByDescending<List<Despesa>> { it.size }.thenByDescending { g -> g.maxOf { it.data.time } })
                .take(MAX_ATALHOS)
                .map { g ->
                    val d = g.maxByOrNull { it.data.time }!!
                    AtalhoWidget(d.descricao, d.valor, d.categoria, d.pic, d.conta, d.cartaoId, g.size)
                }
        }
    }
}
