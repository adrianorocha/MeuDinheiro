package com.meudinheiro.funcoes

import java.math.BigDecimal
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** De onde veio o código lido (R49). */
enum class OrigemCupom(val rotulo: String) {
    SAT("CF-e SAT"),
    NFCE_V1("NFC-e (QR v1)"),
    NFCE_V2("NFC-e (QR v2)"),
    BOLETO("Boleto / arrecadação"),
    PIX("PIX (BR Code)")
}

/**
 * Resultado da interpretação de um código lido. Todo o conteúdo vem de um QR/código de barras
 * (DADO NÃO CONFIÁVEL): aqui só se extrai texto/números, nada é executado ou aberto.
 *
 * @property valorCentavos total em centavos (sempre > 0) ou null quando o código não traz o valor.
 * @property chave chave de acesso de 44 posições, só quando o dígito verificador (módulo 11) confere.
 * @property emissaoMs instante da emissão; se [emissaoAproximada], é só o 1º dia do mês (AAMM da chave).
 * @property urlConsulta URL da consulta (https e `.gov.br` apenas); nunca aberta automaticamente.
 */
data class CupomLido(
    val valorCentavos: Long?,
    val chave: String?,
    val cnpj: String?,
    val emissaoMs: Long?,
    val estabelecimento: String?,
    val origem: OrigemCupom,
    val urlConsulta: String? = null,
    val emissaoAproximada: Boolean = false,
    val cidade: String? = null
) {
    /** Descrição sugerida para o lançamento, sem inventar nada além do que o código informa. */
    val descricaoSugerida: String?
        get() = estabelecimento?.takeIf { it.isNotBlank() }
            ?: cnpj?.let { "Compra – CNPJ ${LeitorCupomFiscal.formatarCnpj(it)}" }
}

object LeitorCupomFiscal {

    private const val MAX_TEXTO = 4096
    /** Teto de sanidade: R$ 10.000.000,00. */
    private const val MAX_CENTAVOS = 1_000_000_000L
    private val FUSO = TimeZone.getTimeZone("America/Sao_Paulo")
    private val UFS = setOf(
        11, 12, 13, 14, 15, 16, 17, 21, 22, 23, 24, 25, 26, 27, 28, 29, 31, 32, 33, 35,
        41, 42, 43, 50, 51, 52, 53
    )
    private val MODELOS_FISCAIS = setOf("55", "59", "65")
    private val REGEX_CHAVE = Regex("^[0-9A-Z]{44}$")
    private val REGEX_CHAVE_EM_TEXTO = Regex("(?<![0-9A-Za-z])[0-9A-Z]{44}(?![0-9A-Za-z])")

    fun interpretar(texto: String): CupomLido? {
        val t = texto.trim()
        if (t.isEmpty() || t.length > MAX_TEXTO) return null
        return runCatching {
            interpretarPix(t)
                ?: interpretarUrl(t)
                ?: interpretarSat(t)
                ?: interpretarChaveSolta(t)
                ?: interpretarBoleto(t)
        }.getOrNull()
    }

    // ---------------------------------------------------------------- valor

