package com.meudinheiro.componentes

import android.R
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.meudinheiro.MainActivity
import com.meudinheiro.funcoes.formatarMoedaBR

class SaldoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
        val saldo = prefs.getFloat("saldo_atual", 0.0f)
        val nomeMeta = prefs.getString("nome_meta", "Nenhuma Meta") ?: "Nenhuma Meta"
        val idMeta = prefs.getString("id_meta", "") ?: ""

        val valorMeta = prefs.getFloat("valor_meta", 1.0f)
        val valorAlcancado = prefs.getFloat("valor_alcancado", 0.0f)

        // 2. Calcule a porcentagem (entre 0.0 e 1.0)
        val progresso = (valorAlcancado / valorMeta).coerceIn(0f, 1f)
        "${(progresso * 100).toInt()}%"

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(Color(0xFF1E2B3E))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Saldo", style = TextStyle(
                                color = ColorProvider(
                                    day = Color.White,
                                    night = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        )
                        Spacer(GlanceModifier.defaultWeight())

                        // 1. BOTÃO ATUALIZAR
                        Image(
                            provider = ImageProvider(R.drawable.ic_popup_sync),
                            contentDescription = null,
                            modifier = GlanceModifier.size(20.dp)
                                .clickable(actionRunCallback<RefreshAction>())
                        )
                    }

                    Text(
                        text = formatarMoedaBR(saldo.toDouble(), false),
                        style = TextStyle(
                            color = ColorProvider(
                                day = Color(0xFF69F0AE),
                                night = Color(0xFF69F0AE).copy(alpha = 0.6f)
                            ),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(GlanceModifier.height(8.dp))

                    // 2. ADICIONAR DESPESA RÁPIDA (Via Intent Extra)
                    val intentAdd = Intent(context, MainActivity::class.java).apply {
                        action = "ACTION_QUICK_ADD"
                    }
                    Button("+ Despesa", onClick = actionStartActivity(intentAdd))

                    // 3. META ESPECÍFICA (Via Deep Link)
                    val intentMeta = Intent(
                        Intent.ACTION_VIEW,
                        "meudinheiro://meta/$idMeta".toUri(),
                        context,
                        MainActivity::class.java
                    )
                    Box(modifier = GlanceModifier.clickable(actionStartActivity(intentMeta))) {
                        Text(
                            text = "Ver Meta: $nomeMeta", style = TextStyle(
                                color = ColorProvider(
                                    day = Color(0xFF69F0AE),
                                    night = Color(0xFF69F0AE).copy(alpha = 0.6f)
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}