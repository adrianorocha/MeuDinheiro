package com.meudinheiro.storage

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Orquestra o armazenamento: observa (modo escolhido × login) e mantém UMA [SyncSession] viva quando —
 * e somente quando — o modo é FIREBASE e há usuário logado. Em modo LOCAL nenhuma chamada de rede é feita.
 * Vive no `Application`; a UI só lê [status] e chama as ações abaixo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = UserPreferences(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val repository = MainRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val auth by lazy { CloudAuth() }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Desligado)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val modo = prefs.storageModeFlow

    /** uid cujo dados estão em uso: o do próprio usuário (vazio) ou o de quem compartilhou com ele (R32). */
    val raizUid: Flow<String> = prefs.cloudRootUidFlow

    @Volatile
    private var sessao: SyncSession? = null

    fun iniciar() {
        combine(prefs.storageModeFlow, authUidOuNulo(), prefs.cloudRootUidFlow) { modo, uid, raiz -> Triple(modo, uid, raiz) }
            .distinctUntilChanged()
            .flatMapLatest { (modo, uid, raiz) ->
                flow {
                    when {
                        modo != StorageMode.FIREBASE -> {
                            _status.value = SyncStatus.Desligado
                        }
                        uid == null -> _status.value = SyncStatus.AguardandoLogin
                        else -> {
                            emit(Unit)
                            runCatching { Compartilhamento(auth).registrarPerfil() } // permite ser convidado por e-mail
                            executarSessao(raiz.ifBlank { uid })
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    /** O Firebase pode estar indisponível (ex.: google-services ausente): nesse caso o app segue em modo local. */
    private fun authUidOuNulo() = runCatching { auth.uidFlow }.getOrElse {
        Log.e(TAG, "Firebase indisponível", it)
        flow { emit(null) }
    }

    private suspend fun executarSessao(uid: String) {
        // Trocou de conta na nuvem: o histórico de sincronização do usuário anterior não vale para este.
        if (prefs.lastCloudUid() != uid) {
            db.syncMetaDao().limpar()
            prefs.saveLastCloudUid(uid)
        }
        var tentativa = 0
        while (true) {
            val nova = SyncSession(uid, db, FirebaseFirestore.getInstance(), repository) { _status.value = it }
            sessao = nova
            try {
                _status.value = SyncStatus.Conectando
                nova.rodar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sessão de sincronização encerrada", e)
                _status.value = SyncStatus.Erro(SyncSession.descreverErro(e))
            } finally {
                sessao = null
            }
            tentativa++
            delay((5_000L * tentativa).coerceAtMost(60_000L)) // recuo progressivo
        }
    }

    // ------------------------------------------------------------------ ações da UI

    suspend fun alterarModo(novo: StorageMode) = prefs.saveStorageMode(novo)

    /** Envia tudo do celular para a nuvem e apaga da nuvem o que não existe no celular. */
    suspend fun enviarTudoParaNuvem() = (sessao ?: error("Sem conexão com a nuvem")).pedir(ModoSync.CELULAR_VENCE)

    /** Baixa tudo da nuvem e apaga do celular o que não existe na nuvem. */
    suspend fun baixarTudoDaNuvem() = (sessao ?: error("Sem conexão com a nuvem")).pedir(ModoSync.NUVEM_VENCE)

    suspend fun sincronizarAgora() = sessao?.pedir(ModoSync.MESCLAR)

    /** R32 — passa a usar os dados de quem convidou (a sincronização troca de raiz). */
    suspend fun usarDadosDe(donoUid: String) = prefs.saveCloudRootUid(donoUid)

    suspend fun usarMeusDados() = prefs.saveCloudRootUid("")

    suspend fun sair() {
        prefs.saveCloudRootUid("")
        auth.sair()
        db.syncMetaDao().limpar()
        prefs.saveLastCloudUid("")
    }

    companion object {
        private const val TAG = "StorageManager"
    }
}
