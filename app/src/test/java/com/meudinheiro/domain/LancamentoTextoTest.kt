package com.meudinheiro.domain

import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.LancamentoTexto.Confianca
import com.meudinheiro.domain.LancamentoTexto.Contexto
import com.meudinheiro.domain.LancamentoTexto.Data
import com.meudinheiro.domain.LancamentoTexto.Destino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R62 — MESMOS casos de portal/src/lib/finance/lancamentoTexto.test.ts. */
class LancamentoTextoTest {

    private fun regra(
        padrao: String = "ifood", categoria: String = "Delivery", conta: String? = null
    ) = RegraCategoria(1, padrao, "CONTEM", categoria, conta, null, true, 0, 1L)

    // 15/03/2026 é domingo
    private val agora = Data(2026, 3, 15)

    private fun ctx(
        hoje: Data = agora,
        contas: List<LancamentoTexto.ContaCtx> = listOf(
            LancamentoTexto.ContaCtx("111", "Nubank"), LancamentoTexto.ContaCtx("222", "Itaú"), LancamentoTexto.ContaCtx("333", "Banco do Brasil")
        ),
        cartoes: List<LancamentoTexto.CartaoCtx> = listOf(LancamentoTexto.CartaoCtx(10, "Itaú Click"), LancamentoTexto.CartaoCtx(11, "Inter Black")),
        regras: List<RegraCategoria> = listOf(regra())
    ) = Contexto(
        contas, cartoes,
        listOf("Alimentação", "Supermercado", "Transporte", "Saúde", "Salário", "Lazer", "Combustível", "Educação", "Compras", "Casa"),
        regras, hoje
    )

    private fun interp(frase: String, c: Contexto = ctx()) = LancamentoTexto.interpretar(frase, c)
    private fun iso(i: LancamentoTexto.Interpretacao) = "%04d-%02d-%02d".format(i.data.ano, i.data.mes, i.data.dia)

    // sentinelas: "não checar" x "esperar nulo"
    private companion object {
        const val X = "?"
        const val NULO = "-"
        const val NAO_CHECA = Long.MIN_VALUE
        const val VAZIO = Long.MIN_VALUE + 1
    }

    private class Esp(
        val tipo: TipoDespesa? = null,
        val valor: Long = NAO_CHECA,
        val desc: String? = null,
        val data: String? = null,
        val destino: String = X,
        val parcelas: Int = -1,
        val categoria: String = X,
        val candidatos: Int = -1,
        val pend: List<String>? = null
    )

    private val D = TipoDespesa.DEBITO
    private val C = TipoDespesa.CREDITO

