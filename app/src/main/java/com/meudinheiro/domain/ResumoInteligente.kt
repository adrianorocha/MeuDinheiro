package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import java.util.Calendar

/**
 * R53 — Resumo mensal/semanal inteligente. Função pura (sem campos novos): compara o período com o anterior e
 * devolve insights ordenados por relevância, em pt-BR e em centavos. Espelha `lib/finance/resumo.ts` do portal
 * (mesmos limiares, textos e números; ver docs/CONTRATO_DADOS.md).
 */
object ResumoInteligente {

    enum class TipoPeriodo { MES, SEMANA }
    enum class TipoInsight {
        DESPESAS_TOTAL, RECEITAS_TOTAL, POUPANCA, PROJECAO, CATEGORIA_ALTA, CATEGORIA_QUEDA, FORA_PADRAO,
        RECORRENCIA_NOVA, RECORRENCIA_AUMENTO, BOA_NOTICIA
    }
    enum class Tom { POSITIVO, NEGATIVO, NEUTRO, ALERTA }

    data class Insight(
        val id: String,
        val tipo: TipoInsight,
        val tom: Tom,
        /** Quanto maior, mais acima na lista. */
        val relevancia: Int,
        val titulo: String,
        val texto: String,
        val valorCentavos: Long?,
        /** Variação em décimos de ponto percentual (125 = 12,5%); `null` = sem base de comparação. */
        val variacaoDecimos: Long?
    )

    data class Comparativo(val atualCentavos: Long, val anteriorCentavos: Long, val deltaCentavos: Long, val variacaoDecimos: Long?)

    data class Periodo(
        val tipo: TipoPeriodo,
        val inicio: Long,
        val fim: Long,
        val rotulo: String,
        val emAndamento: Boolean,
        val diasDecorridos: Int,
        val diasTotal: Int,
        val anteriorInicio: Long,
        val anteriorFim: Long,
        /** Fim da janela anterior usada na comparação (mesmo ponto do período em andamento). */
        val anteriorFimComparavel: Long
    )

    data class Resumo(
        val periodo: Periodo,
        val despesas: Comparativo,
        val receitas: Comparativo,
        val resultadoCentavos: Long,
        val taxaPoupancaDecimos: Long?,
        val projecaoCentavos: Long?,
        val insights: List<Insight>,
        /** Sem nenhum lançamento comum no período nem no anterior. */
        val vazio: Boolean
    )

    // ----------------------------------------------------------- limiares (documentados no contrato R53)
    const val CATEGORIA_VARIACAO_MIN_PCT = 20L
    const val CATEGORIA_DELTA_MIN_CENTAVOS = 5000L
    const val FORA_PADRAO_MULTIPLO_X10 = 25L // 2,5x
    const val FORA_PADRAO_MIN_CENTAVOS = 10000L
    const val FORA_PADRAO_MIN_AMOSTRA = 3
    const val PROJECAO_MIN_DIAS = 3

    private val MESES = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    private class AgCat(var nome: String, var cents: Long, var ultimoMs: Long)
    private class Agregado(var desp: Long = 0, var rec: Long = 0, var qtd: Int = 0, val cats: MutableMap<String, AgCat> = HashMap())

    private fun Despesa.semCartaoId() = cartaoId == null || cartaoId == 0

