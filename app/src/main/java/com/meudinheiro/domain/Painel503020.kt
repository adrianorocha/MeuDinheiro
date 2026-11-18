package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.data.TipoDespesa

/**
 * R54 — Painel 50-30-20 por categoria e reserva de emergência (cobertura em meses das despesas essenciais).
 * Funções puras, em centavos; espelham `lib/finance/painel503020.ts` do portal (mesmo mapa, regras e textos).
 * A classificação é por NOME de categoria (sem acento, sem diferenciar maiúsculas) com sobrescrita local do
 * usuário. Não altera R25/R29 (`Analises.reservaEmergencia` / `Analises.regra503020`).
 */
object Painel503020 {

    enum class Grupo { NECESSIDADES, DESEJOS, POUPANCA }
    enum class StatusGrupo { OK, ACIMA, ABAIXO, SEM_RECEITA }
    enum class StatusReserva { CRITICO, ATENCAO, OK, SEM_DADOS }

    const val MESES_RESERVA_PADRAO = 6

    // ------------------------------------------------------------ mapa padrão (nomes normalizados)

    private val NECESSIDADES = listOf(
        "alimentacao", "supermercado", "transporte", "combustivel", "oficina", "saude", "educacao", "casa",
        "mercado", "padaria", "acougue", "hortifruti", "feira", "aluguel", "condominio", "moradia", "habitacao", "luz",
        "energia", "energia eletrica", "agua", "esgoto", "gas", "internet", "telefone", "celular", "contas", "contas basicas",
        "contas da casa", "reforma", "limpeza",
        "onibus", "taxi", "uber", "app de transporte", "estacionamento", "pedagio", "manutencao do carro", "manutencao carro",
        "bicicleta", "metro", "metro e trem", "trem",
        "farmacia", "remedios", "medicamentos", "dentista", "consulta", "consulta medica", "plano de saude", "convenio",
        "terapia", "oculos",
        "escola", "faculdade", "mensalidade", "livros", "cursos", "material escolar",
        "pet", "veterinario", "crianca", "familia", "filhos", "escritorio", "lavanderia", "correios", "cartorio", "advogado",
        "contador", "seguro", "seguros", "imposto", "impostos", "ipva", "iptu", "irpf", "taxas bancarias", "taxas", "tarifas",
        "multa", "multas", "imprevisto", "imprevistos"
    )

    private val DESEJOS = listOf(
        "lazer", "compras", "cinema", "academia", "jogos", "bebidas", "lanche",
        "restaurante", "restaurantes", "lanches", "delivery", "ifood", "bar", "cafe", "pizza", "sorvete", "fast food", "fastfood",
        "bebida", "moveis", "eletrodomesticos", "ferramentas", "jardim", "exercicio", "beleza", "cabelo", "salao", "estetica",
        "festa", "musica", "esporte", "esportes", "fotografia", "camera", "streaming", "shows", "shows e eventos", "parque",
        "parques e passeios", "roupas", "vestuario", "calcados", "eletronicos", "acessorios", "compras online", "brinquedos",
        "assinatura", "assinaturas", "nuvem", "nuvem e armazenamento", "aplicativos", "entretenimento", "hobbies", "favorito",
        "viagem", "viagens", "hotel", "aviao", "praia", "passeios", "mapa e passeios", "camping", "presente", "presentes",
        "doacao", "doacoes", "igreja", "flores", "aniversario"
    )

    private val POUPANCA = listOf(
        "reserva", "poupanca", "investimento", "investimentos", "aporte", "aportes", "emprestimo", "emprestimos",
        "financiamento", "divida", "dividas", "quitacao de divida", "juros"
    )

    private val MAPA: Map<String, Grupo> = buildMap {
        NECESSIDADES.forEach { put(it, Grupo.NECESSIDADES) }
        DESEJOS.forEach { put(it, Grupo.DESEJOS) }
        POUPANCA.forEach { put(it, Grupo.POUPANCA) }
    }

    /** Chave canônica de uma categoria (sem acento, minúsculas, `_` vira espaço). */
    fun chaveCategoria(nome: String): String = Texto.normalizar(nome.replace('_', ' '))

    /**
     * Grupo da categoria: 1) sobrescrita do usuário; 2) nome inteiro no mapa padrão; 3) palavras do nome, só se todas
     * as que casam apontarem para o MESMO grupo; senão `null` (não classificada).
     */
    fun classificar(nome: String, overrides: Map<String, Grupo> = emptyMap()): Grupo? {
        val chave = chaveCategoria(nome)
        if (chave.isEmpty()) return null
        overrides[chave]?.let { return it }
        MAPA[chave]?.let { return it }
        val grupos = HashSet<Grupo>()
        for (t in chave.split(Regex("[^a-z]+"))) {
            if (t.length < 3) continue
            MAPA[t]?.let { grupos.add(it) }
        }
        return if (grupos.size == 1) grupos.first() else null
    }

