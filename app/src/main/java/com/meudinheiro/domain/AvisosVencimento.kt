package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import java.text.NumberFormat
import java.util.Locale

/** Texto pronto de um aviso de contas a pagar (sem Android: testado na JVM). */
data class ResumoVencimentos(
    val titulo: String,
    val resumo: String,
    /** Uma linha por conta (até [MAX_LINHAS]) + "e mais N". */
    val linhas: List<String>,
    val atrasado: Boolean,
    /** A conta mais urgente: é a que o botão "Pagar agora" quita. */
    val maisUrgente: Despesa
)

object AvisosVencimento {
    const val MAX_LINHAS = 5

    /** "R$ 1.234,56" — escrito à mão para não depender do separador/NBSP de cada aparelho. */
    fun moeda(valor: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        return "R$ " + nf.format(valor)
    }

    /** "hoje", "amanhã", "em 3 dias", "venceu ontem", "venceu há 5 dias". */
    fun quando(vencimento: Long, agora: Long): String {
        val dias = ((Financas.inicioDoDia(vencimento) - Financas.inicioDoDia(agora)) / 86_400_000L).let { Math.round(it.toDouble()).toInt() }
        return when {
            dias == 0 -> "hoje"
            dias == 1 -> "amanhã"
            dias > 1 -> "em $dias dias"
            dias == -1 -> "venceu ontem"
            else -> "venceu há ${-dias} dias"
        }
    }

    fun montar(pendentes: List<Despesa>, agora: Long): ResumoVencimentos? {
        val lista = pendentes.filter { !it.pago }.sortedBy { it.data.time }
        if (lista.isEmpty()) return null
        val hoje = Financas.inicioDoDia(agora)
        val atrasadas = lista.count { it.data.time < hoje }
        val total = Dinheiro.somar(lista.map { it.valor })
        val primeira = lista.first()

        val titulo = when {
            atrasadas > 0 && atrasadas == lista.size -> if (atrasadas == 1) "1 conta atrasada" else "$atrasadas contas atrasadas"
            atrasadas > 0 -> "$atrasadas atrasada(s) e ${lista.size - atrasadas} a vencer"
            lista.size == 1 -> "${primeira.descricao} vence ${quando(primeira.data.time, agora)}"
            else -> "${lista.size} contas vencem em breve"
        }
        val resumo = if (lista.size == 1) moeda(primeira.valor) else "${moeda(total)} no total · próxima: ${primeira.descricao}, ${quando(primeira.data.time, agora)}"

        val linhas = lista.take(MAX_LINHAS).map { "${quando(it.data.time, agora).replaceFirstChar { c -> c.uppercase() }} · ${it.descricao} · ${moeda(it.valor)}" } +
            (if (lista.size > MAX_LINHAS) listOf("+ ${lista.size - MAX_LINHAS} outras no app") else emptyList())
        return ResumoVencimentos(titulo, resumo, linhas, atrasadas > 0, primeira)
    }
}
