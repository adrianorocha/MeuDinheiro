package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar
import java.util.UUID

/**
 * Regras financeiras puras (sem Android/Room) — espelham `docs/CONTRATO_DADOS.md` (R1–R17).
 * Mantidas sem dependências para serem testadas em JVM e reproduzidas no portal web.
 */
object Financas {

    // ---------------------------------------------------------------- datas

    fun diasNoMes(mes: Int, ano: Int): Int =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)

    fun inicioDoDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun fimDoDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = inicioDoDia(ms)
        add(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    /** Primeiro instante do mês (mes 1–12). */
    fun inicioDoMes(mes: Int, ano: Int): Long = Calendar.getInstance().apply {
        clear(); set(ano, mes - 1, 1, 0, 0, 0)
    }.timeInMillis

    /** Último instante do mês (mes 1–12). */
    fun fimDoMes(mes: Int, ano: Int): Long = Calendar.getInstance().apply {
        clear(); set(ano, mes - 1, 1, 0, 0, 0)
        add(Calendar.MONTH, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    fun mesDe(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.MONTH) + 1
    fun anoDe(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)

    /** Soma [meses] preservando o dia original (limitado ao último dia do mês) — sem "deriva" 31→28→28. */
    fun somarMeses(ms: Long, meses: Int, diaOriginal: Int? = null): Long {
        val origem = Calendar.getInstance().apply { timeInMillis = ms }
        val dia = diaOriginal ?: origem.get(Calendar.DAY_OF_MONTH)
        val alvo = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, meses)
        }
        alvo.set(Calendar.DAY_OF_MONTH, minOf(dia, alvo.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return alvo.timeInMillis
    }

    // ------------------------------------------------------- R3 / R4 saldos

    /** R3 — saldo derivado do extrato: lançamentos pagos, da conta, fora de cartão. */
    fun saldoConta(numeroConta: String, lancamentos: Iterable<Movimento>): Double =
        Dinheiro.reais(
            lancamentos
                .filter { it.conta == numeroConta && it.pago && it.semCartao }
                .sumOf { it.centavosAssinados }
        )

    /** R4 — limite disponível = limite total − (compras em aberto − estornos em aberto). */
    fun limiteDisponivel(
        cartao: Cartao,
        despesasDoCartao: Iterable<Movimento>,
        idsDoGrupo: Set<Int> = setOf(cartao.id)
    ): Double {
        val emAberto = despesasDoCartao
            .filter { it.cartaoId in idsDoGrupo && !it.pago }
            .sumOf { it.centavosAssinados } // saídas negativas, estornos positivos
        return Dinheiro.reais(Dinheiro.centavos(cartao.limiteTotal) + emAberto)
    }

    // ------------------------------------------------------------ R5 KPIs

    data class Kpis(
        val receitasRealizadas: Double = 0.0,
        val receitasPrevistas: Double = 0.0,
        val despesasTotal: Double = 0.0,
        val despesasPagas: Double = 0.0,
        val despesasPendentes: Double = 0.0
    ) {
        val resultado: Double get() = Dinheiro.reais(Dinheiro.centavos(receitasRealizadas) - Dinheiro.centavos(despesasTotal))
        val taxaPoupanca: Double get() = if (receitasRealizadas > 0) resultado / receitasRealizadas else 0.0
    }

    /** R5 — só lançamentos de natureza NORMAL, no intervalo [inicio, fim] (inclusive) pela data. */
    fun kpisPeriodo(lancamentos: Iterable<Movimento>, inicio: Long?, fim: Long?): Kpis {
        var recReal = 0L; var recPrev = 0L; var despTotal = 0L; var despPagas = 0L
        for (l in lancamentos) {
            if (l.natureza != Natureza.NORMAL) continue
            if (inicio != null && l.dataMs < inicio) continue
            if (fim != null && l.dataMs > fim) continue
            val c = Dinheiro.centavos(l.valor)
            if (l.ehEntrada) {
                if (l.semCartao) {
                    if (l.pago) recReal += c else recPrev += c
                } else {
                    despTotal -= c // estorno no cartão abate a despesa
                    if (l.pago) despPagas -= c
                }
            } else {
                despTotal += c
                if (l.pago) despPagas += c
            }
        }
        return Kpis(
            receitasRealizadas = Dinheiro.reais(recReal),
            receitasPrevistas = Dinheiro.reais(recPrev),
            despesasTotal = Dinheiro.reais(despTotal),
            despesasPagas = Dinheiro.reais(despPagas),
            despesasPendentes = Dinheiro.reais(despTotal - despPagas)
        )
    }

    // --------------------------------------------------------- R6 / R7 fatura

    /** Fatura identificada pelo mês (1–12) e ano de **fechamento**. */
    data class FaturaRef(val mes: Int, val ano: Int) {
        fun proxima(): FaturaRef = if (mes == 12) FaturaRef(1, ano + 1) else FaturaRef(mes + 1, ano)
        fun anterior(): FaturaRef = if (mes == 1) FaturaRef(12, ano - 1) else FaturaRef(mes - 1, ano)
        fun deslocar(meses: Int): FaturaRef {
            val idx = ano * 12 + (mes - 1) + meses
            return FaturaRef(idx % 12 + 1, idx / 12)
        }
    }

    fun diaDeFechamento(diaFechamento: Int, ref: FaturaRef): Int =
        diaFechamento.coerceIn(1, 31).coerceAtMost(diasNoMes(ref.mes, ref.ano))

    fun dataFechamento(diaFechamento: Int, ref: FaturaRef): Long =
        fimDoDia(Calendar.getInstance().apply {
            clear(); set(ref.ano, ref.mes - 1, diaDeFechamento(diaFechamento, ref), 12, 0, 0)
        }.timeInMillis)

    fun dataVencimento(diaFechamento: Int, diaVencimento: Int, ref: FaturaRef): Long {
        val refVenc = if (diaVencimento > diaFechamento) ref else ref.proxima()
        val dia = diaVencimento.coerceIn(1, 31).coerceAtMost(diasNoMes(refVenc.mes, refVenc.ano))
        return fimDoDia(Calendar.getInstance().apply {
            clear(); set(refVenc.ano, refVenc.mes - 1, dia, 12, 0, 0)
        }.timeInMillis)
    }

    /** R6 — a que fatura pertence uma compra feita em [dataMs]. */
    fun faturaDaCompra(dataMs: Long, diaFechamento: Int): FaturaRef {
        val mesCompra = FaturaRef(mesDe(dataMs), anoDe(dataMs))
        val dia = Calendar.getInstance().apply { timeInMillis = dataMs }.get(Calendar.DAY_OF_MONTH)
        return if (dia <= diaDeFechamento(diaFechamento, mesCompra)) mesCompra else mesCompra.proxima()
    }

    data class ResumoFatura(
        val ref: FaturaRef,
        val itens: List<Despesa>,
        val total: Double,
        val pendente: Double,
        val paga: Boolean,
        val fechamento: Long,
        val vencimento: Long,
        val itensPendentes: List<Despesa>
    )

    /** [idsDoGrupo]: cartão principal + virtuais (R18) — a fatura é única para o grupo. */
    fun resumoFatura(
        cartao: Cartao,
        despesasDoCartao: Iterable<Despesa>,
        ref: FaturaRef,
        idsDoGrupo: Set<Int> = setOf(cartao.id)
    ): ResumoFatura = resumoFatura(idsDoGrupo, cartao.diaFechamento, cartao.diaVencimento, despesasDoCartao, ref)

    fun resumoFatura(
        cartaoId: Int,
        diaFechamento: Int,
        diaVencimento: Int,
        despesasDoCartao: Iterable<Despesa>,
        ref: FaturaRef
    ): ResumoFatura = resumoFatura(setOf(cartaoId), diaFechamento, diaVencimento, despesasDoCartao, ref)

    fun resumoFatura(
        idsDoGrupo: Set<Int>,
        diaFechamento: Int,
        diaVencimento: Int,
        despesasDoCartao: Iterable<Despesa>,
        ref: FaturaRef
    ): ResumoFatura {
        val itens = despesasDoCartao
            .filter { it.cartaoId in idsDoGrupo && faturaDaCompra(it.dataMs, diaFechamento) == ref }
            .sortedByDescending { it.dataMs }
        val pendentes = itens.filter { !it.pago }
        val total = itens.sumOf { -it.centavosAssinados }
        val pend = pendentes.sumOf { -it.centavosAssinados }
        return ResumoFatura(
            ref = ref,
            itens = itens,
            total = Dinheiro.reais(total),
            pendente = Dinheiro.reais(pend),
            paga = itens.isNotEmpty() && pendentes.isEmpty(),
            fechamento = dataFechamento(diaFechamento, ref),
            vencimento = dataVencimento(diaFechamento, diaVencimento, ref),
            itensPendentes = pendentes
        )
    }

    /** Faturas em aberto (com valor a pagar > 0), da mais antiga para a mais nova. */
    fun faturasEmAberto(
        cartao: Cartao,
        despesasDoCartao: Iterable<Despesa>,
        idsDoGrupo: Set<Int> = setOf(cartao.id)
    ): List<ResumoFatura> =
        despesasDoCartao
            .filter { it.cartaoId in idsDoGrupo && !it.pago }
            .map { faturaDaCompra(it.dataMs, cartao.diaFechamento) }
            .distinct()
            .sortedWith(compareBy({ it.ano }, { it.mes }))
            .map { resumoFatura(cartao, despesasDoCartao, it, idsDoGrupo) }
            .filter { it.pendente > 0 }

    /** Ids do grupo do cartão: o principal e todos os virtuais ligados a ele. */
    fun idsDoGrupo(principal: Cartao, todos: Iterable<Cartao>): Set<Int> =
        todos.filter { it.id == principal.id || it.cartaoPrincipalId == principal.id }.map { it.id }.toSet() + principal.id

    // ------------------------------------------------------------- R8 parcelas

    /** R8 — divide [modelo] em [n] parcelas; a soma é exata (resto na última); sem deriva de datas. */
    fun parcelar(modelo: Despesa, n: Int, agora: Long, grupoId: String = UUID.randomUUID().toString()): List<Despesa> {
        require(n >= 1) { "Número de parcelas inválido" }
        val totalC = Dinheiro.centavos(modelo.valor)
        require(totalC > 0) { "Valor deve ser maior que zero" }
        val baseC = totalC / n
        val resto = totalC - baseC * n
        val diaOriginal = Calendar.getInstance().apply { timeInMillis = modelo.dataMs }.get(Calendar.DAY_OF_MONTH)
        val noCartao = modelo.cartaoId != null && modelo.cartaoId != 0

        return (1..n).map { i ->
            val dataMs = somarMeses(modelo.dataMs, i - 1, diaOriginal)
            modelo.copy(
                id = 0,
                descricao = if (n == 1) modelo.descricao else "${modelo.descricao} ($i/$n)",
                valor = Dinheiro.reais(if (i == n) baseC + resto else baseC),
                data = java.util.Date(dataMs),
                mes = mesDe(dataMs),
                ano = anoDe(dataMs),
                pago = if (noCartao) false else dataMs <= agora,
                grupoId = if (n == 1) modelo.grupoId else grupoId
            )
        }
    }

    // ------------------------------------------------------- R16 recorrências

    /**
     * Datas (ms, meio-dia) das ocorrências que ainda precisam ser lançadas para [regra] até [hoje].
     * Nunca lançadas → só o mês corrente; já lançadas → meses perdidos desde o último (máx. 12).
     */
    fun ocorrenciasPendentes(regra: DespesaFixa, hoje: Long): List<Long> {
        val atual = FaturaRef(mesDe(hoje), anoDe(hoje))
        val primeiro = regra.ultimaDataLancamento
            ?.let { FaturaRef(mesDe(it.time), anoDe(it.time)).proxima() }
            ?: atual
        val inicio = maxOf(primeiro.ano * 12 + primeiro.mes - 1, atual.ano * 12 + atual.mes - 1 - 11)
        val fimIdx = atual.ano * 12 + atual.mes - 1
        val limite = fimDoDia(hoje)

        return (inicio..fimIdx).mapNotNull { idx ->
            val ref = FaturaRef(idx % 12 + 1, idx / 12)
            val dia = regra.diaVencimento.coerceIn(1, 31).coerceAtMost(diasNoMes(ref.mes, ref.ano))
            val data = Calendar.getInstance().apply { clear(); set(ref.ano, ref.mes - 1, dia, 12, 0, 0) }.timeInMillis
            data.takeIf { it <= limite }
        }
    }

    // --------------------------------------------------------- R13 orçamento

    enum class StatusOrcamento { OK, ATENCAO, ESTOURADO }

    data class ProgressoOrcamento(val gasto: Double, val limite: Double, val percentual: Double, val status: StatusOrcamento)

    private fun mesmaCategoria(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    fun progressoOrcamento(
        categoria: String,
        limite: Double,
        lancamentos: Iterable<Movimento>,
        inicio: Long,
        fim: Long
    ): ProgressoOrcamento {
        val gastoC = lancamentos
            .filter { it.natureza == Natureza.NORMAL && it.dataMs in inicio..fim && mesmaCategoria(it.categoria, categoria) }
            .filter { !it.ehEntrada || !it.semCartao } // despesas e estornos de cartão; receitas comuns ficam de fora
            .sumOf { -it.centavosAssinados }
            .coerceAtLeast(0)
        val limiteC = Dinheiro.centavos(limite)
        val pct = if (limiteC > 0) gastoC.toDouble() / limiteC else 0.0
        val status = when {
            pct >= 1.0 -> StatusOrcamento.ESTOURADO
            pct >= 0.8 -> StatusOrcamento.ATENCAO
            else -> StatusOrcamento.OK
        }
        return ProgressoOrcamento(Dinheiro.reais(gastoC), Dinheiro.arredondar(limite), pct, status)
    }

    // ------------------------------------------------------------- R14 / R15

    /** R14 — patrimônio líquido. */
    fun patrimonioLiquido(
        saldosContas: Iterable<Double>,
        valorInvestimentos: Iterable<Double>,
        valorMetas: Iterable<Double>,
        comprasCartao: Iterable<Movimento>
    ): Double {
        val ativos = saldosContas.sumOf { Dinheiro.centavos(it) } +
            valorInvestimentos.sumOf { Dinheiro.centavos(it) } +
            valorMetas.sumOf { Dinheiro.centavos(it) }
        val faturasAbertas = comprasCartao
            .filter { !it.semCartao && !it.pago }
            .sumOf { -it.centavosAssinados }
        return Dinheiro.reais(ativos - faturasAbertas)
    }

    enum class StatusMes(val rotulo: String) {
        SEGURO("Mês Seguro"), ATENCAO("Atenção ao Caixa"), RISCO("Alerta de Risco!")
    }

    data class Previsao(
        val saldoAtual: Double,
        val receitasPrevistas: Double,
        val despesasPendentes: Double,
        val faturasAteVencimento: Double,
        val saldoLivrePrevisto: Double,
        val margem: Double,
        val status: StatusMes
    ) {
        /** Total de contas a pagar até o fim do mês (despesas de conta + faturas). */
        val contasAPagar: Double get() = Dinheiro.reais(Dinheiro.centavos(despesasPendentes) + Dinheiro.centavos(faturasAteVencimento))
    }

    /** R15 — quanto sobra até o fim do mês ([fimDoMes]) se tudo que está pendente for pago/recebido. */
    fun previsaoMes(
        saldoAtual: Double,
        despesas: List<Despesa>,
        cartoes: List<Cartao>,
        fimDoMes: Long
    ): Previsao {
        val normais = despesas.filter { it.natureza == Natureza.NORMAL && it.semCartao && !it.pago && it.dataMs <= fimDoMes }
        val receitasC = normais.filter { it.ehEntrada }.sumOf { Dinheiro.centavos(it.valor) }
        val despesasC = normais.filter { !it.ehEntrada }.sumOf { Dinheiro.centavos(it.valor) }

        // Um grupo (físico + virtuais) tem UMA fatura: soma só pelos principais (R18).
        val faturasC = cartoes.filter { it.cartaoPrincipalId == null }.sumOf { cartao ->
            val ids = idsDoGrupo(cartao, cartoes)
            faturasEmAberto(cartao, despesas.filter { it.cartaoId in ids }, ids)
                .filter { it.vencimento <= fimDoMes }
                .sumOf { Dinheiro.centavos(it.pendente) }
        }

        val saldoC = Dinheiro.centavos(saldoAtual)
        val livreC = saldoC + receitasC - despesasC - faturasC
        val margem = when {
            saldoC > 0 -> livreC.toDouble() / saldoC
            livreC > 0 -> 1.0
            else -> 0.0
        }
        val status = when {
            margem > 0.4 -> StatusMes.SEGURO
            margem > 0.05 -> StatusMes.ATENCAO
            else -> StatusMes.RISCO
        }
        return Previsao(
            saldoAtual = Dinheiro.reais(saldoC),
            receitasPrevistas = Dinheiro.reais(receitasC),
            despesasPendentes = Dinheiro.reais(despesasC),
            faturasAteVencimento = Dinheiro.reais(faturasC),
            saldoLivrePrevisto = Dinheiro.reais(livreC),
            margem = margem,
            status = status
        )
    }

    // ------------------------------------------------------------------ R17

    enum class NivelSaude { SAUDAVEL, ALERTA, PERIGO }

    data class Saude(val consumoReceita: Double, val variacaoGastos: Double, val nivel: NivelSaude, val sobra: Double)

    fun saudeFinanceira(receitas: Double, despesas: Double, despesasMesAnterior: Double): Saude {
        val consumo = when {
            receitas > 0 -> despesas / receitas
            despesas > 0 -> 1.0
            else -> 0.0
        }
        val variacao = if (despesasMesAnterior > 0) (despesas - despesasMesAnterior) / despesasMesAnterior * 100 else 0.0
        val nivel = when {
            consumo >= 0.9 -> NivelSaude.PERIGO
            consumo >= 0.7 -> NivelSaude.ALERTA
            else -> NivelSaude.SAUDAVEL
        }
        return Saude(consumo, variacao, nivel, Dinheiro.reais(Dinheiro.centavos(receitas) - Dinheiro.centavos(despesas)))
    }

    // ------------------------------------------------------------ investimentos

    fun rendimento(valorInvestido: Double, valorAtual: Double): Double =
        Dinheiro.reais(Dinheiro.centavos(valorAtual) - Dinheiro.centavos(valorInvestido))

    fun rentabilidadePercentual(valorInvestido: Double, valorAtual: Double): Double =
        if (valorInvestido > 0) (valorAtual - valorInvestido) / valorInvestido * 100 else 0.0
}

/** Atalho semântico para código de UI. */
val TipoDespesa.ehEntrada: Boolean get() = this == TipoDespesa.CREDITO
