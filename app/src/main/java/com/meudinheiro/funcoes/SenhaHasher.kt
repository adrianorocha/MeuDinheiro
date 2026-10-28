package com.meudinheiro.funcoes

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de senha com PBKDF2 + sal aleatório (a senha deixou de ser gravada em texto puro no DataStore).
 * Formato: `pbkdf2$<algoritmo>$<iterações>$<sal hex>$<hash hex>`.
 */
object SenhaHasher {
    private const val PREFIXO = "pbkdf2"
    private const val ITERACOES = 120_000
    private const val BITS = 256

    // PBKDF2WithHmacSHA256 só existe a partir da API 26; o app suporta API 25.
    private val ALGORITMOS = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

    fun ehHash(armazenado: String) = armazenado.startsWith("$PREFIXO$")

    fun gerar(senha: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val (algoritmo, hash) = derivar(senha, sal, ITERACOES, ALGORITMOS)
        return listOf(PREFIXO, algoritmo, ITERACOES, hex(sal), hex(hash)).joinToString("$")
    }

    /** Aceita hash PBKDF2 e, para migração, senhas antigas em texto puro. */
    fun conferir(senha: String, armazenado: String): Boolean {
        if (armazenado.isBlank()) return false
        if (!ehHash(armazenado)) return constante(senha.toByteArray(), armazenado.toByteArray())
        val partes = armazenado.split("$")
        if (partes.size != 5) return false
        val iteracoes = partes[2].toIntOrNull() ?: return false
        val sal = bytes(partes[3]) ?: return false
        val esperado = bytes(partes[4]) ?: return false
        val calculado = runCatching { derivar(senha, sal, iteracoes, listOf(partes[1])).second }.getOrNull() ?: return false
        return constante(calculado, esperado)
    }

    private fun derivar(senha: String, sal: ByteArray, iteracoes: Int, algoritmos: List<String>): Pair<String, ByteArray> {
        var ultimoErro: Exception? = null
        for (alg in algoritmos) {
            try {
                val spec = PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS)
                return alg to SecretKeyFactory.getInstance(alg).generateSecret(spec).encoded
            } catch (e: Exception) {
                ultimoErro = e
            }
        }
        throw IllegalStateException("PBKDF2 indisponível", ultimoErro)
    }

    private fun constante(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun bytes(hex: String): ByteArray? =
        if (hex.length % 2 != 0) null
        else runCatching { ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
}
