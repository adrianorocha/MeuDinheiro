package com.meudinheiro.componentes

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.funcoes.ConsultaNfceRede
import com.meudinheiro.funcoes.CupomLido
import com.meudinheiro.funcoes.LeitorCupomFiscal
import com.meudinheiro.funcoes.OrigemCupom
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** O que o scanner devolve ao formulário: o usuário ainda revisa e salva. */
data class CupomParaFormulario(
    val valorCentavos: Long,
    val descricao: String?,
    /** Só quando a data é exata (SAT / NFC-e v1); nunca a aproximada do AAMM da chave. */
    val dataMs: Long?
)

private val NeonCyanC = Color(0xFF00E5FF)
private val NeonGreenC = Color(0xFF69F0AE)
private val CardBg = Color(0xFF131E29)

/**
 * Scanner de cupom fiscal (R49). Mesmo visual do scanner de boleto (câmera + overlay neon + diálogo).
 * Voltar/cancelar chama [onClose] sem devolver nada: o formulário fica como estava.
 */
@Composable
fun ScannerCupomScreen(
    onResult: (CupomParaFormulario) -> Unit,
    onClose: () -> Unit
) {
    var lido by remember { mutableStateOf<CupomLido?>(null) }
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize()) {
        ScannerCameraPreview(onCodigoDetectado = { codigo, _ ->
            if (lido == null) { // trava na primeira leitura reconhecida
                LeitorCupomFiscal.interpretar(codigo)?.let {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    lido = it
                }
            }
        })

        ScannerNeonOverlay(
            onClose = onClose,
            onToggleFlash = { },
            instrucao = "Aponte para o QR Code ou código de barras do cupom",
            alturaJanelaDp = 260f
        )

        lido?.let { cupom ->
            CupomConfirmacaoDialog(
                cupom = cupom,
                onUsar = onResult,
                onEscanearDeNovo = { lido = null },
                onCancelar = onClose
            )
        }
    }
}

@Composable
private fun CupomConfirmacaoDialog(
    cupom: CupomLido,
    onUsar: (CupomParaFormulario) -> Unit,
    onEscanearDeNovo: () -> Unit,
    onCancelar: () -> Unit
) {
    var valorCentavos by remember(cupom) { mutableStateOf(cupom.valorCentavos) }
    var estabelecimento by remember(cupom) { mutableStateOf(cupom.estabelecimento) }
    var digitado by remember(cupom) { mutableStateOf("") }
    var consultando by remember(cupom) {
        mutableStateOf(cupom.valorCentavos == null && cupom.origem == OrigemCupom.NFCE_V2 && cupom.urlConsulta != null)
    }
    var consultaFalhou by remember(cupom) { mutableStateOf(false) }

    // Melhor esforço: só a URL do QR (https + .gov.br), sem dados do usuário. Nunca abre o link.
    LaunchedEffect(cupom) {
        val url = cupom.urlConsulta
        if (consultando && url != null) {
            val r = ConsultaNfceRede.consultar(url)
            if (r?.valorCentavos != null) valorCentavos = r.valorCentavos
            if (!r?.estabelecimento.isNullOrBlank() && estabelecimento.isNullOrBlank()) estabelecimento = r?.estabelecimento
            consultaFalhou = r?.valorCentavos == null
            consultando = false
        }
    }

    val digitadoCentavos = LeitorCupomFiscal.parseValorCentavos(digitado)
    val valorFinal = valorCentavos ?: digitadoCentavos
    val descricao = estabelecimento?.takeIf { it.isNotBlank() }
        ?: cupom.descricaoSugerida
    val dataTxt = cupom.emissaoMs?.let { ms ->
        val f = SimpleDateFormat(if (cupom.emissaoAproximada) "MM/yyyy" else "dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        f.timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        (if (cupom.emissaoAproximada) "Mês aproximado: " else "") + f.format(Date(ms))
    }

    Dialog(onDismissRequest = onCancelar) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(2.dp, NeonGreenC.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Icon(
                    Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreenC,
                    modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cupom Identificado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    cupom.origem.rotulo, color = Color.White.copy(0.5f), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))

                when {
                    consultando -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = NeonCyanC, strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("Consultando o valor no site da SEFAZ…", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    }
                    valorCentavos != null -> {
                        Text("Valor total", color = Color.White.copy(0.5f), fontSize = 12.sp)
                        Text(
                            NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(Dinheiro.reais(valorCentavos!!)),
                            color = NeonCyanC, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp
                        )
                    }
                    else -> {
                        Text(
                            mensagemSemValor(cupom, consultaFalhou),
                            color = Color(0xFFFFB74D), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = digitado,
                            onValueChange = { novo -> if (novo.length <= 14 && novo.all { it.isDigit() || it == ',' || it == '.' }) digitado = novo },
                            label = { Text("Valor total (R$)", color = Color.White.copy(0.5f)) },
                            prefix = { Text("R$ ", color = NeonGreenC) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = digitado.isNotEmpty() && digitadoCentavos == null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyanC, focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (descricao != null) Linha("Estabelecimento", descricao)
                cupom.cnpj?.let { if (cupom.estabelecimento == null) Linha("CNPJ do emitente", LeitorCupomFiscal.formatarCnpj(it)) }
                cupom.cidade?.let { Linha("Cidade", it) }
                dataTxt?.let { Linha("Data", it) }
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        valorFinal?.let { onUsar(CupomParaFormulario(it, descricao, cupom.emissaoMs.takeIf { _ -> !cupom.emissaoAproximada })) }
                    },
                    enabled = valorFinal != null && !consultando,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyanC),
                    shape = RoundedCornerShape(100.dp)
                ) { Text("Usar", color = CardBg, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onEscanearDeNovo,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(0.2f))
                    ) { Text("Escanear de novo", fontSize = 13.sp) }
                    TextButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Cancelar", color = Color.White.copy(0.6f))
                    }
                }
            }
        }
    }
}

private fun mensagemSemValor(cupom: CupomLido, consultaFalhou: Boolean): String = when (cupom.origem) {
    OrigemCupom.NFCE_V2, OrigemCupom.SAT, OrigemCupom.NFCE_V1 ->
        if (cupom.chave != null || cupom.cnpj != null)
            "Esse cupom não traz o valor no código; digite o valor — chave e emitente foram preenchidos." +
                if (consultaFalhou) " (Não foi possível consultar o site da SEFAZ.)" else ""
        else "Esse cupom não traz o valor no código; digite o valor."
    OrigemCupom.BOLETO -> "Boleto sem valor definido no código (valor livre). Digite o valor."
    OrigemCupom.PIX -> "Esse PIX não traz valor (QR aberto). Digite o valor."
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(rotulo, color = Color.White.copy(0.5f), fontSize = 11.sp)
        Text(valor, color = Color.White, fontSize = 14.sp)
    }
}
