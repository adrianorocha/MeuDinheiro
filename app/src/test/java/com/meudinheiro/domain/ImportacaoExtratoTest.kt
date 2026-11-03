package com.meudinheiro.domain

import com.meudinheiro.domain.DestinoSugerido.CartaoRef
import com.meudinheiro.domain.DestinoSugerido.ContaRef
import com.meudinheiro.domain.DestinoSugerido.Destino
import com.meudinheiro.domain.DestinoSugerido.Estado
import com.meudinheiro.domain.ImportacaoExtrato.ArquivoPasta
import com.meudinheiro.domain.ImportacaoExtrato.LancamentoRef
import com.meudinheiro.domain.ImportacaoExtrato.Situacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** R63 — espelha os casos de `portal/src/lib/conciliacao/importacao-extrato.test.ts`. */
class ImportacaoExtratoTest {

    private val contas = listOf(
        ContaRef(1, "Itaú", "0001", "12345-6"),
        ContaRef(2, "Nubank", "0001", "99887-1"),
        ContaRef(3, "Itaú", "0002", "55555-0")
    )
    private val cartoes = listOf(
        CartaoRef(10, "Nubank Platinum", "4321", 2, null),
        CartaoRef(11, "Virtual", "4321", 2, 10)
    )

    private fun ofx(acct: String, extra: String = "", cartao: Boolean = false) =
        if (cartao) "OFXHEADER:100\n<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CCACCTFROM>$extra<ACCTID>$acct</CCACCTFROM>"
        else "OFXHEADER:100\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM>$extra<ACCTID>$acct</BANKACCTFROM>"

    private fun id(nome: String, texto: String) = DestinoSugerido.identificar(contas, cartoes, DestinoSugerido.pistasDoArquivo(nome, texto))

    // ------------------------------------------------------------ sugestão de destino

    @Test fun `OFX com ACCTID igual identifica a conta`() {
        val r = id("extrato.ofx", ofx("123456", "<BANKID>341"))
        assertEquals(Estado.UNICO, r.estado)
        assertEquals(Destino.Conta("12345-6"), r.destino)
    }

    @Test fun `OFX de cartao casa pelo final e ignora virtual`() {
        val r = id("fatura.ofx", ofx("5555444433334321", "", cartao = true))
        assertEquals(Estado.UNICO, r.estado)
        assertEquals(Destino.Cartao(10), r.destino)
    }

    @Test fun `pistas do OFX trazem BANKID ORG ACCTID e tipo`() {
        val p = DestinoSugerido.pistasDoOfx(ofx("777", "<BANKID>260<ORG>NU PAGAMENTOS", cartao = true))
        assertEquals("777", p.acctId); assertEquals("260", p.bankId); assertEquals(true, p.ehCartao)
    }

    @Test fun `CSV sem dados nao identifica nada`() {
        val r = id("export.csv", "Data;Descricao;Valor\n01/03/2025;Mercado;-10,00")
        assertEquals(Estado.NENHUM, r.estado)
        assertNull(r.destino)
        assertTrue(r.candidatos.isEmpty())
    }

    @Test fun `CSV com nome do banco no arquivo e unico quando ha uma conta do banco`() {
        val r = id("nubank_marco.csv", "Data;Descricao;Valor")
        assertEquals(Estado.UNICO, r.estado)
        assertEquals(Destino.Conta("99887-1"), r.destino)
    }

    @Test fun `ambiguo quando duas contas do mesmo banco e sem numero`() {
        val r = id("itau.csv", "Data;Descricao;Valor")
        assertEquals(Estado.AMBIGUO, r.estado)
        assertNull(r.destino)
        assertEquals(listOf(Destino.Conta("12345-6"), Destino.Conta("55555-0")), r.candidatos.map { it.destino })
    }

    @Test fun `CSV de fatura com final do cartao no nome`() {
        assertEquals(Destino.Cartao(10), id("fatura_4321.csv", "Data;Descricao;Valor").destino)
    }

    // ------------------------------------------------------------ validação

    @Test fun `valida extensao tamanho e MIME`() {
        assertTrue(ImportacaoExtrato.validarArquivo("a.OFX", 100, null).ok)
        assertTrue(ImportacaoExtrato.validarArquivo("a.csv", 100, "text/csv; charset=utf-8").ok)
        assertTrue(ImportacaoExtrato.validarArquivo("a.ofx", 100, "application/octet-stream").ok)
        assertFalse(ImportacaoExtrato.validarArquivo("a.pdf", 100, null).ok)
        assertFalse(ImportacaoExtrato.validarArquivo("a.exe", 100, null).ok)
        assertFalse(ImportacaoExtrato.validarArquivo("a.ofx", 0, null).ok)
        assertFalse(ImportacaoExtrato.validarArquivo("a.ofx", ImportacaoExtrato.MAX_BYTES + 1, null).ok)
        assertTrue(ImportacaoExtrato.validarArquivo("a.ofx", ImportacaoExtrato.MAX_BYTES, null).ok)
        assertFalse(ImportacaoExtrato.validarArquivo("a.csv", 10, "image/png").ok)
    }

