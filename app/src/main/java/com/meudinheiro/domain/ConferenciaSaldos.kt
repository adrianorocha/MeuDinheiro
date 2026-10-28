package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.text.NumberFormat
import java.util.Locale

/**
 * Conferência de saldos (somente leitura, sem Android/Room): recalcula do extrato o que o app grava em cache
 * (saldo da conta — R3; limite do cartão — R4/R18), decompõe o saldo por natureza e lista inconsistências.
 * Nunca altera nem apaga dados.
 */
object ConferenciaSaldos {

    const val DIAS_A_VENCER = 30

    // ------------------------------------------------------------------ relatório

    enum class LinhaTipo(val rotulo: String) {
        SALDO_INICIAL("Saldo inicial"),
        RECEITAS("Receitas recebidas"),
        DESPESAS("Despesas pagas"),
        PAGAMENTO_FATURA("Pagamentos de fatura"),
        TRANSFERENCIA_ENTRADA("Transferências recebidas"),
        TRANSFERENCIA_SAIDA("Transferências enviadas"),
        APORTE_META("Aportes em metas"),
        RESGATE_META("Resgates de metas"),
        AJUSTE("Ajustes de saldo"),
        OUTROS("Outros (natureza desconhecida)")
    }

    /** Parcela do saldo: [centavos] com sinal (entrada +, saída −) e quantos lançamentos a compõem. */
    data class Linha(val tipo: LinhaTipo, val centavos: Long, val quantidade: Int) {
        val valor: Double get() = Dinheiro.reais(centavos)
    }

    data class Pendencias(
        val receitasPrevistasC: Long = 0,
        val despesasAtrasadasC: Long = 0,
        val despesasAVencerC: Long = 0,
        val despesasFuturasC: Long = 0,
        val comprasCartaoEmAbertoC: Long = 0
    ) {
        val despesasPendentesC: Long get() = despesasAtrasadasC + despesasAVencerC + despesasFuturasC
    }

    data class ContaAuditada(
        val contaId: Int,
        val conta: String,
        val banco: String,
        val saldoGravado: Double,
        val saldoCalculado: Double,
        val decomposicao: List<Linha>,
        val pendencias: Pendencias
    ) {
        val diferenca: Double get() = Dinheiro.reais(safeC(saldoGravado) - safeC(saldoCalculado))
        val divergente: Boolean get() = saldoGravado != saldoCalculado
        /** Soma da decomposição (centavos) — tem de ser igual a [saldoCalculado]. */
        val somaDecomposicaoC: Long get() = decomposicao.sumOf { it.centavos }
    }

    data class CartaoGravado(val cartaoId: Int, val nome: String, val limiteGravado: Double)

    data class GrupoCartaoAuditado(
        val principalId: Int,
        val nome: String,
        val finalCartao: String,
        val contaId: Int,
        val gravados: List<CartaoGravado>,
        val limiteTotal: Double,
        val limiteCalculado: Double,
        val emAbertoFatura: Double,
        val parcelasFuturas: Double
    ) {
        /** Limite gravado do cartão principal. */
        val limiteGravado: Double get() = gravados.firstOrNull { it.cartaoId == principalId }?.limiteGravado ?: gravados.firstOrNull()?.limiteGravado ?: 0.0
        val diferenca: Double get() = Dinheiro.reais(safeC(limiteGravado) - safeC(limiteCalculado))
        val divergente: Boolean get() = gravados.any { it.limiteGravado != limiteCalculado }
        val emAbertoTotal: Double get() = Dinheiro.reais(safeC(emAbertoFatura) + safeC(parcelasFuturas))
    }

    data class Totais(
        val entradasRealizadasC: Long,
        val entradasPrevistasC: Long,
        val saidasPagasC: Long,
        val saidasPendentesC: Long,
        val saldoGravadoC: Long,
        val saldoCalculadoC: Long
    ) {
        val entradasRealizadas: Double get() = Dinheiro.reais(entradasRealizadasC)
        val entradasPrevistas: Double get() = Dinheiro.reais(entradasPrevistasC)
        val saidasPagas: Double get() = Dinheiro.reais(saidasPagasC)
        val saidasPendentes: Double get() = Dinheiro.reais(saidasPendentesC)
        val saidasTotal: Double get() = Dinheiro.reais(saidasPagasC + saidasPendentesC)
        val saldoGravado: Double get() = Dinheiro.reais(saldoGravadoC)
        val saldoCalculado: Double get() = Dinheiro.reais(saldoCalculadoC)
        /** Entradas − Saídas, como o cabeçalho do app mostra. */
        val entradasMenosSaidas: Double get() = Dinheiro.reais(entradasRealizadasC - saidasPagasC - saidasPendentesC)
    }

