package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import java.util.Calendar

/**
 * R59 — rateios e dívidas entre pessoas (paridade com o portal `lib/finance/rateios.ts`: mesmas regras e números nos testes).
 *
 * "Dividir esta despesa" NUNCA altera o lançamento original (continua com o valor TOTAL que saiu da conta) nem saldo: só
 * cria `acertos` A_RECEBER vinculados a ele. "Quanto você realmente gastou" (total − a receber de terceiros) é um indicador
 * à parte, só na tela de pessoas/rateio. Lógica em centavos inteiros; a sobra do arredondamento fica sempre com o pagador (você).
 */
object Rateios {

    const val CATEGORIA_REEMBOLSO = "Reembolso"
    const val CATEGORIA_ACERTOS = "Acertos"
    const val CATEGORIA_EMPRESTIMOS = "Empréstimos"
    const val A_RECEBER = "A_RECEBER"
    const val A_PAGAR = "A_PAGAR"

    fun grupoRateio(despesaId: Long) = "rateio:$despesaId"

    enum class Modo { IGUAL, VALOR, PERCENTUAL }

    /** [valor] em centavos (modo VALOR); [percentual] em décimos de ponto percentual (250 = 25,0%; modo PERCENTUAL). */
    data class Participante(val pessoaId: Int, val valor: Long? = null, val percentual: Long? = null)

    data class Cota(val pessoaId: Int, val centavos: Long)

    sealed class Resultado {
        data class Ok(val cotas: List<Cota>, val voce: Long) : Resultado()
        data class Erro(val mensagem: String) : Resultado()
    }

    /**
     * Divide [total] (centavos) entre as pessoas e você. IGUAL: total ÷ (pessoas + você), sobra em você. VALOR: cada pessoa tem
     * a sua cota; você fica com o resto (nunca negativo). PERCENTUAL: pessoas + [vocePercentual] (décimos) precisam somar
     * exatamente 100%; cota = piso(total × %), a sobra fica com você. Soma exata: Σ cotas + você = total, sempre.
     */
    fun ratear(total: Long, modo: Modo, participantes: List<Participante>, vocePercentual: Long? = null): Resultado {
        if (total <= 0) return Resultado.Erro("O valor a dividir deve ser maior que zero.")
        if (participantes.isEmpty()) return Resultado.Erro("Escolha ao menos uma pessoa.")
        if (participantes.map { it.pessoaId }.toSet().size != participantes.size) return Resultado.Erro("A mesma pessoa foi escolhida duas vezes.")

        val cotas: List<Long> = when (modo) {
            Modo.IGUAL -> {
                val base = total / (participantes.size + 1)
                participantes.map { base }
            }
            Modo.VALOR -> {
                if (participantes.any { (it.valor ?: 0L) <= 0L }) return Resultado.Erro("Informe um valor maior que zero para cada pessoa.")
                val c = participantes.map { it.valor!! }
                if (c.sum() > total) return Resultado.Erro("A soma das cotas passa do valor da despesa.")
                c
            }
            Modo.PERCENTUAL -> {
                if (participantes.any { (it.percentual ?: 0L) <= 0L }) return Resultado.Erro("Informe um percentual maior que zero para cada pessoa.")
                val soma = participantes.sumOf { it.percentual!! } + (vocePercentual ?: 0L)
                if (vocePercentual == null || vocePercentual < 0 || soma != 1000L) {
                    return Resultado.Erro("Os percentuais precisam somar 100% (somam ${Math.floorDiv(soma, 10L)},${Math.abs(soma) % 10}%).")
                }
                participantes.map { total * it.percentual!! / 1000 }
            }
        }
        if (cotas.any { it <= 0 }) return Resultado.Erro("Valor pequeno demais para dividir: alguma cota ficou zerada.")
        val voce = total - cotas.sum()
        return Resultado.Ok(participantes.mapIndexed { i, p -> Cota(p.pessoaId, cotas[i]) }, voce)
    }

    data class SaldoPessoa(
        val pessoa: Pessoa,
        /** centavos pendentes */
        val aReceber: Long,
        val aPagar: Long,
        /** aReceber − aPagar: positivo = a pessoa te deve */
        val liquido: Long,
        val pendentes: List<Acerto>,
        val quitados: List<Acerto>
    )

