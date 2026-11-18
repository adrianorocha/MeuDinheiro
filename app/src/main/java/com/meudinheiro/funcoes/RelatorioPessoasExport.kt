package com.meudinheiro.funcoes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.meudinheiro.domain.ComposicaoGasto
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.RelatorioPessoas
import com.meudinheiro.funcoes.IconesPessoas.AMBAR
import com.meudinheiro.funcoes.IconesPessoas.AMBAR_FUNDO
import com.meudinheiro.funcoes.IconesPessoas.BRANCO
import com.meudinheiro.funcoes.IconesPessoas.CINZA
import com.meudinheiro.funcoes.IconesPessoas.CINZA_CLARO
import com.meudinheiro.funcoes.IconesPessoas.DIVISOR
import com.meudinheiro.funcoes.IconesPessoas.LARANJA
import com.meudinheiro.funcoes.IconesPessoas.LARANJA_FUNDO
import com.meudinheiro.funcoes.IconesPessoas.MARCA
import com.meudinheiro.funcoes.IconesPessoas.MARCA_ESCURA
import com.meudinheiro.funcoes.IconesPessoas.Nome
import com.meudinheiro.funcoes.IconesPessoas.QUITADO
import com.meudinheiro.funcoes.IconesPessoas.QUITADO_FUNDO
import com.meudinheiro.funcoes.IconesPessoas.TEAL_CLARO
import com.meudinheiro.funcoes.IconesPessoas.TINTA
import com.meudinheiro.funcoes.IconesPessoas.VERDE
import com.meudinheiro.funcoes.LayoutPessoas.CW
import com.meudinheiro.funcoes.LayoutPessoas.H_RODAPE
import com.meudinheiro.funcoes.LayoutPessoas.LARGURA
import com.meudinheiro.funcoes.LayoutPessoas.M
import java.io.File
import java.io.FileOutputStream

/**
 * R69 — exporta o [RelatorioPessoas.Relatorio] em PDF (A4, várias páginas), PNG ou JPEG e compartilha. PNG/JPEG usam o plano
 * puro de [LayoutPessoas]; o PDF usa [RelatorioPessoasPdf]. Ambos reproduzem o desenho do portal (`lib/exportar/pessoas*.ts`):
 * mesmos ícones de traço ([IconesPessoas]), mesma paleta, cartão da pessoa, faixa de vencimento da fatura e mini gráfico de composição.
 * Cor nunca é a única informação: sempre há texto e ícone. O arquivo leva os valores REAIS (o modo privado vale só para a tela).
 *
 * Imagens longas: no máximo 6000 px por arquivo; acima disso o relatório sai em partes numeradas (`pessoas_AAAA-MM-DD-1.png`…),
 * compartilhadas juntas (ACTION_SEND_MULTIPLE).
 */
object RelatorioPessoasExport {

    enum class Formato(val ext: String, val mime: String) { PDF("pdf", "application/pdf"), PNG("png", "image/png"), JPEG("jpg", "image/jpeg") }

    fun corAvatar(indice: Int) = IconesPessoas.corAvatar(indice)

    private fun pasta(context: Context) = File(context.cacheDir, "extratos").apply { mkdirs() }

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

