package com.meudinheiro.funcoes

import com.meudinheiro.data.Acerto
import com.meudinheiro.data.Pessoa
import com.meudinheiro.domain.RelatorioPessoas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * R69 — layout puro (altura dinâmica, divisão em partes, paginação do PDF) + bitmap em Robolectric. O PDF em si (PdfDocument) não roda
 * em Robolectric: só o plano de páginas é testado; o desenho é o mesmo [RelatorioPessoasExport.desenharPagina] das imagens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelatorioPessoasExportTest {

    private fun ms(m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, 12, 0, 0) }.timeInMillis
    private val agora = ms(9, 30)
    private val medidor = MedidorTexto { t, _, tam -> t.length * tam * 0.5f }
    private val pessoas = (1..12).map { Pessoa(it, "Pessoa $it com um nome razoavelmente comprido", if (it % 2 == 0) "11 99999-0000" else null) }

    private fun acertos(n: Int) = (0 until n).map { i ->
        Acerto(
            id = i + 1, pessoaId = i % pessoas.size + 1, tipo = if (i % 3 == 0) "A_PAGAR" else "A_RECEBER", valor = 10 + (i % 17) * 1.37,
            descricao = "Acerto número ${i + 1} com descrição longa para forçar a quebra de linha na imagem e no PDF do relatório",
            data = ms(1 + i % 9, 1 + i % 27), quitado = i % 4 == 0, quitadoEm = if (i % 4 == 0) ms(9, 2) else null
        )
    }

    private fun rel(n: Int, agrupar: RelatorioPessoas.Agrupar = RelatorioPessoas.Agrupar.POR_PESSOA, detalhes: Boolean = true) =
        RelatorioPessoas.montar(pessoas, acertos(n), emptyList(), RelatorioPessoas.Opcoes(agrupar = agrupar, incluirDetalhes = detalhes, agora = agora))

    @Test fun `poucos acertos - uma so imagem com altura igual a cabecalho + blocos + rodape`() {
        val plano = LayoutPessoas.planejar(rel(6), medidor, LayoutPessoas.MAX_ALTURA_IMAGEM)
        assertEquals(1, plano.paginas.size)
        val p = plano.paginas[0]
        assertEquals(p.hCabecalho + p.blocos.sumOf { it.h.toDouble() }.toFloat() + LayoutPessoas.H_RODAPE, p.altura, 0.01f)
        assertTrue(p.altura < LayoutPessoas.MAX_ALTURA_IMAGEM)
    }

    @Test fun `lista longa - partes de no maximo 6000 px, sem perder acertos nem isolar cabecalho de pessoa`() {
        val r = rel(400)
        val plano = LayoutPessoas.planejar(r, medidor, LayoutPessoas.MAX_ALTURA_IMAGEM)
        assertTrue(plano.paginas.size > 2)
        for (p in plano.paginas) {
            assertTrue(p.altura <= LayoutPessoas.MAX_ALTURA_IMAGEM)
            assertEquals(plano.paginas.size, p.total)
            assertTrue(p.blocos.last() is LayoutPessoas.Bloco.Linha)
            if (p.indice > 1 && p.blocos[0] !is LayoutPessoas.Bloco.CartaoPessoa && p.blocos[0] !is LayoutPessoas.Bloco.Titulo) {
                assertTrue(p.blocos[0] is LayoutPessoas.Bloco.Cont)
            }
        }
        val ids = plano.paginas.flatMap { it.blocos }.filterIsInstance<LayoutPessoas.Bloco.Linha>().map { it.linha.acertoId }
        assertEquals(400, ids.size)
        assertEquals(400, ids.toSet().size)
        assertEquals(r.pessoas.size, plano.paginas.flatMap { it.blocos }.count { it is LayoutPessoas.Bloco.CartaoPessoa })
    }

    @Test fun `paginacao do PDF - paginas A4 sem estourar a altura e com Pagina X de Y coerente`() {
        val plano = LayoutPessoas.planejar(rel(120), medidor, LayoutPessoas.ALTURA_PAGINA_PDF)
        assertTrue(plano.paginas.size > 3)
        for (p in plano.paginas) {
            assertTrue(p.altura <= LayoutPessoas.ALTURA_PAGINA_PDF)
            assertEquals(plano.paginas.size, p.total)
        }
        assertEquals((1..plano.paginas.size).toList(), plano.paginas.map { it.indice })
        val ids = plano.paginas.flatMap { it.blocos }.filterIsInstance<LayoutPessoas.Bloco.Linha>().map { it.linha.acertoId }
        assertEquals(120, ids.toSet().size)
    }

    @Test fun `quebra de texto longo nao corta e modo cronologico ordena por data`() {
        val longa = "palavra ".repeat(80).trim()
        val linhas = LayoutPessoas.quebrar(longa, 300f) { it.length * 8f }
        assertTrue(linhas.size > 1)
        linhas.forEach { assertTrue(it.length * 8f <= 300f) }
        assertEquals(longa, linhas.joinToString(" "))
        assertEquals(listOf("a".repeat(37), "a".repeat(37), "aaa"), LayoutPessoas.quebrar("a".repeat(77), 300f) { it.length * 8f })

        val plano = LayoutPessoas.planejar(rel(30, RelatorioPessoas.Agrupar.CRONOLOGICO), medidor, LayoutPessoas.MAX_ALTURA_IMAGEM)
        val datas = plano.paginas.flatMap { it.blocos }.filterIsInstance<LayoutPessoas.Bloco.Linha>().map { it.linha.data }
        assertEquals(datas.sorted(), datas)
        assertEquals(30, datas.size)
    }

    @Test fun `bitmap tem altura dinamica, largura fixa`() {
        val curto = rel(3)
        val longo = rel(60)
        val pc = RelatorioPessoasExport.planoImagens(curto)
        val pl = RelatorioPessoasExport.planoImagens(longo)
        val bc = RelatorioPessoasExport.bitmapDaParte(curto, pc, pc.paginas[0])
        val bl = RelatorioPessoasExport.bitmapDaParte(longo, pl, pl.paginas[0])
        assertEquals(LayoutPessoas.LARGURA, bc.width)
        assertEquals(LayoutPessoas.LARGURA, bl.width)
        assertEquals(Math.ceil(pc.paginas[0].altura.toDouble()).toInt(), bc.height)
        assertTrue(bl.height > bc.height)
        assertTrue(bl.height <= LayoutPessoas.MAX_ALTURA_IMAGEM.toInt())
        // Robolectric (gráficos legados) não rasteriza o Canvas: só dimensões são verificáveis aqui; o visual não foi inspecionado.
    }

    @Test fun `cores de avatar estaveis e dentro da paleta`() {
        assertEquals(RelatorioPessoasExport.corAvatar(5), RelatorioPessoasExport.corAvatar(5))
        assertEquals(RelatorioPessoasExport.corAvatar(0), RelatorioPessoasExport.corAvatar(8))
        assertEquals(RelatorioPessoasExport.corAvatar(7), RelatorioPessoasExport.corAvatar(-1))
    }
}
