package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.storage.IndicadorSync
import com.meudinheiro.storage.StorageManager
import com.meudinheiro.storage.SyncStatus
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

/**
 * R86/R87 — detalhes da sincronização (toque no chip do cabeçalho): estado, pendências, erro e a lista de registros em
 * quarentena, com "Tentar enviar agora", "Tentar de novo" e "Ignorar". Só lê estado em memória; nada de Firestore.
 */
@Composable
fun StatusSyncDialog(storageManager: StorageManager, onDismiss: () -> Unit) {
    val info by storageManager.info.collectAsState()
    val registros by storageManager.quarentena.lista.collectAsState()
    val escopo = rememberCoroutineScope()
    val agora = System.currentTimeMillis()
    val indicador = IndicadorSync.derivar(info, agora)
    val visiveis = registros.filter { !it.ignorado }
    val ignorados = registros.size - visiveis.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepSpaceBlue,
        title = { Text("Sincronização", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(indicador?.texto ?: "Sincronização desligada", color = Color.White, fontSize = 15.sp)
                val st = info.status
                if (st is SyncStatus.Erro) {
                    Text(st.mensagem, color = NeonRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (info.pendentes > 0) {
                    val extra = if (info.rede) "Enviando para a nuvem." else "Sem rede: serão enviados quando a conexão voltar."
                    Text(
                        "${info.pendentes} lançamento(s) aguardando confirmação. $extra",
                        color = Color.White.copy(0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                info.confirmadoEm?.let {
                    Text(
                        "Última confirmação da nuvem: ${IndicadorSync.tempoRelativo(it, agora)}",
                        color = Color.White.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (visiveis.isNotEmpty()) {
                    Text(
                        "Registros em quarentena (${visiveis.size})",
                        color = NeonOrange, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        "A nuvem recusou estes registros. Eles continuam salvos no celular e o resto dos dados segue sendo enviado.",
                        color = Color.White.copy(0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                    visiveis.forEach { r ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${r.colecao} · ${r.descricao}", color = Color.White, fontSize = 13.sp)
                            Text(
                                "${r.motivo} (tentativas: ${r.tentativas})",
                                color = Color.White.copy(0.6f), fontSize = 12.sp
                            )
                        }
                    }
                }
                if (ignorados > 0) {
                    Text(
                        "$ignorados registro(s) ignorado(s): continuam salvos no celular, sem reenvio. \"Tentar de novo\" os inclui.",
                        color = Color.White.copy(0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Spacer(Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = { escopo.launch { storageManager.reenviarAgora() } }) {
                    Text("Tentar enviar agora", color = NeonCyan)
                }
                if (registros.isNotEmpty()) {
                    TextButton(onClick = { escopo.launch { storageManager.tentarQuarentenaDeNovo() } }) {
                        Text("Tentar de novo (quarentena)", color = NeonCyan)
                    }
                }
            }
        },
        dismissButton = {
            Column {
                if (visiveis.isNotEmpty()) {
                    TextButton(onClick = { storageManager.ignorarQuarentena() }) {
                        Text("Ignorar", color = Color.White.copy(0.7f))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White.copy(0.7f)) }
            }
        }
    )
}