    @Test fun `nome nunca vira caminho`() {
        assertEquals("passwd.ofx", ImportacaoExtrato.sanitizarNome("../../etc/passwd.ofx"))
        assertEquals("a b.csv", ImportacaoExtrato.sanitizarNome("C:\\x\\a b.csv"))
        assertEquals("extrato", ImportacaoExtrato.sanitizarNome(null))
        assertEquals("extrato", ImportacaoExtrato.sanitizarNome("///"))
    }

    @Test fun `conteudo precisa parecer texto e OFX para extensao ofx`() {
        assertFalse(ImportacaoExtrato.conteudoValido("a.ofx", byteArrayOf(1, 0, 2)).ok)
        assertFalse(ImportacaoExtrato.conteudoValido("a.ofx", "isto nao e ofx".toByteArray()).ok)
        assertTrue(ImportacaoExtrato.conteudoValido("a.ofx", ofx("1").toByteArray()).ok)
        assertTrue(ImportacaoExtrato.conteudoValido("a.csv", "a;b\n1;2".toByteArray()).ok)
    }

    // ------------------------------------------------------------ pasta monitorada

    @Test fun `arquivos novos por nome tamanho e lastModified`() {
        val a = ArquivoPasta("jan.ofx", 100, 10)
        val lista = listOf(a, ArquivoPasta("fev.csv", 50, 20), ArquivoPasta("nota.pdf", 5, 5), ArquivoPasta("foto.png", 5, 1), ArquivoPasta("vazio.ofx", 0, 3))
        val vistos = setOf(ImportacaoExtrato.carimbo(a))
        assertEquals(listOf("fev.csv"), ImportacaoExtrato.arquivosNovos(lista, vistos).map { it.nome })
        assertEquals(1, ImportacaoExtrato.arquivosNovos(listOf(ArquivoPasta("jan.ofx", 100, 11)), vistos).size)
        assertEquals(1, ImportacaoExtrato.arquivosNovos(listOf(ArquivoPasta("jan.ofx", 101, 10)), vistos).size)
        assertTrue(ImportacaoExtrato.arquivosNovos(lista, lista.map { ImportacaoExtrato.carimbo(it) }.toSet()).isEmpty())
        assertTrue(ImportacaoExtrato.arquivosNovos(listOf(ArquivoPasta("grande.ofx", ImportacaoExtrato.MAX_BYTES + 1, 1)), emptySet()).isEmpty())
    }

    // ------------------------------------------------------------ OFX e idempotência por fitid

    private val corpo = """
        OFXHEADER:100
        <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM><BANKID>341<ACCTID>123456</BANKACCTFROM>
        <BANKTRANLIST>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250310120000[-3:BRT]<TRNAMT>-100.00<FITID>a1<NAME>MERCADO ALFA</STMTTRN>
        <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20250312<TRNAMT>2.500,00<FITID>a2<MEMO>SALARIO</STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250315<TRNAMT>-12.50<NAME>SEM ID</STMTTRN>
        <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250316<TRNAMT>-5.00<FITID>a1<NAME>REPETIDO</STMTTRN>
        </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
    """.trimIndent()

    private fun dia(a: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(a, m - 1, d, 12, 0, 0) }.timeInMillis

    @Test fun `le OFX com sinais datas e fitids`() {
        val e = ImportacaoExtrato.lerOfx(corpo)
        assertEquals(listOf("a1", "a2", "a1#2"), e.transacoes.map { it.fitid })
        assertEquals(-100.0, e.transacoes[0].valor, 0.001)
        assertEquals(2500.0, e.transacoes[1].valor, 0.001)
        assertEquals(dia(2025, 3, 10), e.transacoes[0].dataMs)
        assertEquals(1, e.semFitid)
        assertEquals("123456", e.acctId)
        assertFalse(e.ehCartao)
    }

    @Test fun `arquivo repetido nao duplica pela regra de fitid`() {
        val e = ImportacaoExtrato.lerOfx(corpo)
        // 1ª importação: tudo é novo
        val primeira = e.transacoes.map { ImportacaoExtrato.situacao(it, "111", emptyList()) }
        assertTrue(primeira.all { it == Situacao.NOVA })
        // o que foi lançado entra com fitid; a 2ª importação do mesmo arquivo marca tudo como já importado
        val lancados = e.transacoes.map { LancamentoRef("111", Math.abs(it.valor), it.valor > 0, it.dataMs, it.fitid, null) }
        assertTrue(e.transacoes.all { ImportacaoExtrato.situacao(it, "111", lancados) == Situacao.JA_IMPORTADA })
        // em outra conta, o mesmo fitid não conta como duplicado
        assertTrue(e.transacoes.all { ImportacaoExtrato.situacao(it, "222", lancados) == Situacao.NOVA })
    }

    @Test fun `lancamento manual parecido vira possivel duplicada`() {
        val t = ImportacaoExtrato.lerOfx(corpo).transacoes[0]
        val manual = LancamentoRef("111", 100.0, false, t.dataMs + 86_400_000L, null, null)
        assertEquals(Situacao.POSSIVEL_DUPLICADA, ImportacaoExtrato.situacao(t, "111", listOf(manual)))
        assertEquals(Situacao.NOVA, ImportacaoExtrato.situacao(t, "111", listOf(manual.copy(dataMs = t.dataMs + 10 * 86_400_000L))))
        assertEquals(Situacao.NOVA, ImportacaoExtrato.situacao(t, "111", listOf(manual.copy(valor = 101.0))))
    }
}
