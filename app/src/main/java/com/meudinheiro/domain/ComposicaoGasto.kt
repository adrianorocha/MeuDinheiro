package com.meudinheiro.domain

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Despesa

/**
 * R96 — composição do gasto de uma pessoa por categoria (mini gráfico do relatório de Pessoas).
 *
 * Lógica PURA em centavos inteiros. Base = os acertos A_RECEBER (a cota da pessoa em compras/gastos/rateios) que já passaram
 * pelos filtros do relatório (período, situação); pagamentos/acertos A_PAGAR não entram. A categoria vem do lançamento vinculado;
 * acerto avulso, lançamento removido ou categoria vazia = "Sem categoria". Não grava nada.
 */
object ComposicaoGasto {
    const val SEM_CATEGORIA = "Sem categoria"
    const val OUTROS = "Outros"
    const val MAX_FATIAS = 5

    /** Paleta categórica (mesma de `PALETA_COMPOSICAO` do portal: Okabe-Ito/Tol). Cor por POSIÇÃO: maior fatia = posição 0. */
    val PALETA_HEX = listOf("#0072B2", "#D55E00", "#009E73", "#AA4499", "#B58900", "#882255", "#1B7F8C")
    const val COR_OUTROS_HEX = "#7C8794"

    data class Fatia(val categoria: String, val centavos: Long, val percentual: Int, val outros: Boolean = false)

    data class Composicao(val total: Long, val fatias: List<Fatia>) {
        val vazia get() = fatias.isEmpty()

        /** Fatias desenhadas: percentual 0 não tem largura visível (continua somando nos centavos do modelo), como no portal. */
        val visiveis get() = fatias.filter { it.percentual > 0 }
    }

    val VAZIA = Composicao(0L, emptyList())

    fun corHex(indice: Int, fatia: Fatia) = if (fatia.outros) COR_OUTROS_HEX else PALETA_HEX[indice % PALETA_HEX.size]

    /** "Categoria 42%" (o percentual escrito, além da cor); nome acima de [max] vira "Nome..." (igual ao portal). */
    fun rotulo(f: Fatia, max: Int = 22): String {
        val n = if (f.categoria.length > max) f.categoria.take(max - 1).trimEnd() + "..." else f.categoria
        return "$n ${f.percentual}%"
    }

    /** Texto para leitores de tela: "Composição do gasto: Mercado 60% (R$ 12,00), Lazer 40% (R$ 8,00)". */
    fun descricaoAcessivel(c: Composicao) =
        if (c.vazia) "Sem gasto" else "Composição do gasto: " + c.visiveis.joinToString(", ") { "${it.categoria} ${it.percentual}% (${FormatoCentavos.brl(it.centavos)})" }

    /** [acertosDaPessoa] já filtrados (período/situação). */
    fun calcular(acertosDaPessoa: List<Acerto>, despesasPorId: Map<Long, Despesa>): Composicao {
        val porCategoria = LinkedHashMap<String, Long>()
        for (a in acertosDaPessoa) {
            if (a.tipo != Rateios.A_RECEBER) continue
            val cents = Dinheiro.centavos(a.valor)
            if (cents <= 0) continue
            val cat = a.despesaId?.let { despesasPorId[it] }?.categoria?.trim().orEmpty().ifEmpty { SEM_CATEGORIA }
            porCategoria[cat] = (porCategoria[cat] ?: 0L) + cents
        }
        return deCategorias(porCategoria)
    }

    /** Ordena, agrega em "Outros" (acima de [MAX_FATIAS]) e calcula percentuais inteiros somando 100 (maior resto). */
    fun deCategorias(porCategoria: Map<String, Long>): Composicao {
        val itens = porCategoria.filter { it.value > 0 }.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        val total = itens.sumOf { it.second }
        if (total <= 0) return VAZIA
        val topo = itens.take(MAX_FATIAS)
        val resto = itens.drop(MAX_FATIAS).sumOf { it.second }
        val base = topo.map { Triple(it.first, it.second, false) } + (if (resto > 0) listOf(Triple(OUTROS, resto, true)) else emptyList())

        // maior resto: piso de cada percentual; os pontos que faltam vão para os maiores restos (empate: maior valor, depois ordem)
        val pisos = base.map { it.second * 100 / total }
        val restos = base.map { it.second * 100 % total }
        var faltam = (100 - pisos.sum()).toInt()
        val extra = IntArray(base.size)
        for (i in base.indices.sortedWith(compareByDescending<Int> { restos[it] }.thenByDescending { base[it].second }.thenBy { it })) {
            if (faltam <= 0) break
            extra[i] = 1; faltam--
        }
        return Composicao(total, base.mapIndexed { i, b -> Fatia(b.first, b.second, (pisos[i] + extra[i]).toInt(), b.third) })
    }
}
