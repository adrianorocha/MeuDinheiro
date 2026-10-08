package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.text.Normalizer
import java.util.Calendar

/** Normalização de texto para buscas e comparações (sem acento, caixa ou espaços extras). */
object Texto {
    private val ACENTOS = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(ACENTOS, "").lowercase().trim().replace(Regex("\\s+"), " ")

    /** Tokens de descrição: sem dígitos/pontuação, ≥ 3 letras. */
    fun tokens(s: String): Set<String> =
        normalizar(s).replace(Regex("[^a-z ]"), " ").split(' ').filter { it.length >= 3 }.toSet()
}

enum class TipoRelatorio { DESPESA, RECEITA, TODOS }

/** R19 — filtro de relatório. */
data class FiltroRelatorio(
    val inicio: Long,
    val fim: Long,
    val contas: Set<String> = emptySet(),
    val cartoes: Set<Int> = emptySet(),
    val categorias: Set<String> = emptySet(),
    val tipo: TipoRelatorio = TipoRelatorio.TODOS,
    val pago: Boolean? = null,
    val texto: String = "",
    val incluirInternos: Boolean = false
)

data class LinhaCategoria(val nome: String, val total: Double, val percentual: Double)
data class LinhaMes(val mes: String, val total: Double)
data class ResumoPeriodo(val total: Double, val quantidade: Int)

data class ResultadoRelatorio(
    val filtro: FiltroRelatorio,
    val itens: List<Despesa>,
    val quantidade: Int,
    val total: Double,
    val media: Double,
    val maior: Double,
    val porCategoria: List<LinhaCategoria>,
    val porMes: List<LinhaMes>,
    val anterior: ResumoPeriodo,
    val variacaoPercentual: Double?
)

object Relatorios {

    /** Contribuição (em centavos, com sinal) do lançamento no tipo de relatório; `null` = fora do relatório. */
    private fun contribuicao(l: Despesa, tipo: TipoRelatorio): Long? {
        val c = Dinheiro.centavos(l.valor)
        val entrada = l.tipo == TipoDespesa.CREDITO
        return when (tipo) {
            TipoRelatorio.DESPESA -> when {
                !entrada -> c
                !l.semCartao -> -c // estorno no cartão abate a despesa
                else -> null
            }
            TipoRelatorio.RECEITA -> if (entrada && l.semCartao) c else null
            TipoRelatorio.TODOS -> if (entrada) c else -c
        }
    }

    /** Ids dos cartões do filtro: escolher o físico inclui os virtuais dele (R18). */
    private fun idsDeCartoes(selecionados: Set<Int>, cartoes: List<Cartao>): Set<Int> =
        selecionados.flatMap { id ->
            val c = cartoes.firstOrNull { it.id == id }
            if (c != null && c.cartaoPrincipalId == null) Financas.idsDoGrupo(c, cartoes) else setOf(id)
        }.toSet()

    private fun selecionar(filtro: FiltroRelatorio, despesas: List<Despesa>, cartoes: List<Cartao>, inicio: Long, fim: Long): List<Pair<Despesa, Long>> {
        val idsCartao = idsDeCartoes(filtro.cartoes, cartoes)
        val categorias = filtro.categorias.map(Texto::normalizar).toSet()
        val texto = Texto.normalizar(filtro.texto)
        return despesas.mapNotNull { l ->
            if (l.dataMs < inicio || l.dataMs > fim) return@mapNotNull null
            if (!filtro.incluirInternos && l.natureza != Natureza.NORMAL) return@mapNotNull null
            if (filtro.contas.isNotEmpty() && l.conta !in filtro.contas) return@mapNotNull null
            if (idsCartao.isNotEmpty() && l.cartaoId !in idsCartao) return@mapNotNull null
            if (categorias.isNotEmpty() && Texto.normalizar(l.categoria) !in categorias) return@mapNotNull null
            if (filtro.pago != null && l.pago != filtro.pago) return@mapNotNull null
            if (texto.isNotEmpty() && texto !in Texto.normalizar(l.descricao) && texto !in Texto.normalizar(l.categoria)) {
                return@mapNotNull null
            }
            contribuicao(l, filtro.tipo)?.let { l to it }
        }
    }

