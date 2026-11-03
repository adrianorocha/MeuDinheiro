package com.meudinheiro.push

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.meudinheiro.domain.PushPrefs
import com.meudinheiro.domain.PushRegistro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * R65 — registro do token FCM em `users/<uid>/dispositivos/<id>` (SEMPRE o uid do usuário logado, nunca a raiz compartilhada).
 * Fica fora do sincronismo de dados (sem Room/catálogo/backup). Qualquer falha é silenciosa para o fluxo do app e só vira um
 * aviso em [aviso] (mostrado em Configurações). Sem Google Play Services/FCM: nada é registrado.
 */
object PushDispositivo {
    private const val TAG = "PushDispositivo"
    private const val ARQ = "push_dispositivo" // excluído do backup (backup_rules/data_extraction_rules)
    private const val K_ID = "id"
    private const val K_REGISTRADO_UID = "registrado_uid"

    private val _aviso = MutableStateFlow<String?>(null)

    /** Mensagem para a tela de Configurações (null = tudo certo). */
    val aviso: StateFlow<String?> = _aviso.asStateFlow()

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(ARQ, Context.MODE_PRIVATE)

    /** ID estável deste aparelho (nome do documento); criado uma vez. */
    fun idDoAparelho(context: Context): String {
        val p = sp(context)
        val atual = p.getString(K_ID, null)
        val id = PushRegistro.idEstavel(atual) { UUID.randomUUID().toString() }
        if (id != atual) p.edit().putString(K_ID, id).apply()
        return id
    }

    private fun playServicesOk(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context.applicationContext) == ConnectionResult.SUCCESS
    } catch (_: Throwable) {
        false
    }

    private fun docRef(uid: String, id: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("dispositivos").document(id)

    private fun versaoDoApp(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }

    /**
     * Aplica o estado desejado: registra/atualiza o token quando [PushRegistro.deveRegistrar]; senão remove o que este aparelho
     * tiver registrado. [uid] é o usuário logado (ou null). [raizUid] é a conta de dados em uso (só informativa para a Function).
     */
    suspend fun sincronizar(
        context: Context,
        uid: String?,
        nuvemAtiva: Boolean,
        ligado: Boolean,
        raizUid: String,
        prefs: PushPrefs,
        tokenNovo: String? = null
    ) {
        val app = context.applicationContext
        try {
            if (uid != null && PushRegistro.deveRegistrar(true, nuvemAtiva, ligado)) {
                if (!playServicesOk(app)) {
                    _aviso.value = "Notificações push indisponíveis: este aparelho não tem o Google Play Services. Os avisos locais continuam funcionando."
                    return
                }
                val token = tokenNovo?.takeIf { it.isNotBlank() } ?: FirebaseMessaging.getInstance().token.await()
                val id = idDoAparelho(app)
                val doc = PushRegistro.montarDoc(token, Build.MODEL, versaoDoApp(app), prefs, raizUid, uid) +
                    mapOf("atualizadoEm" to FieldValue.serverTimestamp())
                docRef(uid, id).set(doc).await()
                sp(app).edit().putString(K_REGISTRADO_UID, uid).apply()
                _aviso.value = null
            } else {
                val registrado = sp(app).getString(K_REGISTRADO_UID, null)
                if (registrado != null) {
                    // O aparelho tinha registro: apaga com o uid que o criou, se ainda for o logado.
                    if (uid != null && uid == registrado) docRef(uid, idDoAparelho(app)).delete().await()
                    sp(app).edit().remove(K_REGISTRADO_UID).apply()
                }
                _aviso.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Push não registrado", e)
            _aviso.value = "Não foi possível ativar as notificações push agora (${e.javaClass.simpleName}). Tentaremos de novo; os avisos locais continuam."
        }
    }

    /** Logout: apaga o documento e invalida o token (best effort, no máximo [limiteMs]; offline não trava a saída). */
    suspend fun removerAntesDeSair(context: Context, uid: String?, limiteMs: Long = 4_000) {
        val app = context.applicationContext
        val registrado = sp(app).getString(K_REGISTRADO_UID, null)
        sp(app).edit().remove(K_REGISTRADO_UID).apply()
        if (registrado == null) return
        try {
            withTimeoutOrNull(limiteMs) {
                if (uid != null && uid == registrado) docRef(uid, idDoAparelho(app)).delete().await()
                FirebaseMessaging.getInstance().deleteToken().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Falha ao remover o token no logout", e)
        }
    }
}
