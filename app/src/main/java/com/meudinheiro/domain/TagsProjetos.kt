package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Projeto
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar

/**
 * R56 — Tags e projetos. Funções puras, em centavos; espelham `portal/src/lib/finance/tags.ts`
 * (mesmos cenários em `TagsProjetosTest` e `tags.test.ts`).
 * - Rótulo: texto digitado, sem `#` inicial, espaços aparados/colapsados, até 30 caracteres.
 * - Chave: rótulo normalizado (minúsculas, sem acento). Duas tags com a mesma chave são a mesma tag.
 */
object Tags {
    const val MAX_TAGS = 5
    const val MAX_CHARS = 30

    fun rotulo(s: String): String {
        val t = s.trim().trimStart('#').replace(Regex("\\s+"), " ").trim()
        return if (t.length > MAX_CHARS) t.substring(0, MAX_CHARS).trim() else t
    }

    fun chave(s: String): String = Texto.normalizar(rotulo(s))

    /** Lista de rótulos válida: limpa, sem repetidas (pela chave, mantém a 1ª), no máximo [MAX_TAGS]. */
    fun normalizar(entrada: List<String?>?): List<String> {
        val out = ArrayList<String>()
        val vistas = HashSet<String>()
        for (bruto in entrada.orEmpty()) {
            if (bruto == null) continue
            val r = rotulo(bruto)
            val k = Texto.normalizar(r)
            if (k.isEmpty() || !vistas.add(k)) continue
            out += r
            if (out.size >= MAX_TAGS) break
        }
        return out
    }

    fun tem(tags: List<String>?, chaveOuRotulo: String): Boolean {
        val k = chave(chaveOuRotulo)
        return k.isNotEmpty() && tags.orEmpty().any { chave(it) == k }
    }

    data class Usada(val chave: String, val rotulo: String, val usos: Int)

    /** Tags já usadas (rótulo do primeiro uso), por uso decrescente e depois alfabética — sugestões do campo de tags. */
    fun usadas(despesas: List<Despesa>): List<Usada> = usadasDeListas(despesas.map { it.tags })

    /** Mesma regra de [usadas] a partir de listas de tags (ex.: lançamentos da tela, que não são [Despesa]). */
    fun usadasDeListas(listas: List<List<String>>): List<Usada> {
        val m = LinkedHashMap<String, Usada>()
        for (tags in listas) for (t in tags) {
            val k = chave(t)
            if (k.isEmpty()) continue
            val g = m[k]
            m[k] = if (g != null) g.copy(usos = g.usos + 1) else Usada(k, rotulo(t), 1)
        }
        return m.values.sortedWith(compareByDescending<Usada> { it.usos }.thenBy { it.rotulo.lowercase() })
    }
}

enum class StatusProjeto { SEM_ORCAMENTO, NO_ORCAMENTO, ATENCAO, ESTOUROU }

data class ResumoProjeto(
    val projeto: Projeto,
    /** gasto líquido: despesas somam, estornos de cartão abatem */
    val gastoCentavos: Long,
    val orcamentoCentavos: Long?,
    /** 1,0 = 100% do orçamento; nulo sem orçamento */
    val percentual: Double?,
    val status: StatusProjeto,
    val quantidade: Int,
    val porCategoria: List<Pair<String, Long>>,
    /** `AAAA-MM` → centavos */
    val porMes: List<Pair<String, Long>>,
    val itens: List<Despesa>
)

object Projetos {

    /** Conta para o projeto? NORMAL, com a tag e dentro do período (inicio/fim) quando definido. */
    fun lancamentoDoProjeto(p: Projeto, d: Despesa): Boolean {
        if (d.natureza != Natureza.NORMAL) return false
        if (!Tags.tem(d.tags, p.tag)) return false
        if (p.inicio != null && d.dataMs < p.inicio) return false
        if (p.fim != null && d.dataMs > p.fim) return false
        return true
    }

    /** Mesmas regras do R19 (tipo DESPESA): débito soma; estorno de cartão abate; receita comum fica de fora. */
    private fun contribuicao(d: Despesa): Long? {
        val c = Dinheiro.centavos(d.valor)
        return when {
            d.tipo == TipoDespesa.DEBITO -> c
            !d.semCartao -> -c
            else -> null
        }
    }

    private fun chaveMes(ms: Long): String =
        Calendar.getInstance().apply { timeInMillis = ms }.let { "%04d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

    fun resumo(p: Projeto, despesas: List<Despesa>): ResumoProjeto {
        val sel = despesas.mapNotNull { d -> if (lancamentoDoProjeto(p, d)) contribuicao(d)?.let { d to it } else null }
        val total = sel.sumOf { it.second }
        val porCat = sel.groupBy { Texto.normalizar(it.first.categoria) }
            .map { (_, v) -> (v.first().first.categoria.trim().ifBlank { "Sem categoria" }) to v.sumOf { it.second } }
            .sortedWith(compareByDescending<Pair<String, Long>> { kotlin.math.abs(it.second) }.thenBy { it.first.lowercase() })
        val porMes = sel.groupBy { chaveMes(it.first.dataMs) }.toSortedMap().map { (k, v) -> k to v.sumOf { it.second } }
        val orc = p.orcamento?.takeIf { it > 0 }?.let { Dinheiro.centavos(it) }
        val percentual = orc?.let { total.toDouble() / it }
        val status = when {
            percentual == null -> StatusProjeto.SEM_ORCAMENTO
            percentual > 1.0 -> StatusProjeto.ESTOUROU
            percentual >= 0.8 -> StatusProjeto.ATENCAO
            else -> StatusProjeto.NO_ORCAMENTO
        }
        return ResumoProjeto(
            projeto = p, gastoCentavos = total, orcamentoCentavos = orc, percentual = percentual, status = status,
            quantidade = sel.size, porCategoria = porCat, porMes = porMes,
            itens = sel.map { it.first }.sortedWith(compareByDescending<Despesa> { it.dataMs }.thenByDescending { it.id })
        )
    }

    /** Mensagem de erro ou `null` se válido ([idAtual] = projeto em edição, para a unicidade da tag). */
    fun validar(nome: String, tag: String, orcamento: Double?, inicio: Long?, fim: Long?, outros: List<Projeto>, idAtual: Int? = null): String? {
        if (nome.isBlank()) return "Informe o nome do projeto."
        if (Tags.chave(tag).isEmpty()) return "Informe a tag do projeto."
        if (outros.any { it.id != idAtual && Tags.chave(it.tag) == Tags.chave(tag) }) return "Já existe um projeto com esta tag."
        if (orcamento != null && !(orcamento >= 0 && orcamento.isFinite())) return "O orçamento deve ser um valor válido."
        if (inicio != null && fim != null && fim < inicio) return "A data final não pode ser anterior à inicial."
        return null
    }
}
