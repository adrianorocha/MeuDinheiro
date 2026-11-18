package com.meudinheiro.funcoes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.ImpostoRenda
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * R61 — exporta o [ImpostoRenda.Documento] em PDF (A4, várias páginas: aviso em destaque, resumo e listas) ou CSV.
 * Os arquivos ficam em `cache/extratos/` (já exposto pelo FileProvider); o compartilhamento usa `RelatorioExport.compartilhar`.
 */
object ImpostoRendaExport {

    private const val LARG = 595
    private const val ALT = 842
    private const val MARGEM = 36f
    private const val LIMITE_Y = ALT - MARGEM - 16f
    private val dataHoraBr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    private fun pasta(context: Context) = File(context.cacheDir, "extratos").apply { mkdirs() }

    fun csv(context: Context, doc: ImpostoRenda.Documento): File {
        val arq = File(pasta(context), doc.nomeArquivo + ".csv")
        // BOM UTF-8: o Excel abre acentos corretamente.
        FileOutputStream(arq).use {
            it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            it.write(ImpostoRenda.csv(doc).toByteArray(Charsets.UTF_8))
        }
        return arq
    }

    /** Controla páginas e rodapé. Com [pdf] nulo só mede (passada 1, para saber o total de páginas). */
    private class Paginador(private val pdf: PdfDocument?, private val total: Int, private val emitidoEm: String) {
        var numero = 0
        var y = 0f
        private var pagina: PdfDocument.Page? = null
        private val rascunho = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        var c: Canvas = rascunho
        private val rodape = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(100, 116, 139) }

        fun nova() {
            fechar()
            numero++
            if (pdf != null) {
                pagina = pdf.startPage(PdfDocument.PageInfo.Builder(LARG, ALT, numero).create())
                c = pagina!!.canvas
            } else c = rascunho
            y = MARGEM + 12
        }

        fun fechar() {
            if (numero == 0) return
            c.drawText("Meu Dinheiro · emitido em $emitidoEm", MARGEM, ALT - MARGEM + 14, rodape)
            c.drawText("Página $numero de $total", LARG - MARGEM, ALT - MARGEM + 14, Paint(rodape).apply { textAlign = Paint.Align.RIGHT })
            pagina?.let { pdf?.finishPage(it) }
            pagina = null
        }