    // ------------------------------------------------------------ configuração local

    data class Metas(val necessidades: Int = 50, val desejos: Int = 30, val poupanca: Int = 20)

    data class Config(
        val metas: Metas = Metas(),
        /** Meta de cobertura da reserva, em meses (1 a 36). */
        val mesesReserva: Int = MESES_RESERVA_PADRAO,
        /** Chave da categoria ([chaveCategoria]) -> grupo escolhido pelo usuário. */
        val overrides: Map<String, Grupo> = emptyMap()
    )

    val CONFIG_PADRAO = Config()

    private fun entre(v: Int?, min: Int, max: Int, padrao: Int) = if (v != null && v in min..max) v else padrao

    /** Valida valores brutos (nunca lança): o que for inválido volta ao padrão. */
    fun normalizarConfig(nec: Int?, des: Int?, poup: Int?, meses: Int?, overrides: Map<String, Grupo>): Config = Config(
        Metas(entre(nec, 0, 100, 50), entre(des, 0, 100, 30), entre(poup, 0, 100, 20)),
        entre(meses, 1, 36, MESES_RESERVA_PADRAO),
        overrides.mapKeys { chaveCategoria(it.key) }.filterKeys { it.isNotEmpty() }
    )

    /** Texto simples (uma linha por item, campos separados por TAB) guardado no DataStore. */
    fun serializar(c: Config): String = buildString {
        append("metas\t${c.metas.necessidades}\t${c.metas.desejos}\t${c.metas.poupanca}\n")
        append("reserva\t${c.mesesReserva}\n")
        c.overrides.toSortedMap().forEach { (k, g) -> append("ov\t$k\t${g.name}\n") }
    }

    fun desserializar(texto: String?): Config {
        if (texto.isNullOrBlank()) return CONFIG_PADRAO
        var nec: Int? = null; var des: Int? = null; var poup: Int? = null; var meses: Int? = null
        val ov = HashMap<String, Grupo>()
        for (linha in texto.lines()) {
            val p = linha.split('\t')
            when (p.firstOrNull()) {
                "metas" -> if (p.size >= 4) { nec = p[1].toIntOrNull(); des = p[2].toIntOrNull(); poup = p[3].toIntOrNull() }
                "reserva" -> if (p.size >= 2) meses = p[1].toIntOrNull()
                "ov" -> if (p.size >= 3) Grupo.entries.firstOrNull { it.name == p[2] }?.let { ov[p[1]] = it }
            }
        }
        return normalizarConfig(nec, des, poup, meses, ov)
    }

    // ------------------------------------------------------------ painel do mês

    data class CategoriaValor(val nome: String, val centavos: Long)

    data class GrupoResultado(
        val grupo: Grupo,
        val totalCentavos: Long,
        /** % da receita realizada em décimos (250 = 25,0%); `null` sem receita. */
        val percentualDecimos: Long?,
        val metaPercentual: Int,
        val status: StatusGrupo,
        val categorias: List<CategoriaValor>
    )

    data class Painel(
        val mes: Int,
        val ano: Int,
        val receitaCentavos: Long,
        /** Despesas comuns do mês (igual a `Financas.kpisPeriodo().despesasTotal`), incluindo as não classificadas. */
        val despesasCentavos: Long,
        val necessidades: GrupoResultado,
        val desejos: GrupoResultado,
        val poupanca: GrupoResultado,
        /** Parte da poupança vinda de categorias de poupança/dívida (já dentro de [despesasCentavos]). */
        val poupancaCategoriasCentavos: Long,
        /** Aportes − resgates de metas (fora de [despesasCentavos]; pode ser negativo). */
        val poupancaMetasCentavos: Long,
        val naoClassificadoCentavos: Long,
        val naoClassificadas: List<CategoriaValor>,
        val dicas: List<String>
    )

    private fun ordenar(m: Map<String, CategoriaValor>): List<CategoriaValor> =
        m.entries.filter { it.value.centavos != 0L }
            .sortedWith(compareByDescending<Map.Entry<String, CategoriaValor>> { it.value.centavos }.thenBy { it.key })
            .map { it.value }

    private fun semCartao(d: Despesa) = d.cartaoId == null || d.cartaoId == 0

