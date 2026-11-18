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
import com.meudinheiro.domain.AgruparPor
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.RelatorioDetalhe
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
    private const val LIMITE_Y = ALT - MARGEM - 16f
    private val dataHoraBr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    /** Controla páginas e rodapé "Página X de Y". Com [doc] nulo só mede (passada 1, para saber o total de páginas). */
    private class Paginador(private val doc: PdfDocument?, private val total: Int, private val emitidoEm: String) {
        var numero = 0
        var y = 0f
        private var pagina: PdfDocument.Page? = null
        private val rascunho = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        var c: Canvas = rascunho
        private val rodape = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(100, 116, 139) }

        fun nova() {
            fechar()
            numero++
            if (doc != null) {
                pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARG, ALT, numero).create())
                c = pagina!!.canvas
            } else c = rascunho
            y = MARGEM + 12
        }

        fun fechar() {
            if (numero == 0) return
            c.drawLine(MARGEM, ALT - MARGEM + 2, LARG - MARGEM, ALT - MARGEM + 2, Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 0.6f })
            c.drawText("Meu Dinheiro · emitido em $emitidoEm", MARGEM, ALT - MARGEM + 14, rodape)
            c.drawText("Página $numero de $total", LARG - MARGEM, ALT - MARGEM + 14, Paint(rodape).apply { textAlign = Paint.Align.RIGHT })
            pagina?.let { doc?.finishPage(it) }
            pagina = null
        }

        fun cabe(altura: Float) = y + altura <= LIMITE_Y
    }

    fun pdf(
        context: Context,
        r: ResultadoRelatorio,
        desc: DescricaoRelatorio,
        detalhar: Boolean = true,
        nomeCartao: (Int) -> String? = { null },
        agruparPor: AgruparPor = AgruparPor.CATEGORIA,
        nomeConta: (String) -> String
    ): File {
        val emitidoEm = dataHoraBr.format(Date())
        // Passada 1: conta as páginas. Passada 2: desenha com "Página X de Y" correto.
        val medidor = Paginador(null, 0, emitidoEm)
        montarPdf(medidor, r, desc, detalhar, nomeCartao, agruparPor, nomeConta)
        medidor.fechar()
        val total = medidor.numero

        val doc = PdfDocument()
        val arq = File(pasta(context), nomeArquivo(desc.titulo, "pdf"))
        try {
            val p = Paginador(doc, total, emitidoEm)
            montarPdf(p, r, desc, detalhar, nomeCartao, agruparPor, nomeConta)
            p.fechar()
            FileOutputStream(arq).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return arq
    }

    private fun montarPdf(
        p: Paginador, r: ResultadoRelatorio, desc: DescricaoRelatorio, detalhar: Boolean,
        nomeCartao: (Int) -> String?, agruparPor: AgruparPor, nomeConta: (String) -> String
    ) {
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(30, 41, 59) }
        val negrito = Paint(texto).apply { isFakeBoldText = true }
        val cinza = Paint(texto).apply { color = Color.rgb(100, 116, 139); textSize = 8.5f }
        val titulo = Paint(negrito).apply { textSize = 18f }
        val direita = Paint(texto).apply { textAlign = Paint.Align.RIGHT }
        val direitaNeg = Paint(negrito).apply { textAlign = Paint.Align.RIGHT }
        val linha = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 0.6f }
        val fundoGrupo = Paint().apply { color = Color.rgb(241, 245, 249) }

        p.nova()
        // Cabeçalho
        p.c.drawText(desc.titulo, MARGEM, p.y, titulo); p.y += 18
        p.c.drawText(desc.periodo, MARGEM, p.y, negrito); p.y += 13
        desc.filtros.forEach { p.c.drawText(it, MARGEM, p.y, cinza); p.y += 11 }
        p.c.drawText("Gerado em ${dataBr.format(Date())} · Meu Dinheiro", MARGEM, p.y, cinza); p.y += 18

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
            p.c.drawRoundRect(RectF(x, p.y, x + largCaixa, p.y + 36), 6f, 6f, Paint().apply { color = Color.rgb(241, 245, 249) })
            p.c.drawText(rot, x + 6, p.y + 13, cinza)
            p.c.drawText(valor, x + 6, p.y + 28, negrito)
        }
        p.y += 48
        r.variacaoPercentual?.let {
            val seta = if (it >= 0) "▲" else "▼"
            p.c.drawText("$seta %.1f%% em relação ao período anterior (%s)".format(Locale("pt", "BR"), Math.abs(it), moeda.format(r.anterior.total)), MARGEM, p.y, texto)
            p.y += 16
        }

        // Gráfico: barras por categoria
        if (r.porCategoria.isNotEmpty()) {
            p.c.drawText("Por categoria", MARGEM, p.y, negrito); p.y += 12
            p.y = barras(p.c, p.y, r.porCategoria.take(8).map { it.nome to it.total }, r.porCategoria.firstOrNull()?.total ?: 0.0, texto, direita)
        }
        if (r.porMes.size > 1) {
            if (!p.cabe(150f)) p.nova()
            p.c.drawText("Por mês", MARGEM, p.y, negrito); p.y += 12
            p.y = barras(p.c, p.y, r.porMes.map { it.mes to it.total }, r.porMes.maxOf { Math.abs(it.total) }, texto, direita)
        }

        if (!p.cabe(120f)) p.nova()
        p.y += 6

        if (detalhar && r.itens.isNotEmpty()) {
            // ---- R48: detalhamento (itens que compõem cada total), subtotais em centavos
            val origem: (com.meudinheiro.data.Despesa) -> String = { d ->
                d.cartaoId?.let { id -> nomeCartao(id)?.let { "Cartão $it" } } ?: nomeConta(d.conta)
            }
            val det = RelatorioDetalhe.detalhar(r, agruparPor, origem)
            val xData = MARGEM; val xDesc = MARGEM + 54; val xParc = MARGEM + 262; val xOrig = MARGEM + 292
            val xSit = MARGEM + 410; val xVal = LARG - MARGEM
            fun cabecalho() {
                p.c.drawText("Data", xData, p.y, negrito); p.c.drawText("Descrição", xDesc, p.y, negrito)
                p.c.drawText("Parc.", xParc, p.y, negrito); p.c.drawText("Conta/Cartão", xOrig, p.y, negrito)
                p.c.drawText("Situação", xSit, p.y, negrito); p.c.drawText("Valor", xVal, p.y, direitaNeg)
                p.c.drawLine(MARGEM, p.y + 4, LARG - MARGEM, p.y + 4, linha); p.y += 15
            }
            fun faixa(txt: String) {
                p.c.drawRect(MARGEM, p.y - 10, LARG - MARGEM, p.y + 4, fundoGrupo)
                p.c.drawText(txt, MARGEM + 3, p.y, negrito); p.y += 15
            }
            fun linhaTotal(rotulo: String, centavos: Long) {
                p.c.drawLine(MARGEM, p.y - 9, LARG - MARGEM, p.y - 9, linha)
                p.c.drawText(rotulo, MARGEM + 3, p.y + 2, negrito)
                p.c.drawText(moeda.format(Dinheiro.reais(centavos)), xVal, p.y + 2, direitaNeg); p.y += 18
            }
            p.c.drawText("Detalhamento dos lançamentos", MARGEM, p.y, negrito); p.y += 16
            cabecalho()
            det.secoes.forEach { sec ->
                if (sec.titulo != null) {
                    if (!p.cabe(60f)) { p.nova(); cabecalho() }
                    p.c.drawText(sec.titulo.uppercase(Locale("pt", "BR")), MARGEM, p.y + 2, Paint(cinza).apply { isFakeBoldText = true; textSize = 9f }); p.y += 16
                }
                sec.grupos.forEach { g ->
                    val nomeGrupo = if (agruparPor == AgruparPor.MES) g.titulo.split('-').let { if (it.size == 2) "${it[1]}/${it[0]}" else g.titulo } else g.titulo
                    if (!p.cabe(15f + 13f * 2)) { p.nova(); cabecalho() }
                    faixa("$nomeGrupo (${g.linhas.size})")
                    g.linhas.forEach { l ->
                        val linhasDesc = quebrarTexto(l.descricao, texto, xParc - xDesc - 6f).let {
                            if (it.size > 2) listOf(it[0], it[1].dropLast(1).trimEnd() + "…") else it
                        }
                        val h = linhasDesc.size * 11.5f + 1.5f
                        if (!p.cabe(h + 4f)) { p.nova(); cabecalho(); faixa("$nomeGrupo (continuação)") }
                        p.c.drawText(dataBr.format(l.data), xData, p.y, texto)
                        linhasDesc.forEachIndexed { i, t -> p.c.drawText(t, xDesc, p.y + i * 11.5f, texto) }
                        p.c.drawText(l.parcela, xParc, p.y, texto)
                        p.c.drawText(ellipsize(l.origem, texto, xSit - xOrig - 6f), xOrig, p.y, texto)
                        p.c.drawText(l.situacao, xSit, p.y, texto)
                        p.c.drawText(moeda.format(Dinheiro.reais(l.centavos)), xVal, p.y, direita)
                        p.y += h
                    }
                    if (!p.cabe(22f)) { p.nova(); cabecalho() }
                    linhaTotal("Subtotal · $nomeGrupo", g.subtotalCentavos)
                }
                if (sec.titulo != null) {
                    if (!p.cabe(22f)) { p.nova(); cabecalho() }
                    linhaTotal("Total de ${sec.titulo.lowercase(Locale("pt", "BR"))}", sec.totalCentavos)
                }
            }
            if (!p.cabe(26f)) { p.nova(); cabecalho() }
            p.y += 4
            linhaTotal(if (r.filtro.tipo == TipoRelatorio.TODOS) "Resultado (receitas − despesas)" else "Total", det.totalCentavos)
        } else {
            // Tabela simples (sem detalhamento)
            fun cabecalhoTabela() {
                p.c.drawText("Data", MARGEM, p.y, negrito); p.c.drawText("Descrição", MARGEM + 58, p.y, negrito)
                p.c.drawText("Categoria", MARGEM + 270, p.y, negrito); p.c.drawText("Conta", MARGEM + 370, p.y, negrito)
                p.c.drawText("Valor", LARG - MARGEM, p.y, direitaNeg)
                p.c.drawLine(MARGEM, p.y + 4, LARG - MARGEM, p.y + 4, linha); p.y += 15
            }
            cabecalhoTabela()
            r.itens.forEach { d ->
                if (!p.cabe(14f)) { p.nova(); cabecalhoTabela() }
                val entrada = d.tipo == TipoDespesa.CREDITO
                p.c.drawText(dataBr.format(d.data), MARGEM, p.y, texto)
                p.c.drawText(d.descricao.take(38), MARGEM + 58, p.y, texto)
                p.c.drawText(d.categoria.take(18), MARGEM + 270, p.y, texto)
                p.c.drawText(nomeConta(d.conta).take(14), MARGEM + 370, p.y, texto)
                p.c.drawText((if (entrada) "+ " else "- ") + moeda.format(d.valor), LARG - MARGEM, p.y, direita)
                p.y += 12.5f
            }
            if (r.itens.isEmpty()) p.c.drawText("Nenhum lançamento para os filtros escolhidos.", MARGEM, p.y, cinza)
        }
    }

    private fun ellipsize(s: String, paint: Paint, largura: Float): String {
        if (paint.measureText(s) <= largura) return s
        val n = paint.breakText(s, true, largura - paint.measureText("…"), null).coerceAtLeast(0)
        return s.substring(0, n).trimEnd() + "…"
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

    /**
     * Imagem-resumo (1080 px de largura): título, filtros, totais, barras por categoria e — com [detalhar] — os
     * [maxPorGrupo] maiores itens de cada grupo + "… e mais K lançamentos" (o PDF e o CSV levam tudo).
     */
    fun png(
        context: Context,
        r: ResultadoRelatorio,
        desc: DescricaoRelatorio,
        detalhar: Boolean = true,
        nomeCartao: (Int) -> String? = { null },
        agruparPor: AgruparPor = AgruparPor.CATEGORIA,
        nomeConta: (String) -> String = { it },
        maxPorGrupo: Int = 5
    ): File {
        val largura = 1080
        val categorias = r.porCategoria.take(8)

        // Detalhamento enxuto: secao -> (grupos resumidos, grupos omitidos)
        val origem: (com.meudinheiro.data.Despesa) -> String = { d ->
            d.cartaoId?.let { id -> nomeCartao(id)?.let { "Cartão $it" } } ?: nomeConta(d.conta)
        }
        val secoes = if (detalhar && r.itens.isNotEmpty()) {
            RelatorioDetalhe.detalhar(r, agruparPor, origem).secoes.map { it to RelatorioDetalhe.resumirParaImagem(it, maxPorGrupo) }
        } else emptyList()
        var alturaDet = 0
        if (secoes.isNotEmpty()) {
            alturaDet += 90
            secoes.forEach { (sec, res) ->
                if (sec.titulo != null) alturaDet += 56
                res.first.forEach { g -> alturaDet += 58 + g.linhas.size * 42 + (if (g.restantes > 0) 40 else 0) + 14 }
                if (res.second > 0) alturaDet += 44
            }
        }
        val altura = 560 + categorias.size * 64 + 90 + alturaDet
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

        // R48 — detalhamento resumido
        if (secoes.isNotEmpty()) {
            val fraco = Color.rgb(148, 163, 184)
            val claro = Color.rgb(226, 232, 240)
            y += 20f
            txt("Detalhamento", 56f, y, 36f, Color.WHITE, true)
            y += 54f
            secoes.forEach { (sec, res) ->
                if (sec.titulo != null) { txt(sec.titulo.uppercase(Locale("pt", "BR")), 56f, y, 26f, Color.rgb(0, 229, 255), true); y += 56f }
                res.first.forEach { g ->
                    txt(g.titulo.take(30), 56f, y, 30f, Color.WHITE, true)
                    txt(moeda.format(Dinheiro.reais(g.subtotalCentavos)), largura - 56f, y, 30f, Color.WHITE, true, true)
                    y += 16f
                    p.color = Color.rgb(51, 65, 85); c.drawRect(56f, y, largura - 56f, y + 2f, p)
                    y += 40f
                    g.linhas.forEach { l ->
                        txt(dataBr.format(l.data).take(5), 56f, y, 26f, fraco)
                        val d = l.descricao + (if (l.parcela.isNotEmpty()) " (${l.parcela})" else "")
                        txt(if (d.length > 38) d.take(37) + "…" else d, 150f, y, 26f, claro)
                        txt(moeda.format(Dinheiro.reais(l.centavos)), largura - 56f, y, 26f, claro, dir = true)
                        y += 42f
                    }
                    if (g.restantes > 0) { txt("… e mais ${g.restantes} lançamento${if (g.restantes > 1) "s" else ""}", 150f, y, 24f, fraco); y += 40f }
                    y += 14f
                }
                if (res.second > 0) { txt("… e mais ${res.second} grupo${if (res.second > 1) "s" else ""} (veja o PDF)", 56f, y, 24f, fraco); y += 44f }
            }
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
