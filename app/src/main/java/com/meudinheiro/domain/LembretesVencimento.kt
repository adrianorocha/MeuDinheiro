package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.util.TimeZone

/**
 * R91 — lembretes de vencimento com antecedência configurável. TUDO puro (sem Android): quais itens avisar hoje, dada a
 * data, as antecedências marcadas e o que já foi avisado, e o texto da notificação agrupada.
 *
 * Datas por DIA CIVIL no fuso informado (padrão: o do aparelho, como o resto do app): a virada de mês/ano não precisa de
 * tratamento especial porque tudo é comparado em número de dias.
 */
object LembretesVencimento {

    /** Antecedências que o usuário pode marcar (dias antes do vencimento; 0 = no dia). */
    val ANTECEDENCIAS: List<Int> = listOf(0, 1, 3, 7)

    /** Marcadas ao ligar o recurso pela primeira vez. */
    val PADRAO_MARCADAS: Set<Int> = setOf(0, 1, 3)

    /** Janela "atrasada" (no registro de avisados). */
    const val JANELA_ATRASADA = -1

    /** Chaves de avisados com mais de tantos dias após a referência são expurgadas. */
    const val RETENCAO_DIAS = 45

    const val MAX_LINHAS = 6

    enum class Tipo { CONTA, FATURA }

    /**
     * Algo que vence: uma conta a pagar (despesa não paga fora do cartão) ou a fatura em aberto de um cartão.
     * [chave] identifica o item de forma estável ("c:<id>" / "f:<cartão>:<mês>-<ano>").
     */
    data class Item(
        val chave: String,
        val tipo: Tipo,
        val descricao: String,
        val centavos: Long,
        val vencimentoMs: Long,
        /** Id do lançamento (só conta); alimenta o botão "Pagar agora". */
        val lancamentoId: Long? = null
    )

    data class Config(val antecedencias: Set<Int>, val avisarAtrasadas: Boolean = true)

    /** [janela]: antecedência marcada que disparou (0, 1, 3, 7) ou [JANELA_ATRASADA]. */
    data class Lembrete(val item: Item, val diasAte: Int, val janela: Int, val chaveAviso: String)

    // ------------------------------------------------------------------ itens

    /**
     * Itens que vencem: contas a pagar (saída NORMAL não paga e fora do cartão; data = vencimento) e, por cartão
     * principal (com os virtuais do grupo), cada fatura em aberto com o valor pendente e o vencimento do ciclo
     * (dia de vencimento do cartão, `Financas.dataVencimento`).
     */
    fun itens(despesas: List<Despesa>, cartoes: List<Cartao>): List<Item> {
        val contas = despesas
            .filter { !it.pago && it.tipo == TipoDespesa.DEBITO && (it.cartaoId == null || it.cartaoId == 0) && it.natureza == Natureza.NORMAL }
            .map { Item("c:${it.id}", Tipo.CONTA, it.descricao, Dinheiro.centavos(it.valor), it.dataMs, it.id) }
        val faturas = cartoes.filter { !it.ehVirtual }.flatMap { principal ->
            Financas.faturasEmAberto(principal, despesas, Financas.idsDoGrupo(principal, cartoes)).mapNotNull { f ->
                val c = Dinheiro.centavos(f.pendente)
                if (c <= 0) null else Item("f:${principal.id}:${f.ref.mes}-${f.ref.ano}", Tipo.FATURA, "Fatura ${principal.nome}", c, f.vencimento)
            }
        }
        return contas + faturas
    }

    // ------------------------------------------------------------------ configuração (texto salvo)