    /** Despesas comuns do período por categoria: débito soma, estorno de cartão abate (mesma regra de R5/R19). */
    private fun porCategoria(despesas: List<Despesa>, ini: Long, fim: Long): Map<String, CategoriaValor> {
        val out = LinkedHashMap<String, CategoriaValor>()
        for (d in despesas) {
            if (d.natureza != Natureza.NORMAL || d.dataMs < ini || d.dataMs > fim) continue
            val c = Dinheiro.centavos(d.valor)
            val contrib = when {
                d.tipo == TipoDespesa.DEBITO -> c
                !semCartao(d) -> -c
                else -> continue
            }
            val k = chaveCategoria(d.categoria)
            val nome = d.categoria.trim().ifEmpty { "Sem categoria" }
            val g = out[k]
            out[k] = if (g == null) CategoriaValor(nome, contrib) else g.copy(centavos = g.centavos + contrib)
        }
        return out
    }

    private fun receitaRealizada(despesas: List<Despesa>, ini: Long, fim: Long): Long =
        despesas.filter {
            it.natureza == Natureza.NORMAL && it.tipo == TipoDespesa.CREDITO && it.pago && semCartao(it) && it.dataMs in ini..fim
        }.sumOf { Dinheiro.centavos(it.valor) }

    fun painel(despesas: List<Despesa>, mes: Int, ano: Int, config: Config = CONFIG_PADRAO): Painel {
        val ini = Financas.inicioDoMes(mes, ano)
        val fim = Financas.fimDoMes(mes, ano)
        val receita = receitaRealizada(despesas, ini, fim)
        val cats = porCategoria(despesas, ini, fim)

        val por = Grupo.entries.associateWith { LinkedHashMap<String, CategoriaValor>() }
        val nao = LinkedHashMap<String, CategoriaValor>()
        var despesasTotal = 0L
        for ((k, v) in cats) {
            despesasTotal += v.centavos
            val g = classificar(v.nome, config.overrides)
            (if (g == null) nao else por.getValue(g))[k] = v
        }
        var metasNet = 0L
        for (d in despesas) {
            if (d.dataMs < ini || d.dataMs > fim) continue
            if (d.natureza == Natureza.APORTE_META) metasNet += Dinheiro.centavos(d.valor)
            else if (d.natureza == Natureza.RESGATE_META) metasNet -= Dinheiro.centavos(d.valor)
        }
        fun soma(m: Map<String, CategoriaValor>) = m.values.sumOf { it.centavos }
        val necC = soma(por.getValue(Grupo.NECESSIDADES))
        val desC = soma(por.getValue(Grupo.DESEJOS))
        val poupCatC = soma(por.getValue(Grupo.POUPANCA))
        val naoC = soma(nao)
        val poupC = maxOf(0L, poupCatC + metasNet)

        fun metaDe(g: Grupo) = when (g) {
            Grupo.NECESSIDADES -> config.metas.necessidades
            Grupo.DESEJOS -> config.metas.desejos
            Grupo.POUPANCA -> config.metas.poupanca
        }
        fun grupo(g: Grupo, total: Long, categorias: List<CategoriaValor>): GrupoResultado {
            val meta = metaDe(g)
            val status = when {
                receita <= 0 -> StatusGrupo.SEM_RECEITA
                g == Grupo.POUPANCA -> if (total * 100 >= meta * receita) StatusGrupo.OK else StatusGrupo.ABAIXO
                else -> if (total * 100 <= meta * receita) StatusGrupo.OK else StatusGrupo.ACIMA
            }
            return GrupoResultado(g, total, FormatoCentavos.decimosDe(total, receita), meta, status, categorias)
        }
        val catsPoup = ordenar(por.getValue(Grupo.POUPANCA)).toMutableList()
        if (metasNet != 0L) catsPoup.add(0, CategoriaValor("Metas (aportes - resgates)", metasNet))
        val p = Painel(
            mes, ano, receita, despesasTotal,
            grupo(Grupo.NECESSIDADES, necC, ordenar(por.getValue(Grupo.NECESSIDADES))),
            grupo(Grupo.DESEJOS, desC, ordenar(por.getValue(Grupo.DESEJOS))),
            grupo(Grupo.POUPANCA, poupC, catsPoup),
            poupCatC, metasNet, naoC, ordenar(nao), emptyList()
        )
        return p.copy(dicas = dicas(p))
    }

    private fun brl(c: Long) = FormatoCentavos.brl(c)

