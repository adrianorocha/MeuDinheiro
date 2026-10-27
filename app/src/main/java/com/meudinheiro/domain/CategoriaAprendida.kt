package com.meudinheiro.domain

import com.google.gson.Gson
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.RegraCategoria

/** Memória do aprendizado: chave normalizada -> categoria. */
typealias MemoriaCategorias = Map<String, CategoriaAprendida.Entrada>

/**
 * R89 — aprendizado de categoria por estabelecimento (sugestões da captura de notificações, R64).
 * TUDO aqui é puro (sem Android): normalização do estabelecimento, memória estabelecimento -> categoria e a escolha
 * da categoria inicial. A memória é local (SharedPreferences, fora do backup e da sincronização).
 */
object CategoriaAprendida {

    const val MAX_ENTRADAS = 300
    private const val MAX_TOKENS = 3

    /** Ruído de maquininha/adquirente/intermediário e conectivos: não identificam o estabelecimento. */
    private val RUIDO = setOf(
        "pag", "pagseguro", "pagamento", "pgto", "pg", "mp", "mercadopago", "sumup", "ton", "stone", "cielo", "getnet",
        "rede", "ifd", "zig", "pix", "compra", "debito", "credito", "ltda", "me", "epp", "eireli", "sa", "cia", "com",
        "de", "da", "do", "das", "dos", "e", "em", "no", "na", "para", "br", "brasil", "loja", "filial", "matriz"
    )

    /**
     * "PAG*Padaria 123" -> "padaria". Minúsculas, sem acento, só letras; descarta tokens com número, ruído de maquininha,
     * conectivos e letras soltas; mantém até [MAX_TOKENS] palavras. Vazio = sem identificação (nada é aprendido).
     */
    fun normalizarEstabelecimento(descricao: String): String {
        val base = Texto.normalizar(descricao)
        val tokens = base.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return tokens
            .filter { t -> t.none { it.isDigit() } && t.length >= 2 && t !in RUIDO }
            .take(MAX_TOKENS)
            .joinToString(" ")
    }

    data class Entrada(val categoria: String, val vezes: Int, val ultimoUso: Long)

    /**
     * Registra que [categoria] foi confirmada para [descricao]. Mesma categoria: soma uma vez; outra categoria: a escolha
     * mais recente vence (reinicia a contagem). Sem chave ou categoria vazia: não muda nada. Limita a [MAX_ENTRADAS]
     * descartando as menos recentes.
     */
    fun aprender(memoria: MemoriaCategorias, descricao: String, categoria: String, agora: Long): MemoriaCategorias {
        val chave = normalizarEstabelecimento(descricao)
        val cat = categoria.trim()
        if (chave.isEmpty() || cat.isEmpty()) return memoria
        val atual = memoria[chave]
        val nova = if (atual != null && Texto.normalizar(atual.categoria) == Texto.normalizar(cat))
            atual.copy(categoria = cat, vezes = atual.vezes + 1, ultimoUso = agora)
        else Entrada(cat, 1, agora)
        val mapa = memoria.toMutableMap().also { it[chave] = nova }
        if (mapa.size <= MAX_ENTRADAS) return mapa
        return mapa.entries.sortedByDescending { it.value.ultimoUso }.take(MAX_ENTRADAS).associate { it.key to it.value }
    }

    /**
     * Categoria aprendida para [descricao]: casamento exato da chave; senão a chave aprendida cujas palavras são o
     * começo das palavras da nova (ou o inverso), preferindo a mais longa e, no empate, a mais recente.
     */
    fun buscar(memoria: MemoriaCategorias, descricao: String): String? {
        val chave = normalizarEstabelecimento(descricao)
        if (chave.isEmpty()) return null
        memoria[chave]?.let { return it.categoria }
        val tk = chave.split(' ')
        return memoria.entries
            .filter { (k, _) ->
                val ta = k.split(' ')
                val n = minOf(ta.size, tk.size)
                n >= 1 && ta.take(n) == tk.take(n)
            }
            .sortedWith(compareByDescending<Map.Entry<String, Entrada>> { it.key.split(' ').size }.thenByDescending { it.value.ultimoUso })
            .firstOrNull()?.value?.categoria
    }

    enum class Origem { REGRA, APRENDIDA, HISTORICO, NENHUMA }

    data class Escolha(val categoria: String?, val origem: Origem)

    /**
     * Categoria inicial de uma sugestão de DESPESA. [descricao] = estabelecimento extraído (chave do aprendizado);
     * [titulo] = texto do lançamento (para regras e histórico). Ordem: regra explícita (R55) > aprendida > histórico (R21). Só vale
     * categoria que existe em [categoriasValidas] (comparação sem acento/caixa; devolve o nome como cadastrado).
     */
    fun escolher(
        descricao: String,
        titulo: String = descricao,
        conta: String?,
        regras: List<RegraCategoria>,
        memoria: MemoriaCategorias,
        historico: List<Despesa>,
        categoriasValidas: List<String>
    ): Escolha {
        fun existente(nome: String?): String? =
            nome?.let { n -> categoriasValidas.firstOrNull { Texto.normalizar(it) == Texto.normalizar(n) } }
        existente(Regras.categoria(regras, Regras.Entrada(titulo, conta, "DEBITO")))?.let { return Escolha(it, Origem.REGRA) }
        existente(buscar(memoria, descricao))?.let { return Escolha(it, Origem.APRENDIDA) }
        existente(Analises.sugerirCategoria(titulo, historico))?.let { return Escolha(it, Origem.HISTORICO) }
        return Escolha(null, Origem.NENHUMA)
    }

    // ------------------------------------------------------------------ persistência (JSON tolerante)

    private class Arquivo(val versao: Int = 1, val itens: Map<String, Entrada> = emptyMap())

    private val gson = Gson()

    fun serializar(memoria: MemoriaCategorias): String = gson.toJson(Arquivo(1, memoria))

    fun desserializar(json: String?): MemoriaCategorias {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val a = gson.fromJson(json, Arquivo::class.java) ?: return emptyMap()
            @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
            val itens: Map<String, Entrada?> = (a.itens ?: emptyMap())
            itens.mapNotNull { (k, v) ->
                runCatching { if (k.isNotBlank() && v != null && v.categoria.isNotBlank()) k to v else null }.getOrNull()
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
