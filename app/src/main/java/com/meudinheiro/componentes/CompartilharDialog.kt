package com.meudinheiro.componentes

import android.content.Intent
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.Acao
import com.meudinheiro.domain.ConviteCodigo
import com.meudinheiro.domain.Papel
import com.meudinheiro.domain.Permissoes
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.storage.Compartilhamento
import com.meudinheiro.storage.Convite
import com.meudinheiro.storage.ConviteCodigoInfo
import com.meudinheiro.storage.Membro
import com.meudinheiro.storage.StorageManager
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * R32/R67 — conta compartilhada: convite por código (papel + validade), membros e papéis, entrar com um código
 * (mostrando de quem é a conta antes de confirmar) e sair. A UI só esconde botões conforme o papel; as regras do Firestore decidem.
 */
@Composable
fun CompartilharDialog(storage: StorageManager, onFechar: () -> Unit, onMensagem: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val comp = remember { Compartilhamento(storage.auth) }
    val raiz by storage.raizUid.collectAsState(initial = "")
    val meuUid = storage.auth.uidAtual.orEmpty()
    val donoAtual = raiz.ifBlank { meuUid }
    val meuPapel by storage.papel.collectAsState()
    val ehAdmin = Permissoes.podeExecutar(meuPapel, Acao.MEMBROS_GERENCIAR)
    val concediveis = Permissoes.papeisConcediveis(meuPapel)
    val formato = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    var membros by remember { mutableStateOf<List<Membro>>(emptyList()) }
    var convites by remember { mutableStateOf<List<Convite>>(emptyList()) }
    var pendentes by remember { mutableStateOf<List<ConviteCodigoInfo>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var papelConvite by remember { mutableStateOf(Papel.EDITOR) }
    var codigoDigitado by remember { mutableStateOf("") }
    var conviteConsultado by remember { mutableStateOf<ConviteCodigoInfo?>(null) }
    var novoConvite by remember { mutableStateOf<ConviteCodigoInfo?>(null) }
    var confirmacao by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    fun recarregar() {
        scope.launch {
            storage.atualizarCompartilhamento()
            runCatching { membros = comp.membros(donoAtual); convites = comp.convitesRecebidos() }
                .onFailure { onMensagem("Compartilhar | ${it.message ?: "Não foi possível carregar."} | Erro") }
            // A lista de convites pendentes depende da regra de `list` (dono/ADMIN); falha aqui não impede o resto.
            pendentes = if (ehAdmin) runCatching { comp.convitesPendentes(donoAtual) }.getOrDefault(emptyList()) else emptyList()
        }
    }
    LaunchedEffect(donoAtual, meuPapel) { recarregar() }

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
    val campoCores = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
        unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
    )
    val botao = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = DeepSpaceBlue,
        title = { Text("Conta compartilhada", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (usandoOutros) {
                    Text("Você está usando a conta de outra pessoa (seu papel: ${meuPapel.rotulo}).", color = NeonOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { executar("Dados") { storage.usarMeusDados() } }, enabled = !ocupado, colors = botao) { Text("Voltar a usar meus dados") }
                    TextButton(
                        onClick = {
                            confirmacao = "Sair desta conta compartilhada? Você perde o acesso aos dados dela e precisará de um novo convite para voltar." to {
                                executar("Sair", "Você saiu da conta compartilhada.") { comp.sair(donoAtual); storage.usarMeusDados() }
                            }
                        },
                        enabled = !ocupado
                    ) { Text("Sair da conta compartilhada", color = NeonRed) }
                }

                // ---------------------------------------------------------------- entrar com código
                Text("ENTRAR COM UM CÓDIGO", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                OutlinedTextField(
                    value = codigoDigitado, onValueChange = { codigoDigitado = it.take(12); conviteConsultado = null }, singleLine = true,
                    label = { Text("Código de convite (8 caracteres)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(), colors = campoCores
                )
                val consultado = conviteConsultado
                if (consultado == null) {
                    Button(
                        onClick = { executar("Convite") { conviteConsultado = comp.consultarConvite(codigoDigitado) } },
                        enabled = !ocupado && codigoDigitado.isNotBlank(), colors = botao
                    ) { Text("Verificar código", fontWeight = FontWeight.Bold) }
                } else {
                    val quem = consultado.donoNome.ifBlank { consultado.donoEmail }.ifBlank { "outra pessoa" }
                    Text(
                        "Este convite dá acesso à conta de $quem como ${consultado.papel.rotulo}. " +
                            "Vale até ${formato.format(Date(consultado.expiraEm))}.",
                        color = Color.White, fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                executar("Convite", "Você entrou na conta de $quem.") {
                                    val nome = UserPreferences(context).userNameFlow.first()
                                    comp.aceitarConvite(consultado, nome)
                                    storage.usarDadosDe(consultado.donoUid)
                                    conviteConsultado = null; codigoDigitado = ""
                                }
                            },
                            enabled = !ocupado, colors = botao
                        ) { Text("Confirmar entrada", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { conviteConsultado = null }) { Text("Cancelar", color = Color.White) }
                    }
                }

                // ---------------------------------------------------------------- gerar convite (dono/ADMIN)
                if (ehAdmin) {
                    Text("CONVIDAR", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "O convite vale por 7 dias e só pode ser usado uma vez. A pessoa terá o papel escolhido abaixo.",
                        color = Color.White.copy(0.6f), fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        concediveis.forEach { p ->
                            FilterChip(selected = papelConvite == p, onClick = { papelConvite = p }, label = { Text(p.rotulo) })
                        }
                    }
                    Button(
                        onClick = { executar("Convite") { novoConvite = comp.gerarConvite(donoAtual, papelConvite, UserPreferences(context).userNameFlow.first()) } },
                        enabled = !ocupado && papelConvite in concediveis, colors = botao
                    ) { Text("Gerar código de convite", fontWeight = FontWeight.Bold) }
                    novoConvite?.let { c ->
                        Text(c.codigo, color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("${c.papel.rotulo} · válido até ${formato.format(Date(c.expiraEm))}", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(c.codigo)); onMensagem("Convite | Código copiado. | Sucesso") }) { Text("Copiar", color = NeonCyan) }
                            TextButton(onClick = {
                                val envio = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Entre na minha conta no Meu Dinheiro com o código ${c.codigo} (vale 7 dias).")
                                }
                                context.startActivity(Intent.createChooser(envio, "Compartilhar convite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text("Compartilhar", color = NeonCyan) }
                        }
                    }
                    if (pendentes.isNotEmpty()) {
                        Text("CONVITES PENDENTES", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        pendentes.forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${c.codigo} · ${c.papel.rotulo} · até ${formato.format(Date(c.expiraEm))}",
                                    color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { executar("Convite", "Convite revogado.") { comp.revogarConvite(c.codigo) } }, enabled = !ocupado) {
                                    Text("Revogar", color = NeonRed)
                                }
                            }
                        }
                    }

                    Text("CONVIDAR POR E-MAIL", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("A pessoa precisa já ter entrado no app ou no portal com o e-mail dela. Ela terá o papel escolhido acima.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, singleLine = true, label = { Text("E-mail da pessoa") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), colors = campoCores
                    )
                    Button(
                        onClick = { executar("Convite", "Convite enviado.") { comp.convidar(email, donoAtual, papelConvite); email = "" } },
                        enabled = !ocupado && email.isNotBlank() && papelConvite in concediveis, colors = botao
                    ) { Text("Convidar", fontWeight = FontWeight.Bold) }
                }

                // ---------------------------------------------------------------- membros
                Text("PESSOAS COM ACESSO À CONTA", color = Color.White.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                if (membros.isEmpty()) Text("Ninguém ainda.", color = Color.White.copy(0.6f), fontSize = 12.sp)
                membros.forEach { m ->
                    val titulo = m.nome.ifBlank { m.email.ifBlank { m.uid } }
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(titulo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${m.papel.rotulo}" + (if (m.entrouEm > 0) " · desde ${formato.format(Date(m.entrouEm))}" else "") +
                                (if (m.nome.isNotBlank() && m.email.isNotBlank()) " · ${m.email}" else ""),
                            color = Color.White.copy(0.6f), fontSize = 11.sp
                        )
                        if (Permissoes.podeGerenciarMembro(meuPapel, m.papel)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                concediveis.filter { it != m.papel }.forEach { p ->
                                    TextButton(
                                        onClick = { executar("Papel", "${titulo} agora é ${p.rotulo}.") { comp.alterarPapel(donoAtual, m, p) } },
                                        enabled = !ocupado
                                    ) { Text("Tornar ${p.rotulo}", color = NeonCyan, fontSize = 12.sp) }
                                }
                                TextButton(
                                    onClick = {
                                        confirmacao = "Remover $titulo da conta? A pessoa perde o acesso imediatamente." to {
                                            executar("Remover", "Acesso removido.") { comp.removerMembro(m, donoAtual) }
                                        }
                                    },
                                    enabled = !ocupado
                                ) { Text("Remover", color = NeonRed, fontSize = 12.sp) }
                            }
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

    confirmacao?.let { (texto, acao) ->
        AlertDialog(
            onDismissRequest = { confirmacao = null },
            containerColor = DeepSpaceBlue,
            title = { Text("Confirmar", color = Color.White) },
            text = { Text(texto, color = Color.White.copy(0.85f), fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { confirmacao = null; acao() }) { Text("Confirmar", color = NeonRed) } },
            dismissButton = { TextButton(onClick = { confirmacao = null }) { Text("Cancelar", color = Color.White) } }
        )
    }
}
