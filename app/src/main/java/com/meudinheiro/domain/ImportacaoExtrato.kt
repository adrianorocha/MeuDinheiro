package com.meudinheiro.domain

import java.nio.charset.Charset
import java.util.Calendar

/**
 * R63 — importação de extratos no app: validação de arquivo, detecção de arquivos novos de uma pasta,
 * leitura de OFX e heurística de possível duplicidade. Tudo PURO (sem Android), pois o conteúdo vem de fora
 * (compartilhamento, pasta do usuário) e deve ser tratado como dado não confiável.
 */
object ImportacaoExtrato {

    const val MAX_BYTES: Long = 5L * 1024 * 1024
    const val MAX_ARQUIVOS_POR_ENVIO = 20

    /** Extensões aceitas quando o usuário compartilha/abre o arquivo. */
    val EXTENSOES: Set<String> = setOf("ofx", "qfx", "csv", "txt")

    /** Extensões listadas ao varrer a pasta monitorada. */
    val EXTENSOES_PASTA: Set<String> = setOf("ofx", "qfx", "csv")

    private val MIMES = setOf(
        "application/x-ofx", "application/ofx", "text/x-ofx", "text/csv", "text/comma-separated-values",
        "application/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream"
    )

    data class Validacao(val ok: Boolean, val motivo: String? = null)

    /** Só o nome (sem pasta), caracteres seguros e no máximo 80; nunca é usado como caminho. */
    fun sanitizarNome(nome: String?): String {
        val base = (nome ?: "").substringAfterLast('/').substringAfterLast('\\')
        val limpo = base.filter { it.isLetterOrDigit() || it in " ._()-" }.replace(Regex("\\s+"), " ").trim().trimStart('.')
        val cortado = if (limpo.length > 80) limpo.takeLast(80) else limpo
        return cortado.ifBlank { "extrato" }
    }

    fun extensao(nome: String): String = sanitizarNome(nome).substringAfterLast('.', "").lowercase()

    /** Valida nome, tamanho e MIME. A extensão decide; o MIME é só uma dica (provedores variam). */
    fun validarArquivo(nome: String?, tamanho: Long, mime: String?, max: Long = MAX_BYTES): Validacao {
        val limpo = sanitizarNome(nome)
        val ext = extensao(limpo)
        if (ext == "pdf") return Validacao(false, "PDF ainda não é lido. Exporte o extrato em OFX ou CSV.")
        if (ext !in EXTENSOES) return Validacao(false, "Formato não suportado (use OFX, QFX ou CSV).")
        if (tamanho == 0L) return Validacao(false, "Arquivo vazio.")
        if (tamanho > max) return Validacao(false, "Arquivo grande demais (máx. ${max / 1024 / 1024} MB).")
        val tipo = mime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (tipo.isNotEmpty() && tipo !in MIMES) return Validacao(false, "Tipo de arquivo inesperado ($tipo).")
        return Validacao(true)
    }

    fun decodificar(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        val texto = if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
        return texto.removePrefix("﻿")
    }

    fun pareceOfx(texto: String): Boolean {
        val ini = texto.take(2000).uppercase()
        return ini.contains("OFXHEADER") || ini.contains("<OFX") || Regex("<STMTTRN>", RegexOption.IGNORE_CASE).containsMatchIn(texto)
    }

    /** Conferência do conteúdo (não só do nome): sem bytes nulos; .ofx/.qfx precisam parecer OFX. */
    fun conteudoValido(nome: String, bytes: ByteArray): Validacao {
        if (bytes.take(4096).any { it == 0.toByte() }) return Validacao(false, "O conteúdo não é um arquivo de texto.")
        val ext = extensao(nome)
        if ((ext == "ofx" || ext == "qfx") && !pareceOfx(decodificar(bytes))) return Validacao(false, "O arquivo não parece um extrato OFX válido.")
        return Validacao(true)
    }

    // ---------------------------------------------------------------- pasta monitorada

    data class ArquivoPasta(val nome: String, val tamanho: Long, val modificado: Long)

    /** Carimbo estável: nome + tamanho + lastModified. */
    fun carimbo(a: ArquivoPasta): String = "${a.nome}|${a.tamanho}|${a.modificado}"

    /** Arquivos .ofx/.qfx/.csv de tamanho válido cujo carimbo ainda não foi visto (mais antigos primeiro). */
    fun arquivosNovos(arquivos: List<ArquivoPasta>, vistos: Set<String>): List<ArquivoPasta> =
        arquivos
            .filter { extensao(it.nome) in EXTENSOES_PASTA && it.tamanho in 1..MAX_BYTES && carimbo(it) !in vistos }
            .sortedWith(compareBy({ it.modificado }, { it.nome }))

    // ---------------------------------------------------------------- OFX

    data class TransacaoExtrato(val fitid: String, val dataMs: Long, val valor: Double, val descricao: String)

