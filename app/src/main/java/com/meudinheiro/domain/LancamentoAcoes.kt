package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa

/**
 * Quais ações cada lançamento oferece no app (paridade com o portal: EDITAVEL / ALTERNAVEL, R8, R11, R12, R23, R40, R42).
 * Regra pura, sem Android: usada pelo menu de ações das listas e testada em JVM.
 */
object LancamentoAcoes {

    enum class Acao { PAGO_PENDENTE, ANTECIPAR, DUPLICAR, REPETIR, EDITAR, EXCLUIR }

    /** Natureza não editável (gerada por outra operação). SALDO_INICIAL e AJUSTE são editáveis. */
    val BLOQUEADAS_EDICAO = setOf(
        Natureza.PAGAMENTO_FATURA, Natureza.TRANSFERENCIA, Natureza.APORTE_META, Natureza.RESGATE_META
    )

    const val MSG_NAO_EDITAVEL =
        "Este lançamento é gerado automaticamente e não pode ser editado. Exclua-o (se permitido) e refaça a operação."
    const val MSG_PAGAMENTO_FATURA =
        "O pagamento de fatura não pode ser excluído. Para reabrir a fatura, marque as compras como não pagas."
    const val MSG_META = "Aportes e resgates de metas são gerenciados na tela de Metas."
    const val MSG_NAO_ALTERNAVEL = "Este lançamento é sempre efetivado e não pode ser alternado."
    const val MSG_CARTAO_FATURA = "Compras do cartão são quitadas pelo pagamento da fatura (aba Cartões)."

    /** Mensagem quando a exclusão é proibida pela natureza; `null` se pode excluir. */
    fun motivoExclusaoBloqueada(natureza: String): String? = when (natureza) {
        Natureza.PAGAMENTO_FATURA -> MSG_PAGAMENTO_FATURA
        Natureza.APORTE_META, Natureza.RESGATE_META -> MSG_META
        else -> null
    }

    /** Parcelas: `parc:<uuid>` (portal e app atual) ou UUID puro (parcelas antigas do app). Só vale para NORMAL. */
    fun ehGrupoParcelas(natureza: String, grupoId: String?): Boolean =
        natureza == Natureza.NORMAL && grupoId != null && (grupoId.startsWith("parc:") || !grupoId.contains(':'))

    fun acoes(natureza: String, tipo: TipoDespesa, pago: Boolean, cartaoId: Int?, grupoId: String? = null): List<Acao> {
        val noCartao = cartaoId != null && cartaoId != 0
        return buildList {
            if (!noCartao && (natureza == Natureza.NORMAL || natureza == Natureza.AJUSTE)) add(Acao.PAGO_PENDENTE)
            if (natureza == Natureza.NORMAL) {
                if (!noCartao && tipo == TipoDespesa.DEBITO && !pago) add(Acao.ANTECIPAR)
                add(Acao.DUPLICAR)
                add(Acao.REPETIR)
            }
            if (natureza !in BLOQUEADAS_EDICAO) add(Acao.EDITAR)
            add(Acao.EXCLUIR)
        }
    }

    fun acoes(d: Despesa): List<Acao> = acoes(d.natureza, d.tipo, d.pago, d.cartaoId, d.grupoId)
    fun acoes(d: DespesasDomain): List<Acao> = acoes(d.natureza, d.tipo, d.pago, d.cartaoId, d.grupoId)

    /** Texto de aviso do diálogo de exclusão (o que acontece com saldo/limite). */
    fun avisoExclusao(natureza: String, tipo: TipoDespesa, pago: Boolean, cartaoId: Int?): String = when {
        natureza == Natureza.TRANSFERENCIA -> "Esta transferência será excluída junto com o lançamento par na outra conta."
        cartaoId != null && cartaoId != 0 -> "O limite do cartão será recalculado sem esta compra."
        tipo == TipoDespesa.DEBITO -> if (pago) "O valor será restituído ao saldo." else "O saldo não será afetado (não estava pago)."
        else -> if (pago) "O valor será deduzido do saldo." else "O saldo não será afetado."
    }

    // ---------------------------------------------------------------- R40: entrada do diálogo de antecipação

    enum class ModoAntecipacao { QUITAR, PARCIAL }

    /** Como o desconto é informado: valor cobrado, valor do desconto ou percentual. */
    enum class DescontoModo { COBRADO, VALOR, PERCENTUAL }

    data class PagoDesconto(val pago: Double, val desconto: Double)

    /**
     * Converte o que o usuário digitou em (valor pago, desconto), como o portal. `null` = entrada inválida.
     * QUITAR sem texto = paga o devido, sem desconto. PARCIAL: [parcialTxt] é o valor adiantado.
     */
    fun calcularAntecipacao(modo: ModoAntecipacao, descModo: DescontoModo, devido: Double, parcialTxt: String, descTxt: String): PagoDesconto? {
        if (modo == ModoAntecipacao.PARCIAL) {
            val v = Financas.parseSaldoInformado(parcialTxt) ?: return null
            return if (v > 0) PagoDesconto(Dinheiro.arredondar(v), 0.0) else null
        }
        if (descTxt.isBlank()) return PagoDesconto(Dinheiro.arredondar(devido), 0.0)
        val v = Financas.parseSaldoInformado(descTxt) ?: return null
        if (v < 0) return null
        return when (descModo) {
            DescontoModo.COBRADO -> PagoDesconto(Dinheiro.arredondar(v), Dinheiro.arredondar(devido - v))
            DescontoModo.VALOR -> PagoDesconto(Dinheiro.arredondar(devido - v), Dinheiro.arredondar(v))
            DescontoModo.PERCENTUAL -> PagoDesconto(Dinheiro.arredondar(devido * (1 - v / 100)), Dinheiro.arredondar(devido * v / 100))
        }
    }
}
