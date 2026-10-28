package com.meudinheiro.funcoes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.content.FileProvider
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.RelatorioPessoas
import com.meudinheiro.funcoes.LayoutPessoas.CW
import com.meudinheiro.funcoes.LayoutPessoas.H_RODAPE
import com.meudinheiro.funcoes.LayoutPessoas.LARGURA
import com.meudinheiro.funcoes.LayoutPessoas.M
import java.io.File
import java.io.FileOutputStream

/**
 * R69 — exporta o [RelatorioPessoas.Relatorio] em PDF (A4, várias páginas), PNG ou JPEG e compartilha. Todo o desenho usa o plano
 * puro de [LayoutPessoas] (mesmo do portal) com cores de significado + ícones `Icons.Rounded` (vetores desenhados no Canvas):
 * verde = a receber / te deve, laranja = a pagar / você deve, azul-petróleo da marca nos cabeçalhos, cinza = quitado, âmbar = pendente.
 * Cor nunca é a única informação: sempre há texto e ícone. O arquivo leva os valores REAIS (o modo privado vale só para a tela).
 *
 * Imagens longas: no máximo 6000 px por arquivo; acima disso o relatório sai em partes numeradas (`pessoas_AAAA-MM-DD-1.png`…),
 * compartilhadas juntas (ACTION_SEND_MULTIPLE). O PDF usa o mesmo plano com páginas A4.
 */
object RelatorioPessoasExport {

    enum class Formato(val ext: String, val mime: String) { PDF("pdf", "application/pdf"), PNG("png", "image/png"), JPEG("jpg", "image/jpeg") }

    private const val BRANCO = Color.WHITE
    private val MARCA = Color.parseColor("#0F766E")
    private val MARCA_ESCURA = Color.parseColor("#115E59")
    private val TINTA = Color.parseColor("#0F172A")
    private val CINZA = Color.parseColor("#64748B")
    private val CINZA_CLARO = Color.parseColor("#F1F5F9")
    private val DIVISOR = Color.parseColor("#E2E8F0")
    private val VERDE = Color.parseColor("#15803D")
    private val VERDE_FUNDO = Color.parseColor("#DCFCE7")
    private val LARANJA = Color.parseColor("#C2410C")
    private val LARANJA_FUNDO = Color.parseColor("#FFEDD5")
    private val AMBAR = Color.parseColor("#B45309")
    private val AMBAR_FUNDO = Color.parseColor("#FEF3C7")
    private val QUITADO = Color.parseColor("#4B6B62")
    private val QUITADO_FUNDO = Color.parseColor("#E3ECE9")
    private val TEAL_CLARO = Color.parseColor("#E6F4F1")

    /** Mesma paleta do portal (índice vem do modelo). */
    private val PALETA = intArrayOf(
        Color.parseColor("#0F766E"), Color.parseColor("#0E7490"), Color.parseColor("#4F46E5"), Color.parseColor("#B45309"),
        Color.parseColor("#BE185D"), Color.parseColor("#4D7C0F"), Color.parseColor("#7C3AED"), Color.parseColor("#475569")
    )

    fun corAvatar(indice: Int) = PALETA[((indice % PALETA.size) + PALETA.size) % PALETA.size]

    private fun pasta(context: Context) = File(context.cacheDir, "extratos").apply { mkdirs() }

    // ------------------------------------------------------------------ ícones (ImageVector -> android Path)

    private val cachePaths = HashMap<ImageVector, List<Path>>()

    private fun paths(v: ImageVector): List<Path> = cachePaths.getOrPut(v) {
        val saida = ArrayList<Path>()
        fun anda(g: VectorGroup) {
            for (n in g) when (n) {
                is VectorPath -> saida += n.pathData.toPath().asAndroidPath().apply {
                    fillType = if (n.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                }
                is VectorGroup -> anda(n)
                else -> Unit
            }
        }
        anda(v.root)
        saida
    }

    private object Ic {
        val entrada get() = Icons.Rounded.CallReceived
        val saida get() = Icons.Rounded.CallMade
        val relogio get() = Icons.Rounded.Schedule
        val check get() = Icons.Rounded.CheckCircle
        val recibo get() = Icons.Rounded.Receipt
        val carteira get() = Icons.Rounded.AccountBalanceWallet
        val balanca get() = Icons.Rounded.Balance
    }

    // ------------------------------------------------------------------ pincéis e medição

    private fun tf(peso: Int) = Typeface.create(Typeface.DEFAULT, if (peso >= 600) Typeface.BOLD else Typeface.NORMAL)

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Medidor real (Paint) para o plano de layout. */
    fun medidorAndroid() = MedidorTexto { texto, peso, tam ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = tam; typeface = tf(peso) }
        p.measureText(texto)
    }

