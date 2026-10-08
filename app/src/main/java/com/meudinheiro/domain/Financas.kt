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

    /** R41 — saldo individual de um cartão do grupo (o limite total continua compartilhado, R18). */
    data class SaldoCartao(
        val usado: Double,
        val limiteProprio: Double?,
        val disponivel: Double,
        val disponivelGrupo: Double,
        val razao: Double
    )

    fun saldoDoCartao(cartao: Cartao, cartoesDoGrupo: Iterable<Cartao>, despesas: Iterable<Movimento>): SaldoCartao {
        val grupo = cartoesDoGrupo.toList()
        val principal = grupo.firstOrNull { it.id == cartao.idDoGrupo } ?: cartao
        val ids = idsDoGrupo(principal, grupo)
        val usadoCent = despesas
            .filter { it.cartaoId == cartao.id && !it.pago }
            .sumOf { -it.centavosAssinados } // compras positivas, estornos negativos
            .coerceAtLeast(0L)
        val disponivelGrupo = limiteDisponivel(principal, despesas.filter { it.cartaoId in ids }, ids)
        val proprio = cartao.limiteProprio?.takeIf { it > 0.0 }
        val disponivel = if (proprio != null) {
            minOf(Dinheiro.reais(Dinheiro.centavos(proprio) - usadoCent), disponivelGrupo)
        } else disponivelGrupo
        val base = proprio ?: principal.limiteTotal
        val razao = if (base > 0.0) usadoCent.toDouble() / Dinheiro.centavos(base) else 0.0
        return SaldoCartao(Dinheiro.reais(usadoCent), proprio, disponivel, disponivelGrupo, razao)
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

    /**
     * Últimas movimentações de uma conta: lançamentos da própria conta (sem compras de cartão, que só
     * entram no extrato pelo pagamento da fatura), já ocorridos (data até o fim de hoje), mais recentes primeiro.
     * [conta] vazia = todas as contas.
     */
    fun ultimasDaConta(despesas: Iterable<Despesa>, conta: String, agora: Long, limite: Int = 5): List<Despesa> {
        val alvo = conta.trim()
        val fimDeHoje = inicioDoDia(agora) + 86_400_000L - 1
        return despesas
            .filter { it.semCartao && it.dataMs <= fimDeHoje && (alvo.isEmpty() || it.conta.trim().equals(alvo, ignoreCase = true)) }
            .sortedByDescending { it.dataMs }
            .take(limite)
    }

    /** Ids do grupo do cartão: o principal e todos os virtuais ligados a ele. */
    fun idsDoGrupo(principal: Cartao, todos: Iterable<Cartao>): Set<Int> =
        todos.filter { it.id == principal.id || it.cartaoPrincipalId == principal.id }.map { it.id }.toSet() + principal.id

    // ------------------------------------------------------------- R8 parcelas

    /**
     * R8 — divide [modelo] (valor TOTAL) em [n] parcelas; a soma é exata (resto na última); sem deriva de datas.
     * [aPartirDe] (k, padrão 1) cria só as parcelas k..n de uma compra já em andamento: os valores são os das
     * posições k..n da divisão em centavos do total e a parcela k nasce na data de [modelo].
     */
    fun parcelar(
        modelo: Despesa, n: Int, agora: Long,
        grupoId: String = UUID.randomUUID().toString(),
        aPartirDe: Int = 1
    ): List<Despesa> {
        require(n >= 1) { "Número de parcelas inválido" }
        require(aPartirDe in 1..n) { "Parcela atual inválida (1 a $n)" }
        val totalC = Dinheiro.centavos(modelo.valor)
        require(totalC > 0) { "Valor deve ser maior que zero" }
        val baseC = totalC / n
        val resto = totalC - baseC * n
        val diaOriginal = Calendar.getInstance().apply { timeInMillis = modelo.dataMs }.get(Calendar.DAY_OF_MONTH)
        val noCartao = modelo.cartaoId != null && modelo.cartaoId != 0

        return (aPartirDe..n).map { i ->
            val dataMs = somarMeses(modelo.dataMs, i - aPartirDe, diaOriginal)
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

    // ------------------------------------------------- comprometimento do limite

    data class LiberacaoFatura(val ref: FaturaRef, val valor: Double, val vencimento: Long)

    data class ComprometimentoCartao(
        val faturaAtual: Double,
        val parcelasFuturas: Double,
        val emAbertoTotal: Double,
        val liberacaoPorFatura: List<LiberacaoFatura>
    )

    /**
     * Quanto do limite do grupo está comprometido: fatura do ciclo corrente (a de [agora]), faturas de ciclos
     * posteriores (parcelas futuras) e o que volta ao pagar cada fatura (ordenado). Faturas anteriores à atual
     * ainda em aberto entram em [faturaAtual] (já estão vencidas/fechadas).
     */
    fun comprometimentoCartao(
        cartao: Cartao,
        despesasDoGrupo: Iterable<Despesa>,
        agora: Long,
        idsDoGrupo: Set<Int> = setOf(cartao.id)
    ): ComprometimentoCartao {
        val atual = faturaDaCompra(agora, cartao.diaFechamento)
        val abertas = faturasEmAberto(cartao, despesasDoGrupo, idsDoGrupo)
        var atualC = 0L; var futuraC = 0L
        abertas.forEach {
            val c = Dinheiro.centavos(it.pendente)
            if (it.ref.ano * 12 + it.ref.mes > atual.ano * 12 + atual.mes) futuraC += c else atualC += c
        }
        return ComprometimentoCartao(
            faturaAtual = Dinheiro.reais(atualC),
            parcelasFuturas = Dinheiro.reais(futuraC),
            emAbertoTotal = Dinheiro.reais(atualC + futuraC),
            liberacaoPorFatura = abertas.map { LiberacaoFatura(it.ref, it.pendente, it.vencimento) }
        )
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

    // ------------------------------------------------------------------ R42

    /** R42 — como uma compra no cartão é tratada: crédito (consome limite, entra na fatura) ou débito (sai da conta). */
    enum class Modalidade { CREDITO, DEBITO }

    /** "CRÉDITO"/"DÉBITO"/"MÚLTIPLO" → "CREDITO"/"DEBITO"/"MULTIPLO". */
    fun tipoDeCartao(tipo: String): String = semAcento(tipo)

    private fun semAcento(s: String): String =
        java.text.Normalizer.normalize(s.trim().uppercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * CRÉDITO → sempre crédito; DÉBITO → sempre débito; MÚLTIPLO → o que o usuário pediu (padrão crédito).
     * Tipo desconhecido segue o comportamento histórico (crédito).
     */
    fun modalidadeDaCompra(cartao: Cartao, pedida: Modalidade? = null): Modalidade = when (semAcento(cartao.tipo)) {
        "DEBITO" -> Modalidade.DEBITO
        "MULTIPLO" -> pedida ?: Modalidade.CREDITO
        else -> Modalidade.CREDITO
    }

    /**
     * R42 — aplica a modalidade a uma compra feita com [cartao]. Crédito: devolve [compra] como está.
     * Débito: vira lançamento direto na conta do cartão ([contaDoCartao]), sem cartão, pago conforme a data.
     */
    fun aplicarModalidade(compra: Despesa, cartao: Cartao, contaDoCartao: String, pedida: Modalidade?, agora: Long): Despesa =
        if (modalidadeDaCompra(cartao, pedida) == Modalidade.CREDITO) {
            // Compra que volta ao crédito não pode carregar o vínculo de débito de um cartão anterior.
            if (cartaoDeDebito(compra) != null) compra.copy(grupoId = null) else compra
        } else {
            compra.copy(cartaoId = null, conta = contaDoCartao, pago = compra.dataMs <= agora, grupoId = grupoIdDebito(cartao.id))
        }

    // ------------------------------------------------- vínculo de débito (grupoId "debito:<cartaoId>")

    const val PREFIXO_DEBITO = "debito:"

    /** `grupoId` que liga uma compra convertida em débito ao cartão usado. Débito não parcela: não conflita com `parc:`. */
    fun grupoIdDebito(cartaoId: Int): String = "$PREFIXO_DEBITO$cartaoId"

    /** Id do cartão do vínculo `debito:<id>` ou `null` (grupoId ausente, `parc:`, `fixa:`, `fatura:`, `transf:`, `rep:`...). */
    fun cartaoDeDebito(grupoId: String?): Int? =
        grupoId?.takeIf { it.startsWith(PREFIXO_DEBITO) }?.removePrefix(PREFIXO_DEBITO)?.toIntOrNull()

    fun cartaoDeDebito(d: Despesa): Int? = cartaoDeDebito(d.grupoId)

    /** `true` se [grupoId] agrupa lançamentos de verdade (parcelas, repetição, fatura, transferência); `debito:` é só vínculo. */
    fun ehGrupoDeLancamentos(grupoId: String?): Boolean = grupoId != null && cartaoDeDebito(grupoId) == null

    /** Compras do grupo do cartão: crédito (com `cartaoId` do grupo) e débitos vinculados (`debito:<id do grupo>`). */
    data class ComprasDoGrupo(val principalId: Int?, val credito: List<Despesa>, val debito: List<Despesa>)

    fun comprasDoGrupo(cartaoId: Int, cartoes: List<Cartao>, despesas: List<Despesa>): ComprasDoGrupo {
        val alvo = cartoes.firstOrNull { it.id == cartaoId }
        val principal = alvo?.let { a -> cartoes.firstOrNull { it.id == a.idDoGrupo } }
        val ids = principal?.let { idsDoGrupo(it, cartoes) } ?: setOf(cartaoId)
        return ComprasDoGrupo(
            principalId = principal?.id ?: alvo?.idDoGrupo ?: cartaoId,
            credito = despesas.filter { it.cartaoId in ids }.sortedByDescending { it.dataMs },
            debito = despesas.filter { it.cartaoId == null && cartaoDeDebito(it) in ids }.sortedByDescending { it.dataMs }
        )
    }

    /** Débitos vinculados do mês civil da fatura exibida (não entram em total, em aberto, limite nem pagamento). */
    fun debitosDaFatura(debitos: Iterable<Despesa>, ref: FaturaRef): List<Despesa> =
        debitos.filter { mesDe(it.dataMs) == ref.mes && anoDe(it.dataMs) == ref.ano }.sortedByDescending { it.dataMs }

    data class OutrasFaturas(val emAberto: Double, val proxima: FaturaRef?)

    /**
     * Em aberto (líquido) nas faturas diferentes de [exibida] e a fatura em aberto mais próxima para onde ir
     * (a seguinte, senão a anterior mais recente). Só considera itens de cartão do grupo ([ids]).
     */
    fun outrasFaturasEmAberto(
        ids: Set<Int>, diaFechamento: Int, despesas: Iterable<Despesa>, exibida: FaturaRef
    ): OutrasFaturas {
        val porFatura = despesas
            .filter { it.cartaoId in ids && !it.pago }
            .groupBy { faturaDaCompra(it.dataMs, diaFechamento) }
            .filterKeys { it != exibida }
            .mapValues { (_, v) -> v.sumOf { -it.centavosAssinados } }
            .filterValues { it > 0 }
        if (porFatura.isEmpty()) return OutrasFaturas(0.0, null)
        fun idx(r: FaturaRef) = r.ano * 12 + r.mes
        val proxima = porFatura.keys.filter { idx(it) > idx(exibida) }.minByOrNull { idx(it) }
            ?: porFatura.keys.maxByOrNull { idx(it) }
        return OutrasFaturas(Dinheiro.reais(porFatura.values.sum()), proxima)
    }

    /** R42 — diferença a lançar para o saldo do sistema igualar o do banco. */
    data class Ajuste(val tipo: TipoDespesa, val centavos: Long) {
        val valor: Double get() = Dinheiro.reais(centavos)
    }

    /** `null` quando já está igual (ou valores inválidos). CREDITO se o banco tem mais que o sistema, senão DEBITO. */
    fun calcularAjusteSaldo(saldoAtual: Double, saldoReal: Double): Ajuste? {
        if (!saldoAtual.isFinite() || !saldoReal.isFinite()) return null
        val dif = Dinheiro.centavos(saldoReal) - Dinheiro.centavos(saldoAtual)
        if (dif == 0L) return null
        return Ajuste(if (dif > 0) TipoDespesa.CREDITO else TipoDespesa.DEBITO, kotlin.math.abs(dif))
    }

    /** "1.234,56", "-50,00", "R$ 10", "1234.5" → valor (pode ser negativo: conta no vermelho); vazio/inválido → null. */
    fun parseSaldoInformado(texto: String): Double? {
        var t = texto.trim().replace("R$", "").replace(" ", "")
        if (t.isEmpty() || t == "-") return null
        t = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite()) Dinheiro.arredondar(v) else null
    }

    // ------------------------------------------------------------ investimentos

    fun rendimento(valorInvestido: Double, valorAtual: Double): Double =
        Dinheiro.reais(Dinheiro.centavos(valorAtual) - Dinheiro.centavos(valorInvestido))

    fun rentabilidadePercentual(valorInvestido: Double, valorAtual: Double): Double =
        if (valorInvestido > 0) (valorAtual - valorInvestido) / valorInvestido * 100 else 0.0
}

/** Atalho semântico para código de UI. */
val TipoDespesa.ehEntrada: Boolean get() = this == TipoDespesa.CREDITO
