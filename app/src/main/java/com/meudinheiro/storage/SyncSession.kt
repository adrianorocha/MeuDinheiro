package com.meudinheiro.storage

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.SyncMeta
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.security.MessageDigest

/** Como resolver diferenças entre o celular e a nuvem. */
enum class ModoSync {
    /** Sincronização normal em 3 vias (padrão). Em conflito, a nuvem prevalece. */
    MESCLAR,

    /** O celular manda: envia tudo e apaga da nuvem o que não existe no celular. */
    CELULAR_VENCE,

    /** A nuvem manda: baixa tudo e apaga do celular o que não existe na nuvem. */
    NUVEM_VENCE
}

/**
 * Uma sessão de sincronização para um usuário (uid). Mantém em memória o estado da nuvem (alimentado
 * por listeners do Firestore — só os deltas são cobrados) e reconcilia com o Room em 3 vias usando a
 * tabela `sync_meta` (hash do último estado sincronizado de cada documento).
 *
 * Regras de segurança contra perda de dados:
 *  - nada é apagado localmente por "ausência na nuvem" enquanto a coleção não tiver sido confirmada pelo
 *    servidor (um cache vazio offline não significa que a nuvem está vazia);
 *  - documentos com escrita pendente (ainda não confirmada) são ignorados na reconciliação;
 *  - campos derivados (saldo, limite…) são recalculados localmente e não geram sincronização.
 */
