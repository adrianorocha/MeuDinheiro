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

    /** R63 — abrir a revisão de extratos recebidos (compartilhados ou da pasta monitorada). */
    const val ACAO_IMPORTAR_EXTRATOS = "com.meudinheiro.action.IMPORTAR_EXTRATOS"

    var importarExtratosPedido by mutableStateOf(false)
        private set

    fun pedirImportarExtratos() { importarExtratosPedido = true }
    fun consumirImportarExtratos() { importarExtratosPedido = false }
}
