package com.meudinheiro.funcoes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * R69 — paleta e ícones de traço do relatório de pessoas (porte de `portal/src/lib/exportar/pessoasIcones.ts`: formas simples numa
 * grade 24x24, para o PNG/JPEG e o PDF ficarem iguais aos do portal).
 */
object IconesPessoas {
    val MARCA = Color.parseColor("#0F766E")
    val MARCA_ESCURA = Color.parseColor("#115E59")
    val TINTA = Color.parseColor("#0F172A")
    val CINZA = Color.parseColor("#64748B")
    val CINZA_CLARO = Color.parseColor("#F1F5F9")
    val DIVISOR = Color.parseColor("#E2E8F0")
    val VERDE = Color.parseColor("#15803D")
    val LARANJA = Color.parseColor("#C2410C")
    val LARANJA_FUNDO = Color.parseColor("#FFEDD5")
    val AMBAR = Color.parseColor("#B45309")
    val AMBAR_FUNDO = Color.parseColor("#FEF3C7")
    val QUITADO = Color.parseColor("#4B6B62")
    val QUITADO_FUNDO = Color.parseColor("#E3ECE9")
    val TEAL_CLARO = Color.parseColor("#E6F4F1")
    val BRANCO = Color.WHITE

    private val PALETA = intArrayOf(
        Color.parseColor("#0F766E"), Color.parseColor("#0E7490"), Color.parseColor("#4F46E5"), Color.parseColor("#B45309"),
        Color.parseColor("#BE185D"), Color.parseColor("#4D7C0F"), Color.parseColor("#7C3AED"), Color.parseColor("#475569")
    )

    fun corAvatar(indice: Int) = PALETA[((indice % PALETA.size) + PALETA.size) % PALETA.size]

    enum class Nome { ENTRADA, SAIDA, RELOGIO, CHECK, RECIBO, CARTEIRA, BALANCA, CALENDARIO }

    private sealed class Forma {
        class Linha(val p: List<Pair<Float, Float>>, val fechar: Boolean = false) : Forma()
        class Circulo(val cx: Float, val cy: Float, val r: Float) : Forma()
    }

    private fun l(vararg p: Float, fechar: Boolean = false) = Forma.Linha(p.toList().chunked(2).map { it[0] to it[1] }, fechar)

    private val ICONES: Map<Nome, List<Forma>> = mapOf(
        Nome.ENTRADA to listOf(l(18f, 6f, 6f, 18f), l(6f, 9f, 6f, 18f, 15f, 18f)),
        Nome.SAIDA to listOf(l(6f, 18f, 18f, 6f), l(9f, 6f, 18f, 6f, 18f, 15f)),
        Nome.RELOGIO to listOf(Forma.Circulo(12f, 12f, 9f), l(12f, 7f, 12f, 12f, 16f, 14f)),
        Nome.CHECK to listOf(Forma.Circulo(12f, 12f, 9f), l(8f, 12.5f, 11f, 15.5f, 16.5f, 9f)),
        Nome.RECIBO to listOf(
            l(6f, 3f, 18f, 3f, 18f, 21f, 15f, 19f, 12f, 21f, 9f, 19f, 6f, 21f, fechar = true),
            l(9f, 8f, 15f, 8f), l(9f, 12f, 15f, 12f)
        ),
        Nome.CARTEIRA to listOf(
            l(3f, 8f, 21f, 8f, 21f, 20f, 3f, 20f, fechar = true), l(5f, 8f, 5f, 5f, 18f, 5f, 18f, 8f), Forma.Circulo(16.5f, 14f, 1.3f)
        ),
        Nome.CALENDARIO to listOf(
            l(4f, 6f, 20f, 6f, 20f, 20f, 4f, 20f, fechar = true), l(4f, 11f, 20f, 11f), l(8f, 3f, 8f, 8f), l(16f, 3f, 16f, 8f)
        ),
        Nome.BALANCA to listOf(
            l(12f, 4f, 12f, 20f), l(7f, 20f, 17f, 20f), l(4f, 8f, 20f, 8f),
            l(4f, 8f, 2f, 14f, 6f, 14f, fechar = true), l(20f, 8f, 18f, 14f, 22f, 14f, fechar = true)
        )
    )

    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Desenha o ícone em (x, y) (canto superior esquerdo) com [tam] de lado e traço [esp], na unidade do [c]. */
    fun desenhar(c: Canvas, nome: Nome, x: Float, y: Float, tam: Float, cor: Int, esp: Float) {
        val e = tam / 24f
        pincel.reset()
        pincel.isAntiAlias = true
        pincel.style = Paint.Style.STROKE
        pincel.color = cor
        pincel.strokeWidth = esp
        pincel.strokeCap = Paint.Cap.ROUND
        pincel.strokeJoin = Paint.Join.ROUND
        for (f in ICONES.getValue(nome)) when (f) {
            is Forma.Circulo -> c.drawCircle(x + f.cx * e, y + f.cy * e, f.r * e, pincel)
            is Forma.Linha -> {
                val n = if (f.fechar) f.p.size else f.p.size - 1
                for (i in 0 until n) {
                    val a = f.p[i]
                    val b = f.p[(i + 1) % f.p.size]
                    c.drawLine(x + a.first * e, y + a.second * e, x + b.first * e, y + b.second * e, pincel)
                }
            }
        }
    }
}