    private fun preenche(cor: Int, desenha: (Paint) -> Unit) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.style = Paint.Style.FILL
        desenha(pincel)
    }

    private fun retanguloArredondado(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, cor: Int) =
        preenche(cor) { c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, it) }

    private fun icone(c: Canvas, nome: Nome, x: Float, y: Float, tam: Float, cor: Int, esp: Float = 2f) =
        IconesPessoas.desenhar(c, nome, x, y, tam, cor, esp)

    private fun avatar(c: Canvas, cx: Float, cy: Float, r: Float, iniciais: String, cor: Int) {
        preenche(cor) { c.drawCircle(cx, cy, r, it) }
        texto(c, iniciais, cx, cy + r * 0.3f, 700, Math.round(r * 0.85f).toFloat(), BRANCO, Paint.Align.CENTER)
    }

    private fun pilula(c: Canvas, t: String, xDireita: Float, y: Float, fundo: Int, cor: Int, nome: Nome) {
        val w = larg(t, 700, 17f) + 52f
        retanguloArredondado(c, xDireita - w, y, w, 28f, 14f, fundo)
        icone(c, nome, xDireita - w + 10f, y + 5f, 18f, cor, 2f)
        texto(c, t, xDireita - w + 34f, y + 20f, 700, 17f, cor)
    }

    /** Faixa de acento à esquerda, recortada pelo canto arredondado. */
    private fun faixaComAcento(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, fundo: Int, acento: Int, larguraAcento: Float) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) })
        pincel.reset(); pincel.color = fundo
        c.drawRect(x, y, x + w, y + h, pincel)
        pincel.color = acento
        c.drawRect(x, y, x + larguraAcento, y + h, pincel)
        c.restore()
    }

    // ------------------------------------------------------------------ desenho do plano (igual a pessoasImagem.ts)

    private fun corSaldo(s: RelatorioPessoas.Sentido) = when (s) {
        RelatorioPessoas.Sentido.TE_DEVE -> VERDE
        RelatorioPessoas.Sentido.VOCE_DEVE -> LARANJA
        RelatorioPessoas.Sentido.QUITES -> CINZA
    }

    /** R96 — barra empilhada fina + legenda "Categoria 42%" com quadradinho de cor (a cor nunca é a única informação). */
    private fun desenharComposicao(c: Canvas, b: LayoutPessoas.Bloco.CartaoPessoa, yTopo: Float) {
        val fatias = b.pessoa.composicao.visiveis
        if (fatias.isEmpty()) return
        texto(c, "Composição do gasto", M, yTopo + 16f, 500, 16f, CINZA)
        val yb = yTopo + 24f
        val total = fatias.sumOf { it.centavos }.toDouble()
        var x = M
        for ((i, f) in fatias.withIndex()) {
            val w = if (i == fatias.lastIndex) M + CW - x else (CW * (f.centavos / total)).toFloat()
            preenche(android.graphics.Color.parseColor(ComposicaoGasto.corHex(i, f))) { c.drawRect(x, yb, x + w, yb + 14f, it) }
            if (i > 0) preenche(BRANCO) { c.drawRect(x - 1f, yb, x + 1f, yb + 14f, it) }
            x += w
        }
        b.legenda.forEachIndexed { r, linha ->
            val yl = yb + 14f + 10f + r * 28f
            var xl = M
            for (f in linha) {
                val rot = b.rotulos.getValue(f)
                preenche(android.graphics.Color.parseColor(ComposicaoGasto.corHex(b.indices.getValue(f), f))) { c.drawRect(xl, yl + 3f, xl + 14f, yl + 17f, it) }
                texto(c, rot, xl + 14f + 8f, yl + 17f, 500, 17f, TINTA)
                xl += 14f + 8f + larg(rot, 500, 17f) + 28f
            }
        }
    }

    private fun desenharPessoa(c: Canvas, b: LayoutPessoas.Bloco.CartaoPessoa, y0: Float) {
        val p = b.pessoa
        val y = y0 + 20f
        val h = 100f
        val cor = corAvatar(p.corIndice)
        faixaComAcento(c, M, y, CW, h, 18f, CINZA_CLARO, cor, 8f)
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
        icone(c, if (p.sentido == RelatorioPessoas.Sentido.QUITES) Nome.CHECK else Nome.BALANCA, direita - wSaldo - 40f, y + 20f, 30f, cs, 2.2f)
        texto(c, "saldo pendente (a receber − a pagar)", direita, y + 74f, 400, 16f, CINZA, Paint.Align.RIGHT)
        val v = p.proximoVencimento
        if (v != null) {
            val yb = y + h + 8f
            val cc = if (v.vencido) LARANJA else AMBAR
            faixaComAcento(c, M, yb, CW, 52f, 14f, if (v.vencido) LARANJA_FUNDO else AMBAR_FUNDO, cc, 8f)
            icone(c, Nome.CALENDARIO, M + 26f, yb + 12f, 28f, cc, 2.2f)
            texto(c, "VENCIMENTO DA FATURA DO CARTÃO: ${RelatorioPessoas.dataBR(v.ms)}${if (v.vencido) " (vencida)" else ""}", M + 66f, yb + 34f, 800, 23f, cc)
            texto(c, "valor ligado à fatura: ${FormatoCentavos.brl(v.centavos)}", M + CW - 24f, yb + 33f, 500, 18f, cc, Paint.Align.RIGHT)
        }
        desenharComposicao(c, b, y0 + 120f + (if (v != null) 64f else 0f) + 4f)
    }

    private fun desenharLinha(c: Canvas, b: LayoutPessoas.Bloco.Linha, y0: Float) {
        val l = b.linha
        val recebe = l.tipo == com.meudinheiro.domain.Rateios.A_RECEBER
        val cor = if (recebe) VERDE else LARANJA
        val textoX = M + 8f

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
            icone(c, Nome.RECIBO, textoX, ty + 3f, 18f, MARCA, 1.8f)
            texto(c, t, textoX + 26f, ty + 18f, 500, 17f, MARCA)
            ty += 24f
        }
        l.vencimento?.let { v ->
            val cc = if (v.vencido) LARANJA else AMBAR
            val wv = larg(v.texto, 700, 18f) + 48f
            retanguloArredondado(c, textoX, ty + 6f, wv, 30f, 15f, if (v.vencido) LARANJA_FUNDO else AMBAR_FUNDO)
            icone(c, Nome.CALENDARIO, textoX + 10f, ty + 11f, 20f, cc, 2f)
            texto(c, v.texto, textoX + 36f, ty + 27f, 700, 18f, cc)
        }
        val dir = M + CW
        texto(c, FormatoCentavos.brl(l.centavos), dir, y0 + 38f, 700, 26f, cor, Paint.Align.RIGHT)
        if (l.status == RelatorioPessoas.Status.PENDENTE) pilula(c, l.statusTexto, dir, y0 + 72f, AMBAR_FUNDO, AMBAR, Nome.RELOGIO)
        else pilula(c, l.statusTexto, dir, y0 + 72f, QUITADO_FUNDO, QUITADO, Nome.CHECK)
        preenche(DIVISOR) { c.drawRect(M, y0 + b.h - 2f, M + CW, y0 + b.h, it) }
    }

    private fun desenharBloco(c: Canvas, b: LayoutPessoas.Bloco, y: Float) {
        when (b) {
            is LayoutPessoas.Bloco.Titulo -> {
                preenche(MARCA) { c.drawRect(M, y + 34f, M + 6f, y + 58f, it) }
                texto(c, b.texto, M + 18f, y + 54f, 700, 26f, MARCA_ESCURA)
            }
            is LayoutPessoas.Bloco.CartaoPessoa -> desenharPessoa(c, b, y)
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
                preenche(DIVISOR) { c.drawRect(M, y + b.h - 2f, M + CW, y + b.h, it) }
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

    private fun desenharCabecalho1(c: Canvas, rel: RelatorioPessoas.Relatorio, plano: LayoutPessoas.Plano) {
        preenche(MARCA) { c.drawRect(0f, 0f, LARGURA.toFloat(), 12f, it) }
        var y = 12f + 28f
        retanguloArredondado(c, M, y, 56f, 56f, 14f, MARCA)
        icone(c, Nome.CARTEIRA, M + 12f, y + 12f, 32f, BRANCO, 2.4f)
        texto(c, "MeuDinheiro", M + 72f, y + 40f, 800, 38f, MARCA)
        texto(c, "Emitido em ${rel.emitidoEm}", M + CW, y + 38f, 400, 18f, CINZA, Paint.Align.RIGHT)
        y += 64f + 18f + 36f
        texto(c, rel.titulo, M, y, 700, 42f, TINTA)
        y += 34f
        texto(c, "Período: ${rel.periodoTexto}", M, y, 600, 22f, MARCA_ESCURA)
        y += 34f
        for (t in plano.filtrosLinhas) { texto(c, t, M, y, 400, 18f, CINZA); y += 26f }
    }

    /** Pinta uma parte do plano numa tela de [LARGURA] x [altura] px. */
    internal fun desenharPagina(c: Canvas, rel: RelatorioPessoas.Relatorio, plano: LayoutPessoas.Plano, p: LayoutPessoas.Pagina, altura: Float, rotuloPagina: String) {
        c.drawColor(BRANCO)
        if (p.indice == 1) desenharCabecalho1(c, rel, plano)
        else {
            preenche(MARCA) { c.drawRect(0f, 0f, LARGURA.toFloat(), 12f, it) }
            texto(c, "MeuDinheiro", M, 58f, 800, 24f, MARCA)
            texto(c, "${rel.titulo} · ${rel.periodoTexto} (continuação)", M + 190f, 58f, 400, 20f, CINZA)
        }
        var y = p.hCabecalho
        for (b in p.blocos) { desenharBloco(c, b, y); y += b.h }
        val yr = altura - H_RODAPE
        preenche(DIVISOR) { c.drawRect(M, yr + 8f, M + CW, yr + 10f, it) }
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

    fun gerarPdf(context: Context, rel: RelatorioPessoas.Relatorio, agora: Long): File =
        RelatorioPessoasPdf.gerar(rel, File(pasta(context), RelatorioPessoas.nomeArquivo(agora, "pdf")))

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