    private val frases: List<Pair<String, Esp>> = listOf(
        "gastei 45 no mercado ontem no Nubank" to Esp(D, 4500, "Mercado", "2026-03-14", "CONTA:111", categoria = "Supermercado", pend = emptyList()),
        "paguei 120,50 de luz dia 10" to Esp(D, 12050, "Luz", "2026-03-10", categoria = "Casa", pend = emptyList()),
        "recebi 3.500 de salário hoje" to Esp(C, 350000, "Salário", "2026-03-15", categoria = "Salário", pend = emptyList()),
        "almoço 32 reais" to Esp(D, 3200, "Almoço", "2026-03-15", categoria = "Alimentação", pend = emptyList()),
        "uber 18,90 anteontem no cartão itaú" to Esp(D, 1890, "Uber", "2026-03-13", "CARTAO:10", categoria = "Transporte", pend = emptyList()),
        "parcelei 600 em 3x no cartão" to Esp(D, 60000, "", destino = NULO, parcelas = 3, candidatos = 2, pend = listOf("Qual cartão", "Não entendi o que foi")),
        "comprei um tênis por R$ 1.299,90 em 10x no cartão inter" to Esp(valor = 129990, desc = "tênis", destino = "CARTAO:11", parcelas = 10, categoria = "Compras", pend = emptyList()),
        "ganhei 2 mil de freela ontem" to Esp(C, 200000, "Freela", "2026-03-14", categoria = NULO, pend = emptyList()),
        "recebi 1,5 mil de bônus" to Esp(C, 150000, "Bônus", pend = emptyList()),
        "gasolina 200 conto" to Esp(valor = 20000, desc = "Gasolina", categoria = "Combustível", pend = emptyList()),
        "paguei 89.90 de internet dia 5" to Esp(valor = 8990, desc = "Internet", data = "2026-03-05", categoria = "Casa", pend = emptyList()),
        "farmácia 35,50 dia 31" to Esp(valor = 3550, desc = "Farmácia", data = "2026-01-31", categoria = "Saúde", pend = emptyList()),
        "paguei 300 de aluguel 05/03" to Esp(valor = 30000, desc = "Aluguel", data = "2026-03-05", categoria = "Casa", pend = emptyList()),
        "gastei 80 no cinema 20/12" to Esp(valor = 8000, desc = "cinema", data = "2025-12-20", categoria = "Cinema", pend = emptyList()),
        "gastei 50 em 25/12/2025 no ifood" to Esp(valor = 5000, desc = "ifood", data = "2025-12-25", categoria = "Delivery", pend = emptyList()),
        "pizza 70 sexta" to Esp(valor = 7000, desc = "Pizza", data = "2026-03-13", categoria = "Alimentação", pend = emptyList()),
        "mercado 150 segunda passada" to Esp(valor = 15000, desc = "Mercado", data = "2026-03-09", categoria = "Supermercado", pend = emptyList()),
        "gastei 20 na segunda-feira" to Esp(valor = 2000, desc = "", data = "2026-03-09", pend = listOf("Não entendi o que foi")),
        "gastei 1.234,56 no mercado" to Esp(valor = 123456, desc = "mercado", pend = emptyList()),
        "gastei R$45 no mercado" to Esp(valor = 4500, desc = "mercado", pend = emptyList()),
        "gastei 10 reais no mercado e 20 reais na padaria" to Esp(valor = 1000, desc = "mercado e 20 reais na padaria", pend = listOf("mais de um valor")),
        "gastei no mercado" to Esp(valor = VAZIO, desc = "mercado", pend = listOf("Não entendi o valor")),
        "gastei 45 no itaú" to Esp(valor = 4500, destino = NULO, candidatos = 2, pend = listOf("Qual conta ou cartão")),
        "gastei 45 no débito do itaú" to Esp(valor = 4500, desc = "", destino = "CONTA:222", candidatos = 0, pend = listOf("Não entendi o que foi")),
        "recebi 500 e paguei 200 de luz" to Esp(C, 50000, pend = listOf("despesa ou receita", "mais de um valor")),
        "depósito 200 hoje" to Esp(C, 20000, "Depósito", pend = emptyList()),
        "gastei 12,5 no café" to Esp(valor = 1250, desc = "café", categoria = "Alimentação", pend = emptyList()),
        "geladeira 3x de 200 no cartão inter" to Esp(valor = 60000, desc = "Geladeira", destino = "CARTAO:11", parcelas = 3, pend = emptyList()),
        "gastei 45 no mercado dia 31/02" to Esp(valor = 4500, data = "2026-03-15", pend = listOf("Data inválida (31/02)")),
        "paguei 50 de uber 15/03/26" to Esp(valor = 5000, desc = "Uber", data = "2026-03-15", pend = emptyList()),
        "paguei mil reais de aluguel" to Esp(valor = 100000, desc = "aluguel", pend = emptyList()),
        "gastei 2 mil no mercado" to Esp(valor = 200000, desc = "mercado", pend = emptyList()),
        "gastei 100 no cartão nubank" to Esp(valor = 10000, destino = NULO, candidatos = 2, pend = listOf("Não achei um cartão")),
        "gastei 45,00." to Esp(valor = 4500, pend = listOf("Não entendi o que foi")),
        "xyz" to Esp(valor = VAZIO, pend = listOf("Não entendi o valor"))
    )

    @Test fun `tem pelo menos 25 frases`() = assertTrue(frases.size >= 25)

    @Test fun `frases reais`() {
        for ((frase, e) in frases) {
            val r = interp(frase)
            val m = "\"$frase\""
            e.tipo?.let { assertEquals(m, it, r.tipo) }
            if (e.valor == VAZIO) assertNull(m, r.valorCentavos) else if (e.valor != NAO_CHECA) assertEquals(m, e.valor, r.valorCentavos)
            e.desc?.let { assertEquals(m, it.lowercase(), r.descricao.lowercase()) }
            e.data?.let { assertEquals(m, it, iso(r)) }
            if (e.destino != X) assertEquals(m, if (e.destino == NULO) null else e.destino, r.destino?.let { "${it.tipo}:${it.id}" })
            if (e.parcelas != -1) assertEquals(m, e.parcelas, r.parcelas)
            if (e.categoria != X) assertEquals(m, if (e.categoria == NULO) null else e.categoria, r.categoria)
            if (e.candidatos != -1) assertEquals(m, e.candidatos, r.candidatos.size)
            e.pend?.let { esperadas ->
                if (esperadas.isEmpty()) assertEquals(m, emptyList<String>(), r.pendencias)
                for (p in esperadas) assertTrue("$m pendência '$p' em ${r.pendencias}", r.pendencias.any { it.contains(p) })
            }
        }
    }

