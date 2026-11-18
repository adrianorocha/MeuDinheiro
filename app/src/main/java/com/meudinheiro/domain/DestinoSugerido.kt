package com.meudinheiro.domain

import java.text.Normalizer

/**
 * R63 — identificação automática do destino (conta/cartão) de um extrato, heurística PURA.
 * Espelho de `portal/src/lib/conciliacao/destino-sugerido.ts` (mesmas regras e mesmos casos de teste).
 */
object DestinoSugerido {

    data class ContaRef(val id: Int, val banco: String, val agencia: String, val conta: String)
    data class CartaoRef(val id: Int, val nome: String, val finalCartao: String, val contaId: Int, val cartaoPrincipalId: Int?)

    sealed interface Destino {
        data class Conta(val conta: String) : Destino
        data class Cartao(val cartaoId: Int) : Destino
    }

    /** [ehCartao] nulo = desconhecido (CSV/PDF). */
    data class Pistas(
        val acctId: String? = null,
        val bankId: String? = null,
        val org: String? = null,
        val ehCartao: Boolean? = null,
        val nomeArquivo: String? = null,
        val cabecalho: String? = null
    )

    data class Candidato(val destino: Destino, val score: Int, val motivo: String)
    enum class Estado { UNICO, AMBIGUO, NENHUM }
    data class Identificacao(val estado: Estado, val destino: Destino?, val candidatos: List<Candidato>)

    private val BANCOS: Map<String, List<String>> = mapOf(
        "001" to listOf("banco do brasil", "bb"),
        "033" to listOf("santander"),
        "041" to listOf("banrisul"),
        "070" to listOf("brb"),
        "077" to listOf("inter", "banco inter"),
        "104" to listOf("caixa", "caixa economica"),
        "212" to listOf("original"),
        "237" to listOf("bradesco"),
        "260" to listOf("nubank", "nu pagamentos", "nu"),
        "290" to listOf("pagbank", "pagseguro"),
        "323" to listOf("mercado pago", "mercadopago"),
        "336" to listOf("c6", "c6 bank"),
        "341" to listOf("itau", "itau unibanco"),
        "380" to listOf("picpay"),
        "422" to listOf("safra"),
        "655" to listOf("votorantim", "neon"),
        "748" to listOf("sicredi"),
        "756" to listOf("sicoob")
    )

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun soDigitos(s: String?): String = (s ?: "").filter { it.isDigit() }
    private fun contem(hay: String, palavra: String) = " $hay ".contains(" $palavra ")

    private fun bancosNoTexto(norm: String): Set<String> =
        BANCOS.filter { (_, palavras) -> palavras.any { contem(norm, it) } }.keys

    private val RE_NUMEROS = Regex("\\d[\\d.-]{2,}\\d")
    private fun numerosNoTexto(s: String): List<String> =
        RE_NUMEROS.findAll(s).map { soDigitos(it.value) }.filter { it.length >= 4 }.toList()

    private val RE_FATURA = Regex("\\b(fatura|cartao)\\b")

    /** Pistas de um OFX bruto (BANKID, ORG, ACCTID, cartão); dado não confiável: só regex. */
    fun pistasDoOfx(texto: String, nomeArquivo: String? = null): Pistas {
        fun tag(nome: String): String? =
            Regex("<$nome>\\s*([^<\\r\\n]{1,80})", RegexOption.IGNORE_CASE).find(texto)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        return Pistas(
            acctId = tag("ACCTID"), bankId = tag("BANKID"), org = tag("ORG"),
            ehCartao = Regex("<CREDITCARDMSGSRSV1>|<CCSTMTRS>|<CCACCTFROM>", RegexOption.IGNORE_CASE).containsMatchIn(texto),
            nomeArquivo = nomeArquivo
        )
    }