    fun saldosPorPessoa(pessoas: List<Pessoa>, acertos: List<Acerto>): List<SaldoPessoa> = pessoas.map { pessoa ->
        val meus = acertos.filter { it.pessoaId == pessoa.id }
        val pendentes = meus.filter { !it.quitado }
        val aReceber = pendentes.filter { it.tipo == A_RECEBER }.sumOf { Dinheiro.centavos(it.valor) }
        val aPagar = pendentes.filter { it.tipo == A_PAGAR }.sumOf { Dinheiro.centavos(it.valor) }
        SaldoPessoa(pessoa, aReceber, aPagar, aReceber - aPagar, pendentes, meus.filter { it.quitado })
    }

    data class ItemRateio(
        val despesaId: Long,
        val descricao: String,
        val data: Long,
        /** total do lançamento, centavos (0 quando o lançamento foi removido) */
        val total: Long,
        /** a receber de terceiros (quitado ou não) */
        val aReceber: Long,
        /** quanto você realmente gastou = total − aReceber */
        val liquido: Long,
        /** ainda não recebido */
        val pendente: Long,
        /** lançamento de origem removido: o acerto continua valendo */
        val orfao: Boolean
    )

    data class ResumoRateios(val itens: List<ItemRateio>, val totalRateado: Long, val aReceberTerceiros: Long, val liquido: Long)

    /** Itens rateados (acertos com `grupoRateio`) por lançamento, no período (data do lançamento) se informado. Órfãos listados, mas fora dos totais. */
    fun resumo(despesas: List<Despesa>, acertos: List<Acerto>, periodo: LongRange? = null): ResumoRateios {
        val porId = despesas.associateBy { it.id }
        val grupos = acertos
            .filter { it.tipo == A_RECEBER && !it.grupoRateio.isNullOrEmpty() && it.despesaId != null }
            .groupBy { it.despesaId!! }
        val itens = ArrayList<ItemRateio>()
        for ((despesaId, lista) in grupos) {
            val d = porId[despesaId]
            val data = d?.data?.time ?: lista.minOf { it.data }
            if (periodo != null && data !in periodo) continue
            val aReceber = lista.sumOf { Dinheiro.centavos(it.valor) }
            val pendente = lista.filter { !it.quitado }.sumOf { Dinheiro.centavos(it.valor) }
            val total = d?.let { Dinheiro.centavos(it.valor) } ?: 0L
            itens += ItemRateio(
                despesaId, d?.descricao ?: lista.first().descricao, data, total, aReceber,
                if (d != null) total - aReceber else 0L, pendente, d == null
            )
        }
        itens.sortWith(compareByDescending<ItemRateio> { it.data }.thenByDescending { it.despesaId })
        val validos = itens.filter { !it.orfao }
        val totalRateado = validos.sumOf { it.total }
        val terceiros = validos.sumOf { it.aReceber }
        return ResumoRateios(itens, totalRateado, terceiros, totalRateado - terceiros)
    }

    /** Valor líquido de UM lançamento: total − a receber de terceiros vinculados por rateio (centavos). */
    fun gastoLiquido(despesa: Despesa, acertos: List<Acerto>): Long {
        val terceiros = acertos
            .filter { it.tipo == A_RECEBER && it.despesaId == despesa.id && !it.grupoRateio.isNullOrEmpty() }
            .sumOf { Dinheiro.centavos(it.valor) }
        return Dinheiro.centavos(despesa.valor) - terceiros
    }

    private fun dm(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d/%02d".format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1)
    }

    /**
     * Texto pronto para WhatsApp/copiar com os acertos A_RECEBER pendentes da pessoa. A chave PIX só entra se digitada
     * (nunca inventada). `null` quando não há nada a cobrar.
     */
    fun textoCobranca(nome: String, acertos: List<Acerto>, pix: String? = null): String? {
        val itens = acertos.filter { it.tipo == A_RECEBER && !it.quitado }.sortedWith(compareBy<Acerto> { it.data }.thenBy { it.id })
        if (itens.isEmpty()) return null
        val total = itens.sumOf { Dinheiro.centavos(it.valor) }
        val linhas = ArrayList<String>()
        linhas += "Olá, ${nome.trim()}! Segue o que ficou pendente entre nós:"
        for (a in itens) linhas += "- ${dm(a.data)} ${a.descricao.trim()}: ${FormatoCentavos.brl(Dinheiro.centavos(a.valor))}"
        linhas += ""
        linhas += "Total: ${FormatoCentavos.brl(total)}"
        pix?.trim()?.takeIf { it.isNotEmpty() }?.let { linhas += "Chave PIX: $it" }
        return linhas.joinToString("\n")
    }
}
