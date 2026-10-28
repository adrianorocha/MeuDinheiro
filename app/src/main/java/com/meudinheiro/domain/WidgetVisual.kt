package com.meudinheiro.domain

import com.meudinheiro.funcoes.formatarMoedaBR
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * R92 — regras puras do visual do widget de saldo (sem Android, testadas na JVM): qual variação desenhar para o tamanho
 * recebido, como formatar saldo/chips/vencimentos e para onde cada atalho aponta.
 */
object WidgetPlano {
    enum class Variacao { COMPACTO, ENXUTO, MEDIO, GRANDE }

    /**
     * O que desenhar em um dado tamanho (dp).
     * @property vencimentos quantos próximos vencimentos listar (só no GRANDE).
     * @property proxima mostra uma linha "próxima conta" (MEDIO com altura de sobra).
     * @property atualizado mostra "atualizado às HH:mm" (GRANDE com altura de sobra).
     * @property atalhos mostra os botões de repetir despesa (GRANDE bem alto).
     * @property rotulos acesso rápido com texto (GRANDE largo) em vez de só ícone.
     */
    data class Plano(
        val variacao: Variacao,
        val vencimentos: Int = 0,
        val proxima: Boolean = false,
        val atualizado: Boolean = false,
        val atalhos: Boolean = false,
        val rotulos: Boolean = false,
    )

    // Tamanhos declarados no SizeMode.Responsive: cada limite abaixo coincide com um destes (largura, altura em dp).
    const val LARG_COMPACTO = 110
    const val ALT_COMPACTO = 60
    const val LARG_PADRAO = 170
    const val ALT_ENXUTO = 96
    const val ALT_MEDIO = 130
    const val ALT_MEDIO_PROXIMA = 165
    const val ALT_GRANDE = 240
    const val ALT_GRANDE_ATUALIZADO = 260
    const val ALT_GRANDE_3_VENC = 280
    const val ALT_GRANDE_ATALHOS = 320

    /** Largura a partir da qual os atalhos do GRANDE ganham rótulo de texto (abaixo, só ícone). */
    const val LARG_LARGA = 230

    /** Tamanhos (largura, altura) que o widget declara. */
    val TAMANHOS: List<Pair<Int, Int>> = listOf(
        LARG_COMPACTO to ALT_COMPACTO,
        LARG_PADRAO to ALT_ENXUTO,
        LARG_PADRAO to ALT_MEDIO,
        LARG_PADRAO to ALT_MEDIO_PROXIMA,
    ) + listOf(ALT_GRANDE, ALT_GRANDE_ATUALIZADO, ALT_GRANDE_3_VENC, ALT_GRANDE_ATALHOS)
        .flatMap { listOf(LARG_PADRAO to it, LARG_LARGA to it) }

    fun para(larguraDp: Int, alturaDp: Int): Plano = when {
        larguraDp < LARG_PADRAO || alturaDp < ALT_ENXUTO -> Plano(Variacao.COMPACTO)
        alturaDp < ALT_MEDIO -> Plano(Variacao.ENXUTO)
        alturaDp < ALT_GRANDE -> Plano(Variacao.MEDIO, proxima = alturaDp >= ALT_MEDIO_PROXIMA)
        else -> Plano(
            Variacao.GRANDE,
            vencimentos = if (alturaDp >= ALT_GRANDE_3_VENC) 3 else 2,
            atualizado = alturaDp >= ALT_GRANDE_ATUALIZADO,
            atalhos = alturaDp >= ALT_GRANDE_ATALHOS,
            rotulos = larguraDp >= LARG_LARGA,
        )
    }
}

object WidgetFormato {
    private const val MASCARA = "••••••"
    private val PT_BR = Locale("pt", "BR")

    /** Tamanho da fonte do saldo (sp) para o texto não estourar a largura: números longos encolhem. */
    fun fonteSaldoSp(texto: String, variacao: WidgetPlano.Variacao): Int {
        val n = texto.length
        return if (variacao == WidgetPlano.Variacao.COMPACTO) when {
            n <= 9 -> 18
            n <= 11 -> 15
            else -> 12
        } else {
            val base = if (variacao == WidgetPlano.Variacao.ENXUTO) 20 else 26
            when {
                n <= 9 -> base
                n <= 11 -> minOf(base, 22)
                n <= 13 -> 20
                else -> 17
            }
        }
    }

