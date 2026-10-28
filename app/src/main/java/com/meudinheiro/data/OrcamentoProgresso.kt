package com.meudinheiro.data

data class OrcamentoProgresso(
    val categoria: String,
    val limite: Double,
    val gastoAtual: Double,
    val porcentagem: Float,
    val id: Int = 0,
    val escopo: String? = null,
    val pessoaId: Int? = null,
    /** "Só eu", "Só João"; nulo = todos. */
    val rotuloEscopo: String? = null
) {
    /** Categoria + escopo, para listas e avisos. */
    val titulo: String get() = if (rotuloEscopo == null) categoria else "$categoria · $rotuloEscopo"
}
