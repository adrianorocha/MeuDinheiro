package com.meudinheiro.funcoes

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.meudinheiro.domain.Bcb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Armazém do cache do BCB (texto JSON). Real: DataStore local; nos testes: fake em memória. NUNCA vai ao backup/Firestore. */
interface BcbCache {
    suspend fun lerIndices(): String?
    suspend fun gravarIndices(texto: String)
    suspend fun lerCambio(): String?
    suspend fun gravarCambio(texto: String)
}

/** Cache do BCB nas chaves próprias do DataStore `user_prefs` (R100). */
class BcbCachePrefs(private val prefs: UserPreferences) : BcbCache {
    override suspend fun lerIndices(): String? = prefs.bcbIndicesCacheFlow.first().ifEmpty { null }
    override suspend fun gravarIndices(texto: String) = prefs.salvarBcbIndicesCache(texto)
    override suspend fun lerCambio(): String? = prefs.bcbCambioCacheFlow.first().ifEmpty { null }
    override suspend fun gravarCambio(texto: String) = prefs.salvarBcbCambioCache(texto)
}

/** `true` se o aparelho tem rede com acesso à internet (em dúvida, `true`: a requisição decide). */
fun temRede(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val rede = cm?.activeNetwork
    if (cm == null) true else rede != null && cm.getNetworkCapabilities(rede)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: Exception) {
    true
}

data class EstadoIndicesBcb(
    val snapshot: Bcb.SnapshotIndices? = null,
    val carregando: Boolean = false,
    val erro: String? = null,
    val offline: Boolean = false,
    /** Séries que não responderam (mantém o último valor conhecido). */
    val avisoParcial: String? = null,
    val cacheLido: Boolean = false
)

data class EstadoCambioBcb(
    val snapshot: Bcb.SnapshotCambio? = null,
    val carregando: Boolean = false,
    val erro: String? = null,
    val offline: Boolean = false,
    val avisoParcial: String? = null,
    val cacheLido: Boolean = false
)

data class EstadoHistoricoBcb(val carregando: Boolean = false, val erro: String? = null, val pontos: List<Bcb.PontoHistorico> = emptyList())

/**
 * R100 — controle de atualização (cache local com TTL de 6 h). Só consulta quando é CHAMADO: ao abrir a tela de Índices/Câmbio
 * ([atualizarIndices]/[atualizarCambio] com `forcar = false`, que respeita o TTL) ou ao tocar "Atualizar agora" (`forcar = true`).
 * Nunca há agendamento, WorkManager nem alarme. [relogio] injetável. O histórico do gráfico fica só em memória.
 */
