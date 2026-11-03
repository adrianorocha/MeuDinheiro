package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar

/**
 * R48 — detalhamento dos relatórios: os lançamentos que compõem cada total, agrupados, com subtotais em centavos
 * que fecham EXATAMENTE com o total mostrado. Não altera seleção nem cálculo (usa a mesma contribuição do R19).
 */
private val PARCELA = Regex("""\s*\((\d+)/(\d+)\)\s*$""")

/** Extrai "(i/n)" do fim da descrição; devolve (descrição sem o marcador, i, n) ou `null`. */
fun parcelaDaDescricao(descricao: String): Triple<String, Int, Int>? {
    val m = PARCELA.find(descricao) ?: return null
    val i = m.groupValues[1].toIntOrNull() ?: return null
    val n = m.groupValues[2].toIntOrNull() ?: return null
    if (n < 2 || i < 1 || i > n) return null
    return Triple(descricao.removeRange(m.range).trim(), i, n)
}

enum class AgruparPor { CATEGORIA, MES, CONTA }

data class LinhaDetalhe(
    val id: Long,
    val data: Long,
    val descricao: String,
    /** "i/n" ou vazio. */
    val parcela: String,
    val origem: String,
    val situacao: String,
    /** Contribuição do lançamento ao total (com sinal, igual ao R19). */
    val centavos: Long
)

data class GrupoDetalhe(val titulo: String, val subtotalCentavos: Long, val linhas: List<LinhaDetalhe>)

data class SecaoDetalhe(val titulo: String?, val totalCentavos: Long, val grupos: List<GrupoDetalhe>) {
    val quantidade: Int get() = grupos.sumOf { it.linhas.size }
}

data class Detalhamento(val secoes: List<SecaoDetalhe>) {
    val totalCentavos: Long get() = secoes.sumOf { it.totalCentavos }
    val quantidade: Int get() = secoes.sumOf { it.quantidade }
}

object RelatorioDetalhe {

    private fun chaveMes(ms: Long): String =
        Calendar.getInstance().apply { timeInMillis = ms }.let { "%04d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

    /**
     * [origem] diz o texto de "conta/cartão" de cada lançamento. Receitas e despesas ficam em seções separadas quando
     * o tipo é TODOS; nos demais tipos há uma só seção. Soma dos grupos = total da seção; soma das seções = `resultado.total`.
     */
    fun detalhar(
        resultado: ResultadoRelatorio,
        agruparPor: AgruparPor = AgruparPor.CATEGORIA,
        origem: (Despesa) -> String = { it.conta }
    ): Detalhamento {
        val tipo = resultado.filtro.tipo
        val itens = resultado.itens.mapNotNull { d -> Relatorios.contribuicaoDe(d, tipo)?.let { d to it } }

        fun secao(titulo: String?, lista: List<Pair<Despesa, Long>>): SecaoDetalhe {
            val grupos = lista.groupBy { (d, _) ->
                when (agruparPor) {
                    AgruparPor.CATEGORIA -> d.categoria.trim().ifBlank { "Sem categoria" }
                    AgruparPor.MES -> chaveMes(d.dataMs)
                    AgruparPor.CONTA -> origem(d)
                }
            }.map { (nome, v) ->
                val linhas = v.sortedWith(compareBy({ it.first.dataMs }, { it.first.id })).map { (d, c) ->
                    val p = parcelaDaDescricao(d.descricao)
                    LinhaDetalhe(
                        id = d.id, data = d.dataMs, descricao = p?.first ?: d.descricao,
                        parcela = p?.let { "${it.second}/${it.third}" } ?: "",
                        origem = origem(d), situacao = if (d.pago) "Pago" else "Pendente", centavos = c
                    )
                }
                GrupoDetalhe(nome, linhas.sumOf { it.centavos }, linhas)
            }.let { gs ->
                if (agruparPor == AgruparPor.MES) gs.sortedBy { it.titulo }
                else gs.sortedWith(compareByDescending<GrupoDetalhe> { Math.abs(it.subtotalCentavos) }.thenBy { it.titulo.lowercase() })
            }
            return SecaoDetalhe(titulo, grupos.sumOf { it.subtotalCentavos }, grupos)
        }

        val secoes = if (tipo == TipoRelatorio.TODOS) {
            listOf(
                secao("Receitas", itens.filter { it.first.tipo == TipoDespesa.CREDITO }),
                secao("Despesas", itens.filter { it.first.tipo == TipoDespesa.DEBITO })
            ).filter { it.grupos.isNotEmpty() }
        } else {
            listOf(secao(null, itens)).filter { it.grupos.isNotEmpty() }
        }
        return Detalhamento(secoes)
    }

    /** Versão enxuta para imagem: [maxPorGrupo] maiores itens por grupo (por valor absoluto) e quantos ficaram de fora. */
    data class GrupoResumido(val titulo: String, val subtotalCentavos: Long, val linhas: List<LinhaDetalhe>, val restantes: Int)

    fun resumirParaImagem(secao: SecaoDetalhe, maxPorGrupo: Int = 5, maxGrupos: Int = 8): Pair<List<GrupoResumido>, Int> {
        val grupos = secao.grupos.take(maxGrupos).map { g ->
            val maiores = g.linhas.sortedByDescending { Math.abs(it.centavos) }.take(maxPorGrupo).sortedBy { it.data }
            GrupoResumido(g.titulo, g.subtotalCentavos, maiores, g.linhas.size - maiores.size)
        }
        return grupos to (secao.grupos.size - grupos.size)
    }
}

/** R48 — subtotais do extrato mensal: entradas, saídas e saldo (em centavos) e saldo por categoria; tudo fecha com a lista. */
data class ResumoExtrato(
    val entradasCentavos: Long,
    val saidasCentavos: Long,
    val porCategoria: List<Pair<String, Long>>
) {
    val saldoCentavos: Long get() = entradasCentavos - saidasCentavos
}

object ExtratoResumo {
    fun calcular(itens: List<com.meudinheiro.data.DespesasDomain>): ResumoExtrato {
        fun c(d: com.meudinheiro.data.DespesasDomain) = Dinheiro.centavos(d.valor)
        val entradas = itens.filter { it.tipo == TipoDespesa.CREDITO }.sumOf(::c)
        val saidas = itens.filter { it.tipo == TipoDespesa.DEBITO }.sumOf(::c)
        val porCat = itens.groupBy { it.categoria.trim().ifBlank { "Sem categoria" } }
            .map { (nome, v) -> nome to v.sumOf { if (it.tipo == TipoDespesa.CREDITO) c(it) else -c(it) } }
            .sortedWith(compareByDescending<Pair<String, Long>> { Math.abs(it.second) }.thenBy { it.first.lowercase() })
        return ResumoExtrato(entradas, saidas, porCat)
    }
}
