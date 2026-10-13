package com.meudinheiro.domain

import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Divida
import com.meudinheiro.data.Investimento
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa
import java.text.Collator
import java.util.Calendar
import java.util.Locale

/**
 * R61 — Relatório de apoio ao Imposto de Renda (IRPF) por ano-calendário. Funções puras, em CENTAVOS; espelham
 * `portal/src/lib/finance/imposto.ts` (mesmos cenários em `ImpostoRendaTest` e `imposto.test.ts`).
 *
 * NÃO é a declaração oficial nem aconselhamento tributário: é uma TRIAGEM feita só com o que foi lançado no app.
 * Tudo o que entra é REALIZADO (pago/recebido); o que ainda está pendente aparece à parte, como estimativa.
 */
object ImpostoRenda {

    // ------------------------------------------------------------ constantes (UM lugar, conferir a cada ano)

    /**
     * Limite anual de dedução com instrução, POR PESSOA (titular ou dependente), em centavos.
     * CONFERIR O LIMITE VIGENTE DO ANO-BASE na Receita Federal: o valor de referência (R$ 3.561,50) é o da tabela vigente
     * conhecida (anos-calendário 2015 a 2025) e pode mudar. O usuário pode alterá-lo na tela.
     */
    const val LIMITE_EDUCACAO_PADRAO_CENTAVOS = 356_150L

    /** Previdência complementar (PGBL): dedução limitada a 12% da renda tributável (base aproximada aqui). */
    const val LIMITE_PGBL_PERCENTUAL = 12L

    /** Contas com saldo abaixo disto normalmente não precisam constar em Bens e Direitos (conferir). */
    const val SALDO_MINIMO_CONTA_CENTAVOS = 14_000L

    const val AVISO_IR =
        "Isto NÃO é a declaração oficial nem aconselhamento tributário: é uma triagem feita só com os lançamentos do app. " +
            "Confira com os informes de rendimentos (empregador, bancos, corretoras), recibos e notas fiscais antes de declarar."

    // ------------------------------------------------------------ tipos

    enum class Classe(val rotulo: String) {
        TRIBUTAVEL("Tributável"),
        ISENTO("Isento / não tributável"),
        EXCLUSIVO("Tributação exclusiva/definitiva"),
        INVESTIMENTO("Investimentos (conferir informe do banco)"),
        FORA("Não é rendimento (fora)"),
        REVISAR("Sem classificação (revise)")
    }

    /** Classes que o usuário pode ESCOLHER (REVISAR é só o estado "ainda sem classificação"). */
    val CLASSES_ESCOLHA = listOf(Classe.TRIBUTAVEL, Classe.ISENTO, Classe.EXCLUSIVO, Classe.INVESTIMENTO, Classe.FORA)

    enum class Deducao(val rotulo: String) {
        SAUDE("Saúde"),
        EDUCACAO("Educação"),
        PENSAO("Pensão alimentícia"),
        PREVIDENCIA("Previdência privada (PGBL)"),
        DOACAO("Doações dedutíveis")
    }

