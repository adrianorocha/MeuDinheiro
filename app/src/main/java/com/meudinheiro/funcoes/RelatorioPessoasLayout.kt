package com.meudinheiro.funcoes

import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Rateios
import com.meudinheiro.domain.RelatorioPessoas

/** Medidor de texto abstrato (largura em px lógicos para um peso/tamanho): permite testar o layout na JVM sem Android. */
fun interface MedidorTexto {
    fun largura(texto: String, peso: Int, tam: Float): Float
}

/**
 * R69 — layout PURO do relatório de pessoas (mesmo plano do portal `lib/exportar/pessoasImagem.ts`). Largura lógica fixa de
 * [LARGURA] px; o desenho (PNG/JPEG e PDF) só pinta este plano com uma escala uniforme.
 *
 * Imagens: no máximo [MAX_ALTURA_IMAGEM] px por arquivo; acima disso o conteúdo é dividido em partes numeradas (nada é omitido).
 * PDF: o mesmo plano com altura de página A4 ([ALTURA_PAGINA_PDF] px lógicos), cabeçalho de continuação e "Página X de Y".
 */
object LayoutPessoas {
    const val LARGURA = 1200
    const val MAX_ALTURA_IMAGEM = 6000f

    /** A4 (595 x 842 pt) com 1200 px lógicos de largura. */
    const val ALTURA_PAGINA_PDF = 1698f
    const val M = 48f
    const val CW = LARGURA - 2 * M
    const val H_CONT = 96f
    const val H_RODAPE = 64f
    const val H_PESSOA_CONT = 56f
    private const val H_RESUMO = 2 * 118f + 20f

    sealed class Bloco {
        abstract val h: Float
        data class Titulo(val texto: String, override val h: Float = 64f) : Bloco()
        data class CartaoPessoa(val pessoa: RelatorioPessoas.PessoaBloco, override val h: Float = 156f) : Bloco()
        data class Cont(val nome: String, val iniciais: String, val corIndice: Int, override val h: Float = H_PESSOA_CONT) : Bloco()
        data class Linha(
            val linha: RelatorioPessoas.Linha, val desc: List<String>, val origem: List<String>, val rateio: List<String>,
            val mostrarPessoa: Boolean, val corIndice: Int, val iniciais: String, override val h: Float
        ) : Bloco()
        data class Rateio(val item: Rateios.ItemRateio, val l1: List<String>, val l2: List<String>, override val h: Float) : Bloco()
        data class RateioTotais(val total: Long, val terceiros: Long, val liquido: Long, override val h: Float = 112f) : Bloco()
    }

    data class Pagina(val indice: Int, val total: Int, val blocos: List<Bloco>, val altura: Float, val hCabecalho: Float)
    data class Plano(val paginas: List<Pagina>, val filtrosLinhas: List<String>, val hCabecalho1: Float)

    /** Quebra [texto] em linhas que cabem em [max] (palavras maiores que a linha são cortadas por caractere, nunca descartadas). */
    fun quebrar(texto: String, max: Float, medir: (String) -> Float): List<String> {
        val linhas = ArrayList<String>()
        var atual = ""
        fun cabe(t: String) = medir(t) <= max
        for (palavra in texto.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            var p = palavra
            while (!cabe(p) && p.length > 1) {
                var n = p.length - 1
                while (n > 1 && !cabe(p.substring(0, n))) n--
                if (atual.isNotEmpty()) { linhas += atual; atual = "" }
                linhas += p.substring(0, n)
                p = p.substring(n)
            }
            val tentativa = if (atual.isNotEmpty()) "$atual $p" else p
            if (cabe(tentativa)) atual = tentativa else { if (atual.isNotEmpty()) linhas += atual; atual = p }
        }
        if (atual.isNotEmpty()) linhas += atual
        return linhas.ifEmpty { listOf("") }
    }

