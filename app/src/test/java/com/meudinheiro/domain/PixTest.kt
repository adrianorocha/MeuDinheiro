package com.meudinheiro.domain

import com.meudinheiro.domain.Pix.Direcao
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PixTest {
    private val tz: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

    // Vetores com CRC calculado fora do código (Python, CRC16/CCITT-FALSE).
    private val FULANO = "00020101021126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913FULANO DE TAL6008BRASILIA62070503***6304A5E2"
    private val DINAMICO = "00020101021226940014br.gov.bcb.pix0111123456789012557pix.exemplo.com.br/qr/v2/9d36b84fc70b478fb95c12729b90ca2552040000530398654061234.55802BR5910LOJA DO ZE6014RIO DE JANEIRO62100506ABC1236304F0AE"
    private val CENTAVOS = "00020101021126360014br.gov.bcb.pix0114+551199999888852040000530398654040.055802BR5903ANA6014BELO HORIZONTE62070503***6304EDFF"

    private fun tlv(id: String, v: String) = "%s%02d%s".format(id, v.length, v)

    /** Monta um BR Code com o CRC calculado por [Pix.crc16] (a função em si é conferida pelos vetores fixos acima). */
    private fun montar(nome: String, cidade: String = "SAO PAULO", chave: String = "a@b.com", valor: String? = null, moeda: String = "986"): String {
        var p = tlv("00", "01") + tlv("01", "11") + tlv("26", tlv("00", "br.gov.bcb.pix") + tlv("01", chave)) + tlv("52", "0000") + tlv("53", moeda)
        if (valor != null) p += tlv("54", valor)
        p += tlv("58", "BR") + tlv("59", nome) + tlv("60", cidade) + tlv("62", tlv("05", "***")) + "6304"
        return p + Pix.crc16(p)
    }

    private fun ok(r: Pix.ResultadoBrCode) = (r as Pix.ResultadoBrCode.Ok).brCode
    private fun motivo(r: Pix.ResultadoBrCode) = (r as Pix.ResultadoBrCode.Invalido).motivo
    private fun comp(txt: String) = (Pix.lerComprovantePix(txt, tz) as Pix.ResultadoComprovante.Ok).comprovante

    // ------------------------------------------------------------------ CRC / BR Code

    @Test fun crc16VetorPadrao() {
        assertEquals("29B1", Pix.crc16("123456789")) // vetor de verificação do CRC-16/CCITT-FALSE
        assertEquals("FFFF", Pix.crc16(""))
    }

    @Test fun brCodeEstaticoSemValor() {
        val b = ok(Pix.lerBrCode(FULANO))
        assertNull(b.valorCentavos)
        assertEquals("Fulano de Tal", b.beneficiario)
        assertEquals("BRASILIA", b.cidade)
        assertEquals("123e4567-e12b-12d1-a456-426655440000", b.chave)
        assertNull(b.txid)
        assertFalse(b.dinamico)
    }

    @Test fun brCodeComValorMensagemEChaveEmail() {
        var p = tlv("00", "01") + tlv("01", "11") +
            tlv("26", tlv("00", "br.gov.bcb.pix") + tlv("01", "maria@exemplo.com.br") + tlv("02", "Pagamento almoco")) +
            tlv("52", "0000") + tlv("53", "986") + tlv("54", "25.90") + tlv("58", "BR") + tlv("59", "MARIA JOSE") + tlv("60", "SAO PAULO") +
            tlv("62", tlv("05", "***")) + "6304"
        val b = ok(Pix.lerBrCode(p + Pix.crc16(p)))
        assertEquals(2590L, b.valorCentavos)
        assertEquals("Maria Jose", b.beneficiario)
        assertEquals("maria@exemplo.com.br", b.chave)
        assertEquals("Pagamento almoco", b.mensagem)
    }

    @Test fun brCodeDinamicoMostraUrlEtxid() {
        val b = ok(Pix.lerBrCode(DINAMICO))
        assertEquals(123_450L, b.valorCentavos) // "1234.5"
        assertTrue(b.dinamico)
        assertEquals("pix.exemplo.com.br/qr/v2/9d36b84fc70b478fb95c12729b90ca25", b.urlPayload)
        assertEquals("ABC123", b.txid)
        assertEquals("Loja do Ze", b.beneficiario)
        assertEquals("RIO DE JANEIRO", b.cidade)
    }

    @Test fun brCodeValorFracionado() {
        assertEquals(5L, ok(Pix.lerBrCode(CENTAVOS)).valorCentavos) // "0.05"
    }

    @Test fun brCodeToleraQuebrasDeLinhaETextoAoRedor() {
        val com = "Pix copia e cola:\n" + FULANO.chunked(40).joinToString("\n") + "\nobrigado"
        assertEquals("Fulano de Tal", ok(Pix.lerBrCode(com)).beneficiario)
    }

    @Test fun brCodeComAcentoNoNome() {
        val b = ok(Pix.lerBrCode(montar("JOSÉ AÇAÍ NETO")))
        assertEquals("José Açaí Neto", b.beneficiario)
    }

    @Test fun crcInvalidoOuAdulterado() {
        assertEquals(Pix.MotivoBrCode.CRC_INVALIDO, motivo(Pix.lerBrCode(FULANO.dropLast(4) + "0000")))
        // trocar o valor do beneficiário depois de gerado o CRC
        assertEquals(Pix.MotivoBrCode.CRC_INVALIDO, motivo(Pix.lerBrCode(FULANO.replace("FULANO", "BELTRANO".take(6)))))
        assertEquals(Pix.MotivoBrCode.FORMATO_INVALIDO, motivo(Pix.lerBrCode(FULANO.dropLast(4)))) // CRC cortado
        assertEquals(Pix.MotivoBrCode.CRC_INVALIDO, motivo(Pix.lerBrCode(FULANO.dropLast(8)))) // sem o campo 63
        // CRC em minúsculas é aceito
        assertTrue(Pix.lerBrCode(FULANO.dropLast(4) + "a5e2") is Pix.ResultadoBrCode.Ok)
    }

    @Test fun entradasInvalidasNaoLancamExcecao() {
        assertEquals(Pix.MotivoBrCode.VAZIO, motivo(Pix.lerBrCode(null)))
        assertEquals(Pix.MotivoBrCode.VAZIO, motivo(Pix.lerBrCode("   \n ")))
        assertEquals(Pix.MotivoBrCode.NAO_E_BRCODE, motivo(Pix.lerBrCode("https://exemplo.com")))
        assertEquals(Pix.MotivoBrCode.FORMATO_INVALIDO, motivo(Pix.lerBrCode("000201" + "26ZZ")))
        assertEquals(Pix.MotivoBrCode.FORMATO_INVALIDO, motivo(Pix.lerBrCode("0002010102119999abc")))
        assertEquals(Pix.MotivoBrCode.MUITO_LONGO, motivo(Pix.lerBrCode("000201" + "1".repeat(5000))))
    }

    @Test fun naoPixEmoedaEvalorInvalidos() {
        var p = tlv("00", "01") + tlv("26", tlv("00", "com.outro.pagamento") + tlv("01", "x")) + tlv("53", "986") + tlv("59", "LOJA") + "6304"
        assertEquals(Pix.MotivoBrCode.NAO_E_PIX, motivo(Pix.lerBrCode(p + Pix.crc16(p))))
        assertEquals(Pix.MotivoBrCode.MOEDA_INVALIDA, motivo(Pix.lerBrCode(montar("LOJA", moeda = "840"))))
        assertEquals(Pix.MotivoBrCode.VALOR_INVALIDO, motivo(Pix.lerBrCode(montar("LOJA", valor = "12,50"))))
        assertEquals(Pix.MotivoBrCode.VALOR_INVALIDO, motivo(Pix.lerBrCode(montar("LOJA", valor = "-5"))))
        // valor zero = QR aberto
        assertNull(ok(Pix.lerBrCode(montar("LOJA", valor = "0.00"))).valorCentavos)
    }

    // ------------------------------------------------------------------ chave mascarada

    @Test fun chaveSempreMascarada() {
        val chaves = listOf(
            "12345678901", "12345678000190", "maria@exemplo.com.br", "+5511999998888",
            "123e4567-e12b-12d1-a456-426655440000", "abcdefghijklmnop"
        )
        for (c in chaves) {
            val m = Pix.mascararChave(c)
            assertNotEquals(c, m)
            assertFalse("vazou: $m", m.contains(c.take(6)) && !c.contains('@'))
        }
        assertEquals("•••.•••.•••-01", Pix.mascararChave("12345678901"))
        assertEquals("m•••@exemplo.com.br", Pix.mascararChave("maria@exemplo.com.br"))
        assertTrue(Pix.mascararChave("+5511999998888").endsWith("8888"))
        assertEquals("", Pix.mascararChave("  "))
        assertEquals("•••", Pix.mascararChave("abc"))
    }

    // ------------------------------------------------------------------ contraparte e descrição

    @Test fun normalizarContraparte() {
        assertEquals("Maria da Silva", Pix.normalizarContraparte("MARIA DA SILVA"))
        assertEquals("Maria da Silva", Pix.normalizarContraparte("  Nome:  Maria   da Silva  CPF: ***.123.456-**"))
        assertEquals("João Souza", Pix.normalizarContraparte("João Souza - 123.456.789-00"))
        assertEquals("José D'Ávila-Neto", Pix.normalizarContraparte("JOSÉ D'ÁVILA-NETO"))
        assertEquals("Maria Silva", Pix.normalizarContraparte("Maria Silva de"))
        assertEquals("Padaria Pão Quente", Pix.normalizarContraparte("Padaria Pão Quente | Ag 0001"))
        assertNull(Pix.normalizarContraparte("12345678901"))
        assertNull(Pix.normalizarContraparte("***.456.789-**"))
        assertNull(Pix.normalizarContraparte(""))
        assertNull(Pix.normalizarContraparte(null))
        assertTrue(Pix.normalizarContraparte("A".repeat(100) + " BBB")!!.length <= 40)
    }

    @Test fun descricaoNaConvencao() {
        assertEquals("Pix para Maria Silva", Pix.descricao(Direcao.ENVIADO, "MARIA SILVA"))
        assertEquals("Pix de João", Pix.descricao(Direcao.RECEBIDO, "João"))
        assertEquals("Pix enviado", Pix.descricao(Direcao.ENVIADO, null))
        assertEquals("Pix recebido", Pix.descricao(Direcao.RECEBIDO, "123"))
        assertTrue(Pix.descricao(Direcao.ENVIADO, "X".repeat(200) + "Y").length <= Pix.MAX_DESCRICAO)
        assertEquals("Maria Silva", Pix.contraparteDaDescricao("Pix para Maria Silva"))
        assertEquals("João", Pix.contraparteDaDescricao("pix de João"))
        assertNull(Pix.contraparteDaDescricao("Pix enviado"))
        assertNull(Pix.contraparteDaDescricao("Mercado"))
    }

    // ------------------------------------------------------------------ comprovante

    @Test fun comprovanteEnviadoEstiloApp() {
        val c = comp(
            """
            Comprovante de transferência
            Transferência enviada
            Valor
            R$ 1.234,56
            Data: 15/03/2025 às 14:32:10
            Destino
            Nome: MARIA DA SILVA
            CPF: ***.123.456-**
            Instituição: Nu Pagamentos
            Origem
            Nome: Adriano Rocha
            ID da transação
            E18236120202503151732s0123456789
            """.trimIndent()
        )
        assertEquals(Direcao.ENVIADO, c.direcao)
        assertEquals(123_456L, c.valorCentavos)
        assertEquals("Maria da Silva", c.contraparte)
        assertEquals("E18236120202503151732s0123456789", c.e2e)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = c.dataMs!! }
        assertEquals(2025, cal.get(Calendar.YEAR)); assertEquals(2, cal.get(Calendar.MONTH)); assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun comprovanteRecebidoComOrigem() {
        val c = comp("Pix recebido\nValor: R$ 50,00\nOrigem\nNome: João Pereira\n10 de abril de 2025")
        assertEquals(Direcao.RECEBIDO, c.direcao)
        assertEquals(5000L, c.valorCentavos)
        assertEquals("João Pereira", c.contraparte)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = c.dataMs!! }
        assertEquals(3, cal.get(Calendar.MONTH)); assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun comprovanteQuemPagouQuemRecebeu() {
        val c = comp("Pagamento Pix realizado\nValor R$ 89,90\nQuem recebeu\nPADARIA PAO QUENTE LTDA\nQuem pagou\nAdriano Rocha")
        assertEquals(Direcao.ENVIADO, c.direcao)
        assertEquals(8990L, c.valorCentavos)
        assertEquals("Padaria Pao Quente Ltda", c.contraparte)
    }

    @Test fun comprovanteEmFraseCorrida() {
        val e = comp("Você enviou um Pix de R\$ 25,5 para ANA PAULA")
        assertEquals(Direcao.ENVIADO, e.direcao)
        assertEquals(2550L, e.valorCentavos)
        assertEquals("Ana Paula", e.contraparte)
        val r = comp("Pix recebido de Carlos Lima no valor de R\$ 10.50")
        assertEquals(Direcao.RECEBIDO, r.direcao)
        assertEquals(1050L, r.valorCentavos)
        assertEquals("Carlos Lima", r.contraparte)
    }

    @Test fun comprovanteSujoIgnoraSaldoETarifa() {
        val c = comp("  *** PIX ***   \n\n Pix enviado!!  \nSaldo atual: R$ 9.999,00\nTarifa R$ 0,00\nValor: R$ 7,00\n Para:   LOJA DO ZÉ   \n")
        assertEquals(700L, c.valorCentavos)
        assertEquals(Direcao.ENVIADO, c.direcao)
        assertEquals("Loja do Zé", c.contraparte)
    }

    @Test fun comprovanteAmbiguoNaoAdivinha() {
        // dois valores diferentes sem rótulo: não escolhe
        val c = comp("Pix enviado R$ 10,00 e depois R$ 20,00 E18236120202503151732abcdefghijk")
        assertNull(c.valorCentavos)
        assertNotNull(c.e2e)
        // sinais dos dois lados: direção indefinida
        val d = comp("Pix enviado e recebido R$ 5,00")
        assertNull(d.direcao)
        assertEquals(500L, d.valorCentavos)
    }

    @Test fun comprovanteInvalido() {
        assertEquals(Pix.MotivoComprovante.VAZIO, (Pix.lerComprovantePix("  ", tz) as Pix.ResultadoComprovante.Invalido).motivo)
        assertEquals(Pix.MotivoComprovante.VAZIO, (Pix.lerComprovantePix(null, tz) as Pix.ResultadoComprovante.Invalido).motivo)
        assertEquals(Pix.MotivoComprovante.NADA_RECONHECIDO, (Pix.lerComprovantePix("Olá, tudo bem? Pix para amanhã.", tz) as Pix.ResultadoComprovante.Invalido).motivo)
        assertEquals(Pix.MotivoComprovante.MUITO_LONGO, (Pix.lerComprovantePix("R$ 1,00 ".repeat(2000), tz) as Pix.ResultadoComprovante.Invalido).motivo)
        assertEquals(Pix.MotivoComprovante.NADA_RECONHECIDO, (Pix.lerComprovantePix("Valor: R$ 0,00", tz) as Pix.ResultadoComprovante.Invalido).motivo)
    }

    @Test fun e2e() {
        assertEquals("E18236120202503151732s0123456789", Pix.extrairE2e("ID: E18236120202503151732s0123456789."))
        assertNull(Pix.extrairE2e("EXEMPLO12345678"))
        assertNull(Pix.extrairE2e("XE18236120202503151732s0123456789")) // colado em outro texto
        assertNull(Pix.extrairE2e(null))
        val ms = Pix.dataDoE2e("E18236120202503151732s0123456789", tz)!!
        val cal = Calendar.getInstance(tz).apply { timeInMillis = ms }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals("E18236120…6789", Pix.e2eResumido("E18236120202503151732s0123456789"))
    }

    // ------------------------------------------------------------------ rascunho, tags, pessoa

    @Test fun rascunhoDeBrCodeEnviado() {
        val b = ok(Pix.lerBrCode(DINAMICO))
        val r = Pix.rascunhoPix(b, Direcao.ENVIADO)
        assertEquals("Pix para Loja do Ze", r.descricao)
        assertEquals(123_450L, r.valorCentavos)
        assertEquals(listOf("PIX"), r.tags)
        assertEquals("pix.exemplo.com.br", r.resumo.hostPayload)
        // a chave completa nunca vai para a descrição, tags ou resumo
        val tudo = r.descricao + r.tags.joinToString() + r.resumo.toString()
        assertFalse(tudo.contains("12345678901"))
        assertNotNull(r.resumo.chaveMascarada)
        assertFalse(r.conflitoDirecao)
    }

    @Test fun rascunhoDeBrCodeRecebidoNaoUsaNomeDoBeneficiario() {
        val r = Pix.rascunhoPix(ok(Pix.lerBrCode(FULANO)), Direcao.RECEBIDO)
        assertEquals("Pix recebido", r.descricao)
        assertNull(r.valorCentavos)
    }

    @Test fun rascunhoDeComprovanteComConflito() {
        val c = comp("Pix recebido\nValor: R$ 50,00\nOrigem\nNome: João Pereira")
        val r = Pix.rascunhoPix(c, Direcao.ENVIADO) // formulário de despesa
        assertTrue(r.conflitoDirecao)
        assertEquals(Direcao.RECEBIDO, r.direcaoLida)
        val ok = Pix.rascunhoPix(c, Direcao.RECEBIDO)
        assertFalse(ok.conflitoDirecao)
        assertEquals("Pix de João Pereira", ok.descricao)
        // sem direção no texto vale a do formulário
        val s = Pix.rascunhoPix(comp("Valor: R$ 9,00 E18236120202503151732s0123456789"), Direcao.ENVIADO)
        assertFalse(s.conflitoDirecao)
        assertEquals("Pix enviado (ID 23456789)", s.descricao)
    }

    @Test fun tagPix() {
        assertEquals(listOf("PIX", "a"), Pix.comTag(listOf("a")))
        assertEquals(listOf("PIX", "a"), Pix.comTag(listOf("pix", "a"))) // não duplica (chave sem caixa)
        assertEquals(5, Pix.comTag(listOf("a", "b", "c", "d", "e")).size)
        assertEquals("PIX", Pix.comTag(listOf("a", "b", "c", "d", "e"))[0])
        assertTrue(Pix.temTag(listOf("#pix")))
        assertFalse(Pix.temTag(listOf("pixel")))
        assertEquals(listOf("a"), Pix.semTag(listOf("PIX", "a")))
    }

    @Test fun sugestaoDePessoaSoQuandoUnica() {
        val ana = Pix.PessoaRef(1, "Ana Paula")
        val joao = Pix.PessoaRef(2, "João")
        val joao2 = Pix.PessoaRef(3, "João Silva")
        assertEquals(ana, Pix.sugerirPessoa("Ana Paula Souza", listOf(ana, joao)))
        assertEquals(ana, Pix.sugerirPessoa("ANA PAULA", listOf(ana)))
        assertNull(Pix.sugerirPessoa("Ana Maria", listOf(ana)))
        assertEquals(joao, Pix.sugerirPessoa("Joao", listOf(joao, ana)))
        assertNull(Pix.sugerirPessoa("João Pedro Alves", listOf(joao, ana))) // nome único curto não casa com nome composto
        assertNull(Pix.sugerirPessoa("Ana Paula", listOf(ana, Pix.PessoaRef(9, "ana paula")))) // duas pessoas iguais: ambíguo
        assertEquals(joao2, Pix.sugerirPessoa("João Silva Neto", listOf(joao2)))
        assertNull(Pix.sugerirPessoa(null, listOf(ana)))
    }

    // ------------------------------------------------------------------ duplicidade

    private fun ms(d: Int, h: Int = 12): Long = Calendar.getInstance(tz).apply { clear(); set(2025, Calendar.MARCH, d, h, 0) }.timeInMillis

    private fun ex(valor: Long, d: Int, desc: String, conta: String = "123", entrada: Boolean = false, tags: List<String> = emptyList()) =
        Pix.Existente(valor, entrada, ms(d), conta, desc, tags)

    @Test fun duplicidadePorValorDiaContaESentido() {
        val c = Pix.Candidato(8990, false, ms(15), "123", "Padaria Pão Quente")
        assertEquals(1, Pix.possiveisDuplicados(c, listOf(ex(8990, 15, "Pix para Padaria Pao Quente")), tz).size)
        assertEquals(1, Pix.possiveisDuplicados(c, listOf(ex(8990, 16, "Pix para Padaria")), tz).size) // +1 dia
        assertEquals(1, Pix.possiveisDuplicados(c, listOf(ex(8990, 14, "Padaria Pão Quente")), tz).size) // -1 dia
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(8990, 17, "Pix para Padaria")), tz).isEmpty()) // 2 dias
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(8991, 15, "Pix para Padaria")), tz).isEmpty()) // outro valor
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(8990, 15, "Pix para Padaria", conta = "999")), tz).isEmpty()) // outra conta
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(8990, 15, "Pix de Padaria", entrada = true)), tz).isEmpty()) // outro sentido
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(8990, 15, "Mercado Central")), tz).isEmpty()) // contraparte diferente
    }

    @Test fun duplicidadeComPixSemNomeOuContraparteDesconhecida() {
        val semNome = Pix.Candidato(1000, false, ms(15), "123", null)
        assertEquals(1, Pix.possiveisDuplicados(semNome, listOf(ex(1000, 15, "Qualquer coisa")), tz).size)
        val comNome = Pix.Candidato(1000, false, ms(15), "123", "Maria Silva")
        assertEquals(1, Pix.possiveisDuplicados(comNome, listOf(ex(1000, 15, "Pix enviado", tags = listOf("PIX"))), tz).size)
        assertTrue(Pix.possiveisDuplicados(comNome, listOf(ex(1000, 15, "Pix enviado")), tz).isEmpty() ||
            Pix.possiveisDuplicados(comNome, listOf(ex(1000, 15, "Pix enviado")), tz).size == 0)
        assertTrue(Pix.possiveisDuplicados(Pix.Candidato(0, false, ms(15), "123", null), listOf(ex(0, 15, "x")), tz).isEmpty())
    }

    @Test fun duplicidadeVirouDiaComFusoLocal() {
        // 23:30 do dia 15 e 00:30 do dia 16 (local) são dias vizinhos mesmo com ~1h de diferença
        val c = Pix.Candidato(500, false, ms(15, 23), "123", null)
        assertEquals(1, Pix.possiveisDuplicados(c, listOf(Pix.Existente(500, false, ms(16, 0) + 30 * 60_000, "123", "x", emptyList())), tz).size)
    }

    // ------------------------------------------------------------------ notificação do banco (R94)

    private fun notif(titulo: String?, texto: String?) = NotificacaoBancaria.interpretar("com.nu.production", titulo, texto, 0L)

    @Test fun notificacaoPixEnviadoComE2e() {
        val s = notif("Pix enviado", "Você enviou R$ 45,00 para MARIA DA SILVA. ID: E18236120202503151732s0123456789")!!
        assertEquals(NotificacaoBancaria.Tipo.PIX_ENVIADO, s.tipo)
        assertEquals(4500L, s.valorCentavos)
        assertEquals("Maria da Silva", s.descricao)
        assertEquals("E18236120202503151732s0123456789", s.e2e)
        assertTrue(s.ehPix)
        assertFalse(s.entrada)
    }

    @Test fun notificacaoPixRecebido() {
        val s = notif("Pix recebido", "Você recebeu um Pix de R$ 100,00 de João Pereira")!!
        assertEquals(NotificacaoBancaria.Tipo.PIX_RECEBIDO, s.tipo)
        assertEquals(10000L, s.valorCentavos)
        assertTrue(s.entrada)
        assertEquals("João Pereira", s.descricao)
        val t = notif(null, "Maria Souza te enviou um Pix de R$ 20,00")!!
        assertEquals(NotificacaoBancaria.Tipo.PIX_RECEBIDO, t.tipo)
        assertEquals("Maria Souza", t.descricao)
        assertNull(t.e2e)
    }

    @Test fun notificacaoPixSemNomeUsaDescricaoGenerica() {
        val s = notif("Pix enviado", "Pix enviado no valor de R$ 8,00")!!
        assertEquals("Pix enviado", s.descricao)
        assertEquals(NotificacaoBancaria.Confianca.MEDIA, s.confianca)
    }

    @Test fun mesmaE2eEhDuplicadaMesmoForaDaJanela() {
        val g = NotificacaoBancaria.SugestaoGuardada(
            id = "1", tipo = NotificacaoBancaria.Tipo.PIX_ENVIADO, valorCentavos = 4500, descricao = "Maria",
            pacote = "com.nu.production", horarioMs = 0, criadaEm = 0, e2e = "E18236120202503151732s0123456789"
        )
        val nova = NotificacaoBancaria.Sugestao(
            NotificacaoBancaria.Tipo.PIX_ENVIADO, 4500, "MARIA DA SILVA", null, NotificacaoBancaria.Confianca.ALTA, e2e = g.e2e
        )
        assertTrue(NotificacaoBancaria.duplicadaRecente(nova, 5L * 3_600_000L, listOf(g)))
        assertFalse(NotificacaoBancaria.duplicadaRecente(nova.copy(e2e = null), 5L * 3_600_000L, listOf(g)))
    }

    @Test fun sugestaoPixGanhaTagNoLancamentoDaRevisaoEmLote() {
        val g = NotificacaoBancaria.SugestaoGuardada(
            id = "1", tipo = NotificacaoBancaria.Tipo.PIX_RECEBIDO, valorCentavos = 1000, descricao = "Ana", entrada = true,
            pacote = "com.nu.production", horarioMs = 1_700_000_000_000, criadaEm = 0
        )
        val d = RevisaoLote.montar(g, RevisaoLote.Edicao("", null, RevisaoLote.Destino(conta = "123")), "pic")
        assertEquals(listOf("PIX"), d.tags)
        assertEquals("Pix de Ana", d.descricao)
        val compra = g.copy(tipo = NotificacaoBancaria.Tipo.COMPRA_DEBITO, entrada = false)
        assertTrue(RevisaoLote.montar(compra, RevisaoLote.Edicao("", "Mercado", RevisaoLote.Destino(conta = "123")), "pic").tags.isEmpty())
    }

    // ------------------------------------------------------------------ ID curto (convenção do portal, R93)

    @Test fun descricaoComIdCurto() {
        assertEquals("8", Pix.idCurto("E18236120202503151732s0123456789")!!.length.toString())
        assertEquals("23456789", Pix.idCurto("E18236120202503151732s0123456789"))
        assertNull(Pix.idCurto("ABC123"))
        assertNull(Pix.idCurto(null))
        assertEquals("Pix para Maria Silva (ID 23456789)", Pix.descricao(Direcao.ENVIADO, "MARIA SILVA", "E18236120202503151732s0123456789"))
        assertEquals("Pix recebido (ID 23456789)", Pix.descricao(Direcao.RECEBIDO, null, "E18236120202503151732s0123456789"))
        assertEquals("23456789", Pix.idDaDescricao("Pix para Maria (ID 23456789)"))
        assertNull(Pix.idDaDescricao("Pix para Maria"))
        assertEquals("Maria Silva", Pix.contraparteDaDescricao("Pix para Maria Silva (ID 23456789)"))
        assertTrue(Pix.descricao(Direcao.ENVIADO, "X".repeat(100) + "Y", "E18236120202503151732s0123456789").length <= Pix.MAX_DESCRICAO)
        val r = Pix.rascunhoPix(comp("Pix enviado\nValor: R$ 9,00\nPara: Ana Paula\nE18236120202503151732s0123456789"), Direcao.ENVIADO)
        assertEquals("Pix para Ana Paula (ID 23456789)", r.descricao)
    }

    @Test fun duplicidadePeloMesmoIdBastaNaMesmaConta() {
        val c = Pix.Candidato(1000, false, ms(15), "123", "Maria", "23456789")
        val outroValorEData = ex(5000, 28, "Pix para Maria (ID 23456789)")
        assertEquals(1, Pix.possiveisDuplicados(c, listOf(outroValorEData), tz).size)
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(5000, 28, "Pix para Maria (ID 23456789)", conta = "999")), tz).isEmpty())
        assertTrue(Pix.possiveisDuplicados(c, listOf(ex(5000, 28, "Pix para Maria (ID 99999999)")), tz).isEmpty())
        // o ID não polui a comparação de nomes
        val semNomeEmComum = ex(1000, 15, "Mercado (ID abcdef12)")
        assertTrue(Pix.possiveisDuplicados(Pix.Candidato(1000, false, ms(15), "123", "Abcdef Souza"), listOf(semNomeEmComum), tz).isEmpty())
    }

    @Test fun comprovanteComDataIso() {
        val c = comp("Pix recebido R$ 3,00 em 2025-03-15 10:20:30 de Pedro Alves")
        val cal = Calendar.getInstance(tz).apply { timeInMillis = c.dataMs!! }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH)); assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun tituloDaSugestaoSegueConvencaoComId() {
        val base = NotificacaoBancaria.SugestaoGuardada(
            id = "1", tipo = NotificacaoBancaria.Tipo.PIX_ENVIADO, valorCentavos = 100, descricao = "Maria da Silva",
            pacote = "p", horarioMs = 0, criadaEm = 0
        )
        assertEquals("Pix para Maria da Silva", NotificacaoBancaria.tituloLancamento(base))
        assertEquals("Pix para Maria da Silva (ID 23456789)", NotificacaoBancaria.tituloLancamento(base.copy(e2e = "E18236120202503151732s0123456789")))
        assertEquals("Pix enviado (ID 23456789)", NotificacaoBancaria.tituloLancamento(base.copy(descricao = "Pix enviado", e2e = "E18236120202503151732s0123456789")))
        assertEquals("Pix de Ana", NotificacaoBancaria.tituloLancamento(base.copy(tipo = NotificacaoBancaria.Tipo.PIX_RECEBIDO, descricao = "Ana")))
    }
}
