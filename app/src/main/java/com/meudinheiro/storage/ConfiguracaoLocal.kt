package com.meudinheiro.storage

import com.google.gson.Gson
import com.meudinheiro.data.AppDatabase
import com.meudinheiro.data.Configuracao
import com.meudinheiro.domain.ConfigGeral
import com.meudinheiro.domain.Painel503020
import com.meudinheiro.funcoes.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * R57 — ponte entre as configurações locais (DataStore: fonte imediata da UI, funciona offline) e a linha única
 * `configuracoes` do Room, que guarda o carimbo `atualizadoEm` (e o JSON do documento) e entra no backup e na
 * sincronização. Sem nuvem tudo segue como antes: só passa a existir também o carimbo local.
 */
class ConfiguracaoLocal(
    private val db: AppDatabase,
    private val prefs: UserPreferences,
    private val agora: () -> Long = { System.currentTimeMillis() }
) {
    private val dao = db.configuracaoDao()

    /** Estado local: valores do DataStore + carimbo da linha Room (0 = nunca alterada pelo usuário). */
    suspend fun atual(): ConfigGeral {
        val carimbo = dao.obterGeral()?.atualizadoEm ?: 0L
        val texto = prefs.painel503020Flow.first()
        return ConfigGeral(
            painel = texto.takeIf { it.isNotBlank() }?.let { Painel503020.desserializar(it) },
            limiteDiarioAteReceita = prefs.limiteDiarioAteReceitaFlow.first(),
            atualizadoEm = carimbo.takeIf { it > 0 }
        )
    }

    /** O usuário acabou de alterar uma configuração: carimba (sempre estritamente maior que o anterior) e grava a linha. */
    suspend fun registrarAlteracao() {
        val atual = atual()
        val novo = ConfigGeral.alterar(atual, agora())
        dao.salvar(Configuracao(json = GSON.toJson(novo.toDoc()), atualizadoEm = novo.carimbo))
    }

    /**
     * Recebeu a configuração da nuvem (ou de um backup): aplica SÓ se for mais nova (empate/nulo = local vence).
     * Devolve a decisão; [ConfigGeral.Acao.ENVIAR_LOCAL] significa que o local é mais novo e deve ser enviado.
     */
    suspend fun aplicar(nuvem: ConfigGeral?): ConfigGeral.Acao {
        val local = atual()
        val (resultado, acao) = ConfigGeral.mesclar(local, nuvem)
        if (acao == ConfigGeral.Acao.APLICAR_NUVEM) {
            resultado.painel?.let { prefs.salvarPainel503020(Painel503020.serializar(it), carimbar = false) }
            resultado.limiteDiarioAteReceita?.let { prefs.saveLimiteDiarioAteReceita(it, carimbar = false) }
            val efetivo = atual().copy(atualizadoEm = resultado.carimbo)
            dao.salvar(Configuracao(json = GSON.toJson(efetivo.toDoc()), atualizadoEm = resultado.carimbo))
        }
        return acao
    }

    /** Documento para backup/Firestore, ou `null` se o usuário nunca alterou nada (nada a levar). */
    suspend fun documentoParaEnvio(): Map<String, Any?>? = atual().takeIf { it.carimbo > 0 }?.toDoc()

    private companion object {
        val GSON = Gson()
    }
}
