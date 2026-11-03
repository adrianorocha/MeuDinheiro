package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.util.Locale

/**
 * R98 — insights de economia: regras simples e explicáveis; cada um traz a regra, o cálculo e o valor estimado em R$/mês.
 * Paridade com `portal/src/lib/finance/insightsEconomia.ts` (R97). Valores em CENTAVOS. São estimativas sobre os lançamentos
 * (não é recomendação personalizada de investimento).
 *
 *  CATEGORIA   — média mensal da categoria nos 3 meses completos mais recentes (R) > média dos 6 meses anteriores (A; meses −9..−4)
 *                × (1 + limiar), com limiar = (1+π)^6 − 1 + 5 p.p. (π = inflação mensal informada). A categoria precisa ter gasto
 *                em ≥ 3 dos 6 meses anteriores e o excesso ≥ R$ 10. Estimativa = R − A × (1+π)^6 (o que passou da inflação).
 *  ASSINATURAS — soma das assinaturas/recorrências de débito ([Analises.assinaturas]); estimativa = valor da MAIOR.
 *  REGRA_503020 — último mês completo com receita > 0: necessidades ≤ 50%, desejos ≤ 30%, poupança ≥ 20% ([Analises.regra503020]).
 *                Estimativa = max(excesso de desejos sobre 30%, faltante de poupança até 20%); só aparece se algum grupo fugir.
 *  META        — metas com prazo futuro e valor guardado < objetivo: aporte mensal P que, rendendo à taxa LÍQUIDA mensal equivalente
 *                do produto CDI da simulação (R98-B) no prazo, atinge o objetivo (aportes no fim do mês):
 *                P = (objetivo − guardado × (1+r)^m) × r / ((1+r)^m − 1). Mostra também o aporte sem rendimento (falta/m).
 */
object InsightsEconomia {

    enum class Tipo { CATEGORIA, ASSINATURAS, REGRA_503020, META }

    /** [id] estável entre recarregamentos (usado para dispensar). [calculo]: linhas em texto pt-BR. [valorMensal] em centavos. */
    data class Insight(
        val id: String,
        val tipo: Tipo,
        val titulo: String,
        val regra: String,
        val calculo: List<String>,
        val valorMensal: Long
    )

    /** [pctCdi] 1 = 100% do CDI; taxas em fração. */
    data class Parametros(val inflacaoMensal: Double, val cdiMensal: Double, val pctCdi: Double)

    const val LIMIAR_PP = 0.05
    const val MIN_EXCESSO_CENTAVOS = 1000L

    private fun brl(c: Long) = FormatoCentavos.brl(c)

    /** Fração -> "12,3%" (uma casa, vírgula). */
    fun pct(f: Double): String = BigDecimal(f * 100).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"

    private fun semCartao(d: Despesa) = d.cartaoId == null || d.cartaoId == 0

    private class GastoCat(val nome: String, val porMes: LongArray)

    /** Gasto por categoria (centavos) em cada mês de [de]..[ate] (offsets em relação ao mês atual; negativos = passado). */
    private fun gastoPorCategoria(despesas: List<Despesa>, agora: Long, de: Int, ate: Int): LinkedHashMap<String, GastoCat> {
        val mes = Financas.mesDe(agora)
        val ano = Financas.anoDe(agora)
        val total = ate - de + 1
        val mapa = LinkedHashMap<String, GastoCat>()
        for (i in 0 until total) {
            val (m, a) = FluxoCaixa.deslocarMes(mes, ano, de + i)
            val ini = Financas.inicioDoMes(m, a)
            val fim = Financas.fimDoMes(m, a)
            for (d in despesas) {
                if (d.dataMs < ini || d.dataMs > fim || !FluxoCaixa.ehVariavel(d)) continue
                val nome = d.categoria.trim()
                if (nome.isEmpty()) continue
                val chave = nome.lowercase(Locale("pt", "BR"))
                val g = mapa.getOrPut(chave) { GastoCat(nome, LongArray(total)) }
                val cents = Dinheiro.centavos(d.valor)
                if (d.tipo == TipoDespesa.DEBITO) g.porMes[i] += cents
                else if (!semCartao(d)) g.porMes[i] -= cents
            }
        }
        return mapa
    }

