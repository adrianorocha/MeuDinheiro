package com.meudinheiro.domain

/**
 * R67 — filtro "Quem lançou" e visão "Minhas despesas" × "Conjunta" (padrão) em contas com membros. Função pura.
 * Lançamento sem `autorUid` (dado antigo, modo local, importação sem uid) NÃO pertence a "Minhas" nem a um membro específico;
 * continua aparecendo em "Conjunta" (todos).
 */
object QuemLancou {
    sealed interface Selecao {
        /** Conjunta: tudo (padrão). */
        data object Todos : Selecao

        /** Minhas despesas: só `autorUid == meuUid`. */
        data object Eu : Selecao

        /** Um membro específico. */
        data class Membro(val uid: String) : Selecao
    }

    data class Autor(val uid: String, val nome: String)

    fun <T> filtrar(itens: List<T>, selecao: Selecao, meuUid: String?, uidDe: (T) -> String?): List<T> = when (selecao) {
        Selecao.Todos -> itens
        Selecao.Eu -> if (meuUid.isNullOrBlank()) emptyList() else itens.filter { uidDe(it) == meuUid }
        is Selecao.Membro -> itens.filter { uidDe(it) == selecao.uid }
    }

    /** Autores distintos (uid → último nome visto), ordenados por nome, sem o próprio usuário. */
    fun <T> autoresDistintos(itens: List<T>, meuUid: String?, uidDe: (T) -> String?, nomeDe: (T) -> String?): List<Autor> =
        itens.mapNotNull { i -> uidDe(i)?.takeIf { it.isNotBlank() && it != meuUid }?.let { it to (nomeDe(i)?.takeIf(String::isNotBlank) ?: "Membro") } }
            .toMap()
            .map { Autor(it.key, it.value) }
            .sortedBy { it.nome.lowercase() }

    /** Texto discreto "por <nome>"; só quando a conta tem membros e o lançamento traz autor. */
    fun rotuloAutor(autor: String?, contaTemMembros: Boolean): String? =
        if (!contaTemMembros) null else autor?.trim()?.takeIf { it.isNotEmpty() }?.let { "por ${nomeCurto(it)}" }

    /** E-mail (autor gravado pelo portal) vira a parte antes do @. */
    fun nomeCurto(autor: String): String = autor.trim().let { if (it.contains('@')) it.substringBefore('@') else it }
}
