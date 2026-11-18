package com.meudinheiro.domain

/**
 * R88 — regras PURAS do push silencioso de sincronização (portal -> app). Sem Android/Firebase: testado na JVM.
 * O FCM traz `data = { tipo: "SYNC", origem: "portal" }`; o app só acorda, deixa os listeners do Firestore receberem o que
 * mudou e encerra. Nada é lido além do que a sessão já lê.
 */
object SinalSync {
    const val TIPO = "SYNC"

    /** Sinais com intervalo menor que isto desde o último processado são descartados (anti-rajada/abuso). */
    const val INTERVALO_MIN_MS = 10_000L

    /** Teto da janela de recebimento. */
    const val JANELA_MAX_MS = 25_000L

    /** Folga depois da sessão confirmar o servidor, para o debounce/reconciliação terminarem de gravar no Room. */
    const val FOLGA_MS = 1_500L

    fun ehSinal(data: Map<String, String>): Boolean = data["tipo"] == TIPO

    /** Só age com push ligado, modo nuvem e usuário logado. */
    fun deveAgir(pushLigado: Boolean, nuvem: Boolean, logado: Boolean): Boolean = pushLigado && nuvem && logado

    /** Anti-rajada: processa se nunca processou ou se passou o intervalo mínimo. Relógio que voltou no tempo = processa. */
    fun passouIntervalo(ultimoMs: Long, agoraMs: Long, minimoMs: Long = INTERVALO_MIN_MS): Boolean =
        ultimoMs <= 0L || agoraMs < ultimoMs || agoraMs - ultimoMs >= minimoMs

    /** App na tela: os listeners já estão entregando as mudanças; não precisa de janela. */
    fun precisaJanela(emPrimeiroPlano: Boolean): Boolean = !emPrimeiroPlano

    /** A janela termina quando a sessão confirmou dados do servidor depois do sinal (ou deu erro/sem login). */
    fun recebeu(sincronizadoEmMs: Long?, sinalMs: Long): Boolean = sincronizadoEmMs != null && sincronizadoEmMs >= sinalMs
}