    fun gerar(filtro: FiltroRelatorio, despesas: List<Despesa>, cartoes: List<Cartao> = emptyList()): ResultadoRelatorio {
        val sel = selecionar(filtro, despesas, cartoes, filtro.inicio, filtro.fim)
        val totalC = sel.sumOf { it.second }

        // Por categoria: sempre pelo "lado" do relatório (despesas, ou receitas no tipo RECEITA).
        val ladoCategoria = if (filtro.tipo == TipoRelatorio.TODOS) {
            selecionar(filtro.copy(tipo = TipoRelatorio.DESPESA), despesas, cartoes, filtro.inicio, filtro.fim)
        } else sel
        val porCatC = ladoCategoria.groupBy { it.first.categoria.trim().ifBlank { "Sem categoria" } }
            .mapValues { (_, v) -> v.sumOf { it.second } }
            .filterValues { it > 0 }
        val somaCat = porCatC.values.sum()
        val porCategoria = porCatC.entries.sortedByDescending { it.value }.map {
            LinhaCategoria(it.key, Dinheiro.reais(it.value), if (somaCat > 0) it.value.toDouble() / somaCat * 100 else 0.0)
        }

        val porMes = sel.groupBy { chaveMes(it.first.dataMs) }
            .mapValues { (_, v) -> v.sumOf { it.second } }
            .toSortedMap()
            .map { LinhaMes(it.key, Dinheiro.reais(it.value)) }

        val duracao = filtro.fim - filtro.inicio + 1
        val antFim = filtro.inicio - 1
        val antIni = filtro.inicio - duracao
        val ant = selecionar(filtro, despesas, cartoes, antIni, antFim)
        val antTotalC = ant.sumOf { it.second }

        val qtd = sel.size
        val variacao = if (antTotalC > 0) (totalC - antTotalC).toDouble() / antTotalC * 100 else null
        return ResultadoRelatorio(
            filtro = filtro,
            itens = sel.map { it.first }.sortedByDescending { it.dataMs },
            quantidade = qtd,
            total = Dinheiro.reais(totalC),
            media = if (qtd > 0) Dinheiro.reais(totalC / qtd) else 0.0,
            maior = Dinheiro.reais(sel.maxOfOrNull { Dinheiro.centavos(it.first.valor) } ?: 0L),
            porCategoria = porCategoria,
            porMes = porMes,
            anterior = ResumoPeriodo(Dinheiro.reais(antTotalC), ant.size),
            variacaoPercentual = variacao
        )
    }

    private fun chaveMes(ms: Long): String =
        Calendar.getInstance().apply { timeInMillis = ms }.let { "%04d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

    // ------------------------------------------------------------ R20 modelos

    enum class Modelo(val titulo: String) {
        EXTRATO_CONTA("Extrato por conta"),
        FATURA_CARTAO("Fatura do cartão"),
        GASTOS_CATEGORIA("Gastos por categoria"),
        RECEITAS_DESPESAS("Receitas × despesas do mês"),
        ANUAL_IR("Anual para Imposto de Renda (saúde e educação)")
    }

    /** Filtro pronto de cada modelo; [mes]/[ano] referenciam o período e [conta]/[cartaoId] o recorte. */
    fun filtroDoModelo(modelo: Modelo, mes: Int, ano: Int, conta: String? = null, cartaoId: Int? = null): FiltroRelatorio = when (modelo) {
        Modelo.EXTRATO_CONTA -> FiltroRelatorio(
            Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano),
            contas = setOfNotNull(conta), incluirInternos = true
        )
        Modelo.FATURA_CARTAO -> FiltroRelatorio(
            Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano),
            cartoes = setOfNotNull(cartaoId), tipo = TipoRelatorio.DESPESA
        )
        Modelo.GASTOS_CATEGORIA -> FiltroRelatorio(
            Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano), tipo = TipoRelatorio.DESPESA
        )
        Modelo.RECEITAS_DESPESAS -> FiltroRelatorio(
            Financas.inicioDoMes(mes, ano), Financas.fimDoMes(mes, ano), tipo = TipoRelatorio.TODOS
        )
        Modelo.ANUAL_IR -> FiltroRelatorio(
            Financas.inicioDoMes(1, ano), Financas.fimDoMes(12, ano),
            categorias = setOf("Saúde", "Educação"), tipo = TipoRelatorio.DESPESA
        )
    }

    // ------------------------------------------------------------ exportação CSV

    /** CSV `;`, valores `1234,56`, pronto para planilhas em pt-BR (o chamador grava com BOM UTF-8). */
    fun csv(resultado: ResultadoRelatorio, conta: (String) -> String = { it }): String {
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        fun num(v: Double) = "%.2f".format(java.util.Locale.US, v).replace('.', ',')
        val data = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
        val sb = StringBuilder("Data;Descrição;Categoria;Conta;Tipo;Pago;Valor\n")
        resultado.itens.forEach { d ->
            sb.append(data.format(d.data)).append(';').append(esc(d.descricao)).append(';').append(esc(d.categoria)).append(';')
                .append(esc(conta(d.conta))).append(';')
                .append(if (d.tipo == TipoDespesa.CREDITO) "Receita" else "Despesa").append(';')
                .append(if (d.pago) "Sim" else "Não").append(';')
                .append(num(if (d.tipo == TipoDespesa.CREDITO) d.valor else -d.valor)).append('\n')
        }
        return sb.toString()
    }
}
