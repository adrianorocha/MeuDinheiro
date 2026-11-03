package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import java.text.Collator
import java.util.Locale

/**
 * R103 — "Onde cortar": aponta, com base nos 3 últimos meses COMPLETOS (mesma janela do fluxo de caixa), onde dá para economizar e
 * quanto. Paridade com `portal/src/lib/finance/cortes.ts`. Tudo em CENTAVOS; puro; nada é gravado. Estimativas, não recomendação.
 *
 * Só entram débitos NORMAL (e estornos de cartão, que abatem). Cada lançamento cai em UM único bloco, em ordem de prioridade:
 *  1 EVITAVEL   — juros, multa, mora, IOF, encargos, tarifas, anuidade, rotativo: 100% evitáveis (total da janela / 3).
 *  2 ASSINATURA — assinaturas/recorrências de débito fora de categorias essenciais: economia = valor mensal.
 *  3 FREQUENTE  — mesma descrição em >= 6 compras, em >= 2 meses, ticket médio <= R$ 40, categoria não essencial: 30% do mensal.
 *  4 FLEXIVEL   — o restante das categorias não essenciais: média mensal x pct (padrão 20%).
 *  5 ESSENCIAL  — categorias essenciais: média mensal x pct (padrão 5%).
 * Categoria "reserva" nunca é sugerida. Blocos com média < R$ 20 ou economia < R$ 5 não aparecem.
 */
object Cortes {

    enum class Tipo { EVITAVEL, ASSINATURA, FREQUENTE, FLEXIVEL, ESSENCIAL }

    const val PCT_FLEXIVEL_PADRAO = 0.2
    const val PCT_ESSENCIAL_PADRAO = 0.05
    const val PCT_FREQUENTE = 0.3
    const val MEDIA_MINIMA = 2000L
    const val ECONOMIA_MINIMA = 500L
    const val FREQ_MIN_COMPRAS = 6
    const val FREQ_MIN_MESES = 2
    const val FREQ_TICKET_MAX = 4000L
    private const val MESES_JANELA = 3
    const val PREFIXO_ID = "corte:"

    private val RE_EVITAVEL = Regex("\\b(juros|multa|mora|iof|encargos?|tarifas?|anuidade|rotativo|atraso|cesta de servicos|seguro prestamista)\\b")
    private val RE_PARCELA = Regex("\\(\\s*\\d+\\s*/\\s*\\d+\\s*\\)")

    private val NECESSIDADES = setOf(
        "supermercado", "saude", "educacao", "transporte", "combustivel", "oficina", "casa", "aluguel", "moradia",
        "contas", "luz", "agua", "internet"
    )

    data class Corte(
        /** Estável entre recarregamentos (serve para dispensar). */
        val id: String,
        val tipo: Tipo,
        val titulo: String,
        val categoria: String,
        /** Média mensal atual do bloco (centavos). */
        val mensalAtual: Long,
        /** Economia mensal estimada (centavos). */
        val economiaMensal: Long,
        val calculo: List<String>
    )

    data class GastoPesado(val nome: String, val categoria: String, val mensal: Long, val compras: Int)

    data class Plano(val alvo: Long, val selecionados: List<Corte>, val economia: Long, val atinge: Boolean, val falta: Long)

    data class Parametros(
        val pctFlexivel: Double = PCT_FLEXIVEL_PADRAO,
        val pctEssencial: Double = PCT_ESSENCIAL_PADRAO,
        /** Alvo mensal do plano (centavos). 0 = sem plano. */
        val alvoMensal: Long = 0L,
        val dispensados: Set<String> = emptySet()
    )

    data class Resultado(
        val cortes: List<Corte>,
        val totalMensal: Long,
        val totalAnual: Long,
        val maioresGastos: List<GastoPesado>,
        val mesesComDados: Int,
        val plano: Plano?
    )

    private class Item(val d: Despesa, val v: Long, val chave: String, val cat: String, val catNorm: String, val mes: Int)

    private fun semCartao(d: Despesa) = d.cartaoId == null || d.cartaoId == 0

    private fun itensDaJanela(despesas: List<Despesa>, agora: Long): List<Item> {
        val mes = Financas.mesDe(agora)
        val ano = Financas.anoDe(agora)
        val (mi, ai) = FluxoCaixa.deslocarMes(mes, ano, -MESES_JANELA)
        val (mf, af) = FluxoCaixa.deslocarMes(mes, ano, -1)
        val ini = Financas.inicioDoMes(mi, ai)
        val fim = Financas.fimDoMes(mf, af)
        val out = ArrayList<Item>()
        for (d in despesas) {
            if (d.dataMs < ini || d.dataMs > fim || d.natureza != Natureza.NORMAL) continue
            val cents = Dinheiro.centavos(d.valor)
            val v = when {
                d.tipo == TipoDespesa.DEBITO -> cents
                !semCartao(d) -> -cents
                else -> continue
            }
            val cat = d.categoria.trim().ifEmpty { "Sem categoria" }
            out += Item(d, v, Analises.chaveAssinatura(d.descricao), cat, Texto.normalizar(cat), Financas.anoDe(d.dataMs) * 12 + Financas.mesDe(d.dataMs))
        }
        return out
    }

