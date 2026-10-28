package com.meudinheiro.storage

/**
 * Decisão de sincronização de UM documento (celular × nuvem × último estado sincronizado), sem I/O.
 * Entradas são hashes do conteúdo (`null` = o documento não existe naquele lado).
 */
object Reconciliador {

    enum class Acao {
        /** Nada a fazer. */
        NADA,

        /** Aplicar a versão da nuvem no celular (e registrar o hash). */
        PUXAR,

        /** Enviar a versão do celular para a nuvem. */
        ENVIAR,

        /** Foi excluído na nuvem: excluir no celular. */
        APAGAR_LOCAL,

        /** Foi excluído no celular: excluir na nuvem. */
        APAGAR_REMOTO,

        /** Os dois lados já são iguais: apenas registrar o hash como "sincronizado". */
        CARIMBAR,

        /** Sumiu dos dois lados: esquecer o histórico. */
        ESQUECER
    }

    /**
     * @param local hash local; @param nuvem hash na nuvem; @param historico hash gravado na última sincronização
     * @param enviado hash do que esta sessão acabou de enviar e a nuvem ainda não confirmou
     * @param pendente há escrita ainda não confirmada pelo servidor para este documento
     * @param colecaoConfirmada o servidor já respondeu para a coleção (cache vazio offline NÃO prova ausência)
     */
    fun decidir(
        local: String?,
        nuvem: String?,
        historico: String?,
        enviado: String?,
        pendente: Boolean,
        colecaoConfirmada: Boolean,
        modo: ModoSync
    ): Acao {
        if (pendente) return Acao.NADA
        val referencia = historico ?: enviado

        return when {
            // ---- nos dois lados
            local != null && nuvem != null -> when {
                local == nuvem -> if (historico != local) Acao.CARIMBAR else Acao.NADA
                modo == ModoSync.CELULAR_VENCE -> Acao.ENVIAR
                modo == ModoSync.NUVEM_VENCE -> Acao.PUXAR
                referencia == local -> Acao.PUXAR      // só a nuvem mudou
                referencia == nuvem -> Acao.ENVIAR     // só o celular mudou
                else -> Acao.PUXAR                     // os dois mudaram: a nuvem prevalece
            }

            // ---- só no celular
            local != null -> when {
                modo == ModoSync.CELULAR_VENCE -> Acao.ENVIAR
                modo == ModoSync.NUVEM_VENCE || historico != null ->
                    // Existia na nuvem e sumiu (ou a nuvem manda): só decide com o servidor confirmado.
                    if (colecaoConfirmada) Acao.APAGAR_LOCAL else Acao.NADA
                else -> Acao.ENVIAR                    // novo no celular
            }

            // ---- só na nuvem
            nuvem != null -> when {
                modo == ModoSync.CELULAR_VENCE -> Acao.APAGAR_REMOTO
                modo == ModoSync.NUVEM_VENCE || referencia == null -> Acao.PUXAR   // novo na nuvem
                else -> Acao.APAGAR_REMOTO             // existia (histórico) e foi excluído no celular
            }

            // ---- só no histórico
            else -> if (historico != null) Acao.ESQUECER else Acao.NADA
        }
    }
}