class BcbControle(
    private val cache: BcbCache,
    private val client: BcbClient,
    private val relogio: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = Bcb.TTL_MS
) {
    private val _indices = MutableStateFlow(EstadoIndicesBcb())
    val indices: StateFlow<EstadoIndicesBcb> = _indices

    private val _cambio = MutableStateFlow(EstadoCambioBcb())
    val cambio: StateFlow<EstadoCambioBcb> = _cambio

    private val _historico = MutableStateFlow<Map<String, EstadoHistoricoBcb>>(emptyMap())
    val historico: StateFlow<Map<String, EstadoHistoricoBcb>> = _historico

    private suspend fun gravar(bloco: suspend () -> Unit) {
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sem armazenamento: o valor vale só na sessão
        }
    }

    suspend fun carregarCacheIndices() {
        if (_indices.value.cacheLido) return
        val s = try { Bcb.lerSnapshotIndices(cache.lerIndices()) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        _indices.update { it.copy(snapshot = s ?: it.snapshot, cacheLido = true) }
    }

    suspend fun carregarCacheCambio() {
        if (_cambio.value.cacheLido) return
        val s = try { Bcb.lerSnapshotCambio(cache.lerCambio()) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
        _cambio.update { it.copy(snapshot = s ?: it.snapshot, cacheLido = true) }
    }

    /** [forcar] = false respeita o TTL (nenhuma requisição se o cache tem menos de 6 h). [conectado] = false marca "offline" sem requisição. */
    suspend fun atualizarIndices(forcar: Boolean, conectado: Boolean) {
        carregarCacheIndices()
        val st = _indices.value
        if (st.carregando) return
        if (!forcar && !Bcb.cacheExpirado(st.snapshot?.atualizadoEm, relogio(), ttlMs)) return
        if (!conectado) {
            _indices.update { it.copy(offline = true, erro = null) }
            return
        }
        if (!_indices.compareAndSet(st, st.copy(carregando = true, offline = false, erro = null, avisoParcial = null))) return
        try {
            val novo = client.buscarIndices()
            if (novo.vazio) {
                _indices.update { it.copy(carregando = false, erro = novo.falhas.firstOrNull()?.motivo ?: "sem resposta") }
                return
            }
            val junto = Bcb.mesclarSnapshots(_indices.value.snapshot, novo)
            gravar { cache.gravarIndices(Bcb.serializarSnapshotIndices(junto)) }
            val aviso = if (novo.falhas.isEmpty()) null
            else "Sem resposta do Banco Central para: ${novo.falhas.joinToString(", ") { it.fonte.replace(Regex(" \\(SGS.*$"), "") }}. Mantidos os últimos valores conhecidos."
            _indices.update { it.copy(carregando = false, snapshot = junto, avisoParcial = aviso) }
        } catch (e: CancellationException) {
            _indices.update { it.copy(carregando = false) }
            throw e
        } catch (e: Exception) {
            _indices.update { it.copy(carregando = false, erro = e.message?.takeIf { m -> m.isNotBlank() } ?: "falha de rede") }
        }
    }

    suspend fun atualizarCambio(forcar: Boolean, conectado: Boolean) {
        carregarCacheCambio()
        val st = _cambio.value
        if (st.carregando) return
        if (!forcar && !Bcb.cacheExpirado(st.snapshot?.atualizadoEm, relogio(), ttlMs)) return
        if (!conectado) {
            _cambio.update { it.copy(offline = true, erro = null) }
            return
        }
        if (!_cambio.compareAndSet(st, st.copy(carregando = true, offline = false, erro = null, avisoParcial = null))) return
        try {
            val novo = client.buscarCotacoes()
            if (novo.cotacoes.isEmpty()) {
                _cambio.update { it.copy(carregando = false, erro = novo.falhas.firstOrNull()?.motivo ?: "sem resposta") }
                return
            }
            // moedas que falharam agora mantêm a última cotação salva
            val antigas = _cambio.value.snapshot?.cotacoes.orEmpty().filter { c -> novo.cotacoes.none { it.moeda == c.moeda } }
            val junto = Bcb.SnapshotCambio(novo.atualizadoEm, novo.cotacoes + antigas)
            gravar { cache.gravarCambio(Bcb.serializarSnapshotCambio(junto)) }
            val aviso = if (novo.falhas.isEmpty()) null
            else "Sem resposta para: ${novo.falhas.joinToString(", ") { it.fonte }}. Mantidas as últimas cotações salvas, quando houver."
            _cambio.update { it.copy(carregando = false, snapshot = junto, avisoParcial = aviso) }
        } catch (e: CancellationException) {
            _cambio.update { it.copy(carregando = false) }
            throw e
        } catch (e: Exception) {
            _cambio.update { it.copy(carregando = false, erro = e.message?.takeIf { m -> m.isNotBlank() } ?: "falha de rede") }
        }
    }

    /** Histórico de uma moeda: só quando o usuário pede (uma consulta por moeda); fica em memória, não vai ao cache. */
    suspend fun carregarHistorico(moeda: String, conectado: Boolean) {
        val atual = _historico.value[moeda]
        if (atual?.carregando == true) return
        val pontosAntes = atual?.pontos.orEmpty()
        if (!conectado) {
            _historico.update { it + (moeda to EstadoHistoricoBcb(false, "sem conexão", pontosAntes)) }
            return
        }
        _historico.update { it + (moeda to EstadoHistoricoBcb(true, null, pontosAntes)) }
        try {
            val pontos = client.buscarHistorico(moeda)
            if (pontos.isEmpty()) throw BcbErro("sem cotações no período")
            _historico.update { it + (moeda to EstadoHistoricoBcb(false, null, pontos)) }
        } catch (e: CancellationException) {
            _historico.update { it + (moeda to EstadoHistoricoBcb(false, null, pontosAntes)) }
            throw e
        } catch (e: Exception) {
            _historico.update { it + (moeda to EstadoHistoricoBcb(false, e.message?.takeIf { m -> m.isNotBlank() } ?: "falha de rede", pontosAntes)) }
        }
    }
}
