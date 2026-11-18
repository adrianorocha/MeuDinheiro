package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.TipoDespesa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/** R55 — MESMOS cenários de portal/src/lib/finance/regras.test.ts. */
class RegrasCategoriaTest {

    private fun r(
        id: Int, padrao: String, categoria: String, tipoMatch: String = "CONTEM", conta: String? = null,
        tipo: String? = null, ativa: Boolean = true, prioridade: Int = 0
    ) = RegraCategoria(id, padrao, tipoMatch, categoria, conta, tipo, ativa, prioridade, 1L)

    private val regras = listOf(
        r(1, "uber", "Transporte"),
        r(2, "uber eats", "Restaurantes"),
        r(3, "mercado", "Supermercado", tipoMatch = "COMECA_COM"),
        r(4, "Padaria", "Padaria", tipoMatch = "IGUAL"),
        r(5, "pix", "Transferências", prioridade = 5),
        r(6, "uber", "Lazer", ativa = false, prioridade = 10),
        r(7, "salario", "Salário", tipo = "CREDITO"),
        r(8, "academia", "Saúde", conta = "222")
    )

    private fun cat(descricao: String, conta: String = "111", tipo: String = "DEBITO") =
        Regras.aplicar(regras, Regras.Entrada(descricao, conta, tipo))?.categoria

    private fun desp(id: Long, desc: String, cat: String, tipo: TipoDespesa = TipoDespesa.DEBITO, natureza: String = Natureza.NORMAL) =
        Despesa(id = id, descricao = desc, valor = 10.0, data = Date(1_760_000_000_000), categoria = cat, conta = "111", pic = "p",
            tipo = tipo, mes = 10, ano = 2025, pago = true, natureza = natureza)

    @Test fun `contem ignora acento e caixa e regra inativa nao conta`() {
        assertEquals("Transporte", cat("Uber *Trip 123"))
        assertEquals("Transporte", cat("UBER"))
    }

    @Test fun `empate de prioridade vence o padrao mais longo`() {
        assertEquals("Restaurantes", cat("UBER EATS pedido"))
    }

    @Test fun `comeca com e igual`() {
        assertEquals("Supermercado", cat("Mercado Central"))
        assertNull(cat("Super Mercado"))
        assertEquals("Padaria", cat("padaria"))
        assertEquals("Padaria", cat("  PADARIA  "))
        assertNull(cat("Padaria do Zé"))
    }

    @Test fun `maior prioridade vence mesmo com padrao mais curto`() {
        assertEquals("Transferências", cat("Pix uber eats"))
    }

    @Test fun `restricao de tipo e acento na descricao`() {
        assertNull(cat("SALÁRIO março", tipo = "DEBITO"))
        assertEquals("Salário", cat("SALÁRIO março", tipo = "CREDITO"))
    }

    @Test fun `restricao de conta`() {
        assertNull(cat("Academia Fit", conta = "111"))
        assertEquals("Saúde", cat("Academia Fit", conta = "222"))
    }

    @Test fun `empate total vence o menor id e padrao vazio nunca casa`() {
        val iguais = listOf(r(10, "abc", "X"), r(9, "abc", "Y"))
        assertEquals("Y", Regras.aplicar(iguais, Regras.Entrada("abc"))?.categoria)
        assertNull(Regras.aplicar(listOf(r(1, "   ", "Z")), Regras.Entrada("qualquer")))
        assertNull(Regras.aplicar(emptyList(), Regras.Entrada("x")))
    }

    @Test fun `validacao`() {
        assertEquals("Informe o texto da regra.", Regras.validar(" ", "A", "CONTEM"))
        assertEquals("Informe a categoria.", Regras.validar("a", " ", "CONTEM"))
        assertEquals("Tipo de comparação inválido.", Regras.validar("a", "A", "XYZ"))
        assertNull(Regras.validar("a", "A", "IGUAL"))
    }

    @Test fun `previa lista so o que mudaria, aplica e desfaz`() {
        val despesas = listOf(
            desp(1, "Uber trip", "Outros"),
            desp(2, "Uber trip", "transporte"),
            desp(3, "Uber trip", "Outros", natureza = Natureza.TRANSFERENCIA),
            desp(4, "Compra qualquer", "Outros"),
            desp(5, "Salário", "Outros", tipo = TipoDespesa.CREDITO)
        )
        val antes = despesas.toList()
        val previa = Regras.previa(despesas, regras)
        assertEquals(listOf(Triple(1L, "Outros", "Transporte"), Triple(5L, "Outros", "Salário")), previa.map { Triple(it.id, it.de, it.para) })
        assertEquals(antes, despesas)

        val (novas, desfazer) = Regras.aplicarMudancas(despesas, previa)
        assertEquals("Transporte", novas.first { it.id == 1L }.categoria)
        assertEquals("Outros", novas.first { it.id == 3L }.categoria)
        assertEquals(listOf(Regras.Anterior(1, "Outros"), Regras.Anterior(5, "Outros")), desfazer)
        assertEquals(despesas, Regras.desfazer(novas, desfazer))
    }

    @Test fun `aprendizado leve`() {
        val base = listOf(desp(1, "Netflix", "Lazer"), desp(2, "NETFLIX ", "Lazer"), desp(3, "Cinema", "Lazer"))
        assertEquals(Regras.Sugestao("netflix", "Lazer", 2), Regras.sugerirAprendida(base, emptyList(), base[1]))
        assertNull(Regras.sugerirAprendida(base, emptyList(), base[2]))
        assertNull(Regras.sugerirAprendida(base, listOf(r(1, "netflix", "Lazer")), base[1]))
        val so = listOf(base[0], desp(9, "Netflix", "Lazer", natureza = Natureza.AJUSTE))
        assertNull(Regras.sugerirAprendida(so, emptyList(), so[0]))
    }
}
