package com.meudinheiro.data

import com.meudinheiro.domain.Movimento
import com.meudinheiro.domain.Natureza

data class DespesasDomain(
    /** Long: ids vindos do portal/Firebase passam de 32 bits; como Int chegavam truncados e as ações atingiam outro lançamento. */
    val id: Long,
    val pic: String,
    val descricao: String,
    override val valor: Double,
    val data: Long,
    override val conta: String,
    override val categoria: String,
    override val tipo: TipoDespesa,
    override val pago: Boolean,
    override val cartaoId: Int? = null,
    override val natureza: String = Natureza.NORMAL,
    val grupoId: String? = null,
    val autor: String? = null,
    val fitid: String? = null,
    val conciliadoEm: Long? = null
) : Movimento {
    override val dataMs: Long get() = data
}

data class DespesaAviso(
    val id: Long,
    val titulo: String,
    val valor: Double,
    val vencimentoMillis: Long,
    val tipo: String
)

data class ResumoFinanceiroDto(
    val conta: String,
    val tipo: TipoDespesa,
    val valorTotal: Double
)
