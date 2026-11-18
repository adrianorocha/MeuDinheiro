package com.meudinheiro.componentes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.meudinheiro.data.Anexo
import com.meudinheiro.domain.Anexos
import com.meudinheiro.storage.AnexosGerenciador
import com.meudinheiro.storage.AnexosGerenciador.Preparado
import com.meudinheiro.ui.theme.NeonCyan
import kotlinx.coroutines.launch
import java.io.File

private val Fundo = Color(0xFF1B263B)
private val Erro = Color(0xFFFF8A80)

/** R60 — mudanças de anexos feitas no formulário (novos já preparados + ids dos existentes a remover). */
class AnexosFormState {
    val novos = mutableStateListOf<Preparado>()
    val removidos = mutableStateListOf<String>()
    fun quantidade(existentes: List<Anexo>) = existentes.count { it.id !in removidos } + novos.size
}

/** Entrega do diálogo de edição ao host: o que anexar/remover ao salvar. */
data class AnexosEdicao(val novos: List<Preparado> = emptyList(), val removidos: Set<String> = emptySet()) {
    val vazio get() = novos.isEmpty() && removidos.isEmpty()
}

/**
 * Seção "Anexos" dos formulários (novo/editar): botões Foto e Arquivo, miniaturas com remover, progresso e
 * "Tentar de novo" para o que ainda não foi para a nuvem. [reenviar] só existe na edição (o lançamento já tem id).
 */
@Composable
fun SecaoAnexos(
    existentes: List<Anexo>,
    estado: AnexosFormState,
    modifier: Modifier = Modifier,
    reenviar: ((Anexo) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val gerenciador = remember { AnexosGerenciador.get(ctx) }
    val escopo = rememberCoroutineScope()
    var erro by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val nuvem by produceState(false) { value = gerenciador.nuvemAtiva() }
    val progresso by gerenciador.progresso.collectAsState()
    val falhas by gerenciador.falhas.collectAsState()
    var anexoAberto by remember { mutableStateOf<Anexo?>(null) }
    val visiveis = existentes.filter { it.id !in estado.removidos }
    val total = estado.quantidade(existentes)

    fun adicionar(uri: Uri?) {
        if (uri == null) return
        escopo.launch {
            ocupado = true
            gerenciador.preparar(uri, estado.quantidade(existentes)).fold(
                onSuccess = { estado.novos += it; erro = null },
                onFailure = { erro = it.message }
            )
            ocupado = false
        }
    }

    var uriFoto by remember { mutableStateOf<Uri?>(null) }
    val tirarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) adicionar(uriFoto)
    }
    fun abrirCamera() {
        if (total >= Anexos.MAX_ANEXOS) { erro = Anexos.validarNovo("image/jpeg", 1, total); return }
        runCatching {
            val pasta = File(ctx.cacheDir, "images").apply { mkdirs() }
            val arq = File(pasta, "anexo_${System.currentTimeMillis()}.jpg")
            uriFoto = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arq)
            tirarFoto.launch(uriFoto!!)
        }.onFailure { erro = "Não foi possível abrir a câmera." }
    }
    val pedirCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) abrirCamera() else erro = "Permissão da câmera negada. Use o botão Arquivo para escolher uma foto da galeria."
    }
    val escolherArquivo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { adicionar(it) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anexos ($total/${Anexos.MAX_ANEXOS})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (ocupado) CircularProgressIndicator(Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamera()
                    else pedirCamera.launch(Manifest.permission.CAMERA)
                },
                enabled = !ocupado && total < Anexos.MAX_ANEXOS,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoCamera, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Foto", color = NeonCyan)
            }
            OutlinedButton(
                onClick = { escolherArquivo.launch("*/*") },
                enabled = !ocupado && total < Anexos.MAX_ANEXOS,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AttachFile, null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Arquivo", color = NeonCyan)
            }
        }
        Text("JPEG, PNG ou PDF, até 5 MB cada. Fotos são reduzidas antes do envio.", color = Color.White.copy(0.45f), fontSize = 11.sp)
        erro?.let { Text(it, color = Erro, fontSize = 12.sp) }

        visiveis.forEach { a ->
            val pct = progresso[a.id]
            val pendenteNuvem = nuvem && Anexos.ehLocal(a.caminho)
            LinhaAnexo(
                nome = a.nome, detalhe = Anexos.formatarTamanho(a.tamanho) + when {
                    pct != null -> " · enviando $pct%"
                    pendenteNuvem && a.id in falhas -> " · falha no envio"
                    pendenteNuvem -> " · só neste aparelho"
                    Anexos.ehLocal(a.caminho) -> " · salvo no aparelho"
                    else -> ""
                },
                imagem = if (Anexos.ehLocal(a.caminho) && Anexos.ehImagem(a.tipo)) File(ctx.filesDir, a.caminho.removePrefix(Anexos.PREFIXO_LOCAL)) else null,
                ehPdf = a.tipo == "application/pdf", progresso = pct,
                aoAbrir = { anexoAberto = a }, aoRemover = { estado.removidos += a.id },
                acaoExtra = if (pendenteNuvem && pct == null && reenviar != null) {
                    (if (a.id in falhas) "Tentar de novo" else "Enviar para a nuvem") to { reenviar(a) }
                } else null
            )
        }
        estado.novos.forEach { p ->
            LinhaAnexo(
                nome = p.nome, detalhe = Anexos.formatarTamanho(p.tamanho) + " · será salvo ao confirmar",
                imagem = if (Anexos.ehImagem(p.tipo)) p.arquivo else null, ehPdf = p.tipo == "application/pdf", progresso = null,
                aoAbrir = null, aoRemover = { estado.novos.remove(p); gerenciador.descartarPreparado(p) }, acaoExtra = null
            )
        }
    }
    anexoAberto?.let { AnexosVisualizadorDialog(listOf(it), onDismiss = { anexoAberto = null }) }
}

