package com.meudinheiro.domain

/** Regras puras do formulário de receita do app ("Novo Lcto" > Receita). Espelha o portal: receita = CREDITO, natureza NORMAL. */
object NovaReceita {
    const val CATEGORIA_PADRAO = "Salário"

    /** Valor digitado (aceita vírgula) em reais; nulo se inválido ou <= 0. */
    fun parseValor(txt: String): Double? =
        txt.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Categoria inicial: "Salário" se existir na lista (sem diferenciar maiúsculas), senão a primeira. */
    fun categoriaInicial(categorias: List<String>): String? =
        categorias.firstOrNull { it.equals(CATEGORIA_PADRAO, ignoreCase = true) } ?: categorias.firstOrNull()

    /** Receita com data futura fica pendente (não altera o saldo até a data); a marcada como "já recebida" só vale se a data não for futura. */
    fun pagoInicial(dataMs: Long, agoraMs: Long): Boolean = dataMs <= agoraMs

    fun pagoFinal(jaRecebido: Boolean, dataMs: Long, agoraMs: Long): Boolean = jaRecebido && dataMs <= agoraMs

    const val PREFIXO_ORIGEM = "De: "

    /**
     * R84 — origem informativa da receita ("João me pagou"): vira a tag "De: <nome>" (R56, máx. 30 caracteres, sem campo novo).
     * Não cria acerto nem mexe em dívidas/saldos. Nome vazio/nulo = [tags] normalizadas, sem origem.
     */
    fun tagsComOrigem(tags: List<String>, nomePessoa: String?): List<String> {
        val nome = nomePessoa?.trim().orEmpty()
        if (nome.isEmpty()) return Tags.normalizar(tags)
        val origem = Tags.rotulo(PREFIXO_ORIGEM + nome)
        // a origem tem prioridade: entra primeiro e nunca é descartada pelo limite de tags
        return Tags.normalizar(listOf(origem) + tags)
    }

    /** Mensagem de erro de validação ou nulo se válido. */
    fun validar(descricao: String, valorTxt: String, categoria: String?): String? = when {
        descricao.isBlank() -> "Informe a descrição."
        parseValor(valorTxt) == null -> "Informe um valor maior que zero."
        categoria.isNullOrBlank() -> "Escolha a categoria."
        else -> null
    }
}