    private fun dicas(r: Painel): List<String> {
        val d = ArrayList<String>()
        if (r.receitaCentavos <= 0) {
            d.add("Sem receita realizada neste mês: registre (ou marque como recebidas) suas entradas para ver os percentuais.")
        } else {
            val g = r.necessidades
            if (g.status == StatusGrupo.ACIMA) {
                val excesso = g.totalCentavos - (g.metaPercentual * r.receitaCentavos) / 100
                val top = g.categorias.firstOrNull()
                d.add("Necessidades acima de ${g.metaPercentual}% da receita (${brl(excesso)} a mais).${if (top != null) " Maior peso: ${top.nome} (${brl(top.centavos)})." else ""} Renegociar contas fixas costuma ajudar.")
            }
            val ds = r.desejos
            if (ds.status == StatusGrupo.ACIMA) {
                val excesso = ds.totalCentavos - (ds.metaPercentual * r.receitaCentavos) / 100
                val top = ds.categorias.firstOrNull()
                d.add("Desejos acima de ${ds.metaPercentual}% da receita (${brl(excesso)} a mais).${if (top != null) " Comece revisando ${top.nome} (${brl(top.centavos)})." else ""}")
            }
            val p = r.poupanca
            if (p.status == StatusGrupo.ABAIXO) {
                val alvo = p.metaPercentual * r.receitaCentavos
                val falta = (alvo + 99) / 100 - p.totalCentavos
                d.add("Poupança abaixo de ${p.metaPercentual}% da receita: faltam ${brl(falta)} para chegar à meta. Automatize um aporte logo após receber.")
            }
            if (r.necessidades.status == StatusGrupo.OK && ds.status == StatusGrupo.OK && p.status == StatusGrupo.OK) {
                d.add("Seu mês está dentro da regra 50-30-20. Continue assim!")
            }
        }
        if (r.naoClassificadas.isNotEmpty()) {
            d.add("${r.naoClassificadas.size} categoria(s) sem classificação (${brl(r.naoClassificadoCentavos)}): ${r.naoClassificadas.joinToString(", ") { it.nome }}. Classifique-as para os percentuais ficarem completos.")
        }
        return d
    }

    // ------------------------------------------------------------ reserva de emergência (essenciais)

    data class Reserva(
        val saldoContasCentavos: Long,
        val metasReservaCentavos: Long,
        val liquidezCentavos: Long,
        /** Média mensal de NECESSIDADES nos 3 meses anteriores com lançamentos; `null` sem dados. */
        val mediaEssenciaisCentavos: Long?,
        val mesesCobertos: Double?,
        val mesesMeta: Int,
        val metaCentavos: Long?,
        val faltanteCentavos: Long?,
        val status: StatusReserva
    )

    private val REGEX_RESERVA = Regex("\\b(reserva|emergencia)\\b")

    /** Soma (centavos) das metas cujo nome indica reserva/emergência (o modelo de [Meta] não tem tipo). */
    fun metasDeReservaCentavos(metas: List<Meta>): Long =
        metas.filter { REGEX_RESERVA.containsMatchIn(Texto.normalizar(it.nome)) }.sumOf { Dinheiro.centavos(it.valorGuardado) }

    fun reservaEssenciais(
        despesas: List<Despesa>,
        saldoContasCentavos: Long,
        metasReservaCentavos: Long,
        hoje: Long,
        config: Config = CONFIG_PADRAO
    ): Reserva {
        val atual = Financas.FaturaRef(Financas.mesDe(hoje), Financas.anoDe(hoje))
        val totais = ArrayList<Long>()
        for (i in 1..3) {
            val ref = atual.deslocar(-i)
            val ini = Financas.inicioDoMes(ref.mes, ref.ano)
            val fim = Financas.fimDoMes(ref.mes, ref.ano)
            if (despesas.none { it.natureza == Natureza.NORMAL && it.dataMs in ini..fim }) continue
            var ess = 0L
            for (v in porCategoria(despesas, ini, fim).values) if (classificar(v.nome, config.overrides) == Grupo.NECESSIDADES) ess += v.centavos
            totais.add(maxOf(0L, ess))
        }
        val liquidez = saldoContasCentavos + metasReservaCentavos
        fun semDados(media: Long?) = Reserva(saldoContasCentavos, metasReservaCentavos, liquidez, media, null, config.mesesReserva, null, null, StatusReserva.SEM_DADOS)
        if (totais.isEmpty()) return semDados(null)
        val n = totais.size
        val media = (2 * totais.sum() + n) / (2 * n)
        if (media <= 0) return semDados(media)
        val meta = config.mesesReserva * media
        val status = if (liquidez * 2 < meta) StatusReserva.CRITICO else if (liquidez < meta) StatusReserva.ATENCAO else StatusReserva.OK
        return Reserva(
            saldoContasCentavos, metasReservaCentavos, liquidez, media, liquidez.toDouble() / media, config.mesesReserva,
            meta, maxOf(0L, meta - liquidez), status
        )
    }
}