    private fun essencial(catNorm: String) = catNorm in NECESSIDADES
    private fun brl(c: Long) = FormatoCentavos.brl(c)
    private fun pct(f: Double) = "${Math.round(f * 100)}%"
    private fun soma(l: List<Item>) = l.sumOf { it.v }
    private fun media(total: Long): Long = Math.round(total / MESES_JANELA.toDouble())

    private fun nomeDe(l: List<Item>): String {
        var a = l[0]
        for (b in l) if (b.d.dataMs > a.d.dataMs) a = b
        return a.d.descricao.replace(RE_PARCELA, "").trim().ifEmpty { l[0].d.descricao }
    }

    private val PRIORIDADE = mapOf(Tipo.EVITAVEL to 1, Tipo.ASSINATURA to 2, Tipo.FREQUENTE to 3, Tipo.FLEXIVEL to 4, Tipo.ESSENCIAL to 5)

    fun calcular(despesas: List<Despesa>, fixas: List<DespesaFixa>, agora: Long, par: Parametros = Parametros()): Resultado {
        val todos = itensDaJanela(despesas, agora)
        val variaveis = todos.filter { FluxoCaixa.ehVariavel(it.d) }
        val usados = HashSet<Long>()
        val cortes = ArrayList<Corte>()
        val mesesComDados = todos.filter { FluxoCaixa.ehVariavel(it.d) && it.v > 0 }.map { it.mes }.toSet().size

        // 1) evitáveis
        val evitaveis = todos.filter { it.d.tipo == TipoDespesa.DEBITO && RE_EVITAVEL.containsMatchIn(Texto.normalizar("${it.d.descricao} ${it.cat}")) }
        for ((k, l) in evitaveis.groupBy { it.chave }) {
            val total = soma(l)
            val mensal = media(total)
            l.forEach { usados += it.d.id }
            if (mensal < ECONOMIA_MINIMA) continue
            cortes += Corte(
                id = "corte:evitavel:$k", tipo = Tipo.EVITAVEL, titulo = "Evitar: ${nomeDe(l)}", categoria = l[0].cat,
                mensalAtual = mensal, economiaMensal = mensal,
                calculo = listOf(
                    "${l.size} lançamento(s) de juros/multas/tarifas somam ${brl(total)} em $MESES_JANELA meses (média ${brl(mensal)}/mês)",
                    "Regra: juros, multa, mora, IOF, encargos, tarifas e anuidade são evitáveis (pague em dia, negocie isenção/troque de pacote)."
                )
            )
        }

        // 2) assinaturas fora de categorias essenciais
        val chavesAssinatura = HashSet<String>()
        for (a in Analises.assinaturas(agora, despesas, fixas)) {
            if (essencial(Texto.normalizar(a.categoria))) continue
            val mensal = Dinheiro.centavos(a.totalMensal)
            val k = Analises.chaveAssinatura(a.nome)
            chavesAssinatura += k
            if (mensal < ECONOMIA_MINIMA) continue
            cortes += Corte(
                id = "corte:assinatura:$k", tipo = Tipo.ASSINATURA, titulo = "Cancelar ou renegociar: ${a.nome}", categoria = a.categoria,
                mensalAtual = mensal, economiaMensal = mensal,
                calculo = listOf(
                    "${if (a.origem == Analises.OrigemAssinatura.FIXA) "Recorrência cadastrada" else "Assinatura detectada"}: ${brl(mensal)}/mês (${brl(mensal * 12)}/ano)",
                    "Regra: economia = valor mensal inteiro, se cancelar; renegociar plano ou dividir o acesso reduz parte."
                )
            )
        }

        val pool = variaveis.filter { it.d.id !in usados && it.chave !in chavesAssinatura && it.catNorm != "reserva" }

        // 3) compras pequenas e frequentes
        for ((k, l) in pool.filter { !essencial(it.catNorm) && it.v > 0 }.groupBy { it.chave }) {
            if (k.isEmpty() || l.size < FREQ_MIN_COMPRAS || l.map { it.mes }.toSet().size < FREQ_MIN_MESES) continue
            val total = soma(l)
            if (total.toDouble() / l.size > FREQ_TICKET_MAX) continue
            l.forEach { usados += it.d.id }
            val mensal = media(total)
            val eco = Math.round(mensal * PCT_FREQUENTE)
            if (eco < ECONOMIA_MINIMA) continue
            cortes += Corte(
                id = "corte:frequente:$k", tipo = Tipo.FREQUENTE, titulo = "Compras pequenas e frequentes: ${nomeDe(l)}", categoria = l[0].cat,
                mensalAtual = mensal, economiaMensal = eco,
                calculo = listOf(
                    "${l.size} compras em $MESES_JANELA meses, ticket médio ${brl(Math.round(total.toDouble() / l.size))}, total ${brl(total)} (média ${brl(mensal)}/mês)",
                    "Regra: $FREQ_MIN_COMPRAS+ compras em $FREQ_MIN_MESES+ meses, ticket até ${brl(FREQ_TICKET_MAX)}. Estimativa = ${pct(PCT_FREQUENTE)} do mensal = ${brl(eco)}/mês"
                )
            )
        }

        // 4 e 5) categorias
        for ((_, l) in pool.filter { it.d.id !in usados }.groupBy { it.catNorm }) {
            val total = soma(l)
            val media = media(total)
            if (media < MEDIA_MINIMA) continue
            val ess = essencial(l[0].catNorm)
            val p = if (ess) par.pctEssencial else par.pctFlexivel
            val eco = Math.round(media * p)
            if (eco < ECONOMIA_MINIMA) continue
            val topo = l.groupBy { it.chave }.values.map { nomeDe(it) to soma(it) }.sortedByDescending { it.second }.take(3)
                .map { "${it.first} ${brl(media(it.second))}/mês" }
            cortes += Corte(
                id = "corte:categoria:${l[0].catNorm}", tipo = if (ess) Tipo.ESSENCIAL else Tipo.FLEXIVEL,
                titulo = if (ess) "Pesquisar preço e desperdício: ${l[0].cat}" else "Reduzir gastos com ${l[0].cat}",
                categoria = l[0].cat, mensalAtual = media, economiaMensal = eco,
                calculo = listOf(
                    "Média dos últimos $MESES_JANELA meses: ${brl(media)}/mês",
                    "Regra: categoria ${if (ess) "essencial" else "flexível"}, redução de ${pct(p)} = ${brl(eco)}/mês"
                ) + (if (topo.isNotEmpty()) listOf("O que mais pesa: ${topo.joinToString("; ")}") else emptyList())
            )
        }

        val col = Collator.getInstance(Locale("pt", "BR"))
        val ordenados = cortes.filter { it.id !in par.dispensados }.sortedWith(
            compareBy<Corte> { PRIORIDADE.getValue(it.tipo) }.thenByDescending { it.economiaMensal }.thenComparator { a, b -> col.compare(a.titulo, b.titulo) }
        )
        val totalMensal = ordenados.sumOf { it.economiaMensal }

        val maiores = variaveis.filter { it.catNorm != "reserva" }.groupBy { it.chave }.entries
            .filter { it.key.isNotEmpty() }
            .map { (_, l) -> GastoPesado(nomeDe(l), l[0].cat, media(soma(l)), l.count { it.v > 0 }) }
            .filter { it.mensal > 0 }
            .sortedWith(compareByDescending<GastoPesado> { it.mensal }.thenComparator { a, b -> col.compare(a.nome, b.nome) })
            .take(8)

        return Resultado(ordenados, totalMensal, totalMensal * 12, maiores, mesesComDados, montarPlano(ordenados, par.alvoMensal))
    }

