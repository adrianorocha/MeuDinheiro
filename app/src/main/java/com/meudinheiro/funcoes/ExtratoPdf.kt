package com.meudinheiro.funcoes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.ExtratoResumo
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.parcelaDaDescricao
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

    private val LIMITE_Y = ALTURA - MARGEM - 16f

    fun gerar(context: Context, mes: String, ano: Int, despesas: List<DespesasDomain>): File {
        val emitidoEm = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        // Passada 1 só mede o número de páginas; passada 2 desenha com "Página X de Y".
        val total = desenhar(null, 0, mes, ano, despesas, emitidoEm)
        val pdf = PdfDocument()
        val pasta = File(context.cacheDir, "extratos").apply { mkdirs() }
        val arquivo = File(pasta, "Extrato_${mes}_$ano.pdf")
        try {
            desenhar(pdf, total, mes, ano, despesas, emitidoEm)
            FileOutputStream(arquivo).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return arquivo
    }

    /** Desenha o extrato; com [pdf] nulo apenas mede. Devolve o número de páginas. */
    private fun desenhar(pdf: PdfDocument?, totalPaginas: Int, mes: String, ano: Int, despesas: List<DespesasDomain>, emitidoEm: String): Int {
        val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val ordenadas = despesas.sortedWith(compareBy({ it.data }, { it.id }))
        val kpis = Financas.kpisPeriodo(ordenadas, null, null)
        val resumo = ExtratoResumo.calcular(ordenadas)

        val texto = Paint().apply { textSize = 9.5f; color = Color.rgb(30, 41, 59); isAntiAlias = true }
        val negrito = Paint(texto).apply { isFakeBoldText = true }
        val cinza = Paint(texto).apply { color = Color.rgb(100, 116, 139); textSize = 8f }
        val titulo = Paint(negrito).apply { textSize = 18f }
        val direita = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val direitaNegrito = Paint(negrito).apply { textAlign = Paint.Align.RIGHT }
        val linhaPaint = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 0.6f }
        val rascunho = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

        var numero = 0
        var pagina: PdfDocument.Page? = null
        var c: Canvas = rascunho
        var y = 0f

        fun fecharPagina() {
            if (numero == 0) return
            c.drawLine(MARGEM, ALTURA - MARGEM + 2, LARGURA - MARGEM, ALTURA - MARGEM + 2, linhaPaint)
            c.drawText("Meu Dinheiro · emitido em $emitidoEm", MARGEM, ALTURA - MARGEM + 14, cinza)
            c.drawText("Página $numero de $totalPaginas", LARGURA - MARGEM, ALTURA - MARGEM + 14, Paint(cinza).apply { textAlign = Paint.Align.RIGHT })
            pagina?.let { pdf?.finishPage(it) }
            pagina = null
        }

        fun novaPagina() {
            fecharPagina()
            numero++
            if (pdf != null) {
                pagina = pdf.startPage(PdfDocument.PageInfo.Builder(LARGURA, ALTURA, numero).create())
                c = pagina!!.canvas
            } else c = rascunho
            y = MARGEM + 10f
            if (numero == 1) {
                c.drawText("Extrato Mensal - $mes / $ano", MARGEM, y, titulo)
                y += 20f
                c.drawText("Gerado em ${formatoData.format(Date())} pelo Meu Dinheiro · ${ordenadas.size} lançamentos", MARGEM, y, texto)
                y += 28f
            }
            // cabeçalho da tabela repetido em todas as páginas
            c.drawText("Data", MARGEM, y, negrito)
            c.drawText("Descrição", MARGEM + 58f, y, negrito)
            c.drawText("Categoria", MARGEM + 270f, y, negrito)
            c.drawText("Situação", MARGEM + 370f, y, negrito)
            c.drawText("Valor", LARGURA - MARGEM, y, direitaNegrito)
            c.drawLine(MARGEM, y + 5f, LARGURA - MARGEM, y + 5f, texto)
            y += LINHA + 4f
        }
        fun cabe(h: Float) = y + h <= LIMITE_Y

        novaPagina()
        ordenadas.forEach { item ->
            val p = parcelaDaDescricao(item.descricao)
            val desc = if (p != null) "${p.first} (${p.second}/${p.third})" else item.descricao
            val linhasDesc = quebrarTexto(desc, texto, 205f).let { if (it.size > 2) listOf(it[0], it[1].dropLast(1).trimEnd() + "…") else it }
            val h = linhasDesc.size * 12f + 6f
            if (!cabe(h)) novaPagina()
            val sinal = if (item.tipo == TipoDespesa.CREDITO) "+ " else "- "
            c.drawText(formatoData.format(Date(item.data)), MARGEM, y, texto)
            linhasDesc.forEachIndexed { i, t -> c.drawText(t, MARGEM + 58f, y + i * 12f, texto) }
            c.drawText(item.categoria.take(20), MARGEM + 270f, y, texto)
            c.drawText(if (item.pago) "Pago" else "Pendente", MARGEM + 370f, y, texto)
            c.drawText(sinal + moeda.format(item.valor), LARGURA - MARGEM, y, direita)
            y += h
        }

        // Subtotais: entradas, saídas e saldo do extrato (em centavos, fecham com a lista) + por categoria
        fun linhaValor(rotulo: String, centavos: Long, forte: Boolean = false) {
            if (!cabe(LINHA)) novaPagina()
            val pt = if (forte) negrito else texto
            c.drawText(rotulo, MARGEM, y, pt)
            c.drawText(moeda.format(Dinheiro.reais(centavos)), LARGURA - MARGEM, y, if (forte) direitaNegrito else direita)
            y += LINHA
        }
        if (!cabe(120f)) novaPagina()
        y += 8f
        c.drawLine(MARGEM, y, LARGURA - MARGEM, y, texto)
        y += LINHA
        linhaValor("Entradas listadas (${ordenadas.count { it.tipo == TipoDespesa.CREDITO }})", resumo.entradasCentavos)
        linhaValor("Saídas listadas (${ordenadas.count { it.tipo == TipoDespesa.DEBITO }})", resumo.saidasCentavos)
        linhaValor("Saldo do extrato (entradas − saídas)", resumo.saldoCentavos, forte = true)
        y += 8f
        if (resumo.porCategoria.isNotEmpty()) {
            if (!cabe(40f)) novaPagina()
            c.drawText("Subtotais por categoria (entradas − saídas)", MARGEM, y, negrito); y += LINHA
            resumo.porCategoria.forEach { (nome, cent) -> linhaValor(nome.take(40), cent) }
            linhaValor("Soma das categorias", resumo.porCategoria.sumOf { it.second }, forte = true)
            y += 8f
        }
        if (!cabe(70f)) novaPagina()
        c.drawText("Receitas realizadas: ${moeda.format(kpis.receitasRealizadas)}", MARGEM, y, negrito)
        y += LINHA
        c.drawText("Despesas: ${moeda.format(kpis.despesasTotal)}", MARGEM, y, negrito)
        y += LINHA
        c.drawText("Resultado: ${moeda.format(kpis.resultado)}", MARGEM, y, negrito)
        fecharPagina()
        return numero
    }
}
