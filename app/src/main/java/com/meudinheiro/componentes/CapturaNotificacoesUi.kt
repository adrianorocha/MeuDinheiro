package com.meudinheiro.componentes

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meudinheiro.captura.CapturaConfig
import com.meudinheiro.captura.CapturaNotificacoesService
import com.meudinheiro.captura.NotificadorSugestoes
import com.meudinheiro.captura.SugestoesStore
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.NotificacaoBancaria
import com.meudinheiro.domain.NotificacaoBancaria.Estado
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale


/** Recarrega o conteúdo quando o usuário volta das configurações do sistema ou do formulário de lançamento. */
@Composable
private fun AoRetomar(acao: () -> Unit) {
    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) acao() }
        dono.lifecycle.addObserver(obs)
        onDispose { dono.lifecycle.removeObserver(obs) }
    }
}

internal fun rotuloDoApp(ctx: Context, pacote: String): String {
    if (pacote.endsWith(".*")) return "Todos os apps $pacote"
    return runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "(não instalado ou não visível)"
}

/** Configurações > "Captura de notificações do banco" (R64). Desligada por padrão. */
@Composable
fun SecaoCapturaNotificacoes(isPrivate: Boolean = false) {
    val ctx = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    AoRetomar { versao++ }

    val concedido = remember(versao) { CapturaNotificacoesService.acessoConcedido(ctx) }
    val ligado = remember(versao) { CapturaConfig.ativo(ctx) }
    val conectado = remember(versao) { CapturaNotificacoesService.conectado }
    val permitidos = remember(versao) { CapturaConfig.permitidos(ctx) }
    val vistos = remember(versao) { CapturaConfig.vistos(ctx).filter { !NotificacaoBancaria.pacoteAceito(it, permitidos) } }
    val pendentes = remember(versao) { SugestoesStore.pendentes(ctx).size }
    var novoPacote by remember { mutableStateOf("") }
    var erroPacote by remember { mutableStateOf<String?>(null) }
    var confirmaApagar by remember { mutableStateOf(false) }
    var revisando by remember { mutableStateOf(false) } // R89: revisão em lote como tela cheia

    fun abrirAcesso() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    PremiumConfigCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Cria SUGESTÕES de lançamento a partir das notificações dos apps de banco que você permitir (Pix, compras, saques, boletos). " +
                    "Nunca lança sozinho: você confirma cada uma (com biometria, se estiver ativa).",
                color = TextWhite.copy(0.7f), fontSize = 12.sp
            )
            Column(
                Modifier.fillMaxWidth().background(Color.White.copy(0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("O que é lido", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "Somente notificações dos apps marcados abaixo. Delas, o app extrai: valor, tipo, estabelecimento/nome, final do cartão, horário e o app de origem.",
                    color = TextWhite.copy(0.7f), fontSize = 12.sp
                )
                Text("O que NÃO é lido nem guardado", color = NeonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "Notificações de qualquer outro app são descartadas sem ler o conteúdo (só o nome do app é anotado para você poder adicioná-lo). " +
                        "O texto original da notificação nunca é gravado. Nada é enviado à internet. As sugestões ficam só neste aparelho (fora do backup), " +
                        "expiram em 7 dias e você pode apagá-las quando quiser.",
                    color = TextWhite.copy(0.7f), fontSize = 12.sp
                )
            }

            Text("Acesso às notificações: " + if (concedido) "concedido" else "não concedido", color = if (concedido) NeonGreen else Color(0xFFFFB74D), fontSize = 13.sp)
            Text(
                "Serviço: " + when {
                    !concedido -> "inativo (sem acesso)"
                    !ligado -> "aguardando (captura desligada)"
                    conectado -> "ativo"
                    else -> "iniciando… se continuar assim, desligue e ligue o acesso nas configurações do sistema"
                },
                color = TextWhite.copy(0.7f), fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { abrirAcesso() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                ) { Text(if (concedido) "Gerenciar acesso" else "Conceder acesso") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Capturar notificações do banco", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (concedido) "Interruptor opt-in (desligado por padrão)" else "Conceda o acesso acima para poder ligar",
                        color = TextWhite.copy(0.5f), fontSize = 12.sp
                    )
                }
                Switch(
                    checked = ligado && concedido, enabled = concedido,
                    onCheckedChange = {
                        CapturaConfig.definirAtivo(ctx, it)
                        if (it) CapturaNotificacoesService.religar(ctx)
                        versao++
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepSpaceBlue, checkedTrackColor = NeonCyan)
                )
            }

            Text("Apps permitidos", color = TextWhite, fontWeight = FontWeight.SemiBold)
            val lista = (NotificacaoBancaria.PACOTES_PADRAO + permitidos).distinct().sorted()
            lista.forEach { pacote ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = pacote in permitidos,
                        onCheckedChange = { CapturaConfig.marcar(ctx, pacote, it); versao++ },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan, uncheckedColor = TextWhite.copy(0.5f), checkmarkColor = DeepSpaceBlue)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(pacote, color = TextWhite, fontSize = 13.sp)
                        Text(rotuloDoApp(ctx, pacote), color = TextWhite.copy(0.5f), fontSize = 11.sp)
                    }
                }
            }

            OutlinedTextField(
                value = novoPacote, onValueChange = { novoPacote = it.take(120); erroPacote = null },
                label = { Text("Adicionar pelo nome do pacote (ex.: com.banco.app)") }, singleLine = true,
                isError = erroPacote != null, supportingText = { erroPacote?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val p = novoPacote.trim()
                    if (!NotificacaoBancaria.pacoteValido(p)) erroPacote = "Nome de pacote inválido."
                    else { CapturaConfig.marcar(ctx, p, true); novoPacote = ""; versao++ }
                }) { Text("Adicionar") }
                OutlinedButton(onClick = { CapturaConfig.restaurarPadrao(ctx); versao++ }) { Text("Restaurar padrão") }
            }

            if (vistos.isNotEmpty()) {
                Text("Outros apps que enviaram notificação (toque para permitir)", color = TextWhite.copy(0.7f), fontSize = 12.sp)
                vistos.forEach { pacote ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pacote, color = TextWhite, fontSize = 12.sp)
                            Text(rotuloDoApp(ctx, pacote), color = TextWhite.copy(0.5f), fontSize = 11.sp)
                        }
                        TextButton(onClick = { CapturaConfig.marcar(ctx, pacote, true); versao++ }) { Text("Permitir", color = NeonCyan) }
                    }
                }
                TextButton(onClick = { CapturaConfig.limparVistos(ctx); versao++ }) { Text("Limpar esta lista", color = TextWhite.copy(0.6f)) }
            }

            Button(
                onClick = { revisando = true }, enabled = pendentes > 0, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
            ) { Text(if (pendentes > 0) "Revisar sugestões em lote ($pendentes)" else "Nenhuma sugestão para revisar") }
            OutlinedButton(onClick = { confirmaApagar = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (pendentes > 0) "Apagar sugestões ($pendentes pendentes)" else "Apagar sugestões", color = NeonRed)
            }
        }
    }
    if (revisando) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { revisando = false; versao++ },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(DeepSpaceBlue)) {
                SugestoesLancamentoScreen(isPrivate, null) { revisando = false; versao++ }
            }
        }
    }
    if (confirmaApagar) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmaApagar = false },
            title = { Text("Apagar todas as sugestões?") },
            text = { Text("Remove a fila de sugestões deste aparelho. Lançamentos já feitos não são afetados.") },
            confirmButton = {
                TextButton(onClick = {
                    NotificadorSugestoes.cancelarTodas(ctx, SugestoesStore.todas(ctx).map { it.id })
                    SugestoesStore.apagarTudo(ctx); confirmaApagar = false; versao++
                }) { Text("Apagar", color = NeonRed) }
            },
            dismissButton = { TextButton(onClick = { confirmaApagar = false }) { Text("Cancelar") } }
        )
    }
}
