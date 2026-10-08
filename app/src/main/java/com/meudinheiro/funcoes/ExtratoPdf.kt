package com.meudinheiro.funcoes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.domain.Financas
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gera o extrato mensal em PDF (A4, várias páginas, valores em R$) e abre o seletor de compartilhamento. */
object ExtratoPdf {

    private const val LARGURA = 595
    private const val ALTURA = 842
    private const val MARGEM = 40f
    private const val LINHA = 18f
    private const val RODAPE = 60f

    fun exportarECompartilhar(context: Context, mes: String, ano: Int, despesas: List<DespesasDomain>) {
        val arquivo = gerar(context, mes, ano, despesas)
        // Autoridade igual à declarada no AndroidManifest; arquivo dentro de cache/extratos (file_paths.xml).
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Extrato").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun gerar(context: Context, mes: String, ano: Int, despesas: List<DespesasDomain>): File {
        val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val ordenadas = despesas.sortedBy { it.data }

        val kpis = Financas.kpisPeriodo(ordenadas, null, null)

        val texto = Paint().apply { textSize = 10f; color = Color.BLACK; isAntiAlias = true }
        val negrito = Paint(texto).apply { isFakeBoldText = true }
        val titulo = Paint(negrito).apply { textSize = 18f }
        val direita = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val direitaNegrito = Paint(negrito).apply { textAlign = Paint.Align.RIGHT }

        val pdf = PdfDocument()
        var numeroPagina = 0
        lateinit var pagina: PdfDocument.Page
        var y = 0f

        fun novaPagina() {
            if (numeroPagina > 0) pdf.finishPage(pagina)
            numeroPagina++
            pagina = pdf.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numeroPagina).create())
            y = MARGEM + 10f
            if (numeroPagina == 1) {
                pagina.canvas.drawText("Extrato Mensal - $mes / $ano", MARGEM, y, titulo)
                y += 20f
                pagina.canvas.drawText("Gerado em ${formatoData.format(Date())} pelo Meu Dinheiro", MARGEM, y, texto)
                y += 28f
            }
            pagina.canvas.drawText("Data", MARGEM, y, negrito)
            pagina.canvas.drawText("Descrição", MARGEM + 70f, y, negrito)
            pagina.canvas.drawText("Categoria", MARGEM + 300f, y, negrito)
            pagina.canvas.drawText("Valor", LARGURA - MARGEM, y, direitaNegrito)
            pagina.canvas.drawLine(MARGEM, y + 5f, LARGURA - MARGEM, y + 5f, texto)
            y += LINHA + 4f
        }

        novaPagina()
        ordenadas.forEach { item ->
            if (y > ALTURA - RODAPE) novaPagina()
            val sinal = if (item.tipo.name == "CREDITO") "+ " else "- "
            pagina.canvas.drawText(formatoData.format(Date(item.data)), MARGEM, y, texto)
            pagina.canvas.drawText(item.descricao.take(42), MARGEM + 70f, y, texto)
            pagina.canvas.drawText(item.categoria.take(20), MARGEM + 300f, y, texto)
            pagina.canvas.drawText(sinal + moeda.format(item.valor), LARGURA - MARGEM, y, direita)
            y += LINHA
        }

        if (y > ALTURA - RODAPE - 60f) novaPagina()
        y += 10f
        pagina.canvas.drawLine(MARGEM, y, LARGURA - MARGEM, y, texto)
        y += LINHA
        pagina.canvas.drawText("Receitas realizadas: ${moeda.format(kpis.receitasRealizadas)}", MARGEM, y, negrito)
        y += LINHA
        pagina.canvas.drawText("Despesas: ${moeda.format(kpis.despesasTotal)}", MARGEM, y, negrito)
        y += LINHA
        pagina.canvas.drawText("Resultado: ${moeda.format(kpis.resultado)}", MARGEM, y, negrito)
        pdf.finishPage(pagina)

        val pasta = File(context.cacheDir, "extratos").apply { mkdirs() }
        val arquivo = File(pasta, "Extrato_${mes}_$ano.pdf")
        try {
            FileOutputStream(arquivo).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return arquivo
    }
}
