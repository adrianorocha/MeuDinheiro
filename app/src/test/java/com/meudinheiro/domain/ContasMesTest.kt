package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.ContasMes.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** R108 — mesmos cenários de portal/src/lib/finance/contasMes.test.ts. */
class ContasMesTest {
    private fun ms(ano: Int, mes: Int, dia: Int, hora: Int = 12) = Calendar.getInstance().apply { clear(); set(ano, mes - 1, dia, hora, 0, 0) }.timeInMillis
    private val agora = ms(2025, 3, 10)

    private fun regra(id: Int = 1, desc: String = "Conta de luz", dia: Int = 15, tipo: TipoDespesa = TipoDespesa.DEBITO, valor: Double = 200.0, cartaoId: Int? = null,
                      pausada: Boolean = false, pausadaAte: Date? = null) =
        DespesaFixa(id = id, descricao = desc, valor = valor, conta = "111", categoria = "Moradia", pic = "", tipo = tipo, diaVencimento = dia, cartaoId = cartaoId, pausada = pausada, pausadaAte = pausadaAte)

    private fun oc(id: Long, r: DespesaFixa, mes: Int, ano: Int = 2025, valor: Double = r.valor, pago: Boolean = false, dia: Int = r.diaVencimento, grupo: String? = ContasMes.grupoOcorrencia(r.id, mes, ano), cartaoId: Int? = null, desc: String = r.descricao) =
        Despesa(id = id, descricao = desc, valor = valor, data = Date(ms(ano, mes, dia)), categoria = "Moradia", conta = "111", pic = "", tipo = r.tipo, mes = mes, ano = ano, pago = pago, grupoId = grupo, cartaoId = cartaoId)

    @Test fun `classifica nao lancada, provisionada, pendente, vencida, paga e desabilitada`() {
        val luz = regra(1, "Luz", 15); val tel = regra(2, "Telefone", 5); val gas = regra(3, "Gás", 25); val agua = regra(4, "Água", 8)
        val net = regra(5, "Internet", 20, pausada = true); val salario = regra(6, "Salário", tipo = TipoDespesa.CREDITO)
        val ds = listOf(oc(10, luz, 3), oc(11, tel, 3), oc(12, agua, 3, valor = 90.0, pago = true))
        val r = ContasMes.contasDoMes(ds, listOf(luz, tel, gas, agua, net, salario), 3, 2025, agora)
        val est = r.linhas.associate { it.regra.descricao to it.estado }
        assertEquals(mapOf("Luz" to Estado.PROVISIONADA, "Telefone" to Estado.VENCIDA, "Gás" to Estado.NAO_LANCADA, "Água" to Estado.PAGA, "Internet" to Estado.DESABILITADA), est)
        assertEquals(1, r.vencidas)
        assertEquals(Dinheiro.centavos(200.0 + 200.0 + 200.0 + 90.0), r.previstoCentavos) // exclui a desabilitada
        assertEquals(Dinheiro.centavos(90.0), r.pagoCentavos)
        assertEquals(Dinheiro.centavos(600.0), r.aPagarCentavos)
        assertTrue(r.linhas.first { it.regra.descricao == "Água" }.valorDifere)
    }

    @Test fun `compra no cartao nao e vencida e a baixa e pela fatura`() {
        val r = regra(cartaoId = 10)
        val l = ContasMes.contasDoMes(listOf(oc(1, r, 3, dia = 5, cartaoId = 10)), listOf(r), 3, 2025, agora).linhas.single()
        assertTrue(l.noCartao)
        assertEquals(Estado.PENDENTE, l.estado)
    }

    @Test fun `mes passado sem lancamento nao lista a conta`() {
        assertTrue(ContasMes.contasDoMes(emptyList(), listOf(regra()), 1, 2025, agora).linhas.isEmpty())
    }

    @Test fun `lancamento antigo sem grupoId e reconhecido se for um so, ambiguo nao`() {
        val r = regra()
        val legado = oc(900, r, 3, valor = 190.0, grupo = null, desc = "conta de LUZ", dia = 14)
        assertEquals(900L, ContasMes.ocorrenciaDoMes(listOf(legado), r, 3, 2025)?.id)
        val outro = legado.copy(id = 901)
        assertNull(ContasMes.ocorrenciaDoMes(listOf(legado, outro), r, 3, 2025))
        // com grupoId exato, ele manda
        assertEquals(10L, ContasMes.ocorrenciaDoMes(listOf(legado, oc(10, r, 3)), r, 3, 2025)?.id)
    }

    @Test fun `mesesParaProvisionar pula os que ja existem e vira o ano`() {
        val r = regra()
        val existentes = listOf(oc(1, r, 12, 2025))
        val novembro = ms(2025, 11, 5)
        assertEquals(listOf(11 to 2025, 1 to 2026), ContasMes.mesesParaProvisionar(existentes, r, novembro, 3))
        assertEquals(12, ContasMes.mesesParaProvisionar(emptyList(), r, novembro, 12).size)
    }

    @Test fun `provisionadasFuturas so pega as futuras em aberto da regra`() {
        val r = regra()
        val ds = listOf(oc(1, r, 3, pago = true), oc(2, r, 3, dia = 5), oc(3, r, 4), oc(4, r, 5), oc(5, regra(2, "Outra"), 4))
        assertEquals(listOf(3L, 4L), ContasMes.provisionadasFuturas(ds, 1, agora).map { it.id }.sorted())
    }

    @Test fun `dataPrevista usa o ultimo dia do mes curto`() {
        val c = Calendar.getInstance().apply { timeInMillis = ContasMes.dataPrevista(regra(dia = 31), 2, 2025) }
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
        assertNotNull(c)
    }
}
