package com.meudinheiro.componentes

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        withContext(Dispatchers.IO) {
            val repository = MainRepository(context)
            val resumo = repository.obterResumoGlobal()
            val saldoTotal = resumo.entradas - resumo.saidas

            // 🚀 CAPTURA AS METAS AQUI
            val listaMetas = repository.obterTodasAsMetasSync().take(3)

            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            editor.putFloat("saldo_atual", saldoTotal.toFloat())

            // 🚀 SALVA NO FORMATO NOVO QUE O WIDGET ESTÁ ESPERANDO
            editor.putInt("quantidade_metas", listaMetas.size)

            listaMetas.forEachIndexed { index, meta ->
                editor.putString("meta_${index}_nome", meta.nome)
                editor.putString("meta_${index}_id", meta.id.toString())
                editor.putFloat("meta_${index}_valor_meta", (meta.valorObjetivo ?: 1.0).toFloat())
                editor.putFloat("meta_${index}_valor_alcancado", (meta.valorGuardado ?: 0.0).toFloat())
            }
            editor.apply()
        }

        // Manda o widget se redesenhar com os dados novos
        SaldoWidget().updateAll(context)
    }
}