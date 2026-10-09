package com.meudinheiro.funcoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LeitorCupomFiscalTest {

    /** Chave sintética: UF 35, AAMM 2610, CNPJ fictício, modelo dado, DV calculado aqui. */
    private fun chave(modelo: String, uf: String = "35", aamm: String = "2610", cnpj: String = "12345678000195"): String {
        val base = uf + aamm + cnpj + modelo + "001" + "000000123" + "1" + "12345678"
        require(base.length == 43)
        return base + LeitorCupomFiscal.digitoVerificadorChave(base)
    }

    private fun brt(ms: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).apply { timeInMillis = ms }

    // ------------------------------------------------------------ chave / valor

    @Test fun chaveValida_confereDv() {
        val c = chave("65")
        assertTrue(LeitorCupomFiscal.chaveValida(c))
        val errada = c.dropLast(1) + ((c.last().code - 48 + 1) % 10)
        assertFalse(LeitorCupomFiscal.chaveValida(errada))
        assertFalse(LeitorCupomFiscal.chaveValida(chave("65", uf = "99")))
        assertFalse(LeitorCupomFiscal.chaveValida("123"))
    }

    @Test fun parseValor_formatos() {
        assertEquals(1234L, LeitorCupomFiscal.parseValorCentavos("12,34"))
        assertEquals(123456L, LeitorCupomFiscal.parseValorCentavos("1.234,56"))
        assertEquals(1250L, LeitorCupomFiscal.parseValorCentavos("12.50"))
        assertEquals(1200L, LeitorCupomFiscal.parseValorCentavos("R$ 12"))
        assertEquals(5L, LeitorCupomFiscal.parseValorCentavos("0,05"))
    }

    @Test fun parseValor_rejeitaInvalidoNegativoZero() {
        assertNull(LeitorCupomFiscal.parseValorCentavos("-10,00"))
        assertNull(LeitorCupomFiscal.parseValorCentavos("0"))
        assertNull(LeitorCupomFiscal.parseValorCentavos("0,00"))
        assertNull(LeitorCupomFiscal.parseValorCentavos(""))
        assertNull(LeitorCupomFiscal.parseValorCentavos(null))
        assertNull(LeitorCupomFiscal.parseValorCentavos("abc"))
        assertNull(LeitorCupomFiscal.parseValorCentavos("1,234"))
        assertNull(LeitorCupomFiscal.parseValorCentavos("99999999999999,00"))
    }

    // ------------------------------------------------------------ SAT

    @Test fun sat_extraiValorDataEChave() {
        val c = chave("59")
        val r = LeitorCupomFiscal.interpretar("$c|20261009143005|25.90||ASSINATURAxyz")!!
        assertEquals(OrigemCupom.SAT, r.origem)
        assertEquals(2590L, r.valorCentavos)
        assertEquals(c, r.chave)
        assertEquals("12345678000195", r.cnpj)
        assertFalse(r.emissaoAproximada)
        val cal = brt(r.emissaoMs!!)
        assertEquals(2026, cal.get(Calendar.YEAR)); assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(9, cal.get(Calendar.DAY_OF_MONTH)); assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Compra – CNPJ 12.345.678/0001-95", r.descricaoSugerida)
    }

    @Test fun sat_valorZeroOuNegativoEhRejeitado_chaveInvalidaIgnorada() {
        val c = chave("59")
        assertNull(LeitorCupomFiscal.interpretar("$c|20261009143005|0.00||x")!!.valorCentavos)
        assertNull(LeitorCupomFiscal.interpretar("$c|20261009143005|-5.00||x")!!.valorCentavos)
        val ruim = c.dropLast(1) + ((c.last().code - 48 + 1) % 10)
        val r = LeitorCupomFiscal.interpretar("$ruim|20261009143005|10.00||x")!!
        assertEquals(1000L, r.valorCentavos); assertNull(r.chave); assertNull(r.cnpj)
    }

    @Test fun sat_dataInvalidaNaoQuebra() {
        assertTrue(LeitorCupomFiscal.interpretar("${chave("59")}|20261345250000|10.00||x")!!.emissaoAproximada)
    }

    // ------------------------------------------------------------ NFC-e

    @Test fun nfceV1_vNFnaQuery() {
        val c = chave("65")
        val dh = "2026-10-09T12:34:56-03:00".map { "%02x".format(it.code) }.joinToString("")
        val url = "https://www.fazenda.sp.gov.br/nfce/qrcode?chNFe=$c&nVersao=100&tpAmb=1&dhEmi=$dh&vNF=89.90&vICMS=0.00&digVal=abc&cIdToken=000001&cHashQRCode=ABCDEF"
        val r = LeitorCupomFiscal.interpretar(url)!!
        assertEquals(OrigemCupom.NFCE_V1, r.origem)
        assertEquals(8990L, r.valorCentavos)
        assertEquals(c, r.chave)
        assertEquals(url.replace("|", "%7C"), r.urlConsulta)
        val cal = brt(r.emissaoMs!!)
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY)); assertEquals(34, cal.get(Calendar.MINUTE))
        assertFalse(r.emissaoAproximada)
    }

    @Test fun nfceV1_vNFeAlternativo() {
        val c = chave("65")
        assertEquals(1500L, LeitorCupomFiscal.interpretar("https://x.sefaz.gov.br/q?chNFe=$c&vNFe=15.00")!!.valorCentavos)
    }

    @Test fun nfceV2_semValor_derivaCnpjEMesAproximado() {
        val c = chave("65")
        val url = "https://www.nfce.fazenda.sp.gov.br/qrcode?p=$c|2|1|1|ABCDEF0123456789"
        val r = LeitorCupomFiscal.interpretar(url)!!
        assertEquals(OrigemCupom.NFCE_V2, r.origem)
        assertNull(r.valorCentavos)
        assertEquals(c, r.chave)
        assertEquals("12345678000195", r.cnpj)
        assertTrue(r.emissaoAproximada)
        val cal = brt(r.emissaoMs!!)
        assertEquals(2026, cal.get(Calendar.YEAR)); assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(url.replace("|", "%7C"), r.urlConsulta)
    }

    @Test fun nfceV2_contingenciaTrazValor() {
        val c = chave("65")
        val r = LeitorCupomFiscal.interpretar("https://x.sefaz.gov.br/qr?p=$c|2|1|09|45.67|1|12345678901|ASSINATURA")!!
        assertEquals(4567L, r.valorCentavos)
    }

    @Test fun nfceV2_urlNaoGovBrNaoViraUrlDeConsulta() {
        val c = chave("65")
        val r = LeitorCupomFiscal.interpretar("https://evil.example.com/qr?p=$c|2|1|1|AAA")!!
        assertNull(r.urlConsulta)
        assertEquals(c, r.chave) // dados continuam úteis, mas a URL não será consultada
        assertNull(LeitorCupomFiscal.interpretar("http://x.sefaz.gov.br/qr?p=$c|2|1|1|AAA")!!.urlConsulta)
    }

    @Test fun chaveSolta_44Digitos() {
        val c = chave("65")
        val r = LeitorCupomFiscal.interpretar(c)!!
        assertEquals(OrigemCupom.NFCE_V2, r.origem); assertNull(r.valorCentavos); assertEquals("12345678000195", r.cnpj)
        assertEquals(OrigemCupom.SAT, LeitorCupomFiscal.interpretar(chave("59"))!!.origem)
    }

    // ------------------------------------------------------------ boleto

    private fun dv10(s: String): Int { // módulo 10 dos campos da linha digitável
        var soma = 0; var peso = 2
        for (i in s.length - 1 downTo 0) { val p = (s[i] - '0') * peso; soma += p / 10 + p % 10; peso = if (peso == 2) 1 else 2 }
        return (10 - soma % 10) % 10
    }

    @Test fun boletoBancario_44Digitos() {
        // banco 341, moeda 9, DV 1, fator 9999, valor 0000012345 = R$ 123,45, campo livre 25
        val barras = "341" + "9" + "1" + "9999" + "0000012345" + "1234567890123456789012345"
        assertEquals(44, barras.length)
        val r = LeitorCupomFiscal.interpretar(barras)!!
        assertEquals(OrigemCupom.BOLETO, r.origem); assertEquals(12345L, r.valorCentavos)
    }

    @Test fun boletoBancario_linhaDigitavel47() {
        val livre = "1234567890123456789012345"
        val c1 = "3419" + livre.substring(0, 5); val c2 = livre.substring(5, 15); val c3 = livre.substring(15, 25)
        val linha = c1 + dv10(c1) + c2 + dv10(c2) + c3 + dv10(c3) + "1" + "9999" + "0000012345"
        assertEquals(47, linha.length)
        assertEquals(12345L, LeitorCupomFiscal.interpretar(linha)!!.valorCentavos)
        // com pontos e espaços, como impressa
        val fmt = "${linha.substring(0, 5)}.${linha.substring(5, 10)} ${linha.substring(10, 15)}.${linha.substring(15, 21)} ${linha.substring(21, 26)}.${linha.substring(26, 32)} ${linha[32]} ${linha.substring(33)}"
        assertEquals(12345L, LeitorCupomFiscal.interpretar(fmt)!!.valorCentavos)
    }

    @Test fun boletoValorLivreOuZero_semValor() {
        val barras = "341" + "9" + "1" + "9999" + "0000000000" + "1234567890123456789012345"
        assertNull(LeitorCupomFiscal.interpretar(barras)!!.valorCentavos)
    }

    @Test fun arrecadacao_44e48() {
        // 8 | segmento 1 | identificador 6 (reais) | DV | valor 11 dígitos | resto
        val barras = "816" + "0" + "00000012345" + "00000000001234567890123456789"
        val b44 = barras.take(44)
        assertEquals(44, b44.length)
        assertEquals(12345L, LeitorCupomFiscal.interpretar(b44)!!.valorCentavos)
        val linha48 = (0 until 4).joinToString("") { b44.substring(it * 11, it * 11 + 11) + "0" }
        assertEquals(12345L, LeitorCupomFiscal.interpretar(linha48)!!.valorCentavos)
        // identificador 8 = índice de referência, não é valor em reais
        val ref = "818" + b44.substring(3)
        assertNull(LeitorCupomFiscal.interpretar(ref)!!.valorCentavos)
    }

    // ------------------------------------------------------------ PIX

    private fun tlv(tag: String, v: String) = tag + "%02d".format(v.length) + v

    private fun pix(valor: String?, nome: String = "LOJA TESTE", cidade: String = "SAO PAULO", corromper: Boolean = false): String {
        val sem = "000201" + tlv("26", tlv("00", "br.gov.bcb.pix") + tlv("01", "teste@exemplo.com")) +
            tlv("52", "0000") + tlv("53", "986") + (valor?.let { tlv("54", it) } ?: "") +
            tlv("58", "BR") + tlv("59", nome) + tlv("60", cidade) + tlv("62", tlv("05", "***")) + "6304"
        val corpo = sem
        val crc = LeitorCupomFiscal.crc16(corpo)
        val final = corpo + crc
        return if (corromper) final.dropLast(1) + (if (final.last() == '0') '1' else '0') else final
    }

    @Test fun pix_valorNomeCidade() {
        val r = LeitorCupomFiscal.interpretar(pix("37.50"))!!
        assertEquals(OrigemCupom.PIX, r.origem); assertEquals(3750L, r.valorCentavos)
        assertEquals("LOJA TESTE", r.estabelecimento); assertEquals("SAO PAULO", r.cidade)
    }

    @Test fun pix_semValor_valorZeroNegativoOuCrcRuim() {
        assertNull(LeitorCupomFiscal.interpretar(pix(null))!!.valorCentavos)
        assertNull(LeitorCupomFiscal.interpretar(pix("0.00"))!!.valorCentavos)
        assertNull(LeitorCupomFiscal.interpretar(pix("-3.00"))!!.valorCentavos)
        assertNull(LeitorCupomFiscal.interpretar(pix("10.00", corromper = true)))
    }

    // ------------------------------------------------------------ lixo

    @Test fun textoDesconhecidoOuMalicioso_retornaNull() {
        assertNull(LeitorCupomFiscal.interpretar(""))
        assertNull(LeitorCupomFiscal.interpretar("   "))
        assertNull(LeitorCupomFiscal.interpretar("hello world"))
        assertNull(LeitorCupomFiscal.interpretar("javascript:alert(1)"))
        assertNull(LeitorCupomFiscal.interpretar("https://exemplo.com/promo"))
        assertNull(LeitorCupomFiscal.interpretar("1".repeat(10_000)))
        assertNotNull(LeitorCupomFiscal.interpretar(chave("65")))
    }

    // ------------------------------------------------------------ HTML da consulta

    @Test fun html_valorAPagarTemPrioridade() {
        val html = """
            <html><head><style>.x{}</style><script>var total = "Valor total R$ 1,00";</script></head><body>
            <div id="u20" class="txtTopo">SUPERMERCADO FICTICIO LTDA</div>
            <table><tr><td>Qtd. total de itens</td><td>3</td></tr>
            <tr><td>Valor total R$</td><td class="totalNumb">1.234,56</td></tr>
            <tr><td>Descontos R$</td><td>34,56</td></tr>
            <tr><td>Valor a pagar R$</td><td class="totalNumb txtMax">1.200,00</td></tr></table></body></html>
        """.trimIndent()
        val r = LeitorCupomFiscal.extrairDeHtml(html)!!
        assertEquals(120000L, r.valorCentavos)
        assertEquals("SUPERMERCADO FICTICIO LTDA", r.estabelecimento)
    }

    @Test fun html_soValorTotal_eEntidades() {
        val r = LeitorCupomFiscal.extrairDeHtml("<p>VALOR&nbsp;TOTAL&nbsp;R$&nbsp;<b>45,90</b></p><p>Valor total dos tributos R$ 9,99</p>")!!
        assertEquals(4590L, r.valorCentavos)
    }

    @Test fun html_semValorOuInvalido() {
        assertNull(LeitorCupomFiscal.extrairDeHtml("<html><body>Captcha: digite os caracteres</body></html>"))
        assertNull(LeitorCupomFiscal.extrairDeHtml(""))
        assertNull(LeitorCupomFiscal.extrairDeHtml("<p>Valor a pagar R$ 0,00</p>")?.valorCentavos)
    }

    // ------------------------------------------------------------ segurança da consulta

    @Test fun urlPermitida_apenasHttpsGovBr() {
        assertTrue(ConsultaNfceRede.urlPermitida("https://www.nfce.fazenda.sp.gov.br/qrcode?p=1"))
        assertFalse(ConsultaNfceRede.urlPermitida("http://www.sefaz.gov.br/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://sefaz.gov.br.evil.com/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://evil.com/.gov.br"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://usuario@sefaz.gov.br/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://evil.com@sefaz.gov.br.outro.com/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://sefaz.gov.br:8443/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("https://127.0.0.1/x"))
        assertFalse(ConsultaNfceRede.urlPermitida("file:///etc/passwd"))
        assertFalse(ConsultaNfceRede.urlPermitida("nao é url"))
    }

    @Test fun redirecionamento_naoSaiDeGovBr() {
        val base = "https://a.sefaz.gov.br/x"
        assertEquals("https://a.sefaz.gov.br/y", ConsultaNfceRede.resolverRedirecionamento(base, "/y"))
        assertEquals("https://b.fazenda.gov.br/z", ConsultaNfceRede.resolverRedirecionamento(base, "https://b.fazenda.gov.br/z"))
        assertNull(ConsultaNfceRede.resolverRedirecionamento(base, "https://evil.com/z"))
        assertNull(ConsultaNfceRede.resolverRedirecionamento(base, "http://b.fazenda.gov.br/z"))
    }
}
