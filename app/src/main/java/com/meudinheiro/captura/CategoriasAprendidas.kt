package com.meudinheiro.captura

import android.content.Context
import com.meudinheiro.domain.CategoriaAprendida
import com.meudinheiro.domain.MemoriaCategorias

/**
 * R89 — memória local estabelecimento -> categoria (SharedPreferences privado `categorias_aprendidas`, fora do backup e
 * da sincronização; ver data_extraction_rules/backup_rules). Só o estabelecimento normalizado e a categoria são guardados.
 */
object CategoriasAprendidas {
    const val ARQUIVO = "categorias_aprendidas"
    private const val K_DADOS = "dados"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    @Synchronized
    fun memoria(ctx: Context): MemoriaCategorias = CategoriaAprendida.desserializar(prefs(ctx).getString(K_DADOS, null))

    /** Registra a categoria confirmada para o estabelecimento (ignora despesa sem identificação ou sem categoria). */
    @Synchronized
    fun aprender(ctx: Context, descricao: String, categoria: String, agora: Long = System.currentTimeMillis()) {
        val atual = memoria(ctx)
        val nova = CategoriaAprendida.aprender(atual, descricao, categoria, agora)
        if (nova !== atual && nova != atual) prefs(ctx).edit().putString(K_DADOS, CategoriaAprendida.serializar(nova)).apply()
    }

    @Synchronized
    fun esquecer(ctx: Context) { prefs(ctx).edit().remove(K_DADOS).apply() }
}
