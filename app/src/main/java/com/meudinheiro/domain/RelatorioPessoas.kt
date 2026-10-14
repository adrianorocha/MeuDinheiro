package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Pessoa
import java.util.Calendar

/**
 * R69 — relatório detalhado de Pessoas (paridade com o portal `lib/finance/relatorioPessoas.ts`: mesmas regras, números e textos).
 *
 * Modelo PURO em centavos inteiros: sem Locale (datas e moeda formatadas à mão, em horário local). Não altera nenhum dado; só
 * apresenta `pessoas` + `acertos` (+ `despesas` para descrever o lançamento vinculado). Os renderizadores só desenham este modelo.
 *
 * Tudo é calculado sobre o conjunto FILTRADO (pessoas escolhidas, data do acerto no período, situação): a receber/a pagar = acertos
 * NÃO quitados; quitado = acertos quitados; saldo líquido = a receber − a pagar; Σ subtotais das pessoas = totais do resumo.
 */
object RelatorioPessoas {

    enum class Situacao { TODOS, PENDENTES, QUITADOS }
    enum class Agrupar { POR_PESSOA, CRONOLOGICO }
    enum class Sentido { TE_DEVE, VOCE_DEVE, QUITES }
    enum class Status { PENDENTE, QUITADO }
    enum class OrigemTipo { LANCAMENTO, AVULSO, REMOVIDO }
    enum class Atalho { ESTE_MES, MES_PASSADO, TRES_MESES, ANO, TUDO }

    const val MSG_SEM_ACERTOS = "Nenhum acerto no período"

    /** [periodo] sobre a data do acerto, inclusivo; null = tudo. [agora] = epoch ms da emissão. */
    data class Opcoes(
        val pessoaIds: Set<Int> = emptySet(),
        val periodo: LongRange? = null,
        val situacao: Situacao = Situacao.TODOS,
        val incluirDetalhes: Boolean = true,
        val agrupar: Agrupar = Agrupar.POR_PESSOA,
        val incluirRateios: Boolean = false,
        val agora: Long
    )

    data class RateioLinha(val totalDespesa: Long, val cota: Long, val texto: String)

    data class Linha(
        val acertoId: Int,
        val pessoaId: Int,
        val pessoaNome: String,
        val data: Long,
        val dataTexto: String,
        val descricao: String,
        val origem: String,
        val origemTipo: OrigemTipo,
        val tipo: String,
        val tipoTexto: String,
        val centavos: Long,
        val status: Status,
        val statusTexto: String,
        val rateio: RateioLinha?
    )

    data class PessoaBloco(
        val pessoaId: Int,
        val nome: String,
        val contato: String?,
        val iniciais: String,
        val corIndice: Int,
        val aReceber: Long,
        val aPagar: Long,
        val liquido: Long,
        val sentido: Sentido,
        val saldoTexto: String,
        val quitadoAReceber: Long,
        val quitadoAPagar: Long,
        val nAcertos: Int,
        /** vazio quando `incluirDetalhes` é falso */
        val linhas: List<Linha>
    )

    data class Resumo(
        val aReceberPendente: Long,
        val aPagarPendente: Long,
        val saldoLiquido: Long,
        val quitadoAReceber: Long,
        val quitadoAPagar: Long,
        val nAcertos: Int,
        val nPessoasComSaldo: Int,
        val nPessoas: Int
    )

    data class BlocoRateios(val itens: List<com.meudinheiro.domain.Rateios.ItemRateio>, val totalRateado: Long, val aReceberTerceiros: Long, val liquido: Long)

    data class Relatorio(
        val titulo: String,
        val periodoTexto: String,
        val filtros: List<String>,
        val emitidoEm: String,
        val agrupar: Agrupar,
        val incluirDetalhes: Boolean,
        val resumo: Resumo,
        val pessoas: List<PessoaBloco>,
        /** só em CRONOLOGICO com detalhes */
        val cronologico: List<Linha>,
        val rateios: BlocoRateios?,
        val vazio: Boolean
    )

    private fun dois(n: Int) = n.toString().padStart(2, '0')

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    fun dataBR(ms: Long): String {
        val c = cal(ms)
        return "${dois(c.get(Calendar.DAY_OF_MONTH))}/${dois(c.get(Calendar.MONTH) + 1)}/${c.get(Calendar.YEAR)}"
    }

    private fun diaMes(ms: Long) = dataBR(ms).substring(0, 5)

    fun dataHoraBR(ms: Long): String {
        val c = cal(ms)
        return "${dataBR(ms)} ${dois(c.get(Calendar.HOUR_OF_DAY))}:${dois(c.get(Calendar.MINUTE))}"
    }

