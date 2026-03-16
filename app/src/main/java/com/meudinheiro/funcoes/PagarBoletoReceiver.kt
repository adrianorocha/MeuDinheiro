package com.meudinheiro.funcoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PagarBoletoReceiver : BroadcastReceiver() {
    @RequiresPermission("android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS")
    override fun onReceive(context: Context, intent: Intent) {
        val idBoleto = intent.getIntExtra("ID_BOLETO", -1)

        if (idBoleto != -1) {
            // Usamos o Scope Global para operações rápidas em segundo plano
            CoroutineScope(Dispatchers.IO).launch {
                // Aqui deves chamar o teu Repository ou Base de Dados
                val repository = MainRepository(context)
                repository.marcarDespesaComoPaga(idBoleto, true)

                // Feedback tátil ou visual simples
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "Pagamento confirmado!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Fecha a bandeja de notificações (opcional, dependendo da versão do Android)
        val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        context.sendBroadcast(it)
    }
}