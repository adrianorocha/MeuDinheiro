package com.meudinheiro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. Entidade
@Entity(tableName = "orcamentos")
data class Orcamento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoria: String,
    val valorLimite: Double,
    /** "EU" | "PESSOA"; nulo = "TODOS" (tudo da categoria, comportamento original). */
    val escopo: String? = null,
    /** Só com [escopo] = "PESSOA". */
    val pessoaId: Int? = null
)

/** Valores de [Orcamento.escopo] (contrato compartilhado com o portal). */
object EscopoOrcamento {
    const val EU = "EU"
    const val PESSOA = "PESSOA"

    /** Normaliza o par (escopo, pessoaId): desconhecido/PESSOA sem pessoa = TODOS (nulos). */
    fun normalizar(escopo: String?, pessoaId: Int?): Pair<String?, Int?> = when (escopo?.trim()?.uppercase()) {
        EU -> EU to null
        PESSOA -> if (pessoaId != null) PESSOA to pessoaId else null to null
        else -> null to null
    }
}

