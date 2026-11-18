package com.meudinheiro.domain

import com.meudinheiro.domain.NotificacaoBancaria.Confianca
import com.meudinheiro.domain.NotificacaoBancaria.Estado
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.domain.NotificacaoBancaria.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class NotificacaoBancariaTest {

    private val nu = "com.nu.production"
    private val agora = 1_760_000_000_000L
    private val utc = TimeZone.getTimeZone("UTC")

    private fun p(titulo: String?, texto: String?) = NotificacaoBancaria.interpretar(nu, titulo, texto, agora)

    // ------------------------------------------------------------------ parser: positivos

    @Test fun pixRecebidoComNome() {
        val s = p("Pix recebido", "Você recebeu um Pix de R$ 150,00 de João Silva")!!
        assertEquals(Tipo.PIX_RECEBIDO, s.tipo); assertEquals(15000L, s.valorCentavos)
        assertEquals("João Silva", s.descricao); assertTrue(s.entrada); assertEquals(Confianca.ALTA, s.confianca)
    }

    @Test fun pixRecebidoNomeAntesDoValor() {
        val s = p(null, "Pix recebido de Maria Souza: R$ 80,50")!!
        assertEquals(Tipo.PIX_RECEBIDO, s.tipo); assertEquals(8050L, s.valorCentavos); assertEquals("Maria Souza", s.descricao)
    }

    @Test fun pixEnviado() {
        val s = p("Pix enviado", "Pix enviado: R$ 45,90 para Mercado X")!!
        assertEquals(Tipo.PIX_ENVIADO, s.tipo); assertEquals(4590L, s.valorCentavos)
        assertEquals("Mercado X", s.descricao); assertFalse(s.entrada)
    }

    @Test fun pixEnviadoSemNome() {
        val s = p("Pix", "Você enviou R$ 20,00 por Pix")!!
        assertEquals(Tipo.PIX_ENVIADO, s.tipo); assertEquals(2000L, s.valorCentavos); assertEquals(Confianca.MEDIA, s.confianca)
    }

    @Test fun compraAprovadaComLocalEFinal() {
        val s = p("Compra aprovada", "Compra aprovada R$ 89,90 em PADARIA X no cartão final 1234")!!
        assertEquals(Tipo.COMPRA_CREDITO, s.tipo); assertEquals(8990L, s.valorCentavos)
        assertEquals("PADARIA X", s.descricao); assertEquals("1234", s.finalCartao); assertEquals(Confianca.ALTA, s.confianca)
    }

    @Test fun compraDebitoSemLocal() {
        val s = p("Compra no débito", "Compra no débito de R$ 32,00")!!
        assertEquals(Tipo.COMPRA_DEBITO, s.tipo); assertEquals(3200L, s.valorCentavos); assertNull(s.finalCartao)
    }

    @Test fun compraCreditoExplicita() {
        val s = p("Compra no crédito", "Compra no crédito aprovada: R$ 120,00 em LOJA Y")!!
        assertEquals(Tipo.COMPRA_CREDITO, s.tipo); assertEquals(12000L, s.valorCentavos); assertEquals("LOJA Y", s.descricao)
    }

    @Test fun compraSemModalidadeNemFinalTemConfiancaBaixa() {
        val s = p(null, "Compra aprovada de R$ 20,00")!!
        assertEquals(Tipo.COMPRA_CREDITO, s.tipo); assertEquals(Confianca.BAIXA, s.confianca)
    }

    @Test fun compraFinalComAsteriscos() {
        val s = p("Itaú", "Compra aprovada R$ 15,00 em CAFE Z cartão final **4321")!!
        assertEquals("4321", s.finalCartao); assertEquals("CAFE Z", s.descricao)
    }

    @Test fun saque() {
        val s = p("Saque realizado", "Saque de R$ 200,00")!!
        assertEquals(Tipo.SAQUE, s.tipo); assertEquals(20000L, s.valorCentavos)
    }

    @Test fun boletoPago() {
        val s = p("Boleto", "Boleto de R$ 350,00 pago")!!
        assertEquals(Tipo.BOLETO_PAGO, s.tipo); assertEquals(35000L, s.valorCentavos)
    }

    @Test fun valorComMilhar() {
        val s = p("Pix recebido", "Você recebeu um Pix de R$ 1.234,56 de Ana Lima")!!
        assertEquals(123456L, s.valorCentavos)
    }

    @Test fun valorComMilharSemCentavos() {
        assertEquals(350000L, p("Saque", "Saque de R$ 3.500")!!.valorCentavos)
    }

    @Test fun valorColadoSemEspaco() {
        assertEquals(4590L, p("Pix enviado", "Pix enviado de R$45,90 para Loja")!!.valorCentavos)
    }

    @Test fun valorSemCentavosEUmaCasa() {
        assertEquals(1200L, p(null, "Compra no débito de R$ 12,0")!!.valorCentavos)
        assertEquals(2500L, p(null, "Compra no débito de R$ 25")!!.valorCentavos)
    }

    @Test fun quebrasDeLinha() {
        val s = p("Pix recebido\n", "Você recebeu\num Pix de R$ 99,00\nde Carlos Dias")!!
        assertEquals(9900L, s.valorCentavos); assertEquals("Carlos Dias", s.descricao)
    }

    @Test fun transferenciaRecebida() {
        val s = p("Transferência recebida", "Você recebeu uma transferência de R$ 500,00 de Empresa SA")!!
        assertEquals(Tipo.TRANSFERENCIA, s.tipo); assertTrue(s.entrada); assertEquals(50000L, s.valorCentavos)
    }

    @Test fun transferenciaEnviada() {
        val s = p("Transferência enviada", "Transferência de R$ 300,00 enviada para Pedro Alves")!!
        assertEquals(Tipo.TRANSFERENCIA, s.tipo); assertFalse(s.entrada); assertEquals("Pedro Alves", s.descricao)
    }

    @Test fun pagamentoGenericoEhOutro() {
        val s = p("Pagamento efetuado", "Pagamento efetuado de R$ 75,00")!!
        assertEquals(Tipo.OUTRO, s.tipo); assertEquals(Confianca.BAIXA, s.confianca)
    }

    @Test fun ignoraSaldoELimiteNaMesmaNotificacao() {
        val s = p("Compra aprovada", "Compra no débito de R$ 32,00 em MERCADO. Saldo: R$ 1.000,00. Limite R$ 5.000,00")!!
        assertEquals(3200L, s.valorCentavos); assertEquals("MERCADO", s.descricao)
    }

    // ------------------------------------------------------------------ parser: negativos

    @Test fun promocaoEhIgnorada() {
        assertNull(p("Oferta para você", "Aproveite R$ 50,00 de desconto na sua próxima compra"))
        assertNull(p("Promoção", "Compre com cashback de R$ 10,00"))
    }

    @Test fun limiteSaldoFaturaNaoSaoLancamentos() {
        assertNull(p("Seu limite", "Seu limite disponível é de R$ 5.000,00"))
        assertNull(p("Saldo", "Seu saldo em conta é R$ 1.234,56"))
        assertNull(p("Fatura fechada", "Sua fatura fechou em R$ 1.200,00"))
        assertNull(p("Fatura", "Sua fatura de R$ 1.200,00 vence amanhã"))
        assertNull(p("Boleto", "Boleto de R$ 350,00 vence amanhã"))
    }

    @Test fun compraNegadaOuAgendadaOuCobranca() {
        assertNull(p("Compra negada", "Compra de R$ 89,90 negada em LOJA"))
        assertNull(p("Pix agendado", "Pix agendado de R$ 100,00 para Fulano"))
        assertNull(p("Cobrança", "Fulano solicitou um Pix de R$ 20,00"))
        assertNull(p("Atenção", "Você reconhece a compra de R$ 300,00?"))
    }

    @Test fun ambiguosOuSemValorViramNull() {
        assertNull(p("Pix", "Você tem um novo Pix"))
        assertNull(p("Banco", "Olá! Confira as novidades do app"))
        assertNull(p("Pix", "Pix de R$ 10,00 ")) // sem sentido
        assertNull(p("Compra", "Compra de R$ 10,00 no débito e no crédito"))
        assertNull(p(null, "Compra de R$ 10,00 ou R$ 20,00"))
        assertNull(p("Saque", "Saque de R$ 0,00"))
        assertNull(p("", ""))
        assertNull(p(null, null))
    }

    @Test fun codigoDeSegurancaEEmprestimoIgnorados() {
        assertNull(p("Código", "Seu código de segurança é 123456. Compra de R$ 10,00"))
        assertNull(p("Empréstimo", "Você tem R$ 3.000,00 pré-aprovados"))
    }

    @Test fun valorAbsurdoEhRecusado() {
        assertNull(p("Saque", "Saque de R$ 999.999.999.999,00"))
    }

    @Test fun textoGiganteEhRecusado() {
        assertNull(p("Pix", "Pix de R$ 10,00 de " + "A".repeat(2000)))
    }

    @Test fun descricaoLimitadaA60() {
        val s = p("Pix recebido", "Você recebeu um Pix de R$ 10,00 de " + "Nome ".repeat(30))!!
        assertTrue(s.descricao.length <= 60)
    }

    // ------------------------------------------------------------------ lista permitida

    @Test fun filtroPadraoAceitaBancosConhecidos() {
        val padrao = NotificacaoBancaria.PACOTES_PADRAO
        assertTrue(NotificacaoBancaria.pacoteAceito("com.nu.production", padrao))
        assertTrue(NotificacaoBancaria.pacoteAceito("br.com.sicoobnet.android", padrao))
        assertTrue(NotificacaoBancaria.pacoteAceito("COM.PICPAY", padrao))
    }

    @Test fun filtroDescartaPacoteForaDaLista() {
        val padrao = NotificacaoBancaria.PACOTES_PADRAO
        assertFalse(NotificacaoBancaria.pacoteAceito("com.whatsapp", padrao))
        assertFalse(NotificacaoBancaria.pacoteAceito("com.nu.production.fake", padrao)) // não é prefixo livre
        assertFalse(NotificacaoBancaria.pacoteAceito("com.itau.evil", padrao))
        assertFalse(NotificacaoBancaria.pacoteAceito("br.com.sicoobnet", padrao)) // sem o ponto final do prefixo
        assertFalse(NotificacaoBancaria.pacoteAceito(null, padrao))
        assertFalse(NotificacaoBancaria.pacoteAceito("", padrao))
        assertFalse(NotificacaoBancaria.pacoteAceito("com.nu.production", emptyList()))
        assertFalse(NotificacaoBancaria.pacoteAceito("x.y", listOf(".*", " ")))
    }

    @Test fun pacoteValido() {
        assertTrue(NotificacaoBancaria.pacoteValido("com.banco.app"))
        assertTrue(NotificacaoBancaria.pacoteValido("br.com.x.*"))
        assertFalse(NotificacaoBancaria.pacoteValido("semponto"))
        assertFalse(NotificacaoBancaria.pacoteValido("com..x"))
        assertFalse(NotificacaoBancaria.pacoteValido("com.x y"))
    }

    // ------------------------------------------------------------------ deduplicação

    private fun guardada(id: String = "1", tipo: Tipo = Tipo.PIX_ENVIADO, valor: Long = 4590, desc: String = "Mercado X", horario: Long = agora, criada: Long = agora, estado: Estado = Estado.PENDENTE) =
        SugestaoGuardada(id, tipo, valor, desc, null, Confianca.ALTA, false, nu, horario, criada, estado)

    private val sug = NotificacaoBancaria.Sugestao(Tipo.PIX_ENVIADO, 4590, "mercado x", null, Confianca.ALTA)

    @Test fun duplicadaEmDezMinutos() {
        val reg = listOf(guardada(horario = agora))
        assertTrue(NotificacaoBancaria.duplicadaRecente(sug, agora + 9 * 60_000, reg))
        assertTrue(NotificacaoBancaria.duplicadaRecente(sug, agora + 10 * 60_000, reg))
        assertFalse(NotificacaoBancaria.duplicadaRecente(sug, agora + 10 * 60_000 + 1, reg))
    }

    @Test fun naoEhDuplicadaSeValorTipoOuDescricaoMudam() {
        val reg = listOf(guardada())
        assertFalse(NotificacaoBancaria.duplicadaRecente(sug.copy(valorCentavos = 4591), agora, reg))
        assertFalse(NotificacaoBancaria.duplicadaRecente(sug.copy(tipo = Tipo.PIX_RECEBIDO), agora, reg))
        assertFalse(NotificacaoBancaria.duplicadaRecente(sug.copy(descricao = "Outro"), agora, reg))
        assertTrue(NotificacaoBancaria.duplicadaRecente(sug.copy(descricao = "MERCADO  X!"), agora, reg)) // normalizada
    }

    @Test fun duplicadaConsideraIgnoradasTambem() {
        assertTrue(NotificacaoBancaria.duplicadaRecente(sug, agora, listOf(guardada(estado = Estado.IGNORADA))))
    }

    private val dia = 86_400_000L
    private val destinoConta = NotificacaoBancaria.Destino(null, "111")

    @Test fun jaLancadoNaMesmaContaEmMaisOuMenosUmDia() {
        val base = 20_000 * dia + 12 * 3_600_000L // meio-dia UTC
        fun l(dMs: Long, valor: Long = 4590, entrada: Boolean = false, conta: String? = "111") =
            NotificacaoBancaria.LancamentoRecente(valor, entrada, dMs, conta)
        assertTrue(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base - dia)), utc))
        assertTrue(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base + dia)), utc))
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base + 2 * dia)), utc))
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base, valor = 4591)), utc))
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base, entrada = true)), utc))
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, destinoConta, listOf(l(base, conta = "222")), utc))
        assertTrue(NotificacaoBancaria.jaLancado(sug, base, null, listOf(l(base, conta = "222")), utc)) // destino desconhecido
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, destinoConta, emptyList(), utc))
    }

    @Test fun jaLancadoUsaDiaDoCalendarioLocal() {
        val sp = TimeZone.getTimeZone("America/Sao_Paulo")
        val noite = 20_000 * dia + 1 * 3_600_000L // 22h do dia anterior em SP
        val l = NotificacaoBancaria.LancamentoRecente(4590, false, noite + 3 * dia, "111")
        assertFalse(NotificacaoBancaria.jaLancado(sug, noite, destinoConta, listOf(l), sp))
    }

    @Test fun jaLancadoNoCartao() {
        val base = 20_000 * dia
        val d = NotificacaoBancaria.Destino(7, "111")
        assertTrue(NotificacaoBancaria.jaLancado(sug, base, d, listOf(NotificacaoBancaria.LancamentoRecente(4590, false, base, "111", 7)), utc))
        assertFalse(NotificacaoBancaria.jaLancado(sug, base, d, listOf(NotificacaoBancaria.LancamentoRecente(4590, false, base, "222", 8)), utc))
    }

    @Test fun notificacaoRepetidaDoMesmoAppNaoDuplica() {
        val v = NotificacaoBancaria.ChavesVistas()
        val k1 = NotificacaoBancaria.chaveNotificacao(nu, 5, "Pix", "texto")
        assertTrue(v.registrar(k1, agora))
        assertFalse(v.registrar(NotificacaoBancaria.chaveNotificacao(nu, 5, "Pix", "texto"), agora + 1))
        assertNotEquals(k1, NotificacaoBancaria.chaveNotificacao(nu, 6, "Pix", "texto"))
        assertNotEquals(k1, NotificacaoBancaria.chaveNotificacao("com.itau", 5, "Pix", "texto"))
        assertNotEquals(k1, NotificacaoBancaria.chaveNotificacao(nu, 5, "Pix", "outro"))
        assertEquals(20, k1.length)
    }

    @Test fun chavesVistasTemLimite() {
        val v = NotificacaoBancaria.ChavesVistas(3)
        listOf("a", "b", "c", "d").forEach { assertTrue(v.registrar(it, 0)) }
        assertTrue(v.registrar("a", 0)) // "a" já saiu da memória
    }

    // ------------------------------------------------------------------ expiração / limite / serialização

    @Test fun expiraEmSeteDias() {
        val g = guardada(criada = agora)
        assertFalse(NotificacaoBancaria.expirada(g, agora + 7 * dia - 1))
        assertTrue(NotificacaoBancaria.expirada(g, agora + 7 * dia))
        val lista = listOf(guardada("velha", criada = agora - 8 * dia), guardada("nova", criada = agora - dia))
        assertEquals(listOf("nova"), NotificacaoBancaria.manter(lista, agora).map { it.id })
    }

    @Test fun filaLimitadaA200MantendoAsMaisRecentes() {
        val lista = (1..250).map { guardada("id$it", criada = agora - it * 1000L) }
        val m = NotificacaoBancaria.manter(lista, agora)
        assertEquals(200, m.size)
        assertEquals("id1", m.first().id)
        assertEquals("id200", m.last().id)
    }

    @Test fun serializacaoIdaEVolta() {
        val g = listOf(
            guardada("a", Tipo.COMPRA_CREDITO, 8990, "PADARIA X").copy(finalCartao = "1234"),
            guardada("b", Tipo.PIX_RECEBIDO, 15000, "João", estado = Estado.IGNORADA).copy(entrada = true)
        )
        val json = NotificacaoBancaria.serializar(g)
        assertEquals(g, NotificacaoBancaria.desserializar(json))
    }

    @Test fun serializacaoNaoTemTextoBrutoNemCamposExtras() {
        val json = NotificacaoBancaria.serializar(listOf(guardada()))
        assertFalse(json.contains("titulo")); assertFalse(json.contains("texto"))
        assertNotNull(NotificacaoBancaria.desserializar(json).firstOrNull())
    }

    @Test fun desserializarToleraLixo() {
        assertTrue(NotificacaoBancaria.desserializar(null).isEmpty())
        assertTrue(NotificacaoBancaria.desserializar("").isEmpty())
        assertTrue(NotificacaoBancaria.desserializar("{não é json").isEmpty())
        assertTrue(NotificacaoBancaria.desserializar("[1,2]").isEmpty())
        val invalido = """{"versao":1,"itens":[{"id":"x","tipo":"INEXISTENTE","valorCentavos":5,"descricao":"d","pacote":"p","horarioMs":1,"criadaEm":1,"estado":"PENDENTE","confianca":"ALTA","entrada":false},
            {"id":"y","tipo":"SAQUE","valorCentavos":-5,"descricao":"d","pacote":"p","horarioMs":1,"criadaEm":1,"estado":"PENDENTE","confianca":"ALTA","entrada":false},
            {"id":"ok","tipo":"SAQUE","valorCentavos":500,"descricao":"Saque","pacote":"p","horarioMs":1,"criadaEm":1,"estado":"PENDENTE","confianca":"ALTA","entrada":false}]}"""
        assertEquals(listOf("ok"), NotificacaoBancaria.desserializar(invalido).map { it.id })
    }

    // ------------------------------------------------------------------ destino

    @Test fun destinoPeloFinalDoCartaoEPeloBanco() {
        val cartoes = listOf(NotificacaoBancaria.CartaoRef(7, "1234", "111"), NotificacaoBancaria.CartaoRef(8, "9999", "222"))
        val contas = listOf(NotificacaoBancaria.ContaRef("111", "Nubank"), NotificacaoBancaria.ContaRef("222", "Itaú"))
        val compra = NotificacaoBancaria.Sugestao(Tipo.COMPRA_CREDITO, 100, "x", "1234", Confianca.ALTA)
        assertEquals(NotificacaoBancaria.Destino(7, "111"), NotificacaoBancaria.resolverDestino(compra, nu, cartoes, contas))
        val pix = NotificacaoBancaria.Sugestao(Tipo.PIX_ENVIADO, 100, "x", null, Confianca.ALTA)
        assertEquals(NotificacaoBancaria.Destino(null, "111"), NotificacaoBancaria.resolverDestino(pix, nu, cartoes, contas))
        assertEquals(NotificacaoBancaria.Destino(null, "222"), NotificacaoBancaria.resolverDestino(pix, "com.itau", cartoes, contas))
        assertNull(NotificacaoBancaria.resolverDestino(pix, "com.whatsapp", cartoes, contas))
        assertNull(NotificacaoBancaria.resolverDestino(pix, nu, cartoes, contas + NotificacaoBancaria.ContaRef("333", "Nu Conta 2")))
    }
}
