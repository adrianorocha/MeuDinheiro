package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import java.util.Date

/** Regras do lançamento feito pelo widget (sem abrir o app). Puro: testado sem emulador. */
object LancamentoRapido {

    /** "12", "12,5", "1.234,56", "1234.56", "R$ 9,90" → valor positivo; vazio/inválido/≤0 → null. */
    fun parseValor(texto: String): Double? {
        var t = texto.trim().replace("R$", "").replace(" ", "")
        if (t.isEmpty()) return null
        t = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            t.count { it == '.' } > 1 -> t.replace(".", "")
            else -> t
        }
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite() && v > 0) Dinheiro.arredondar(v) else null
    }

    /**
     * Monta o lançamento. Em conta, nasce pago (o dinheiro saiu/entrou agora); no cartão, fica em aberto até pagar
     * a fatura (R7). Sem descrição, usa a categoria.
     */
    fun montar(
        valor: Double,
        descricao: String,
        categoria: String,
        pic: String,
        tipo: TipoDespesa,
        conta: String,
        cartaoId: Int?,
        agora: Long
    ): Despesa {
        val v = Dinheiro.arredondar(valor)
        return Despesa(
            descricao = descricao.trim().ifBlank { categoria.trim() }.ifBlank { if (tipo == TipoDespesa.CREDITO) "Receita" else "Despesa" },
            valor = v,
            data = Date(agora),
            categoria = categoria.trim().ifBlank { "Outros" },
            conta = conta,
            pic = pic.ifBlank { "default_pic" },
            tipo = tipo,
            mes = Financas.mesDe(agora),
            ano = Financas.anoDe(agora),
            cartaoId = cartaoId,
            pago = cartaoId == null
        )
    }

    /** Categorias ordenadas pelo uso (mais usadas primeiro); as sem uso mantêm a ordem original. */
    fun categoriasPorUso(todas: List<String>, historico: List<Despesa>, tipo: TipoDespesa): List<String> {
        val uso = historico.filter { it.natureza == Natureza.NORMAL && it.tipo == tipo }
            .groupingBy { Texto.normalizar(it.categoria) }.eachCount()
        return todas.distinctBy { Texto.normalizar(it) }
            .sortedByDescending { uso[Texto.normalizar(it)] ?: 0 } // sortedBy é estável: empate mantém a ordem
    }
}
