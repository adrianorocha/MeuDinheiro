package com.meudinheiro.funcoes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Natureza
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/** R48: o conteúdo do comprovante vem de uma função pura; campos vazios não aparecem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComprovanteTest {

    private fun ms(m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, 12, 0, 0) }.timeInMillis
    private val agora = ms(9, 20)

    private fun desp(
        descricao: String = "Compra", valor: Double = 100.0, tipo: TipoDespesa = TipoDespesa.DEBITO, pago: Boolean = true,
        cartaoId: Int? = null, natureza: String = Natureza.NORMAL, dia: Int = 10, grupoId: String? = null, id: Long = 42
    ) = Despesa(
        id = id, descricao = descricao, valor = valor, data = Date(ms(9, dia)), categoria = "Mercado", conta = "123456-7", pic = "p",
        tipo = tipo, mes = 9, ano = 2026, cartaoId = cartaoId, pago = pago, natureza = natureza, grupoId = grupoId
    )

    private fun rotulos(m: ComprovanteModelo) = m.linhas.map { it.rotulo }
    private fun valor(m: ComprovanteModelo, r: String) = m.linhas.first { it.rotulo == r }.valor

    @Test fun `despesa paga em conta tem selo, status e conta mascarada`() {
        val m = montarComprovante(desp(), null, "123456-7", agora = agora)
        assertEquals(SeloComprovante.DESPESA, m.selo)
        assertEquals(StatusComprovante.PAGO, m.status)
        assertTrue(m.valor.startsWith("− "))
        assertEquals("•••• 4567", valor(m, "Conta de origem"))
        assertEquals("MD-000042", m.id)
        assertTrue("Data do pagamento" in rotulos(m))
        assertFalse("Cartão" in rotulos(m))
        assertFalse("Parcela" in rotulos(m))
        assertFalse(m.linhas.any { it.valor.isBlank() })
    }

    @Test fun `pendente vencida x a vencer`() {
        assertEquals(StatusComprovante.VENCIDO, montarComprovante(desp(pago = false, dia = 5), null, "x", agora = agora).status)
        assertEquals(StatusComprovante.PENDENTE, montarComprovante(desp(pago = false, dia = 25), null, "x", agora = agora).status)
        assertTrue("Vencimento" in rotulos(montarComprovante(desp(pago = false, dia = 25), null, "x", agora = agora)))
    }

    @Test fun `compra parcelada no cartao mostra fatura parcela total e restantes`() {
        val cartao = Cartao(id = 7, nome = "Nubank", finalCartao = "9876", tipo = "CRÉDITO", limiteDisponivel = 0.0, limiteTotal = 0.0, diaFechamento = 25, diaVencimento = 5, contaId = 1)
        val p1 = desp("Sofá (2/3)", 100.0, cartaoId = 7, pago = false, grupoId = "parc:a", id = 1, dia = 10)
        val irmaos = listOf(p1, desp("Sofá (1/3)", 100.0, cartaoId = 7, grupoId = "parc:a", id = 2), desp("Sofá (3/3)", 100.01, cartaoId = 7, grupoId = "parc:a", id = 3))
        val m = montarComprovante(p1, "Nubank", "123456-7", ComprovanteExtras(cartao = cartao, irmaos = irmaos), agora)
        assertEquals("Sofá", valor(m, "Descrição"))
        assertEquals("2/3", valor(m, "Parcela"))
        assertEquals("1", valor(m, "Parcelas restantes"))
        assertTrue(valor(m, "Valor total da compra").contains("300,01"))
        assertEquals("09/2026", valor(m, "Fatura de referência"))
        assertEquals("Nubank •••• 9876", valor(m, "Cartão"))
        assertEquals("Físico", valor(m, "Tipo do cartão"))
        assertEquals("Crédito", valor(m, "Modalidade"))
        assertTrue("Vencimento da fatura" in rotulos(m))
    }

    @Test fun `transferencia mostra origem e destino e natureza`() {
        val origem = desp("Transf", 50.0, natureza = Natureza.TRANSFERENCIA, grupoId = "transf:x", id = 1)
        val destino = origem.copy(id = 2, conta = "999", tipo = TipoDespesa.CREDITO)
        val contas = listOf(ContaSaldo(1, 0.0, "Itaú", "p", "1", "123456-7", "A"), ContaSaldo(2, 0.0, "Nubank", "p", "1", "999", "A"))
        val m = montarComprovante(origem, null, "123456-7", ComprovanteExtras(conta = contas[0], irmaos = listOf(origem, destino), contas = contas), agora)
        assertEquals(SeloComprovante.TRANSFERENCIA, m.selo)
        assertEquals("Itaú → Nubank", valor(m, "Transferência"))
        assertTrue("Natureza" in rotulos(m))
    }

    @Test fun `selos por natureza e receita`() {
        fun selo(n: String, t: TipoDespesa = TipoDespesa.DEBITO) = montarComprovante(desp(natureza = n, tipo = t), null, "x", agora = agora).selo
        assertEquals(SeloComprovante.RECEITA, selo(Natureza.NORMAL, TipoDespesa.CREDITO))
        assertEquals(SeloComprovante.PAGAMENTO_FATURA, selo(Natureza.PAGAMENTO_FATURA))
        assertEquals(SeloComprovante.APORTE_META, selo(Natureza.APORTE_META))
        assertEquals(SeloComprovante.RESGATE_META, selo(Natureza.RESGATE_META, TipoDespesa.CREDITO))
        assertEquals(SeloComprovante.AJUSTE, selo(Natureza.AJUSTE))
    }

    @Test fun `quebra texto longo sem cortar e bitmap tem altura dinamica`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val longa = "Compra muito longa ".repeat(12).trim()
        val curta = montarComprovante(desp(), null, "123456-7", agora = agora)
        val grande = montarComprovante(desp(descricao = longa), null, "123456-7", ComprovanteExtras(observacoes = longa), agora)
        val bc = desenharComprovante(ctx, curta)
        val bg = desenharComprovante(ctx, grande)
        assertTrue(bg.height > bc.height)
        assertEquals(900, bc.width)
        val p = android.graphics.Paint().apply { textSize = 28f }
        quebrarTexto(longa, p, 420f).forEach { assertTrue(p.measureText(it) <= 420f + 0.5f) }
        assertEquals(longa, quebrarTexto(longa, p, 420f).joinToString(" "))
    }
}