class SyncSession(
    private val uid: String,
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val repository: MainRepository,
    private val aoMudarEstado: (SyncStatus) -> Unit,
    /** Chamado (em qualquer thread) quando a sessão acabou de enviar escritas ao Firestore. */
    private val aoEnviar: () -> Unit = {},
    /** R87 — registros que a nuvem recusou (isolados para não travar o envio dos demais). */
    private val quarentena: Quarentena = Quarentena(object : PersistenciaQuarentena {
        override fun ler(): String? = null
        override fun gravar(texto: String) = Unit
    }),
    /** R86 — (documentos com escrita pendente, instante da última confirmação do servidor). Chamado em qualquer thread. */
    private val aoMudarEnvio: (Int, Long?) -> Unit = { _, _ -> }
) {
    private sealed interface Evento {
        data class Snapshot(val colecao: String, val mudancas: List<Mudanca>, val doServidor: Boolean) : Evento
        data object LocalMudou : Evento
        data class Pedido(val modo: ModoSync, val concluido: CompletableDeferred<Unit>) : Evento
        data class Falha(val erro: Throwable) : Evento

        /** R87 — um lote terminou de ser enviado (com ou sem registros isolados / falha). */
        data class LoteResolvido(val resultado: IsolamentoLote.Resultado) : Evento
    }

    private data class Mudanca(val id: String, val removido: Boolean, val doc: Doc?, val pendente: Boolean)

    private val catalogo = FirestoreMapper.catalogo(db)
    private val metaDao = db.syncMetaDao()
    private val eventos = Channel<Evento>(Channel.UNLIMITED)

    private val remoto = catalogo.associate { it.nome to HashMap<String, Doc>() }
    private val pendentes = catalogo.associate { it.nome to HashSet<String>() }
    private val confirmadas = HashSet<String>()

    /** Hash do que esta sessão enviou e ainda não foi "carimbado" no histórico (ver [executar]). */
    private val ultimoEnviado = HashMap<String, String>()

    /** Contador de mudanças locais vistas; comparado antes/depois de reconciliar para não perder avisos. */
    @Volatile
    private var geracaoLocal = 0

    @Volatile
    private var pendenciasCache = false

    /** Escopo da sessão (para os envios em lote rodarem sem bloquear o laço de eventos). */
    @Volatile
    private var escopo: CoroutineScope? = null

    @Volatile
    private var confirmadoEm: Long? = null

    /** R86 — documentos aguardando confirmação do servidor (escrita pendente ou enviada e ainda não carimbada). */
    private fun contarPendentes(): Int {
        val chaves = HashSet<String>(ultimoEnviado.keys)
        pendentes.forEach { (col, ids) -> ids.forEach { chaves += "$col/$it" } }
        return chaves.size
    }

    private fun publicarEnvio() = aoMudarEnvio(contarPendentes(), confirmadoEm)

    /**
     * R85 — há algo a enviar/confirmar? (mudança local ainda não reconciliada, envio não carimbado ou escrita
     * sem confirmação do servidor). Barato: só lê campos em memória.
     */
    val temPendencias: Boolean get() = pendenciasCache

    private fun calcularPendencias() = ultimoEnviado.isNotEmpty() || pendentes.values.any { it.isNotEmpty() }

    /**
     * R85 — reconcilia agora e espera o servidor confirmar as escritas pendentes (até [timeoutMs]).
     * Não lê nada novo da nuvem. @return true se tudo foi confirmado a tempo.
     */
    suspend fun enviarPendentes(timeoutMs: Long): Boolean =
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            pedir(ModoSync.MESCLAR)
            firestore.waitForPendingWrites().await()
            true
        } ?: false

    private fun raiz(colecao: String) = firestore.collection("users").document(uid).collection(colecao)

    /** Pede uma sincronização com o [modo] indicado e espera terminar. */
    suspend fun pedir(modo: ModoSync) {
        val concluido = CompletableDeferred<Unit>()
        eventos.send(Evento.Pedido(modo, concluido))
        concluido.await()
    }

    /** Roda até ser cancelada (mudança de modo, logout…). Lança em falha irrecuperável (ex.: permissão). */
    suspend fun rodar() = coroutineScope {
        val registros = mutableListOf<ListenerRegistration>()
        escopo = this
        try {
            catalogo.forEach { col ->
                registros += raiz(col.nome).addSnapshotListener(MetadataChanges.INCLUDE) { snap, erro ->
                    if (erro != null) {
                        eventos.trySend(Evento.Falha(erro))
                    } else if (snap != null) {
                        val mudancas = snap.documentChanges.map { dc ->
                            Mudanca(
                                id = dc.document.id,
                                removido = dc.type == DocumentChange.Type.REMOVED,
                                doc = dc.document.data.filterKeys { it != CAMPO_ATUALIZADO },
                                pendente = dc.document.metadata.hasPendingWrites()
                            )
                        }
                        eventos.trySend(Evento.Snapshot(col.nome, mudancas, doServidor = !snap.metadata.isFromCache))
                    }
                }
            }

            db.invalidationTracker
                .createFlow(*TABELAS_LOCAIS, emitInitialState = false)
                .onEach { geracaoLocal++; pendenciasCache = true; eventos.trySend(Evento.LocalMudou) }
                .launchIn(this)

            launch {
                // Garante uma reconciliação inicial mesmo que nenhum evento chegue (ex.: nuvem vazia e offline).
                delay(1_500)
                eventos.trySend(Evento.LocalMudou)
            }

            aoMudarEstado(SyncStatus.Conectando)
            laco()
        } finally {
            escopo = null
            registros.forEach { it.remove() }
        }
    }

    private suspend fun laco() {
        var modoPendente = ModoSync.MESCLAR
        val aguardando = mutableListOf<CompletableDeferred<Unit>>()
        while (true) {
            var evento: Evento = eventos.receive()
            // Debounce: junta rajadas de eventos (um lançamento gera vários) numa só reconciliação.
            while (true) {
                when (evento) {
                    is Evento.Snapshot -> aplicarSnapshot(evento)
                    is Evento.Falha -> {
                        // Quem espera um envio (pedir) não pode ficar pendurado numa sessão que vai cair.
                        val erro = evento.erro
                        aguardando.forEach { it.completeExceptionally(erro) }
                        aguardando.clear()
                        throw erro
                    }
                    is Evento.Pedido -> {
                        modoPendente = evento.modo
                        aguardando += evento.concluido
                    }
                    is Evento.LoteResolvido -> {
                        val r = evento.resultado
                        val agora = System.currentTimeMillis()
                        r.recusados.forEach {
                            Log.w(TAG, "Registro recusado, em quarentena: ${it.item.chave}", it.erro)
                            quarentena.registrar(it.item, IsolamentoLote.motivo(it.erro), agora)
                            ultimoEnviado.remove(it.item.chave)
                        }
                        val falha = r.falha
                        if (falha != null) {
                            // Transitório/sistêmico: segue o recuo da sessão (não é culpa de um registro).
                            aguardando.forEach { it.completeExceptionally(falha) }
                            aguardando.clear()
                            throw falha
                        }
                    }
                    Evento.LocalMudou -> Unit
                }
                evento = withTimeoutOrNullCompat(DEBOUNCE_MS) { eventos.receive() } ?: break
            }

            val geracao = geracaoLocal
            try {
                reconciliar(modoPendente)
                pendenciasCache = geracaoLocal != geracao || calcularPendencias()
                publicarEnvio()
                aoMudarEstado(
                    if (confirmadas.size == catalogo.size) SyncStatus.Sincronizado(System.currentTimeMillis())
                    else SyncStatus.Conectando
                )
                aguardando.forEach { it.complete(Unit) }
            } catch (e: Exception) {
                aguardando.forEach { it.completeExceptionally(e) }
                throw e
            } finally {
                aguardando.clear()
                modoPendente = ModoSync.MESCLAR
            }
        }
    }

    private suspend fun <T> withTimeoutOrNullCompat(ms: Long, bloco: suspend () -> T): T? =
        kotlinx.coroutines.withTimeoutOrNull(ms) { bloco() }

    private fun aplicarSnapshot(e: Evento.Snapshot) {
        val docs = remoto.getValue(e.colecao)
        val pend = pendentes.getValue(e.colecao)
        val antes = contarPendentes()
        e.mudancas.forEach { m ->
            if (m.removido) {
                docs.remove(m.id); pend.remove(m.id)
            } else if (m.doc != null) {
                docs[m.id] = m.doc
                if (m.pendente) pend += m.id else pend.remove(m.id)
            }
        }
        if (e.doServidor) confirmadas += e.colecao
        // R86: confirmação do servidor = dados recebidos ou pendências zeradas (sem custo: é o mesmo snapshot).
        val agora = System.currentTimeMillis()
        if (e.doServidor && e.mudancas.any { !it.pendente }) confirmadoEm = agora
        confirmadoEm = IndicadorSync.confirmacao(antes, contarPendentes(), e.doServidor, agora, confirmadoEm)
        publicarEnvio()
    }

    // ------------------------------------------------------------------ reconciliação

    private class Plano {
        val aplicarLocal = LinkedHashMap<String, MutableList<Doc>>()
        val apagarLocal = LinkedHashMap<String, MutableList<String>>()
        val enviar = LinkedHashMap<String, MutableList<Pair<String, Doc>>>()
        val apagarRemoto = LinkedHashMap<String, MutableList<String>>()
        val metaSalvar = mutableListOf<SyncMeta>()
        val metaRemover = mutableListOf<Pair<String, String>>()

        val vazio get() = aplicarLocal.isEmpty() && apagarLocal.isEmpty() && enviar.isEmpty() && apagarRemoto.isEmpty()
    }

    private suspend fun reconciliar(modo: ModoSync) {
        if (modo == ModoSync.CELULAR_VENCE) metaDao.limpar()
        val historicos = metaDao.todos().groupBy { it.colecao }.mapValues { (_, l) -> l.associate { it.docId to it.hash } }
        val plano = Plano()

        for (col in catalogo) {
            val local = col.lerLocal()
            val nuvem = remoto.getValue(col.nome)
            val pend = pendentes.getValue(col.nome)
            val historico = historicos[col.nome].orEmpty()
            val confirmada = col.nome in confirmadas

            val hashesLocais = local.mapValues { hash(col, it.value) }
            val hashesNuvem = nuvem.mapValues { hash(col, it.value) }

            // R87: registro em quarentena que sumiu dos dois lados não precisa mais de acompanhamento.
            quarentena.chaves().filter { it.startsWith(col.nome + "/") }.map { it.removePrefix(col.nome + "/") }
                .filter { it !in hashesLocais && it !in hashesNuvem }
                .forEach { quarentena.remover(col.nome, it) }

            for (id in hashesLocais.keys + hashesNuvem.keys + historico.keys) {
                // R87: se o usuário editou o registro recusado, vale uma nova tentativa.
                hashesLocais[id]?.let { quarentena.liberarSeEditado(col.nome, id, it) }
                val acao = Reconciliador.decidir(
                    local = hashesLocais[id],
                    nuvem = hashesNuvem[id],
                    historico = historico[id],
                    enviado = ultimoEnviado["${col.nome}/$id"],
                    pendente = id in pend,
                    colecaoConfirmada = confirmada,
                    modo = modo
                )
                if (quarentena.bloqueia(col.nome, id) &&
                    acao != Reconciliador.Acao.CARIMBAR && acao != Reconciliador.Acao.ESQUECER
                ) {
                    // Em quarentena: não envia, não apaga e não sobrescreve o dado local (nada é perdido).
                    ultimoEnviado.remove("${col.nome}/$id")
                    continue
                }
                if (acao == Reconciliador.Acao.CARIMBAR || acao == Reconciliador.Acao.ESQUECER ||
                    acao == Reconciliador.Acao.PUXAR || acao == Reconciliador.Acao.APAGAR_LOCAL
                ) {
                    quarentena.remover(col.nome, id) // já bate com a nuvem (ou a nuvem prevaleceu): resolvido
                }
                when (acao) {
                    Reconciliador.Acao.NADA -> Unit
                    Reconciliador.Acao.PUXAR -> plano.puxar(col.nome, id, nuvem.getValue(id), hashesNuvem.getValue(id))
                    Reconciliador.Acao.ENVIAR -> plano.enviar.getOrPut(col.nome) { mutableListOf() } += id to local.getValue(id)
                    Reconciliador.Acao.APAGAR_LOCAL -> {
                        plano.apagarLocal.getOrPut(col.nome) { mutableListOf() } += id
                        plano.metaRemover += col.nome to id
                    }
                    Reconciliador.Acao.APAGAR_REMOTO -> {
                        plano.apagarRemoto.getOrPut(col.nome) { mutableListOf() } += id
                        plano.metaRemover += col.nome to id
                    }
                    Reconciliador.Acao.CARIMBAR -> {
                        ultimoEnviado.remove("${col.nome}/$id")
                        plano.metaSalvar += SyncMeta(col.nome, id, hashesLocais.getValue(id))
                    }
                    Reconciliador.Acao.ESQUECER -> plano.metaRemover += col.nome to id
                }
            }
        }

        if (plano.vazio && plano.metaSalvar.isEmpty() && plano.metaRemover.isEmpty()) return
        executar(plano)
    }

    private fun Plano.puxar(colecao: String, id: String, doc: Doc, hashRemoto: String) {
        aplicarLocal.getOrPut(colecao) { mutableListOf() } += doc
        metaSalvar += SyncMeta(colecao, id, hashRemoto)
    }

    private suspend fun executar(plano: Plano) {
        // 1) Nuvem → celular (uma transação: remoções na ordem inversa das dependências, depois gravações).
        if (plano.aplicarLocal.isNotEmpty() || plano.apagarLocal.isNotEmpty()) {
            db.withTransaction {
                catalogo.asReversed().forEach { col -> plano.apagarLocal[col.nome]?.let { col.apagarLocal(it) } }
                catalogo.forEach { col -> plano.aplicarLocal[col.nome]?.let { col.gravarLocal(it) } }
            }
            repository.recalcularTudo()
        }

        // 2) Celular → nuvem (lotes atômicos de até 400 operações). R87: se um lote for recusado por culpa de
        // registro(s), eles são isolados por bisseção e vão para a quarentena; o resto é enviado normalmente.
        val agora = System.currentTimeMillis()
        val itens = mutableListOf<ItemEnvio>()
        plano.enviar.forEach { (colecao, lista) ->
            val col = catalogo.first { it.nome == colecao }
            lista.forEach { (id, doc) ->
                val h = hash(col, doc)
                itens += ItemEnvio(colecao, id, doc + (CAMPO_ATUALIZADO to agora), h)
                ultimoEnviado["$colecao/$id"] = h
            }
        }
        plano.apagarRemoto.forEach { (colecao, ids) ->
            ids.forEach { id -> itens += ItemEnvio(colecao, id, null, "") }
        }
        val operacoes = itens
        val escopoAtual = escopo
        if (escopoAtual != null) {
            val enviador = IsolamentoLote.Enviador { lote ->
                val batch = firestore.batch()
                lote.forEach { i ->
                    val ref = raiz(i.colecao).document(i.id)
                    if (i.doc != null) batch.set(ref, i.doc) else batch.delete(ref)
                }
                batch.commit().await()
            }
            itens.chunked(TAMANHO_LOTE).forEach { lote ->
                escopoAtual.launch {
                    val resultado = IsolamentoLote.processar(lote, enviador)
                    eventos.trySend(Evento.LoteResolvido(resultado))
                }
            }
        }
        // R85: escritas feitas fora da tela (serviço/receiver/worker) são empurradas na hora pelo WorkManager.
        if (operacoes.isNotEmpty()) aoEnviar()

        // 3) Histórico (hash do último estado sincronizado). Envios NÃO são carimbados aqui: só quando o
        // servidor confirma (snapshot sem pendência e com o mesmo hash → a reconciliação seguinte grava o
        // histórico). Assim uma escrita recusada nunca é confundida com "excluído na nuvem".
        plano.metaRemover.forEach { (c, id) -> metaDao.remover(c, id) }
        if (plano.metaSalvar.isNotEmpty()) metaDao.salvar(plano.metaSalvar.distinctBy { it.colecao to it.docId })
    }

    // ------------------------------------------------------------------ util

    private fun hash(col: Colecao, doc: Doc): String {
        val canonico = doc.entries
            .filter { it.key != CAMPO_ATUALIZADO && it.key !in col.derivados }
            .filterNot { it.key in col.ignorarSeVazio && (it.value == null || (it.value as? Collection<*>)?.isEmpty() == true) }
            .sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k=${canonizar(v)}" }
        return MessageDigest.getInstance("SHA-256").digest(canonico.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun canonizar(v: Any?): String = when (v) {
        null -> "∅"
        is Number -> BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
        else -> v.toString()
    }

    companion object {
        private const val TAG = "SyncSession"
        const val CAMPO_ATUALIZADO = "atualizadoEm"
        private const val DEBOUNCE_MS = 700L
        private const val TAMANHO_LOTE = 400

        private val TABELAS_LOCAIS = arrayOf(
            "contasaldo", "cartoes", "categorias", "despesas", "despesas_fixas", "orcamentos",
            "metas", "investimentos", "transferencias_agendadas", "patrimonio_historico", "transacoes", "lixeira",
            "regras_categoria", "projetos", "dividas", "pessoas", "acertos"
        )

        /** Mensagem amigável para erros comuns do Firestore. */
        fun descreverErro(e: Throwable): String = when ((e as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permissão negada no Firestore. Publique as regras de segurança (firestore.rules) e confirme o login."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Sem conexão com o Firestore. Os dados continuam salvos no celular."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "O Firestore não está configurado neste projeto (crie o banco de dados no console do Firebase)."
            else -> e.message ?: "Falha na sincronização."
        }
    }
}