        fun cabe(altura: Float) = y + altura <= LIMITE_Y
    }

    fun pdf(context: Context, doc: ImpostoRenda.Documento): File {
        val emitidoEm = dataHoraBr.format(Date())
        val medidor = Paginador(null, 0, emitidoEm)
        montar(medidor, doc)
        medidor.fechar()
        val total = medidor.numero

        val pdf = PdfDocument()
        val arq = File(pasta(context), doc.nomeArquivo + ".pdf")
        try {
            val p = Paginador(pdf, total, emitidoEm)
            montar(p, doc)
            p.fechar()
            FileOutputStream(arq).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return arq
    }

    private fun quebrar(s: String, paint: Paint, largura: Float): List<String> {
        if (s.isEmpty()) return listOf("")
        val out = ArrayList<String>()
        var resto = s
        while (resto.isNotEmpty()) {
            val n = paint.breakText(resto, true, largura, null)
            if (n <= 0) break
            var corte = n
            if (n < resto.length) {
                val sp = resto.lastIndexOf(' ', n - 1)
                if (sp > 0) corte = sp
            }
            out += resto.substring(0, corte).trim()
            resto = resto.substring(corte).trim()
        }
        return out.ifEmpty { listOf("") }
    }

    private fun montar(p: Paginador, doc: ImpostoRenda.Documento) {
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(30, 41, 59) }
        val negrito = Paint(texto).apply { isFakeBoldText = true }
        val cinza = Paint(texto).apply { color = Color.rgb(100, 116, 139); textSize = 8.5f }
        val titulo = Paint(negrito).apply { textSize = 16f }
        val direita = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val direitaNeg = Paint(negrito).apply { textAlign = Paint.Align.RIGHT }
        val linha = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 0.6f }
        val faixa = Paint().apply { color = Color.rgb(241, 245, 249) }

        p.nova()
        p.c.drawText(doc.titulo, MARGEM, p.y, titulo); p.y += 16
        p.c.drawText("Gerado em ${SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())} · Meu Dinheiro", MARGEM, p.y, cinza); p.y += 14

        // Aviso em destaque (caixa âmbar) + premissas
        val largTexto = LARG - 2 * MARGEM - 16
        val avisoPaint = Paint(texto).apply { color = Color.rgb(120, 53, 15) }
        val linhasAviso = ArrayList<Pair<String, Boolean>>() // texto, negrito
        doc.avisos.forEachIndexed { i, a ->
            quebrar(a, avisoPaint, largTexto).forEach { linhasAviso += it to (i == 0) }
            if (i == 0) linhasAviso += "" to false
        }
        val altAviso = linhasAviso.size * 11f + 14f
        if (!p.cabe(altAviso)) p.nova()
        p.c.drawRoundRect(RectF(MARGEM, p.y - 8, LARG - MARGEM, p.y - 8 + altAviso), 6f, 6f, Paint().apply { color = Color.rgb(254, 243, 199) })
        var ya = p.y + 4
        linhasAviso.forEach { (l, forte) ->
            p.c.drawText(l, MARGEM + 8, ya, if (forte) Paint(avisoPaint).apply { isFakeBoldText = true } else avisoPaint)
            ya += 11f
        }
        p.y += altAviso + 6

        // Resumo
        p.c.drawText("Resumo", MARGEM, p.y, negrito); p.y += 14
        doc.totais.forEach { (rot, valor) ->
            if (!p.cabe(14f)) p.nova()
            p.c.drawText(rot, MARGEM + 4, p.y, texto)
            p.c.drawText(valor, LARG - MARGEM, p.y, direita)
            p.y += 12.5f
        }
        p.y += 8

        // Tabela: cabeçalho de seção a cada mudança
        val xData = MARGEM; val xDesc = MARGEM + 52; val xInfo = MARGEM + 236; val xComp = MARGEM + 420; val xVal = LARG - MARGEM
        fun cabecalho() {
            p.c.drawText("Data", xData, p.y, negrito); p.c.drawText("Descrição", xDesc, p.y, negrito)
            p.c.drawText("Categoria · classe/tipo", xInfo, p.y, negrito); p.c.drawText("Comprov.", xComp, p.y, negrito)
            p.c.drawText("Valor", xVal, p.y, direitaNeg)
            p.c.drawLine(MARGEM, p.y + 4, LARG - MARGEM, p.y + 4, linha); p.y += 15
        }
        if (!p.cabe(40f)) p.nova()
        cabecalho()
        var secaoAtual = ""
        doc.linhas.forEachIndexed { i, l ->
            val secao = l[0]
            if (secao != secaoAtual) {
                if (!p.cabe(40f)) { p.nova(); cabecalho() }
                p.y += 4
                p.c.drawRect(MARGEM, p.y - 10, LARG - MARGEM, p.y + 4, faixa)
                p.c.drawText(secao, MARGEM + 3, p.y, negrito); p.y += 15
                secaoAtual = secao
            }
            val desc = quebrar(l[2], texto, xInfo - xDesc - 6f).take(2)
            val info = quebrar(listOf(l[3], l[4]).filter { it.isNotEmpty() }.joinToString(" · "), texto, xComp - xInfo - 6f).take(2)
            val h = maxOf(desc.size, info.size) * 11.5f + 1.5f
            if (!p.cabe(h + 4f)) { p.nova(); cabecalho() }
            p.c.drawText(l[1], xData, p.y, texto)
            desc.forEachIndexed { k, t -> p.c.drawText(t, xDesc, p.y + k * 11.5f, texto) }
            info.forEachIndexed { k, t -> p.c.drawText(t, xInfo, p.y + k * 11.5f, texto) }
            p.c.drawText(l[5], xComp, p.y, texto)
            p.c.drawText(FormatoCentavos.brl(doc.centavosPorLinha[i]), xVal, p.y, direita)
            p.y += h
        }
        if (doc.linhas.isEmpty()) p.c.drawText("Nada a listar neste ano.", MARGEM, p.y, cinza)
    }
}
