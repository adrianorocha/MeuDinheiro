package com.meudinheiro.componentes

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.meudinheiro.MainActivity
import com.meudinheiro.R
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.AtalhoWidget
import com.meudinheiro.domain.WidgetResumo
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val Fundo = Color(0xE61B263B)
private val Ciano = Color(0xFF00E5FF)
private val Verde = Color(0xFF69F0AE)
private val Vermelho = Color(0xFFFF8A80)
private val Ambar = Color(0xFFFFD180)

private val TAMANHO_PEQUENO = DpSize(110.dp, 90.dp)
private val TAMANHO_MEDIO = DpSize(180.dp, 110.dp)
private val TAMANHO_GRANDE = DpSize(250.dp, 180.dp)
private val TAMANHO_EXTRA = DpSize(250.dp, 260.dp)

/** Dias à frente considerados "a vencer" no widget. */
private const val JANELA_DIAS = 7

/** O que o widget desenha: o resumo (ou null se o banco falhou) e se os valores devem ficar ocultos. */
private data class EstadoWidget(val resumo: WidgetResumo?, val privado: Boolean)

/**
 * Widget de saldo. Os dados são lidos do banco a cada desenho (sem cópia em SharedPreferences),
 * então ele nunca fica defasado em relação ao app e respeita o modo privado.
 */
class SaldoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(TAMANHO_PEQUENO, TAMANHO_MEDIO, TAMANHO_GRANDE, TAMANHO_EXTRA))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val estado = carregar(context)
        provideContent { GlanceTheme { Conteudo(context, estado) } }
    }

    private suspend fun carregar(context: Context): EstadoWidget = withContext(Dispatchers.IO) {
        val privado = runCatching { UserPreferences(context).privateModeFlow.first() }.getOrDefault(false)
        val resumo = runCatching { MainRepository(context).resumoParaWidget(JANELA_DIAS) }.getOrNull()
        EstadoWidget(resumo, privado)
    }
}

private fun abrirApp(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
)

/** Painel flutuante de lançamento: o app principal não é aberto. */
private fun lancarRapido(context: Context, tipo: TipoDespesa) = actionStartActivity(
    Intent(context, LancamentoRapidoActivity::class.java).apply {
        putExtra(LancamentoRapidoActivity.EXTRA_TIPO, tipo.name)
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

private val DATA_CURTA = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

@androidx.compose.runtime.Composable
private fun Conteudo(context: Context, estado: EstadoWidget) {
    val tamanho = LocalSize.current
    val compacto = tamanho.width < TAMANHO_MEDIO.width
    val medio = tamanho.height >= TAMANHO_MEDIO.height && !compacto
    val grande = tamanho.height >= TAMANHO_GRANDE.height && !compacto
    val extra = tamanho.height >= TAMANHO_EXTRA.height && !compacto
    val resumo = estado.resumo
    val p = estado.privado

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Fundo)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(abrirApp(context))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(provider = ImageProvider(R.drawable.sim_chip_2), contentDescription = null, modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.size(6.dp))
            Text("SALDO", style = TextStyle(color = ColorProvider(Color.White.copy(0.55f)), fontSize = 10.sp))
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_popup_sync),
                contentDescription = "Atualizar",
                modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<RefreshAction>())
            )
        }

        if (resumo == null) {
            Text("Não foi possível ler os dados.", style = TextStyle(color = ColorProvider(Color.White.copy(0.6f)), fontSize = 11.sp))
        } else {
            Text(
                text = formatarMoedaBR(resumo.saldoTotal, p),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(if (resumo.saldoTotal < 0) Vermelho else Verde),
                    fontSize = if (compacto) 17.sp else 21.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            if (medio) {
                Linha("Mês  ▲ ${formatarMoedaBR(resumo.receitasMes, p)}  ▼ ${formatarMoedaBR(resumo.despesasMes, p)}", Color.White.copy(0.75f))
            }
            if (grande) Linha("Hoje  ${formatarMoedaBR(resumo.gastoHoje, p)} gastos", Color.White.copy(0.75f))

            if (resumo.atrasadas > 0 || resumo.aVencer > 0) {
                val partes = buildList {
                    if (resumo.atrasadas > 0) add("${resumo.atrasadas} atrasada(s)")
                    if (resumo.aVencer > 0) add("${resumo.aVencer} a vencer")
                }
                Linha(
                    partes.joinToString(" · ") + if (compacto) "" else " · " + formatarMoedaBR(resumo.valorPendente, p),
                    if (resumo.atrasadas > 0) Vermelho else Ambar
                )
            }
            if (grande) resumo.proximaConta?.let { c ->
                Linha("Próxima: ${c.descricao} · ${DATA_CURTA.format(c.data)} · ${formatarMoedaBR(c.valor, p)}", if (c.atrasada) Vermelho else Color.White.copy(0.75f))
            }

            if (extra) {
                resumo.orcamento?.let { o ->
                    Linha("Orçamento ${o.categoria}: ${o.percentual}%", if (o.percentual >= 100) Vermelho else if (o.percentual >= 80) Ambar else Verde)
                }
                Spacer(GlanceModifier.height(4.dp))
                if (resumo.metas.isEmpty()) {
                    Linha("Nenhuma meta", Color.White.copy(0.4f))
                } else {
                    resumo.metas.forEach { meta ->
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎯 ${meta.nome}",
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = ColorProvider(Ciano), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Text("${meta.percentual}%", style = TextStyle(color = ColorProvider(Color.White.copy(0.75f)), fontSize = 10.sp))
                        }
                    }
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())

        // Atalhos de 1 toque (despesas que você repete): lançam na hora, sem abrir nada.
        if (grande && resumo != null && resumo.atalhos.isNotEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                resumo.atalhos.forEachIndexed { i, a ->
                    if (i > 0) Spacer(GlanceModifier.size(6.dp))
                    Button(
                        text = "↻ ${a.descricao.take(12)} ${formatarMoedaBR(a.valor, false)}",
                        onClick = repetirAtalho(context, a),
                        colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(Color(0x3300E5FF)), contentColor = ColorProvider(Ciano)),
                        modifier = GlanceModifier.defaultWeight().height(30.dp)
                    )
                }
            }
        }

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button(
                text = "+ DESPESA",
                onClick = lancarRapido(context, TipoDespesa.DEBITO),
                colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(Ciano), contentColor = ColorProvider(Color(0xFF1B263B))),
                modifier = GlanceModifier.defaultWeight().height(36.dp)
            )
            if (!compacto) {
                Spacer(GlanceModifier.size(6.dp))
                Button(
                    text = "+ RECEITA",
                    onClick = lancarRapido(context, TipoDespesa.CREDITO),
                    colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(Verde), contentColor = ColorProvider(Color(0xFF1B263B))),
                    modifier = GlanceModifier.defaultWeight().height(36.dp)
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Linha(texto: String, cor: Color) {
    Text(text = texto, maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp), style = TextStyle(color = ColorProvider(cor), fontSize = 10.sp))
}
