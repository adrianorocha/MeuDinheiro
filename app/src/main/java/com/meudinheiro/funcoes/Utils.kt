package com.meudinheiro.funcoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.PowerManager
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.meudinheiro.R
import com.meudinheiro.componentes.MonthPill
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.PieChartData
import com.meudinheiro.ui.theme.CardGlass
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.atan2
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

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
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return isLowPower
}

fun compartilharComprovante(
    context: Context,
    despesa: Despesa,
    nomeCartao: String?,
    nomeConta: String

) {
    // 1. Gera o Bitmap (Usando a função Ultra Premium que criamos)
    val bitmap = gerarBitmapComprovanteUltraPremium(context, despesa, nomeCartao, nomeConta)

    // 2. Salva temporariamente na pasta de cache
    val imagesFolder = File(context.cacheDir, "images")
    imagesFolder.mkdirs()
    val file = File(imagesFolder, "comprovante_${despesa.id}.png")
    val stream = FileOutputStream(file)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.close()

    // 3. Pega a URI segura via FileProvider
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // 4. Dispara a intent de compartilhamento do Android
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar Recibo Blu Macaw"))
}

fun gerarBitmapComprovanteUltraPremium(
    ctx: Context,
    despesa: Despesa,
    cartaoNome: String?,
    contaNome: String
): Bitmap {
    val width = 850 // Ligeiramente mais largo para elegância
    val height = 1500 // Mais altura para os detalhes refinados
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Cores Ultra Premium (Sincronizadas com o App)
    val colorBackground = android.graphics.Color.parseColor("#0D1B2A")
    val colorCard = android.graphics.Color.parseColor("#1B263B")
    val colorNeonCyan = android.graphics.Color.parseColor("#00E5FF")
    val colorNeonGreen = android.graphics.Color.parseColor("#69F0AE")
    val colorLabelText = android.graphics.Color.parseColor("#8E9BAE")
    val colorValueText = android.graphics.Color.WHITE
    val colorDivider = android.graphics.Color.parseColor("#3A4B66")

    canvas.drawColor(colorBackground)

    // 2. Fundo do "Cartão" com Efeito Holográfico Suave
    val paintCard = android.graphics.Paint().apply {
        color = colorCard
        isAntiAlias = true
        // Efeito de sombra suave para profundidade
        setShadowLayer(30f, 0f, 15f, android.graphics.Color.parseColor("#99000000"))
    }
    val cardRect = android.graphics.RectF(40f, 40f, width - 40f, height - 40f)
    canvas.drawRoundRect(cardRect, 32f, 32f, paintCard)

    // --- PAINTS ---
    val paintAmount = android.graphics.Paint().apply {
        color = colorValueText
        textSize = 90f // Valor enorme e imponente
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        // Brilho suave no valor
        setShadowLayer(15f, 0f, 0f, colorNeonCyan)
    }

    val paintLabel = android.graphics.Paint().apply {
        color = colorLabelText
        textSize = 28f
        textAlign = android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }

    val paintValue = android.graphics.Paint().apply {
        color = colorValueText
        textSize = 30f
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        textAlign = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }

    val paintBrand = android.graphics.Paint().apply {
        color = colorNeonCyan
        textSize = 20f
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    val paintSubtitle = android.graphics.Paint().apply {
        color = colorLabelText
        textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    // --- DESENHO DO CONTEÚDO ---

    // 🏆 BRANDING NO TOPO (Logotipo e Nome)
    val logoDrawable = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.meu_dinheiro)
    logoDrawable?.let {
        val logoSize = 60
        it.setBounds(
            (width / 2) - (logoSize / 2),
            100,
            (width / 2) + (logoSize / 2),
            100 + logoSize
        )
        // Opcional: Aplicar filtro de cor Neon Cyan no logotipo
        androidx.core.graphics.drawable.DrawableCompat.setTint(it, colorNeonCyan)
        it.draw(canvas)
    }

    val paintTitle = android.graphics.Paint(paintBrand)
        .apply { textSize = 26f; color = android.graphics.Color.WHITE }
    canvas.drawText("Blu Macaw Lab's", width / 2f, 190f, paintBrand)
    canvas.drawText("Comprovante Detalhado", width / 2f, 230f, paintTitle)

    // 💰 VALOR IMPONENTE
    canvas.drawText(formatarMoedaBR(despesa.valor, false), width / 2f, 380f, paintAmount)

    // ✅ STATUS TAG PREMIUM
    val paintStatus = android.graphics.Paint(paintValue).apply {
        color = colorNeonGreen
        textSize = 24f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(
        if (despesa.pago) "PAGAMENTO CONFIRMADO" else "AGUARDANDO PAGAMENTO",
        width / 2f,
        440f,
        paintStatus
    )

    // LINHA DIVISORA ELEGANTE
    val paintDivider = android.graphics.Paint().apply {
        color = colorDivider
        strokeWidth = 2f
        isAntiAlias = true
    }
    canvas.drawLine(80f, 500f, width - 80f, 500f, paintDivider)

    // --- DETALHES ---
    var startY = 580f
    val lineSpacing = 80f // Mais espaço para clareza
    val leftX = 80f
    val rightX = width - 80f

    // Função auxiliar para desenhar linha com Ícone e Detalhe
    fun drawDetailLine(
        canvas: android.graphics.Canvas,
        label: String,
        value: String,
        y: Float,
        iconDrawable: android.graphics.drawable.Drawable?
    ) {
        // Desenha Ícone sutil
        iconDrawable?.let {
            val iSize = 35
            it.setBounds(
                leftX.toInt(),
                (y - iSize + 5).toInt(),
                (leftX + iSize).toInt(),
                (y + 5).toInt()
            )
            androidx.core.graphics.drawable.DrawableCompat.setTint(it, colorLabelText)
            it.draw(canvas)
        }
        canvas.drawText(label, leftX + 50f, y, paintLabel)
        canvas.drawText(value, rightX, y, paintValue)
    }

    val dataFormatada =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(despesa.data)
    drawDetailLine(canvas, "Data do Pagamento", dataFormatada, startY, null)

    startY += lineSpacing
    // Detalhe inteligente: Mostra Cartão se for crédito, ou Conta se for débito
    if (cartaoNome != null) {
        drawDetailLine(canvas, "Cartão de Crédito", cartaoNome, startY, null)
    } else {
        drawDetailLine(canvas, "Origem do Saldo", contaNome, startY, null)
    }

    startY += lineSpacing
    val authId =
        "MD-${despesa.id.toString().padStart(6, '0')}-${despesa.data.time.toString().takeLast(4)}"
    drawDetailLine(canvas, "Autenticação MD", authId, startY, null)

    // SEGUNDA DIVISORA
    canvas.drawLine(80f, startY + 80f, width - 80f, startY + 80f, paintDivider)

    // --- QR CODE ---
    val textoQR = "{\"app\":\"BluMacaw\",\"id\":${despesa.id},\"valor\":${despesa.valor}}"
    val qrSize = 340
    val qrBitmap = gerarBitmapQRCode(textoQR, qrSize)

    val qrLeft = (width - qrSize) / 2f
    val qrTop = startY + 120f
    val qrBgRect = android.graphics.RectF(
        qrLeft - 10f,
        qrTop - 10f,
        qrLeft + qrSize + 10f,
        qrTop + qrSize + 10f
    )
    val paintQrBg =
        android.graphics.Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
    canvas.drawRoundRect(qrBgRect, 16f, 16f, paintQrBg)

    canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

    paintSubtitle.textSize = 20f
    canvas.drawText(
        "Escaneie para validar a autenticidade",
        width / 2f,
        qrTop + qrSize + 60f,
        paintSubtitle
    )

    val paintBrand2 = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00E5FF")
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    // BRANDING FINAL
    canvas.drawText("Gerado por Meu Dinheiro", width / 2f, height - 80f, paintBrand2)

    return bitmap

}

fun gerarBitmapQRCode(
    conteudo: String,
    tamanho: Int,
    logo: Bitmap? = null // Adicionado como opcional para manter compatibilidade
): Bitmap {
    // 1. Configurações para permitir o logo no centro (Erro nível H para suportar sobreposição)
    val hints = HashMap<EncodeHintType, Any>()
    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
    hints[EncodeHintType.MARGIN] = 1

    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(conteudo, BarcodeFormat.QR_CODE, tamanho, tamanho, hints)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Fundo Branco Arredondado
    val paintBg =
        android.graphics.Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
    canvas.drawRoundRect(RectF(0f, 0f, width.f, height.f), 40f, 40f, paintBg)

    // 2. Configuração do Gradiente (Laranja -> Roxo/Rosa)
    val paint = Paint().apply {
        isAntiAlias = true
        shader = LinearGradient(
            0f, 0f, width.f, height.f,
            intArrayOf(
                android.graphics.Color.parseColor("#F58529"), // Laranja
                android.graphics.Color.parseColor("#DD2A7B"), // Rosa
                android.graphics.Color.parseColor("#8134AF")  // Roxo
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    val moduleSize = width / bitMatrix.width.toFloat()
    val dotRadius =
        moduleSize / 2 * 0.85f // Reduzi um pouco para dar o efeito de "bolinhas" separadas

    // 3. Desenho dos módulos
    for (x in 0 until width) {
        for (y in 0 until height) {
            if (bitMatrix.get(x, y)) {
                // Pular a área central para o Logo (aproximadamente 20% do centro)
                val centralLimit = bitMatrix.width * 0.2
                val center = bitMatrix.width / 2
                if (logo != null &&
                    x > (center - centralLimit) && x < (center + centralLimit) &&
                    y > (center - centralLimit) && y < (center + centralLimit)
                ) {
                    continue
                }

                // Desenha bolinhas em vez de quadrados
                val cx = x * moduleSize + moduleSize / 2
                val cy = y * moduleSize + moduleSize / 2
                canvas.drawCircle(cx, cy, dotRadius, android.graphics.Paint())
            }
        }
    }

    // 4. Desenho do Logo Central (Estilo Premium)
    logo?.let {
        val logoSize = (tamanho * 0.22).toInt() // Tamanho proporcional
        val left = (tamanho - logoSize) / 2
        val top = (tamanho - logoSize) / 2
        val rect = Rect(left, top, left + logoSize, top + logoSize)

        // Fundo branco do logo para não misturar com os pontos
        val paintLogoBg = android.graphics.Paint()
            .apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
        val logoBgRect = RectF(
            (left - 5).f, (top - 5).f,
            (left + logoSize + 5).f, (top + logoSize + 5).f
        )
        canvas.drawRoundRect(logoBgRect, 20f, 20f, paintLogoBg)

        canvas.drawBitmap(it, null, rect, null)
    }

    return bitmap
}

// Extensão apenas para facilitar a escrita
private val Int.f get() = this.toFloat()

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
            Icon(
                Icons.Default.CheckCircle,
                "Sucesso",
                tint = Color.Green,
                modifier = Modifier.size(100.dp)
            )
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
        Canvas(
            modifier = Modifier
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
                    color = if (selectedIndex == -1 || isSelected) fatia.cor else fatia.cor.copy(
                        alpha = 0.3f
                    ),
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
                    color = White
                )
            } else {
                // Mostra o total geral
                Text("TOTAL", fontSize = 8.sp, color = White.copy(0.5f))
                Text(
                    text = formatarMoedaBR(total.toDouble(), isPrivate),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = White
                )
            }
        }
    }
}

/*@Composable
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
}*/

@Composable
fun HorizontalBalanceBarSlim(
    label: String,
    value: Double,
    progress: Float,
    color: Color,
    isPrivate: Boolean,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .shimmerEffect()
                )
            } else {
                Text(
                    text = label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = White.copy(alpha = 0.4f)
                )
                Text(
                    text = formatarMoedaBR(value, isPrivate),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        // A nossa nova barra neon
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .shimmerEffect()
            )
        } else {
            NeonProgressBar(
                progress = progress,
                primaryColor = color
            )
        }
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
                color = White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Valor logo abaixo
        Text(
            text = formatarMoedaBR(fatia.valor, isPrivate),
            color = White,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "DISTRIBUIÇÃO POR CATEGORIA",
            color = White.copy(alpha = 0.4f),
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
            color = White.copy(alpha = 0.4f),
            fontSize = 10.sp
        )
    }

}