    /** Soma os cortes na ordem de prioridade até atingir o alvo (centavos/mês); nulo se não há alvo. */
    fun montarPlano(cortes: List<Corte>, alvo: Long): Plano? {
        if (alvo <= 0) return null
        val sel = ArrayList<Corte>()
        var acc = 0L
        for (c in cortes) {
            if (acc >= alvo) break
            sel += c
            acc += c.economiaMensal
        }
        return Plano(alvo, sel, acc, acc >= alvo, maxOf(0L, alvo - acc))
    }

    data class FluxoComCorte(
        val saldoFinalDepois: Long,
        val mesesNegativosAntes: Int,
        val mesesNegativosDepois: Int,
        val primeiroNegativoDepois: Int?,
        val saldos: List<Long>
    )

    /** Refaz os saldos do fluxo com [economiaMensal] (centavos) aplicada desde o mês atual: saldo_j + economia x (j+1). */
    fun aplicarCorteNoFluxo(r: FluxoCaixa.Resultado, economiaMensal: Long): FluxoComCorte {
        val saldos = r.meses.mapIndexed { j, m -> m.saldoFinal + economiaMensal * (j + 1) }
        val primeiro = saldos.indexOfFirst { it < 0 }
        return FluxoComCorte(
            saldos.lastOrNull() ?: r.saldoInicial, r.meses.count { it.negativo }, saldos.count { it < 0 },
            if (primeiro == -1) null else primeiro, saldos
        )
    }
}