@Composable
private fun LinhaAnexo(
    nome: String, detalhe: String, imagem: File?, ehPdf: Boolean, progresso: Int?,
    aoAbrir: (() -> Unit)?, aoRemover: () -> Unit, acaoExtra: Pair<String, () -> Unit>?
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.05f))
            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp)).padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.3f))
                    .then(if (aoAbrir != null) Modifier.clickable { aoAbrir() } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (imagem != null) AsyncImage(model = imagem, contentDescription = "Miniatura de $nome", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(if (ehPdf) Icons.Default.PictureAsPdf else Icons.Default.AttachFile, null, tint = NeonCyan, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(nome, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detalhe, color = Color.White.copy(0.55f), fontSize = 11.sp, maxLines = 2)
            }
            TextButton(onClick = aoRemover) {
                Icon(Icons.Default.Close, contentDescription = "Remover anexo $nome", tint = Erro, modifier = Modifier.size(18.dp))
            }
        }
        progresso?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = NeonCyan) }
        acaoExtra?.let { (rotulo, acao) -> TextButton(onClick = acao) { Text(rotulo, color = NeonCyan, fontSize = 12.sp) } }
    }
}

/** Clipe + contador na linha do lançamento. Não ocupa espaço (nem muda o layout) quando não há anexo. */
@Composable
fun IndicadorAnexos(anexos: List<Anexo>, modifier: Modifier = Modifier) {
    if (anexos.isEmpty()) return
    var aberto by remember { mutableStateOf(false) }
    Row(
        modifier.clickable { aberto = true }.padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AttachFile, contentDescription = "${anexos.size} anexo(s)", tint = NeonCyan.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
        Text("${anexos.size}", color = NeonCyan.copy(alpha = 0.85f), fontSize = 11.sp)
    }
    if (aberto) AnexosVisualizadorDialog(anexos, onDismiss = { aberto = false })
}

/** Visualizador: imagem em tela cheia (baixa do Storage quando preciso) ou abre o PDF no leitor do aparelho. */
@Composable
fun AnexosVisualizadorDialog(anexos: List<Anexo>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val gerenciador = remember { AnexosGerenciador.get(ctx) }
    var indice by remember { mutableStateOf(0) }
    val atual = anexos.getOrNull(indice.coerceIn(0, (anexos.size - 1).coerceAtLeast(0))) ?: return
    var pct by remember(atual.id) { mutableStateOf(0) }
    var tentativa by remember(atual.id) { mutableStateOf(0) }
    val carga by produceState<Pair<Boolean, File?>>(false to null, atual.id, tentativa) {
        value = false to null
        value = true to gerenciador.arquivoParaVer(atual) { pct = it }
    }
    val carregou = carga.first
    val arquivo = carga.second

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(atual.nome, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Anexos.formatarTamanho(atual.tamanho), color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) { Text("Fechar", color = NeonCyan) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    !carregou -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan)
                        if (pct > 0) Text("Baixando $pct%", color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    arquivo == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (Anexos.ehLocal(atual.caminho)) "Este arquivo não está neste aparelho (foi anexado em outro dispositivo sem ir para a nuvem)."
                            else "Não foi possível carregar o anexo. Verifique a conexão.",
                            color = Erro, fontSize = 13.sp
                        )
                        if (!Anexos.ehLocal(atual.caminho)) TextButton(onClick = { tentativa++ }) { Text("Tentar de novo", color = NeonCyan) }
                    }
                    Anexos.ehImagem(atual.tipo) -> AsyncImage(
                        model = arquivo, contentDescription = "Anexo ${atual.nome}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = NeonCyan, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arquivo)
                                    ctx.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Abrir PDF"
                                        )
                                    )
                                }
                            },
                            border = BorderStroke(1.dp, NeonCyan)
                        ) { Text("Abrir PDF", color = NeonCyan) }
                    }
                }
            }
            if (anexos.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    anexos.forEachIndexed { i, a ->
                        Text(
                            a.nome, color = if (i == indice) NeonCyan else Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.08f))
                                .clickable { indice = i }.padding(horizontal = 10.dp, vertical = 6.dp).heightIn(min = 24.dp)
                        )
                    }
                }
            }
        }
    }
}
