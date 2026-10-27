package com.meudinheiro.componentes

import android.content.Context
import android.content.Intent
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meudinheiro.MainActivity
import com.meudinheiro.R
import com.meudinheiro.domain.AtalhoWidget
import com.meudinheiro.domain.ContaWidget
import com.meudinheiro.domain.DestinoApp
import com.meudinheiro.domain.RoteamentoLancamento
import com.meudinheiro.domain.WidgetFormato
import com.meudinheiro.domain.WidgetPlano
import com.meudinheiro.domain.WidgetResumo
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Dias à frente considerados "a vencer" no widget. */
private const val JANELA_DIAS = 7

/** O que o widget desenha: o resumo (ou null se o banco falhou), o modo privado e o instante da leitura. */
private data class EstadoWidget(val resumo: WidgetResumo?, val privado: Boolean, val instanteMs: Long)

/**
 * Widget de saldo (R92: visual novo, responsivo). Os dados são lidos do banco a cada desenho (sem cópia em
 * SharedPreferences), então ele nunca fica defasado em relação ao app e respeita o modo privado.
 * Cores e fundos vêm de recursos (claro/escuro e, no Android 12+, tons do papel de parede no cartão).
 */
class SaldoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetPlano.TAMANHOS.map { (l, a) -> DpSize(l.dp, a.dp) }.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = carregar(context)
        provideContent { GlanceTheme { Conteudo(context, estado) } }
    }

    private suspend fun carregar(context: Context): EstadoWidget = withContext(Dispatchers.IO) {
        val privado = runCatching { UserPreferences(context).privateModeFlow.first() }.getOrDefault(false)
        val resumo = runCatching { MainRepository(context).resumoParaWidget(JANELA_DIAS) }.getOrNull()
        EstadoWidget(resumo, privado, System.currentTimeMillis())
    }
}

private fun abrirApp(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
)

/** Abre o app direto no destino (ação própria por destino; a MainActivity já é o ponto de entrada do app). */
private fun abrirDestino(context: Context, destino: DestinoApp) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        action = destino.acao
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
)

/** Painel flutuante de lançamento: o app principal não é aberto. */
private fun lancarRapido(context: Context, modo: RoteamentoLancamento.Modo) = actionStartActivity(
    Intent(context, LancamentoRapidoActivity::class.java).apply {
        // Ação distinta por modo: as PendingIntents dos dois botões não se confundem (extras não contam na comparação).
        action = if (modo == RoteamentoLancamento.Modo.RECEITA) RoteamentoLancamento.ACAO_NOVA_RECEITA else RoteamentoLancamento.ACAO_NOVO_LANCAMENTO
        putExtra(LancamentoRapidoActivity.EXTRA_TIPO, RoteamentoLancamento.tipoDoExtra(modo))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
)

/** Abre o painel já preenchido com o atalho; ele pede a biometria e lança. */
private fun repetirAtalho(context: Context, a: AtalhoWidget) = actionStartActivity(
    Intent(context, LancamentoRapidoActivity::class.java).apply {
        putExtra(LancamentoRapidoActivity.EXTRA_DESCRICAO, a.descricao)
        putExtra(LancamentoRapidoActivity.EXTRA_VALOR, a.valor.toString())
        putExtra(LancamentoRapidoActivity.EXTRA_CATEGORIA, a.categoria)
        putExtra(LancamentoRapidoActivity.EXTRA_CONTA, a.conta)
        putExtra(LancamentoRapidoActivity.EXTRA_CARTAO, a.cartaoId ?: -1)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
)

private fun cor(@ColorRes id: Int) = ColorProvider(id)

private fun estilo(@ColorRes cor: Int, tamanho: Int, negrito: Boolean = false, centro: Boolean = false) = TextStyle(
    color = cor(cor),
    fontSize = tamanho.sp,
    fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
    textAlign = if (centro) TextAlign.Center else TextAlign.Start
)

@androidx.compose.runtime.Composable
private fun Conteudo(context: Context, estado: EstadoWidget) {
    val tamanho = LocalSize.current
    val plano = WidgetPlano.para(tamanho.width.value.toInt(), tamanho.height.value.toInt())
    val resumo = estado.resumo
    val raiz = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_card), ContentScale.FillBounds)

    if (resumo == null) {
        Column(modifier = raiz.padding(12.dp).clickable(abrirApp(context)), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.widget_erro_dados), maxLines = 3, style = estilo(R.color.widget_text_sec, 12))
        }
        return
    }

    when (plano.variacao) {
        WidgetPlano.Variacao.COMPACTO -> Compacto(context, resumo, estado.privado, raiz)
        WidgetPlano.Variacao.ENXUTO -> Enxuto(context, resumo, estado.privado, raiz)
        WidgetPlano.Variacao.MEDIO -> Medio(context, resumo, estado.privado, plano, estado.instanteMs, raiz)
        WidgetPlano.Variacao.GRANDE -> Grande(context, resumo, estado.privado, plano, estado.instanteMs, raiz)
    }
}