fun obterCorDaCategoria(categoria: String): Color {
    return when (categoria.lowercase().trim()) {
        "alimentação", "comida", "mercado" -> Color(0xFFFFB74D) // Laranja
        "transporte", "combustível", "uber" -> Color(0xFF64B5F6) // Azul
        "lazer", "viagem", "cinema" -> Color(0xFFBA68C8) // Roxo
        "saúde", "farmácia", "médico" -> Color(0xFFE57373) // Vermelho
        "contas", "fixas", "luz", "água" -> Color(0xFF4FC3F7) // Ciano
        "poupado", "metas", "investimento" -> Color(0xFF69F0AE) // Verde Blu Macaw
        "educação", "cursos" -> Color(0xFFFFF176) // Amarelo
        else -> Color(0xFF90A4AE) // Cinza Azulado (Padrão)
    }
}

@Composable
fun NeonProgressBar(
    progress: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp) // Um pouco mais grossa para o gradiente aparecer
            .clip(CircleShape)
            .background(White.copy(alpha = 0.05f)) // Trilho de fundo
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * animProgress

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.7f), // Cor suave no início
                        primaryColor                    // Neon puro no fim
                    )
                ),
                size = size.copy(width = width),
                cornerRadius = CornerRadius(100f, 100f)
            )
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    // Cores otimizadas para o seu tema Dark (fundo do card é 1E2B3E)
    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF2A3B52), // Escuro
                Color(0xFF405675), // Brilho mais claro
                Color(0xFF2A3B52), // Escuro
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + 400f, 0f) // Largura do feixe de luz
        ),
        shape = RoundedCornerShape(8.dp) // Cantos arredondados
    )
}

