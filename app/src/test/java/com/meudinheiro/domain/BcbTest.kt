package com.meudinheiro.domain

import com.meudinheiro.domain.Bcb.SnapshotIndices
import com.meudinheiro.domain.IndicesEconomicos.Origem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** R100 — mesmos cenários de portal/src/lib/finance/bcb.test.ts (R99), com as amostras reais de 28/10/2026. */
class BcbTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val hoje = "2026-10-28"
    private fun ms(a: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(a, m - 1, d, h, min, 0) }.timeInMillis

    private val agora = ms(2026, 10, 28, 14, 30)

    /** Monta a resposta do SGS: itens (data, valor-string), como a API devolve. */
    private fun sgs(vararg itens: Pair<String, String>) =
        itens.joinToString(",", "[", "]") { "{\"data\":\"${it.first}\",\"valor\":\"${it.second}\"}" }

    private fun pontos(vararg itens: Pair<String, String>) = Bcb.parsePontosSgs(sgs(*itens))

    // ------------------------------------------------------------------ parsing SGS

    @Test fun `amostra real do SGS e lida`() {
        assertEquals(listOf(Bcb.PontoSgs("2026-09-01", 0.82)), Bcb.parsePontosSgs("""[{"data":"01/09/2026","valor":"0.82"}]"""))
    }

    @Test fun `le itens reais (valor string, dataFim ignorada) e descarta lixo`() {
        val r = Bcb.parsePontosSgs(
            """[{"data":"25/10/2026","dataFim":"25/11/2026","valor":"0.9259"},{"data":"31/02/2026","valor":"1"},
               {"data":"01/10/2026","valor":"abc"},{"data":"01/10/2026","valor":""},null,{"valor":"1"},{"data":"01/10/2026","valor":"NaN"}]"""
        )
        assertEquals(listOf(Bcb.PontoSgs("2026-10-25", 0.9259)), r)
        assertEquals(emptyList<Bcb.PontoSgs>(), Bcb.parsePontosSgs("""{"erro":true}"""))
        assertEquals(emptyList<Bcb.PontoSgs>(), Bcb.parsePontosSgs("isto nao e json"))
        assertEquals(emptyList<Bcb.PontoSgs>(), Bcb.parsePontosSgs(null))
        assertEquals(emptyList<Bcb.PontoSgs>(), Bcb.parsePontosSgs(""))
    }

    @Test fun `converte data dd-mm-aaaa`() {
        assertEquals("2026-09-01", Bcb.dataBrParaIso("01/09/2026"))
        assertNull(Bcb.dataBrParaIso("2026-09-01"))
        assertNull(Bcb.dataBrParaIso(null))
        assertNull(Bcb.dataBrParaIso("31/02/2026"))
        assertNull(Bcb.dataBrParaIso("29/02/2026"))
        assertEquals("2028-02-29", Bcb.dataBrParaIso("29/02/2028"))
        assertNull(Bcb.dataBrParaIso("01/13/2026"))
    }

    @Test fun `IPCA 433 amostra real ultimos 3 meses competencia 09-2026 nao parcial`() {
        val r = Bcb.indiceMensal(pontos("01/07/2026" to "0.07", "01/08/2026" to "-0.32", "01/09/2026" to "0.82"), hoje)
        assertEquals(Bcb.IndiceBcb(listOf(0.07, -0.32, 0.82), "09/2026", false), r)
    }

    @Test fun `Selic 4390 em ordem decrescente ordena e marca o mes corrente como parcial`() {
        val r = Bcb.indiceMensal(pontos("01/10/2026" to "0.92", "01/09/2026" to "1.08", "01/08/2026" to "1.09"), hoje)
        assertEquals(Bcb.IndiceBcb(listOf(1.09, 1.08, 0.92), "10/2026", true), r)
    }

    @Test fun `TR 7811 poupanca 196 e IGP-DI 190 amostras reais`() {
        assertEquals(listOf(0.1693, 0.169, 0.1616), Bcb.indiceMensal(pontos("01/08/2026" to "0.1693", "01/09/2026" to "0.1690", "01/10/2026" to "0.1616"), hoje)?.serie)
        assertEquals(listOf(0.6701, 0.6698, 0.6624), Bcb.indiceMensal(pontos("01/10/2026" to "0.6624", "01/09/2026" to "0.6698", "01/08/2026" to "0.6701"), hoje)?.serie)
        assertEquals("09/2026", Bcb.indiceMensal(pontos("01/07/2026" to "-0.86", "01/08/2026" to "0.06", "01/09/2026" to "1.50"), hoje)?.competencia)
    }

    @Test fun `repeticao no mes fica com o ultimo e so os 3 ultimos meses`() {
        val r = Bcb.indiceMensal(pontos("01/05/2026" to "1", "01/06/2026" to "2", "01/07/2026" to "3", "01/08/2026" to "4", "15/08/2026" to "5"), hoje)!!
        assertEquals(listOf(2.0, 3.0, 5.0), r.serie)
        assertEquals("08/2026", r.competencia)
    }

    @Test fun `datas futuras sao invalidas e usa o ultimo menor ou igual a hoje (Selic meta 432)`() {
        val p = pontos("02/11/2026" to "13.75", "03/11/2026" to "13.75", "04/11/2026" to "13.75", "27/10/2026" to "13.75")
        assertEquals(Bcb.PontoSgs("2026-10-27", 13.75), Bcb.maisRecenteAteHoje(p, hoje, Bcb.FAIXA_SELIC_META))
        assertEquals(Bcb.SelicMetaBcb(13.75, "2026-10-27"), Bcb.selicMetaDe(p, hoje))
        assertNull(Bcb.selicMetaDe(pontos("03/11/2026" to "13.75"), hoje))
        // a data de hoje vale
        assertEquals("2026-10-28", Bcb.selicMetaDe(pontos("28/10/2026" to "13.75"), hoje)?.data)
    }

    @Test fun `salario minimo 1619 ignora 11-2026 e 12-2026 futuros`() {
        val p = pontos("01/10/2026" to "1621.00", "01/11/2026" to "1621.00", "01/12/2026" to "1700.00")
        assertEquals(Bcb.SalarioBcb(162100, "10/2026"), Bcb.salarioDe(p, hoje))
    }

    @Test fun `mensal com todos os pontos no futuro vira nulo`() {
        assertNull(Bcb.indiceMensal(pontos("01/11/2026" to "0.5"), hoje))
    }

    @Test fun `valida faixa plausivel e descarta lixo`() {
        val lixo = pontos("01/09/2026" to "0.82", "01/10/2026" to "999")
        assertEquals("09/2026", Bcb.indiceMensal(lixo, hoje)?.competencia)
        assertNull(Bcb.indiceMensal(pontos("01/10/2026" to "-9"), hoje))
        assertNull(Bcb.selicMetaDe(pontos("27/10/2026" to "130"), hoje))
        assertNull(Bcb.salarioDe(pontos("01/10/2026" to "5"), hoje))
    }

    // ------------------------------------------------------------------ mapeamento série -> índice

    @Test fun `mapeamento das series conforme o portal`() {
        val m = Bcb.SERIES.associate { it.chave to it.codigo }
        assertEquals(
            mapOf(
                "selic" to 4390, "cdi" to 4391, "tr" to 7811, "poupanca" to 196, "ipca" to 433, "inpc" to 188, "igpm" to 189, "igpdi" to 190,
                "incc" to 192, "ipcfipe" to 7465, "ipcae" to 10764, "selicMeta" to 432, "salarioMinimo" to 1619
            ),
            m
        )
        // IVAR, TBF e TJLP seguem semente/manual
        assertTrue(listOf("ivar", "tbf", "tjlp").none { it in m })
        assertEquals("https://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados/ultimos/8?formato=json", Bcb.urlSgs(433, Bcb.ultimosPedidos(Bcb.SERIES.first { it.codigo == 433 })))
        assertEquals(12, Bcb.ultimosPedidos(Bcb.SERIES.first { it.codigo == 432 }))
        assertEquals(12, Bcb.ultimosPedidos(Bcb.SERIES.first { it.codigo == 1619 }))
    }

    @Test fun `parcial so para Selic e CDI`() {
        val txt = sgs("01/10/2026" to "0.5")
        fun parcial(chave: String) = (Bcb.interpretarSerie(Bcb.SERIES.first { it.chave == chave }, txt, hoje) as Bcb.Resultado.Indice).valor.parcial
        assertTrue(parcial("selic"))
        assertTrue(parcial("cdi"))
        assertFalse(parcial("tr"))
        assertFalse(parcial("poupanca"))
        assertFalse(parcial("ipca"))
    }

    // ------------------------------------------------------------------ cache

    @Test fun `TTL de 6 h com relogio injetado`() {
        val t = 1_000_000_000_000L
        assertTrue(Bcb.cacheExpirado(null, t))
        assertFalse(Bcb.cacheExpirado(t - 1000, t))
        assertFalse(Bcb.cacheExpirado(t - Bcb.TTL_MS + 1, t))
        assertTrue(Bcb.cacheExpirado(t - Bcb.TTL_MS, t))
        assertTrue(Bcb.cacheExpirado(t + 5000, t)) // relogio no futuro
        assertEquals(6 * 60 * 60 * 1000L, Bcb.TTL_MS)
    }

    @Test fun `snapshot de indices faz ida e volta e valida o que le`() {
        val s = SnapshotIndices(
            5, mapOf("ipca" to Bcb.IndiceBcb(listOf(0.07, -0.32, 0.82), "09/2026", false), "selic" to Bcb.IndiceBcb(listOf(1.09, 1.08, 0.92), "10/2026", true)),
            Bcb.SelicMetaBcb(13.75, "2026-10-27"), Bcb.SalarioBcb(162100, "10/2026")
        )
        assertEquals(s, Bcb.lerSnapshotIndices(Bcb.serializarSnapshotIndices(s)))
        val ok = Bcb.lerSnapshotIndices(
            """{"atualizadoEm":5,"itens":{"ipca":{"serie":[0.1,0.2],"competencia":"09/2026","parcial":false},"xxx":{"serie":[1],"competencia":"09/2026"},
               "cdi":{"serie":[99],"competencia":"09/2026"}},"selicMeta":{"valor":13.75,"data":"2026-10-27"},"salarioMinimo":{"centavos":162100,"competencia":"10/2026"}}"""
        )!!
        assertEquals(listOf("ipca"), ok.itens.keys.toList())
        assertEquals(13.75, ok.selicMeta?.valor ?: 0.0, 0.0)
        assertNull(Bcb.lerSnapshotIndices("lixo"))
        assertNull(Bcb.lerSnapshotIndices("""{"itens":{}}"""))
        assertNull(Bcb.lerSnapshotIndices(null))
        assertNull(Bcb.lerSnapshotIndices(""))
    }

    @Test fun `snapshot de cambio faz ida e volta e descarta invalidos`() {
        val c = Bcb.CotacaoMoeda("USD", 5.3, 5.34, "2026-10-27 13:05:00", Bcb.TipoBoletim.FECHAMENTO, false, 1.5)
        val i = Bcb.CotacaoMoeda("EUR", 6.0, 6.1, "2026-10-28 11:00:00", Bcb.TipoBoletim.INTERMEDIARIO, true, null)
        val s = Bcb.SnapshotCambio(9, listOf(c, i))
        assertEquals(s, Bcb.lerSnapshotCambio(Bcb.serializarSnapshotCambio(s)))
        assertNull(Bcb.lerSnapshotCambio("""{"atualizadoEm":1,"cotacoes":[{"moeda":"XXX","compra":1,"venda":1,"dataHora":"x","tipo":"Fechamento","variacaoPct":null}]}"""))
        assertNull(Bcb.lerSnapshotCambio("""{"cotacoes":[]}"""))
        assertNull(Bcb.lerSnapshotCambio("lixo"))
    }

    @Test fun `texto de estado`() {
        val t = ms(2026, 10, 28, 14, 5)
        assertEquals("Atualizado em 28/10 14:05 — Banco Central", Bcb.descreverEstado(Bcb.EstadoAtualizacao(t, false, null, false), tz))
        assertEquals("Offline: usando valores salvos de 28/10 14:05", Bcb.descreverEstado(Bcb.EstadoAtualizacao(t, false, null, true), tz))
        assertTrue(Bcb.descreverEstado(Bcb.EstadoAtualizacao(null, false, "HTTP 500", false), tz).contains("Não foi possível atualizar: HTTP 500"))
        assertEquals("Atualizando com o Banco Central…", Bcb.descreverEstado(Bcb.EstadoAtualizacao(t, true, null, false), tz))
        assertEquals("Ainda não atualizado: usando os valores de referência (semente).", Bcb.descreverEstado(Bcb.EstadoAtualizacao(null, false, null, false), tz))
    }

    // ------------------------------------------------------------------ mesclagem manual x BCB

    private val snap = SnapshotIndices(
        1,
        mapOf("ipca" to Bcb.IndiceBcb(listOf(0.07, -0.32, 0.82), "09/2026", false), "selic" to Bcb.IndiceBcb(listOf(1.09, 1.08, 0.92), "10/2026", true)),
        Bcb.SelicMetaBcb(13.75, "2026-10-27"), Bcb.SalarioBcb(162100, "10/2026")
    )

    @Test fun `sem edicao o BCB prevalece e traz competencia real`() {
        val ef = Bcb.aplicarBcb(IndicesEconomicos.padrao(), snap)
        val selic = ef.itens.first { it.id == "selic" }
        assertEquals(Origem.BCB, selic.origem)
        assertTrue(selic.parcial)
        assertFalse(selic.aproximada)
        assertEquals("10/2026", selic.competencia)
        assertEquals(listOf(1.09, 1.08, 0.92), selic.serie)
        assertEquals(Origem.SEMENTE, ef.itens.first { it.id == "tbf" }.origem) // sem serie: continua semente
        assertEquals(13.75, ef.extras.selicMetaAnualPct!!, 0.0)
        assertEquals(Origem.BCB, ef.extras.selicMetaOrigem)
        assertEquals(Origem.BCB, ef.salarioMinimo.origem)
    }

    @Test fun `edicao manual prevalece e nunca e sobrescrita`() {
        val base = IndicesEconomicos.padrao()
        val b = base.copy(
            itens = base.itens.map { if (it.id == "ipca") it.copy(serie = listOf(0.5), origem = Origem.MANUAL) else it },
            salarioMinimo = IndicesEconomicos.SalarioMinimo(170000, "2026", Origem.MANUAL),
            extras = base.extras.copy(selicMetaAnualPct = 12.0, selicMetaOrigem = Origem.MANUAL)
        )
        val ef = Bcb.aplicarBcb(b, snap)
        assertEquals(listOf(0.5), ef.itens.first { it.id == "ipca" }.serie)
        assertEquals(Origem.MANUAL, ef.itens.first { it.id == "ipca" }.origem)
        assertEquals(12.0, ef.extras.selicMetaAnualPct!!, 0.0)
        assertEquals(Origem.MANUAL, ef.extras.selicMetaOrigem)
        assertEquals(170000L, ef.salarioMinimo.centavos)
        // a base nao e alterada
        assertEquals(Origem.SEMENTE, b.itens.first { it.id == "selic" }.origem)
        assertEquals(listOf(1.09, 1.08), b.itens.first { it.id == "selic" }.serie)
    }

    @Test fun `meta da Selic do BCB so preenche se o usuario nao informou`() {
        val ef = Bcb.aplicarBcb(IndicesEconomicos.padrao(), snap)
        assertEquals(13.75, ef.extras.selicMetaAnualPct!!, 0.0)
        val sem = Bcb.aplicarBcb(IndicesEconomicos.padrao(), null)
        assertNull(sem.extras.selicMetaAnualPct)
        assertNull(sem.extras.selicMetaOrigem)
    }

    @Test fun `mesclar snapshots mantem o ultimo valor conhecido do que falhou`() {
        val novo = SnapshotIndices(9, mapOf("ipca" to Bcb.IndiceBcb(listOf(1.0), "10/2026", false)), null, null, listOf(Bcb.Falha("x", "HTTP 500")))
        val m = Bcb.mesclarSnapshots(snap, novo)
        assertEquals(9L, m.atualizadoEm)
        assertEquals(listOf(1.0), m.itens["ipca"]?.serie)
        assertEquals("10/2026", m.itens["selic"]?.competencia)
        assertEquals(13.75, m.selicMeta?.valor ?: 0.0, 0.0)
        assertEquals(162100L, m.salarioMinimo?.centavos)
        assertEquals(novo, Bcb.mesclarSnapshots(null, novo))
    }

    @Test fun `poupanca real do BCB prevalece e a regra vira dica calculada`() {
        val snapP = snap.copy(itens = snap.itens + ("poupanca" to Bcb.IndiceBcb(listOf(0.6701, 0.6698, 0.6624), "10/2026", false)) + ("tr" to Bcb.IndiceBcb(listOf(0.1693, 0.169, 0.1616), "10/2026", false)))
        val ef = Bcb.aplicarBcb(IndicesEconomicos.padrao(), snapP)
        assertEquals(0.6624, IndicesEconomicos.buscar(ef, "poupanca").valorMensalPct, 0.0)
        assertEquals(Origem.BCB, IndicesEconomicos.buscar(ef, "poupanca").origem)
        // Selic meta 13,75% > 8,5% => 0,5% a.m. + TR
        val dica = Bcb.dicaPoupanca(ef)!!
        assertEquals(Rendimentos.poupancaPelaRegra(0.1375, 0.001616), dica, 1e-12)
        assertEquals(1.005 * 1.001616 - 1, dica, 1e-12)
        assertNull(Bcb.dicaPoupanca(IndicesEconomicos.padrao())) // sem Selic meta
    }

    @Test fun `os calculos passam a usar os valores atualizados`() {
        val ef = Bcb.aplicarBcb(IndicesEconomicos.padrao(), snap)
        assertEquals(0.92, IndicesEconomicos.buscar(ef, "selic").valorMensalPct, 0.0)
        assertEquals(0.82, IndicesEconomicos.buscar(ef, "ipca").valorMensalPct, 0.0)
        assertEquals(1.08, IndicesEconomicos.buscar(IndicesEconomicos.padrao(), "selic").valorMensalPct, 0.0)
    }

    // ------------------------------------------------------------------ PTAX

    private fun b(dataHora: String, venda: Double, tipo: String, compra: Double = venda - 0.002) =
        """{"cotacaoCompra":$compra,"cotacaoVenda":$venda,"paridadeCompra":1,"paridadeVenda":1,"dataHoraCotacao":"$dataHora.600448","tipoBoletim":"$tipo"}"""

    private fun ptax(vararg itens: String) = itens.joinToString(",", """{"value":[""", "]}")

    @Test fun `amostra real da PTAX`() {
        val bol = Bcb.parseBoletins(
            """{"value":[{"cotacaoCompra":7.1041,"cotacaoVenda":7.1059,"dataHoraCotacao":"2026-10-28 13:05:31.600448","tipoBoletim":"Fechamento"}]}""", hoje
        )
        assertEquals(listOf(Bcb.Boletim("2026-10-28 13:05:31", 7.1041, 7.1059, Bcb.TipoBoletim.FECHAMENTO)), bol)
        val c = Bcb.calcularCotacao("GBP", bol)!!
        assertEquals(7.1041, c.compra, 0.0)
        assertEquals(7.1059, c.venda, 0.0)
        assertNull(c.variacaoPct)
    }

    @Test fun `monta a URL com datas MM-DD-AAAA e orderby`() {
        val u = Bcb.urlPtax("GBP", ms(2026, 10, 18), agora, tz)
        assertEquals("10-28-2026", Bcb.dataPtax(agora, tz))
        assertTrue(u.contains("@moeda='GBP'&@dataInicial='10-18-2026'&@dataFinalCotacao='10-28-2026'"))
        assertTrue(u.contains("\$orderby=dataHoraCotacao%20desc&\$format=json"))
        assertTrue(u.startsWith("https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"))
        assertTrue(u.contains("CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)?"))
        assertEquals(listOf("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "DKK", "NOK", "SEK"), Bcb.MOEDAS.map { it.codigo })
    }

    @Test fun `variacao do fechamento contra o fechamento anterior (amostra GBP)`() {
        val json = ptax(
            b("2026-10-28 13:05:31", 7.1059, "Fechamento", 7.1041), b("2026-10-28 13:05:31", 7.1402, "Intermediário"),
            b("2026-10-27 13:05:00", 7.0, "Fechamento"), b("2026-10-27 10:05:00", 6.9, "Intermediário"), b("2026-10-26 13:05:00", 6.5, "Fechamento")
        )
        val c = Bcb.calcularCotacao("GBP", Bcb.parseBoletins(json, hoje))!!
        assertEquals(Bcb.TipoBoletim.FECHAMENTO, c.tipo)
        assertFalse(c.intermediario)
        assertEquals(7.1059, c.venda, 0.0)
        assertEquals(7.1041, c.compra, 0.0)
        assertEquals((7.1059 / 7.0 - 1) * 100, c.variacaoPct!!, 1e-8)
        assertEquals(1.5129, c.variacaoPct!!, 1e-4) // 7,1059 / 7,0000 - 1
        assertEquals("2026-10-28 13:05:31", c.dataHora)
    }

    @Test fun `so intermediario no dia corrente mostra rotulado com variacao contra o ultimo fechamento`() {
        val json = ptax(b("2026-10-28 11:07:12", 5.4, "Intermediário"), b("2026-10-27 13:05:00", 5.0, "Fechamento"), b("2026-10-26 13:05:00", 4.0, "Fechamento"))
        val c = Bcb.calcularCotacao("USD", Bcb.parseBoletins(json, hoje))!!
        assertTrue(c.intermediario)
        assertEquals(Bcb.TipoBoletim.INTERMEDIARIO, c.tipo)
        assertEquals(8.0, c.variacaoPct!!, 1e-8)
    }

    @Test fun `sem fechamento anterior variacao nula, sem dados nulo, descarta lixo e futuro`() {
        assertNull(Bcb.calcularCotacao("USD", Bcb.parseBoletins(ptax(b("2026-10-27 13:05:00", 5.0, "Fechamento")), hoje))!!.variacaoPct)
        assertNull(Bcb.calcularCotacao("USD", emptyList()))
        val l = Bcb.parseBoletins(
            ptax(
                b("2026-10-29 13:05:00", 5.0, "Fechamento"), b("2026-10-27 13:05:00", -1.0, "Fechamento"), b("2026-10-27 13:05:00", 0.0, "Fechamento"),
                b("2026-10-27 13:05:00", 5.0, "Outro"), b("2026-10-27 13:05:00", 5.0, "Fechamento")
            ),
            hoje
        )
        assertEquals(1, l.size)
        assertEquals(emptyList<Bcb.Boletim>(), Bcb.parseBoletins("x", hoje))
        assertEquals(emptyList<Bcb.Boletim>(), Bcb.parseBoletins(null, hoje))
    }

    @Test fun `um fechamento por dia e historico em ordem cronologica`() {
        val bol = Bcb.parseBoletins(
            ptax(b("2026-10-27 13:05:00", 5.1, "Fechamento"), b("2026-10-27 13:00:00", 5.0, "Fechamento"), b("2026-10-26 13:00:00", 4.9, "Fechamento")), hoje
        )
        assertEquals(listOf(5.1, 4.9), Bcb.fechamentosPorDia(bol).map { it.venda })
        assertEquals(listOf(Bcb.PontoHistorico("2026-10-26", 4.9), Bcb.PontoHistorico("2026-10-27", 5.1)), Bcb.historicoDe(bol))
    }

    @Test fun `historico limita a 30 pontos`() {
        val itens = (1..40).map { d -> b("2026-09-%02d 13:00:00".format(if (d <= 30) d else 30), 5.0 + d / 100.0, "Fechamento") } +
            (1..10).map { d -> b("2026-10-%02d 13:00:00".format(d), 6.0 + d / 100.0, "Fechamento") }
        val h = Bcb.historicoDe(Bcb.parseBoletins(ptax(*itens.toTypedArray()), hoje))
        assertEquals(30, h.size)
        assertEquals("2026-10-10", h.last().data)
        assertTrue(h.zipWithNext().all { (a, c) -> a.data < c.data })
    }

    // ------------------------------------------------------------------ conversor

    @Test fun `conversor sem spread`() {
        assertEquals(200.0, Bcb.reaisParaMoeda(1000.0, 5.0, 0.0), 1e-10)
        assertEquals(1000.0, Bcb.moedaParaReais(200.0, 5.0, 0.0), 1e-10)
    }

    @Test fun `conversor com spread ou IOF informado`() {
        assertEquals(100 * 5.34 * 1.0638, Bcb.moedaParaReais(100.0, 5.34, 6.38), 1e-8)
        assertEquals(568.0692, Bcb.moedaParaReais(100.0, 5.34, 6.38), 1e-4)
        assertEquals(1000 / 5.5, Bcb.reaisParaMoeda(1000.0, 5.0, 10.0), 1e-10)
        assertEquals(181.8182, Bcb.reaisParaMoeda(1000.0, 5.0, 10.0), 1e-4)
        assertEquals(50.0, Bcb.reaisParaMoeda(Bcb.moedaParaReais(50.0, 7.1, 3.0), 7.1, 3.0), 1e-10)
    }

    @Test fun `conversor com entradas invalidas da NaN`() {
        assertTrue(Bcb.reaisParaMoeda(100.0, 0.0, 0.0).isNaN())
        assertTrue(Bcb.reaisParaMoeda(100.0, 5.0, -1.0).isNaN())
        assertTrue(Bcb.reaisParaMoeda(100.0, 5.0, 101.0).isNaN())
        assertTrue(Bcb.moedaParaReais(Double.NaN, 5.0, 0.0).isNaN())
        assertTrue(Bcb.spreadValido(0.0))
        assertTrue(Bcb.spreadValido(100.0))
        assertFalse(Bcb.spreadValido(Double.NaN))
    }

    // ------------------------------------------------------------------ seguranca de URL

    @Test fun `so https e so os dois hosts do Banco Central`() {
        assertTrue(Bcb.urlPermitida(Bcb.urlSgs(433, 8)))
        assertTrue(Bcb.urlPermitida(Bcb.urlPtax("USD", agora, agora, tz)))
        assertFalse(Bcb.urlPermitida("http://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados/ultimos/8?formato=json"))
        assertFalse(Bcb.urlPermitida("https://evil.example.com/dados/serie/bcdata.sgs.433/dados/ultimos/8?formato=json"))
        assertFalse(Bcb.urlPermitida("https://api.bcb.gov.br.evil.com/dados/serie/bcdata.sgs.433/dados/ultimos/8"))
        assertFalse(Bcb.urlPermitida("https://user:pass@api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados/ultimos/8"))
        assertFalse(Bcb.urlPermitida("https://api.bcb.gov.br:8443/dados/serie/bcdata.sgs.433/dados/ultimos/8"))
        assertFalse(Bcb.urlPermitida("https://api.bcb.gov.br/outro/caminho"))
        assertFalse(Bcb.urlPermitida("https://olinda.bcb.gov.br/outro"))
        assertFalse(Bcb.urlPermitida("nao e url"))
        assertNotNull(Bcb.SGS_BASE)
    }
}