    fun categorias(despesas: List<Despesa>, agora: Long, inflacaoMensal: Double): List<Insight> {
        val recentes = gastoPorCategoria(despesas, agora, -3, -1)
        val anteriores = gastoPorCategoria(despesas, agora, -9, -4)
        val corr6 = Math.pow(1 + inflacaoMensal, 6.0) - 1
        val limiar = corr6 + LIMIAR_PP
        val out = ArrayList<Insight>()
        for ((chave, r) in recentes) {
            val a = anteriores[chave] ?: continue
            if (a.porMes.count { it > 0 } < 3) continue
            val mediaR = r.porMes.sumOf { maxOf(0L, it) } / 3.0
            val mediaA = a.porMes.sumOf { maxOf(0L, it) } / 6.0
            if (!(mediaA > 0) || !(mediaR > mediaA * (1 + limiar))) continue
            val excesso = Math.round(mediaR - mediaA * (1 + corr6))
            if (excesso < MIN_EXCESSO_CENTAVOS) continue
            out += Insight(
                id = "categoria:$chave",
                tipo = Tipo.CATEGORIA,
                titulo = "${r.nome}: gasto subiu acima da inflação",
                regra = "Gasto médio dos últimos 3 meses acima da média dos 6 meses anteriores em mais que a inflação do período + 5 pontos percentuais.",
                calculo = listOf(
                    "Média dos últimos 3 meses: ${brl(Math.round(mediaR))}",
                    "Média dos 6 meses anteriores: ${brl(Math.round(mediaA))}",
                    "Variação: ${pct(mediaR / mediaA - 1)} (limite: inflação de 6 meses ${pct(corr6)} + 5 p.p. = ${pct(limiar)})",
                    "Estimativa = média recente − média anterior corrigida pela inflação = ${brl(excesso)}/mês"
                ),
                valorMensal = excesso
            )
        }
        return out.sortedByDescending { it.valorMensal }
    }

    fun assinaturas(agora: Long, despesas: List<Despesa>, fixas: List<DespesaFixa>): Insight? {
        val lista = Analises.assinaturas(agora, despesas, fixas)
        if (lista.isEmpty()) return null
        val total = lista.sumOf { Dinheiro.centavos(it.totalMensal) }
        val maior = lista[0]
        val vMaior = Dinheiro.centavos(maior.totalMensal)
        if (total <= 0) return null
        return Insight(
            id = "assinaturas",
            tipo = Tipo.ASSINATURAS,
            titulo = "Revise assinaturas e gastos recorrentes",
            regra = "Soma das assinaturas e recorrências de débito; a estimativa é o valor da maior, que seria economizado se for cancelada ou renegociada.",
            calculo = listOf(
                "${lista.size} item(ns) somam ${brl(total)}/mês (${brl(total * 12)}/ano)",
                "Maior peso: ${maior.nome} — ${brl(vMaior)}/mês (${pct(vMaior.toDouble() / total)} do total)"
            ),
            valorMensal = vMaior
        )
    }

    /** Último mês completo com receita; nulo se não houver ou se tudo estiver dentro dos alvos. */
    fun regra503020(despesas: List<Despesa>, agora: Long): Insight? {
        val (mes, ano) = FluxoCaixa.deslocarMes(Financas.mesDe(agora), Financas.anoDe(agora), -1)
        val ini = Financas.inicioDoMes(mes, ano)
        val fim = Financas.fimDoMes(mes, ano)
        val receitas = Financas.kpisPeriodo(despesas, ini, fim).receitasRealizadas
        if (!(receitas > 0)) return null
        val doMes = despesas.filter { it.dataMs in ini..fim }
        val r = Analises.regra503020(receitas, doMes)
        val rec = Dinheiro.centavos(receitas)
        val vNec = Dinheiro.centavos(r.necessidades.valor)
        val vDes = Dinheiro.centavos(r.desejos.valor)
        // Poupança SEM o piso em zero de Analises.regra503020 (como no portal): resgates acima dos aportes aumentam o que faltou.
        var vPoup = 0L
        for (l in doMes) {
            val c = Dinheiro.centavos(l.valor)
            when {
                l.natureza == Natureza.APORTE_META -> vPoup += c
                l.natureza == Natureza.RESGATE_META -> vPoup -= c
                l.natureza == Natureza.NORMAL && l.tipo == TipoDespesa.DEBITO && Texto.normalizar(l.categoria) == "reserva" -> vPoup += c
            }
        }
        val excessoDesejos = maxOf(0L, vDes - Math.round(rec * 0.3))
        val faltaPoup = maxOf(0L, Math.round(rec * 0.2) - vPoup)
        val excessoNec = maxOf(0L, vNec - Math.round(rec * 0.5))
        // Mesma tolerância do portal (percentual <= alvo + 1e-9), em centavos inteiros.
        val necOk = vNec * 100 <= 50 * rec
        val desOk = vDes * 100 <= 30 * rec
        val poupOk = vPoup * 100 >= 20 * rec
        if (necOk && desOk && poupOk) return null
        fun pctDe(v: Long) = pct(v.toDouble() / rec)
        val valor = maxOf(excessoDesejos, faltaPoup)
        return Insight(
            id = "503020:$ano-$mes",
            tipo = Tipo.REGRA_503020,
            titulo = "Distribuição 50/30/20 do último mês",
            regra = "Necessidades até 50% da receita, desejos até 30% e poupança de pelo menos 20%. Estimativa = maior entre o excesso em desejos e o que faltou poupar.",
            calculo = listOf(
                "Receita do mês: ${brl(rec)}",
                "Necessidades: ${brl(vNec)} (${pctDe(vNec)}; alvo até 50%)" + if (excessoNec > 0) " — excesso ${brl(excessoNec)}" else "",
                "Desejos: ${brl(vDes)} (${pctDe(vDes)}; alvo até 30%)" + if (excessoDesejos > 0) " — excesso ${brl(excessoDesejos)}" else "",
                "Poupança: ${brl(vPoup)} (${pctDe(vPoup)}; alvo de 20% ou mais)" + if (faltaPoup > 0) " — faltou ${brl(faltaPoup)}" else ""
            ),
            valorMensal = valor
        )
    }