    private fun texto(c: Canvas, t: String, x: Float, y: Float, peso: Int, tam: Float, cor: Int, alinha: Paint.Align = Paint.Align.LEFT) {
        pincel.reset(); pincel.isAntiAlias = true
        pincel.textSize = tam; pincel.typeface = tf(peso); pincel.color = cor; pincel.textAlign = alinha
        c.drawText(t, x, y, pincel)
    }

    private fun larg(t: String, peso: Int, tam: Float): Float {
        pincel.reset(); pincel.isAntiAlias = true
        pincel.textSize = tam; pincel.typeface = tf(peso)
        return pincel.measureText(t)
    }

    private fun preenche(c: Canvas, cor: Int, f: (Paint) -> Unit = {}, desenha: (Paint) -> Unit) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.style = Paint.Style.FILL
        f(pincel)
        desenha(pincel)
    }

    private fun retanguloArredondado(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, cor: Int) =
        preenche(c, cor) { c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, it) }

    private fun icone(c: Canvas, v: ImageVector, x: Float, y: Float, tam: Float, cor: Int) {
        c.save()
        c.translate(x, y)
        c.scale(tam / v.viewportWidth, tam / v.viewportHeight)
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.style = Paint.Style.FILL
        for (p in paths(v)) c.drawPath(p, pincel)
        c.restore()
    }

    private fun avatar(c: Canvas, cx: Float, cy: Float, r: Float, iniciais: String, cor: Int) {
        preenche(c, cor) { c.drawCircle(cx, cy, r, it) }
        texto(c, iniciais, cx, cy + r * 0.3f, 700, r * 0.85f, BRANCO, Paint.Align.CENTER)
    }

    private fun pilula(c: Canvas, t: String, xDireita: Float, y: Float, fundo: Int, cor: Int, ic: ImageVector) {
        val w = larg(t, 700, 17f) + 52f
        retanguloArredondado(c, xDireita - w, y, w, 28f, 14f, fundo)
        icone(c, ic, xDireita - w + 10f, y + 5f, 18f, cor)
        texto(c, t, xDireita - w + 34f, y + 20f, 700, 17f, cor)
    }

    /** Barra de acento à esquerda, recortada pelo canto arredondado do cartão. */
    private fun cartaoComAcento(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, acento: Int, larguraAcento: Float) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) })
        pincel.reset(); pincel.color = CINZA_CLARO
        c.drawRect(x, y, x + w, y + h, pincel)
        pincel.color = acento
        c.drawRect(x, y, x + larguraAcento, y + h, pincel)
        c.restore()
    }

    // ------------------------------------------------------------------ desenho do plano

    private fun corSaldo(s: RelatorioPessoas.Sentido) = when (s) {
        RelatorioPessoas.Sentido.TE_DEVE -> VERDE
        RelatorioPessoas.Sentido.VOCE_DEVE -> LARANJA
        RelatorioPessoas.Sentido.QUITES -> CINZA
    }

    private fun desenharPessoa(c: Canvas, p: RelatorioPessoas.PessoaBloco, y0: Float) {
        val y = y0 + 20f
        val h = 136f
        val cor = corAvatar(p.corIndice)
        cartaoComAcento(c, M, y, CW, h, 18f, cor, 8f)
        avatar(c, M + 64f, y + 50f, 32f, p.iniciais, cor)
        var tam = 32f
        val maxNome = CW - 116f - 20f - 380f
        while (tam > 20f && larg(p.nome, 700, tam) > maxNome) tam -= 2f
        texto(c, p.nome, M + 112f, y + 50f, 700, tam, TINTA)
        p.contato?.let {
            var t2 = 19f
            while (t2 > 14f && larg(it, 400, t2) > maxNome) t2 -= 1f
            texto(c, it, M + 112f, y + 80f, 400, t2, CINZA)
        }
        val cs = corSaldo(p.sentido)
        val direita = M + CW - 24f
        var ts = 30f
        while (ts > 18f && larg(p.saldoTexto, 700, ts) > 340f) ts -= 2f
        val wSaldo = larg(p.saldoTexto, 700, ts)
        texto(c, p.saldoTexto, direita, y + 46f, 700, ts, cs, Paint.Align.RIGHT)
        icone(c, if (p.sentido == RelatorioPessoas.Sentido.QUITES) Ic.check else Ic.balanca, direita - wSaldo - 40f, y + 20f, 30f, cs)
        texto(c, "saldo pendente (a receber − a pagar)", direita, y + 74f, 400, 16f, CINZA, Paint.Align.RIGHT)
        val itens = listOf(
            Triple(Ic.entrada, "A receber ${FormatoCentavos.brl(p.aReceber)}", VERDE),
            Triple(Ic.saida, "A pagar ${FormatoCentavos.brl(p.aPagar)}", LARANJA),
            Triple(Ic.check, "Quitado ${FormatoCentavos.brl(p.quitadoAReceber + p.quitadoAPagar)}", QUITADO)
        )
        var x = M + 28f
        for ((ic, t, cc) in itens) {
            icone(c, ic, x, y + h - 42f, 22f, cc)
            texto(c, t, x + 30f, y + h - 24f, 600, 19f, cc)
            x += 30f + larg(t, 600, 19f) + 36f
        }
    }

    private fun desenharLinha(c: Canvas, b: LayoutPessoas.Bloco.Linha, y0: Float) {
        val l = b.linha
        val recebe = l.tipo == com.meudinheiro.domain.Rateios.A_RECEBER
        val cor = if (recebe) VERDE else LARANJA
        val fundo = if (recebe) VERDE_FUNDO else LARANJA_FUNDO
        val textoX = M + 72f
        preenche(c, fundo) { c.drawCircle(M + 26f, y0 + 38f, 22f, it) }
        icone(c, if (recebe) Ic.entrada else Ic.saida, M + 15f, y0 + 27f, 22f, cor)

        var ty = y0 + 12f
        if (b.mostrarPessoa) {
            val cp = corAvatar(b.corIndice)
            avatar(c, textoX + 14f, ty + 15f, 14f, b.iniciais, cp)
            texto(c, l.pessoaNome, textoX + 38f, ty + 21f, 600, 20f, cp)
            ty += 34f
        }
        for (t in b.desc) { texto(c, t, textoX, ty + 22f, 600, 22f, TINTA); ty += 28f }
        for (t in b.origem) { texto(c, t, textoX, ty + 18f, 400, 18f, CINZA); ty += 24f }
        for (t in b.rateio) {
            icone(c, Ic.recibo, textoX, ty + 3f, 18f, MARCA)
            texto(c, t, textoX + 26f, ty + 18f, 500, 17f, MARCA)
            ty += 24f
        }
        val dir = M + CW
        texto(c, FormatoCentavos.brl(l.centavos), dir, y0 + 38f, 700, 26f, cor, Paint.Align.RIGHT)
        texto(c, l.tipoTexto, dir, y0 + 62f, 600, 17f, cor, Paint.Align.RIGHT)
        if (l.status == RelatorioPessoas.Status.PENDENTE) pilula(c, l.statusTexto, dir, y0 + 72f, AMBAR_FUNDO, AMBAR, Ic.relogio)
        else pilula(c, l.statusTexto, dir, y0 + 72f, QUITADO_FUNDO, QUITADO, Ic.check)
        preenche(c, DIVISOR) { c.drawRect(M, y0 + b.h - 2f, M + CW, y0 + b.h, it) }
    }

    private fun desenharBloco(c: Canvas, b: LayoutPessoas.Bloco, y: Float) {
        when (b) {
            is LayoutPessoas.Bloco.Titulo -> {
                preenche(c, MARCA) { c.drawRect(M, y + 34f, M + 6f, y + 58f, it) }
                texto(c, b.texto, M + 18f, y + 54f, 700, 26f, MARCA_ESCURA)
            }
            is LayoutPessoas.Bloco.CartaoPessoa -> desenharPessoa(c, b.pessoa, y)
            is LayoutPessoas.Bloco.Cont -> {
                val cor = corAvatar(b.corIndice)
                avatar(c, M + 22f, y + 30f, 16f, b.iniciais, cor)
                texto(c, "${b.nome} (continuação)", M + 52f, y + 38f, 700, 22f, cor)
            }
            is LayoutPessoas.Bloco.Linha -> desenharLinha(c, b, y)
            is LayoutPessoas.Bloco.Rateio -> {
                var ty = y + 20f
                for (t in b.l1) { texto(c, t, M + 8f, ty + 18f, 600, 21f, TINTA); ty += 28f }
                for (t in b.l2) { texto(c, t, M + 8f, ty + 16f, 400, 19f, CINZA); ty += 26f }
                preenche(c, DIVISOR) { c.drawRect(M, y + b.h - 2f, M + CW, y + b.h, it) }
            }
            is LayoutPessoas.Bloco.RateioTotais -> {
                retanguloArredondado(c, M, y + 10f, CW, 92f, 16f, TEAL_CLARO)
                val col = CW / 3f
                listOf(
                    Triple("Total das despesas divididas", b.total, TINTA),
                    Triple("A receber de terceiros", b.terceiros, VERDE),
                    Triple("Você realmente gastou", b.liquido, MARCA_ESCURA)
                ).forEachIndexed { i, (r, v, cc) ->
                    texto(c, r, M + 24f + i * col, y + 44f, 400, 18f, CINZA)
                    texto(c, FormatoCentavos.brl(v), M + 24f + i * col, y + 84f, 700, 28f, cc)
                }
            }
        }
    }

    private fun desenharResumo(c: Canvas, rel: RelatorioPessoas.Relatorio, y0: Float) {
        val r = rel.resumo
        val w = (CW - 40f) / 3f
        val sentido = if (r.saldoLiquido > 0) "a seu favor" else if (r.saldoLiquido < 0) "contra você" else "quites"
        val corSal = if (r.saldoLiquido > 0) VERDE else if (r.saldoLiquido < 0) LARANJA else CINZA
        class Caixa(val ic: ImageVector, val rot: String, val v: String, val sub: String, val cor: Int)
        val caixas = listOf(
            Caixa(Ic.entrada, "A receber (pendente)", FormatoCentavos.brl(r.aReceberPendente), "quem te deve", VERDE),
            Caixa(Ic.saida, "A pagar (pendente)", FormatoCentavos.brl(r.aPagarPendente), "o que você deve", LARANJA),
            Caixa(Ic.balanca, "Saldo líquido", FormatoCentavos.brl(Math.abs(r.saldoLiquido)), sentido, corSal),
            Caixa(Ic.check, "Quitado · a receber", FormatoCentavos.brl(r.quitadoAReceber), "já recebido", QUITADO),
            Caixa(Ic.check, "Quitado · a pagar", FormatoCentavos.brl(r.quitadoAPagar), "já pago", QUITADO),
            Caixa(
                Ic.carteira, "Acertos", "${r.nAcertos}",
                "${r.nPessoas} pessoa${if (r.nPessoas == 1) "" else "s"} · ${r.nPessoasComSaldo} com saldo", MARCA
            )
        )
        caixas.forEachIndexed { i, cx ->
            val x = M + (i % 3) * (w + 20f)
            val y = y0 + (i / 3) * 138f
            cartaoComAcento(c, x, y, w, 118f, 16f, cx.cor, 8f)
            icone(c, cx.ic, x + 24f, y + 16f, 24f, cx.cor)
            texto(c, cx.rot, x + 56f, y + 34f, 600, 17f, CINZA)
            var t = 34f
            while (t > 20f && larg(cx.v, 700, t) > w - 44f) t -= 2f
            texto(c, cx.v, x + 24f, y + 80f, 700, t, cx.cor)
            texto(c, cx.sub, x + 24f, y + 104f, 500, 16f, CINZA)
        }
    }

    private fun desenharCabecalho1(c: Canvas, rel: RelatorioPessoas.Relatorio, plano: LayoutPessoas.Plano) {
        preenche(c, MARCA) { c.drawRect(0f, 0f, LARGURA.toFloat(), 12f, it) }
        var y = 12f + 28f
        retanguloArredondado(c, M, y, 56f, 56f, 14f, MARCA)
        icone(c, Ic.carteira, M + 12f, y + 12f, 32f, BRANCO)
        texto(c, "MeuDinheiro", M + 72f, y + 40f, 800, 38f, MARCA)
        texto(c, "Emitido em ${rel.emitidoEm}", M + CW, y + 38f, 400, 18f, CINZA, Paint.Align.RIGHT)
        y += 64f + 18f + 36f
        texto(c, rel.titulo, M, y, 700, 42f, TINTA)
        y += 34f
        texto(c, "Período: ${rel.periodoTexto}", M, y, 600, 22f, MARCA_ESCURA)
        y += 34f
        for (t in plano.filtrosLinhas) { texto(c, t, M, y, 400, 18f, CINZA); y += 26f }
        y += 8f
        desenharResumo(c, rel, y)
    }

    /** Pinta uma parte do plano numa tela de [LARGURA] x [altura] px lógicos (o chamador aplica a escala). */
    internal fun desenharPagina(c: Canvas, rel: RelatorioPessoas.Relatorio, plano: LayoutPessoas.Plano, p: LayoutPessoas.Pagina, altura: Float, rotuloPagina: String) {
        c.drawColor(BRANCO)
        if (p.indice == 1) desenharCabecalho1(c, rel, plano)
        else {
            preenche(c, MARCA) { c.drawRect(0f, 0f, LARGURA.toFloat(), 12f, it) }
            texto(c, "MeuDinheiro", M, 58f, 800, 24f, MARCA)
            texto(c, "${rel.titulo} · ${rel.periodoTexto} (continuação)", M + 190f, 58f, 400, 20f, CINZA)
        }
        var y = p.hCabecalho
        for (b in p.blocos) { desenharBloco(c, b, y); y += b.h }
        val yr = altura - H_RODAPE
        preenche(c, DIVISOR) { c.drawRect(M, yr + 8f, M + CW, yr + 10f, it) }
        texto(c, "MeuDinheiro · emitido em ${rel.emitidoEm}", M, yr + 42f, 400, 17f, CINZA)
        texto(c, rotuloPagina, M + CW, yr + 42f, 400, 17f, CINZA, Paint.Align.RIGHT)
    }

    /** Bitmap de UMA parte (altura dinâmica = cabeçalho + conteúdo + rodapé). */
    internal fun bitmapDaParte(rel: RelatorioPessoas.Relatorio, plano: LayoutPessoas.Plano, p: LayoutPessoas.Pagina): Bitmap {
        val altura = Math.ceil(p.altura.toDouble()).toInt()
        val bmp = Bitmap.createBitmap(LARGURA, altura, Bitmap.Config.ARGB_8888)
        desenharPagina(Canvas(bmp), rel, plano, p, altura.toFloat(), "Parte ${p.indice} de ${p.total}")
        return bmp
    }

    // ------------------------------------------------------------------ arquivos

    fun planoImagens(rel: RelatorioPessoas.Relatorio) = LayoutPessoas.planejar(rel, medidorAndroid(), LayoutPessoas.MAX_ALTURA_IMAGEM)

    fun planoPdf(rel: RelatorioPessoas.Relatorio) = LayoutPessoas.planejar(rel, medidorAndroid(), LayoutPessoas.ALTURA_PAGINA_PDF)

    fun gerarImagens(context: Context, rel: RelatorioPessoas.Relatorio, formato: Formato, agora: Long): List<File> {
        require(formato != Formato.PDF) { "Use gerarPdf para PDF." }
        val plano = planoImagens(rel)
        val partes = plano.paginas.size
        return plano.paginas.map { p ->
            val arq = File(pasta(context), RelatorioPessoas.nomeArquivo(agora, formato.ext, if (partes > 1) p.indice else null))
            val bmp = bitmapDaParte(rel, plano, p)
            try {
                FileOutputStream(arq).use {
                    // JPEG não tem transparência: o fundo já é branco opaco (drawColor); qualidade 90.
                    if (formato == Formato.PNG) bmp.compress(Bitmap.CompressFormat.PNG, 100, it) else bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
            } finally {
                bmp.recycle()
            }
            arq
        }
    }

    fun gerarPdf(context: Context, rel: RelatorioPessoas.Relatorio, agora: Long): File {
        val plano = planoPdf(rel)
        val doc = PdfDocument()
        val arq = File(pasta(context), RelatorioPessoas.nomeArquivo(agora, "pdf"))
        try {
            val escala = 595f / LARGURA
            for (p in plano.paginas) {
                val pagina = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p.indice).create())
                val c = pagina.canvas
                c.scale(escala, escala)
                desenharPagina(c, rel, plano, p, LayoutPessoas.ALTURA_PAGINA_PDF, "Página ${p.indice} de ${p.total}")
                doc.finishPage(pagina)
            }
            FileOutputStream(arq).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return arq
    }

    fun gerar(context: Context, rel: RelatorioPessoas.Relatorio, formato: Formato, agora: Long): List<File> =
        if (formato == Formato.PDF) listOf(gerarPdf(context, rel, agora)) else gerarImagens(context, rel, formato, agora)

    /** Um arquivo: o mesmo compartilhamento dos demais relatórios; várias partes: ACTION_SEND_MULTIPLE. */
    fun compartilhar(context: Context, arquivos: List<File>, formato: Formato) {
        if (arquivos.size == 1) return RelatorioExport.compartilhar(context, arquivos[0], formato.mime)
        val uris = ArrayList(arquivos.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = formato.mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