    /** Configuração do usuário (em `configuracoes/geral` → `ir`; chaves por `Painel503020.chaveCategoria`). */
    data class Config(
        val rendimentos: Map<String, Classe> = emptyMap(),
        /** valor nulo = "NAO" (a categoria não entra) */
        val deducoes: Map<String, Deducao?> = emptyMap(),
        val limiteEducacaoCentavos: Long = LIMITE_EDUCACAO_PADRAO_CENTAVOS,
        /** titular + dependentes com despesa de educação: o limite vale por pessoa */
        val pessoasEducacao: Int = 1
    ) {
        fun toDoc(): Map<String, Any?> = mapOf(
            "rendimentos" to rendimentos.toSortedMap().mapValues { it.value.name },
            "deducoes" to deducoes.toSortedMap().mapValues { it.value?.name ?: "NAO" },
            "limiteEducacaoCentavos" to limiteEducacaoCentavos,
            "pessoasEducacao" to pessoasEducacao
        )

        companion object {
            /** Tolerante: aceita qualquer coisa (documento, backup antigo, lixo) e devolve uma configuração válida. */
            fun deDoc(doc: Map<*, *>?): Config {
                if (doc == null) return Config()
                val rend = HashMap<String, Classe>()
                (doc["rendimentos"] as? Map<*, *>)?.forEach { (k, v) ->
                    val c = CLASSES_ESCOLHA.firstOrNull { it.name == v }
                    val chave = (k as? String)?.let { Painel503020.chaveCategoria(it) }
                    if (chave != null && chave.isNotEmpty() && c != null) rend[chave] = c
                }
                val ded = HashMap<String, Deducao?>()
                (doc["deducoes"] as? Map<*, *>)?.forEach { (k, v) ->
                    val chave = (k as? String)?.let { Painel503020.chaveCategoria(it) }
                    if (chave == null || chave.isEmpty()) return@forEach
                    if (v == "NAO") ded[chave] = null
                    else Deducao.entries.firstOrNull { it.name == v }?.let { ded[chave] = it }
                }
                val lim = (doc["limiteEducacaoCentavos"] as? Number)?.toDouble()
                    ?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= 0 && it <= 100_000_000 }?.toLong()
                val pes = (doc["pessoasEducacao"] as? Number)?.toDouble()
                    ?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= 1 && it <= 20 }?.toInt()
                return Config(rend, ded, lim ?: LIMITE_EDUCACAO_PADRAO_CENTAVOS, pes ?: 1)
            }
        }
    }

    // ------------------------------------------------------------ classificação padrão por nome

    private fun norm(s: String) = Painel503020.chaveCategoria(s)
    private fun palavras(chave: String) = chave.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private val FORA = setOf("reembolso", "reembolsos", "estorno", "estornos", "devolucao", "emprestimo", "emprestimos", "transferencia", "transferencias", "resgate", "resgates", "aporte", "aportes", "acerto", "acertos", "rateio", "ajuste", "troco")
    private val INVEST = setOf("rendimento", "rendimentos", "juros", "dividendos", "dividendo", "proventos", "jcp", "cdb", "lci", "lca", "fii", "fiis", "acoes", "tesouro", "investimento", "investimentos", "poupanca")
    private val TRIBUT = setOf("salario", "salarios", "ordenado", "prolabore", "ferias", "freelance", "freela", "comissao", "bonus", "honorarios", "autonomo", "aluguel", "alugueis")
    private val ISENTO = setOf("heranca", "fgts")
    private val EXCLUSIVO = setOf("plr")
    private val TREZE = Regex("(^|[^0-9])13([^0-9]|$)")

    private fun porNome(t: String): Classe? {
        val c = norm(t)
        if (c.isEmpty()) return null
        val ps = palavras(c)
        fun tem(l: Set<String>) = ps.any { it in l }
        if (c.replace(Regex("[\\s-]"), "").contains("prolabore")) return Classe.TRIBUTAVEL
        if (c.contains("doacao recebida") || c.contains("doacoes recebidas")) return Classe.ISENTO
        if (tem(FORA)) return Classe.FORA
        if (tem(INVEST)) return Classe.INVESTIMENTO
        if (TREZE.containsMatchIn(c) || c.contains("decimo terceiro") || tem(EXCLUSIVO)) return Classe.EXCLUSIVO
        if (tem(TRIBUT)) return Classe.TRIBUTAVEL
        if (tem(ISENTO)) return Classe.ISENTO
        return null
    }

    /** Classe padrão de um rendimento pelo NOME (categoria; na falta, a descrição). REVISAR = não sei. */
    fun classificarRendimento(categoria: String, descricao: String = "", overrides: Map<String, Classe> = emptyMap()): Classe {
        val chave = norm(categoria)
        if (chave.isNotEmpty()) overrides[chave]?.let { if (it in CLASSES_ESCOLHA) return it }
        return porNome(categoria) ?: porNome(descricao) ?: Classe.REVISAR
    }

    private val SAUDE = setOf("saude", "medico", "medicos", "consulta", "consultas", "dentista", "odontologico", "psicologo", "psicologa", "psicologia", "terapia", "fisioterapia", "fonoaudiologia", "plano", "convenio", "exame", "exames", "laboratorio", "hospital", "clinica", "cirurgia", "nutricionista", "pediatra", "dermatologista", "oftalmologista", "ortopedista", "cardiologista")
    private val EDUCACAO = setOf("educacao", "escola", "faculdade", "universidade", "colegio", "creche", "graduacao", "mestrado", "doutorado", "ensino")
    private val PENSAO = setOf("pensao")
    private val PREVIDENCIA = setOf("previdencia", "pgbl", "funpresp")
    private val DOACAO = setOf("doacao", "doacoes")
    private val NAO_DEDUTIVEL = setOf("farmacia", "drogaria", "remedio", "remedios", "medicamento", "medicamentos", "oculos", "lente", "lentes", "suplemento", "suplementos", "vitamina", "vitaminas")

    /** Tipo padrão de dedução pelo NOME da categoria; `null` = não candidata. */
    fun classificarDeducao(categoria: String, overrides: Map<String, Deducao?> = emptyMap()): Deducao? {
        val chave = norm(categoria)
        if (chave.isEmpty()) return null
        if (overrides.containsKey(chave)) return overrides[chave]
        val ps = palavras(chave)
        fun tem(l: Set<String>) = ps.any { it in l }
        return when {
            tem(PENSAO) -> Deducao.PENSAO
            tem(PREVIDENCIA) -> Deducao.PREVIDENCIA
            tem(DOACAO) -> Deducao.DOACAO
            tem(EDUCACAO) -> Deducao.EDUCACAO
            tem(SAUDE) -> Deducao.SAUDE
            else -> null
        }
    }

    /** O lançamento de saúde é de um tipo que normalmente não é dedutível (remédio, óculos…)? */
    fun pareceNaoDedutivel(descricao: String): Boolean = palavras(norm(descricao)).any { it in NAO_DEDUTIVEL }

    // ------------------------------------------------------------ resultado

    data class Item(
        val id: Long,
        val data: Long,
        val descricao: String,
        val categoria: String,
        val centavos: Long,
        val comprovante: Boolean,
        /** conta (número) ou "Cartão" */
        val origem: String,
        val atencao: String? = null
    )

    data class Grupo(
        val categoria: String,
        val chave: String,
        val classe: Classe? = null,
        val tipo: Deducao? = null,
        val totalCentavos: Long,
        val itens: List<Item>
    )

    data class TotalClasse(val classe: Classe, val totalCentavos: Long, val quantidade: Int)

    data class TotalDeducao(
        val tipo: Deducao,
        /** soma dos lançamentos (sem os "não dedutíveis") */
        val totalCentavos: Long,
        /** teto aplicado (educação por pessoa; PGBL 12%); null = sem limite calculado */
        val limiteCentavos: Long?,
        val deduzivelCentavos: Long,
        val excessoCentavos: Long
    )

    data class Saldo(val nome: String, val detalhe: String, val centavos: Long)

    data class Rendimentos(
        val porClasse: List<TotalClasse>,
        val grupos: List<Grupo>,
        val totalRealizadoCentavos: Long,
        /** receitas NORMAL ainda não recebidas no ano (estimativa; NÃO entram nos totais) */
        val previstoCentavos: Long
    )

    data class Deducoes(
        val porTipo: List<TotalDeducao>,
        val grupos: List<Grupo>,
        val naoDedutiveis: List<Item>,
        val naoDedutiveisCentavos: Long,
        val totalDeduzivelCentavos: Long,
        /** candidatos ainda NÃO pagos no ano (estimativa; NÃO entram) */
        val pendentesCentavos: Long,
        val semComprovante: Int,
        val quantidade: Int,
        val limiteEducacaoTotalCentavos: Long,
        val limitePgblCentavos: Long,
        val baseTributavelCentavos: Long
    )

    data class Bens(
        val contas: List<Saldo>,
        val totalContasCentavos: Long,
        val investimentos: List<Saldo>,
        val totalInvestimentosCentavos: Long,
        val metas: List<Saldo>,
        val totalMetasCentavos: Long,
        val dividas: List<Saldo>,
        val totalDividasCentavos: Long,
        /** não nulo quando investimentos/metas/dívidas mostram o saldo ATUAL e o ano não é o anterior ao corrente */
        val avisoSaldoAtual: String?
    )

    data class Checagem(
        val receitasRealizadasCentavos: Long,
        val somaClassesCentavos: Long,
        val confere: Boolean,
        val deducoesConferem: Boolean
    )

    data class Relatorio(
        val ano: Int,
        val inicio: Long,
        val fim: Long,
        val rendimentos: Rendimentos,
        val deducoes: Deducoes,
        val bens: Bens,
        val checagem: Checagem,
        /** rendimento tributável − deduções candidatas (APENAS indicativo; não é a base de cálculo oficial) */
        val indicativoBaseCentavos: Long,
        val avisos: List<String>
    )

    // ------------------------------------------------------------ cálculo

    private fun cal(ano: Int, mes: Int, dia: Int, h: Int, mi: Int, s: Int, ms: Int): Long =
        Calendar.getInstance().apply { clear(); set(ano, mes, dia, h, mi, s); set(Calendar.MILLISECOND, ms) }.timeInMillis

    fun inicioDoAno(ano: Int) = cal(ano, 0, 1, 0, 0, 0, 0)
    fun fimDoAno(ano: Int) = cal(ano, 11, 31, 23, 59, 59, 999)

    private fun anoDe(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)

    private fun cartaoDe(d: Despesa): Int? = d.cartaoId?.takeIf { it != 0 }

    /** Saldo de uma conta (número) ao FIM do instante [fimMs]: mesma regra do saldo derivado do extrato (R3), só até a data. */
    fun saldoContaEm(despesas: List<Despesa>, numeroConta: String, fimMs: Long): Long {
        var total = 0L
        for (d in despesas) {
            if (d.conta != numeroConta || !d.pago || cartaoDe(d) != null || d.dataMs > fimMs) continue
            val c = Dinheiro.centavos(d.valor)
            total += if (d.tipo == TipoDespesa.CREDITO) c else -c
        }
        return total
    }

    private fun item(d: Despesa, centavos: Long, temComprovante: (Despesa) -> Boolean, atencao: String? = null) = Item(
        id = d.id, data = d.dataMs, descricao = d.descricao, categoria = d.categoria.trim().ifEmpty { "Sem categoria" },
        centavos = centavos, comprovante = temComprovante(d), origem = if (cartaoDe(d) != null) "Cartão" else d.conta, atencao = atencao
    )

    private class Acum(val categoria: String, val chave: String, val classe: Classe?, val tipo: Deducao?) {
        var total = 0L
        val itens = ArrayList<Item>()
    }

    fun relatorio(
        despesas: List<Despesa>,
        contas: List<ContaSaldo>,
        investimentos: List<Investimento>,
        metas: List<Meta>,
        dividas: List<Divida>,
        ano: Int,
        config: Config = Config(),
        agora: Long = System.currentTimeMillis(),
        /** "tem comprovante?" = ao menos um anexo (R60) */
        temComprovante: (Despesa) -> Boolean = { it.anexos.isNotEmpty() }
    ): Relatorio {
        val ini = inicioDoAno(ano)
        val fim = fimDoAno(ano)
        val pt = Collator.getInstance(Locale("pt", "BR"))

        val gruposRend = LinkedHashMap<String, Acum>()
        val totais = Classe.entries.associateWith { LongArray(2) } // [total, quantidade]
        var previsto = 0L
        var receitas = 0L
        val gruposDed = LinkedHashMap<String, Acum>()
        val naoDed = ArrayList<Item>()
        var pendentes = 0L

        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.dataMs < ini || d.dataMs > fim) continue
            val c = Dinheiro.centavos(d.valor)
            if (d.tipo == TipoDespesa.CREDITO) {
                if (cartaoDe(d) != null) continue // estorno de cartão: não é rendimento
                val classe = classificarRendimento(d.categoria, d.descricao, config.rendimentos)
                if (!d.pago) {
                    if (classe != Classe.FORA) previsto += c
                    continue
                }
                receitas += c
                val nome = d.categoria.trim().ifEmpty { "Sem categoria" }
                val g = gruposRend.getOrPut("${classe.name}|${norm(nome)}") { Acum(nome, norm(nome), classe, null) }
                g.total += c
                g.itens += item(d, c, temComprovante)
                totais.getValue(classe).let { it[0] += c; it[1] += 1 }
            } else {
                val tipo = classificarDeducao(d.categoria, config.deducoes) ?: continue
                val naoDedutivel = tipo == Deducao.SAUDE && pareceNaoDedutivel(d.descricao)
                if (!d.pago) {
                    if (!naoDedutivel) pendentes += c
                    continue
                }
                if (naoDedutivel) {
                    naoDed += item(d, c, temComprovante, "Medicamentos, óculos e similares normalmente não são dedutíveis.")
                    continue
                }
                val nome = d.categoria.trim().ifEmpty { "Sem categoria" }
                val g = gruposDed.getOrPut("${tipo.name}|${norm(nome)}") { Acum(nome, norm(nome), null, tipo) }
                g.total += c
                g.itens += item(d, c, temComprovante, if (tipo == Deducao.DOACAO) "Só vale para fundos e projetos incentivados (confira o recibo)." else null)
            }
        }

        fun ordenar(m: Map<String, Acum>): List<Grupo> = m.values
            .map { Grupo(it.categoria, it.chave, it.classe, it.tipo, it.total, it.itens.sortedWith(compareBy({ x -> x.data }, { x -> x.id }))) }
            .sortedWith(Comparator { a, b ->
                if (a.totalCentavos != b.totalCentavos) b.totalCentavos.compareTo(a.totalCentavos) else pt.compare(a.categoria, b.categoria)
            })

        val gRend = ordenar(gruposRend)
        val gDed = ordenar(gruposDed)
        val porClasse = Classe.entries.map { TotalClasse(it, totais.getValue(it)[0], totais.getValue(it)[1].toInt()) }
        val somaClasses = porClasse.sumOf { it.totalCentavos }
        val tributavel = totais.getValue(Classe.TRIBUTAVEL)[0]

        val limEduc = config.limiteEducacaoCentavos * config.pessoasEducacao
        val limPgbl = Math.floorDiv(tributavel * LIMITE_PGBL_PERCENTUAL, 100L)
        val porTipo = Deducao.entries.map { tipo ->
            val total = gDed.filter { it.tipo == tipo }.sumOf { it.totalCentavos }
            val limite: Long? = when (tipo) { Deducao.EDUCACAO -> limEduc; Deducao.PREVIDENCIA -> limPgbl; else -> null }
            val deduzivel = if (limite == null) total else minOf(total, limite)
            TotalDeducao(tipo, total, limite, deduzivel, total - deduzivel)
        }
        val totalDeduzivel = porTipo.sumOf { it.deduzivelCentavos }
        val todosItens = gDed.flatMap { it.itens }

        val saldosContas = contas
            .map { Saldo(it.banco, "conta ${it.conta}", saldoContaEm(despesas, it.conta, fim)) }
            .sortedWith(Comparator { a, b ->
                val r = pt.compare(a.nome, b.nome)
                if (r != 0) r else pt.compare(a.detalhe, b.detalhe)
            })
        val inv = investimentos.map { Saldo(it.nome, it.tipo, Dinheiro.centavos(it.valorAtual)) }
        val mts = metas.map { Saldo(it.nome, "meta", Dinheiro.centavos(it.valorGuardado)) }
        val dvs = dividas.filter { it.ativa }
            .map { Saldo(it.nome, it.credor?.trim()?.takeIf { c -> c.isNotEmpty() } ?: it.tipo, Dinheiro.centavos(it.saldoDevedor)) }
        val anoCorrente = anoDe(agora)
        val mostraAtual = inv.size + mts.size + dvs.size > 0
        val aviso = if (mostraAtual && ano != anoCorrente - 1)
            "Investimentos, metas e dívidas mostram o saldo ATUAL, não o de 31/12/$ano. Para declarar $ano, use o saldo de 31/12/$ano do informe do banco ou da corretora."
        else null

        val avisos = ArrayList<String>()
        if (ano > anoCorrente) avisos += "O ano $ano ainda não começou: os valores são só o que já foi lançado."
        else if (ano == anoCorrente) avisos += "O ano $ano ainda está em andamento: os totais são parciais."
        if (totais.getValue(Classe.REVISAR)[1] > 0) avisos += "Há receitas sem classificação: escolha a classe delas para o total de rendimentos fazer sentido."
        if (naoDed.isNotEmpty()) avisos += "Alguns itens de saúde (remédios, óculos) ficaram fora do total por normalmente não serem dedutíveis."
        if (porTipo.any { it.excessoCentavos > 0 }) avisos += "Parte das despesas passou do limite de dedução e não entra no total deduzível."
        avisos += "Estornos de cartão não são abatidos das despesas candidatas: confira os recibos."
        avisos += "Valores de referência (limite de educação, PGBL $LIMITE_PGBL_PERCENTUAL%) podem mudar: confirme na Receita Federal."

        return Relatorio(
            ano = ano, inicio = ini, fim = fim,
            rendimentos = Rendimentos(porClasse, gRend, somaClasses, previsto),
            deducoes = Deducoes(
                porTipo = porTipo, grupos = gDed,
                naoDedutiveis = naoDed.sortedWith(compareBy({ it.data }, { it.id })),
                naoDedutiveisCentavos = naoDed.sumOf { it.centavos },
                totalDeduzivelCentavos = totalDeduzivel, pendentesCentavos = pendentes,
                semComprovante = todosItens.count { !it.comprovante }, quantidade = todosItens.size,
                limiteEducacaoTotalCentavos = limEduc, limitePgblCentavos = limPgbl, baseTributavelCentavos = tributavel
            ),
            bens = Bens(
                saldosContas, saldosContas.sumOf { it.centavos }, inv, inv.sumOf { it.centavos },
                mts, mts.sumOf { it.centavos }, dvs, dvs.sumOf { it.centavos }, aviso
            ),
            checagem = Checagem(receitas, somaClasses, receitas == somaClasses, porTipo.sumOf { it.totalCentavos } == gDed.sumOf { it.totalCentavos }),
            indicativoBaseCentavos = tributavel - totalDeduzivel,
            avisos = avisos
        )
    }

    /** Anos para escolher na tela: o ano anterior ao corrente (o da declaração) e os que têm lançamentos. */
    fun anosComDados(despesas: List<Despesa>, agora: Long): List<Int> {
        val s = HashSet<Int>()
        s += anoDe(agora) - 1
        despesas.forEach { s += anoDe(it.dataMs) }
        return s.sortedDescending()
    }

    // ------------------------------------------------------------ exportação (mesmo documento do portal)

    data class Documento(
        val titulo: String,
        val avisos: List<String>,
        val totais: List<Pair<String, String>>,
        val colunas: List<String>,
        /** última coluna = valor em centavos (formatada no PDF/CSV) */
        val linhas: List<List<String>>,
        val centavosPorLinha: List<Long>,
        val nomeArquivo: String
    )

    private fun dataBr(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let {
        "%02d/%02d/%04d".format(it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.MONTH) + 1, it.get(Calendar.YEAR))
    }

    fun documento(r: Relatorio): Documento {
        val linhas = ArrayList<List<String>>()
        val valores = ArrayList<Long>()
        fun add(secao: String, data: String, desc: String, cat: String, info: String, comp: String, centavos: Long) {
            linhas += listOf(secao, data, desc, cat, info, comp)
            valores += centavos
        }
        for (g in r.rendimentos.grupos) for (i in g.itens) add("Rendimentos", dataBr(i.data), i.descricao, g.categoria, (g.classe ?: Classe.REVISAR).rotulo, "", i.centavos)
        for (g in r.deducoes.grupos) for (i in g.itens) add("Deduções candidatas", dataBr(i.data), i.descricao, g.categoria, g.tipo!!.rotulo, if (i.comprovante) "Sim" else "Não", i.centavos)
        for (i in r.deducoes.naoDedutiveis) add("Fora das deduções (provável não dedutível)", dataBr(i.data), i.descricao, i.categoria, "Saúde", if (i.comprovante) "Sim" else "Não", i.centavos)
        for (s in r.bens.contas) add("Saldo das contas em 31/12", "", s.nome, "", s.detalhe, "", s.centavos)
        for (s in r.bens.investimentos) add("Investimentos (saldo atual)", "", s.nome, "", s.detalhe, "", s.centavos)
        for (s in r.bens.metas) add("Metas (saldo atual)", "", s.nome, "", s.detalhe, "", s.centavos)
        for (s in r.bens.dividas) add("Dívidas (saldo devedor atual)", "", s.nome, "", s.detalhe, "", s.centavos)

        val totais = ArrayList<Pair<String, String>>()
        r.rendimentos.porClasse.filter { it.totalCentavos != 0L }.forEach { totais += it.classe.rotulo to FormatoCentavos.brl(it.totalCentavos) }
        r.deducoes.porTipo.filter { it.totalCentavos != 0L }.forEach { totais += "${it.tipo.rotulo} (dedutível)" to FormatoCentavos.brl(it.deduzivelCentavos) }
        totais += "Total de deduções candidatas" to FormatoCentavos.brl(r.deducoes.totalDeduzivelCentavos)
        totais += "Saldo das contas em 31/12" to FormatoCentavos.brl(r.bens.totalContasCentavos)
        val avisos = listOf(AVISO_IR) + listOfNotNull(r.bens.avisoSaldoAtual) + r.avisos
        return Documento(
            titulo = "Apoio ao Imposto de Renda - ano-calendário ${r.ano}",
            avisos = avisos, totais = totais,
            colunas = listOf("Seção", "Data", "Descrição", "Categoria", "Classe/Tipo", "Comprovante", "Valor"),
            linhas = linhas, centavosPorLinha = valores, nomeArquivo = "imposto-de-renda-${r.ano}"
        )
    }

    /** CSV com `;`, valores `1234,56` e linhas CRLF (o BOM UTF-8 é escrito por quem grava o arquivo). */
    fun csv(doc: Documento): String {
        fun cel(t: String) = if (t.any { it == '"' || it == ';' || it == '\n' || it == '\r' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        val sb = StringBuilder()
        sb.append(doc.colunas.joinToString(";") { cel(it) }).append("\r\n")
        doc.linhas.forEachIndexed { i, l ->
            val c = doc.centavosPorLinha[i]
            val valor = (if (c < 0) "-" else "") + (Math.abs(c) / 100) + "," + (Math.abs(c) % 100).toString().padStart(2, '0')
            sb.append((l.map { cel(it) } + valor).joinToString(";")).append("\r\n")
        }
        return sb.toString()
    }
}
