package com.meudinheiro.storage

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.domain.ConfigGeral
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

/**
 * R57 — sincroniza `users/{uid}/configuracoes/geral` com [ConfiguracaoLocal]. Regra: o MAIS NOVO (`atualizadoEm`)
 * vence; empate ou nuvem sem data nunca sobrescreve o local. Só envia depois de ter ouvido o servidor (um cache vazio
 * offline não prova que a nuvem está vazia). Falhas (rede, permissão) nunca derrubam a sincronização principal: o local
 * continua valendo.
 */
class ConfigNuvem(
    uid: String,
    firestore: FirebaseFirestore,
    private val local: ConfiguracaoLocal,
    private val db: AppDatabase
) {
    private val ref = firestore.collection("users").document(uid).collection("configuracoes").document("geral")

    @Volatile private var nuvem: ConfigGeral? = null
    @Volatile private var confirmada = false

    suspend fun rodar() = coroutineScope {
        val eventos = Channel<Unit>(Channel.CONFLATED)
        val registro = ref.addSnapshotListener { snap, erro ->
            if (erro != null) {
                Log.w(TAG, "Configurações: sem acesso à nuvem (o local segue valendo)", erro)
            } else if (snap != null) {
                nuvem = if (snap.exists()) ConfigGeral.deDoc(snap.data) else null
                if (!snap.metadata.isFromCache) confirmada = true
                eventos.trySend(Unit)
            }
        }
        db.configuracaoDao().observarGeral().onEach { eventos.trySend(Unit) }.launchIn(this)
        try {
            for (e in eventos) {
                try {
                    when (local.aplicar(nuvem)) {
                        ConfigGeral.Acao.ENVIAR_LOCAL -> if (confirmada) {
                            local.documentoParaEnvio()?.let { ref.set(it) }
                        }
                        else -> Unit
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (ex: Exception) {
                    Log.w(TAG, "Falha ao sincronizar configurações", ex)
                }
            }
        } finally {
            registro.remove()
        }
    }

    private companion object {
        const val TAG = "ConfigNuvem"
    }
}