    enum class Severidade { ERRO, AVISO, INFO }

    enum class InconsistenciaTipo(val rotulo: String) {
        CONTA_INEXISTENTE("Conta inexistente"),
        CARTAO_INEXISTENTE("Cartão inexistente"),
        VALOR_INVALIDO("Valor inválido"),
        NATUREZA_DESCONHECIDA("Natureza desconhecida"),
        ID_DUPLICADO("Id duplicado"),
        VIRTUAL_SEM_PRINCIPAL("Cartão virtual sem principal"),
        PARCELAS_INCOMPLETAS("Parcelamento incompleto"),
        PARCELAS_FORA_DE_ORDEM("Parcelas pagas fora de ordem")
    }

    data class Inconsistencia(
        val tipo: InconsistenciaTipo,
        val severidade: Severidade,
        /** Id do lançamento (ou do cartão/conta) envolvido. */
        val id: String,
        val descricao: String
    )

    data class Relatorio(
        val contas: List<ContaAuditada>,
        val cartoes: List<GrupoCartaoAuditado>,
        /** Histórico completo. */
        val totais: Totais,
        /** Mesmo cálculo restrito ao período pedido (nulo se nenhum período foi informado). */
        val totaisPeriodo: Totais?,
        val inconsistencias: List<Inconsistencia>
    ) {
        val saldosDivergentes: Int get() = contas.count { it.divergente }
        val limitesDivergentes: Int get() = cartoes.sumOf { g -> g.gravados.count { it.limiteGravado != g.limiteCalculado } }
        val tudoConfere: Boolean get() = saldosDivergentes == 0 && limitesDivergentes == 0
        val errosDeDados: Int get() = inconsistencias.count { it.severidade != Severidade.INFO }

        /** Frase que explica por que Entradas − Saídas não é o saldo. [fmt] formata valores (ex.: modo privado). */
        fun explicacao(fmt: (Double) -> String = ::moedaPtBr): String = explicar(totais, fmt)
    }

    // ------------------------------------------------------------------ auditoria

    /**
     * @param inicio/@param fim intervalo opcional (inclusive) para [Relatorio.totaisPeriodo], igual ao filtro do cabeçalho.
     */
    fun auditar(
        contas: List<ContaSaldo>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        agora: Long,
        inicio: Long? = null,
        fim: Long? = null
    ): Relatorio {
        val validas = despesas.filter { it.valor.isFinite() }
        return Relatorio(
            contas = contas.map { auditarConta(it, cartoes, validas, agora) },
            cartoes = auditarCartoes(cartoes, validas, agora),
            totais = totais(contas, validas, agora, null, null),
            totaisPeriodo = if (inicio != null || fim != null) totais(contas, validas, agora, inicio, fim) else null,
            inconsistencias = inconsistencias(contas, cartoes, despesas)
        )
    }

    private fun tipoDaLinha(d: Despesa): LinhaTipo = when (d.natureza) {
        Natureza.SALDO_INICIAL -> LinhaTipo.SALDO_INICIAL
        Natureza.NORMAL -> if (d.ehEntrada) LinhaTipo.RECEITAS else LinhaTipo.DESPESAS
        Natureza.PAGAMENTO_FATURA -> LinhaTipo.PAGAMENTO_FATURA
        Natureza.TRANSFERENCIA -> if (d.ehEntrada) LinhaTipo.TRANSFERENCIA_ENTRADA else LinhaTipo.TRANSFERENCIA_SAIDA
        Natureza.APORTE_META -> LinhaTipo.APORTE_META
        Natureza.RESGATE_META -> LinhaTipo.RESGATE_META
        Natureza.AJUSTE -> LinhaTipo.AJUSTE
        else -> LinhaTipo.OUTROS
    }