    /**
     * Converte "12,34", "1.234,56", "R$ 12.50" ou "12.5" para centavos. Rejeita vazio, negativo,
     * zero, mais de 2 casas decimais, lixo e valores acima do teto de sanidade.
     */
    fun parseValorCentavos(bruto: String?): Long? {
        if (bruto == null) return null
        var s = bruto.trim().replace("R$", "", ignoreCase = true).replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.startsWith("-") || s.startsWith("+")) return null
        if (!s.all { it.isDigit() || it == '.' || it == ',' }) return null
        val ultimaVirgula = s.lastIndexOf(',')
        val ultimoPonto = s.lastIndexOf('.')
        s = when {
            ultimaVirgula >= 0 && ultimoPonto >= 0 -> {
                val decimal = if (ultimaVirgula > ultimoPonto) ',' else '.'
                val milhar = if (decimal == ',') '.' else ','
                if (s.count { it == decimal } > 1) return null
                s.replace(milhar.toString(), "").replace(decimal, '.')
            }
            ultimaVirgula >= 0 -> {
                if (s.count { it == ',' } > 1) return null
                s.replace(',', '.')
            }
            ultimoPonto >= 0 -> {
                val casas = s.length - ultimoPonto - 1
                if (s.count { it == '.' } == 1 && casas <= 2) s
                else if (casas == 3) s.replace(".", "") // 1.234 = mil duzentos e trinta e quatro
                else return null
            }
            else -> s
        }
        val bd = s.toBigDecimalOrNull() ?: return null
        if (bd.scale() > 2 || bd.signum() <= 0) return null
        val centavos = runCatching { bd.movePointRight(2).longValueExact() }.getOrNull() ?: return null
        return centavos.takeIf { it in 1..MAX_CENTAVOS }
    }

    private fun centavosDeDigitos(digitos: String): Long? {
        if (digitos.isEmpty() || !digitos.all { it.isDigit() }) return null
        val v = digitos.toBigDecimalOrNull() ?: return null
        val c = runCatching { v.longValueExact() }.getOrNull() ?: return null
        return c.takeIf { it in 1..MAX_CENTAVOS }
    }

    // ---------------------------------------------------------------- chave 44

    /** Dígito verificador (módulo 11, pesos 2..9 da direita p/ esquerda) da chave de acesso. */
    fun digitoVerificadorChave(primeiras43: String): Int {
        var peso = 2
        var soma = 0
        for (i in primeiras43.length - 1 downTo 0) {
            soma += (primeiras43[i].code - 48) * peso
            peso = if (peso == 9) 2 else peso + 1
        }
        val r = soma % 11
        return if (r < 2) 0 else 11 - r
    }

    /** true quando é chave de NF-e/NFC-e/CF-e: 44 posições, UF e modelo plausíveis e DV conferindo. */
    fun chaveValida(chave: String?): Boolean {
        if (chave == null || !REGEX_CHAVE.matches(chave)) return false
        val uf = chave.substring(0, 2).toIntOrNull() ?: return false
        if (uf !in UFS || chave.substring(20, 22) !in MODELOS_FISCAIS) return false
        val mes = chave.substring(4, 6).toIntOrNull() ?: return false
        if (mes !in 1..12) return false
        val dv = chave[43]
        return dv.isDigit() && dv.code - 48 == digitoVerificadorChave(chave.substring(0, 43))
    }

    fun cnpjDaChave(chave: String): String = chave.substring(6, 20)

    fun formatarCnpj(cnpj: String): String =
        if (cnpj.length == 14) "${cnpj.substring(0, 2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}/${cnpj.substring(8, 12)}-${cnpj.substring(12)}"
        else cnpj

    /** 1º dia do mês AAMM da chave, ao meio-dia (America/Sao_Paulo). Aproximação, nunca a data exata. */
    private fun mesDaChave(chave: String): Long? {
        val aa = chave.substring(2, 4).toIntOrNull() ?: return null
        val mm = chave.substring(4, 6).toIntOrNull() ?: return null
        return montarData(2000 + aa, mm, 1, 12, 0, 0)
    }

    private fun montarData(ano: Int, mes: Int, dia: Int, h: Int, mi: Int, s: Int): Long? = runCatching {
        GregorianCalendar(FUSO).apply {
            isLenient = false
            clear()
            set(ano, mes - 1, dia, h, mi, s)
            timeInMillis // força validação
        }.timeInMillis
    }.getOrNull()

    private fun montarCupom(
        chaveBruta: String?, valor: Long?, emissaoExata: Long?, origem: OrigemCupom, url: String?,
        estabelecimento: String? = null
    ): CupomLido {
        val chave = chaveBruta?.takeIf { chaveValida(it) }
        val aprox = if (emissaoExata == null && chave != null) mesDaChave(chave) else null
        return CupomLido(
            valorCentavos = valor,
            chave = chave,
            cnpj = chave?.let { cnpjDaChave(it) },
            emissaoMs = emissaoExata ?: aprox,
            estabelecimento = estabelecimento,
            origem = origem,
            urlConsulta = url,
            emissaoAproximada = emissaoExata == null && aprox != null
        )
    }

    // ---------------------------------------------------------------- SAT

    /** CF-e SAT: `chave44|aaaaMMddHHmmss|valorTotal|cpfCnpjDest|assinatura`. */
    private fun interpretarSat(t: String): CupomLido? {
        if (!t.contains('|')) return null
        val partes = t.split('|')
        if (partes.size < 3) return null
        val chaveTxt = partes[0].trim().removePrefix("CFe").removePrefix("NFe")
        if (!REGEX_CHAVE.matches(chaveTxt)) return null
        val dt = partes[1].trim()
        if (!Regex("^\\d{14}$").matches(dt)) return null
        val emissao = montarData(
            dt.substring(0, 4).toInt(), dt.substring(4, 6).toInt(), dt.substring(6, 8).toInt(),
            dt.substring(8, 10).toInt(), dt.substring(10, 12).toInt(), dt.substring(12, 14).toInt()
        )
        val valor = parseValorCentavos(partes[2].trim())
        return montarCupom(chaveTxt, valor, emissao, OrigemCupom.SAT, null)
    }

    // ---------------------------------------------------------------- NFC-e (URL)

    private fun interpretarUrl(t: String): CupomLido? {
        if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return null
        val semFragmento = t.substringBefore('#')
        val query = semFragmento.substringAfter('?', "")
        val params = parseQuery(query)
        val urlSegura = semFragmento.replace("|", "%7C").takeIf { ConsultaNfceRede.urlPermitida(it) }

        // QR v2: ?p=chave|2|tpAmb|... (online: sem valor; contingência: ...|dia|vNF|...)
        params["p"]?.let { p ->
            val campos = p.split('|')
            if (campos.isNotEmpty() && REGEX_CHAVE.matches(campos[0])) {
                var valor: Long? = null
                if (campos.size >= 7 && Regex("^\\d{1,2}$").matches(campos[3])) {
                    valor = campos[4].takeIf { Regex("^\\d+(\\.\\d{1,2})?$").matches(it) }
                        ?.let { parseValorCentavos(it) }
                }
                return montarCupom(campos[0], valor, null, OrigemCupom.NFCE_V2, urlSegura)
            }
        }

        // QR v1: ?chNFe=...&dhEmi=hex&vNF=10.50&...
        val chNFe = (params["chNFe"] ?: params["chnfe"])?.takeIf { REGEX_CHAVE.matches(it) }
        val vNF = params["vNF"] ?: params["vNFe"] ?: params["vnf"]
        if (chNFe != null || vNF != null) {
            val valor = vNF?.let { parseValorCentavos(it) }
            val emissao = params["dhEmi"]?.let { parseDhEmi(it) }
            val origem = if (vNF != null) OrigemCupom.NFCE_V1 else OrigemCupom.NFCE_V2
            if (chNFe != null || valor != null) return montarCupom(chNFe, valor, emissao, origem, urlSegura)
        }

        // Outras variações: chave de 44 em qualquer parte da URL.
        REGEX_CHAVE_EM_TEXTO.findAll(semFragmento).map { it.value }.firstOrNull { chaveValida(it) }?.let {
            return montarCupom(it, null, null, OrigemCupom.NFCE_V2, urlSegura)
        }
        return null
    }

    private fun parseQuery(q: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (par in q.split('&')) {
            if (par.isEmpty()) continue
            val k = par.substringBefore('=')
            val v = par.substringAfter('=', "")
            if (k.isNotEmpty() && k !in m) m[k] = decodificarPercentual(v)
        }
        return m
    }

    private fun decodificarPercentual(s: String): String = runCatching {
        java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(s)

    /** `dhEmi` do QR v1: ISO-8601 (`2026-10-09T12:34:56-03:00`) em ASCII hexadecimal ou puro. */
    private fun parseDhEmi(bruto: String): Long? {
        val iso = if (bruto.length % 2 == 0 && bruto.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            buildString { for (i in bruto.indices step 2) append(bruto.substring(i, i + 2).toInt(16).toChar()) }
        } else bruto
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:Z|([+-])(\\d{2}):?(\\d{2}))?$").find(iso) ?: return null
        val g = m.groupValues
        val base = montarData(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt()) ?: return null
        if (g[7].isEmpty()) {
            return if (iso.endsWith("Z")) base + FUSO.getOffset(base) else base // sem fuso: assume Brasília
        }
        // Reinterpreta no fuso informado.
        val offMs = (g[8].toInt() * 60 + g[9].toInt()) * 60_000L * (if (g[7] == "-") -1 else 1)
        val utc = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false; clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        }.timeInMillis
        return utc - offMs
    }

    // ---------------------------------------------------------------- chave solta

    private fun interpretarChaveSolta(t: String): CupomLido? {
        val limpo = t.replace(" ", "").removePrefix("CFe").removePrefix("NFe")
        if (!REGEX_CHAVE.matches(limpo) || !chaveValida(limpo)) return null
        val modelo = limpo.substring(20, 22)
        return montarCupom(limpo, null, null, if (modelo == "59") OrigemCupom.SAT else OrigemCupom.NFCE_V2, null)
    }

    // ---------------------------------------------------------------- boleto

    private fun interpretarBoleto(t: String): CupomLido? {
        val d = t.filter { it.isDigit() }
        if (d.length != t.count { it.isDigit() } || t.any { !it.isDigit() && it !in " .-" }) return null
        val barras = when (d.length) {
            44 -> d
            47 -> linhaBancariaParaBarras(d)
            48 -> d.takeIf { it.startsWith("8") }?.let { linhaArrecadacaoParaBarras(it) }
            else -> null
        } ?: return null
        val valor: Long? = if (barras.startsWith("8")) {
            // Arrecadação: pos. 2 = identificador de valor (6/7 = reais; 8/9 = índice de referência).
            if (barras[2] == '6' || barras[2] == '7') centavosDeDigitos(barras.substring(4, 15)) else null
        } else {
            // Bancário: pos. 5-8 fator de vencimento, 9-18 valor (0 = valor livre).
            centavosDeDigitos(barras.substring(9, 19))
        }
        return CupomLido(valor, null, null, null, null, OrigemCupom.BOLETO)
    }

    /** 47 dígitos (linha digitável) -> 44 (código de barras). */
    private fun linhaBancariaParaBarras(l: String): String =
        l.substring(0, 4) + l[32] + l.substring(33, 47) + l.substring(4, 9) + l.substring(10, 20) + l.substring(21, 31)

    /** 48 dígitos (arrecadação): remove o DV de cada bloco de 12. */
    private fun linhaArrecadacaoParaBarras(l: String): String =
        (0 until 4).joinToString("") { l.substring(it * 12, it * 12 + 11) }

    // ---------------------------------------------------------------- PIX (BR Code)

    private fun interpretarPix(t: String): CupomLido? {
        if (!t.startsWith("000201")) return null
        val campos = lerTlv(t) ?: return null
        if (campos.none { (tag, v) -> tag in 26..51 && v.contains("br.gov.bcb.pix", ignoreCase = true) }) return null
        // CRC16/CCITT-FALSE sobre tudo até "6304".
        val crc = campos.firstOrNull { it.first == 63 }?.second
        if (crc != null) {
            val idx = t.lastIndexOf("6304")
            if (idx < 0 || crc16(t.substring(0, idx + 4)) != crc.uppercase()) return null
        }
        val valorTxt = campos.firstOrNull { it.first == 54 }?.second
        val valor = parseValorCentavos(valorTxt?.takeIf { Regex("^\\d+(\\.\\d{1,2})?$").matches(it) })
        val nome = campos.firstOrNull { it.first == 59 }?.second?.trim()?.takeIf { it.isNotEmpty() }
        val cidade = campos.firstOrNull { it.first == 60 }?.second?.trim()?.takeIf { it.isNotEmpty() }
        return CupomLido(valor, null, null, null, nome, OrigemCupom.PIX, cidade = cidade)
    }

    private fun lerTlv(s: String): List<Pair<Int, String>>? {
        val out = ArrayList<Pair<Int, String>>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val tag = s.substring(i, i + 2).toIntOrNull() ?: return null
            val len = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (i + 4 + len > s.length) return null
            out += tag to s.substring(i + 4, i + 4 + len)
            i += 4 + len
        }
        return out
    }

    fun crc16(texto: String): String {
        var crc = 0xFFFF
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return "%04X".format(crc)
    }

    // ---------------------------------------------------------------- HTML da consulta

    data class ConsultaHtml(val valorCentavos: Long?, val estabelecimento: String?)

    private val REGEX_MOEDA = "(\\d{1,3}(?:\\.\\d{3})+,\\d{2}|\\d+,\\d{2})"
    private val PADROES_TOTAL = listOf(
        Regex("valor\\s+a\\s+pagar\\s*(?:\\(?\\s*R\\$\\s*\\)?)?\\s*:?\\s*(?:R\\$)?\\s*$REGEX_MOEDA", RegexOption.IGNORE_CASE),
        Regex("valor\\s+total(?:\\s+(?:da\\s+nota|do\\s+cupom|da\\s+compra|da\\s+nfc-?e))?\\s*(?:\\(?\\s*R\\$\\s*\\)?)?\\s*:?\\s*(?:R\\$)?\\s*$REGEX_MOEDA", RegexOption.IGNORE_CASE),
        Regex("\\btotal\\s*(?:\\(?\\s*R\\$\\s*\\)?)\\s*:?\\s*$REGEX_MOEDA", RegexOption.IGNORE_CASE)
    )

    /**
     * Melhor esforço: extrai "Valor a pagar"/"Valor total" do HTML da consulta pública (cada UF tem um
     * layout). Prefere "a pagar" (já com descontos). Nunca executa nada do HTML.
     */
    fun extrairDeHtml(html: String): ConsultaHtml? {
        if (html.isBlank()) return null
        val texto = htmlParaTexto(html)
        val valor = PADROES_TOTAL.firstNotNullOfOrNull { p ->
            p.findAll(texto).mapNotNull { parseValorCentavos(it.groupValues[1]) }.firstOrNull()
        }
        val estab = Regex("class=\"txtTopo\"[^>]*>\\s*([^<]{3,80}?)\\s*<", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { htmlParaTexto(it) }?.takeIf { it.length >= 3 }
        if (valor == null && estab == null) return null
        return ConsultaHtml(valor, estab)
    }

    private fun htmlParaTexto(html: String): String =
        html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]*>"), " ")
            .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
            .replace("&#36;", "$")
            .replace(Regex("\\s+"), " ")
            .trim()
}
