package com.meudinheiro.storage

/**
 * R86 — tudo que o indicador de sincronização precisa saber, já derivado de dados que o app tem em memória
 * (nenhuma leitura extra no Firestore).
 */
data class InfoSync(
    /** Modo nuvem ligado? Em modo local nada é mostrado. */
    val nuvem: Boolean = false,
    val status: SyncStatus = SyncStatus.Desligado,
    /** Documentos com escrita ainda não confirmada pelo servidor. */
    val pendentes: Int = 0,
    /** Há rede (ConnectivityManager)? Só informa; o Firestore continua gravando offline. */
    val rede: Boolean = true,
    /** Registros em quarentena que precisam de atenção (R87). */
    val quarentena: Int = 0,
    /** Última confirmação do servidor (envio confirmado ou dados recebidos); null = ainda nenhuma. */
    val confirmadoEm: Long? = null
)

enum class TomIndicador { OK, ATIVO, AVISO, ERRO, NEUTRO }

/** Texto curto + cor + se o toque abre os detalhes. */
data class Indicador(val texto: String, val tom: TomIndicador, val detalhes: Boolean)

/** R86 — regras puras do indicador (texto e transições), testáveis sem Android. */
object IndicadorSync {

    /** Intervalo da recomposição leve do "há X min" (só enquanto a tela está visível). */
    const val ATUALIZAR_A_CADA_MS = 30_000L

    fun derivar(info: InfoSync, agora: Long): Indicador? {
        if (!info.nuvem || info.status == SyncStatus.Desligado) return null
        val st = info.status
        return when {
            st == SyncStatus.AguardandoLogin -> Indicador("Nuvem: sem login", TomIndicador.NEUTRO, false)
            st is SyncStatus.Erro -> Indicador("Erro ao enviar · toque para ver", TomIndicador.ERRO, true)
            info.pendentes > 0 && !info.rede ->
                Indicador("Sem conexão · ${pendentes(info.pendentes)}", TomIndicador.AVISO, true)
            info.pendentes > 0 -> Indicador("Enviando ${info.pendentes}…", TomIndicador.ATIVO, true)
            info.quarentena > 0 ->
                Indicador(
                    "${info.quarentena} ${if (info.quarentena == 1) "registro" else "registros"} em quarentena · toque para ver",
                    TomIndicador.AVISO, true
                )
            st == SyncStatus.Conectando -> Indicador("Sincronizando…", TomIndicador.ATIVO, true)
            st is SyncStatus.Sincronizado -> {
                val ref = info.confirmadoEm?.let { maxOf(it, st.em) } ?: st.em
                Indicador("Sincronizado ${tempoRelativo(ref, agora)}", TomIndicador.OK, true)
            }
            else -> null
        }
    }

    private fun pendentes(n: Int) = if (n == 1) "1 pendente" else "$n pendentes"

    /** "agora", "há 2 min", "há 3 h", "há 2 d". Relógio atrasado (futuro) conta como "agora". */
    fun tempoRelativo(deMs: Long, agora: Long): String {
        val s = ((agora - deMs).coerceAtLeast(0)) / 1000
        return when {
            s < 60 -> "agora"
            s < 3_600 -> "há ${s / 60} min"
            s < 86_400 -> "há ${s / 3_600} h"
            else -> "há ${s / 86_400} d"
        }
    }

    /** O texto depende do relógio? (só então vale a recomposição periódica). */
    fun dependeDoRelogio(ind: Indicador?): Boolean = ind != null && ind.texto.startsWith("Sincronizado")

    /** Nova resolução de pendências (função pura): quando zera depois de >0, foi confirmação do servidor. */
    fun confirmacao(pendentesAntes: Int, pendentesDepois: Int, doServidor: Boolean, agora: Long, atual: Long?): Long? =
        if (doServidor && pendentesAntes > 0 && pendentesDepois == 0) agora else atual
}