    // ------------------------------------------------------------ confiança e ambiguidade

    @Test fun `campos explicitos tem confianca alta e assumidos baixa`() {
        val r = interp("gastei 45 no mercado ontem no Nubank")
        assertEquals(LancamentoTexto.Confiancas(Confianca.ALTA, Confianca.ALTA, Confianca.ALTA, Confianca.ALTA, Confianca.ALTA, Confianca.MEDIA, Confianca.BAIXA), r.confianca)
        val s = interp("almoço 32 reais")
        assertEquals(Confianca.BAIXA, s.confianca.data)
        assertEquals(Confianca.BAIXA, s.confianca.tipo)
        assertEquals(Confianca.BAIXA, s.confianca.destino)
    }

    @Test fun `regra R55 vence o mapa de palavras`() {
        val r = interp("ifood 50", ctx(regras = listOf(regra(categoria = "Restaurantes"))))
        assertEquals("Restaurantes", r.categoria)
        assertEquals(Confianca.ALTA, r.confianca.categoria)
    }

    @Test fun `regra restrita a conta so vale com aquela conta`() {
        val regras = listOf(regra(conta = "222", categoria = "Trabalho"))
        assertEquals("Trabalho", interp("ifood 50 no itaú débito", ctx(regras = regras)).categoria)
        assertEquals("Alimentação", interp("ifood 50 no nubank", ctx(regras = regras)).categoria)
    }

    @Test fun `mesmo nome em conta e cartao e ambiguo sem dica`() {
        val c = ctx(contas = listOf(LancamentoTexto.ContaCtx("111", "Nubank")), cartoes = listOf(LancamentoTexto.CartaoCtx(5, "Nubank")))
        val a = interp("gastei 30 no nubank", c)
        assertNull(a.destino)
        assertEquals(listOf(Destino.CARTAO, Destino.CONTA), a.candidatos.map { it.tipo }.sortedBy { it.name })
        assertEquals(LancamentoTexto.Candidato(Destino.CARTAO, "5", "Nubank"), interp("gastei 30 no cartão nubank", c).destino)
        assertEquals(Destino.CARTAO, interp("parcelei 90 em 3x no nubank", c).destino?.tipo)
        assertEquals(Destino.CONTA, interp("gastei 30 na conta nubank", c).destino?.tipo)
    }

    @Test fun `so um cartao no cartao sem nome escolhe esse cartao`() {
        val r = interp("parcelei 600 em 3x no cartão", ctx(cartoes = listOf(LancamentoTexto.CartaoCtx(5, "Visa"))))
        assertEquals("5", r.destino?.id)
        assertEquals(Confianca.MEDIA, r.confianca.destino)
    }

    @Test fun `nome aproximado sem acento caixa e MercadoPago em duas palavras`() {
        val c = ctx(contas = listOf(LancamentoTexto.ContaCtx("9", "MercadoPago"), LancamentoTexto.ContaCtx("8", "Banco do Brasil")))
        assertEquals("9", interp("gastei 10 no mercado pago", c).destino?.id)
        assertEquals("8", interp("gastei 10 no BRASIL", c).destino?.id)
        assertEquals(2, interp("gastei 10 no ITAU").candidatos.size)
    }

    @Test fun `internet nao vira o cartao Inter`() {
        assertNull(interp("paguei 100 de internet").destino)
    }

    @Test fun `resumo para a previa`() {
        assertEquals(
            listOf("Despesa", "R$ 18,90", "Uber", "13/03/2026", "Itaú Click (cartão)", "Transporte"),
            LancamentoTexto.resumo(interp("uber 18,90 anteontem no cartão itaú"))
        )
        assertEquals(Data(2026, 3, 13).paraMs(), Data(2026, 3, 13).paraMs())
    }

