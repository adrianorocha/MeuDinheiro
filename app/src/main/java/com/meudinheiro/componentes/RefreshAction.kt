package com.meudinheiro.componentes

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Botão "atualizar" do widget: reprocessa as recorrências devidas e recalcula os saldos a partir do
 * extrato (R3) e redesenha. O widget lê o banco direto, então nada é copiado para preferências.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            val repository = MainRepository(context)
            runCatching { repository.processarRecorrencias() }
            runCatching { repository.recalcularTudo() }
        }
        SaldoWidget().updateAll(context)
    }
}