    /** Hash estável (UTF-16, 31x) do nome -> índice na paleta; idêntico ao portal. */
    fun corIndiceDaPessoa(nome: String, tamanhoPaleta: Int = 8): Int {
        var h = 7
        for (ch in nome) h = (h * 31 + ch.code) % 1000003
        return h % tamanhoPaleta
    }

    /** Até duas iniciais (primeira e última palavra), maiúsculas. */
    fun iniciaisDoNome(nome: String): String {
        val partes = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (partes.isEmpty()) return "?"
        val a = partes.first().codePointAt(0).let { String(Character.toChars(it)) }
        val b = if (partes.size > 1) partes.last().codePointAt(0).let { String(Character.toChars(it)) } else ""
        return (a + b).uppercase()
    }

    fun saldoTexto(liquido: Long): Pair<Sentido, String> = when {
        liquido > 0 -> Sentido.TE_DEVE to "te deve ${FormatoCentavos.brl(liquido)}"
        liquido < 0 -> Sentido.VOCE_DEVE to "você deve ${FormatoCentavos.brl(-liquido)}"
        else -> Sentido.QUITES to "quites"
    }

    /** Atalhos do diálogo: dias inteiros em horário local; null = tudo. "3 meses" = mês atual e os 2 anteriores. */
    fun periodoPorAtalho(atalho: Atalho, agora: Long): LongRange? {
        val c = cal(agora)
        val a = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH)
        fun inicio(ano: Int, mes: Int) = Calendar.getInstance().apply { clear(); set(ano, mes, 1, 0, 0, 0) }.timeInMillis
        fun fim(ano: Int, mes: Int) = Calendar.getInstance().apply { clear(); set(ano, mes + 1, 1, 0, 0, 0); add(Calendar.MILLISECOND, -1) }.timeInMillis
        return when (atalho) {
            Atalho.ESTE_MES -> inicio(a, m)..fim(a, m)
            Atalho.MES_PASSADO -> inicio(a, m - 1)..fim(a, m - 1)
            Atalho.TRES_MESES -> inicio(a, m - 2)..fim(a, m)
            Atalho.ANO -> inicio(a, 0)..fim(a, 11)
            Atalho.TUDO -> null
        }
    }

    private fun rotuloSituacao(s: Situacao) = when (s) {
        Situacao.TODOS -> "Todos"
        Situacao.PENDENTES -> "Somente pendentes"
        Situacao.QUITADOS -> "Somente quitados"
    }

    fun montar(pessoas: List<Pessoa>, acertos: List<Acerto>, despesas: List<Despesa>, op: Opcoes): Relatorio {
        val porDespesa = despesas.associateBy { it.id }
        val alvo = pessoas.filter { op.pessoaIds.isEmpty() || it.id in op.pessoaIds }
        fun noPeriodo(a: Acerto) = op.periodo == null || a.data in op.periodo
        fun naSituacao(a: Acerto) = op.situacao == Situacao.TODOS || (if (op.situacao == Situacao.PENDENTES) !a.quitado else a.quitado)

        fun linhaDe(a: Acerto, p: Pessoa): Linha {
            val cents = Dinheiro.centavos(a.valor)
            val d = a.despesaId?.let { porDespesa[it] }
            val origemTipo = if (a.despesaId == null) OrigemTipo.AVULSO else if (d != null) OrigemTipo.LANCAMENTO else OrigemTipo.REMOVIDO
            var rateio: RateioLinha? = null
            if (!a.grupoRateio.isNullOrEmpty() && d != null) {
                val total = Dinheiro.centavos(d.valor)
                val rotulo = if (a.grupoRateio.startsWith("vinculo:")) "Vinculado" else "Rateio"
                rateio = RateioLinha(total, cents, "$rotulo: despesa ${FormatoCentavos.brl(total)} · cota ${FormatoCentavos.brl(cents)}")
            }
            return Linha(
                acertoId = a.id, pessoaId = p.id, pessoaNome = p.nome, data = a.data, dataTexto = dataBR(a.data), descricao = a.descricao,
                origem = when (origemTipo) {
                    OrigemTipo.AVULSO -> "avulso"
                    OrigemTipo.REMOVIDO -> "lançamento removido"
                    OrigemTipo.LANCAMENTO -> "lançamento de ${diaMes(d!!.data.time)} — ${d.descricao}"
                },
                origemTipo = origemTipo, tipo = a.tipo, tipoTexto = if (a.tipo == Rateios.A_RECEBER) "A receber" else "A pagar",
                centavos = cents, status = if (a.quitado) Status.QUITADO else Status.PENDENTE,
                statusTexto = if (a.quitado) (a.quitadoEm?.let { "Quitado em ${diaMes(it)}" } ?: "Quitado") else "Pendente",
                rateio = rateio
            )
        }

        val porData = compareBy<Linha>({ it.data }, { it.acertoId })
        val ordenadas = alvo.sortedWith(compareBy<Pessoa>({ it.nome.lowercase() }, { it.id }))
        val blocos = ArrayList<PessoaBloco>()
        val todas = ArrayList<Linha>()
        for (p in ordenadas) {
            val meus = acertos.filter { it.pessoaId == p.id && noPeriodo(it) && naSituacao(it) }
            if (meus.isEmpty()) continue
            fun soma(tipo: String, quitado: Boolean) = meus.filter { it.tipo == tipo && it.quitado == quitado }.sumOf { Dinheiro.centavos(it.valor) }
            val aReceber = soma(Rateios.A_RECEBER, false)
            val aPagar = soma(Rateios.A_PAGAR, false)
            val liquido = aReceber - aPagar
            val (sentido, texto) = saldoTexto(liquido)
            val linhas = meus.map { linhaDe(it, p) }.sortedWith(porData)
            todas += linhas
            blocos += PessoaBloco(
                pessoaId = p.id, nome = p.nome, contato = p.contato?.trim()?.takeIf { it.isNotEmpty() },
                iniciais = iniciaisDoNome(p.nome), corIndice = corIndiceDaPessoa(p.nome),
                aReceber = aReceber, aPagar = aPagar, liquido = liquido, sentido = sentido, saldoTexto = texto,
                quitadoAReceber = soma(Rateios.A_RECEBER, true), quitadoAPagar = soma(Rateios.A_PAGAR, true),
                nAcertos = meus.size, linhas = if (op.incluirDetalhes) linhas else emptyList()
            )
        }

        val aRec = blocos.sumOf { it.aReceber }
        val aPag = blocos.sumOf { it.aPagar }
        val resumo = Resumo(
            aReceberPendente = aRec, aPagarPendente = aPag, saldoLiquido = aRec - aPag,
            quitadoAReceber = blocos.sumOf { it.quitadoAReceber }, quitadoAPagar = blocos.sumOf { it.quitadoAPagar },
            nAcertos = blocos.sumOf { it.nAcertos }, nPessoasComSaldo = blocos.count { it.liquido != 0L }, nPessoas = blocos.size
        )

        var rateios: BlocoRateios? = null
        if (op.incluirRateios && blocos.isNotEmpty()) {
            // Total e líquido precisam de TODOS os A_RECEBER do lançamento; o filtro de pessoas só decide quais lançamentos aparecem.
            val r = com.meudinheiro.domain.Rateios.resumo(despesas, acertos, op.periodo)
            val idsBloco = blocos.map { it.pessoaId }.toSet()
            val comPessoa = acertos.filter { it.pessoaId in idsBloco && it.despesaId != null }.map { it.despesaId!! }.toSet()
            val itens = if (op.pessoaIds.isEmpty()) r.itens else r.itens.filter { it.despesaId in comPessoa }
            val validos = itens.filter { !it.orfao }
            val totalRateado = validos.sumOf { it.total }
            val terceiros = validos.sumOf { it.aReceber }
            if (itens.isNotEmpty()) rateios = BlocoRateios(itens, totalRateado, terceiros, totalRateado - terceiros)
        }

        val filtros = listOf(
            "Pessoas: " + if (op.pessoaIds.isEmpty()) "todas" else alvo.joinToString(", ") { it.nome }.ifEmpty { "nenhuma" },
            "Situação: ${rotuloSituacao(op.situacao)}",
            "Agrupamento: ${if (op.agrupar == Agrupar.POR_PESSOA) "Por pessoa" else "Cronológico"}",
            "Detalhes: ${if (op.incluirDetalhes) "incluídos" else "apenas totais"}"
        )

        return Relatorio(
            titulo = "Relatório de pessoas",
            periodoTexto = op.periodo?.let { "${dataBR(it.first)} a ${dataBR(it.last)}" } ?: "Todo o período",
            filtros = filtros, emitidoEm = dataHoraBR(op.agora), agrupar = op.agrupar, incluirDetalhes = op.incluirDetalhes,
            resumo = resumo, pessoas = blocos,
            cronologico = if (op.agrupar == Agrupar.CRONOLOGICO && op.incluirDetalhes) todas.sortedWith(porData) else emptyList(),
            rateios = rateios, vazio = blocos.isEmpty()
        )
    }

    /** `pessoas_AAAA-MM-DD` (+ `-N` em partes de imagem) + extensão. */
    fun nomeArquivo(agora: Long, ext: String, parte: Int? = null): String {
        val c = cal(agora)
        return "pessoas_${c.get(Calendar.YEAR)}-${dois(c.get(Calendar.MONTH) + 1)}-${dois(c.get(Calendar.DAY_OF_MONTH))}" +
            (if (parte != null && parte > 0) "-$parte" else "") + ".$ext"
    }
}
