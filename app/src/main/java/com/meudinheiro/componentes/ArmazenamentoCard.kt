package com.meudinheiro.componentes

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.storage.AuthException
import com.meudinheiro.storage.StorageManager
import com.meudinheiro.storage.StorageMode
import com.meudinheiro.storage.SyncStatus
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

private enum class AcaoForte { ENVIAR, BAIXAR }

/**
 * Configurações → Armazenamento de dados: escolhe entre gravar só no celular ou na nuvem (Firebase),
 * faz login na nuvem e dispara sincronizações manuais.
 */
@Composable
fun ArmazenamentoCard(
    storage: StorageManager,
    onMensagem: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val modo by storage.modo.collectAsState(initial = StorageMode.LOCAL)
    val status by storage.status.collectAsState()
    val uid by storage.auth.uidFlow.collectAsState(initial = storage.auth.uidAtual)

    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var acaoForte by remember { mutableStateOf<AcaoForte?>(null) }
    var compartilhando by remember { mutableStateOf(false) }

    fun executar(erroTitulo: String, bloco: suspend () -> Unit) {
        scope.launch {
            ocupado = true
            try {
                bloco()
            } catch (e: AuthException) {
                onMensagem("$erroTitulo | ${e.message} | Erro")
            } catch (e: Exception) {
                onMensagem("$erroTitulo | ${e.message ?: "Não foi possível concluir."} | Erro")
            } finally {
                ocupado = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StorageMode.entries.forEach { opcao ->
            OpcaoArmazenamento(
                selecionada = modo == opcao,
                titulo = opcao.rotulo,
                descricao = opcao.descricao,
                nuvem = opcao == StorageMode.FIREBASE,
                onClick = { scope.launch { storage.alterarModo(opcao) } }
            )
        }

        if (modo == StorageMode.FIREBASE) {
            StatusSync(status)

            if (uid == null) {
                Text(
                    "Entre com a mesma conta usada no portal web. Seus dados atuais deste celular serão mesclados com os da nuvem " +
                        "(em caso de conflito, a nuvem prevalece).",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
                CampoTexto(email, { email = it }, "E-mail", KeyboardType.Email)
                CampoTexto(senha, { senha = it }, "Senha (mín. 6 caracteres)", KeyboardType.Password, senhaOculta = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { executar("Entrar") { storage.auth.entrar(email, senha); senha = "" } },
                        enabled = !ocupado && email.isNotBlank() && senha.length >= 6,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                    ) { Text("Entrar", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { executar("Criar conta") { storage.auth.criarConta(email, senha); senha = "" } },
                        enabled = !ocupado && email.isNotBlank() && senha.length >= 6,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NeonCyan.copy(0.5f))
                    ) { Text("Criar conta", color = NeonCyan) }
                }
                TextButton(
                    onClick = {
                        executar("Recuperar senha") {
                            storage.auth.recuperarSenha(email)
                            onMensagem("Recuperação | Enviamos um e-mail para redefinir a senha. | Sucesso")
                        }
                    },
                    enabled = !ocupado && email.isNotBlank()
                ) { Text("Esqueci minha senha", color = Color.White.copy(0.6f), fontSize = 12.sp) }
            } else {
                Text(
                    "Conectado como ${storage.auth.emailAtual ?: "usuário"}",
                    color = Color.White.copy(0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { executar("Sincronização") { storage.sincronizarAgora() } },
                        enabled = !ocupado && status is SyncStatus.Sincronizado,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NeonCyan.copy(0.5f))
                    ) { Text("Sincronizar agora", color = NeonCyan, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { executar("Sair") { storage.sair() } },
                        enabled = !ocupado,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(0.2f))
                    ) { Text("Sair da nuvem", color = Color.White.copy(0.8f), fontSize = 12.sp) }
                }
                OutlinedButton(
                    onClick = { compartilhando = true },
                    enabled = !ocupado, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NeonCyan.copy(0.5f))
                ) { Text("Compartilhar conta com outra pessoa", color = NeonCyan, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { acaoForte = AcaoForte.ENVIAR },
                        enabled = !ocupado && status is SyncStatus.Sincronizado,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NeonOrange.copy(0.5f))
                    ) { Text("Enviar tudo ↑", color = NeonOrange, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { acaoForte = AcaoForte.BAIXAR },
                        enabled = !ocupado && status is SyncStatus.Sincronizado,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NeonOrange.copy(0.5f))
                    ) { Text("Baixar tudo ↓", color = NeonOrange, fontSize = 12.sp) }
                }
            }

            if (ocupado) CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }

    if (compartilhando) CompartilharDialog(storage, { compartilhando = false }, onMensagem)

    acaoForte?.let { acao ->
        val enviar = acao == AcaoForte.ENVIAR
        AlertDialog(
            onDismissRequest = { acaoForte = null },
            containerColor = DeepSpaceBlue,
            title = { Text(if (enviar) "Enviar tudo para a nuvem?" else "Baixar tudo da nuvem?", color = Color.White) },
            text = {
                Text(
                    if (enviar) "Os dados da nuvem serão SUBSTITUÍDOS pelos deste celular: o que existir só na nuvem será apagado."
                    else "Os dados deste celular serão SUBSTITUÍDOS pelos da nuvem: o que existir só aqui será apagado.",
                    color = Color.White.copy(0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        acaoForte = null
                        executar(if (enviar) "Enviar" else "Baixar") {
                            if (enviar) storage.enviarTudoParaNuvem() else storage.baixarTudoDaNuvem()
                            onMensagem("Nuvem | Sincronização concluída. | Sucesso")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DeepSpaceBlue)
                ) { Text(if (enviar) "Substituir a nuvem" else "Substituir este celular", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { acaoForte = null }) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
        )
    }
}

@Composable
private fun OpcaoArmazenamento(
    selecionada: Boolean,
    titulo: String,
    descricao: String,
    nuvem: Boolean,
    onClick: () -> Unit
) {
    val cor = if (selecionada) NeonCyan else Color.White.copy(0.15f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selecionada,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = Color.White.copy(0.4f))
        )
        Icon(
            if (nuvem) Icons.Default.Cloud else Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = if (selecionada) NeonCyan else Color.White.copy(0.5f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(descricao, color = Color.White.copy(0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusSync(status: SyncStatus) {
    val (icone, cor, texto) = when (status) {
        SyncStatus.Desligado -> Triple(Icons.Default.CloudOff, Color.White.copy(0.5f), "Sincronização desligada")
        SyncStatus.AguardandoLogin -> Triple(Icons.Default.CloudOff, NeonOrange, "Entre na sua conta para sincronizar")
        SyncStatus.Conectando -> Triple(Icons.Default.Cloud, NeonCyan, "Conectando…")
        is SyncStatus.Sincronizado -> Triple(
            Icons.Default.CloudDone, NeonGreen,
            "Sincronizado " + DateUtils.getRelativeTimeSpanString(status.em, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        )
        is SyncStatus.Erro -> Triple(Icons.Default.CloudOff, NeonRed, status.mensagem)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CampoTexto(
    valor: String,
    onChange: (String) -> Unit,
    rotulo: String,
    teclado: KeyboardType,
    senhaOculta: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        visualTransformation = if (senhaOculta) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = NeonCyan, unfocusedBorderColor = Color.White.copy(0.2f),
            focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f), cursorColor = NeonCyan
        )
    )
}