// ───────────────────────── Variações ─────────────────────────

/** Saldo + botão "+" (abre o painel de lançamento com seletor Despesa/Receita). */
@androidx.compose.runtime.Composable
private fun Compacto(context: Context, r: WidgetResumo, privado: Boolean, raiz: GlanceModifier) {
    Column(modifier = raiz.padding(horizontal = 10.dp, vertical = 6.dp).clickable(abrirApp(context))) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.widget_label_saldo), maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = estilo(R.color.widget_text_sec, 11))
            Box(
                modifier = GlanceModifier.size(26.dp)
                    .background(ImageProvider(R.drawable.widget_bg_btn_rec), ContentScale.FillBounds)
                    .clickable(lancarRapido(context, RoteamentoLancamento.Modo.DESPESA)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    ImageProvider(R.drawable.ic_w_add), context.getString(R.string.widget_novo_lancamento),
                    modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(cor(R.color.widget_btn_rec_fg))
                )
            }
        }
        Saldo(r, privado, WidgetPlano.Variacao.COMPACTO)
    }
}

/** Saldo, entradas/saídas em uma linha e os dois botões. */
@androidx.compose.runtime.Composable
private fun Enxuto(context: Context, r: WidgetResumo, privado: Boolean, raiz: GlanceModifier) {
    Column(modifier = raiz.padding(horizontal = 12.dp, vertical = 5.dp).clickable(abrirApp(context))) {
        Text(context.getString(R.string.widget_label_saldo), maxLines = 1, style = estilo(R.color.widget_text_sec, 11))
        Saldo(r, privado, WidgetPlano.Variacao.ENXUTO)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(WidgetFormato.chipEntradas(r.receitasMes, privado), maxLines = 1, style = estilo(R.color.widget_positive, 11, negrito = true))
            Spacer(GlanceModifier.width(10.dp))
            Text(WidgetFormato.chipSaidas(r.despesasMes, privado), maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = estilo(R.color.widget_negative, 11, negrito = true))
            BotaoOlho(context, privado)
        }
        Spacer(GlanceModifier.defaultWeight())
        BotoesLancar(context, 28.dp)
    }
}

