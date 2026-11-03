package com.meudinheiro.domain

/** Formatação determinística (sem Locale) de centavos em pt-BR; idêntica ao portal (`lib/finance/centavos.ts`). */
object FormatoCentavos {

    /** `123456` -> `R$ 1.234,56`; negativo -> `-R$ 0,05`. */
    fun brl(centavos: Long): String {
        val abs = Math.abs(centavos)
        val inteiro = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val frac = (abs % 100).toString().padStart(2, '0')
        return (if (centavos < 0) "-" else "") + "R\$ " + inteiro + "," + frac
    }

    /** Décimos de ponto percentual -> `12,5`. */
    fun decimos(decimos: Long): String {
        val abs = Math.abs(decimos)
        return "${abs / 10},${abs % 10}"
    }

    /** Percentual em décimos (meio para cima) de `num/den`; `den <= 0` -> `null`. */
    fun decimosDe(num: Long, den: Long): Long? {
        if (den <= 0) return null
        return Math.round((num * 1000).toDouble() / den.toDouble())
    }
}
