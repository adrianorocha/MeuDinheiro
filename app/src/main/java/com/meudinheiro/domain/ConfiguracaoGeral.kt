package com.meudinheiro.domain

/**
 * R57 — Configurações sincronizadas (documento único `configuracoes/geral`). Funções puras; espelham
 * `portal/src/lib/finance/configuracoes.ts` (mesmos cenários em `ConfiguracaoGeralTest` e `configuracoes.test.ts`).
 * O local continua sendo a fonte imediata; a nuvem só sobrescreve se for MAIS NOVA.
 */
data class ConfigGeral(
    /** R54 — nulo = nunca configurado. */
    val painel: Painel503020.Config? = null,
    /** R51 — nulo = padrão (fim do mês). */
    val limiteDiarioAteReceita: Boolean? = null,
    /** epoch ms da última alteração do usuário; nulo/0 = nunca alterada. */
    val atualizadoEm: Long? = null,
    /** R61 — escolhas do relatório de IR (classe por categoria, limite de educação…); nulo = padrões. */
    val ir: ImpostoRenda.Config? = null
) {
    /** Carimbo efetivo (nulo, 0 ou negativo = 0). */
    val carimbo: Long get() = atualizadoEm?.takeIf { it > 0 } ?: 0L

    /** Documento do Firestore/backup: campos ausentes não são gravados. */
    fun toDoc(): Map<String, Any?> = buildMap {
        painel?.let { c ->
            put(
                "painel503020",
                mapOf(
                    "metas" to mapOf("necessidades" to c.metas.necessidades, "desejos" to c.metas.desejos, "poupanca" to c.metas.poupanca),
                    "mesesReserva" to c.mesesReserva,
                    "overrides" to c.overrides.toSortedMap().mapValues { it.value.name }
                )
            )
        }
        limiteDiarioAteReceita?.let { put("limiteDiarioAteReceita", it) }
        ir?.let { put("ir", it.toDoc()) }
        if (carimbo > 0) put("atualizadoEm", carimbo)
    }

    enum class Acao { NADA, APLICAR_NUVEM, ENVIAR_LOCAL }

    companion object {

        /** Quem tem `atualizadoEm` MAIOR vence. Empate ou nuvem sem data = a nuvem NÃO sobrescreve o local. */
        fun decidir(local: ConfigGeral?, nuvem: ConfigGeral?): Acao {
            val l = local?.carimbo ?: 0L
            val n = nuvem?.carimbo ?: 0L
            return when {
                n > l -> Acao.APLICAR_NUVEM
                l > n -> Acao.ENVIAR_LOCAL
                else -> Acao.NADA
            }
        }

        /** Resultado da mesclagem: a configuração que passa a valer e o que fazer com a nuvem. */
        fun mesclar(local: ConfigGeral?, nuvem: ConfigGeral?): Pair<ConfigGeral, Acao> {
            val acao = decidir(local, nuvem)
            return (if (acao == Acao.APLICAR_NUVEM) nuvem!! else local ?: ConfigGeral()) to acao
        }

        /** Aplica uma alteração do usuário, carimbando; o carimbo nunca volta no tempo (relógio atrasado). */
        fun alterar(
            atual: ConfigGeral,
            agora: Long,
            painel: Painel503020.Config? = atual.painel,
            limite: Boolean? = atual.limiteDiarioAteReceita,
            ir: ImpostoRenda.Config? = atual.ir
        ): ConfigGeral = ConfigGeral(painel, limite, maxOf(agora, atual.carimbo + 1), ir)

        private fun inteiro(v: Any?): Int? =
            (v as? Number)?.toDouble()?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()

        /** Tolerante: aceita qualquer coisa (documento da nuvem, backup antigo ou lixo) e devolve uma configuração válida. */
        fun deDoc(doc: Map<*, *>?): ConfigGeral {
            if (doc == null) return ConfigGeral()
            val p = doc["painel503020"] as? Map<*, *>
            val painel = p?.let {
                val m = it["metas"] as? Map<*, *>
                val ov = HashMap<String, Painel503020.Grupo>()
                (it["overrides"] as? Map<*, *>)?.forEach { (k, v) ->
                    val g = Painel503020.Grupo.entries.firstOrNull { g -> g.name == v }
                    if (k is String && g != null) ov[k] = g
                }
                Painel503020.normalizarConfig(
                    inteiro(m?.get("necessidades")), inteiro(m?.get("desejos")), inteiro(m?.get("poupanca")),
                    inteiro(it["mesesReserva"]), ov
                )
            }
            val ts = (doc["atualizadoEm"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 }?.toLong()
            val ir = (doc["ir"] as? Map<*, *>)?.let { ImpostoRenda.Config.deDoc(it) }
            return ConfigGeral(painel, doc["limiteDiarioAteReceita"] as? Boolean, ts, ir)
        }
    }
}