    // ------------------------------------------------------------ valores

    @Test fun `virgula ponto e milhar`() {
        fun n(s: String) = LancamentoTexto.numeroEmCentavos(s)
        assertEquals(4500L, n("45"))
        assertEquals(12050L, n("120,50"))
        assertEquals(1890L, n("18.90"))
        assertEquals(350000L, n("3.500"))
        assertEquals(123456L, n("1.234,56"))
        assertEquals(123456700L, n("1.234.567"))
        assertEquals(150L, n("1,5"))
        assertEquals(150L, n("1.5"))
        assertEquals(5L, n("0,05"))
        assertNull(n("1,234"))
        assertNull(n("abc"))
    }

    // ------------------------------------------------------------ datas relativas com agora injetado

    private fun dia(frase: String, hoje: Data) = iso(interp(frase, ctx(hoje = hoje)))

    @Test fun `ontem e anteontem em meses de 28 29 30 e 31 dias`() {
        assertEquals("2026-02-28", dia("ontem 10 reais", Data(2026, 3, 1)))
        assertEquals("2024-02-29", dia("ontem 10 reais", Data(2024, 3, 1)))
        assertEquals("2026-04-30", dia("ontem 10 reais", Data(2026, 5, 1)))
        assertEquals("2026-07-31", dia("ontem 10 reais", Data(2026, 8, 1)))
        assertEquals("2026-02-27", dia("anteontem 10 reais", Data(2026, 3, 1)))
        assertEquals("2024-02-28", dia("anteontem 10 reais", Data(2024, 3, 1)))
        assertEquals("2026-02-27", dia("antes de ontem 10 reais", Data(2026, 3, 1)))
    }

    @Test fun `virada de ano`() {
        assertEquals("2025-12-31", dia("ontem 10 reais", Data(2026, 1, 1)))
        assertEquals("2025-12-30", dia("anteontem 10 reais", Data(2026, 1, 1)))
        assertEquals("2025-12-20", dia("dia 20 10 reais", Data(2026, 1, 5)))
        assertEquals("2025-12-28", dia("28/12 10 reais", Data(2026, 1, 1)))
        assertEquals(Confianca.MEDIA, interp("28/12 10 reais", ctx(hoje = Data(2026, 1, 1))).confianca.data)
        assertEquals("2026-01-01", dia("01/01 10 reais", Data(2026, 1, 1)))
    }

    @Test fun `dia N e o dia N mais recente que nao esta no futuro`() {
        assertEquals("2026-03-15", dia("dia 15 10 reais", Data(2026, 3, 15)))
        assertEquals("2026-02-16", dia("dia 16 10 reais", Data(2026, 3, 15)))
        assertEquals("2026-01-31", dia("dia 31 10 reais", Data(2026, 3, 1))) // fevereiro não tem dia 31
        assertEquals("2026-01-30", dia("dia 30 10 reais", Data(2026, 3, 15)))
        assertEquals("2026-01-29", dia("dia 29 10 reais", Data(2026, 3, 10)))
        assertEquals("2024-02-29", dia("dia 29 10 reais", Data(2024, 3, 10))) // ano bissexto
        assertEquals("2026-03-31", dia("dia 31 10 reais", Data(2026, 5, 20))) // abril tem 30
    }

    @Test fun `dia da semana passada e sempre antes de hoje`() {
        val seg = Data(2026, 3, 16) // segunda
        assertEquals("2026-03-09", dia("segunda passada 10 reais", seg))
        assertEquals("2026-03-16", dia("segunda 10 reais", seg))
        assertEquals("2026-03-10", dia("terça 10 reais", seg))
        assertEquals("2026-03-14", dia("sábado passado 10 reais", seg))
        assertEquals("2026-03-15", dia("domingo 10 reais", seg))
        assertEquals("2025-12-31", dia("quarta 10 reais", Data(2026, 1, 1))) // 1/1/2026 é quinta
        assertEquals("2025-12-25", dia("quinta passada 10 reais", Data(2026, 1, 1)))
    }

    @Test fun `dd mm aaaa explicito e invalidos`() {
        assertEquals("2024-02-29", dia("10 reais 29/02/2024", agora))
        assertTrue(interp("10 reais 29/02/2025").pendencias.any { it.contains("Data inválida") })
        assertTrue(interp("10 reais dia 40").pendencias.any { it.contains("inválido") })
    }
}
