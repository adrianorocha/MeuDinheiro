package com.meudinheiro.funcoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.meudinheiro.R
import com.meudinheiro.componentes.PremiumDarkBlue
import com.meudinheiro.componentes.TextWhite
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.DespesasDomain
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat


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
fun compartilharComprovante(ctx: Context, bitmap: Bitmap) {
    val cachePath = File(ctx.cacheDir, "images")
    cachePath.mkdirs()
    val file = File(cachePath, "comprovante_${System.currentTimeMillis()}.png")
    val stream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.close()

    val contentUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Compartilhar Comprovante"))
}

fun gerarBitmapComprovante(despesa: Despesa): Bitmap {
    val width = 800
    val height = 1200 // Aumentamos de 1000 para 1200 para caber o QR
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // 1. Fundo
    canvas.drawColor(android.graphics.Color.parseColor("#1E2B3E"))

    val paintText = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // 2. Cabeçalho e Valor (Mantendo seu código)
    paintText.textSize = 40f
    canvas.drawText("Comprovante Meu Dinheiro", width / 2f, 100f, paintText)

    paintText.textSize = 80f
    paintText.color = android.graphics.Color.parseColor("#69F0AE")
    canvas.drawText("R$ ${String.format("%.2f", despesa.valor)}", width / 2f, 280f, paintText)

    // 3. Detalhes
    paintText.textSize = 30f
    paintText.color = android.graphics.Color.LTGRAY
    canvas.drawText("Categoria: ${despesa.categoria}", width / 2f, 400f, paintText)
    canvas.drawText("Data: ${SimpleDateFormat("dd/MM/yyyy").format(despesa.data)}", width / 2f, 460f, paintText)

    // 4. LINHA DIVISÓRIA
    val paintLine = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        strokeWidth = 2f
    }
    canvas.drawLine(100f, 550f, 700f, 550f, paintLine)

    // 5. QR CODE (A MÁGICA)
    val textoQR = "Despesa: ${despesa.descricao} | Valor: ${despesa.valor}"
    val qrBitmap = gerarBitmapQRCode(textoQR, 300)

    // Centraliza o QR Code no Canvas
    val left = (width - 300) / 2f
    val top = 650f
    canvas.drawBitmap(qrBitmap, left, top, null)

    // 6. Rodapé Final
    paintText.textSize = 25f
    paintText.color = android.graphics.Color.GRAY
    canvas.drawText("Escaneie para validar a transação", width / 2f, 1050f, paintText)

    return bitmap
}

fun gerarBitmapQRCode(conteudo: String, tamanho: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho)
    val width = bitMatrix.width
    val height = bitMatrix.height

    // Criamos o bitmap
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

    for (x in 0 until width) {
        for (y in 0 until height) {
            // Usamos o caminho completo para evitar erro de referência
            val cor = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            bitmap.setPixel(x, y, cor)
        }
    }
    return bitmap
}

@Composable
fun SuccessAnimation(onFinished: () -> Unit) {
    // 1. Tenta carregar a composição
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.success_animation))

    // 2. Controla o progresso
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        restartOnPlay = true
    )

    // Log para depuração (verifique no Logcat do Android Studio)
    LaunchedEffect(composition) {
        if (composition == null) {
            println("ERRO: Composição Lottie é nula. Verifique o arquivo em res/raw")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.1f)), // Fundo leve para teste visual
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(200.dp)
            )
        } else {
            // Se o arquivo sumiu, mostra um ícone reserva para o app não ficar vazio
            Icon(Icons.Default.CheckCircle, "Sucesso", tint = Color.Green, modifier = Modifier.size(100.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text("Gasto registrado!", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)

        // Fecha o diálogo quando a animação termina (progress chega em 1.0)
        LaunchedEffect(progress) {
            if (progress >= 1f) {
                delay(1000) // Aguarda 1 segundo após o fim
                onFinished()
            }
        }
    }
}