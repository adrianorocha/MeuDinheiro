package com.meudinheiro.domain

import com.meudinheiro.data.Divida

/**
 * R58 — quitação de dívidas (paridade com o portal `lib/finance/dividas.ts`: mesmas regras e mesmos números nos testes).
 *
 * Tudo em CENTAVOS (`Long`) na lógica; o armazenamento segue o padrão (reais com 2 casas, [Dinheiro.arredondar]).
 * Juros compostos mensais: `juros = saldo × taxa`, arredondado meio para cima a cada mês, em centavos inteiros (a taxa vira
 * "centésimos de ponto percentual": 2,5% a.m. = 250). Dívida e simulação NÃO mexem em saldo de conta: o pagamento é um
 * lançamento comum (despesa) e o saldo devedor é um controle próprio.
 */
object Dividas {

    val TIPOS = setOf("EMPRESTIMO", "FINANCIAMENTO", "CHEQUE_ESPECIAL", "CARTAO_ROTATIVO", "CONSIGNADO", "OUTRA")

    val ROTULOS = mapOf(
        "EMPRESTIMO" to "Empréstimo",
        "FINANCIAMENTO" to "Financiamento",
        "CHEQUE_ESPECIAL" to "Cheque especial",
        "CARTAO_ROTATIVO" to "Cartão (rotativo)",
        "CONSIGNADO" to "Consignado",
        "OUTRA" to "Outra"
    )

    const val CATEGORIA = "Dívidas"
    const val MAX_MESES = 600

    /** Proteção: saldo total acima disto (R$ 1 bilhão) = juros explodindo, a dívida nunca é quitada. */
    const val LIMITE_SALDO_CENTAVOS = 100_000_000_000L
    const val TAXA_MAXIMA_PERCENTUAL = 100.0
    const val SALDO_MAXIMO_REAIS = 1_000_000_000.0

    fun grupoId(id: Int) = "divida:$id"

    enum class Estrategia { MINIMOS, BOLA_DE_NEVE, AVALANCHE }

    /** [saldo] e [minimo] em centavos; [taxaBp] em centésimos de ponto percentual ao mês. */
    data class DividaSim(val id: Int, val nome: String, val saldo: Long, val taxaBp: Long, val minimo: Long)

    data class MesSim(val mes: Int, val juros: Long, val pago: Long, val saldoFinal: Long)
    data class QuitacaoSim(val id: Int, val nome: String, val mes: Int)

    data class ResultadoSim(
        val estrategia: Estrategia,
        /** meses até quitar tudo; `null` = nunca quita (limite de 600 meses) */
        val meses: Int?,
        val jurosTotal: Long,
        val pagoTotal: Long,
        val saldoRestante: Long,
        val nuncaQuita: Boolean,
        val cronograma: List<MesSim>,
        val quitacoes: List<QuitacaoSim>
    )

    /** Juros do mês em centavos: meio para cima, só com inteiros. */
    fun jurosDoMes(saldo: Long, taxaBp: Long): Long {
        if (saldo <= 0 || taxaBp <= 0) return 0
        return (saldo * taxaBp * 2 + 10_000) / 20_000
    }

    /** Dívida cadastrada (reais, % a.m.) -> entrada do simulador; inativa ou quitada = `null`. */
    fun paraSim(d: Divida): DividaSim? {
        val saldo = Dinheiro.centavos(d.saldoDevedor)
        if (!d.ativa || saldo <= 0) return null
        val taxa = if (d.taxaJurosMensal.isFinite() && d.taxaJurosMensal > 0) d.taxaJurosMensal else 0.0
        val minimo = d.parcelaMinima?.takeIf { it > 0 && it.isFinite() }?.let { Dinheiro.centavos(it) } ?: 0L
        return DividaSim(d.id, d.nome, saldo, Math.round(Math.min(taxa, TAXA_MAXIMA_PERCENTUAL) * 100), minimo)
    }

    private class Item(val id: Int, val nome: String, var saldo: Long, val taxaBp: Long, val minimo: Long)

    private fun prioridade(e: Estrategia): Comparator<Item> =
        if (e == Estrategia.AVALANCHE) compareByDescending<Item> { it.taxaBp }.thenBy { it.saldo }.thenBy { it.id }
        else compareBy<Item> { it.saldo }.thenByDescending { it.taxaBp }.thenBy { it.id }