    /** Saldo: igual ao do app (`formatarMoedaBR`, oculto no modo privado); no compacto, valores muito longos viram "R$ 1,2 mi". */
    fun saldo(valor: Double, privado: Boolean, variacao: WidgetPlano.Variacao): String {
        val cheio = formatarMoedaBR(valor, privado)
        return if (!privado && variacao == WidgetPlano.Variacao.COMPACTO && cheio.length > 12) {
            "${if (valor < 0) "-" else ""}R$ ${abreviado(abs(valor))}"
        } else cheio
    }

    /** Valor de um chip de entradas/saídas, sem "R$" (cabe na largura mínima): "950,00", "12 mil", "1,2 mi". */
    fun valorChip(valor: Double, privado: Boolean): String {
        if (privado) return MASCARA
        val nf = NumberFormat.getNumberInstance(PT_BR).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        val cheio = nf.format(abs(valor))
        return if (cheio.length <= 8) cheio else abreviado(abs(valor))
    }

    fun chipEntradas(valor: Double, privado: Boolean) = "▲ ${valorChip(valor, privado)}"
    fun chipSaidas(valor: Double, privado: Boolean) = "▼ ${valorChip(valor, privado)}"

    /** Valor de vencimento/próxima conta: igual ao app (oculto no modo privado). */
    fun valorConta(valor: Double, privado: Boolean): String = formatarMoedaBR(valor, privado)

    /** 950 -> "950"; 1.234 -> "1,2 mil"; 12.345 -> "12 mil"; 1.234.567 -> "1,2 mi". */
    internal fun abreviado(v: Double): String {
        fun um(x: Double): String =
            NumberFormat.getNumberInstance(PT_BR).apply { minimumFractionDigits = 0; maximumFractionDigits = 1 }.format(x)
        return when {
            v < 1_000 -> NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 0 }.format(v)
            v < 10_000 -> "${um(v / 1_000)} mil"
            v < 1_000_000 -> "${Math.round(v / 1_000)} mil"
            v < 10_000_000 -> "${um(v / 1_000_000)} mi"
            else -> "${Math.round(v / 1_000_000)} mi"
        }
    }

    /** "HH:mm" do instante da leitura do banco. */
    fun horaAtualizacao(instanteMs: Long, fuso: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm", PT_BR).apply { timeZone = fuso }.format(Date(instanteMs))

    /** Rótulo do dia de um vencimento: "Hoje", "Amanhã" ou "dd/MM". Atrasada sempre mostra a data. */
    fun rotuloDia(c: ContaWidget, agoraMs: Long, fuso: TimeZone = TimeZone.getDefault()): String {
        val dia = SimpleDateFormat("yyyyMMdd", PT_BR).apply { timeZone = fuso }
        val hoje = dia.format(Date(agoraMs))
        val amanha = dia.format(Date(agoraMs + 86_400_000L))
        val d = dia.format(c.data)
        return when {
            !c.atrasada && d == hoje -> "Hoje"
            !c.atrasada && d == amanha -> "Amanhã"
            else -> SimpleDateFormat("dd/MM", PT_BR).apply { timeZone = fuso }.format(c.data)
        }
    }
}

object WidgetVencimentos {
    /** Próximos vencimentos: atrasadas primeiro (mais antigas antes), depois por data; empate por maior valor e nome. */
    fun selecionar(contas: List<ContaWidget>, max: Int): List<ContaWidget> =
        contas.sortedWith(
            compareByDescending<ContaWidget> { it.atrasada }.thenBy { it.data.time }.thenByDescending { it.valor }.thenBy { it.descricao }
        ).take(max.coerceAtLeast(0))
}

/** Destinos do app que o widget abre (ação própria por destino: as PendingIntents não se confundem). */
enum class DestinoApp(val acao: String) {
    EXTRATO("com.meudinheiro.action.ABRIR_EXTRATO"),
    CARTOES("com.meudinheiro.action.ABRIR_CARTOES"),
    METAS("com.meudinheiro.action.ABRIR_METAS");

    companion object {
        fun daAcao(acao: String?): DestinoApp? = values().firstOrNull { it.acao == acao }
    }
}

object RoteamentoWidget {
    /** Aba da tela principal de cada destino (0 Saldo, 1 Contas, 2 Cartões, 3 Cofrinhos); null = abre a tela de extrato. */
    fun abaPrincipal(destino: DestinoApp): Int? = when (destino) {
        DestinoApp.EXTRATO -> null
        DestinoApp.CARTOES -> 2
        DestinoApp.METAS -> 3
    }
}
