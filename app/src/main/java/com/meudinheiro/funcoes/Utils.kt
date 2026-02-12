package com.meudinheiro.funcoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meudinheiro.componentes.PremiumDarkBlue
import com.meudinheiro.componentes.TextWhite
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.notif.BackupReminderWorker
import java.util.concurrent.TimeUnit

@Composable
fun ChartLegendItem(color: Color, label: String, value: Double, isPrivate: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextWhite.copy(0.6f))
            Text(
                formatarMoedaBR(value, isPrivate),
                fontSize = 12.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IndicatorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}

// --- Gráfico de Pizza (Distribuicao de Gastos) mantido e organizado ---
@Composable
fun PremiumPieChart(
    despesas: List<DespesasDomain>,
    isPrivate: Boolean = false
) {
    val gastosPorCategoria = despesas
        .filter { it.tipo == TipoDespesa.DEBITO }
        .groupBy { it.categoria }
        .mapValues { it.value.sumOf { d -> d.valor } }

    val totalGeral = gastosPorCategoria.values.sum()
    val listaCores = listOf(
        Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFFFD54F),
        Color(0xFFFF8A80), Color(0xFFB388FF), Color(0xFF80D8FF)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Distribuição de Gastos",
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                var startAngle = -90f
                gastosPorCategoria.entries.forEachIndexed { index, entry ->
                    val sweepAngle = (entry.value / totalGeral).toFloat() * 360f
                    drawArc(
                        color = listaCores[index % listaCores.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
                drawCircle(color = PremiumDarkBlue, radius = size.minDimension / 4)
            }
        }

        Spacer(Modifier.height(24.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = 3
        ) {
            gastosPorCategoria.entries.forEachIndexed { index, entry ->
                ChartLegendItem(
                    color = listaCores[index % listaCores.size],
                    label = entry.key,
                    value = entry.value,
                    isPrivate = isPrivate
                )
            }
        }
    }
}

@Composable
fun HorizontalBalanceBar(
    label: String,
    value: Double,
    progress: Float,
    color: Color,
    isPrivate: Boolean
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f))

            // --- VALOR DA BARRA ANIMADO ---
            AnimatedContent(targetState = value) { valor ->
                Text(
                    text = formatarMoedaBR(valor, isPrivate),
                    fontSize = 9.sp,
                    color = Color.White.copy(0.8f)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(0.05f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun lembrarEstadoPerformance(): Boolean {
    val context = LocalContext.current
    var isLowPower by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val manager = context?.getSystemService(Context.POWER_SERVICE) as PowerManager
                // Detecta se a economia do sistema está ativa ou bateria < 15%
                isLowPower = manager.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return isLowPower
}

