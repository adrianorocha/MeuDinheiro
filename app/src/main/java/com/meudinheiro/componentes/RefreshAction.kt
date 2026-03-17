package com.meudinheiro.componentes

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Aqui você dispara a atualização de todos os widgets do tipo SaldoWidget
        SaldoWidget().updateAll(context)
    }
}