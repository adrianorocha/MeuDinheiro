package com.meudinheiro.funcoes

import com.meudinheiro.domain.Bcb
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Fake da porta de rede: registra TODAS as URLs pedidas (a interface só tem `get(url)`) e responde por trecho da URL. */
internal class FakeBcbHttp(private val resposta: suspend (String) -> String) : BcbHttp {
    val urls = mutableListOf<String>()
    override suspend fun get(url: String): String {
        urls += url
        return resposta(url)
    }
}

internal val TZ_UTC: TimeZone = TimeZone.getTimeZone("UTC")

internal fun msUtc(a: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
    Calendar.getInstance(TZ_UTC).apply { clear(); set(a, m - 1, d, h, min, 0) }.timeInMillis

internal fun sgsJson(data: String, valor: String) = """[{"data":"$data","valor":"$valor"}]"""

internal fun boletimJson(dataHora: String, venda: Double, tipo: String) =
    """{"cotacaoCompra":${venda - 0.002},"cotacaoVenda":$venda,"paridadeCompra":1,"paridadeVenda":1,"dataHoraCotacao":"$dataHora.600448","tipoBoletim":"$tipo"}"""

/** R100 — cliente do BCB com rede falsa: falha parcial, timeout, URLs fixas e nenhum dado do usuário. */
class BcbClientTest {

    private val agora = msUtc(2026, 10, 28, 14, 30)
    private fun cliente(http: BcbHttp, timeout: Long = Bcb.TIMEOUT_MS) = BcbClient(http, { agora }, { TZ_UTC }, timeout)

    private val selicMeta = """[{"data":"02/11/2026","valor":"13.75"},{"data":"03/11/2026","valor":"13.75"},{"data":"27/10/2026","valor":"13.75"}]"""

    @Test fun `falha parcial uma serie com erro nao derruba as outras`() = runTest {
        val http = FakeBcbHttp { url ->
            when {
                url.contains("sgs.433/") -> throw BcbErro("HTTP 500")
                url.contains("sgs.432/") -> selicMeta
                url.contains("sgs.1619/") -> sgsJson("01/10/2026", "1621.00")
                url.contains("sgs.190/") -> "[]"
                else -> sgsJson("01/09/2026", "0.5")
            }
        }
        val s = cliente(http).buscarIndices()
        assertNull(s.itens["ipca"])
        assertEquals(listOf(0.5), s.itens["igpm"]?.serie)
        assertFalse(s.itens["tr"]!!.parcial) // 09/2026 nao e o mes corrente; e TR nunca e "parcial"
        assertFalse(s.itens["selic"]!!.parcial) // a amostra de teste e 09/2026
        assertEquals(13.75, s.selicMeta!!.valor, 0.0)
        assertEquals(162100L, s.salarioMinimo!!.centavos)
        assertTrue(s.falhas.any { it.fonte.contains("IPCA (SGS 433)") && it.motivo == "HTTP 500" })
        assertTrue(s.falhas.any { it.fonte.contains("IGP-DI") && it.motivo == "sem valor válido" })
        assertEquals(2, s.falhas.size)
        assertEquals(agora, s.atualizadoEm)
        assertEquals(13, http.urls.size)
    }

    @Test fun `Selic e CDI no mes corrente sao parciais e TR nao`() = runTest {
        val http = FakeBcbHttp { sgsJson("01/10/2026", "0.92") }
        val s = cliente(http).buscarIndices()
        assertTrue(s.itens["selic"]!!.parcial)
        assertTrue(s.itens["cdi"]!!.parcial)
        assertFalse(s.itens["tr"]!!.parcial)
        assertFalse(s.itens["poupanca"]!!.parcial)
        assertEquals("10/2026", s.itens["selic"]!!.competencia)
    }

    @Test fun `todas as falhas dao snapshot vazio`() = runTest {
        val s = cliente(FakeBcbHttp { throw BcbErro("sem conexão") }).buscarIndices()
        assertTrue(s.vazio)
        assertEquals(13, s.falhas.size)
        assertTrue(s.falhas.all { it.motivo == "sem conexão" })
    }

    @Test fun `somente URLs fixas dos dois hosts sem nenhum dado do usuario`() = runTest {
        val http = FakeBcbHttp { url -> if (url.contains("olinda")) """{"value":[]}""" else "[]" }
        val c = cliente(http)
        c.buscarIndices()
        c.buscarCotacoes()
        runCatching { c.buscarHistorico("USD") }
        val sgs = Regex("^https://api\\.bcb\\.gov\\.br/dados/serie/bcdata\\.sgs\\.\\d+/dados/ultimos/\\d+\\?formato=json$")
        val ptax = Regex(
            "^https://olinda\\.bcb\\.gov\\.br/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaPeriodo\\(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao\\)" +
                "\\?@moeda='[A-Z]{3}'&@dataInicial='\\d{2}-\\d{2}-\\d{4}'&@dataFinalCotacao='\\d{2}-\\d{2}-\\d{4}'&\\\$orderby=dataHoraCotacao%20desc&\\\$format=json$"
        )
        assertEquals(13 + 10 + 1, http.urls.size)
        for (u in http.urls) {
            assertTrue(u, u.startsWith("https://"))
            assertTrue(u, Bcb.urlPermitida(u))
            assertTrue(u, sgs.matches(u) || ptax.matches(u))
        }
        // as URLs so variam por codigo da serie, moeda e datas: nada que venha do usuario
        assertEquals(13, http.urls.count { sgs.matches(it) })
        assertEquals(11, http.urls.count { ptax.matches(it) })
    }

    @Test fun `URL fora da lista permitida e recusada antes de ir a rede`() = runTest {
        // um cliente nao envia nada se a URL nao for dos hosts do BCB (defesa em profundidade; as URLs sao geradas por Bcb)
        assertFalse(Bcb.urlPermitida("http://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados/ultimos/8?formato=json"))
        val http = FakeBcbHttp { "[]" }
        val r = runCatching { cliente(http).buscarHistorico("XXX") }
        assertTrue(r.exceptionOrNull() is BcbErro)
        assertEquals("moeda inválida", r.exceptionOrNull()?.message)
        assertTrue(http.urls.isEmpty())
    }

    @Test fun `timeout de 8 s por requisicao vira falha isolada`() = runTest {
        val http = FakeBcbHttp { delay(Long.MAX_VALUE); "[]" }
        val s = cliente(http).buscarIndices()
        assertTrue(s.vazio)
        assertEquals(13, s.falhas.size)
        assertTrue(s.falhas.all { it.motivo == "tempo esgotado" })
        assertEquals(8000L, Bcb.TIMEOUT_MS)
    }

    @Test fun `cotacoes falha em uma moeda nao derruba as outras`() = runTest {
        val http = FakeBcbHttp { url ->
            when {
                url.contains("'EUR'") -> throw BcbErro("HTTP 503")
                url.contains("'JPY'") -> """{"value":[]}"""
                else -> """{"value":[${boletimJson("2026-10-27 13:05:00", 5.34, "Fechamento")},${boletimJson("2026-10-26 13:05:00", 5.3, "Fechamento")}]}"""
            }
        }
        val s = cliente(http).buscarCotacoes()
        assertEquals(8, s.cotacoes.size)
        assertTrue(s.falhas.any { it.fonte.contains("EUR") && it.motivo == "HTTP 503" })
        assertTrue(s.falhas.any { it.fonte.contains("JPY") })
        val usd = s.cotacoes.first { it.moeda == "USD" }
        assertEquals((5.34 / 5.3 - 1) * 100, usd.variacaoPct!!, 1e-8)
    }

    @Test fun `historico devolve os fechamentos em ordem cronologica`() = runTest {
        val http = FakeBcbHttp {
            """{"value":[${boletimJson("2026-10-27 13:05:00", 5.1, "Fechamento")},${boletimJson("2026-10-27 10:00:00", 5.05, "Intermediário")},${boletimJson("2026-10-26 13:05:00", 4.9, "Fechamento")}]}"""
        }
        val h = cliente(http).buscarHistorico("USD")
        assertEquals(listOf(Bcb.PontoHistorico("2026-10-26", 4.9), Bcb.PontoHistorico("2026-10-27", 5.1)), h)
        assertTrue(http.urls.single().contains("@dataInicial='09-13-2026'")) // 45 dias antes de 28/10
    }
}
