package com.meudinheiro.domain

import com.meudinheiro.data.Cartao
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.LembretesVencimento.Config
import com.meudinheiro.domain.LembretesVencimento.Item
import com.meudinheiro.domain.LembretesVencimento.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class LembretesVencimentoTest {
    private val sp = TimeZone.getTimeZone("America/Sao_Paulo")
    private val utc = TimeZone.getTimeZone("UTC")

    private fun ms(tz: TimeZone, ano: Int, mes: Int, dia: Int, h: Int = 9, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(ano, mes - 1, dia, h, min, 0) }.timeInMillis

    private fun item(chave: String, venc: Long, desc: String = chave, centavos: Long = 10_000, tipo: Tipo = Tipo.CONTA, id: Long? = null) =
        Item(chave, tipo, desc, centavos, venc, id)

    private val todas = Config(setOf(0, 1, 3, 7))

    private fun sel(itens: List<Item>, agora: Long, cfg: Config = todas, avisados: Set<String> = emptySet(), tz: TimeZone = sp) =
        LembretesVencimento.selecionar(itens, agora, cfg, avisados, tz)

    // ------------------------------------------------------------ configuração

    @Test fun `marcadas ida e volta e filtro de lixo`() {
        assertEquals(setOf(0, 1, 3, 7), LembretesVencimento.lerMarcadas("7,3,1,0"))
        assertEquals(setOf(1, 3), LembretesVencimento.lerMarcadas(" 1 , x, 3, 5, -1, 99"))
        assertEquals(emptySet<Int>(), LembretesVencimento.lerMarcadas(null))
        assertEquals(emptySet<Int>(), LembretesVencimento.lerMarcadas(""))
        assertEquals("0,3", LembretesVencimento.gravarMarcadas(setOf(3, 0, 42)))
        assertEquals(LembretesVencimento.PADRAO_MARCADAS, LembretesVencimento.lerMarcadas(LembretesVencimento.gravarMarcadas(LembretesVencimento.PADRAO_MARCADAS)))
        assertEquals("No dia do vencimento", LembretesVencimento.rotuloAntecedencia(0))
        assertEquals("1 dia antes", LembretesVencimento.rotuloAntecedencia(1))
        assertEquals("7 dias antes", LembretesVencimento.rotuloAntecedencia(7))
    }

    // ------------------------------------------------------------ seleção por antecedência

    @Test fun `avisa exatamente nas antecedencias marcadas`() {
        val venc = ms(sp, 2026, 5, 20, 0, 0)
        val i = item("c:1", venc)
        val cfg = Config(setOf(0, 3))
        // 7 e 5 dias antes: a marca 3 ainda não cobre (faltam mais dias que ela)
        assertTrue(sel(listOf(i), ms(sp, 2026, 5, 13), cfg).isEmpty())
        assertTrue(sel(listOf(i), ms(sp, 2026, 5, 15), cfg).isEmpty())
        // 3 dias antes
        val r3 = sel(listOf(i), ms(sp, 2026, 5, 17), cfg)
        assertEquals(1, r3.size); assertEquals(3, r3[0].janela); assertEquals(3, r3[0].diasAte)
        // no dia
        val r0 = sel(listOf(i), ms(sp, 2026, 5, 20, 23, 59), cfg)
        assertEquals(0, r0[0].janela)
    }

    @Test fun `sete dias antes dispara quando faltam 7`() {
        val i = item("c:1", ms(sp, 2026, 5, 20))
        assertEquals(7, sel(listOf(i), ms(sp, 2026, 5, 13)).single().janela)
        assertTrue(sel(listOf(i), ms(sp, 2026, 5, 12)).isEmpty())
    }

    @Test fun `idempotente - a mesma janela nao repete e a proxima dispara`() {
        val i = item("c:1", ms(sp, 2026, 5, 20))
        val d3 = ms(sp, 2026, 5, 17)
        val primeira = sel(listOf(i), d3)
        assertEquals(1, primeira.size)
        val avisados = primeira.map { it.chaveAviso }.toSet()
        assertTrue("segunda execução no mesmo dia", sel(listOf(i), d3, avisados = avisados).isEmpty())
        assertTrue("mais tarde no mesmo dia", sel(listOf(i), ms(sp, 2026, 5, 17, 22), avisados = avisados).isEmpty())
        assertTrue("faltam 2: ainda é a janela 3, já avisada", sel(listOf(i), ms(sp, 2026, 5, 18), avisados = avisados).isEmpty())
        val d1 = sel(listOf(i), ms(sp, 2026, 5, 19), avisados = avisados)
        assertEquals(1, d1.single().janela)
    }

    @Test fun `aparelho desligado no dia exato avisa na primeira execucao dentro da janela, sem acumular`() {
        val i = item("c:1", ms(sp, 2026, 5, 20))
        // nunca avisou; faltam 2 dias, marcas 0,1,3,7: só a janela 3 (a menor que cobre), não 3+7
        val r = sel(listOf(i), ms(sp, 2026, 5, 18))
        assertEquals(listOf(3), r.map { it.janela })
    }

    @Test fun `item novo dentro da janela avisa uma vez`() {
        val i = item("c:9", ms(sp, 2026, 5, 20))
        assertEquals(listOf(7), sel(listOf(i), ms(sp, 2026, 5, 15)).map { it.janela }) // faltam 5 -> marca 7 cobre
    }

    @Test fun `nada marcado nao avisa a vencer`() {
        val i = item("c:1", ms(sp, 2026, 5, 20))
        assertTrue(sel(listOf(i), ms(sp, 2026, 5, 20), Config(emptySet())).isEmpty())
    }

    @Test fun `vencimento alterado gera aviso novo`() {
        val antes = item("c:1", ms(sp, 2026, 5, 20))
        val chave = sel(listOf(antes), ms(sp, 2026, 5, 20)).single().chaveAviso
        val depois = item("c:1", ms(sp, 2026, 5, 25))
        assertEquals(1, sel(listOf(depois), ms(sp, 2026, 5, 25), avisados = setOf(chave)).size)
    }

    // ------------------------------------------------------------ atrasadas

    @Test fun `atrasada avisa uma vez por dia e pode ser desligada`() {
        val i = item("c:1", ms(sp, 2026, 5, 10))
        val hoje = ms(sp, 2026, 5, 12)
        val r = sel(listOf(i), hoje)
        assertEquals(LembretesVencimento.JANELA_ATRASADA, r.single().janela)
        assertEquals(-2, r.single().diasAte)
        val av = r.map { it.chaveAviso }.toSet()
        assertTrue(sel(listOf(i), ms(sp, 2026, 5, 12, 20), avisados = av).isEmpty())
        assertEquals(1, sel(listOf(i), ms(sp, 2026, 5, 13), avisados = av).size) // dia seguinte
        assertTrue(sel(listOf(i), hoje, Config(setOf(0, 1, 3, 7), avisarAtrasadas = false)).isEmpty())
    }

    // ------------------------------------------------------------ virada de mês/ano e fuso

    @Test fun `virada de mes`() {
        val i = item("c:1", ms(sp, 2026, 3, 1))
        assertEquals(2, LembretesVencimento.diasAte(ms(sp, 2026, 3, 1), ms(sp, 2026, 2, 27), sp)) // fev/2026 tem 28 dias
        assertEquals(3, sel(listOf(i), ms(sp, 2026, 2, 26)).single().janela) // faltam 3
        assertEquals(1, sel(listOf(i), ms(sp, 2026, 2, 28)).single().janela)
        val i2 = item("c:2", ms(sp, 2026, 5, 1))
        assertEquals(1, LembretesVencimento.diasAte(ms(sp, 2026, 5, 1), ms(sp, 2026, 4, 30), sp)) // abril tem 30
    }

    @Test fun `virada de ano e bissexto`() {
        assertEquals(3, LembretesVencimento.diasAte(ms(sp, 2027, 1, 2), ms(sp, 2026, 12, 30), sp))
        assertEquals(2, LembretesVencimento.diasAte(ms(sp, 2028, 3, 1), ms(sp, 2028, 2, 28), sp)) // 2028 é bissexto: 29/02 existe
        assertEquals(1, LembretesVencimento.diasAte(ms(sp, 2026, 3, 1), ms(sp, 2026, 2, 28), sp))
    }

    @Test fun `o dia e o do fuso informado`() {
        // 23:30 de 10/03 em São Paulo; vencimento 00:00 de 11/03 em São Paulo
        val agora = ms(sp, 2026, 3, 10, 23, 30)
        val venc = ms(sp, 2026, 3, 11, 0, 0)
        assertEquals(1, LembretesVencimento.diasAte(venc, agora, sp))
        // os mesmos instantes em UTC caem no MESMO dia (02:30Z e 03:00Z de 11/03)
        assertEquals(0, LembretesVencimento.diasAte(venc, agora, utc))
        assertEquals(0, LembretesVencimento.diasAte(ms(sp, 2026, 3, 10, 0, 0), ms(sp, 2026, 3, 10, 23, 59), sp))
    }

    @Test fun `fuso extremo nao quebra a contagem`() {
        val kiritimati = TimeZone.getTimeZone("Pacific/Kiritimati") // UTC+14
        val pago = TimeZone.getTimeZone("Pacific/Pago_Pago") // UTC-11
        assertEquals(1, LembretesVencimento.diasAte(ms(kiritimati, 2026, 1, 1, 0, 1), ms(kiritimati, 2025, 12, 31, 23, 59), kiritimati))
        assertEquals(1, LembretesVencimento.diasAte(ms(pago, 2026, 1, 1, 0, 1), ms(pago, 2025, 12, 31, 23, 59), pago))
    }

    // ------------------------------------------------------------ ordem, expurgo, itens inválidos

    @Test fun `ordena por vencimento e ignora valor zero`() {
        val a = item("c:a", ms(sp, 2026, 5, 21), "Luz")
        val b = item("c:b", ms(sp, 2026, 5, 20), "Água")
        val c = item("c:c", ms(sp, 2026, 5, 20), "Aluguel", centavos = 0)
        val r = sel(listOf(a, b, c), ms(sp, 2026, 5, 20))
        assertEquals(listOf("c:b", "c:a"), r.map { it.item.chave })
    }

    @Test fun `expurga avisados antigos e chaves malformadas`() {
        val hoje = ms(sp, 2026, 5, 20)
        val d = LembretesVencimento.diaCivil(hoje, sp)
        val manter = "c:1|3|${d - 10}"
        val velha = "c:2|3|${d - LembretesVencimento.RETENCAO_DIAS - 1}"
        val limite = "c:3|0|${d - LembretesVencimento.RETENCAO_DIAS}"
        val lixo = "lixo"
        assertEquals(setOf(manter, limite), LembretesVencimento.expurgar(setOf(manter, velha, limite, lixo), hoje, sp))
    }

    // ------------------------------------------------------------ texto da notificação agrupada

    @Test fun `sem lembretes nao ha notificacao`() {
        assertNull(LembretesVencimento.montar(emptyList()))
    }

    @Test fun `um item e varios itens`() {
        val agora = ms(sp, 2026, 5, 20)
        val a = item("c:1", ms(sp, 2026, 5, 20), "Aluguel", 150_000, id = 1)
        val l1 = sel(listOf(a), agora)
        val n1 = LembretesVencimento.montar(l1)!!
        assertEquals("Aluguel vence hoje", n1.titulo)
        assertTrue(n1.resumo.contains("1.500,00"))
        assertFalse(n1.atrasado)
        assertEquals(1L, n1.contaParaPagar?.lancamentoId)

        val b = item("f:2:5-2026", ms(sp, 2026, 5, 23), "Fatura Nubank", 80_000, Tipo.FATURA)
        val c = item("c:3", ms(sp, 2026, 5, 18), "Luz", 20_000, id = 3)
        val n = LembretesVencimento.montar(sel(listOf(a, b, c), agora))!!
        assertEquals("3 vencimentos (1 atrasado(s))", n.titulo)
        assertTrue(n.atrasado)
        assertEquals(3, n.linhas.size)
        assertEquals("Venceu há 2 dias · Luz · R$ 200,00", n.linhas[0])
        assertEquals("Hoje · Aluguel · R$ 1.500,00", n.linhas[1])
        assertEquals("Em 3 dias · Fatura Nubank · R$ 800,00", n.linhas[2])
        assertTrue(n.resumo.contains("2.500,00"))
        assertEquals(3L, n.contaParaPagar?.lancamentoId) // a mais urgente que é conta
    }

    @Test fun `so faturas nao oferece pagar agora, modo privado oculta valores, e limita linhas`() {
        val agora = ms(sp, 2026, 5, 20)
        val f = item("f:1:5-2026", ms(sp, 2026, 5, 20), "Fatura Inter", 99_900, Tipo.FATURA)
        val n = LembretesVencimento.montar(sel(listOf(f), agora), ocultarValores = true)!!
        assertNull(n.contaParaPagar)
        assertFalse(n.resumo.contains("999"))
        assertFalse(n.linhas.joinToString().contains("999"))
        assertTrue(n.resumo.contains("••••"))

        val muitos = (1..9).map { item("c:$it", ms(sp, 2026, 5, 20), "Conta $it", id = it.toLong()) }
        val nm = LembretesVencimento.montar(sel(muitos, agora))!!
        assertEquals(LembretesVencimento.MAX_LINHAS + 1, nm.linhas.size)
        assertEquals("+ 3 outros no app", nm.linhas.last())
        assertEquals("9 vencimentos chegando", nm.titulo)
    }

    @Test fun `todas atrasadas`() {
        val agora = ms(sp, 2026, 5, 20)
        val itens = listOf(item("c:1", ms(sp, 2026, 5, 10)), item("c:2", ms(sp, 2026, 5, 19)))
        assertEquals("2 vencimentos atrasados", LembretesVencimento.montar(sel(itens, agora))!!.titulo)
    }

    // ------------------------------------------------------------ itens a partir do banco

    private fun desp(id: Long, desc: String, valor: Double, data: Long, pago: Boolean = false, cartaoId: Int? = null,
                     tipo: TipoDespesa = TipoDespesa.DEBITO, natureza: String = Natureza.NORMAL) = Despesa(
        id = id, descricao = desc, valor = valor, data = Date(data), categoria = "X", conta = "1", pic = "p", tipo = tipo,
        mes = 1, ano = 2026, cartaoId = cartaoId, pago = pago, natureza = natureza
    )

    private fun cartao(id: Int, nome: String, principal: Int? = null) = Cartao(
        id = id, nome = nome, finalCartao = "1234", tipo = "CRÉDITO", limiteDisponivel = 1000.0, limiteTotal = 1000.0,
        diaFechamento = 10, diaVencimento = 20, contaId = 1, cartaoPrincipalId = principal
    )

    @Test fun `itens - contas pendentes fora do cartao e fatura agregada do grupo`() {
        val cal = Calendar.getInstance().apply { clear(); set(2026, 4, 5, 12, 0, 0) }.timeInMillis // 05/05/2026 (antes do fechamento 10)
        val despesas = listOf(
            desp(1, "Aluguel", 1500.0, cal),
            desp(2, "Paga", 10.0, cal, pago = true),
            desp(3, "Receita", 100.0, cal, tipo = TipoDespesa.CREDITO),
            desp(4, "Transferência", 50.0, cal, natureza = Natureza.TRANSFERENCIA),
            desp(5, "Compra física", 200.0, cal, cartaoId = 7),
            desp(6, "Compra virtual", 100.0, cal, cartaoId = 8),
            desp(7, "Compra paga", 300.0, cal, cartaoId = 7, pago = true)
        )
        val cartoes = listOf(cartao(7, "Nubank"), cartao(8, "Nubank virtual", principal = 7))
        val itens = LembretesVencimento.itens(despesas, cartoes)

        val contas = itens.filter { it.tipo == Tipo.CONTA }
        assertEquals(listOf("c:1"), contas.map { it.chave })
        assertEquals(150_000L, contas.single().centavos)

        val faturas = itens.filter { it.tipo == Tipo.FATURA }
        assertEquals(1, faturas.size) // o virtual compartilha a fatura do principal
        assertEquals("Fatura Nubank", faturas.single().descricao)
        assertEquals(30_000L, faturas.single().centavos)
        assertEquals("f:7:5-2026", faturas.single().chave)
        assertNotNull(faturas.single().vencimentoMs)
        // vence dia 20 do mês do fechamento (20 > 10)
        assertEquals(20, Calendar.getInstance().apply { timeInMillis = faturas.single().vencimentoMs }.get(Calendar.DAY_OF_MONTH))
    }
}