    /** Mesma regra dos relatórios/KPIs (R5/R19): só NORMAL; estorno de cartão abate a despesa. */
    private fun agregar(despesas: List<Despesa>, ini: Long, fim: Long): Agregado {
        val a = Agregado()
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.dataMs < ini || d.dataMs > fim) continue
            a.qtd++
            val c = Dinheiro.centavos(d.valor)
            val contrib: Long
            if (d.tipo == TipoDespesa.CREDITO) {
                if (d.semCartaoId()) {
                    if (d.pago) a.rec += c
                    continue
                }
                contrib = -c
            } else contrib = c
            a.desp += contrib
            val k = Texto.normalizar(d.categoria)
            val nome = d.categoria.trim().ifEmpty { "Sem categoria" }
            val g = a.cats.getOrPut(k) { AgCat(nome, 0, Long.MIN_VALUE) }
            g.cents += contrib
            if (d.dataMs >= g.ultimoMs) {
                g.ultimoMs = d.dataMs
                g.nome = nome
            }
        }
        return a
    }

    private fun comparar(atual: Long, anterior: Long): Comparativo =
        Comparativo(atual, anterior, atual - anterior, FormatoCentavos.decimosDe(atual - anterior, anterior))

    private fun sinalPct(decimos: Long) = (if (decimos >= 0) "+" else "-") + FormatoCentavos.decimos(decimos) + "%"
    private fun sinalBrl(c: Long) = (if (c >= 0) "+" else "-") + FormatoCentavos.brl(Math.abs(c))
    private fun brl(c: Long) = FormatoCentavos.brl(c)

    private fun cal(ano: Int, mesIdx: Int, dia: Int, h: Int, mi: Int, s: Int, ms: Int): Long =
        Calendar.getInstance().apply { clear(); set(ano, mesIdx, dia, h, mi, s); set(Calendar.MILLISECOND, ms) }.timeInMillis

    // ----------------------------------------------------------- período

    /** Mês civil de [referencia], ou semana (segunda a domingo) que o contém; compara com o período anterior. */
    fun periodo(tipo: TipoPeriodo, referencia: Long, agora: Long): Periodo {
        val r = Calendar.getInstance().apply { timeInMillis = referencia }
        val inicio: Long
        val fim: Long
        val anteriorInicio: Long
        val anteriorFim: Long
        val diasTotal: Int
        val rotulo: String
        if (tipo == TipoPeriodo.MES) {
            val mes = r.get(Calendar.MONTH) + 1
            val ano = r.get(Calendar.YEAR)
            val ant = Financas.FaturaRef(mes, ano).anterior()
            inicio = Financas.inicioDoMes(mes, ano)
            fim = Financas.fimDoMes(mes, ano)
            anteriorInicio = Financas.inicioDoMes(ant.mes, ant.ano)
            anteriorFim = Financas.fimDoMes(ant.mes, ant.ano)
            diasTotal = Financas.diasNoMes(mes, ano)
            rotulo = "${MESES[mes - 1]} de $ano"
        } else {
            val dow = (r.get(Calendar.DAY_OF_WEEK) + 5) % 7 // segunda = 0
            val y = r.get(Calendar.YEAR)
            val m = r.get(Calendar.MONTH)
            val d = r.get(Calendar.DAY_OF_MONTH) - dow
            inicio = cal(y, m, d, 0, 0, 0, 0)
            fim = cal(y, m, d + 6, 23, 59, 59, 999)
            anteriorInicio = cal(y, m, d - 7, 0, 0, 0, 0)
            anteriorFim = cal(y, m, d - 1, 23, 59, 59, 999)
            diasTotal = 7
            fun f(ms: Long): String {
                val c = Calendar.getInstance().apply { timeInMillis = ms }
                return c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0') + "/" + (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            }
            rotulo = "Semana de ${f(inicio)} a ${f(fim)}"
        }
        val emAndamento = agora in inicio..fim
        val diasDecorridos = when {
            agora > fim -> diasTotal
            emAndamento -> {
                val a = Calendar.getInstance().apply { timeInMillis = agora }
                if (tipo == TipoPeriodo.MES) a.get(Calendar.DAY_OF_MONTH) else (a.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            }
            else -> 0
        }
        var anteriorFimComparavel = anteriorFim
        if (emAndamento) {
            val pi = Calendar.getInstance().apply { timeInMillis = anteriorInicio }
            val ate = cal(pi.get(Calendar.YEAR), pi.get(Calendar.MONTH), pi.get(Calendar.DAY_OF_MONTH) + diasDecorridos - 1, 23, 59, 59, 999)
            anteriorFimComparavel = minOf(ate, anteriorFim)
        }
        return Periodo(tipo, inicio, fim, rotulo, emAndamento, diasDecorridos, diasTotal, anteriorInicio, anteriorFim, anteriorFimComparavel)
    }

    // ----------------------------------------------------------- auxiliares

    private val PARCELA = Regex("\\(\\s*\\d+\\s*/\\s*\\d+\\s*\\)")
    private fun chaveRec(descricao: String): String =
        Texto.normalizar(descricao.replace(PARCELA, " ")).replace(Regex("[^a-z\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun ehParcela(d: Despesa) = d.grupoId?.startsWith("parc:") == true

    /** 2 × mediana (inteiro) de uma lista de centavos. */
    private fun mediana2(v: List<Long>): Long {
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] * 2 else s[m - 1] + s[m]
    }

    // ----------------------------------------------------------- principal

    fun gerar(
        despesas: List<Despesa>,
        fixas: List<DespesaFixa>,
        orcamentos: List<Orcamento>,
        tipo: TipoPeriodo,
        referencia: Long,
        agora: Long
    ): Resumo {
        val p = periodo(tipo, referencia, agora)
        val atual = agregar(despesas, p.inicio, p.fim)
        val ant = agregar(despesas, p.anteriorInicio, p.anteriorFimComparavel)
        val antInteiro = if (p.emAndamento) agregar(despesas, p.anteriorInicio, p.anteriorFim) else ant
        val desp = comparar(atual.desp, ant.desp)
        val rec = comparar(atual.rec, ant.rec)
        val resultado = atual.rec - atual.desp
        val taxa = if (atual.rec > 0) FormatoCentavos.decimosDe(resultado, atual.rec) else null
        val vazio = atual.qtd == 0 && antInteiro.qtd == 0
        val base = Resumo(p, desp, rec, resultado, taxa, null, emptyList(), vazio)
        if (vazio) return base

        val mesmoPonto = if (tipo == TipoPeriodo.MES) {
            if (p.emAndamento) "ao mesmo período do mês anterior (até o dia ${p.diasDecorridos})" else "ao mês anterior"
        } else {
            if (p.emAndamento) "ao mesmo ponto da semana anterior" else "à semana anterior"
        }
        val nomeAnt = if (tipo == TipoPeriodo.MES) "mês anterior" else "semana anterior"
        val ins = ArrayList<Insight>()
        fun add(tipoI: TipoInsight, tom: Tom, rel: Int, titulo: String, texto: String, valor: Long?, variacao: Long?, id: String = tipoI.name) {
            ins.add(Insight(id, tipoI, tom, rel, titulo, texto, valor, variacao))
        }

        // (a) totais de despesas e receitas
        if (atual.desp != 0L || ant.desp != 0L) {
            val c = desp
            val v = c.variacaoDecimos
            val texto = when {
                v == null -> "Você gastou ${brl(c.atualCentavos)}. Não houve despesas no $nomeAnt para comparar."
                c.deltaCentavos == 0L -> "Você gastou ${brl(c.atualCentavos)}, igual $mesmoPonto."
                else -> "Você gastou ${brl(c.atualCentavos)}: ${sinalBrl(c.deltaCentavos)} (${sinalPct(v)}) em relação $mesmoPonto."
            }
            val tom = if (c.deltaCentavos < 0) Tom.POSITIVO
            else if (c.deltaCentavos > 0 && (v == null || v >= 100)) Tom.ALERTA else Tom.NEUTRO
            add(TipoInsight.DESPESAS_TOTAL, tom, 90, "Despesas", texto, c.atualCentavos, v)
        }
        if (atual.rec != 0L || ant.rec != 0L) {
            val c = rec
            val v = c.variacaoDecimos
            val texto = when {
                v == null -> "Você recebeu ${brl(c.atualCentavos)}. Não houve receitas no $nomeAnt para comparar."
                c.deltaCentavos == 0L -> "Você recebeu ${brl(c.atualCentavos)}, igual $mesmoPonto."
                else -> "Você recebeu ${brl(c.atualCentavos)}: ${sinalBrl(c.deltaCentavos)} (${sinalPct(v)}) em relação $mesmoPonto."
            }
            val tom = if (c.deltaCentavos > 0) Tom.POSITIVO
            else if (c.deltaCentavos < 0 && v != null && v <= -100) Tom.ALERTA else Tom.NEUTRO
            add(TipoInsight.RECEITAS_TOTAL, tom, 85, "Receitas", texto, c.atualCentavos, v)
        }

        // (e) economia / taxa de poupança
        if (taxa != null) {
            if (resultado >= 0) {
                add(
                    TipoInsight.POUPANCA, if (taxa >= 200) Tom.POSITIVO else Tom.NEUTRO, 80, "Taxa de poupança",
                    "Sobraram ${brl(resultado)}, ${FormatoCentavos.decimos(taxa)}% da receita realizada${if (p.emAndamento) " até agora" else ""}.",
                    resultado, taxa
                )
            } else {
                add(
                    TipoInsight.POUPANCA, Tom.NEGATIVO, 80, "Taxa de poupança",
                    "Você gastou ${brl(-resultado)} a mais do que recebeu (receita realizada de ${brl(atual.rec)}).", resultado, taxa
                )
            }
        } else if (atual.desp > 0) {
            add(
                TipoInsight.POUPANCA, Tom.NEUTRO, 55, "Taxa de poupança",
                "Nenhuma receita realizada no período: não dá para calcular a taxa de poupança.", resultado, null
            )
        }

        // (f) projeção do fechamento no ritmo atual
        var projecao: Long? = null
        if (p.emAndamento && p.diasDecorridos >= PROJECAO_MIN_DIAS && atual.desp > 0) {
            val dec = p.diasDecorridos.toLong()
            val proj = (atual.desp * p.diasTotal * 2 + dec) / (2 * dec)
            projecao = proj
            val alvo = if (tipo == TipoPeriodo.MES) "o mês" else "a semana"
            if (antInteiro.desp > 0) {
                val delta = proj - antInteiro.desp
                val pct = FormatoCentavos.decimosDe(delta, antInteiro.desp)!!
                val rel = if (delta == 0L) "igual" else "${brl(Math.abs(delta))} ${if (delta > 0) "acima" else "abaixo"} (${sinalPct(pct)})"
                add(
                    TipoInsight.PROJECAO, if (delta < 0) Tom.POSITIVO else if (pct >= 100) Tom.ALERTA else Tom.NEUTRO, 75, "Projeção de fechamento",
                    "No ritmo atual, $alvo deve fechar em ${brl(proj)} em despesas: $rel do $nomeAnt inteiro (${brl(antInteiro.desp)}).", proj, pct
                )
            } else {
                add(
                    TipoInsight.PROJECAO, Tom.NEUTRO, 75, "Projeção de fechamento",
                    "No ritmo atual, $alvo deve fechar em ${brl(proj)} em despesas. Sem despesas no $nomeAnt para comparar.", proj, null
                )
            }
        }

        // (b) categorias com maior alta/queda (só relevantes: >= 20% e >= R$ 50; sem base anterior conta como relevante)
        class VarCat(val k: String, val nome: String, val a: Long, val pr: Long, val delta: Long)
        val altas = ArrayList<VarCat>()
        val quedas = ArrayList<VarCat>()
        for (k in (atual.cats.keys + ant.cats.keys).toSortedSet()) {
            val a = maxOf(0L, atual.cats[k]?.cents ?: 0L)
            val pr = maxOf(0L, ant.cats[k]?.cents ?: 0L)
            val delta = a - pr
            if (Math.abs(delta) < CATEGORIA_DELTA_MIN_CENTAVOS) continue
            if (pr > 0 && Math.abs(delta) * 100 < CATEGORIA_VARIACAO_MIN_PCT * pr) continue
            val nome = atual.cats[k]?.nome ?: ant.cats[k]?.nome ?: "Sem categoria"
            (if (delta > 0) altas else quedas).add(VarCat(k, nome, a, pr, delta))
        }
        val altasOrd = altas.sortedByDescending { it.delta }
        val quedasOrd = quedas.sortedBy { it.delta }
        for (c in altasOrd.take(3)) {
            val pct = FormatoCentavos.decimosDe(c.delta, c.pr)
            add(
                TipoInsight.CATEGORIA_ALTA, Tom.ALERTA, 70 + minOf(9L, c.delta / 10000).toInt(), "${c.nome} em alta",
                if (pct == null) "${c.nome}: ${brl(c.a)}, sem gasto equivalente no $nomeAnt (${sinalBrl(c.delta)})."
                else "${c.nome}: ${brl(c.a)}, ${sinalBrl(c.delta)} (${sinalPct(pct)}) em relação $mesmoPonto.",
                c.a, pct, "CATEGORIA_ALTA:${c.k}"
            )
        }
        for (c in quedasOrd.take(2)) {
            val pct = FormatoCentavos.decimosDe(c.delta, c.pr)
            add(
                TipoInsight.CATEGORIA_QUEDA, Tom.POSITIVO, 50 + minOf(9L, -c.delta / 10000).toInt(), "${c.nome} em queda",
                "${c.nome}: ${brl(c.a)}, ${sinalBrl(c.delta)} (${sinalPct(pct!!)}) em relação $mesmoPonto.", c.a, pct, "CATEGORIA_QUEDA:${c.k}"
            )
        }

        // (c) gasto fora do padrão: lançamento único > 2,5x a mediana da categoria (6 meses anteriores) e >= R$ 100
        val desde = Financas.somarMeses(p.inicio, -6)
        val hist = HashMap<String, MutableList<Long>>()
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.tipo != TipoDespesa.DEBITO || ehParcela(d) || d.dataMs < desde || d.dataMs >= p.inicio) continue
            hist.getOrPut(Texto.normalizar(d.categoria)) { ArrayList() }.add(Dinheiro.centavos(d.valor))
        }
        class Fora(val d: Despesa, val c: Long, val med2: Long)
        val fora = ArrayList<Fora>()
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.tipo != TipoDespesa.DEBITO || ehParcela(d) || d.dataMs < p.inicio || d.dataMs > p.fim) continue
            val c = Dinheiro.centavos(d.valor)
            if (c < FORA_PADRAO_MIN_CENTAVOS) continue
            val h = hist[Texto.normalizar(d.categoria)] ?: continue
            if (h.size < FORA_PADRAO_MIN_AMOSTRA) continue
            val med2 = mediana2(h) // c > 2,5 x mediana  <=>  c * 20 > 25 * (2 x mediana)
            if (med2 > 0 && c * 20 > FORA_PADRAO_MULTIPLO_X10 * med2) fora.add(Fora(d, c, med2))
        }
        for (f in fora.sortedWith(compareByDescending<Fora> { it.c }.thenBy { it.d.id }).take(3)) {
            val razao = Math.round((f.c * 20).toDouble() / f.med2.toDouble())
            val cat = f.d.categoria.trim().ifEmpty { "Sem categoria" }
            add(
                TipoInsight.FORA_PADRAO, Tom.ALERTA, 78, "Gasto fora do padrão",
                "${f.d.descricao.trim().ifEmpty { cat }} ($cat): ${brl(f.c)}, ${FormatoCentavos.decimos(razao)}x a mediana da categoria (${brl((f.med2 + 1) / 2)}).",
                f.c, null, "FORA_PADRAO:${f.d.id}"
            )
        }

        // (d) recorrências novas ou com aumento de valor (assinaturas no fim do período e no do anterior)
        if (agora >= p.inicio) {
            val tAgora = if (p.emAndamento) agora else p.fim
            val agoraSet = Analises.assinaturas(tAgora, despesas, fixas).filter { it.origem == Analises.OrigemAssinatura.DETECTADA }
            val antSet = Analises.assinaturas(p.anteriorFim, despesas, fixas)
            val antChaves = antSet.map { chaveRec(it.nome) }.toSet()
            val recs = ArrayList<Insight>()
            for (a in agoraSet) {
                val k = chaveRec(a.nome)
                val ult = a.ultimaData
                if (k in antChaves || ult == null || ult < p.inicio) continue
                val valor = Dinheiro.centavos(a.valorMedio)
                recs.add(
                    Insight(
                        "RECORRENCIA_NOVA:$k", TipoInsight.RECORRENCIA_NOVA, Tom.ALERTA, 60, "Nova recorrência",
                        "${a.nome} passou a se repetir todo mês: cerca de ${brl(valor)} por mês.", valor, null
                    )
                )
            }
            for (a in antSet.filter { it.origem == Analises.OrigemAssinatura.DETECTADA }) {
                val k = chaveRec(a.nome)
                val doPeriodo = despesas
                    .filter { it.natureza == Natureza.NORMAL && it.tipo == TipoDespesa.DEBITO && it.dataMs in p.inicio..tAgora && chaveRec(it.descricao) == k }
                    .sortedWith(compareByDescending<Despesa> { it.dataMs }.thenByDescending { it.id })
                if (doPeriodo.isEmpty()) continue
                val c = Dinheiro.centavos(doPeriodo[0].valor)
                val antes = Dinheiro.centavos(a.valorMedio)
                val diff = c - antes
                if (diff >= 100 && diff * 20 >= antes) {
                    val pct = FormatoCentavos.decimosDe(diff, antes)!!
                    recs.add(
                        Insight(
                            "RECORRENCIA_AUMENTO:$k", TipoInsight.RECORRENCIA_AUMENTO, Tom.ALERTA, 60, "Recorrência mais cara",
                            "${a.nome} subiu de ${brl(antes)} para ${brl(c)} (${sinalPct(pct)}).", c, pct
                        )
                    )
                }
            }
            ins.addAll(recs.take(4))
        }

        // (g) boa notícia (no máximo uma)
        val lim = orcamentos.filter { Dinheiro.centavos(it.valorLimite) > 0 }
        if (tipo == TipoPeriodo.MES && lim.isNotEmpty() &&
            lim.all { maxOf(0L, atual.cats[Texto.normalizar(it.categoria)]?.cents ?: 0L) <= Dinheiro.centavos(it.valorLimite) }
        ) {
            add(
                TipoInsight.BOA_NOTICIA, Tom.POSITIVO, 45, "Boa notícia",
                "Orçamento respeitado em ${if (lim.size == 1) "1 categoria" else "todas as ${lim.size} categorias"}${if (p.emAndamento) " até agora" else ""}.",
                null, null
            )
        } else if (taxa != null && taxa >= 200) {
            add(
                TipoInsight.BOA_NOTICIA, Tom.POSITIVO, 45, "Boa notícia",
                "Você está poupando ${FormatoCentavos.decimos(taxa)}% da receita, acima dos 20% recomendados.", null, taxa
            )
        }

        // ordem estável: relevância decrescente, depois ordem de geração
        return base.copy(projecaoCentavos = projecao, insights = ins.sortedByDescending { it.relevancia })
    }

    /** Texto pronto para copiar/compartilhar (WhatsApp, e-mail...). */
    fun texto(r: Resumo): String {
        val linhas = ArrayList<String>()
        linhas.add("Resumo de ${r.periodo.rotulo} - MeuDinheiro")
        linhas.add("")
        if (r.vazio || r.insights.isEmpty()) linhas.add("Sem lançamentos suficientes para gerar o resumo.")
        for (i in r.insights) linhas.add("- ${i.titulo}: ${i.texto}")
        return linhas.joinToString("\n")
    }
}
