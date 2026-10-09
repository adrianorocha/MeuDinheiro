package com.meudinheiro.funcoes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.parcelaDaDescricao
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * R48 — Comprovante do lançamento. Tudo que decide O QUE aparece no documento fica em [montarComprovante]
 * (função pura, testável na JVM); [desenharComprovante] só pinta o modelo num bitmap de altura dinâmica.
 * Campos vazios não geram linha. Não altera nenhuma regra financeira: só apresenta o que o lançamento já tem.
 */
enum class SeloComprovante(val rotulo: String, val cor: Int) {
    RECEITA("RECEITA", 0xFF15803D.toInt()),
    DESPESA("DESPESA", 0xFFB91C1C.toInt()),
    TRANSFERENCIA("TRANSFERÊNCIA", 0xFF1D4ED8.toInt()),
    PAGAMENTO_FATURA("PAGAMENTO DE FATURA", 0xFF6D28D9.toInt()),
    APORTE_META("APORTE EM META", 0xFF0F766E.toInt()),
    RESGATE_META("RESGATE DE META", 0xFF0E7490.toInt()),
    AJUSTE("AJUSTE DE SALDO", 0xFFB45309.toInt()),
    SALDO_INICIAL("SALDO INICIAL", 0xFF475569.toInt())
}

enum class StatusComprovante(val rotulo: String, val cor: Int) {
    PAGO("PAGO", 0xFF15803D.toInt()),
    PENDENTE("PENDENTE", 0xFFB45309.toInt()),
    VENCIDO("VENCIDO", 0xFFB91C1C.toInt())
}

data class ComprovanteLinha(val rotulo: String, val valor: String)

data class ComprovanteModelo(
    val selo: SeloComprovante,
    /** Valor já formatado, com sinal (+ receita / − despesa). */
    val valor: String,
    val entrada: Boolean,
    val status: StatusComprovante,
    val linhas: List<ComprovanteLinha>,
    val id: String,
    val emitidoEm: String
)

/** Dados opcionais que enriquecem o comprovante; todos têm default para não quebrar chamadores antigos. */
data class ComprovanteExtras(
    val conta: ContaSaldo? = null,
    val cartao: Cartao? = null,
    /** Lançamentos do mesmo `grupoId` (parcelas / pernas da transferência), inclusive o próprio. */
    val irmaos: List<Despesa> = emptyList(),
    /** Contas conhecidas (para resolver a conta da outra perna de uma transferência). */
    val contas: List<ContaSaldo> = emptyList(),
    val recorrencia: String? = null,
    val observacoes: String? = null
)

/** Mascara número de conta/cartão: só os 4 últimos dígitos ficam visíveis. */
fun mascararNumero(s: String): String {
    val digitos = s.filter { it.isDigit() }
    return if (digitos.length > 4) "•••• " + digitos.takeLast(4) else s
}

private fun pareceNumero(s: String) = s.isNotBlank() && s.all { it.isDigit() || it in ".-xX " }

