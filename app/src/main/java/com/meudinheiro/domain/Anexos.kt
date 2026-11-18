package com.meudinheiro.domain

import com.meudinheiro.data.Anexo

/**
 * R60 — regras PURAS de anexos (idênticas ao portal, `lib/finance/anexos.ts`; mesmos cenários em `AnexosTest` e `anexos.test.ts`).
 * Limites: até 5 anexos por lançamento, 5 MB cada, só JPEG, PNG e PDF.
 */
object Anexos {
    const val MAX_ANEXOS = 5
    const val MAX_BYTES = 5L * 1024 * 1024
    const val LADO_MAX_IMAGEM = 1600
    const val QUALIDADE_JPEG = 82
    const val PREFIXO_LOCAL = "local:"
    val TIPOS = listOf("image/jpeg", "image/png", "application/pdf")
    private const val NOME_MAX = 120

    private val REGEX_STORAGE = Regex("""^usuarios/[^/]+/anexos/[^/]+/[^/]+[.](jpg|png|pdf)$""")
    private val REGEX_LOCAL = Regex("""^local:anexos/[^/]+/[^/]+[.](jpg|png|pdf)$""")

    fun extensao(tipo: String): String? = when (tipo.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "application/pdf" -> "pdf"
        else -> null
    }

    fun ehImagem(tipo: String) = tipo.startsWith("image/")
    fun ehLocal(caminho: String) = caminho.startsWith(PREFIXO_LOCAL)

    /** Mensagem de erro (pt-BR) ou `null` se o arquivo pode ser anexado. [quantidadeAtual] = anexos que o lançamento já tem. */
    fun validarNovo(tipo: String, tamanho: Long, quantidadeAtual: Int): String? = when {
        quantidadeAtual >= MAX_ANEXOS -> "Limite de $MAX_ANEXOS anexos por lançamento."
        extensao(tipo) == null -> "Tipo de arquivo não permitido. Use JPEG, PNG ou PDF."
        tamanho <= 0L -> "O arquivo está vazio."
        tamanho > MAX_BYTES -> "Arquivo muito grande (${formatarTamanho(tamanho)}). O limite é 5 MB."
        else -> null
    }

    /** "512 B", "340 KB", "1,2 MB". */
    fun formatarTamanho(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale("pt", "BR"), "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    /** Caminho no Storage: `usuarios/<uid>/anexos/<despesaId>/<id>.<ext>`; `null` para tipo inválido ou parte vazia/com "/". */
    fun caminhoStorage(uid: String, despesaId: Long, id: String, tipo: String): String? {
        val ext = extensao(tipo) ?: return null
        if (uid.isBlank() || id.isBlank() || uid.contains('/') || id.contains('/')) return null
        return "usuarios/$uid/anexos/$despesaId/$id.$ext"
    }

    /** Caminho "local:" (modo sem nuvem). */
    fun caminhoLocal(despesaId: Long, id: String, tipo: String): String? {
        val ext = extensao(tipo) ?: return null
        if (id.isBlank() || id.contains('/')) return null
        return "${PREFIXO_LOCAL}anexos/$despesaId/$id.$ext"
    }

    fun caminhoValido(caminho: String) = REGEX_STORAGE.matches(caminho) || REGEX_LOCAL.matches(caminho)

    /** Nome exibido: sem barras, espaços colapsados, até 120 caracteres; vazio vira "anexo". */
    fun nomeSeguro(nome: String?): String {
        val limpo = (nome ?: "").replace(Regex("""[\\/]"""), "_").replace(Regex("""\s+"""), " ").trim()
        return limpo.take(NOME_MAX).ifBlank { "anexo" }
    }

    /**
     * Saneia a lista vinda de dados antigos/de fora: descarta itens sem id, com tipo inválido ou caminho inválido,
     * repetidos (mesmo id; vale o primeiro) e o que passar de 5. `null`/ausente = lista vazia.
     */
    fun normalizar(lista: List<Anexo?>?): List<Anexo> {
        val vistos = HashSet<String>()
        val saida = ArrayList<Anexo>()
        for (a in lista.orEmpty()) {
            if (a == null) continue
            @Suppress("USELESS_CAST") val id = (a.id as String?)?.trim().orEmpty()
            @Suppress("USELESS_CAST") val tipo = (a.tipo as String?).orEmpty().lowercase()
            @Suppress("USELESS_CAST") val caminho = (a.caminho as String?).orEmpty()
            if (id.isEmpty() || extensao(tipo) == null || !caminhoValido(caminho) || !vistos.add(id)) continue
            saida += Anexo(id, nomeSeguro(a.nome as String?), tipo, a.tamanho.coerceAtLeast(0), caminho, a.criadoEm)
            if (saida.size >= MAX_ANEXOS) break
        }
        return saida
    }

    /** Dimensões após reduzir para que o maior lado seja ≤ [max] (mantém a proporção; nunca amplia; mínimo 1). */
    fun ladoReduzido(largura: Int, altura: Int, max: Int = LADO_MAX_IMAGEM): Pair<Int, Int> {
        if (largura <= 0 || altura <= 0) return 1 to 1
        val maior = maxOf(largura, altura)
        if (maior <= max) return largura to altura
        val f = max.toDouble() / maior
        return Math.max(1, Math.round(largura * f).toInt()) to Math.max(1, Math.round(altura * f).toInt())
    }

    /** Escala inteira (potência de 2) para `inSampleSize` na decodificação, sem deixar o lado menor que [max]. */
    fun amostragem(largura: Int, altura: Int, max: Int = LADO_MAX_IMAGEM): Int {
        var s = 1
        var maior = maxOf(largura, altura)
        while (maior / 2 >= max) { maior /= 2; s *= 2 }
        return s
    }
}
