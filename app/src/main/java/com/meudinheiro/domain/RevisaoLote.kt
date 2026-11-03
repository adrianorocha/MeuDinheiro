package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada

/**
 * R89 — revisão em lote das sugestões da captura (R64). Puro: valida e monta os lançamentos de várias sugestões de uma
 * vez. NADA aqui grava; a tela só chama isto depois de o usuário marcar as sugestões e confirmar (com biometria).
 */
object RevisaoLote {

    /** Destino escolhido na revisão: conta (número) OU cartão (id). */
    data class Destino(val conta: String? = null, val cartaoId: Int? = null) {
        val definido: Boolean get() = cartaoId != null || !conta.isNullOrBlank()
    }

    /** O que o usuário deixou na linha da sugestão (descrição e categoria editáveis, destino). */
    data class Edicao(val descricao: String, val categoria: String?, val destino: Destino)

    const val CATEGORIA_RECEITA = "Receita"

    /** Mensagem de erro (exibível) ou null se a sugestão, com a edição feita, pode virar lançamento. */
    fun validar(g: SugestaoGuardada, e: Edicao): String? = when {
        g.valorCentavos <= 0 -> "Valor inválido."
        !e.destino.definido -> "Escolha a conta ou o cartão."
        g.entrada && e.destino.cartaoId != null -> "Receita entra em uma conta, não em um cartão."
        !g.entrada && e.categoria.isNullOrBlank() -> "Escolha uma categoria."
        else -> null
    }

    /** Descrição padrão da linha (a mesma do formulário individual). */
    fun descricaoInicial(g: SugestaoGuardada): String = NotificacaoBancaria.tituloLancamento(g)

    /**
     * Lançamento da sugestão: a data é a do horário da notificação (não a de quando o usuário confirma); em conta nasce
     * pago, no cartão fica em aberto (regra do [LancamentoRapido.montar]).
     */
    fun montar(g: SugestaoGuardada, e: Edicao, pic: String): Despesa =
        LancamentoRapido.montar(
            valor = Dinheiro.reais(g.valorCentavos),
            descricao = e.descricao.ifBlank { descricaoInicial(g) },
            categoria = if (g.entrada) CATEGORIA_RECEITA else e.categoria.orEmpty(),
            pic = pic,
            tipo = if (g.entrada) TipoDespesa.CREDITO else TipoDespesa.DEBITO,
            conta = if (e.destino.cartaoId != null) "" else e.destino.conta.orEmpty(),
            cartaoId = e.destino.cartaoId,
            agora = g.horarioMs,
            tags = if (g.comoSugestao().ehPix) Pix.comTag(emptyList()) else emptyList() // R94: Pix vira lançamento com a tag PIX
        )

    /** Modalidade só importa em cartão múltiplo: compra no débito da notificação = débito; senão crédito. */
    fun modalidade(g: SugestaoGuardada): Financas.Modalidade =
        if (g.tipo == NotificacaoBancaria.Tipo.COMPRA_DEBITO) Financas.Modalidade.DEBITO else Financas.Modalidade.CREDITO

    /** Estabelecimento (texto extraído) e categoria a memorizar ao confirmar; null = nada a aprender (receita ou sem categoria). */
    fun paraAprender(g: SugestaoGuardada, e: Edicao): Pair<String, String>? =
        if (g.entrada || e.categoria.isNullOrBlank()) null else g.descricao to e.categoria.trim()

    /**
     * Destino padrão da linha: o resolvido na captura se ainda existir; senão a última origem usada; senão a primeira
     * conta. Chaves no formato "c:<conta>" / "k:<cartão>". Receita nunca vai para cartão.
     */
    fun destinoPadrao(g: SugestaoGuardada, contas: Collection<String>, cartoes: Collection<Int>, ultimaChave: String?): Destino {
        if (!g.entrada && g.cartaoId != null && g.cartaoId in cartoes) return Destino(cartaoId = g.cartaoId)
        if (g.conta != null && g.conta in contas) return Destino(conta = g.conta)
        destinoDeChave(ultimaChave)?.takeIf { d -> d.cartaoId?.let { it in cartoes && !g.entrada } ?: (d.conta in contas) }?.let { return it }
        return Destino(conta = contas.firstOrNull())
    }

    fun chaveDoDestino(d: Destino): String? = d.cartaoId?.let { "k:$it" } ?: d.conta?.takeIf { it.isNotBlank() }?.let { "c:$it" }

    fun destinoDeChave(chave: String?): Destino? = when {
        chave == null -> null
        chave.startsWith("k:") -> chave.drop(2).toIntOrNull()?.let { Destino(cartaoId = it) }
        chave.startsWith("c:") -> chave.drop(2).takeIf { it.isNotBlank() }?.let { Destino(conta = it) }
        else -> null
    }

    /** Texto da barra de ações. */
    fun rotuloConfirmar(n: Int) = if (n <= 0) "Confirmar selecionadas" else "Confirmar selecionadas ($n)"
    fun rotuloDescartar(n: Int) = if (n <= 0) "Descartar selecionadas" else "Descartar selecionadas ($n)"

    /** Mensagem final do lote. */
    fun mensagemResultado(criados: Int, erros: Int): String = when {
        criados > 0 && erros == 0 -> if (criados == 1) "1 lançamento criado." else "$criados lançamentos criados."
        criados > 0 -> "$criados criado(s); $erros com erro (veja nas linhas)."
        erros > 0 -> "Nenhum lançamento criado: $erros com erro."
        else -> "Nada selecionado."
    }

    /** Resumo (≥ 2 pendentes) usado na notificação-resumo; abaixo disso não há resumo. */
    fun precisaResumo(pendentes: Int) = pendentes >= 2
    fun tituloResumo(pendentes: Int) = "$pendentes sugestões de lançamento"
}
