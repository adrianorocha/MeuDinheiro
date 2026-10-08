package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.storage.Compartilhamento
import com.meudinheiro.storage.Convite
import com.meudinheiro.storage.Membro
import com.meudinheiro.storage.StorageManager
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

/** R32 — convidar pessoas, ver/remover membros e escolher de quem são os dados em uso. */
@Composable
fun CompartilharDialog(storage: StorageManager, onFechar: () -> Unit, onMensagem: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val comp = remember { Compartilhamento(storage.auth) }
    val raiz by storage.raizUid.collectAsState(initial = "")
    val meuUid = storage.auth.uidAtual.orEmpty()
    var membros by remember { mutableStateOf<List<Membro>>(emptyList()) }
    var convites by remember { mutableStateOf<List<Convite>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }

    fun recarregar() {
        scope.launch {
            runCatching { membros = comp.membros(); convites = comp.convitesRecebidos() }
                .onFailure { onMensagem("Compartilhar | ${it.message ?: "Não foi possível carregar."} | Erro") }
        }
    }
    LaunchedEffect(Unit) { recarregar() }

    fun executar(titulo: String, ok: String? = null, bloco: suspend () -> Unit) {
        scope.launch {
            ocupado = true
            try {
                bloco()
                ok?.let { onMensagem("$titulo | $it | Sucesso") }
                recarregar()
            } catch (e: Exception) {
                onMensagem("$titulo | ${e.message ?: "Não foi possível concluir."} | Erro")
            } finally {
                ocupado = false
            }
        }
    }

    val usandoOutros = raiz.isNotBlank() && raiz != meuUid

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = DeepSpaceBlue,
        title = { Text("Conta compartilhada", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (usandoOutros) {
                    Text("Você está usando os dados de outra pessoa.", color = NeonOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { executar("Dados") { storage.usarMeusDados() } },
                        enabled = !ocupado, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                    ) { Text("Voltar a usar meus dados") }
                }

                Text("CONVIDAR", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "A pessoa precisa já ter entrado no app ou no portal com o e-mail dela. Quem for convidado vê e edita TODOS os seus dados.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, singleLine = true, label = { Text("E-mail da pessoa") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
                    )
                )
                Button(
                    onClick = { executar("Convite", "Convite enviado.") { comp.convidar(email); email = "" } },
                    enabled = !ocupado && email.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                ) { Text("Convidar", fontWeight = FontWeight.Bold) }

                Text("PESSOAS COM ACESSO AOS MEUS DADOS", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                if (membros.isEmpty()) Text("Ninguém ainda.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                membros.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(m.email.ifBlank { m.uid }, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { executar("Remover", "Acesso removido.") { comp.removerMembro(m) } }, enabled = !ocupado) {
                            Text("Remover", color = NeonRed)
                        }
                    }
                }

                Text("CONVITES RECEBIDOS", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                if (convites.isEmpty()) Text("Nenhum convite.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                convites.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(c.donoEmail.ifBlank { c.donoUid }, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { executar("Dados", "Agora você usa os dados de ${c.donoEmail}.") { storage.usarDadosDe(c.donoUid) } }, enabled = !ocupado) {
                            Text("Usar dados", color = NeonCyan)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar", color = NeonCyan) } }
    )
}
