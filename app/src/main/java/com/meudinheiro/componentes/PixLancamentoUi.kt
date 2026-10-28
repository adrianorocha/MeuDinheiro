package com.meudinheiro.componentes

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Natureza
import com.meudinheiro.domain.Pix
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * R94 — scanner de QR Code Pix: mesma câmera/ML Kit do scanner de cupom e de boleto ([ScannerCameraPreview]), com um
 * filtro "Pix": só aceita BR Code válido (CRC conferido). Devolve o código lido ao formulário; nunca consulta a URL do
 * payload dinâmico e não registra o conteúdo lido.
 */
@Composable
fun ScannerPixScreen(onResult: (Pix.BrCode) -> Unit, onClose: () -> Unit) {
    var lido by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize()) {
        ScannerCameraPreview(onCodigoDetectado = { codigo, _ ->
            if (!lido) {
                when (val r = Pix.lerBrCode(codigo)) {
                    is Pix.ResultadoBrCode.Ok -> {
                        lido = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onResult(r.brCode)
                    }
                    is Pix.ResultadoBrCode.Invalido ->
                        aviso = if (codigo.contains("000201")) r.motivo.mensagem else "Esse QR Code não é um Pix."
                }
            }
        })
        ScannerNeonOverlay(
            onClose = onClose,
            onToggleFlash = { },
            instrucao = aviso ?: "Aponte para o QR Code do Pix",
            alturaJanelaDp = 260f
        )
    }
}

private fun lerAreaDeTransferencia(ctx: Context): String? = try {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
} catch (_: Exception) {
    null
}

/**
 * R94 — seção "Pix" dos formulários de lançamento EM CONTA (não aparece em cartão). Marca/desmarca a tag PIX e oferece 3 formas
 * de capturar os dados: Pix copia e cola (lê a área de transferência SÓ quando o usuário toca), QR Code pela câmera e
 * comprovante colado. A leitura nunca salva: mostra um resumo (chave mascarada) e só preenche o formulário ao tocar em
 * "Aplicar", avisando o que será substituído.
 *
 * @param direcao ENVIADO no formulário de despesa (débito), RECEBIDO no de receita (crédito).
 */
