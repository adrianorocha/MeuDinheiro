package com.meudinheiro.domain

import com.google.gson.Gson
import com.meudinheiro.data.Despesa
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** R66 — ações registradas na trilha local de alterações. */
object AcaoTrilha {
    const val CRIAR = "CRIAR"
    const val EDITAR = "EDITAR"
    const val EXCLUIR = "EXCLUIR"
    const val RESTAURAR_LIXEIRA = "RESTAURAR_LIXEIRA"
    const val PAGAR = "PAGAR"
    const val DESPAGAR = "DESPAGAR"
    const val RESTAURAR_BACKUP = "RESTAURAR_BACKUP"
    val TODAS = listOf(CRIAR, EDITAR, EXCLUIR, RESTAURAR_LIXEIRA, PAGAR, DESPAGAR, RESTAURAR_BACKUP)

    fun rotulo(a: String) = when (a) {
        CRIAR -> "Criado"; EDITAR -> "Editado"; EXCLUIR -> "Excluído"; RESTAURAR_LIXEIRA -> "Restaurado da lixeira"
        PAGAR -> "Marcado como pago"; DESPAGAR -> "Marcado como pendente"; RESTAURAR_BACKUP -> "Backup restaurado"
        else -> a
    }
}

/** Um campo alterado (valores já em texto; só campos não sensíveis). */
data class MudancaCampo(val campo: String, val antigo: String, val novo: String)

/** Uma linha da trilha (JSON-lines). */
data class EntradaTrilha(
    val quando: Long,
    val acao: String,
    val id: Long? = null,
    val descricao: String = "",
    val valor: Double? = null,
    val alteracoes: List<MudancaCampo> = emptyList()
)

/** R66 — construção e filtro (puros) das entradas da trilha. */
object Trilha {
    private val DATA = ThreadLocal.withInitial { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    fun criar(quando: Long, d: Despesa, extra: String? = null) =
        EntradaTrilha(quando, AcaoTrilha.CRIAR, d.id.takeIf { it != 0L }, descricaoCom(d.descricao, extra), d.valor)

    fun excluir(quando: Long, d: Despesa, extra: String? = null) =
        EntradaTrilha(quando, AcaoTrilha.EXCLUIR, d.id, descricaoCom(d.descricao, extra), d.valor)

    fun pago(quando: Long, d: Despesa, pago: Boolean) =
        EntradaTrilha(quando, if (pago) AcaoTrilha.PAGAR else AcaoTrilha.DESPAGAR, d.id, d.descricao, d.valor)

    fun editar(quando: Long, antigo: Despesa, novo: Despesa) =
        EntradaTrilha(quando, AcaoTrilha.EDITAR, novo.id, novo.descricao, novo.valor, diferencas(antigo, novo))

    private fun descricaoCom(desc: String, extra: String?) = if (extra.isNullOrBlank()) desc else "$desc ($extra)"

    /** Campos que mudaram entre [a] e [b] (descrição, valor, categoria, conta, tipo, data, cartão, pago, tags, natureza). */
    fun diferencas(a: Despesa, b: Despesa): List<MudancaCampo> {
        val r = mutableListOf<MudancaCampo>()
        fun cmp(campo: String, x: Any?, y: Any?) { if (x != y) r += MudancaCampo(campo, x?.toString() ?: "", y?.toString() ?: "") }
        cmp("descricao", a.descricao, b.descricao)
        cmp("valor", a.valor, b.valor)
        cmp("categoria", a.categoria, b.categoria)
        cmp("conta", a.conta, b.conta)
        cmp("tipo", a.tipo, b.tipo)
        if (a.data != b.data) r += MudancaCampo("data", DATA.get()!!.format(a.data), DATA.get()!!.format(b.data))
        cmp("cartaoId", a.cartaoId, b.cartaoId)
        cmp("pago", a.pago, b.pago)
        cmp("tags", a.tags.joinToString(","), b.tags.joinToString(","))
        cmp("natureza", a.natureza, b.natureza)
        return r
    }

    /** Filtra por período ([de]..[ate] em ms, inclusive), ação (null = todas) e busca (descrição, campo ou valor). */
    fun filtrar(lista: List<EntradaTrilha>, de: Long? = null, ate: Long? = null, acao: String? = null, busca: String = ""): List<EntradaTrilha> {
        val termo = busca.trim().lowercase()
        return lista.filter { e ->
            (de == null || e.quando >= de) && (ate == null || e.quando <= ate) && (acao == null || e.acao == acao) &&
                (termo.isEmpty() || e.descricao.lowercase().contains(termo) ||
                    e.alteracoes.any { it.campo.lowercase().contains(termo) || it.antigo.lowercase().contains(termo) || it.novo.lowercase().contains(termo) } ||
                    (e.valor?.toString()?.contains(termo) == true))
        }
    }
}

/**
 * R66 — arquivo JSON-lines com no máximo [limite] entradas (descarta as mais antigas). Falha de gravação nunca deve
 * derrubar a operação de quem registra: use [registrarSeguro].
 */
class TrilhaArquivo(private val arquivo: File, private val limite: Int = LIMITE_PADRAO) {
    private val gson = Gson()
    private var contagem: Int
        get() = CONTAGENS[arquivo.absolutePath] ?: -1
        set(v) { CONTAGENS[arquivo.absolutePath] = v }

    fun registrar(e: EntradaTrilha) = synchronized(LOCK) {
        arquivo.parentFile?.mkdirs()
        if (contagem < 0) contagem = linhas().size
        arquivo.appendText(gson.toJson(e) + "\n", Charsets.UTF_8)
        contagem++
        if (contagem > limite) {
            val manter = linhas().takeLast(limite)
            arquivo.writeText(manter.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            contagem = manter.size
        }
    }

    /** Registra sem propagar erro. Retorna se gravou. */
    fun registrarSeguro(e: EntradaTrilha): Boolean = try { registrar(e); true } catch (_: Throwable) { synchronized(LOCK) { contagem = -1 }; false }

    /** Mais recentes primeiro; linhas inválidas são ignoradas. */
    fun ler(): List<EntradaTrilha> = synchronized(LOCK) {
        linhas().mapNotNull { runCatching { gson.fromJson(it, EntradaTrilha::class.java) }.getOrNull() }
            .filter { @Suppress("SENSELESS_COMPARISON") (it != null && it.acao != null) }
            .asReversed()
    }

    fun limpar() = synchronized(LOCK) { arquivo.delete(); contagem = -1 }

    private fun linhas(): List<String> =
        if (arquivo.isFile) arquivo.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()

    companion object {
        const val LIMITE_PADRAO = 2000
        private val LOCK = Any()
        private val CONTAGENS = HashMap<String, Int>() // por caminho: várias instâncias do repositório compartilham o arquivo
    }
}