    /** "0,1,3" -> {0,1,3}; ignora lixo e valores fora de [ANTECEDENCIAS]. Nulo/vazio = vazio. */
    fun lerMarcadas(txt: String?): Set<Int> =
        txt.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in ANTECEDENCIAS }.toSet()

    fun gravarMarcadas(marcadas: Set<Int>): String = ANTECEDENCIAS.filter { it in marcadas }.joinToString(",")

    fun rotuloAntecedencia(dias: Int): String = when (dias) {
        0 -> "No dia do vencimento"
        1 -> "1 dia antes"
        else -> "$dias dias antes"
    }

    // ------------------------------------------------------------------ datas

    private const val DIA_MS = 86_400_000L

    /** Número do dia civil local (dias desde 1970-01-01 no fuso [tz]). */
    fun diaCivil(ms: Long, tz: TimeZone = TimeZone.getDefault()): Long = Math.floorDiv(ms + tz.getOffset(ms), DIA_MS)

    /** Dias entre o dia de [agoraMs] e o dia do vencimento (negativo = atrasado). */
    fun diasAte(vencimentoMs: Long, agoraMs: Long, tz: TimeZone = TimeZone.getDefault()): Int =
        (diaCivil(vencimentoMs, tz) - diaCivil(agoraMs, tz)).toInt()

    // ------------------------------------------------------------------ seleção

    /**
     * Chave de idempotência: item + antecedência + data de referência (dia do vencimento; para atrasada, o dia de hoje —
     * no máximo um aviso de atraso por item por dia).
     */
    fun chaveAviso(item: Item, janela: Int, diaReferencia: Long): String = "${item.chave}|$janela|$diaReferencia"

    /**
     * O que avisar AGORA. Para cada item a vencer escolhe a MENOR antecedência marcada que ainda cobre os dias que
     * faltam (faltam 2 dias e há 3 e 1 marcadas -> janela 3). Assim cada janela dispara uma vez só e, se o aparelho
     * estava desligado no dia exato, o aviso sai na primeira execução seguinte dentro da janela, sem acumular as
     * antecedências já passadas. Itens em atraso entram (uma vez por dia) se [Config.avisarAtrasadas]. Itens cuja chave
     * já está em [jaAvisados] não voltam. Ordem: vencimento, depois descrição.
     */
    fun selecionar(
        itens: List<Item>,
        agoraMs: Long,
        config: Config,
        jaAvisados: Set<String>,
        tz: TimeZone = TimeZone.getDefault()
    ): List<Lembrete> {
        val hoje = diaCivil(agoraMs, tz)
        val marcadas = config.antecedencias.filter { it in ANTECEDENCIAS }.sorted()
        val saida = ArrayList<Lembrete>()
        for (item in itens) {
            if (item.centavos <= 0) continue
            val diaVenc = diaCivil(item.vencimentoMs, tz)
            val dias = (diaVenc - hoje).toInt()
            val janela: Int
            val referencia: Long
            if (dias < 0) {
                if (!config.avisarAtrasadas) continue
                janela = JANELA_ATRASADA; referencia = hoje
            } else {
                janela = marcadas.firstOrNull { it >= dias } ?: continue
                referencia = diaVenc
            }
            val chave = chaveAviso(item, janela, referencia)
            if (chave in jaAvisados) continue
            saida += Lembrete(item, dias, janela, chave)
        }
        return saida.sortedWith(compareBy({ it.item.vencimentoMs }, { it.item.descricao }))
    }

    /** Remove avisados antigos (referência anterior a hoje - [RETENCAO_DIAS]) e chaves malformadas. */
    fun expurgar(avisados: Set<String>, agoraMs: Long, tz: TimeZone = TimeZone.getDefault()): Set<String> {
        val limite = diaCivil(agoraMs, tz) - RETENCAO_DIAS
        return avisados.filter { (it.substringAfterLast('|', "").toLongOrNull() ?: Long.MIN_VALUE) >= limite }.toSet()
    }

    // ------------------------------------------------------------------ texto

    data class Notificacao(
        val titulo: String,
        val resumo: String,
        val linhas: List<String>,
        val atrasado: Boolean,
        /** Conta mais urgente com lançamento (botão "Pagar agora"); nula se só há faturas. */
        val contaParaPagar: Item?
    )

    private fun valor(centavos: Long, ocultar: Boolean) = if (ocultar) "R$ ••••" else AvisosVencimento.moeda(Dinheiro.reais(centavos))

    private fun rotuloItem(i: Item) = i.descricao

    /** UMA notificação para todos os lembretes da execução. Vazio = null. [ocultarValores]: modo privado do app. */
    fun montar(lembretes: List<Lembrete>, ocultarValores: Boolean = false): Notificacao? {
        if (lembretes.isEmpty()) return null
        val atrasados = lembretes.count { it.diasAte < 0 }
        val total = lembretes.sumOf { it.item.centavos }
        val primeiro = lembretes.first()

        val titulo = when {
            lembretes.size == 1 -> "${rotuloItem(primeiro.item)} ${verbo(primeiro)}"
            atrasados == lembretes.size -> "$atrasados vencimentos atrasados"
            atrasados > 0 -> "${lembretes.size} vencimentos ($atrasados atrasado(s))"
            else -> "${lembretes.size} vencimentos chegando"
        }
        val resumo = if (lembretes.size == 1) valor(primeiro.item.centavos, ocultarValores)
        else "${valor(total, ocultarValores)} no total · primeiro: ${rotuloItem(primeiro.item)}, ${quando(primeiro)}"

        val linhas = lembretes.take(MAX_LINHAS).map {
            quando(it).replaceFirstChar { c -> c.uppercase() } + " · " + rotuloItem(it.item) + " · " + valor(it.item.centavos, ocultarValores)
        } + (if (lembretes.size > MAX_LINHAS) listOf("+ ${lembretes.size - MAX_LINHAS} outros no app") else emptyList())

        return Notificacao(titulo, resumo, linhas, atrasados > 0, lembretes.map { it.item }.firstOrNull { it.tipo == Tipo.CONTA && it.lancamentoId != null })
    }

    /** "hoje", "amanhã", "em 3 dias", "venceu ontem", "venceu há 5 dias" (a partir dos dias civis já calculados). */
    fun quandoDias(dias: Int): String = when {
        dias == 0 -> "hoje"
        dias == 1 -> "amanhã"
        dias > 1 -> "em $dias dias"
        dias == -1 -> "venceu ontem"
        else -> "venceu há ${-dias} dias"
    }

    private fun quando(l: Lembrete): String = quandoDias(l.diasAte)

    private fun verbo(l: Lembrete): String =
        if (l.diasAte < 0) quando(l) else "vence ${quando(l)}"
}