@Composable
fun SecaoPix(
    direcao: Pix.Direcao,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    descricaoAtual: String,
    valorAtualCentavos: Long?,
    onAplicar: (Pix.RascunhoPix) -> Unit,
    modifier: Modifier = Modifier,
    pessoas: List<Pix.PessoaRef> = emptyList(),
    onVincularPessoa: ((Pix.PessoaRef) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val ativo = Pix.temTag(tags)
    var rascunho by remember { mutableStateOf<Pix.RascunhoPix?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var aplicado by remember { mutableStateOf(false) }
    var comprovante by remember { mutableStateOf("") } // de propósito sem rememberSaveable: o texto colado não é persistido
    var mostrarScanner by remember { mutableStateOf(false) }
    var pessoaVinculada by remember { mutableStateOf(false) }

    val permissaoCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) mostrarScanner = true
        else Toast.makeText(ctx, "Permissão de câmera negada. Use o Pix copia e cola ou o comprovante.", Toast.LENGTH_LONG).show()
    }

    fun receber(r: Pix.RascunhoPix) {
        rascunho = r; erro = null; aplicado = false; pessoaVinculada = false
    }

    fun colar() {
        val txt = lerAreaDeTransferencia(ctx)
        if (txt.isNullOrBlank()) { erro = "A área de transferência está vazia."; return }
        when (val r = Pix.lerBrCode(txt)) {
            is Pix.ResultadoBrCode.Ok -> receber(Pix.rascunhoPix(r.brCode, direcao))
            is Pix.ResultadoBrCode.Invalido -> {
                // Não é um copia e cola: talvez seja o texto de um comprovante.
                val c = Pix.lerComprovantePix(txt)
                if (r.motivo == Pix.MotivoBrCode.NAO_E_BRCODE && c is Pix.ResultadoComprovante.Ok) receber(Pix.rascunhoPix(c.comprovante, direcao))
                else erro = r.motivo.mensagem
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ativo,
                onCheckedChange = { marcar ->
                    onTagsChange(if (marcar) Pix.comTag(tags) else Pix.semTag(tags))
                    if (!marcar) { rascunho = null; erro = null }
                }
            )
            Column {
                Text(
                    if (direcao == Pix.Direcao.ENVIADO) "Foi um Pix (enviado)" else "Foi um Pix (recebido)",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Text("Marca o lançamento com a tag PIX.", color = Color.White.copy(0.5f), fontSize = 11.sp)
            }
        }

        if (ativo) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { colar() }) { Text("Colar Pix", color = NeonCyan, fontSize = 13.sp) }
                TextButton(onClick = {
                    val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (ok) mostrarScanner = true else permissaoCamera.launch(Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Text("  Ler QR Code", color = NeonCyan, fontSize = 13.sp)
                }
            }
            Text("\"Colar Pix\" lê o copia e cola copiado, só quando você toca. Nada é enviado para fora do aparelho.", color = Color.White.copy(0.45f), fontSize = 10.sp)

            OutlinedTextField(
                value = comprovante,
                onValueChange = { comprovante = it.take(6_000); erro = null },
                label = { Text("Ou cole o texto do comprovante", fontSize = 12.sp) },
                minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = NeonCyan, unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan,
                    focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.6f)
                )
            )
            TextButton(
                onClick = {
                    when (val c = Pix.lerComprovantePix(comprovante)) {
                        is Pix.ResultadoComprovante.Ok -> { receber(Pix.rascunhoPix(c.comprovante, direcao)); comprovante = "" }
                        is Pix.ResultadoComprovante.Invalido -> erro = c.motivo.mensagem
                    }
                },
                enabled = comprovante.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) { Text("Interpretar comprovante", color = NeonCyan, fontSize = 13.sp) }

            erro?.let { Text(it, color = Color(0xFFFFB74D), fontSize = 12.sp) }
            if (aplicado) Text("Preenchido pelo Pix. Confira os campos antes de salvar.", color = NeonGreen, fontSize = 12.sp)

            rascunho?.let { r ->
                ResumoPixCard(
                    r = r, descricaoAtual = descricaoAtual, valorAtualCentavos = valorAtualCentavos,
                    sugestaoPessoa = if (direcao == Pix.Direcao.RECEBIDO && onVincularPessoa != null && !pessoaVinculada)
                        Pix.sugerirPessoa(r.resumo.contraparte, pessoas) else null,
                    onVincular = { p -> onVincularPessoa?.invoke(p); pessoaVinculada = true },
                    onAplicar = { onAplicar(r); rascunho = null; aplicado = true },
                    onDescartar = { rascunho = null }
                )
            }
        }
    }

    if (mostrarScanner) {
        Dialog(onDismissRequest = { mostrarScanner = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScannerPixScreen(
                    onResult = { b -> receber(Pix.rascunhoPix(b, direcao)); mostrarScanner = false },
                    onClose = { mostrarScanner = false }
                )
            }
        }
    }
}

