package com.meudinheiro.funcoes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.meudinheiro.domain.ComposicaoGasto
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Rateios
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
import com.meudinheiro.funcoes.IconesPessoas.TEAL_CLARO
import com.meudinheiro.funcoes.IconesPessoas.TINTA
import com.meudinheiro.funcoes.IconesPessoas.VERDE
import java.io.File
import java.io.FileOutputStream

/**
 * R69 — PDF A4 multipágina do relatório de pessoas, porte de `portal/src/lib/exportar/pessoasPdf.ts` (mesmas medidas em mm, mesmos
 * textos, cores, ícones e tabelas com cabeçalho repetido; rodapé "Página X de Y" + emissão em todas as páginas). O desenho é gravado
 * por página e só pintado no fim, quando o total de páginas é conhecido. Cor nunca é a única informação: sempre há texto e ícone.
 */
object RelatorioPessoasPdf {
    private const val PT_W = 595
    private const val PT_H = 842
    private const val K = PT_W / 210f          // pt por mm
    private const val L = 210f
    private const val A = PT_H / K              // altura da página em mm (~297)
    private const val MARGEM = 14f
    private const val TOPO_CONT = 22f
    private const val RODAPE = 16f
    private const val CW = L - 2 * MARGEM
    private const val MM_PT = 0.352778f          // 1 pt em mm
    private const val PAD = 1.8f

    /** Helvetica padrão não garante "—", "−", "→" nem "…" (no portal): troca por equivalentes seguros, igual ao portal. */
    fun textoPdf(s: String) = s.replace('—', '-').replace('–', '-').replace('−', '-').replace("→", "->").replace("…", "...")

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun typeface(bold: Boolean) = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

