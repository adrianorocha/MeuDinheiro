package com.meudinheiro.funcoes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Relatorios
import com.meudinheiro.domain.ResultadoRelatorio
import com.meudinheiro.domain.TipoRelatorio
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Descrição legível dos filtros aplicados (aparece no cabeçalho do PDF/PNG). */
data class DescricaoRelatorio(
    val titulo: String,
    val periodo: String,
    val filtros: List<String>
)

/**
 * Exporta um [ResultadoRelatorio] (R19/R20) em PDF (A4, várias páginas), PNG (imagem-resumo) ou CSV,
 * e compartilha o arquivo. Arquivos ficam em `cache/extratos/` (já exposto pelo FileProvider).
 */
object RelatorioExport {

    private val moeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dataBr = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    private val CORES = intArrayOf(
        0xFF00B8D4.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF9800.toInt(), 0xFF00C853.toInt(), 0xFFFF5252.toInt(),
        0xFF2979FF.toInt(), 0xFFFFD600.toInt(), 0xFFEC407A.toInt(), 0xFF90A4AE.toInt()
    )

    private fun pasta(context: Context) = File(context.cacheDir, "extratos").apply { mkdirs() }
    private fun nomeArquivo(base: String, ext: String) =
        base.lowercase(Locale("pt", "BR")).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "relatorio" } + "_" +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + "." + ext

    // ------------------------------------------------------------------ CSV

    fun csv(context: Context, r: ResultadoRelatorio, desc: DescricaoRelatorio, nomeConta: (String) -> String): File {
        val arq = File(pasta(context), nomeArquivo(desc.titulo, "csv"))
        // BOM UTF-8: o Excel abre acentos corretamente.
        FileOutputStream(arq).use { it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())); it.write(Relatorios.csv(r, nomeConta).toByteArray(Charsets.UTF_8)) }
        return arq
    }

    // ------------------------------------------------------------------ PDF

    private const val LARG = 595
    private const val ALT = 842
    private const val MARGEM = 36f

    fun pdf(context: Context, r: ResultadoRelatorio, desc: DescricaoRelatorio, nomeConta: (String) -> String): File {
        val doc = PdfDocument()
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(30, 41, 59) }
        val negrito = Paint(texto).apply { isFakeBoldText = true }
        val cinza = Paint(texto).apply { color = Color.rgb(100, 116, 139); textSize = 8.5f }
        val titulo = Paint(negrito).apply { textSize = 18f }
        val direita = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val direitaNeg = Paint(negrito).apply { textAlign = Paint.Align.RIGHT }
        val linha = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 0.6f }

        var numero = 0
        lateinit var pagina: PdfDocument.Page
        var y = 0f
        fun novaPagina(): Canvas {
            if (numero > 0) doc.finishPage(pagina)
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARG, ALT, numero).create())
            y = MARGEM + 12
            return pagina.canvas
        }
        var c = novaPagina()

        // Cabeçalho
        c.drawText(desc.titulo, MARGEM, y, titulo); y += 18
        c.drawText(desc.periodo, MARGEM, y, negrito); y += 13
        desc.filtros.forEach { c.drawText(it, MARGEM, y, cinza); y += 11 }
        c.drawText("Gerado em ${dataBr.format(Date())} · Meu Dinheiro", MARGEM, y, cinza); y += 18

        // Totais
        val rotuloTotal = when (r.filtro.tipo) {
            TipoRelatorio.DESPESA -> "Total de despesas"
            TipoRelatorio.RECEITA -> "Total de receitas"
            TipoRelatorio.TODOS -> "Resultado (receitas − despesas)"
        }
        val caixas = listOf(
            rotuloTotal to moeda.format(r.total),
            "Lançamentos" to r.quantidade.toString(),
            "Média" to moeda.format(r.media),
            "Maior" to moeda.format(r.maior)
        )
        val largCaixa = (LARG - 2 * MARGEM - 3 * 8) / 4
        caixas.forEachIndexed { i, (rot, valor) ->
            val x = MARGEM + i * (largCaixa + 8)
            c.drawRoundRect(RectF(x, y, x + largCaixa, y + 36), 6f, 6f, Paint().apply { color = Color.rgb(241, 245, 249) })
            c.drawText(rot, x + 6, y + 13, cinza)
            c.drawText(valor, x + 6, y + 28, negrito)
        }
        y += 48
        r.variacaoPercentual?.let {
            val seta = if (it >= 0) "▲" else "▼"
            c.drawText("$seta %.1f%% em relação ao período anterior (%s)".format(Locale("pt", "BR"), Math.abs(it), moeda.format(r.anterior.total)), MARGEM, y, texto)
            y += 16
        }

        // Gráfico: barras por categoria
        if (r.porCategoria.isNotEmpty()) {
            c.drawText("Por categoria", MARGEM, y, negrito); y += 12
            y = barras(c, y, r.porCategoria.take(8).map { it.nome to it.total }, r.porCategoria.firstOrNull()?.total ?: 0.0, texto, direita)
        }
        if (r.porMes.size > 1) {
            if (y > ALT - 150) { c = novaPagina() }
            c.drawText("Por mês", MARGEM, y, negrito); y += 12
            y = barras(c, y, r.porMes.map { it.mes to it.total }, r.porMes.maxOf { Math.abs(it.total) }, texto, direita)
        }

        // Tabela
        fun cabecalhoTabela(canvas: Canvas) {
            canvas.drawText("Data", MARGEM, y, negrito); canvas.drawText("Descrição", MARGEM + 58, y, negrito)
            canvas.drawText("Categoria", MARGEM + 270, y, negrito); canvas.drawText("Conta", MARGEM + 370, y, negrito)
            canvas.drawText("Valor", LARG - MARGEM, y, direitaNeg)
            canvas.drawLine(MARGEM, y + 4, LARG - MARGEM, y + 4, linha); y += 15
        }
        if (y > ALT - 120) c = novaPagina()
        y += 6
        cabecalhoTabela(c)
        r.itens.forEach { d ->
            if (y > ALT - MARGEM - 24) { c = novaPagina(); cabecalhoTabela(c) }
            val entrada = d.tipo == TipoDespesa.CREDITO
            c.drawText(dataBr.format(d.data), MARGEM, y, texto)
            c.drawText(d.descricao.take(38), MARGEM + 58, y, texto)
            c.drawText(d.categoria.take(18), MARGEM + 270, y, texto)
            c.drawText(nomeConta(d.conta).take(14), MARGEM + 370, y, texto)
            c.drawText((if (entrada) "+ " else "- ") + moeda.format(d.valor), LARG - MARGEM, y, direita)
            y += 12.5f
        }
        if (r.itens.isEmpty()) c.drawText("Nenhum lançamento para os filtros escolhidos.", MARGEM, y, cinza)
        doc.finishPage(pagina)

        val arq = File(pasta(context), nomeArquivo(desc.titulo, "pdf"))
        try {
            FileOutputStream(arq).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return arq
    }

    private fun barras(c: Canvas, yInicial: Float, itens: List<Pair<String, Double>>, maximo: Double, texto: Paint, direita: Paint): Float {
        var y = yInicial
        val larguraMax = LARG - 2 * MARGEM - 230f
        itens.forEachIndexed { i, (nome, valor) ->
            c.drawText(nome.take(24), MARGEM, y + 8, texto)
            val w = if (maximo > 0) (Math.abs(valor) / maximo * larguraMax).toFloat().coerceAtLeast(1f) else 1f
            c.drawRoundRect(RectF(MARGEM + 130, y, MARGEM + 130 + w, y + 10), 3f, 3f, Paint().apply { color = CORES[i % CORES.size] })
            c.drawText(moeda.format(valor), LARG - MARGEM, y + 8, direita)
            y += 14
        }
        return y + 8
    }

    // ------------------------------------------------------------------ PNG

    /** Imagem-resumo (1080 px de largura): título, filtros, totais e barras por categoria — ideal para compartilhar. */
    fun png(context: Context, r: ResultadoRelatorio, desc: DescricaoRelatorio): File {
        val largura = 1080
        val categorias = r.porCategoria.take(8)
        val altura = 560 + categorias.size * 64 + (if (r.filtro.tipo == TipoRelatorio.TODOS) 0 else 0) + 90
        val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(15, 23, 42))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun txt(s: String, x: Float, y: Float, tam: Float, cor: Int, negrito: Boolean = false, dir: Boolean = false) {
            p.textSize = tam; p.color = cor; p.isFakeBoldText = negrito
            p.textAlign = if (dir) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(s, x, y, p)
        }
        txt(desc.titulo, 56f, 110f, 54f, Color.WHITE, true)
        txt(desc.periodo, 56f, 168f, 34f, Color.rgb(0, 229, 255), true)
        var y = 214f
        desc.filtros.take(3).forEach { txt(it, 56f, y, 28f, Color.rgb(148, 163, 184)); y += 38f }

        y = maxOf(y + 20f, 330f)
        val rotulo = when (r.filtro.tipo) {
            TipoRelatorio.DESPESA -> "TOTAL DE DESPESAS"
            TipoRelatorio.RECEITA -> "TOTAL DE RECEITAS"
            TipoRelatorio.TODOS -> "RESULTADO DO PERÍODO"
        }
        txt(rotulo, 56f, y, 28f, Color.rgb(148, 163, 184), true)
        y += 78f
        val corTotal = if (r.filtro.tipo == TipoRelatorio.TODOS && r.total < 0) Color.rgb(255, 138, 128) else Color.rgb(105, 240, 174)
        txt(moeda.format(r.total), 56f, y, 84f, corTotal, true)
        y += 56f
        val sub = buildString {
            append("${r.quantidade} lançamentos · média ${moeda.format(r.media)}")
            r.variacaoPercentual?.let { append(" · %s%.1f%% vs. anterior".format(Locale("pt", "BR"), if (it >= 0) "▲ " else "▼ ", Math.abs(it))) }
        }
        txt(sub, 56f, y, 28f, Color.rgb(203, 213, 225))
        y += 70f

        val maximo = categorias.firstOrNull()?.total ?: 0.0
        categorias.forEachIndexed { i, cat ->
            txt(cat.nome.take(22), 56f, y + 28f, 30f, Color.WHITE)
            val w = if (maximo > 0) (cat.total / maximo * 430).toFloat().coerceAtLeast(6f) else 6f
            p.color = CORES[i % CORES.size]
            c.drawRoundRect(RectF(350f, y + 6f, 350f + w, y + 38f), 10f, 10f, p)
            txt(moeda.format(cat.total), largura - 56f, y + 30f, 28f, Color.rgb(226, 232, 240), dir = true)
            y += 64f
        }
        txt("Gerado em ${dataBr.format(Date())} · Meu Dinheiro", 56f, altura - 40f, 24f, Color.rgb(100, 116, 139))

        val arq = File(pasta(context), nomeArquivo(desc.titulo, "png"))
        FileOutputStream(arq).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return arq
    }

    // ------------------------------------------------------------------ compartilhar

    fun compartilhar(context: Context, arquivo: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

}