    fun planejar(rel: RelatorioPessoas.Relatorio, m: MedidorTexto, maxAltura: Float): Plano {
        fun q(texto: String, peso: Int, tam: Float, max: Float) = quebrar(texto, max) { m.largura(it, peso, tam) }
        val filtrosLinhas = q(rel.filtros.joinToString("   ·   "), 400, 18f, CW)
        val hCabecalho1 = 12f + 28f + 64f + 18f + 48f + 34f + filtrosLinhas.size * 26f + 22f + H_RESUMO + 24f

        val blocos = ArrayList<Bloco>()
        val cronologico = rel.agrupar == RelatorioPessoas.Agrupar.CRONOLOGICO && rel.incluirDetalhes
        val textoW = CW - 72f - 250f

        fun linhaBloco(l: RelatorioPessoas.Linha, p: RelatorioPessoas.PessoaBloco?, mostrarPessoa: Boolean): Bloco {
            val desc = q(l.descricao, 600, 22f, textoW)
            val origem = q("${l.dataTexto} · ${l.origem}", 400, 18f, textoW)
            val rateio = l.rateio?.let { q(it.texto, 500, 17f, textoW - 26f) } ?: emptyList()
            val texto = (if (mostrarPessoa) 34f else 0f) + desc.size * 28f + origem.size * 24f + rateio.size * 24f
            return Bloco.Linha(l, desc, origem, rateio, mostrarPessoa, p?.corIndice ?: 0, p?.iniciais ?: "?", maxOf(texto + 30f, 104f))
        }

        val porId = rel.pessoas.associateBy { it.pessoaId }
        if (cronologico) {
            blocos += Bloco.Titulo("Saldo por pessoa")
            rel.pessoas.forEach { blocos += Bloco.CartaoPessoa(it) }
            blocos += Bloco.Titulo("Acertos em ordem cronológica")
            rel.cronologico.forEach { blocos += linhaBloco(it, porId[it.pessoaId], true) }
        } else {
            for (p in rel.pessoas) {
                blocos += Bloco.CartaoPessoa(p)
                p.linhas.forEach { blocos += linhaBloco(it, p, false) }
            }
        }
        rel.rateios?.let { r ->
            blocos += Bloco.Titulo("Rateios · quanto você realmente gastou (informativo)")
            for (item in r.itens) {
                val l1 = q("${RelatorioPessoas.dataBR(item.data)} · ${item.descricao}${if (item.orfao) " (lançamento removido)" else ""}", 600, 21f, CW - 16f)
                val l2 = q(
                    if (item.orfao) "A receber de terceiros ${FormatoCentavos.brl(item.aReceber)}"
                    else "Total ${FormatoCentavos.brl(item.total)} · A receber de terceiros ${FormatoCentavos.brl(item.aReceber)} · Você gastou ${FormatoCentavos.brl(item.liquido)}",
                    400, 19f, CW - 16f
                )
                blocos += Bloco.Rateio(item, l1, l2, 20f + l1.size * 28f + l2.size * 26f + 14f)
            }
            blocos += Bloco.RateioTotais(r.totalRateado, r.aReceberTerceiros, r.liquido)
        }

        // Empacota em partes de no máximo [maxAltura]; título/cartão ("manter") não ficam sozinhos no fim da parte.
        val partes = arrayListOf(ArrayList<Bloco>())
        var usado = 0f
        var capacidade = maxAltura - hCabecalho1 - H_RODAPE
        var atual: RelatorioPessoas.PessoaBloco? = null
        for ((i, b) in blocos.withIndex()) {
            if (b is Bloco.CartaoPessoa) atual = b.pessoa
            var precisa = b.h
            if (b is Bloco.Titulo || b is Bloco.CartaoPessoa) blocos.getOrNull(i + 1)?.let { precisa += it.h }
            if (usado + precisa > capacidade && partes.last().isNotEmpty()) {
                partes += ArrayList<Bloco>()
                usado = 0f
                capacidade = maxAltura - H_CONT - H_RODAPE
                if (b is Bloco.Linha && !b.mostrarPessoa && atual != null) {
                    partes.last() += Bloco.Cont(atual.nome, atual.iniciais, atual.corIndice)
                    usado += H_PESSOA_CONT
                }
            }
            partes.last() += b
            usado += b.h
        }
        val total = partes.size
        val paginas = partes.mapIndexed { i, bl ->
            val hCab = if (i == 0) hCabecalho1 else H_CONT
            Pagina(i + 1, total, bl, hCab + bl.sumOf { it.h.toDouble() }.toFloat() + H_RODAPE, hCab)
        }
        return Plano(paginas, filtrosLinhas, hCabecalho1)
    }
}