    private fun larg(s: String, pt: Float, bold: Boolean = false): Float {
        pincel.reset(); pincel.isAntiAlias = true; pincel.textSize = pt * MM_PT; pincel.typeface = typeface(bold)
        return pincel.measureText(textoPdf(s))
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, pt: Float, cor: Int, bold: Boolean = false, alinha: Paint.Align = Paint.Align.LEFT) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.textSize = pt * MM_PT; pincel.typeface = typeface(bold)
        pincel.color = cor; pincel.textAlign = alinha
        c.drawText(textoPdf(s), x, y, pincel)
    }

    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, cor: Int, r: Float = 0f) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.style = Paint.Style.FILL
        if (r > 0f) c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, pincel) else c.drawRect(x, y, x + w, y + h, pincel)
    }

    /** Faixa de acento à esquerda com cantos arredondados (como o cartão do portal). */
    private fun faixa(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, fundo: Int, acento: Int, la: Float) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) })
        rect(c, x, y, w, h, fundo)
        rect(c, x, y, la, h, acento)
        c.restore()
    }

    private fun linha(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, cor: Int, esp: Float) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.strokeWidth = esp; pincel.style = Paint.Style.STROKE
        c.drawLine(x1, y1, x2, y2, pincel)
    }

    private fun icone(c: Canvas, nome: Nome, x: Float, y: Float, tam: Float, cor: Int, esp: Float = 0.25f) = IconesPessoas.desenhar(c, nome, x, y, tam, cor, esp)

    private fun avatar(c: Canvas, cx: Float, cy: Float, r: Float, iniciais: String, cor: Int) {
        pincel.reset(); pincel.isAntiAlias = true; pincel.color = cor; pincel.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r, pincel)
        txt(c, iniciais, cx, cy + r * 0.33f, r * 1.7f, BRANCO, true, Paint.Align.CENTER)
    }

    private class Cel(
        val linhas: List<String>, val cor: Int = TINTA, val bold: Boolean = false, val direita: Boolean = false,
        val padE: Float = PAD, val fundo: Int? = null, val span: Int = 1
    )

    private class Pagina { val ops = ArrayList<(Canvas) -> Unit>() }

    fun gerar(rel: RelatorioPessoas.Relatorio, destino: File): File {
        val paginas = arrayListOf(Pagina())
        var y = 0f
        fun d(op: (Canvas) -> Unit) { paginas.last().ops += op }
        fun garantir(h: Float) { if (y + h > A - RODAPE) { paginas += Pagina(); y = TOPO_CONT } }
        fun quebrar(texto: String, pt: Float, bold: Boolean, max: Float) = LayoutPessoas.quebrar(textoPdf(texto), max) { larg(it, pt, bold) }

        // ---- cabeçalho (faixa de marca)
        d { c ->
            rect(c, 0f, 0f, L, 36f, MARCA)
            rect(c, MARGEM, 7f, 10f, 10f, BRANCO, 2f)
            icone(c, Nome.CARTEIRA, MARGEM + 1.5f, 8.5f, 7f, MARCA, 0.35f)
            txt(c, "MeuDinheiro", MARGEM + 13f, 14.5f, 17f, BRANCO, true)
            txt(c, "Emitido em ${rel.emitidoEm}", L - MARGEM, 14f, 8f, BRANCO, false, Paint.Align.RIGHT)
            txt(c, rel.titulo, MARGEM, 26f, 14f, BRANCO, true)
            txt(c, "Período: ${rel.periodoTexto}", MARGEM, 32f, 9.5f, BRANCO)
        }
        y = 42f
        for (l in quebrar(rel.filtros.joinToString("   ·   "), 8f, false, CW)) {
            val yy = y
            d { c -> txt(c, l, MARGEM, yy, 8f, CINZA) }
            y += 4f
        }
        y += 3f

        // ---- R95/R96: mini gráfico (barra empilhada fina + legenda com o percentual escrito)
        val sw = 2.2f
        val gap = 5f
        class Leg(val fatias: List<ComposicaoGasto.Fatia>, val rotulos: List<String>, val linhas: List<List<Int>>)
        fun legendaPdf(p: RelatorioPessoas.PessoaBloco): Leg {
            val fatias = p.composicao.visiveis
            val rotulos = fatias.map { textoPdf(ComposicaoGasto.rotulo(it)) }
            val larguras = rotulos.map { sw + 1.6f + larg(it, 7.5f) + gap }
            val linhas = ArrayList<List<Int>>()
            var atual = ArrayList<Int>()
            var usado = 0f
            larguras.forEachIndexed { i, w ->
                if (atual.isNotEmpty() && usado + w > CW + gap) { linhas += atual; atual = ArrayList(); usado = 0f }
                atual += i; usado += w
            }
            if (atual.isNotEmpty()) linhas += atual
            return Leg(fatias, rotulos, linhas)
        }
        fun alturaComposicao(p: RelatorioPessoas.PessoaBloco) = if (p.composicao.vazia) 0f else 4.2f + 3f + 3.6f + legendaPdf(p).linhas.size * 4f + 1f
        fun composicaoPessoa(p: RelatorioPessoas.PessoaBloco) {
            if (p.composicao.vazia) return
            val leg = legendaPdf(p)
            val y0 = y
            val total = leg.fatias.sumOf { it.centavos }.toDouble()
            d { c ->
                txt(c, "Composição do gasto", MARGEM, y0 + 2.4f, 7f, CINZA)
                val yb = y0 + 4.2f
                var x = MARGEM
                leg.fatias.forEachIndexed { i, f ->
                    val w = if (i == leg.fatias.lastIndex) MARGEM + CW - x else (CW * (f.centavos / total)).toFloat()
                    rect(c, x, yb, w, 3f, Color.parseColor(ComposicaoGasto.corHex(i, f)))
                    if (i > 0) linha(c, x, yb, x, yb + 3f, BRANCO, 0.3f)
                    x += w
                }
                var yl = yb + 3f + 3.6f
                for (ix in leg.linhas) {
                    var xl = MARGEM
                    for (i in ix) {
                        rect(c, xl, yl - 2f, sw, sw, Color.parseColor(ComposicaoGasto.corHex(i, leg.fatias[i])))
                        txt(c, leg.rotulos[i], xl + sw + 1.6f, yl, 7.5f, TINTA)
                        xl += sw + 1.6f + larg(leg.rotulos[i], 7.5f) + gap
                    }
                    yl += 4f
                }
            }
            y = y0 + 4.2f + 3f + 3.6f + (leg.linhas.size - 1) * 4f + 3.4f
        }

        // ---- cartão da pessoa
        fun cartaoPessoa(p: RelatorioPessoas.PessoaBloco) {
            garantir((if (p.proximoVencimento != null) 30f else 18f) + alturaComposicao(p))
            val y0 = y
            val hex = IconesPessoas.corAvatar(p.corIndice)
            val cs = when (p.sentido) { RelatorioPessoas.Sentido.TE_DEVE -> VERDE; RelatorioPessoas.Sentido.VOCE_DEVE -> LARANJA; else -> CINZA }
            d { c ->
                faixa(c, MARGEM, y0, CW, 14f, 2f, CINZA_CLARO, hex, 1.8f)
                avatar(c, MARGEM + 9f, y0 + 7f, 4.6f, p.iniciais, hex)
                txt(c, p.nome, MARGEM + 16f, y0 + 7f, 11.5f, TINTA, true)
                p.contato?.let { txt(c, it, MARGEM + 16f, y0 + 11.2f, 7.5f, CINZA) }
                val wSaldo = larg(p.saldoTexto, 11f, true)
                txt(c, p.saldoTexto, L - MARGEM - 3f, y0 + 7f, 11f, cs, true, Paint.Align.RIGHT)
                icone(c, if (p.sentido == RelatorioPessoas.Sentido.QUITES) Nome.CHECK else Nome.BALANCA, L - MARGEM - 3f - wSaldo - 7f, y0 + 2.6f, 5f, cs, 0.3f)
                txt(c, "saldo pendente (a receber - a pagar)", L - MARGEM - 3f, y0 + 11f, 6.5f, CINZA, false, Paint.Align.RIGHT)
            }
            y += 17f
            p.proximoVencimento?.let { v ->
                val y1 = y
                val cc = if (v.vencido) LARANJA else AMBAR
                d { c ->
                    faixa(c, MARGEM, y1 - 1f, CW, 10f, 2f, if (v.vencido) LARANJA_FUNDO else AMBAR_FUNDO, cc, 1.8f)
                    icone(c, Nome.CALENDARIO, MARGEM + 4.5f, y1 + 0.9f, 5.2f, cc, 0.3f)
                    txt(c, "VENCIMENTO DA FATURA DO CARTÃO: ${RelatorioPessoas.dataBR(v.ms)}${if (v.vencido) " (vencida)" else ""}", MARGEM + 12f, y1 + 5.4f, 10f, cc, true)
                    txt(c, "valor ligado à fatura: ${FormatoCentavos.brl(v.centavos)}", L - MARGEM - 3f, y1 + 5.4f, 8f, cc, false, Paint.Align.RIGHT)
                }
                y += 12f
            }
            composicaoPessoa(p)
        }

        fun titulo(s: String) {
            garantir(16f)
            val y0 = y
            d { c ->
                rect(c, MARGEM, y0 - 0.5f, 1.4f, 6f, MARCA)
                txt(c, s, MARGEM + 4f, y0 + 4f, 11f, MARCA_ESCURA, true)
            }
            y += 8f
        }

        // ---- tabela genérica (cabeçalho repetido em cada página, linha nunca quebra no meio)
        val lh = 8f * 1.15f * MM_PT
        fun tabela(
            cab: List<String>, larguras: FloatArray, direitas: Set<Int>, corpo: List<List<Cel>>, nomeContinuacao: String?,
            extras: (Int, FloatArray, Float, Float) -> ((Canvas) -> Unit)?
        ) {
            val xs = FloatArray(larguras.size + 1).also { for (i in larguras.indices) it[i + 1] = it[i] + larguras[i] }
            val hCab = lh + 2 * PAD
            fun cabecalho(yy: Float) = d { c ->
                rect(c, MARGEM, yy, CW, hCab, MARCA)
                cab.forEachIndexed { i, t ->
                    val x = MARGEM + xs[i]
                    if (i in direitas) txt(c, t, x + larguras[i] - PAD, yy + PAD + lh * 0.78f, 8f, BRANCO, true, Paint.Align.RIGHT)
                    else txt(c, t, x + PAD, yy + PAD + lh * 0.78f, 8f, BRANCO, true)
                    linha(c, x, yy, x, yy + hCab, DIVISOR, 0.1f)
                }
                linha(c, MARGEM + CW, yy, MARGEM + CW, yy + hCab, DIVISOR, 0.1f)
            }
            var primeira = paginas.size
            fun novaPagina() {
                paginas += Pagina(); y = TOPO_CONT
                if (nomeContinuacao != null && paginas.size > primeira) {
                    d { c -> txt(c, "$nomeContinuacao (continuação)", MARGEM, 19f, 8f, MARCA_ESCURA, true) }
                }
                cabecalho(y); y += hCab
            }
            val hPrimeira = corpo.firstOrNull()?.let { r -> r.maxOf { it.linhas.size } * lh + 2 * PAD } ?: 0f
            if (y + hCab + hPrimeira > A - RODAPE) { paginas += Pagina(); y = TOPO_CONT; primeira = paginas.size }
            cabecalho(y); y += hCab
            corpo.forEachIndexed { idx, cels ->
                val h = cels.maxOf { it.linhas.size } * lh + 2 * PAD
                if (y + h > A - RODAPE) novaPagina()
                val yy = y
                val ex = extras(idx, xs, yy, h)
                d { c ->
                    if (idx % 2 == 1) rect(c, MARGEM, yy, CW, h, Color.rgb(248, 250, 252))
                    var col = 0
                    for (cel in cels) {
                        var w = 0f
                        for (k in 0 until cel.span) w += larguras[col + k]
                        val x = MARGEM + xs[col]
                        if (cel.fundo != null) rect(c, x, yy, w, h, cel.fundo)
                        cel.linhas.forEachIndexed { i, t ->
                            val by = yy + PAD + lh * 0.78f + i * lh
                            if (cel.direita) txt(c, t, x + w - PAD, by, 8f, cel.cor, cel.bold, Paint.Align.RIGHT)
                            else txt(c, t, x + cel.padE, by, 8f, cel.cor, cel.bold)
                        }
                        linha(c, x, yy, x, yy + h, DIVISOR, 0.1f)
                        col += cel.span
                    }
                    linha(c, MARGEM + CW, yy, MARGEM + CW, yy + h, DIVISOR, 0.1f)
                    linha(c, MARGEM, yy + h, MARGEM + CW, yy + h, DIVISOR, 0.1f)
                    ex?.invoke(c)
                }
                y += h
            }
            y += 5f
        }

        fun tabelaAcertos(linhas: List<RelatorioPessoas.Linha>, mostrarPessoa: Boolean, nomeContinuacao: String?, corPorPessoa: Map<Int, Int>, iniciais: Map<Int, String>) {
            val cab = if (mostrarPessoa) listOf("Data", "Pessoa", "Descrição / origem", "Status", "Valor") else listOf("Data", "Descrição / origem", "Status", "Valor")
            val colW = if (mostrarPessoa) floatArrayOf(19f, 28f, CW - 105f, 32f, 26f) else floatArrayOf(19f, CW - 77f, 32f, 26f)
            val colDesc = if (mostrarPessoa) 2 else 1
            val colStatus = colDesc + 1
            val corpo = linhas.map { l ->
                val wDesc = colW[colDesc] - 2 * PAD
                val desc = quebrar(l.descricao, 8f, false, wDesc) + quebrar(l.origem, 8f, false, wDesc) + (l.rateio?.let { quebrar(it.texto, 8f, false, wDesc) } ?: emptyList())
                val wSt = colW[colStatus] - PAD - 7f
                val status = quebrar(l.statusTexto, 8f, false, wSt) + (l.vencimento?.let { listOf("${if (it.vencido) "VENCEU" else "VENCE"} ${RelatorioPessoas.dataBR(it.ms)}") } ?: emptyList())
                val corSt = if (l.vencimento?.vencido == true) LARANJA else if (l.status == RelatorioPessoas.Status.PENDENTE) AMBAR else QUITADO
                val cels = ArrayList<Cel>()
                cels += Cel(listOf(l.dataTexto))
                if (mostrarPessoa) cels += Cel(quebrar(l.pessoaNome, 8f, false, colW[1] - PAD - 8f), cor = corPorPessoa[l.pessoaId] ?: TINTA, padE = 8f)
                cels += Cel(desc)
                cels += Cel(status, cor = corSt, bold = l.vencimento != null, padE = 7f)
                cels += Cel(listOf(FormatoCentavos.brl(l.centavos)), cor = if (l.tipo == Rateios.A_RECEBER) VERDE else LARANJA, bold = true, direita = true)
                cels as List<Cel>
            }
            tabela(cab, colW, setOf(cab.size - 1), corpo, nomeContinuacao) { idx, xs, yy, h ->
                val l = linhas[idx]
                val fn: (Canvas) -> Unit = { c ->
                    val xSt = MARGEM + xs[colStatus] + 1.6f
                    icone(c, if (l.status == RelatorioPessoas.Status.PENDENTE) Nome.RELOGIO else Nome.CHECK, xSt, yy + 1.7f, 4f, if (l.status == RelatorioPessoas.Status.PENDENTE) AMBAR else QUITADO, 0.28f)
                    if (mostrarPessoa) avatar(c, MARGEM + xs[1] + 4.2f, yy + 1.7f + 2f, 2.4f, (iniciais[l.pessoaId] ?: "?").take(1), corPorPessoa[l.pessoaId] ?: CINZA)
                    if (l.rateio != null) icone(c, Nome.RECIBO, MARGEM + xs[colDesc + 1] - 5f, yy + h - 5.4f, 3.6f, MARCA, 0.25f)
                }
                fn
            }
        }

        if (rel.agrupar == RelatorioPessoas.Agrupar.CRONOLOGICO && rel.incluirDetalhes) {
            titulo("Saldo por pessoa")
            for (p in rel.pessoas) cartaoPessoa(p)
            y += 2f
            titulo("Acertos em ordem cronológica")
            tabelaAcertos(
                rel.cronologico, true, "Acertos em ordem cronológica",
                rel.pessoas.associate { it.pessoaId to IconesPessoas.corAvatar(it.corIndice) }, rel.pessoas.associate { it.pessoaId to it.iniciais }
            )
        } else {
            for (p in rel.pessoas) {
                cartaoPessoa(p)
                if (p.linhas.isNotEmpty()) tabelaAcertos(p.linhas, false, p.nome, emptyMap(), emptyMap()) else y += 1f
            }
        }

        // ---- bloco opcional: rateios
        rel.rateios?.let { rt ->
            garantir(40f)
            titulo("Rateios · quanto você realmente gastou (informativo)")
            val colW = floatArrayOf(19f, CW - 111f, 26f, 38f, 28f)
            val cab = listOf("Data", "Lançamento", "Total", "A receber de terceiros", "Você gastou")
            val corpo = ArrayList<List<Cel>>()
            for (i in rt.itens) {
                corpo += listOf(
                    Cel(listOf(RelatorioPessoas.dataBR(i.data))),
                    Cel(quebrar(i.descricao + if (i.orfao) " (lançamento removido)" else "", 8f, false, colW[1] - 2 * PAD)),
                    Cel(listOf(if (i.orfao) "-" else FormatoCentavos.brl(i.total)), direita = true),
                    Cel(listOf(FormatoCentavos.brl(i.aReceber)), direita = true),
                    Cel(listOf(if (i.orfao) "-" else FormatoCentavos.brl(i.liquido)), direita = true)
                )
            }
            val fundo = TEAL_CLARO
            corpo += listOf(
                Cel(listOf("Total (lançamentos existentes)"), bold = true, direita = true, fundo = fundo, span = 2),
                Cel(listOf(FormatoCentavos.brl(rt.totalRateado)), bold = true, direita = true, fundo = fundo),
                Cel(listOf(FormatoCentavos.brl(rt.aReceberTerceiros)), bold = true, cor = VERDE, direita = true, fundo = fundo),
                Cel(listOf(FormatoCentavos.brl(rt.liquido)), bold = true, cor = MARCA_ESCURA, direita = true, fundo = fundo)
            )
            tabela(cab, colW, setOf(2, 3, 4), corpo, null) { _, _, _, _ -> null }
            y -= 1f
            val y2 = y
            d { c -> txt(c, "Informativo: o lançamento e os relatórios continuam com o valor total que saiu da conta.", MARGEM, y2, 7.5f, CINZA) }
        }

        // ---- pinta: cabeçalho de continuação e rodapé (agora que o total de páginas é conhecido)
        val total = paginas.size
        val doc = PdfDocument()
        try {
            paginas.forEachIndexed { i, pg ->
                val folha = doc.startPage(PdfDocument.PageInfo.Builder(PT_W, PT_H, i + 1).create())
                val c = folha.canvas
                c.drawColor(BRANCO)
                c.scale(K, K)
                pg.ops.forEach { it(c) }
                if (i > 0) {
                    rect(c, 0f, 0f, L, 3f, MARCA)
                    txt(c, "MeuDinheiro", MARGEM, 10f, 10f, MARCA, true)
                    txt(c, "${rel.titulo} · ${rel.periodoTexto}", MARGEM + 30f, 10f, 8f, CINZA)
                }
                linha(c, MARGEM, A - 12f, L - MARGEM, A - 12f, DIVISOR, 0.2f)
                txt(c, "MeuDinheiro · emitido em ${rel.emitidoEm}", MARGEM, A - 8f, 8f, CINZA)
                txt(c, "Página ${i + 1} de $total", L - MARGEM, A - 8f, 8f, CINZA, false, Paint.Align.RIGHT)
                doc.finishPage(folha)
            }
            FileOutputStream(destino).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return destino
    }
}