@Composable
private fun ResumoPixCard(
    r: Pix.RascunhoPix,
    descricaoAtual: String,
    valorAtualCentavos: Long?,
    sugestaoPessoa: Pix.PessoaRef?,
    onVincular: (Pix.PessoaRef) -> Unit,
    onAplicar: () -> Unit,
    onDescartar: () -> Unit
) {
    val s = r.resumo
    val substitui = buildList {
        if (descricaoAtual.isNotBlank() && !descricaoAtual.trim().equals(r.descricao, ignoreCase = true)) add("descrição")
        if (r.valorCentavos != null && valorAtualCentavos != null && valorAtualCentavos > 0 && valorAtualCentavos != r.valorCentavos) {
            add("valor (${FormatoCentavos.brl(valorAtualCentavos)})")
        }
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Pix lido: ${s.origem}", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            r.valorCentavos?.let { Linha("Valor", FormatoCentavos.brl(it)) } ?: Linha("Valor", "não informado (digite o valor)")
            s.contraparte?.let { Linha(if (r.direcao == Pix.Direcao.ENVIADO) "Beneficiário" else "Nome", it) }
            s.chaveMascarada?.let { Linha("Chave Pix", it) }
            s.cidade?.let { Linha("Cidade", it) }
            s.e2e?.let { Linha("ID da transação", Pix.e2eResumido(it)) }
            s.txid?.let { Linha("txid", it) }
            s.mensagem?.let { Linha("Mensagem", it) }
            s.hostPayload?.let { Linha("Link do QR dinâmico", "$it (não é aberto)") }
            r.dataMs?.let { Linha("Data", SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it))) }
            Linha("Descrição", r.descricao)

            if (r.conflitoDirecao) {
                Text(
                    if (r.direcao == Pix.Direcao.ENVIADO) "Este comprovante parece de um Pix RECEBIDO; aqui você lança uma despesa. Use Nova Receita."
                    else "Este comprovante parece de um Pix ENVIADO; aqui você lança uma receita. Use Novo Lcto > Despesa.",
                    color = Color(0xFFFF8A80), fontSize = 12.sp
                )
            } else if (substitui.isNotEmpty()) {
                Text("Atenção: vai substituir " + substitui.joinToString(" e ") + " que você já preencheu.", color = Color(0xFFFFB74D), fontSize = 12.sp)
            }
            if (sugestaoPessoa != null) {
                AssistChip(onClick = { onVincular(sugestaoPessoa) }, label = { Text("Vincular a ${sugestaoPessoa.nome}? (tag \"De: ...\")", fontSize = 12.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(
                    onClick = onAplicar, enabled = !r.conflitoDirecao,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0D1B2A))
                ) { Text("Aplicar ao lançamento") }
                TextButton(onClick = onDescartar) { Text("Descartar", color = Color.White.copy(0.6f)) }
            }
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$rotulo:", color = Color.White.copy(0.5f), fontSize = 12.sp)
        Text(valor, color = Color.White, fontSize = 12.sp)
    }
}

/**
 * R94 — aviso (não bloqueante) de possível duplicidade: já existe lançamento na MESMA conta, mesmo sentido e valor, data
 * ±1 dia e contraparte parecida (ou já marcado PIX) — típico de notificação + comprovante do mesmo Pix.
 */
@Composable
fun AvisoPixDuplicado(
    valorCentavos: Long?,
    entrada: Boolean,
    dataMs: Long,
    conta: String,
    descricao: String,
    lancamentos: List<Despesa>,
    modifier: Modifier = Modifier
) {
    val achados = remember(valorCentavos, entrada, dataMs, conta, descricao, lancamentos) {
        Pix.possiveisDuplicados(
            Pix.Candidato(valorCentavos ?: 0L, entrada, dataMs, conta, Pix.contraparteDaDescricao(descricao), Pix.idDaDescricao(descricao)),
            lancamentos.filter { it.natureza == Natureza.NORMAL && (it.cartaoId == null || it.cartaoId == 0) }
                .map { Pix.Existente(com.meudinheiro.domain.Dinheiro.centavos(it.valor), it.tipo == TipoDespesa.CREDITO, it.dataMs, it.conta, it.descricao, it.tags) }
        )
    }
    if (achados.isEmpty()) return
    val f = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    val e = achados.first()
    Text(
        "Possível duplicidade: já existe ${FormatoCentavos.brl(e.valorCentavos)} nesta conta em ${f.format(Date(e.dataMs))} (\"${e.descricao.take(30)}\")." +
            if (achados.size > 1) " Há mais ${achados.size - 1} parecido(s)." else "",
        color = Color(0xFFFFB74D), fontSize = 12.sp, modifier = modifier.fillMaxWidth()
    )
}