    /**
     * Cronograma mês a mês. `MINIMOS` paga só o mínimo de cada dívida (sem repassar o que sobra das quitadas).
     * `BOLA_DE_NEVE` (menor saldo primeiro) e `AVALANCHE` (maior taxa primeiro) usam orçamento fixo = soma dos mínimos
     * iniciais + [extra]: cada dívida recebe o mínimo e o resto vai, em ordem de prioridade, para as demais (o mínimo
     * liberado por uma dívida quitada é repassado). O último pagamento de cada dívida é ajustado ao saldo.
     */
    fun simular(dividas: List<DividaSim>, extra: Long, estrategia: Estrategia): ResultadoSim {
        val itens = dividas.filter { it.saldo > 0 }.map { Item(it.id, it.nome, it.saldo, it.taxaBp, it.minimo) }
        val orcamento = if (estrategia == Estrategia.MINIMOS) 0L else itens.sumOf { it.minimo } + maxOf(0L, extra)
        val cmp = prioridade(estrategia)
        val cronograma = ArrayList<MesSim>()
        val quitacoes = ArrayList<QuitacaoSim>()
        var jurosTotal = 0L
        var pagoTotal = 0L
        var meses: Int? = if (itens.isEmpty()) 0 else null

        var mes = 1
        while (mes <= MAX_MESES && itens.isNotEmpty()) {
            val antes = itens.filter { it.saldo > 0 }.map { it.id }.toSet()
            var jurosMes = 0L
            for (d in itens) {
                if (d.saldo <= 0) continue
                val j = jurosDoMes(d.saldo, d.taxaBp)
                d.saldo += j
                jurosMes += j
            }
            var pagoMes = 0L
            if (estrategia == Estrategia.MINIMOS) {
                for (d in itens) {
                    if (d.saldo <= 0) continue
                    val p = minOf(d.minimo, d.saldo)
                    d.saldo -= p
                    pagoMes += p
                }
            } else {
                var disp = orcamento
                for (d in itens) {
                    if (d.saldo <= 0) continue
                    val p = minOf(d.minimo, d.saldo, disp)
                    d.saldo -= p
                    disp -= p
                    pagoMes += p
                }
                for (d in itens.filter { it.saldo > 0 }.sortedWith(cmp)) {
                    if (disp <= 0) break
                    val p = minOf(d.saldo, disp)
                    d.saldo -= p
                    disp -= p
                    pagoMes += p
                }
            }
            for (d in itens) if (d.id in antes && d.saldo <= 0) quitacoes += QuitacaoSim(d.id, d.nome, mes)
            val saldoFinal = itens.sumOf { it.saldo }
            jurosTotal += jurosMes
            pagoTotal += pagoMes
            cronograma += MesSim(mes, jurosMes, pagoMes, saldoFinal)
            if (saldoFinal <= 0) {
                meses = mes
                break
            }
            if (saldoFinal > LIMITE_SALDO_CENTAVOS) break
            mes++
        }

        val saldoRestante = itens.sumOf { it.saldo }
        val nunca = meses == null
        return ResultadoSim(estrategia, meses, jurosTotal, pagoTotal, if (nunca) saldoRestante else 0L, nunca, cronograma, quitacoes)
    }

    data class AnoMes(val ano: Int, val mes: Int)

    /** Mês/ano do último pagamento: o 1º pagamento acontece no mês de referência (`mes` 1-12). */
    fun dataQuitacao(ano: Int, mes: Int, meses: Int?): AnoMes? {
        if (meses == null || meses <= 0) return null
        val total = ano * 12 + (mes - 1) + (meses - 1)
        return AnoMes(total / 12, total % 12 + 1)
    }

    data class Economia(val bolaVsMinimos: Long?, val avalancheVsMinimos: Long?, val avalancheVsBola: Long?)

    data class Comparativo(
        val minimos: ResultadoSim,
        val bolaDeNeve: ResultadoSim,
        val avalanche: ResultadoSim,
        /** juros economizados (centavos) entre estratégias; `null` quando alguma delas nunca quita */
        val economia: Economia,
        val avisos: List<String>
    )