    data class ExtratoLido(
        val transacoes: List<TransacaoExtrato>,
        val acctId: String?,
        val ehCartao: Boolean,
        /** transações sem FITID (não importadas aqui: use o portal, que gera identificador estável) */
        val semFitid: Int,
        val ignoradas: Int,
        val periodo: Pair<Long, Long>?
    )

    private fun entidades(s: String) = s.replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
        .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
        .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
        .replace(Regex("&apos;", RegexOption.IGNORE_CASE), "'")
        .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")

    private fun campo(bloco: String, nome: String): String? {
        val m = Regex("<$nome>\\s*([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(bloco) ?: return null
        return entidades(m.groupValues[1]).trim().ifEmpty { null }
    }

    /** `AAAAMMDD[...]` -> meio-dia LOCAL do dia (o fuso nunca desloca o dia). */
    fun dataOfx(texto: String): Long? {
        val m = Regex("^\\s*(\\d{4})(\\d{2})(\\d{2})").find(texto) ?: return null
        val (a, me, d) = m.destructured
        val c = Calendar.getInstance().apply { clear(); isLenient = false; set(a.toInt(), me.toInt() - 1, d.toInt(), 12, 0, 0) }
        return runCatching { c.timeInMillis }.getOrNull()
    }

    fun valorOfx(s: String): Double? {
        var t = s.filterNot { it.isWhitespace() }
        if (t.contains(',') && !t.contains('.')) t = t.replace(',', '.')
        else if (t.contains(',') && t.contains('.')) t = if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun descricaoOfx(name: String?, memo: String?): String {
        val n = name?.trim().orEmpty()
        val m = memo?.trim().orEmpty()
        if (n.isNotEmpty() && m.isNotEmpty() && !n.equals(m, ignoreCase = true)) {
            return when {
                m.contains(n, ignoreCase = true) -> m
                n.contains(m, ignoreCase = true) -> n
                else -> "$n $m"
            }
        }
        return n.ifEmpty { m }.ifEmpty { "Sem descrição" }
    }

    /** Lê um OFX (v1 SGML ou v2 XML). Conteúdo não confiável: só regex, nada é executado. */
    fun lerOfx(texto: String): ExtratoLido {
        val re = Regex("<STMTTRN>([\\s\\S]*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|</CCSTMTRS>|</STMTRS>|$)", RegexOption.IGNORE_CASE)
        val vistos = mutableMapOf<String, Int>()
        val saida = mutableListOf<TransacaoExtrato>()
        var semFitid = 0
        var ignoradas = 0
        for (m in re.findAll(texto)) {
            val b = m.groupValues[1]
            val data = dataOfx(campo(b, "DTPOSTED").orEmpty())
            val valor = valorOfx(campo(b, "TRNAMT").orEmpty())
            if (data == null || valor == null || valor == 0.0) { ignoradas++; continue }
            var fitid = campo(b, "FITID")?.take(80)
            if (fitid == null) { semFitid++; continue }
            val k = (vistos[fitid] ?: 0) + 1
            vistos[fitid] = k
            if (k > 1) fitid = "$fitid#$k"
            saida.add(TransacaoExtrato(fitid, data, valor, descricaoOfx(campo(b, "NAME"), campo(b, "MEMO")).take(120)))
        }
        val p = DestinoSugerido.pistasDoOfx(texto)
        return ExtratoLido(
            transacoes = saida, acctId = p.acctId, ehCartao = p.ehCartao == true, semFitid = semFitid, ignoradas = ignoradas,
            periodo = if (saida.isEmpty()) null else saida.minOf { it.dataMs } to saida.maxOf { it.dataMs }
        )
    }

    // ---------------------------------------------------------------- duplicidade (R38: idempotência por fitid)

    /** Lançamento já existente, reduzido ao necessário. */
    data class LancamentoRef(val conta: String, val valor: Double, val ehCredito: Boolean, val dataMs: Long, val fitid: String?, val cartaoId: Int?)

    enum class Situacao {
        /** fitid já existe neste destino: nunca duplica (R38) */
        JA_IMPORTADA,

        /** existe lançamento manual parecido (mesmo valor, ±3 dias): provável duplicidade, vem desmarcada */
        POSSIVEL_DUPLICADA,

        NOVA
    }

    private const val DIA_MS = 86_400_000L

    fun situacao(t: TransacaoExtrato, conta: String, existentes: List<LancamentoRef>): Situacao {
        val daConta = existentes.filter { it.conta == conta && it.cartaoId == null }
        if (daConta.any { it.fitid == t.fitid }) return Situacao.JA_IMPORTADA
        val credito = t.valor > 0
        val cents = Math.round(Math.abs(t.valor) * 100)
        val parecido = daConta.any {
            it.fitid == null && it.ehCredito == credito && Math.round(it.valor * 100) == cents && Math.abs(it.dataMs - t.dataMs) <= 3 * DIA_MS
        }
        return if (parecido) Situacao.POSSIVEL_DUPLICADA else Situacao.NOVA
    }
}
