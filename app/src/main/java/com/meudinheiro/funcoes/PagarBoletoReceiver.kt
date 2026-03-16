package com.meudinheiro.funcoes

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
        val idBoleto = intent.getIntExtra("ID_BOLETO", -1)

        if (idBoleto != -1) {
            val appContext = context.applicationContext

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = MainRepository(appContext)

                    // 1. Faz a baixa no banco
                    repository.marcarDespesaComoPaga(idBoleto, true)

                    // 2. DISPARA O SINAL (A mágica da atualização automática)
                    repository.avisarQueHouveMudanca()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "✅ Pagamento confirmado!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("BluMacaw_Receiver", "Erro: ${e.message}")
                }
            }
        }
        // Nota: O fechamento da notificação agora é automático pelo 'setAutoCancel(true)' no Helper.
    }
}