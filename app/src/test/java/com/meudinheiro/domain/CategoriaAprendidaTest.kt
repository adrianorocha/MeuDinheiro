package com.meudinheiro.domain

import com.meudinheiro.data.Despesa
import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.CategoriaAprendida.Origem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CategoriaAprendidaTest {
    private val cats = listOf("Alimentação", "Transporte", "Mercado", "Lazer")

    // ------------------------------------------------------------ normalização

    @Test fun `tira ruido de maquininha, numeros, caixa e acento`() {
        assertEquals("padaria", CategoriaAprendida.normalizarEstabelecimento("PAG*Padaria 123"))
        assertEquals("padaria", CategoriaAprendida.normalizarEstabelecimento("padaria"))
        assertEquals("padaria", CategoriaAprendida.normalizarEstabelecimento("MP *PADARIA  0456"))
        assertEquals("padaria sao jose", CategoriaAprendida.normalizarEstabelecimento("Padaria São José"))
        assertEquals("supermercado extra", CategoriaAprendida.normalizarEstabelecimento("SUMUP*Supermercado Extra Ltda 77"))
        assertEquals("farmacia", CategoriaAprendida.normalizarEstabelecimento("Farmácia - Loja 12"))
    }

    @Test fun `sem identificacao devolve vazio`() {
        assertEquals("", CategoriaAprendida.normalizarEstabelecimento("PAG* 123"))
        assertEquals("", CategoriaAprendida.normalizarEstabelecimento(""))
        assertEquals("", CategoriaAprendida.normalizarEstabelecimento("de da do"))
    }

    @Test fun `limita a tres palavras`() {
        assertEquals("casa carne boi", CategoriaAprendida.normalizarEstabelecimento("Casa de Carne Boi Gordo Centro"))
    }

    // ------------------------------------------------------------ memória

    @Test fun `aprende e busca pelo estabelecimento normalizado`() {
        val m = CategoriaAprendida.aprender(emptyMap(), "PAG*Padaria 123", "Alimentação", 1000)
        assertEquals("Alimentação", CategoriaAprendida.buscar(m, "Padaria 999"))
        assertEquals("Alimentação", CategoriaAprendida.buscar(m, "MP*PADARIA"))
        assertNull(CategoriaAprendida.buscar(m, "Posto Shell"))
    }

    @Test fun `mesma categoria soma, outra categoria vence pela mais recente`() {
        var m = CategoriaAprendida.aprender(emptyMap(), "Padaria", "Alimentação", 1)
        m = CategoriaAprendida.aprender(m, "PAG*Padaria 2", "Alimentação", 2)
        assertEquals(2, m["padaria"]!!.vezes)
        m = CategoriaAprendida.aprender(m, "Padaria", "Lazer", 3)
        assertEquals("Lazer", m["padaria"]!!.categoria)
        assertEquals(1, m["padaria"]!!.vezes)
    }

    @Test fun `sem chave ou sem categoria nao aprende`() {
        val vazio = emptyMap<String, CategoriaAprendida.Entrada>()
        assertEquals(vazio, CategoriaAprendida.aprender(vazio, "PAG* 123", "Lazer", 1))
        assertEquals(vazio, CategoriaAprendida.aprender(vazio, "Padaria", "  ", 1))
    }

    @Test fun `busca por prefixo de palavras prefere a chave mais longa`() {
        var m = CategoriaAprendida.aprender(emptyMap(), "Padaria", "Alimentação", 1)
        m = CategoriaAprendida.aprender(m, "Padaria Doce Lar", "Lazer", 2)
        assertEquals("Lazer", CategoriaAprendida.buscar(m, "Padaria Doce Lar 55"))
        assertEquals("Alimentação", CategoriaAprendida.buscar(m, "Padaria Estrela"))
        val m2 = CategoriaAprendida.aprender(emptyMap(), "Posto Ipiranga Centro", "Transporte", 1)
        assertEquals("Transporte", CategoriaAprendida.buscar(m2, "Posto Ipiranga"))
        assertNull(CategoriaAprendida.buscar(m2, "Posto Shell"))
    }

    private fun nome(n: Int): String = "loja" + n.toString().map { 'a' + it.digitToInt() }.joinToString("")

    @Test fun `limita o tamanho descartando as menos recentes`() {
        var m: MemoriaCategorias = emptyMap()
        val total = CategoriaAprendida.MAX_ENTRADAS + 20
        for (i in 1..total) m = CategoriaAprendida.aprender(m, nome(i), "Lazer", i.toLong())
        assertEquals(CategoriaAprendida.MAX_ENTRADAS, m.size)
        assertTrue(m.containsKey(CategoriaAprendida.normalizarEstabelecimento(nome(total))))
        assertTrue(!m.containsKey(CategoriaAprendida.normalizarEstabelecimento(nome(1))))
    }

    // ------------------------------------------------------------ escolha

    private fun desp(desc: String, cat: String) = Despesa(
        descricao = desc, valor = 10.0, data = Date(0), categoria = cat, conta = "1", pic = "p", tipo = TipoDespesa.DEBITO, mes = 1, ano = 2026, pago = true
    )

    private fun regra(padrao: String, cat: String) = RegraCategoria(id = 1, padrao = padrao, tipoMatch = "CONTEM", categoria = cat)

    @Test fun `regra explicita vence a aprendida, que vence o historico`() {
        val m = CategoriaAprendida.aprender(emptyMap(), "Padaria", "Alimentação", 1)
        val hist = listOf(desp("Padaria", "Lazer"), desp("Padaria", "Lazer"))
        val porRegra = CategoriaAprendida.escolher("Padaria", "Padaria", "1", listOf(regra("padaria", "Mercado")), m, hist, cats)
        assertEquals(CategoriaAprendida.Escolha("Mercado", Origem.REGRA), porRegra)
        val aprendida = CategoriaAprendida.escolher("Padaria", "Padaria", "1", emptyList(), m, hist, cats)
        assertEquals(CategoriaAprendida.Escolha("Alimentação", Origem.APRENDIDA), aprendida)
        val historico = CategoriaAprendida.escolher("Padaria", "Padaria", "1", emptyList(), emptyMap(), hist, cats)
        assertEquals(CategoriaAprendida.Escolha("Lazer", Origem.HISTORICO), historico)
        assertEquals(Origem.NENHUMA, CategoriaAprendida.escolher("Xpto", "Xpto", "1", emptyList(), emptyMap(), emptyList(), cats).origem)
    }

    @Test fun `categoria que nao existe mais e ignorada e devolve o nome cadastrado`() {
        val m = CategoriaAprendida.aprender(emptyMap(), "Padaria", "Categoria Apagada", 1)
        assertEquals(Origem.NENHUMA, CategoriaAprendida.escolher("Padaria", "Padaria", "1", emptyList(), m, emptyList(), cats).origem)
        val m2 = CategoriaAprendida.aprender(emptyMap(), "Padaria", "alimentacao", 1)
        assertEquals("Alimentação", CategoriaAprendida.escolher("Padaria", "Padaria", "1", emptyList(), m2, emptyList(), cats).categoria)
    }

    // ------------------------------------------------------------ serialização

    @Test fun `serializa e le de volta e tolera lixo`() {
        val m = CategoriaAprendida.aprender(emptyMap(), "Padaria", "Alimentação", 5)
        assertEquals(m, CategoriaAprendida.desserializar(CategoriaAprendida.serializar(m)))
        val vazio = emptyMap<String, CategoriaAprendida.Entrada>()
        assertEquals(vazio, CategoriaAprendida.desserializar("{lixo"))
        assertEquals(vazio, CategoriaAprendida.desserializar(null))
        assertEquals(vazio, CategoriaAprendida.desserializar("""{"versao":1,"itens":{"x":null,"y":{"categoria":"","vezes":1,"ultimoUso":1}}}"""))
    }
}
