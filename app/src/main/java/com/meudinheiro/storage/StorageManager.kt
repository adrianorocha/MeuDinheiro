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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    /** R87 — registros que a nuvem recusou (persistente; não usa Room, então não há migração nem risco para os dados). */
    val quarentena = Quarentena(object : PersistenciaQuarentena {
        private val sp = appContext.getSharedPreferences("quarentena_sync", Context.MODE_PRIVATE)
        override fun ler(): String? = sp.getString("dados", null)
        override fun gravar(texto: String) { sp.edit().putString("dados", texto).apply() }
    })

    private data class EnvioEstado(val pendentes: Int = 0, val confirmadoEm: Long? = null)

    private val _envio = MutableStateFlow(EnvioEstado())
    private val _rede = MutableStateFlow(true)

    /** Acorda o recuo entre tentativas de abrir a sessão ("Tentar enviar agora"). */
    private val acordar = Channel<Unit>(Channel.CONFLATED)

    /**
     * R86 — estado observável do envio (status × pendências × rede × quarentena). Derivado só de dados em memória:
     * nenhuma leitura extra no Firestore. A UI formata com [IndicadorSync.derivar].
     */
    val info: StateFlow<InfoSync> = combine(prefs.storageModeFlow, _status, _envio, _rede, quarentena.lista) { m, st, env, rede, q ->
        InfoSync(
            nuvem = m == StorageMode.FIREBASE, status = st, pendentes = env.pendentes, rede = rede,
            quarentena = q.count { !it.ignorado }, confirmadoEm = env.confirmadoEm
        )
    }.stateIn(scope, SharingStarted.Eagerly, InfoSync())

    /** uid cujo dados estão em uso: o do próprio usuário (vazio) ou o de quem compartilhou com ele (R32). */
    val raizUid: Flow<String> = prefs.cloudRootUidFlow

    /** R67 — meu papel na conta em uso. Local-only/conta própria = DONO (tudo liberado); a UI só esconde botões, quem barra são as regras. */
    private val _papel = MutableStateFlow(com.meudinheiro.domain.Papel.DONO)
    val papel: StateFlow<com.meudinheiro.domain.Papel> = _papel.asStateFlow()

    /** R67 — a conta em uso tem outros membros (mostra "por <nome>" e o filtro "Quem lançou"). */
    private val _temMembros = MutableStateFlow(false)
    val temMembros: StateFlow<Boolean> = _temMembros.asStateFlow()

    @Volatile
    private var sessao: SyncSession? = null

    /** Relê papel e existência de membros da conta em uso (ao iniciar a sessão e depois de mudanças no compartilhamento). */
    suspend fun atualizarCompartilhamento() {
        val uid = auth.uidAtual
        if (uid == null || prefs.storageModeFlow.first() != StorageMode.FIREBASE) {
            _papel.value = com.meudinheiro.domain.Papel.DONO
            _temMembros.value = false
            return
        }
        val raiz = prefs.cloudRootUidFlow.first().ifBlank { uid }
        runCatching {
            val c = Compartilhamento(auth)
            _papel.value = c.meuPapel(raiz)
            _temMembros.value = raiz != uid || c.membros(raiz).isNotEmpty()
        }.onFailure { if (it is CancellationException) throw it }
    }

    /** Acompanha a conectividade só para o aviso "Sem conexão"; o Firestore em si segue gravando offline. */
    private fun observarRede() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            _rede.value = cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { _rede.value = true }
                override fun onLost(network: android.net.Network) { _rede.value = false }
            })
        }.onFailure { Log.w(TAG, "Sem monitor de rede", it) }
    }

    fun iniciar() {
        observarRede()
        combine(prefs.storageModeFlow, authUidOuNulo(), prefs.cloudRootUidFlow) { modo, uid, raiz -> Triple(modo, uid, raiz) }
            .distinctUntilChanged()
            .flatMapLatest { (modo, uid, raiz) ->
                flow {
                    when {
                        modo != StorageMode.FIREBASE -> {
                            _status.value = SyncStatus.Desligado
                            _envio.value = EnvioEstado()
                        }
                        uid == null -> _status.value = SyncStatus.AguardandoLogin
                        else -> {
                            emit(Unit)
                            runCatching { Compartilhamento(auth).registrarPerfil() } // permite ser convidado por e-mail
                            scope.launch { atualizarCompartilhamento() }
                            executarSessao(raiz.ifBlank { uid })
                        }
                    }
                }
            }
            .launchIn(scope)
        iniciarPush()
    }

    /**
     * R65 — mantém o token FCM deste aparelho em `users/<uid>/dispositivos/<id>` conforme (login × nuvem × interruptor × prefs).
     * Fora do sincronismo de dados. Falhas são silenciosas (ver [com.meudinheiro.push.PushDispositivo.aviso]).
     */
    private fun iniciarPush() {
        combine(prefs.storageModeFlow, authUidOuNulo(), prefs.cloudRootUidFlow, prefs.pushEnabledFlow, prefs.pushPrefsFlow) { modo, uid, raiz, ligado, p ->
            PushEstado(uid, modo == StorageMode.FIREBASE, raiz, ligado, p)
        }
            .distinctUntilChanged()
            .onEach { e ->
                com.meudinheiro.push.PushDispositivo.sincronizar(appContext, e.uid, e.nuvem, e.ligado, e.raiz, e.prefs)
            }
            .launchIn(scope)
    }

    private data class PushEstado(val uid: String?, val nuvem: Boolean, val raiz: String, val ligado: Boolean, val prefs: com.meudinheiro.domain.PushPrefs)

    /** O Firebase pode estar indisponível (ex.: google-services ausente): nesse caso o app segue em modo local. */
    private fun authUidOuNulo() = runCatching { auth.uidFlow }.getOrElse {
        Log.e(TAG, "Firebase indisponível", it)
        flow { emit(null) }
    }

    private suspend fun executarSessao(uid: String) {
        // Trocou de conta na nuvem: o histórico de sincronização do usuário anterior não vale para este.
        if (prefs.lastCloudUid() != uid) {
            db.syncMetaDao().limpar()
            quarentena.limpar()
            prefs.saveLastCloudUid(uid)
        }
        // R87: atualização do app = uma nova chance (uma vez por versão) para os registros recusados.
        quarentena.liberarSeNovaVersao(versaoDoApp())
        var tentativa = 0
        while (true) {
            val firestore = FirebaseFirestore.getInstance()
            val inicio = System.currentTimeMillis()
            _envio.value = EnvioEstado(0, _envio.value.confirmadoEm)
            val nova = SyncSession(
                uid, db, firestore, repository, { _status.value = it }, { EnvioImediato.aoEnviar(appContext) },
                quarentena, { n, t -> _envio.value = EnvioEstado(n, t) }
            )
            sessao = nova
            try {
                _status.value = SyncStatus.Conectando
                coroutineScope {
                    // R57: configurações sincronizadas (documento único); falhas dela nunca derrubam a sincronização principal.
                    val config = launch {
                        runCatching { ConfigNuvem(uid, firestore, ConfiguracaoLocal(db, prefs), db).rodar() }
                            .onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Configurações sem sincronização", it) }
                    }
                    try {
                        nova.rodar()
                    } finally {
                        config.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sessão de sincronização encerrada", e)
                _status.value = SyncStatus.Erro(SyncSession.descreverErro(e))
            } finally {
                sessao = null
            }
            // R85: uma sessão que viveu bem recomeça o recuo do zero (antes, a contagem só crescia e uma queda
            // transitória horas depois já esperava 60 s); erro persistente (ex.: permissão) segue com recuo até 60 s.
            tentativa = if (System.currentTimeMillis() - inicio > PoliticaEnvio.SESSAO_ESTAVEL_MS) 1 else tentativa + 1
            // "Tentar enviar agora" ([reenviarAgora]) interrompe a espera.
            withTimeoutOrNull(PoliticaEnvio.recuoMs(tentativa)) { acordar.receive() }
        }
    }

    // ------------------------------------------------------------------ ações da UI

    suspend fun alterarModo(novo: StorageMode) = prefs.saveStorageMode(novo)

    /** Envia tudo do celular para a nuvem e apaga da nuvem o que não existe no celular. */
    suspend fun enviarTudoParaNuvem() = (sessao ?: error("Sem conexão com a nuvem")).pedir(ModoSync.CELULAR_VENCE)

    /** Baixa tudo da nuvem e apaga do celular o que não existe na nuvem. */
    suspend fun baixarTudoDaNuvem() = (sessao ?: error("Sem conexão com a nuvem")).pedir(ModoSync.NUVEM_VENCE)

    suspend fun sincronizarAgora() = sessao?.pedir(ModoSync.MESCLAR)

    /** R86 — toque no indicador: tenta reenviar já (corta o recuo se a sessão caiu; senão reconcilia agora). */
    suspend fun reenviarAgora() {
        if (_status.value is SyncStatus.Erro) acordar.trySend(Unit)
        val s = sessao ?: return
        try {
            s.pedir(ModoSync.MESCLAR)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reenvio manual não concluído", e)
        }
    }

    /** R87 — "Tentar de novo": devolve os registros da quarentena à fila (os dados locais nunca saíram do lugar). */
    suspend fun tentarQuarentenaDeNovo() {
        quarentena.liberarTodos()
        reenviarAgora()
    }

    /** R87 — "Ignorar": tira o aviso; os registros seguem parados e preservados no celular. */
    fun ignorarQuarentena() = quarentena.ignorarTodos()

    private fun versaoDoApp(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime.toString()
    }.getOrDefault("")

    /** R85 — há algo a enviar para a nuvem (sinal barato, em memória). */
    val temPendencias: Boolean get() = sessao?.temPendencias == true

    /** R85 — envia o que estiver pendente e espera o servidor confirmar (até [timeoutMs]). false se sem sessão/tempo esgotado/falha. */
    suspend fun enviarPendentes(timeoutMs: Long): Boolean {
        val s = sessao ?: return false
        return try {
            s.enviarPendentes(timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Envio imediato não concluído", e)
            false
        }
    }

    /** R32 — passa a usar os dados de quem convidou (a sincronização troca de raiz). */
    suspend fun usarDadosDe(donoUid: String) = prefs.saveCloudRootUid(donoUid)

    suspend fun usarMeusDados() = prefs.saveCloudRootUid("")

    suspend fun sair() {
        // R65: remove o token deste aparelho ENQUANTO ainda há sessão (as regras só deixam o próprio uid apagar)
        runCatching { com.meudinheiro.push.PushDispositivo.removerAntesDeSair(appContext, auth.uidAtual) }
        prefs.saveCloudRootUid("")
        auth.sair()
        db.syncMetaDao().limpar()
        quarentena.limpar()
        prefs.saveLastCloudUid("")
    }

    companion object {
        private const val TAG = "StorageManager"
    }
}
