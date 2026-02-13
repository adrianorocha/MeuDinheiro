package com.meudinheiro.funcoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.meudinheiro.data.PieChartData
import com.meudinheiro.data.TipoDespesa
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import kotlin.math.atan2

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

fun gerarCorParaCategoria(cat: String): Color {
    return when (cat.lowercase().trim()) {
        "alimentação", "comida" -> Color(0xFFFFB74D) // Laranja
        "transporte", "combustível" -> Color(0xFF64B5F6) // Azul
        "lazer", "viagem" -> Color(0xFFBA68C8) // Roxo
        "saúde", "farmácia" -> Color(0xFFE57373) // Vermelho
        "contas", "fixas" -> Color(0xFF4FC3F7) // Ciano
        "poupado", "metas" -> Color(0xFF69F0AE) // Verde Blu Macaw
        else -> Color(0xFF90A4AE) // Cinza Azulado (Padrão para outros)
    }
}

@Composable
fun PremiumPieChart(
    dados: List<PieChartData>,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false
) {
    val total = dados.sumOf { it.valor }.toFloat()

    // Estado para saber qual fatia foi clicada (-1 significa nenhuma)
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // Animação de entrada
    var animar by remember { mutableStateOf(false) }
    LaunchedEffect(dados) { animar = true }

    val progresso by animateFloatAsState(
        targetValue = if (animar) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing), label = "anim"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(dados) {
                detectTapGestures { offset ->
                    // 1. Calcula o centro e a distância do toque
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY

                    // 2. Transforma (x,y) em ângulo (graus)
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                    // Ajusta para o sistema do Canvas (-90 graus é o topo)
                    angle += 90f
                    if (angle < 0) angle += 360f

                    // 3. Verifica em qual fatia o ângulo caiu
                    var currentStartAngle = 0f
                    dados.forEachIndexed { index, fatia ->
                        val sweepAngle = (fatia.valor.toFloat() / total) * 360f
                        if (angle in currentStartAngle..(currentStartAngle + sweepAngle)) {
                            // Se clicar na mesma, desmarca. Se não, seleciona a nova.
                            selectedIndex = if (selectedIndex == index) -1 else index
                            return@detectTapGestures
                        }
                        currentStartAngle += sweepAngle
                    }
                }
            }
        ) {
            var startAngle = -90f
            dados.forEachIndexed { index, fatia ->
                val sweepAngle = if (total > 0) (fatia.valor.toFloat() / total) * 360f else 0f

                // Se a fatia estiver selecionada, ela fica um pouco mais grossa (Efeito Zoom)
                val isSelected = selectedIndex == index
                val strokeWidth = if (isSelected) 28f else 18f

                drawArc(
                    color = if (selectedIndex == -1 || isSelected) fatia.cor else fatia.cor.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * progresso,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        }

        // --- TEXTO CENTRAL DINÂMICO ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedIndex != -1) {
                // Mostra dados da categoria selecionada
                val item = dados[selectedIndex]
                Text(
                    text = item.categoria.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = item.cor
                )
                Text(
                    text = formatarMoedaBR(item.valor, isPrivate),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            } else {
                // Mostra o total geral
                Text("TOTAL", fontSize = 8.sp, color = Color.White.copy(0.5f))
                Text(
                    text = formatarMoedaBR(total.toDouble(), isPrivate),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun HorizontalBalanceBarSlim(label: String, value: Double, progress: Float, color: Color, isPrivate: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f))
            Text(formatarMoedaBR(value, isPrivate), fontSize = 9.sp, color = Color.White)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun CategoryGridItem(
    fatia: PieChartData,
    isPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Linha superior: Bolinha de cor + Nome
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(fatia.cor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = fatia.categoria,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Valor logo abaixo
        Text(
            text = formatarMoedaBR(fatia.valor,isPrivate),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 14.dp) // Alinha abaixo do nome
        )
    }
}

@OptIn(ExperimentalLayoutApi::class) // Necessário para FlowRow
@Composable
fun CompactCategoryGrid(
    dados: List<PieChartData>,
    isPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "DISTRIBUIÇÃO POR CATEGORIA",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
        )

        // O FlowRow organiza os itens em grade automaticamente
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            maxItemsInEachRow = 4 // Força as 4 colunas
        ) {
            val itemModifier = Modifier.fillMaxWidth(0.25f) // Cada item ocupa 25% da largura (1/4)

            dados.sortedByDescending { it.valor }.forEach { fatia ->
                CategoryGridItem(
                    fatia = fatia,
                    isPrivate = isPrivate,
                    modifier = itemModifier
                )
            }
        }
    }
}

@Composable
fun TrendIndicator(
    valorAtual: Double,
    valorAnterior: Double,
    modifier: Modifier = Modifier
) {
    val diferenca = valorAtual - valorAnterior
    val percentual = if (valorAnterior > 0) (diferenca / valorAnterior) * 100 else 0.0

    // Define a cor e o ícone com base na tendência
    // Nota: Para despesas, "mais" é ruim (vermelho), "menos" é bom (verde).
    val isPositiveTrend = diferenca <= 0
    val color = if (isPositiveTrend) Color(0xFF69F0AE) else Color(0xFFEF5350)
    val icon = if (isPositiveTrend) "↓" else "↑"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$icon ${String.format("%.1f", Math.abs(percentual))}%",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "vs mês ant.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp
        )
    }
}