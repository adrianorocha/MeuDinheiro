package com.meudinheiro.captura

import android.content.Context
import com.meudinheiro.domain.NotificacaoBancaria

/**
 * R64 — configuração local da captura de notificações (SharedPreferences privado, fora do backup do sistema).
 * DESLIGADA por padrão. Nada aqui sai do aparelho.
 */
object CapturaConfig {
    const val ARQUIVO = "captura_notificacoes"
    private const val K_ATIVO = "ativo"
    private const val K_PACOTES = "pacotes"
    private const val K_VISTOS = "vistos"
    private const val MAX_VISTOS = 40
    private const val MAX_PACOTES = 60

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun ativo(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ATIVO, false)
    fun definirAtivo(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean(K_ATIVO, v).apply() }

    /** Lista permitida (padrão até o usuário editar). */
    fun permitidos(ctx: Context): List<String> =
        prefs(ctx).getStringSet(K_PACOTES, null)?.sorted() ?: NotificacaoBancaria.PACOTES_PADRAO

    fun salvarPermitidos(ctx: Context, lista: Collection<String>) {
        val limpa = lista.map { it.trim() }.filter { NotificacaoBancaria.pacoteValido(it) }.toSortedSet().take(MAX_PACOTES).toSet()
        prefs(ctx).edit().putStringSet(K_PACOTES, limpa).apply()
    }

    fun marcar(ctx: Context, pacote: String, permitir: Boolean) {
        val atual = permitidos(ctx).toMutableSet()
        if (permitir) atual.add(pacote) else atual.remove(pacote)
        salvarPermitidos(ctx, atual)
    }

    fun restaurarPadrao(ctx: Context) { prefs(ctx).edit().remove(K_PACOTES).apply() }

    /**
     * Apps FORA da lista que postaram notificação (só o nome do pacote, sem conteúdo), para o usuário poder adicioná-los.
     * Só é preenchida com a captura ligada; pode ser limpa a qualquer momento.
     */
    @Synchronized
    fun registrarVisto(ctx: Context, pacote: String) {
        if (!NotificacaoBancaria.pacoteValido(pacote) || pacote == ctx.packageName) return
        val atual = prefs(ctx).getStringSet(K_VISTOS, emptySet()).orEmpty()
        if (pacote in atual) return
        prefs(ctx).edit().putStringSet(K_VISTOS, (atual + pacote).sorted().takeLast(MAX_VISTOS).toSet()).apply()
    }

    fun vistos(ctx: Context): List<String> = prefs(ctx).getStringSet(K_VISTOS, emptySet()).orEmpty().sorted()
    fun limparVistos(ctx: Context) { prefs(ctx).edit().remove(K_VISTOS).apply() }
}
