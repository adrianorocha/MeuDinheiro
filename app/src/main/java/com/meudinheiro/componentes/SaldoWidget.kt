package com.meudinheiro.componentes

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.meudinheiro.MainActivity
import com.meudinheiro.R
import com.meudinheiro.funcoes.formatarMoedaBR

class SaldoWidget : GlanceAppWidget() {
    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // 🚀 LENDO TUDO ANTES DE MONTAR A UI
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val saldo = prefs.getFloat("saldo_atual", 0.0f)
        val qtdMetas = prefs.getInt("quantidade_metas", 0)

        // Criamos uma lista local para o Compose do Glance não se perder
        val listaMetasLocal = mutableListOf<MetaWidgetData>()
        for (i in 0 until qtdMetas) {
            listaMetasLocal.add(
                MetaWidgetData(
                    nome = prefs.getString("meta_${i}_nome", "") ?: "",
                    id = prefs.getString("meta_${i}_id", "") ?: "",
                    vMeta = prefs.getFloat("meta_${i}_valor_meta", 1f),
                    vAlcancado = prefs.getFloat("meta_${i}_valor_alcancado", 0f)
                )
            )
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xCC1B263B)) // Vidro translúcido
                        .padding(12.dp)
                ) {
                    // --- CABEÇALHO (SALDO) ---
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(provider = ImageProvider(R.drawable.sim_chip_2), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                        Spacer(GlanceModifier.size(6.dp))
                        Text("SALDO", style = TextStyle(color = ColorProvider(Color.White.copy(0.5f)), fontSize = 10.sp))
                        Spacer(GlanceModifier.defaultWeight())
                        Image(
                            provider = ImageProvider(R.drawable.ic_popup_sync),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshAction>())
                        )
                    }

                    Text(
                        text = formatarMoedaBR(saldo.toDouble(), false),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(0xFF69F0AE)), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    )

                    Spacer(GlanceModifier.height(8.dp))

                    // --- LISTA DE METAS ---
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        if (listaMetasLocal.isEmpty()) {
                            Text("Nenhuma meta", style = TextStyle(color = ColorProvider(Color.White.copy(0.4f)), fontSize = 11.sp))
                        } else {
                            listaMetasLocal.forEach { meta ->
                                val progresso = ((meta.vAlcancado / meta.vMeta) * 100).toInt().coerceIn(0, 100)
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎯 ${meta.nome}",
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight(),
                                        style = TextStyle(color = ColorProvider(Color(0xFF00E5FF)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    )
                                    Text(text = "$progresso%", style = TextStyle(color = ColorProvider(Color.White.copy(0.7f)), fontSize = 10.sp))
                                }
                            }
                        }
                    }

                    Spacer(GlanceModifier.defaultWeight())

                    // --- BOTÃO NOVA DESPESA ---
                    Button(
                        text = "+ NOVA DESPESA",
                        onClick = actionStartActivity<MainActivity>(), // Simplificado
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ColorProvider(Color(0xFF00E5FF)),
                            contentColor = ColorProvider(Color(0xFF1B263B))
                        ),
                        modifier = GlanceModifier.fillMaxWidth().height(36.dp)
                    )
                }
            }
        }
    }
}

// Classe auxiliar simples para carregar os dados
data class MetaWidgetData(val nome: String, val id: String, val vMeta: Float, val vAlcancado: Float)