    /** OFX traz as suas pistas; CSV só nome do arquivo e cabeçalho. */
    fun pistasDoArquivo(nome: String, texto: String): Pistas {
        val ini = texto.take(2000)
        val ehOfx = Regex("OFXHEADER|<OFX|<STMTTRN>", RegexOption.IGNORE_CASE).containsMatchIn(ini)
        return if (ehOfx) pistasDoOfx(texto, nome) else Pistas(nomeArquivo = nome, cabecalho = ini.take(600))
    }

    fun identificar(contas: List<ContaRef>, cartoes: List<CartaoRef>, p: Pistas): Identificacao {
        val textoLivre = normalizar("${p.nomeArquivo.orEmpty()} ${p.cabecalho.orEmpty()}")
        val numerosLivres = numerosNoTexto("${p.nomeArquivo.orEmpty()} ${p.cabecalho.orEmpty()}")
        val ehCartao: Boolean? = p.ehCartao ?: if (RE_FATURA.containsMatchIn(textoLivre)) true else null

        val hayBanco = normalizar("${p.org.orEmpty()} $textoLivre")
        val bancosArq = bancosNoTexto(hayBanco).toMutableSet()
        val cod = soDigitos(p.bankId)
        if (cod.isNotEmpty()) bancosArq.add(cod.padStart(3, '0'))
        fun mesmoBanco(nomeBanco: String): Boolean {
            val n = normalizar(nomeBanco)
            if (n.isEmpty()) return false
            if (bancosNoTexto(n).any { it in bancosArq }) return true
            return n.length >= 3 && contem(hayBanco, n)
        }

        val acct = soDigitos(p.acctId)
        val candidatos = mutableListOf<Candidato>()

        if (ehCartao != true) {
            for (c in contas) {
                val num = soDigitos(c.conta)
                var score = 0
                var motivo = ""
                if (num.length >= 3 && acct.isNotEmpty() && num == acct) { score = 100; motivo = "número da conta igual ao ACCTID" }
                else if (num.length >= 4 && acct.length >= 4 && (acct.endsWith(num) || num.endsWith(acct))) { score = 70; motivo = "número da conta parecido com o ACCTID" }
                else if (num.length >= 4 && numerosLivres.any { it == num }) { score = 60; motivo = "número da conta no nome/cabeçalho do arquivo" }
                if (mesmoBanco(c.banco)) { score += 25; motivo = if (motivo.isNotEmpty()) "$motivo; banco confere" else "banco confere" }
                if (score > 0) candidatos.add(Candidato(Destino.Conta(c.conta), score, motivo))
            }
        }
        if (ehCartao != false) {
            for (k in cartoes) {
                if (k.cartaoPrincipalId != null) continue
                val fim = soDigitos(k.finalCartao)
                var score = 0
                var motivo = ""
                if (fim.length >= 4 && acct.endsWith(fim)) { score = 80; motivo = "final do cartão igual ao do ACCTID" }
                else if (fim.length >= 4 && numerosLivres.any { it == fim || it.endsWith(fim) }) { score = 60; motivo = "final do cartão no nome/cabeçalho do arquivo" }
                val conta = contas.firstOrNull { it.id == k.contaId }
                if (ehCartao == true && (mesmoBanco(k.nome) || (conta != null && mesmoBanco(conta.banco)))) {
                    score += 25; motivo = if (motivo.isNotEmpty()) "$motivo; banco confere" else "banco confere"
                }
                if (score > 0) candidatos.add(Candidato(Destino.Cartao(k.id), score, motivo))
            }
        }

        val ordenados = candidatos.sortedByDescending { it.score }
        if (ordenados.isEmpty()) return Identificacao(Estado.NENHUM, null, ordenados)
        val topo = ordenados[0]
        val segundo = ordenados.getOrNull(1)
        val unico = segundo == null || (topo.score >= 60 && topo.score - segundo.score >= 20)
        return if (unico) Identificacao(Estado.UNICO, topo.destino, ordenados) else Identificacao(Estado.AMBIGUO, null, ordenados)
    }
}
