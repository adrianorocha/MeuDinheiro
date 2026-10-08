package com.meudinheiro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ponte entre o atalho do launcher ("Nova despesa") e a tela principal. */
object AtalhosApp {
    const val ACAO_NOVA_DESPESA = "com.meudinheiro.action.NOVA_DESPESA"

    var novaDespesaPedida by mutableStateOf(false)
        private set

    fun pedirNovaDespesa() { novaDespesaPedida = true }
    fun consumir() { novaDespesaPedida = false }
}
