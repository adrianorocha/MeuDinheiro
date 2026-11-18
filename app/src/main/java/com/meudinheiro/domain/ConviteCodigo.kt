package com.meudinheiro.domain

import java.security.SecureRandom

/**
 * R67 — código de convite (8 caracteres, uso único, validade de 7 dias). Funções puras (o sorteio é injetável).
 * Espelho em `portal/src/lib/finance/convite.ts`; o formato também é validado em `portal/firestore.rules`.
 */
object ConviteCodigo {
    /** Sem 0/O/1/I/L (ambíguos): 23 letras + 8 dígitos (2..9) = 31 símbolos. */
    const val ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val TAMANHO = 8
    const val VALIDADE_MS = 7L * 24 * 60 * 60 * 1000

    enum class Estado { VALIDO, EXPIRADO, USADO, INVALIDO }

    private val aleatorio by lazy { SecureRandom() }

    /** [sorteio] devolve um inteiro em [0, n). Padrão: SecureRandom. */
    fun gerar(sorteio: (Int) -> Int = { aleatorio.nextInt(it) }): String =
        buildString { repeat(TAMANHO) { append(ALFABETO[sorteio(ALFABETO.length)]) } }

    /** Aceita minúsculas, espaços e hífen ("abcd-2345" → "ABCD2345"). */
    fun normalizar(texto: String?): String =
        texto.orEmpty().uppercase().filter { !it.isWhitespace() && it != '-' }

    fun valido(codigo: String?): Boolean =
        codigo != null && codigo.length == TAMANHO && codigo.all { it in ALFABETO }

    fun expirado(expiraEm: Long, agora: Long): Boolean = agora >= expiraEm

    /** Situação de um convite lido do Firestore. Usado tem precedência sobre expirado. */
    fun estado(usado: Boolean?, expiraEm: Long?, agora: Long): Estado = when {
        usado == null || expiraEm == null -> Estado.INVALIDO
        usado -> Estado.USADO
        expirado(expiraEm, agora) -> Estado.EXPIRADO
        else -> Estado.VALIDO
    }

    fun mensagem(e: Estado): String? = when (e) {
        Estado.VALIDO -> null
        Estado.EXPIRADO -> "Este convite expirou. Peça um código novo a quem convidou."
        Estado.USADO -> "Este convite já foi usado. Peça um código novo a quem convidou."
        Estado.INVALIDO -> "Código de convite inválido. Confira os 8 caracteres."
    }

    /** Documento `convites/<codigo>` (campos do contrato R67). */
    fun documento(donoUid: String, papel: Papel, criadoPor: String, agora: Long): Map<String, Any> {
        require(papel in Papel.ATRIBUIVEIS) { "Papel inválido para convite." }
        return mapOf(
            "donoUid" to donoUid, "papel" to papel.name, "criadoEm" to agora, "expiraEm" to agora + VALIDADE_MS,
            "usado" to false, "criadoPor" to criadoPor
        )
    }
}