/** Cabeçalho com olho/atualizar, saldo, chips de entradas/saídas, [próxima conta] e os botões. */
@androidx.compose.runtime.Composable
private fun Medio(context: Context, r: WidgetResumo, privado: Boolean, plano: WidgetPlano.Plano, instanteMs: Long, raiz: GlanceModifier) {
    Column(modifier = raiz.padding(10.dp).clickable(abrirApp(context))) {
        Cabecalho(context, privado, comAtualizar = true)
        Saldo(r, privado, WidgetPlano.Variacao.MEDIO)
        Spacer(GlanceModifier.height(4.dp))
        Chips(r, privado)
        if (plano.proxima) {
            r.proximas.firstOrNull()?.let { c ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinhaVencimento(c, privado, instanteMs, GlanceModifier.defaultWeight())
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        BotoesLancar(context, 30.dp)
    }
}

/** Tudo do médio + próximos vencimentos, acesso rápido (extrato, cartões, metas, app) e "atualizado às". */
@androidx.compose.runtime.Composable
private fun Grande(context: Context, r: WidgetResumo, privado: Boolean, plano: WidgetPlano.Plano, instanteMs: Long, raiz: GlanceModifier) {
    Column(modifier = raiz.padding(10.dp).clickable(abrirApp(context))) {
        Cabecalho(context, privado, comAtualizar = true)
        Saldo(r, privado, WidgetPlano.Variacao.GRANDE)
        Spacer(GlanceModifier.height(4.dp))
        Chips(r, privado)
        Spacer(GlanceModifier.height(6.dp))
        BotoesLancar(context, 30.dp)
        Spacer(GlanceModifier.height(6.dp))

        Text(context.getString(R.string.widget_proximos_vencimentos), maxLines = 1, style = estilo(R.color.widget_text_sec, 11, negrito = true))
        if (r.proximas.isEmpty()) {
            Text(context.getString(R.string.widget_sem_vencimentos), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp), style = estilo(R.color.widget_text_sec, 11))
        } else {
            r.proximas.take(plano.vencimentos).forEach { c ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinhaVencimento(c, privado, instanteMs, GlanceModifier.defaultWeight())
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        if (plano.atalhos && r.atalhos.isNotEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                r.atalhos.take(if (plano.rotulos) 2 else 1).forEachIndexed { i, a ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Pilula(
                        texto = "↻ ${a.descricao.take(10)} ${WidgetFormato.valorChip(a.valor, privado)}",
                        bg = R.drawable.widget_bg_tile, fg = R.color.widget_accent,
                        acao = repetirAtalho(context, a), altura = 28.dp, modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }

        AcessoRapido(context, plano.rotulos)
        if (plano.atualizado) {
            Text(
                context.getString(R.string.widget_atualizado_as, WidgetFormato.horaAtualizacao(instanteMs)),
                maxLines = 1, modifier = GlanceModifier.fillMaxWidth().padding(top = 3.dp),
                style = estilo(R.color.widget_text_sec, 10, centro = true)
            )
        }
    }
}

// ───────────────────────── Peças ─────────────────────────

@androidx.compose.runtime.Composable
private fun Saldo(r: WidgetResumo, privado: Boolean, variacao: WidgetPlano.Variacao) {
    val texto = WidgetFormato.saldo(r.saldoTotal, privado, variacao)
    Text(
        text = texto,
        maxLines = 1,
        style = estilo(if (r.saldoTotal < 0) R.color.widget_negative else R.color.widget_positive, WidgetFormato.fonteSaldoSp(texto, variacao), negrito = true)
    )
}

@androidx.compose.runtime.Composable
private fun Cabecalho(context: Context, privado: Boolean, comAtualizar: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(context.getString(R.string.widget_label_saldo), maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = estilo(R.color.widget_text_sec, 11, negrito = true))
        BotaoOlho(context, privado)
        if (comAtualizar) {
            Box(
                modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    ImageProvider(R.drawable.ic_w_refresh), context.getString(R.string.widget_atualizar),
                    modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(cor(R.color.widget_text_sec))
                )
            }
        }
    }
}

/** Alterna ocultar/mostrar valores (a mesma preferência de modo privado do app). */
@androidx.compose.runtime.Composable
private fun BotaoOlho(context: Context, privado: Boolean) {
    Box(modifier = GlanceModifier.size(24.dp).clickable(actionRunCallback<AlternarPrivacidadeAction>()), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(if (privado) R.drawable.ic_w_eye_off else R.drawable.ic_w_eye),
            context.getString(if (privado) R.string.widget_mostrar else R.string.widget_ocultar),
            modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(cor(R.color.widget_text_sec))
        )
    }
}

@androidx.compose.runtime.Composable
private fun Chips(r: WidgetResumo, privado: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Chip(WidgetFormato.chipEntradas(r.receitasMes, privado), R.drawable.widget_bg_chip_pos, R.color.widget_positive, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        Chip(WidgetFormato.chipSaidas(r.despesasMes, privado), R.drawable.widget_bg_chip_neg, R.color.widget_negative, GlanceModifier.defaultWeight())
    }
}

@androidx.compose.runtime.Composable
private fun Chip(texto: String, @DrawableRes bg: Int, @ColorRes fg: Int, modifier: GlanceModifier) {
    Box(
        modifier = modifier.height(22.dp).background(ImageProvider(bg), ContentScale.FillBounds),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, maxLines = 1, style = estilo(fg, 11, negrito = true, centro = true))
    }
}

@androidx.compose.runtime.Composable
private fun BotoesLancar(context: Context, altura: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Pilula(
            context.getString(R.string.widget_btn_despesa), R.drawable.widget_bg_btn_desp, R.color.widget_btn_desp_fg,
            lancarRapido(context, RoteamentoLancamento.Modo.DESPESA), altura, GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(6.dp))
        Pilula(
            context.getString(R.string.widget_btn_receita), R.drawable.widget_bg_btn_rec, R.color.widget_btn_rec_fg,
            lancarRapido(context, RoteamentoLancamento.Modo.RECEITA), altura, GlanceModifier.defaultWeight()
        )
    }
}

