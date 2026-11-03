package com.meudinheiro.funcoes

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PagarBoletoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ID_DESPESA é Long (ids vindos do portal passam de 32 bits); ID_BOLETO (Int) fica só como legado.
        val idBoleto: Long = intent.getLongExtra("ID_DESPESA", intent.getIntExtra("ID_BOLETO", -1).toLong())

        if (idBoleto != -1L) {
            val appContext = context.applicationContext

            // 1. A MÁGICA PARA FECHAR A NOTIFICAÇÃO
            // O id da notificação (Int) viaja à parte do id da despesa (Long).
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(com.meudinheiro.notif.Notificacoes.TAG, intent.getIntExtra("ID_NOTIF", idBoleto.toInt()))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = MainRepository(appContext)

                    // 2. Faz a baixa no banco (ID convertido para Long se necessário)
                    repository.marcarDespesaComoPaga(idBoleto, true)

                    // 3. Avisa a tela para atualizar
                    repository.avisarQueHouveMudanca()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "✅ Conta paga e removida do aviso!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("BluMacaw_Receiver", "Erro ao processar baixa: ${e.message}")
                }
            }
        }
    }
}