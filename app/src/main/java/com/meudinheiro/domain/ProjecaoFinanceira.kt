package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar

/**
 * R51 (limite diário seguro) e R52 (calendário financeiro) — projeção de caixa em centavos.
 * Espelha `portal/src/lib/finance/projecao.ts`: mesmos números nos mesmos cenários. Só cálculo
 * sobre dados existentes; nada é gravado (sem campos novos nem migração).
 */
object ProjecaoFinanceira {

    // ------------------------------------------------------------ datas (dia serial)

    /** Dias desde 1970-01-01 para a data civil (mes 1–12), sem fuso (algoritmo de Hinnant). */
    fun diaSerial(ano: Int, mes: Int, dia: Int): Int {
        val y = if (mes <= 2) ano - 1 else ano
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (mes + (if (mes > 2) -3 else 9)) + 2) / 5 + dia - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    data class DataCivil(val ano: Int, val mes: Int, val dia: Int)

    fun dataDeSerial(serial: Int): DataCivil {
        val z = serial + 719468
        val era = Math.floorDiv(z, 146097)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = doy - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        val ano = yoe + era * 400 + (if (mes <= 2) 1 else 0)
        return DataCivil(ano, mes, dia)
    }

    /** Dia serial da data LOCAL de um instante (epoch ms). */
    fun serialDeMs(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return diaSerial(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Último dia serial do mês (mes 1–12). */
    fun ultimoDiaSerial(mes: Int, ano: Int): Int =
        if (mes == 12) diaSerial(ano + 1, 1, 1) - 1 else diaSerial(ano, mes + 1, 1) - 1

    /** 0 = domingo … 6 = sábado. */
    fun diaDaSemana(serial: Int): Int = Math.floorMod(serial + 4, 7)

    private fun pad2(n: Int): String = n.toString().padStart(2, '0')

    fun ddmm(serial: Int): String {
        val d = dataDeSerial(serial)
        return "${pad2(d.dia)}/${pad2(d.mes)}"
    }

    /** "R$ 1.234,56" — escrito à mão (independe de locale). Negativo vira "-R$ …". */
    fun brlCentavos(centavos: Long): String {
        val neg = centavos < 0
        val abs = Math.abs(centavos)
        val reais = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (neg) "-" else "") + "R$ " + reais + "," + pad2((abs % 100).toInt())
    }

    // ------------------------------------------------------------ eventos previstos

    enum class TipoEvento(val ordem: Int) {
        RECEITA(0), RECORRENCIA_RECEITA(1), DESPESA(2), RECORRENCIA_DESPESA(3), FATURA(4)
    }

    val TipoEvento.ehReceita: Boolean get() = this == TipoEvento.RECEITA || this == TipoEvento.RECORRENCIA_RECEITA

    data class EventoProjecao(
        /** Dia serial em que o dinheiro se move (pendência vencida é antecipada para hoje). */
        val dia: Int,
        /** Com sinal: recebimento positivo, pagamento negativo. */
        val centavos: Long,
        val tipo: TipoEvento,
        val descricao: String,
        /** Banco da conta ou nome do cartão. */
        val origem: String,
        /** Vencimento anterior a hoje e ainda não pago. */
        val atrasado: Boolean = false,
        /** Id do lançamento real (pendências); nulo em fatura e recorrência ainda não lançada. */
        val lancamentoId: Long? = null,
        /** Cartão principal da fatura; nulo nos demais. */
        val cartaoId: Int? = null
    )

    data class GastoHoje(
        /** Compra positiva, estorno negativo. */
        val centavos: Long,
        /** Nulo = saiu da conta; senão, dia serial do vencimento da fatura em que a compra caiu. */
        val vencimentoFatura: Int? = null
    )

    /** Dados mínimos de uma conta (desacopla de `ContaSaldo`/`ContaSaldoDomain`). */
    data class ContaRef(val id: Int, val conta: String, val banco: String)

    fun ordenarEventos(eventos: List<EventoProjecao>): List<EventoProjecao> =
        eventos.sortedWith(
            compareBy<EventoProjecao>({ it.dia }, { it.tipo.ordem }, { it.descricao }, { it.centavos })
        )

    private fun principalDe(c: Cartao, cartoes: List<Cartao>): Cartao =
        if (c.cartaoPrincipalId == null) c else cartoes.firstOrNull { it.id == c.cartaoPrincipalId } ?: c

    private fun Despesa.cartaoReal(): Int? = cartaoId?.takeIf { it != 0 }

    private fun ocorrenciaPausada(regra: DespesaFixa, dataMs: Long, agora: Long): Boolean {
        if (!regra.pausada) return false
        val ate = regra.pausadaAte?.time
        return ate == null || dataMs < ate || dataMs <= agora
    }

    private fun meiaNoite(ano: Int, mes: Int, dia: Int): Long =
        Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, 0, 0, 0) }.timeInMillis

    private fun vencimentoSerial(cartao: Cartao, ref: Financas.FaturaRef): Int =
        serialDeMs(Financas.dataVencimento(cartao.diaFechamento, cartao.diaVencimento, ref))

    /**
     * Converte os dados em eventos previstos (a partir de hoje): pendências NORMAL em conta, faturas de
     * cartão com valor a pagar (no vencimento) e ocorrências de recorrência ainda não lançadas (exceto
     * pausadas). Pendência/fatura vencida entra em hoje. Compra recorrente no crédito soma na fatura do ciclo.
     * [ate]: último dia serial para o qual gerar ocorrências de recorrência. [filtroContas]: nulo = todas.
     */
    fun eventosPrevistos(
        contas: List<ContaRef>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        fixas: List<DespesaFixa>,
        agora: Long,
        ate: Int,
        filtroContas: Set<String>? = null
    ): List<EventoProjecao> {
        val hoje = serialDeMs(agora)
        fun contaOk(numero: String?) = filtroContas == null || (numero != null && numero in filtroContas)
        fun banco(numero: String) = contas.firstOrNull { it.conta == numero }?.banco ?: numero
        fun numeroDaConta(contaId: Int) = contas.firstOrNull { it.id == contaId }?.conta
        val eventos = mutableListOf<EventoProjecao>()

        // 1) Pendências em conta (R15: NORMAL, sem cartão, não pagas).
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.pago || d.cartaoReal() != null || !contaOk(d.conta)) continue
            val dia = serialDeMs(d.dataMs)
            val credito = d.tipo == TipoDespesa.CREDITO
            eventos += EventoProjecao(
                dia = maxOf(dia, hoje), centavos = d.centavosAssinados,
                tipo = if (credito) TipoEvento.RECEITA else TipoEvento.DESPESA,
                descricao = d.descricao, origem = banco(d.conta), atrasado = dia < hoje, lancamentoId = d.id
            )
        }

        // 2) Faturas (uma por grupo e ciclo) + compras recorrentes no crédito.
        class Fatura(val cartao: Cartao, val ref: Financas.FaturaRef, var cents: Long)
        val faturas = linkedMapOf<String, Fatura>()
        fun somaFatura(cartao: Cartao, dataMs: Long, cents: Long) {
            val ref = Financas.faturaDaCompra(dataMs, cartao.diaFechamento)
            val chave = "${cartao.id}:${ref.ano}-${ref.mes}"
            faturas.getOrPut(chave) { Fatura(cartao, ref, 0L) }.cents += cents
        }
        for (cartao in cartoes.filter { principalDe(it, cartoes) === it }) {
            val ids = setOf(cartao.id) + cartoes.filter { it.id != cartao.id && it.cartaoPrincipalId == cartao.id }.map { it.id }
            for (d in despesas) {
                val cid = d.cartaoReal() ?: continue
                if (cid !in ids || d.pago) continue
                somaFatura(cartao, d.dataMs, -d.centavosAssinados)
            }
        }

        // 3) Recorrências ainda não lançadas.
        val cal = Calendar.getInstance().apply { timeInMillis = agora }
        val idxHoje = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
        val dAte = dataDeSerial(ate)
        val idxAte = dAte.ano * 12 + dAte.mes - 1
        val lancados = despesas.mapNotNull { it.grupoId }.toSet()
        for (regra in fixas) {
            val idxUlt = regra.ultimaDataLancamento?.let {
                val c = Calendar.getInstance().apply { time = it }
                c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
            }
            val cartaoRegra = regra.cartaoId?.takeIf { it != 0 }?.let { id -> cartoes.firstOrNull { it.id == id } }
            val contaCartao = cartaoRegra?.let { numeroDaConta(it.contaId) }
            val noCredito = cartaoRegra != null && contaCartao != null && Financas.tipoDeCartao(cartaoRegra.tipo) != "DEBITO"
            val numeroConta = if (cartaoRegra != null && contaCartao != null) contaCartao else regra.conta
            if (!contaOk(numeroConta)) continue
            for (idx in idxHoje..idxAte) {
                if (idxUlt != null && idx <= idxUlt) continue
                val ano = idx / 12
                val mes = idx % 12 + 1
                if ("fixa:${regra.id}:$ano-${pad2(mes)}" in lancados) continue
                val dia = regra.diaVencimento.coerceIn(1, 31).coerceAtMost(Financas.diasNoMes(mes, ano))
                val dataMs = meiaNoite(ano, mes, dia)
                if (ocorrenciaPausada(regra, dataMs, agora)) continue
                val cents = Dinheiro.centavos(regra.valor)
                val sinal = if (regra.tipo == TipoDespesa.CREDITO) 1L else -1L
                if (noCredito && cartaoRegra != null) {
                    somaFatura(principalDe(cartaoRegra, cartoes), dataMs, -sinal * cents)
                    continue
                }
                eventos += EventoProjecao(
                    dia = maxOf(diaSerial(ano, mes, dia), hoje), centavos = sinal * cents,
                    tipo = if (regra.tipo == TipoDespesa.CREDITO) TipoEvento.RECORRENCIA_RECEITA else TipoEvento.RECORRENCIA_DESPESA,
                    descricao = regra.descricao, origem = banco(numeroConta)
                )
            }
        }

        for (g in faturas.values) {
            if (g.cents <= 0) continue
            if (!contaOk(numeroDaConta(g.cartao.contaId))) continue
            val venc = vencimentoSerial(g.cartao, g.ref)
            eventos += EventoProjecao(
                dia = maxOf(venc, hoje), centavos = -g.cents, tipo = TipoEvento.FATURA,
                descricao = "Fatura ${g.cartao.nome}", origem = g.cartao.nome,
                atrasado = venc < hoje, cartaoId = g.cartao.id
            )
        }
        return ordenarEventos(eventos)
    }

    /** Saldo atual (centavos) das contas consideradas — R3. */
    fun saldoCentavos(contas: List<ContaRef>, despesas: List<Despesa>, filtroContas: Set<String>? = null): Long {
        val validas = contas.map { it.conta }.toSet()
        return despesas
            .filter { it.pago && it.cartaoReal() == null && it.conta in validas && (filtroContas == null || it.conta in filtroContas) }
            .sumOf { it.centavosAssinados }
    }

    /** Gasto de hoje (NORMAL, débito em conta ou compra no cartão, líquido de estornos de cartão). */
    fun gastosDeHoje(
        contas: List<ContaRef>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        agora: Long,
        filtroContas: Set<String>? = null
    ): List<GastoHoje> {
        val hoje = serialDeMs(agora)
        val out = mutableListOf<GastoHoje>()
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || serialDeMs(d.dataMs) != hoje) continue
            val cid = d.cartaoReal()
            if (cid == null) {
                if (d.tipo != TipoDespesa.DEBITO) continue
                if (filtroContas != null && d.conta !in filtroContas) continue
                out += GastoHoje(Dinheiro.centavos(d.valor), null)
                continue
            }
            val cartao = cartoes.firstOrNull { it.id == cid } ?: continue
            val principal = principalDe(cartao, cartoes)
            val numero = contas.firstOrNull { it.id == principal.contaId }?.conta
            if (filtroContas != null && (numero == null || numero !in filtroContas)) continue
            val ref = Financas.faturaDaCompra(d.dataMs, principal.diaFechamento)
            out += GastoHoje(-d.centavosAssinados, maxOf(vencimentoSerial(principal, ref), hoje))
        }
        return out
    }

    // ------------------------------------------------------------ R51

    enum class StatusLimite { VERDE, AMARELO, VERMELHO }

    data class EntradaLimiteDiario(
        val saldoCentavos: Long,
        val eventos: List<EventoProjecao>,
        val hoje: Int,
        /** Último dia serial do mês corrente. */
        val fimDoMes: Int,
        /** Período termina no dia anterior à próxima receita prevista (se houver). */
        val ateProximaReceita: Boolean,
        val gastosHoje: List<GastoHoje>
    )

    data class ResultadoLimiteDiario(
        val fimPeriodo: Int,
        val diasRestantes: Int,
        val receitasPrevistasCentavos: Long,
        val despesasPrevistasCentavos: Long,
        val disponivelCentavos: Long,
        val limiteDiarioCentavos: Long,
        val gastoHojeCentavos: Long,
        val restanteHojeCentavos: Long,
        val status: StatusLimite,
        val mesNoVermelho: Boolean,
        val faltaCentavos: Long,
        val mensagem: String
    )

    /** R51 — quanto posso gastar hoje (tudo em centavos inteiros). */
    fun calcularLimiteDiario(e: EntradaLimiteDiario): ResultadoLimiteDiario {
        var fim = e.fimDoMes
        if (e.ateProximaReceita) {
            val proxima = e.eventos
                .filter { it.tipo.ehReceita && it.centavos > 0 && it.dia > e.hoje }
                .minOfOrNull { it.dia }
            if (proxima != null) fim = proxima - 1
        }
        if (fim < e.hoje) fim = e.hoje

        var receitas = 0L
        var despesas = 0L
        for (ev in e.eventos) {
            if (ev.dia > fim) continue
            if (ev.centavos >= 0) receitas += ev.centavos else despesas += -ev.centavos
        }
        val disponivel = e.saldoCentavos + receitas - despesas

        var gasto = 0L
        var jaDeduzido = 0L
        for (g in e.gastosHoje) {
            gasto += g.centavos
            if (g.vencimentoFatura == null || g.vencimentoFatura <= fim) jaDeduzido += g.centavos
        }
        gasto = maxOf(0L, gasto)
        jaDeduzido = maxOf(0L, jaDeduzido)

        val dias = fim - e.hoje + 1
        val base = disponivel + jaDeduzido
        val limite = maxOf(0L, base) / dias
        val restante = limite - gasto
        val mesNoVermelho = disponivel <= 0
        val falta = maxOf(0L, -disponivel)

        val status = when {
            mesNoVermelho || gasto > limite -> StatusLimite.VERMELHO
            limite > 0 && gasto * 10 >= limite * 8 -> StatusLimite.AMARELO
            else -> StatusLimite.VERDE
        }
        val mensagem = when {
            mesNoVermelho ->
                if (falta > 0) "Mês no vermelho: faltam ${brlCentavos(falta)} para cobrir tudo até ${ddmm(fim)}."
                else "Mês no vermelho: não sobra nada para gastar até ${ddmm(fim)}."
            status == StatusLimite.VERMELHO -> "Hoje você já passou do limite em ${brlCentavos(gasto - limite)}."
            status == StatusLimite.AMARELO -> "Atenção: restam ${brlCentavos(restante)} do limite de hoje."
            else -> "Hoje você pode gastar até ${brlCentavos(restante)}."
        }
        return ResultadoLimiteDiario(
            fimPeriodo = fim, diasRestantes = dias, receitasPrevistasCentavos = receitas, despesasPrevistasCentavos = despesas,
            disponivelCentavos = disponivel, limiteDiarioCentavos = limite, gastoHojeCentavos = gasto,
            restanteHojeCentavos = restante, status = status, mesNoVermelho = mesNoVermelho,
            faltaCentavos = falta, mensagem = mensagem
        )
    }

    /** R51 sobre os dados completos. */
    fun limiteDiarioDosDados(
        contas: List<ContaRef>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        fixas: List<DespesaFixa>,
        agora: Long,
        ateProximaReceita: Boolean = false,
        filtroContas: Set<String>? = null
    ): ResultadoLimiteDiario {
        val cal = Calendar.getInstance().apply { timeInMillis = agora }
        val mes = cal.get(Calendar.MONTH) + 1
        val ano = cal.get(Calendar.YEAR)
        // Horizonte: fim do mês seguinte (a próxima receita pode estar lá).
        val ate = if (mes == 12) ultimoDiaSerial(1, ano + 1) else ultimoDiaSerial(mes + 1, ano)
        return calcularLimiteDiario(
            EntradaLimiteDiario(
                saldoCentavos = saldoCentavos(contas, despesas, filtroContas),
                eventos = eventosPrevistos(contas, cartoes, despesas, fixas, agora, ate, filtroContas),
                hoje = serialDeMs(agora),
                fimDoMes = ultimoDiaSerial(mes, ano),
                ateProximaReceita = ateProximaReceita,
                gastosHoje = gastosDeHoje(contas, cartoes, despesas, agora, filtroContas)
            )
        )
    }

    // ------------------------------------------------------------ R52

    data class DiaCalendario(
        val dia: Int,
        val ano: Int,
        val mes: Int,
        val diaDoMes: Int,
        /** 0 = domingo. */
        val diaDaSemana: Int,
        val eventos: List<EventoProjecao>,
        val entradasCentavos: Long,
        /** Positivo. */
        val saidasCentavos: Long,
        /** Saldo ao fim do dia; nulo em dias que já passaram (sem projeção). */
        val saldoProjetadoCentavos: Long?,
        val negativo: Boolean,
        val ehHoje: Boolean,
        val passado: Boolean
    )

    data class MenorSaldo(val centavos: Long, val dia: Int)

    data class ResultadoCalendario(
        val mes: Int,
        val ano: Int,
        val dias: List<DiaCalendario>,
        val menorSaldo: MenorSaldo?,
        val primeiroDiaNegativo: Int?,
        /** Próximo recebimento previsto depois do primeiro dia negativo (dia serial). */
        val proximoRecebimentoApos: Int?,
        val alerta: String?,
        val saldoFimDoMesCentavos: Long?
    )

    /** R52 — saldo projetado ao fim de cada dia do mês (saldo atual + movimentos previstos acumulados). */
    fun projetarMes(saldoCentavos: Long, eventos: List<EventoProjecao>, hoje: Int, mes: Int, ano: Int): ResultadoCalendario {
        val primeiro = diaSerial(ano, mes, 1)
        val ultimo = ultimoDiaSerial(mes, ano)
        val inicio = maxOf(hoje, primeiro)
        var corrente = saldoCentavos
        for (ev in eventos) if (ev.dia < inicio) corrente += ev.centavos

        val dias = mutableListOf<DiaCalendario>()
        var menor: MenorSaldo? = null
        var primeiroNeg: Int? = null
        for (s in primeiro..ultimo) {
            val evs = eventos.filter { it.dia == s }
            var ent = 0L
            var sai = 0L
            for (ev in evs) if (ev.centavos >= 0) ent += ev.centavos else sai += -ev.centavos
            var saldo: Long? = null
            if (s >= inicio) {
                corrente += ent - sai
                saldo = corrente
                if (menor == null || corrente < menor.centavos) menor = MenorSaldo(corrente, s)
                if (corrente < 0 && primeiroNeg == null) primeiroNeg = s
            }
            val c = dataDeSerial(s)
            dias += DiaCalendario(
                dia = s, ano = c.ano, mes = c.mes, diaDoMes = c.dia, diaDaSemana = diaDaSemana(s), eventos = evs,
                entradasCentavos = ent, saidasCentavos = sai, saldoProjetadoCentavos = saldo,
                negativo = saldo != null && saldo < 0, ehHoje = s == hoje, passado = s < hoje
            )
        }

        var proximoRec: Int? = null
        var alerta: String? = null
        if (primeiroNeg != null) {
            proximoRec = eventos.filter { it.tipo.ehReceita && it.centavos > 0 && it.dia > primeiroNeg }.minOfOrNull { it.dia }
            alerta = if (proximoRec != null) "Conta ficará negativa em ${ddmm(primeiroNeg)} antes do próximo recebimento."
            else "Conta ficará negativa em ${ddmm(primeiroNeg)} e não há recebimento previsto."
        }
        return ResultadoCalendario(
            mes = mes, ano = ano, dias = dias, menorSaldo = menor, primeiroDiaNegativo = primeiroNeg,
            proximoRecebimentoApos = proximoRec, alerta = alerta, saldoFimDoMesCentavos = dias.lastOrNull()?.saldoProjetadoCentavos
        )
    }

    /** R52 sobre os dados completos (mes/ano = mês exibido). */
    fun calendarioDosDados(
        contas: List<ContaRef>,
        cartoes: List<Cartao>,
        despesas: List<Despesa>,
        fixas: List<DespesaFixa>,
        agora: Long,
        mes: Int,
        ano: Int,
        filtroContas: Set<String>? = null
    ): ResultadoCalendario {
        val ate = if (mes == 12) ultimoDiaSerial(1, ano + 1) else ultimoDiaSerial(mes + 1, ano)
        return projetarMes(
            saldoCentavos(contas, despesas, filtroContas),
            eventosPrevistos(contas, cartoes, despesas, fixas, agora, ate, filtroContas),
            serialDeMs(agora), mes, ano
        )
    }
}
