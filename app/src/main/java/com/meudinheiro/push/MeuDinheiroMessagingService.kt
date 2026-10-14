package com.meudinheiro.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meudinheiro.MyApplication
import com.meudinheiro.domain.PushTextos
import com.meudinheiro.domain.TipoPush
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.notif.Aviso
import com.meudinheiro.notif.Notificacoes
import com.meudinheiro.notif.TipoAviso
import com.meudinheiro.storage.StorageMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * R65 — recebe os pushes (SOMENTE mensagens de dados: o app decide o canal e a privacidade) e atualiza o token.
 * Os avisos locais existentes (ReceptorAvisosDiarios etc.) continuam funcionando como fallback, sem mudança.
 */
class MeuDinheiroMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                val prefs = UserPreferences(applicationContext)
                val storage = (applicationContext as MyApplication).storageManager
                val uid = storage.auth.uidAtual
                val nuvem = prefs.storageModeFlow.first() == StorageMode.FIREBASE
                // Só registra com opt-in + logado + nuvem; senão o sincronizar() não grava nada (e limpa o que sobrou).
                PushDispositivo.sincronizar(
                    applicationContext, uid, nuvem, prefs.pushEnabledFlow.first(),
                    prefs.cloudRootUidFlow.first(), prefs.pushPrefsFlow.first(), tokenNovo = token
                )
            }.onFailure { Log.w(TAG, "onNewToken", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val texto = PushTextos.montar(message.data) ?: return
        scope.launch {
            runCatching {
                val prefs = UserPreferences(applicationContext)
                // Defesa em profundidade: o servidor já filtra, mas respeitamos o interruptor geral e o do tipo localmente.
                if (!prefs.pushEnabledFlow.first() || !prefs.pushPrefsFlow.first().permite(texto.tipo)) return@runCatching
                val (tipo, id) = when (texto.tipo) {
                    TipoPush.VENCIMENTOS -> (if (texto.atrasado) TipoAviso.ATRASADO else TipoAviso.VENCIMENTO) to 65_001
                    TipoPush.ORCAMENTO -> (if (texto.estourado) TipoAviso.ORCAMENTO_ESTOURADO else TipoAviso.ORCAMENTO) to (65_100 + (texto.corpo.hashCode() and 0x3FF))
                    TipoPush.RESUMO_SEMANAL -> TipoAviso.PUSH to 65_002
                    TipoPush.CONVITE -> TipoAviso.PUSH to 65_003
                }
                Notificacoes.mostrar(
                    applicationContext,
                    Aviso(tipo = tipo, titulo = texto.titulo, resumo = texto.corpo, id = id, textoPublico = texto.publico)
                )
            }.onFailure { Log.w(TAG, "onMessageReceived", it) }
        }
    }

    private companion object {
        const val TAG = "MeuDinheiroFcm"
    }
}