    private fun auditarConta(c: ContaSaldo, cartoes: List<Cartao>, despesas: List<Despesa>, agora: Long): ContaAuditada {
        // R3: pagos, da conta, fora de cartão.
        val doSaldo = despesas.filter { it.conta == c.conta && it.pago && it.semCartao }
        val linhas = doSaldo.groupBy(::tipoDaLinha).map { (t, itens) ->
            Linha(t, itens.sumOf { it.centavosAssinados }, itens.size)
        }.sortedBy { it.tipo.ordinal }
        val calculado = linhas.sumOf { it.centavos }

        val hoje = Financas.inicioDoDia(agora)
        val limiteAVencer = Financas.fimDoDia(hoje + DIAS_A_VENCER * 86_400_000L)
        var recPrev = 0L; var atras = 0L; var aVencer = 0L; var futuras = 0L
        despesas.filter { it.conta == c.conta && it.semCartao && !it.pago && it.natureza == Natureza.NORMAL }.forEach {
            val v = Dinheiro.centavos(it.valor)
            if (it.ehEntrada) recPrev += v
            else when {
                it.dataMs < hoje -> atras += v
                it.dataMs <= limiteAVencer -> aVencer += v
                else -> futuras += v
            }
        }
        val idsCartoesDaConta = cartoes.filter { it.contaId == c.id }.map { it.id }.toSet()
        val abertas = -despesas.filter { it.cartaoId != null && it.cartaoId in idsCartoesDaConta && !it.pago }
            .sumOf { it.centavosAssinados }

        return ContaAuditada(
            contaId = c.id, conta = c.conta, banco = c.banco,
            saldoGravado = c.saldo, saldoCalculado = Dinheiro.reais(calculado),
            decomposicao = linhas,
            pendencias = Pendencias(recPrev, atras, aVencer, futuras, abertas)
        )
    }

    private fun auditarCartoes(cartoes: List<Cartao>, despesas: List<Despesa>, agora: Long): List<GrupoCartaoAuditado> =
        cartoes.filter { it.cartaoPrincipalId == null }.map { principal ->
            val ids = Financas.idsDoGrupo(principal, cartoes)
            val doGrupo = despesas.filter { it.cartaoId in ids }
            val emAberto = doGrupo.filter { !it.pago }.sumOf { it.centavosAssinados }
            val limite = Dinheiro.reais(safeC(principal.limiteTotal) + emAberto)
            val comp = runCatching { Financas.comprometimentoCartao(principal, doGrupo, agora, ids) }.getOrNull()
            GrupoCartaoAuditado(
                principalId = principal.id, nome = principal.nome, finalCartao = principal.finalCartao, contaId = principal.contaId,
                gravados = cartoes.filter { it.id in ids }.map { CartaoGravado(it.id, it.nome, it.limiteDisponivel) },
                limiteTotal = principal.limiteTotal, limiteCalculado = limite,
                emAbertoFatura = comp?.faturaAtual ?: 0.0, parcelasFuturas = comp?.parcelasFuturas ?: 0.0
            )
        }

    private fun totais(contas: List<ContaSaldo>, despesas: List<Despesa>, agora: Long, inicio: Long?, fim: Long?): Totais {
        val k = Financas.kpisPeriodo(despesas, inicio, fim)
        val calculadoC = contas.sumOf { c -> despesas.filter { it.conta == c.conta && it.pago && it.semCartao }.sumOf { it.centavosAssinados } }
        return Totais(
            entradasRealizadasC = Dinheiro.centavos(k.receitasRealizadas),
            entradasPrevistasC = Dinheiro.centavos(k.receitasPrevistas),
            saidasPagasC = Dinheiro.centavos(k.despesasPagas),
            saidasPendentesC = Dinheiro.centavos(k.despesasPendentes),
            saldoGravadoC = contas.sumOf { safeC(it.saldo) },
            saldoCalculadoC = calculadoC
        )
    }

    // ------------------------------------------------------------------ inconsistências

    private val PARCELA = Regex("""\((\d+)/(\d+)\)\s*$""")