    /** Meses de agora até o prazo da meta: menor k ≥ 1 com somarMeses(hoje, k) ≥ alvo (mesma regra do portal); nulo se já passou. */
    fun mesesAteAlvo(alvo: Long, agora: Long): Int? {
        val hoje = Financas.inicioDoDia(agora)
        val a = Financas.inicioDoDia(alvo)
        if (a < hoje) return null
        var k = 0
        while (k < 1200 && Financas.somarMeses(hoje, k) < a) k++
        return maxOf(1, k)
    }

    fun metas(metas: List<Meta>, agora: Long, par: Parametros): List<Insight> {
        val out = ArrayList<Insight>()
        val taxa = par.cdiMensal * par.pctCdi
        for (m in metas) {
            val alvo = m.dataAlvo ?: continue
            if (m.valorGuardado >= m.valorObjetivo) continue
            val meses = mesesAteAlvo(alvo, agora) ?: continue
            val rn = Rendimentos.taxaLiquidaMensal(taxa, meses)
            val comRend = Rendimentos.aporteParaMeta(m.valorObjetivo, m.valorGuardado, meses, rn)
            val falta = Dinheiro.centavos(m.valorObjetivo) - Dinheiro.centavos(m.valorGuardado)
            val semRend = Math.ceil(falta.toDouble() / meses).toLong()
            out += Insight(
                id = "meta:${m.id}",
                tipo = Tipo.META,
                titulo = "Meta \"${m.nome}\": aporte mensal necessário",
                regra = "Aporte mensal para atingir o valor-alvo no prazo, considerando o rendimento líquido (CDI × % informado, IR regressivo) da simulação de rendimento.",
                calculo = listOf(
                    "Faltam ${brl(falta)} em $meses mês(es)",
                    "Taxa líquida equivalente: ${BigDecimal(rn * 100).setScale(4, RoundingMode.HALF_UP).toPlainString().replace('.', ',')}% a.m.",
                    "Aporte com rendimento: ${brl(comRend)}/mês · sem rendimento: ${brl(semRend)}/mês"
                ),
                valorMensal = comRend
            )
        }
        return out
    }

    /** Todos os insights, do maior para o menor valor estimado (empate: título em ordem pt-BR). */
    fun gerar(despesas: List<Despesa>, fixas: List<DespesaFixa>, metas: List<Meta>, agora: Long, par: Parametros): List<Insight> {
        val todos = ArrayList<Insight>(categorias(despesas, agora, par.inflacaoMensal))
        assinaturas(agora, despesas, fixas)?.let { todos += it }
        regra503020(despesas, agora)?.let { todos += it }
        todos += metas(metas, agora, par)
        val col = Collator.getInstance(Locale("pt", "BR"))
        return todos.sortedWith(compareByDescending<Insight> { it.valorMensal }.thenComparator { a, b -> col.compare(a.titulo, b.titulo) })
    }

    // ------------------------------------------------------------------ dispensados (só local, DataStore)

    const val MAX_DISPENSADOS = 300
    private const val NL = '\u000A'
    private const val TAB = '\u0009'

    /** Uma linha por id (sem TAB/quebra); mantém só os últimos [MAX_DISPENSADOS]. */
    fun serializarDispensados(ids: Collection<String>): String =
        ids.filter { it.isNotBlank() && !it.contains(NL) && !it.contains(TAB) }.takeLast(MAX_DISPENSADOS).joinToString(NL.toString())

    fun desserializarDispensados(texto: String?): Set<String> =
        if (texto.isNullOrBlank()) emptySet() else texto.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_DISPENSADOS).toCollection(LinkedHashSet())
}
