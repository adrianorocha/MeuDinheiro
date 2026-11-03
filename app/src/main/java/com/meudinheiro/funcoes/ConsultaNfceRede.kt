package com.meudinheiro.funcoes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Consulta opcional (R49) do total de uma NFC-e v2, cujo QR só traz a chave. O endereço vem de um QR
 * (dado não confiável), então: só https, host terminando em `.gov.br`, sem credenciais na URL, sem
 * cookies nem dado do usuário na requisição, timeout curto, resposta limitada e redirecionamentos
 * revalidados um a um (nunca saem de `.gov.br`).
 */
object ConsultaNfceRede {

    const val TIMEOUT_MS = 8_000
    const val MAX_BYTES = 512 * 1024
    private const val MAX_REDIRECTS = 3
    private const val PRAZO_TOTAL_MS = 15_000L

    fun urlPermitida(url: String): Boolean = runCatching {
        if (url.length > 2048) return false
        val u = URI(url.trim())
        val host = u.host?.lowercase() ?: return false
        u.scheme.equals("https", ignoreCase = true) &&
            u.userInfo == null &&
            (u.port == -1 || u.port == 443) &&
            host.endsWith(".gov.br") && host.length > ".gov.br".length &&
            host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }.getOrDefault(false)

    /** Resolve um `Location` contra a URL atual; devolve null se o resultado não for permitido. */
    fun resolverRedirecionamento(atual: String, location: String): String? = runCatching {
        URI(atual).resolve(location.trim()).toString().takeIf { urlPermitida(it) }
    }.getOrNull()

    /** Busca e interpreta; null em qualquer falha (sem internet, bloqueio, layout desconhecido...). */
    suspend fun consultar(url: String): LeitorCupomFiscal.ConsultaHtml? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(PRAZO_TOTAL_MS) {
            runCatching {
                baixar(url)?.let { LeitorCupomFiscal.extrairDeHtml(it) }
            }.getOrNull()
        }
    }

    private fun baixar(urlInicial: String): String? {
        var atual = urlInicial
        repeat(MAX_REDIRECTS + 1) {
            if (!urlPermitida(atual)) return null
            val conn = URL(atual).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.setRequestProperty("Accept", "text/html")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    atual = resolverRedirecionamento(atual, loc) ?: return null
                    return@repeat
                }
                if (code != 200) return null
                val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(conn.contentType.orEmpty())?.groupValues?.get(1)
                    ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                val buf = ByteArrayOutputStream()
                conn.inputStream.use { ins ->
                    val tmp = ByteArray(8192)
                    while (buf.size() < MAX_BYTES) {
                        val n = ins.read(tmp, 0, minOf(tmp.size, MAX_BYTES - buf.size()))
                        if (n < 0) break
                        buf.write(tmp, 0, n)
                    }
                }
                return String(buf.toByteArray(), charset)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}
