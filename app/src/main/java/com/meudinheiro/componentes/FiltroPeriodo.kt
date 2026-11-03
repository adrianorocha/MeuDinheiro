package com.meudinheiro.componentes

import com.meudinheiro.domain.Financas
import java.util.Calendar

enum class FiltroPeriodo(val label: String) {
    ESTE_MES("Este Mês"),
    MES_PASSADO("Mês Passado"),
    TOTAL("Total")
}

/** Intervalo [início do 1º dia, fim do último dia] do período; `null to null` = histórico completo. */
fun obterIntervalo(filtro: FiltroPeriodo): Pair<Long?, Long?> {
    val hoje = Calendar.getInstance()
    val mes = hoje.get(Calendar.MONTH) + 1
    val ano = hoje.get(Calendar.YEAR)
    return when (filtro) {
        FiltroPeriodo.ESTE_MES -> Financas.inicioDoMes(mes, ano) to Financas.fimDoMes(mes, ano)
        FiltroPeriodo.MES_PASSADO -> {
            val anterior = Financas.FaturaRef(mes, ano).anterior()
            Financas.inicioDoMes(anterior.mes, anterior.ano) to Financas.fimDoMes(anterior.mes, anterior.ano)
        }
        FiltroPeriodo.TOTAL -> null to null
    }
}
