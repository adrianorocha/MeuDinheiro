package com.meudinheiro.funcoes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.FiltroRelatorio
import com.meudinheiro.domain.Relatorios
import com.meudinheiro.domain.TipoRelatorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

/** Garante que PDF, PNG e CSV são gerados (arquivo existe, não vazio, formato correto) — inclusive com muitas páginas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelatorioExportTest {

    private fun ms(m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(2026, m - 1, d, 12, 0, 0) }.timeInMillis

    private fun dados(qtd: Int) = (1..qtd).map { i ->
        Despesa(
            id = i.toLong(), descricao = "Posto de combustível número $i", valor = 10.0 + i, data = Date(ms(9, 1 + i % 28)),
            categoria = if (i % 2 == 0) "Combustível" else "Alimentação", conta = "111", pic = "p", tipo = TipoDespesa.DEBITO,
            mes = 9, ano = 2026, pago = true
        )
    }

    private fun resultado(qtd: Int) = Relatorios.gerar(
        FiltroRelatorio(Financas.inicioDoMes(9, 2026), Financas.fimDoMes(9, 2026), tipo = TipoRelatorio.DESPESA), dados(qtd)
    )

    private val desc = DescricaoRelatorio("Gastos de Combustível", "01/09/2026 a 30/09/2026", listOf("Cartões: Visa", "Categorias: Combustível"))
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `csv tem BOM utf8 e cabecalho`() {
        val f = RelatorioExport.csv(ctx, resultado(3), desc) { "Nubank" }
        val bytes = f.readBytes()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertTrue(String(bytes, 3, bytes.size - 3, Charsets.UTF_8).startsWith("Data;Descrição;Categoria;Conta;Tipo;Pago;Valor"))
        assertTrue(f.name.endsWith(".csv"))
    }

    /** O PdfDocument depende de código nativo que o Robolectric (JVM) não tem: nesse caso os testes de PDF são ignorados. */
    private fun pdfDisponivel() = runCatching { android.graphics.pdf.PdfDocument().startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(10, 10, 1).create()); true }
        .getOrDefault(false)

    @Test fun `pdf e gerado com varias paginas sem erro`() {
        org.junit.Assume.assumeTrue("PdfDocument indisponível no Robolectric", pdfDisponivel())
        val f = RelatorioExport.pdf(ctx, resultado(120), desc) { "Nubank" }
        assertTrue(f.name.endsWith(".pdf")); assertTrue(f.length() > 0)
    }

    @Test fun `pdf sem lancamentos tambem e gerado`() {
        org.junit.Assume.assumeTrue("PdfDocument indisponível no Robolectric", pdfDisponivel())
        assertTrue(RelatorioExport.pdf(ctx, resultado(0), desc) { it }.length() > 0)
    }

    @Test fun `png e gerado`() {
        val f = RelatorioExport.png(ctx, resultado(10), desc)
        assertTrue(f.name.endsWith(".png")); assertTrue(f.length() > 0)
    }
}