@androidx.compose.runtime.Composable
private fun Pilula(texto: String, @DrawableRes bg: Int, @ColorRes fg: Int, acao: Action, altura: Dp, modifier: GlanceModifier) {
    Box(
        modifier = modifier.height(altura).background(ImageProvider(bg), ContentScale.FillBounds).clickable(acao),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, maxLines = 1, style = estilo(fg, 12, negrito = true, centro = true))
    }
}

/** "descrição · dia · valor" de uma conta a vencer; atrasadas em cor de alerta. */
@androidx.compose.runtime.Composable
private fun LinhaVencimento(c: ContaWidget, privado: Boolean, agoraMs: Long, modifier: GlanceModifier) {
    val destaque = if (c.atrasada) R.color.widget_negative else R.color.widget_text
    Text(c.descricao, maxLines = 1, modifier = modifier, style = estilo(destaque, 11))
    Text(
        WidgetFormato.rotuloDia(c, agoraMs), maxLines = 1,
        modifier = GlanceModifier.padding(horizontal = 6.dp),
        style = estilo(if (c.atrasada) R.color.widget_negative else R.color.widget_text_sec, 11, negrito = c.atrasada)
    )
    Text(WidgetFormato.valorConta(c.valor, privado), maxLines = 1, style = estilo(destaque, 11, negrito = true))
}

@androidx.compose.runtime.Composable
private fun AcessoRapido(context: Context, rotulos: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Atalho(context, R.drawable.ic_w_extrato, R.string.widget_acesso_extrato, rotulos, abrirDestino(context, DestinoApp.EXTRATO), GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(4.dp))
        Atalho(context, R.drawable.ic_w_cartao, R.string.widget_acesso_cartoes, rotulos, abrirDestino(context, DestinoApp.CARTOES), GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(4.dp))
        Atalho(context, R.drawable.ic_w_meta, R.string.widget_acesso_metas, rotulos, abrirDestino(context, DestinoApp.METAS), GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(4.dp))
        Atalho(context, R.drawable.ic_w_app, R.string.widget_acesso_app, rotulos, abrirApp(context), GlanceModifier.defaultWeight())
    }
}

@androidx.compose.runtime.Composable
private fun Atalho(context: Context, @DrawableRes icone: Int, rotulo: Int, comRotulo: Boolean, acao: Action, modifier: GlanceModifier) {
    val nome = context.getString(rotulo)
    Box(
        modifier = modifier.height(34.dp).background(ImageProvider(R.drawable.widget_bg_tile), ContentScale.FillBounds).clickable(acao),
        contentAlignment = Alignment.Center
    ) {
        if (comRotulo) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(icone), null, modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(cor(R.color.widget_accent)))
                Text(nome, maxLines = 1, style = estilo(R.color.widget_text, 10, centro = true))
            }
        } else {
            Image(ImageProvider(icone), nome, modifier = GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(cor(R.color.widget_accent)))
        }
    }
}
