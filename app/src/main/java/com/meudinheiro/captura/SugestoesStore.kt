package com.meudinheiro.captura

import android.content.Context
import com.meudinheiro.domain.NotificacaoBancaria
import com.meudinheiro.domain.NotificacaoBancaria.Estado
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import java.io.File

/**
 * R64 — fila local de sugestões (JSON em `filesDir/captura_sugestoes.json`, armazenamento privado, fora do backup).
 * Guarda só os campos extraídos; limite de 200 e validade de 7 dias (aplicados a cada leitura/escrita).
 * Nada de rede. Acesso serializado (serviço, receiver e telas usam o mesmo objeto).
 */
object SugestoesStore {
    private const val NOME = "captura_sugestoes.json"

    private fun arquivo(ctx: Context) = File(ctx.applicationContext.filesDir, NOME)

    @Synchronized
    private fun ler(ctx: Context, agora: Long): List<SugestaoGuardada> {
        val f = arquivo(ctx)
        val txt = if (f.exists()) runCatching { f.readText() }.getOrNull() else null
        return NotificacaoBancaria.manter(NotificacaoBancaria.desserializar(txt), agora)
    }

    @Synchronized
    private fun gravar(ctx: Context, lista: List<SugestaoGuardada>) {
        val f = arquivo(ctx)
        if (lista.isEmpty()) { f.delete(); return }
        val tmp = File(f.parentFile, "$NOME.tmp")
        tmp.writeText(NotificacaoBancaria.serializar(lista))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun todas(ctx: Context, agora: Long = System.currentTimeMillis()): List<SugestaoGuardada> = ler(ctx, agora)

    fun pendentes(ctx: Context, agora: Long = System.currentTimeMillis()): List<SugestaoGuardada> =
        ler(ctx, agora).filter { it.estado == Estado.PENDENTE }

    fun obter(ctx: Context, id: String): SugestaoGuardada? = ler(ctx, System.currentTimeMillis()).firstOrNull { it.id == id }

    /** Acrescenta (já expurgando expiradas e respeitando o limite). */
    @Synchronized
    fun adicionar(ctx: Context, s: SugestaoGuardada, agora: Long = System.currentTimeMillis()) {
        gravar(ctx, NotificacaoBancaria.manter(ler(ctx, agora) + s, agora))
    }

    @Synchronized
    fun marcar(ctx: Context, id: String, estado: Estado) {
        val agora = System.currentTimeMillis()
        gravar(ctx, ler(ctx, agora).map { if (it.id == id) it.copy(estado = estado) else it })
    }

    @Synchronized
    fun remover(ctx: Context, id: String) {
        gravar(ctx, ler(ctx, System.currentTimeMillis()).filterNot { it.id == id })
    }

    /** "Apagar sugestões": esvazia a fila inteira. */
    @Synchronized
    fun apagarTudo(ctx: Context) { arquivo(ctx).delete() }
}
