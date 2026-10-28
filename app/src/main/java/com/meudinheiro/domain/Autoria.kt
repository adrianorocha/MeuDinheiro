package com.meudinheiro.domain

import com.meudinheiro.data.Despesa

/**
 * R67 — autoria do lançamento (contas compartilhadas). Função pura.
 * `autor` = nome de quem lançou (o `autorNome` do contrato; "Importação" quando veio do conciliador) e `autorUid` = uid do
 * Firebase Auth. Em modo local-only não há uid (nulo) e o lançamento segue só com o nome (R32).
 */
object Autoria {
    /** Preenche o que estiver vazio; mantém um autor já informado (ex.: "Importação"). */
    fun completar(d: Despesa, nome: String, uid: String?): Despesa = d.copy(
        autor = d.autor?.takeIf { it.isNotBlank() } ?: nome.trim().takeIf { it.isNotEmpty() },
        autorUid = d.autorUid?.takeIf { it.isNotBlank() } ?: uid?.takeIf { it.isNotBlank() }
    )

    /** Duplicar/repetir/parcelar: o autor é quem EXECUTA a ação, nunca o do lançamento de origem. */
    fun paraExecutor(d: Despesa, nome: String, uid: String?): Despesa = completar(d.copy(autor = null, autorUid = null), nome, uid)
}
