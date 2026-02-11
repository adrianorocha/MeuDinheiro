package com.meudinheiro.componentes

import java.util.Calendar

enum class FiltroPeriodo(val label: String) {
    ESTE_MES("Este Mês"),
    MES_PASSADO("Mês Passado"),
    TOTAL("Total")
}

// Função auxiliar para obter os intervalos
fun obterIntervalo(filtro: FiltroPeriodo): Pair<Long?, Long?> {
    val cal = Calendar.getInstance()
    return when (filtro) {
        FiltroPeriodo.ESTE_MES -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val inicio = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            inicio to cal.timeInMillis
        }
        FiltroPeriodo.MES_PASSADO -> {
            cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val inicio = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            inicio to cal.timeInMillis
        }
        FiltroPeriodo.TOTAL -> null to null // Sem filtro de data
    }
}