@Composable
fun HorizontalMonthSelector(
    selectedMonth: Int, // 1 a 12
    onMonthSelected: (Int) -> Unit
) {
    val meses = listOf(
        "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    val listState = rememberLazyListState()

    // Rola para o mês selecionado ao iniciar
    LaunchedEffect(Unit) {
        listState.scrollToItem(maxOf(0, selectedMonth - 2))
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        itemsIndexed(meses) { index, nome ->
            val mesNumero = index + 1
            MonthPill(
                mes = nome,
                isSelected = selectedMonth == mesNumero,
                onClick = { onMonthSelected(mesNumero) }
            )
        }
    }
}

@Composable
fun FloatingActionButtonMeuDinheiro(
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    // Animação de escala (diminui levemente ao tocar)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    FloatingActionButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .padding(16.dp)
            .size(64.dp)
            .scale(scale)
            .graphicsLayer {
                // Sombra neon sutil
                shadowElevation = 12.dp.toPx()
                shape = CircleShape
                clip = true
            },
        containerColor = Color(0xFF69F0AE), // Verde Blu Macaw
        contentColor = Color(0xFF0F172A),    // Ícone escuro para contraste
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Adicionar Despesa",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun EmptyStateSection(
    onAddClick: () -> Unit
) {
    // Animação de flutuação para o ícone
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val translateY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateY"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ícone Flutuante
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(y = translateY.dp),
            contentAlignment = Alignment.Center
        ) {
            // Um círculo de fundo bem suave
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(White.copy(alpha = 0.05f), CircleShape)
            )
            Text("💸", fontSize = 48.sp) // Ou um Icon(Icons.Rounded.History)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tudo limpo por aqui!",
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Que tal registrar seu primeiro gasto ou entrada para ver a mágica acontecer?",
            color = White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botão de Incentivo
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, White.copy(alpha = 0.3f))
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Novo Lançamento", color = White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PremiumSnackbar(data: SnackbarData) {
    // Split: "Título | Subtítulo | Tipo (Sucesso/Erro)"
    val partes = data.visuals.message.split("|")
    val titulo = partes.getOrNull(0)?.trim() ?: ""
    val subtitulo = partes.getOrNull(1)?.trim() ?: ""
    val isErro = partes.getOrNull(2)?.trim()?.contains("Erro", ignoreCase = true) ?: false

    val corDestaque = if (isErro) NeonRed else NeonCyan

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 90.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. O Card Principal com Gradiente Escuro e Borda Glow
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp), // Espaço para o avatar "sobrar" em cima
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            color = Color.Transparent // Necessário para o brush background aparecer
        ) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(CardGlass, DeepSpaceBlue),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
                    .border(1.dp, corDestaque.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, top = 20.dp, bottom = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Espaço reservado para o avatar flutuante (empurra o texto pra direita)
                Spacer(modifier = Modifier.width(68.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    if (subtitulo.isNotEmpty()) {
                        Text(
                            text = subtitulo,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // 2. O Avatar Sobreposto (Hanging Avatar com Badge)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp) // Alinhamento lateral que o card respeita
        ) {
            // Foto/Avatar Principal
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = DeepSpaceBlue,
                border = BorderStroke(1.5.dp, corDestaque.copy(alpha = 0.8f)), // Borda Neon
                shadowElevation = 8.dp
            ) {
                Icon(
                    imageVector = if (isErro) Icons.Default.WarningAmber else Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = corDestaque,
                    modifier = Modifier.padding(14.dp)
                )
            }

            // 💡 TOQUE PREMIUM: Mini Badge de Status no canto inferior direito
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp) // Joga o badge metade pra fora do avatar
                    .background(corDestaque, CircleShape)
                    .border(2.dp, DeepSpaceBlue, CircleShape), // Borda escura para dar contraste
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isErro) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = null,
                    tint = DeepSpaceBlue, // Ícone escuro sobre fundo claro
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun obterResIdPelaPic(picName: String?): Int {
    val context = LocalContext.current
    val nomeLimpo = picName?.lowercase() ?: "ic_default" // Fallback se for nulo

    // Tenta encontrar o ID do drawable pelo nome da String
    val resId = context.resources.getIdentifier(
        nomeLimpo,
        "drawable",
        context.packageName
    )

    // Se não encontrar (resId == 0), retorna um ícone padrão do seu projeto
    return if (resId != 0) resId else R.drawable.sim_chip_2 // 👈 Use um ícone que você já tenha
}

fun converterVetorParaBitmap(context: Context, drawableId: Int, corTint: Int? = null): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null

    // Opcional: Se quiser pintar o ícone de Neon Cyan antes de ir pra notificação
    corTint?.let { drawable.setTint(it) }

    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1), // Evita crash se o vetor não tiver tamanho fixo
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun obterIconeCategoria(nomeIcone: String?): ImageVector {
    return when (nomeIcone) {
        "ic_casa" -> Icons.Default.Home
        "ic_carro" -> Icons.Default.DirectionsCar
        "ic_comida" -> Icons.Default.Restaurant
        "ic_saude" -> Icons.Default.LocalHospital
        "ic_lazer" -> Icons.Default.SportsEsports
        "ic_compras" -> Icons.Default.ShoppingCart
        "ic_estudo" -> Icons.Default.School
        "ic_default" -> Icons.Default.Category
        else -> Icons.Default.Category // Fallback de segurança se não achar nada
    }
}