    fun comparar(dividas: List<DividaSim>, extra: Long): Comparativo {
        val minimos = simular(dividas, extra, Estrategia.MINIMOS)
        val bola = simular(dividas, extra, Estrategia.BOLA_DE_NEVE)
        val avalanche = simular(dividas, extra, Estrategia.AVALANCHE)
        fun dif(a: ResultadoSim, b: ResultadoSim): Long? = if (a.nuncaQuita || b.nuncaQuita) null else a.jurosTotal - b.jurosTotal
        val avisos = ArrayList<String>()
        for (d in dividas) {
            if (d.saldo <= 0) continue
            if (d.minimo <= 0) {
                avisos += "${d.nome}: sem pagamento mínimo informado."
            } else {
                val j = jurosDoMes(d.saldo, d.taxaBp)
                if (d.minimo <= j) avisos += "${d.nome}: o pagamento mínimo (${FormatoCentavos.brl(d.minimo)}) não supera os juros do mês (${FormatoCentavos.brl(j)})."
            }
        }
        if (minimos.nuncaQuita && dividas.any { it.saldo > 0 }) avisos += "Pagando só os mínimos, as dívidas nunca são quitadas (limite de $MAX_MESES meses)."
        if (bola.nuncaQuita || avalanche.nuncaQuita) avisos += "Mesmo com o valor extra, o pagamento não cobre os juros: não quita em $MAX_MESES meses."
        return Comparativo(minimos, bola, avalanche, Economia(dif(minimos, bola), dif(minimos, avalanche), dif(bola, avalanche)), avisos)
    }

    /** Comparativo direto das dívidas cadastradas (ativas e com saldo). */
    fun compararDasDividas(dividas: List<Divida>, extraReais: Double): Comparativo =
        comparar(dividas.mapNotNull(::paraSim), Dinheiro.centavos(maxOf(0.0, extraReais)))

    /** Resultado de aplicar um pagamento: [saldo] (centavos) ou [erro]. */
    data class Pagamento(val saldo: Long, val erro: String? = null)

    /** Saldo = saldo + juros − pago; recusa pagamento maior que o devido. */
    fun aplicarPagamento(saldo: Long, juros: Long, pago: Long): Pagamento {
        if (pago <= 0) return Pagamento(saldo, "O valor do pagamento deve ser maior que zero.")
        if (juros < 0) return Pagamento(saldo, "Os juros não podem ser negativos.")
        val devido = saldo + juros
        if (pago > devido) return Pagamento(saldo, "O pagamento passa do saldo devedor (${FormatoCentavos.brl(devido)}).")
        return Pagamento(devido - pago)
    }

    /** Progresso de quitação 0..1: pago ÷ max(original, saldo). */
    fun progresso(d: Divida): Double {
        val saldo = Dinheiro.centavos(d.saldoDevedor)
        val base = maxOf(d.valorOriginal?.let { Dinheiro.centavos(it) } ?: saldo, saldo)
        if (base <= 0) return 1.0
        return ((base - saldo).toDouble() / base).coerceIn(0.0, 1.0)
    }

    /** Mensagem de erro do cadastro; `null` = válido. */
    fun validar(
        nome: String, tipo: String, saldoDevedor: Double, taxaJurosMensal: Double?, parcelaMinima: Double?,
        parcelasRestantes: Int?, diaVencimento: Int?
    ): String? = when {
        nome.isBlank() -> "Informe o nome da dívida."
        tipo !in TIPOS -> "Tipo de dívida inválido."
        !saldoDevedor.isFinite() || saldoDevedor < 0 || saldoDevedor > SALDO_MAXIMO_REAIS -> "Saldo devedor inválido."
        taxaJurosMensal != null && (!taxaJurosMensal.isFinite() || taxaJurosMensal < 0 || taxaJurosMensal > TAXA_MAXIMA_PERCENTUAL) ->
            "Taxa de juros inválida (0 a 100% ao mês)."
        parcelaMinima != null && (!parcelaMinima.isFinite() || parcelaMinima < 0) -> "A parcela não pode ser negativa."
        parcelasRestantes != null && parcelasRestantes < 0 -> "Parcelas restantes inválidas."
        diaVencimento != null && diaVencimento !in 1..31 -> "O dia de vencimento deve estar entre 1 e 31."
        else -> null
    }
}