fun montarComprovante(
    despesa: Despesa,
    nomeCartao: String?,
    nomeConta: String,
    extras: ComprovanteExtras = ComprovanteExtras(),
    agora: Long = System.currentTimeMillis()
): ComprovanteModelo {
    val pt = Locale("pt", "BR")
    val moeda = NumberFormat.getCurrencyInstance(pt)
    val dia = SimpleDateFormat("dd/MM/yyyy", pt)
    val emissao = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", pt)
    val entrada = despesa.tipo == TipoDespesa.CREDITO

    val selo = when (despesa.natureza) {
        Natureza.TRANSFERENCIA -> SeloComprovante.TRANSFERENCIA
        Natureza.PAGAMENTO_FATURA -> SeloComprovante.PAGAMENTO_FATURA
        Natureza.APORTE_META -> SeloComprovante.APORTE_META
        Natureza.RESGATE_META -> SeloComprovante.RESGATE_META
        Natureza.AJUSTE -> SeloComprovante.AJUSTE
        Natureza.SALDO_INICIAL -> SeloComprovante.SALDO_INICIAL
        else -> if (entrada) SeloComprovante.RECEITA else SeloComprovante.DESPESA
    }

    val inicioHoje = Calendar.getInstance().apply {
        timeInMillis = agora
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val status = when {
        despesa.pago -> StatusComprovante.PAGO
        despesa.data.time < inicioHoje && (despesa.cartaoId == null || despesa.cartaoId == 0) -> StatusComprovante.VENCIDO
        else -> StatusComprovante.PENDENTE
    }

    val linhas = mutableListOf<ComprovanteLinha>()
    fun add(rotulo: String, valor: String?) {
        if (!valor.isNullOrBlank()) linhas += ComprovanteLinha(rotulo, valor.trim())
    }

    val parcela = parcelaDaDescricao(despesa.descricao)
    add("Descrição", parcela?.first ?: despesa.descricao)
    add("Categoria", despesa.categoria)
    if (despesa.natureza != Natureza.NORMAL) add("Natureza", selo.rotulo.lowercase(pt).replaceFirstChar { it.uppercase() })

    // Datas
    val cartao = extras.cartao
    val noCredito = despesa.cartaoId != null && despesa.cartaoId != 0
    val vinculoDebito = Financas.cartaoDeDebito(despesa)
    add("Data do lançamento", dia.format(despesa.data))
    if (noCredito && cartao != null) {
        val ref = Financas.faturaDaCompra(despesa.dataMs, cartao.diaFechamento)
        add("Fatura de referência", "%02d/%04d".format(ref.mes, ref.ano))
        add("Vencimento da fatura", dia.format(Date(Financas.dataVencimento(cartao.diaFechamento, cartao.diaVencimento, ref))))
    } else if (noCredito) {
        add("Fatura de referência", "%02d/%04d".format(despesa.mes, despesa.ano))
    } else if (!despesa.pago) {
        add("Vencimento", dia.format(despesa.data))
    }
    if (despesa.pago) add("Data do pagamento", dia.format(despesa.data))

    // Conta
    val conta = extras.conta
    if (conta != null) {
        add("Conta", "${conta.banco} · ag. ${conta.agencia} · ${mascararNumero(conta.conta)}".replace(" · ag.  ·", " ·"))
    } else {
        add(if (noCredito) "Conta da fatura" else if (entrada) "Conta de destino" else "Conta de origem",
            if (pareceNumero(nomeConta)) mascararNumero(nomeConta) else nomeConta)
    }
    if (despesa.natureza == Natureza.TRANSFERENCIA) {
        val outra = extras.irmaos.firstOrNull { it.id != despesa.id && it.conta != despesa.conta }
        val outraConta = outra?.let { o -> extras.contas.firstOrNull { it.conta == o.conta }?.banco ?: mascararNumero(o.conta) }
        val minha = conta?.banco ?: if (pareceNumero(nomeConta)) mascararNumero(nomeConta) else nomeConta
        if (outraConta != null) add("Transferência", if (entrada) "$outraConta → $minha" else "$minha → $outraConta")
    }

    // Cartão
    val nomeDoCartao = cartao?.nome ?: nomeCartao
    if (!nomeDoCartao.isNullOrBlank()) {
        val final = cartao?.finalCartao?.takeIf { it.isNotBlank() }?.let { " •••• $it" } ?: ""
        add("Cartão", nomeDoCartao + final)
        if (cartao != null) add("Tipo do cartão", if (cartao.ehVirtual) "Virtual" else "Físico")
    }
    if (noCredito) add("Modalidade", "Crédito")
    else if (vinculoDebito != null) add("Modalidade", "Débito")

    // Parcela
    if (parcela != null) {
        val (_, i, n) = parcela
        add("Parcela", "$i/$n")
        val grupo = extras.irmaos.filter { it.grupoId != null && it.grupoId == despesa.grupoId }
        if (grupo.size >= 2) add("Valor total da compra", moeda.format(Dinheiro.reais(grupo.sumOf { Dinheiro.centavos(it.valor) })))
        add("Parcelas restantes", (n - i).toString())
    }

    add("Recorrência", extras.recorrencia)
    if (despesa.moedaOriginal != "BRL" && despesa.valorOriginal > 0) {
        add("Valor original", "${despesa.moedaOriginal} ${"%.2f".format(pt, despesa.valorOriginal)} (cotação ${"%.4f".format(pt, despesa.cotacaoNaData)})")
    }
    add("Lançado por", despesa.autor)
    add("Observações", extras.observacoes)

    return ComprovanteModelo(
        selo = selo,
        valor = (if (entrada) "+ " else "− ") + moeda.format(despesa.valor),
        entrada = entrada,
        status = status,
        linhas = linhas,
        id = "MD-${despesa.id.toString().padStart(6, '0')}",
        emitidoEm = emissao.format(Date(agora))
    )
}

private const val AVISO_RODAPE = "Documento gerado pelo MeuDinheiro — sem valor fiscal"

/** Quebra [texto] em linhas que cabem em [larguraMax] px com a [paint] dada (palavras longas são cortadas por caractere). */
internal fun quebrarTexto(texto: String, paint: Paint, larguraMax: Float): List<String> {
    val saida = mutableListOf<String>()
    var atual = ""
    fun empurra(p: String) {
        var palavra = p
        while (paint.measureText(palavra) > larguraMax && palavra.length > 1) {
            val n = paint.breakText(palavra, true, larguraMax, null).coerceAtLeast(1)
            if (atual.isNotEmpty()) { saida += atual; atual = "" }
            saida += palavra.substring(0, n)
            palavra = palavra.substring(n)
        }
        val tentativa = if (atual.isEmpty()) palavra else "$atual $palavra"
        if (paint.measureText(tentativa) <= larguraMax) atual = tentativa
        else { if (atual.isNotEmpty()) saida += atual; atual = palavra }
    }
    texto.split(' ').filter { it.isNotEmpty() }.forEach(::empurra)
    if (atual.isNotEmpty()) saida += atual
    return saida.ifEmpty { listOf("") }
}

fun desenharComprovante(ctx: Context, m: ComprovanteModelo): Bitmap {
    val largura = 900
    val margem = 72f
    val esquerda = margem
    val direita = largura - margem
    val colRotulo = 300f
    val larguraValor = direita - esquerda - colRotulo - 24f

    val tinta = Color.parseColor("#0F172A")
    val cinza = Color.parseColor("#64748B")
    val divisor = Color.parseColor("#E2E8F0")
    val marca = Color.parseColor("#0F766E")

    fun paint(tam: Float, cor: Int, negrito: Boolean = false, alinha: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam; color = cor; textAlign = alinha
        typeface = Typeface.create(Typeface.DEFAULT, if (negrito) Typeface.BOLD else Typeface.NORMAL)
    }
    val pRotulo = paint(26f, cinza)
    val pValor = paint(28f, tinta, true, Paint.Align.RIGHT)
    val pAviso = paint(22f, cinza, alinha = Paint.Align.CENTER)

    // Primeiro mede as linhas (altura dinâmica), depois desenha.
    val alturaLinha = 38f
    val medidas = m.linhas.map { quebrarTexto(it.valor, pValor, larguraValor) to quebrarTexto(it.rotulo, pRotulo, colRotulo) }
    var corpo = 0f
    medidas.forEach { (v, r) -> corpo += maxOf(v.size, r.size) * alturaLinha + 26f }
    val avisoLinhas = quebrarTexto(AVISO_RODAPE, pAviso, largura - 2 * margem)
    val altura = (60f + 150f + 230f + corpo + 40f + 130f + avisoLinhas.size * 30f + 60f).toInt()

    val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.parseColor("#F1F5F9"))
    val folha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(14f, 0f, 4f, 0x22000000) }
    c.drawRoundRect(RectF(32f, 32f, largura - 32f, altura - 32f), 28f, 28f, folha)
    // faixa de marca no topo da folha (recortada pelo canto arredondado)
    c.save()
    c.clipPath(android.graphics.Path().apply { addRoundRect(RectF(32f, 32f, largura - 32f, altura - 32f), 28f, 28f, android.graphics.Path.Direction.CW) })
    c.drawRect(32f, 32f, largura - 32f, 52f, Paint().apply { color = marca })
    c.restore()

    var y = 150f
    // Cabeçalho: logo + marca
    val logo = androidx.core.content.ContextCompat.getDrawable(ctx, com.meudinheiro.R.drawable.meu_dinheiro)?.mutate()
    var xMarca = esquerda
    logo?.let {
        androidx.core.graphics.drawable.DrawableCompat.setTint(it, marca)
        it.setBounds(esquerda.toInt(), (y - 40).toInt(), (esquerda + 52).toInt(), (y + 12).toInt()); it.draw(c)
        xMarca = esquerda + 68f
    }
    c.drawText("MeuDinheiro", xMarca, y, paint(40f, marca, true))
    c.drawText("Comprovante de lançamento", xMarca, y + 34f, paint(24f, cinza))
    c.drawText(m.id, direita, y - 6f, paint(24f, cinza, true, Paint.Align.RIGHT))
    y += 80f

    // Selo do tipo
    val pSelo = paint(24f, Color.WHITE, true, Paint.Align.CENTER)
    val larguraSelo = pSelo.measureText(m.selo.rotulo) + 56f
    val seloRect = RectF((largura - larguraSelo) / 2f, y, (largura + larguraSelo) / 2f, y + 48f)
    c.drawRoundRect(seloRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.selo.cor })
    c.drawText(m.selo.rotulo, largura / 2f, y + 33f, pSelo)
    y += 48f + 78f

    // Valor em destaque
    val corValor = if (m.entrada) Color.parseColor("#15803D") else tinta
    val pMoeda = paint(84f, corValor, true, Paint.Align.CENTER)
    var tamMoeda = 84f
    while (pMoeda.measureText(m.valor) > largura - 2 * margem && tamMoeda > 36f) { tamMoeda -= 4f; pMoeda.textSize = tamMoeda }
    c.drawText(m.valor, largura / 2f, y, pMoeda)
    y += 52f

    // Status
    val pStatus = paint(24f, Color.WHITE, true, Paint.Align.CENTER)
    val larguraStatus = pStatus.measureText(m.status.rotulo) + 44f
    c.drawRoundRect(
        RectF((largura - larguraStatus) / 2f, y - 26f, (largura + larguraStatus) / 2f, y + 18f), 22f, 22f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.status.cor }
    )
    c.drawText(m.status.rotulo, largura / 2f, y + 8f, pStatus)
    y += 58f

    val pDiv = Paint().apply { color = divisor; strokeWidth = 2f }
    c.drawLine(esquerda, y, direita, y, pDiv)
    y += 46f

    // Linhas de detalhe
    m.linhas.forEachIndexed { idx, l ->
        val (vals, rots) = medidas[idx]
        val n = maxOf(vals.size, rots.size)
        rots.forEachIndexed { k, t -> c.drawText(t, esquerda, y + k * alturaLinha, pRotulo) }
        vals.forEachIndexed { k, t -> c.drawText(t, direita, y + k * alturaLinha, pValor) }
        y += (n - 1) * alturaLinha + 26f
        c.drawLine(esquerda, y, direita, y, pDiv)
        y += 38f
    }

    // Rodapé
    y += 12f
    c.drawText("Emitido em ${m.emitidoEm}", largura / 2f, y, paint(24f, tinta, true, Paint.Align.CENTER))
    y += 40f
    avisoLinhas.forEach { c.drawText(it, largura / 2f, y, pAviso); y += 30f }
    return bmp
}