    private fun inconsistencias(contas: List<ContaSaldo>, cartoes: List<Cartao>, despesas: List<Despesa>): List<Inconsistencia> {
        val out = mutableListOf<Inconsistencia>()
        val numeros = contas.map { it.conta }.toSet()
        val idsCartoes = cartoes.map { it.id }.toSet()

        despesas.groupBy { it.id }.filter { it.value.size > 1 }.forEach { (id, l) ->
            out += Inconsistencia(InconsistenciaTipo.ID_DUPLICADO, Severidade.ERRO, id.toString(), "Id $id repetido em ${l.size} lançamentos.")
        }
        cartoes.groupBy { it.id }.filter { it.value.size > 1 }.forEach { (id, l) ->
            out += Inconsistencia(InconsistenciaTipo.ID_DUPLICADO, Severidade.ERRO, "cartao:$id", "Cartão com id $id repetido ${l.size} vezes.")
        }
        despesas.forEach { d ->
            val id = d.id.toString()
            val nome = "\"${d.descricao}\""
            if (d.conta !in numeros) out += Inconsistencia(
                InconsistenciaTipo.CONTA_INEXISTENTE, Severidade.ERRO, id, "$nome aponta para a conta \"${d.conta}\", que não existe."
            )
            if (d.cartaoId != null && d.cartaoId != 0 && d.cartaoId !in idsCartoes) out += Inconsistencia(
                InconsistenciaTipo.CARTAO_INEXISTENTE, Severidade.ERRO, id, "$nome aponta para o cartão ${d.cartaoId}, que não existe."
            )
            if (!d.valor.isFinite() || d.valor <= 0.0) out += Inconsistencia(
                InconsistenciaTipo.VALOR_INVALIDO, Severidade.ERRO, id, "$nome tem valor inválido (${d.valor}); deveria ser maior que zero."
            )
            if (d.natureza !in Natureza.TODAS) out += Inconsistencia(
                InconsistenciaTipo.NATUREZA_DESCONHECIDA, Severidade.ERRO, id, "$nome tem natureza desconhecida \"${d.natureza}\"."
            )
        }
        cartoes.filter { it.cartaoPrincipalId != null && it.cartaoPrincipalId !in idsCartoes }.forEach {
            out += Inconsistencia(
                InconsistenciaTipo.VIRTUAL_SEM_PRINCIPAL, Severidade.ERRO, "cartao:${it.id}",
                "Cartão virtual \"${it.nome}\" aponta para o principal ${it.cartaoPrincipalId}, que não existe."
            )
        }

        // Parcelamentos (informativo): grupo com buracos, não terminando na última parcela, ou paga fora de ordem.
        despesas.filter { it.cartaoId != null || it.natureza == Natureza.NORMAL }
            .filter { LancamentoAcoes.ehGrupoParcelas(it.natureza, it.grupoId) }
            .groupBy { it.grupoId!! }.forEach { (grupo, itens) ->
                val idx = itens.mapNotNull { d -> PARCELA.find(d.descricao)?.let { m -> Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), d) } }
                if (idx.size < itens.size || idx.isEmpty()) return@forEach
                val n = idx.first().second
                if (idx.any { it.second != n }) return@forEach
                val ordem = idx.sortedBy { it.first }
                val nums = ordem.map { it.first }
                val esperado = (nums.first()..n).toList()
                val ref = ordem.first().third.id.toString()
                if (nums != esperado) out += Inconsistencia(
                    InconsistenciaTipo.PARCELAS_INCOMPLETAS, Severidade.INFO, ref,
                    "Parcelamento \"${ordem.first().third.descricao.replace(PARCELA, "").trim()}\" ($grupo) tem ${nums.size} de ${n - nums.first() + 1} parcelas esperadas (faltam algumas)."
                )
                val primeiraAberta = ordem.indexOfFirst { !it.third.pago }
                if (primeiraAberta >= 0 && ordem.drop(primeiraAberta + 1).any { it.third.pago }) out += Inconsistencia(
                    InconsistenciaTipo.PARCELAS_FORA_DE_ORDEM, Severidade.INFO, ref,
                    "Parcelamento \"${ordem.first().third.descricao.replace(PARCELA, "").trim()}\" ($grupo) tem parcela paga depois de uma em aberto."
                )
            }
        return out
    }

    // ------------------------------------------------------------------ explicação

    fun explicar(t: Totais, fmt: (Double) -> String = ::moedaPtBr): String =
        "Entradas (${fmt(t.entradasRealizadas)}) contam só o que já foi recebido. " +
            "Saídas (${fmt(t.saidasTotal)}) somam o que já foi pago (${fmt(t.saidasPagas)}) mais o que ainda está a pagar " +
            "(${fmt(t.saidasPendentes)}: contas pendentes, parcelas futuras e compras de cartão em aberto). " +
            "Por isso Entradas − Saídas (${fmt(t.entradasMenosSaidas)}) não é o saldo (${fmt(t.saldoCalculado)}): " +
            "o saldo só considera o que já entrou e saiu das contas (inclui saldo inicial, transferências e pagamentos de fatura) " +
            "e ignora o que está pendente ou no cartão."

    fun moedaPtBr(v: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)

    private fun safeC(v: Double): Long = if (v.isFinite()) Dinheiro.centavos(v) else 0L
}
