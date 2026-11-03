package com.meudinheiro.componentes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.AcaoTrilha
import com.meudinheiro.domain.BackupRetencao
import com.meudinheiro.domain.EntradaTrilha
import com.meudinheiro.domain.MetaBackup
import com.meudinheiro.domain.TipoBackup
import com.meudinheiro.domain.Trilha
import com.meudinheiro.funcoes.BackupArmazenamento
import com.meudinheiro.funcoes.ExportarTudo
import com.meudinheiro.funcoes.RelatorioExport
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.BackupJson
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed
import com.meudinheiro.worker.BackupAutomatico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)
private val dataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

private enum class SubTela { LISTA, ALTERACOES }

/** R66 — backups (automático diário, histórico, restauração segura, exportar tudo) e trilha de alterações. */
@Composable
fun BackupsAutomaticosScreen(repository: MainRepository, userPrefs: UserPreferences, onBack: () -> Unit) {
    var sub by remember { mutableStateOf(SubTela.LISTA) }
    BackHandler(enabled = sub != SubTela.LISTA) { sub = SubTela.LISTA }
    when (sub) {
        SubTela.LISTA -> BackupsLista(repository, userPrefs, onBack, abrirAlteracoes = { sub = SubTela.ALTERACOES })
        SubTela.ALTERACOES -> HistoricoAlteracoesScreen(repository) { sub = SubTela.LISTA }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupsLista(repository: MainRepository, userPrefs: UserPreferences, onBack: () -> Unit, abrirAlteracoes: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ativo by userPrefs.autoBackupEnabledFlow.collectAsState(initial = false)
    val exigirBio by userPrefs.biometriaLancarFlow.collectAsState(initial = true)
    val arm = remember { BackupAutomatico.armazenamento(context) }
    var backups by remember { mutableStateOf(emptyList<MetaBackup>()) }
    var restaurando by remember { mutableStateOf<MetaBackup?>(null) }
    var excluindo by remember { mutableStateOf<MetaBackup?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    fun atualizar() { backups = arm.listar() }
    androidx.compose.runtime.LaunchedEffect(Unit) { withContext(Dispatchers.IO) { atualizar() } }

    fun compartilhar(arquivo: File) {
        val destino = File(File(context.cacheDir, "extratos").apply { mkdirs() }, arquivo.name)
        arquivo.copyTo(destino, overwrite = true)
        RelatorioExport.compartilhar(context, destino, "application/json")
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Backups", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Backup automático diário", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Desligado por padrão. Ligado, o app guarda uma cópia por dia (quando a bateria não está fraca) no armazenamento privado do aparelho: " +
                                "os 7 últimos diários e os 4 últimos semanais (domingo). Backups manuais nunca são apagados sozinhos. " +
                                "A cópia fica só neste aparelho — a cópia na nuvem ainda não está habilitada.",
                            color = Color.White.copy(0.55f), fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = ativo,
                        onCheckedChange = { v -> scope.launch { userPrefs.saveAutoBackupEnabled(v); BackupAutomatico.agendar(context, v) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeepSpaceBlue, checkedTrackColor = NeonCyan)
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            ocupado = true
                            try {
                                withContext(Dispatchers.IO) { BackupAutomatico.executar(context, TipoBackup.MANUAL); atualizar() }
                                Toast.makeText(context, "Backup criado.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Falha no backup: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally { ocupado = false }
                        }
                    },
                    enabled = !ocupado, modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                ) { Text("Fazer backup agora", fontWeight = FontWeight.Bold) }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ocupado = true
                                try {
                                    val zip = withContext(Dispatchers.IO) { ExportarTudo.exportar(context, BackupJson.fromJson(repository.gerarBackup())) }
                                    RelatorioExport.compartilhar(context, zip, "application/zip")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Falha ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally { ocupado = false }
                            }
                        },
                        enabled = !ocupado, modifier = Modifier.weight(1f)
                    ) { Text("Exportar tudo (ZIP)", color = NeonCyan, fontSize = 12.sp) }
                    OutlinedButton(onClick = abrirAlteracoes, modifier = Modifier.weight(1f)) { Text("Histórico de alterações", color = NeonCyan, fontSize = 12.sp) }
                }
            }
            if (backups.isEmpty()) item { Text("Nenhum backup ainda.", color = Color.White.copy(0.7f), modifier = Modifier.padding(top = 12.dp)) }
            items(backups, key = { it.arquivo }) { b ->
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(dataHora.format(java.util.Date(b.criadoEm)) + " · " + b.tipo.rotulo, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("%.1f KB".format(b.tamanho / 1024.0) + " · " + BackupRetencao.resumo(b.contagens, 6), color = Color.White.copy(0.55f), fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { compartilhar(arm.arquivo(b)) }) { Text("Enviar", color = NeonCyan) }
                        TextButton(onClick = { excluindo = b }) { Text("Excluir", color = Color.White.copy(0.7f)) }
                        TextButton(onClick = { restaurando = b }) { Text("Restaurar", color = NeonRed) }
                    }
                }
            }
        }
    }

    excluindo?.let { b ->
        AlertDialog(
            onDismissRequest = { excluindo = null }, containerColor = DeepSpaceBlue,
            title = { Text("Excluir este backup?", color = Color.White) },
            text = { Text("${dataHora.format(java.util.Date(b.criadoEm))} · ${b.tipo.rotulo}. Não dá para desfazer.", color = Color.White.copy(0.8f)) },
            confirmButton = {
                Button(
                    onClick = { excluindo = null; scope.launch(Dispatchers.IO) { arm.excluir(b); atualizar() } },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DeepSpaceBlue)
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
        )
    }

    restaurando?.let { b ->
        // O que será substituído: contagens atuais x do backup.
        val atuais by produceState<Map<String, Int>?>(null, b) {
            value = withContext(Dispatchers.IO) { runCatching { BackupRetencao.contagens(BackupJson.fromJson(repository.gerarBackup())) }.getOrNull() }
        }
        AlertDialog(
            onDismissRequest = { restaurando = null }, containerColor = DeepSpaceBlue,
            title = { Text("Restaurar este backup?", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "TODOS os dados atuais serão substituídos pelos do backup de ${dataHora.format(java.util.Date(b.criadoEm))}. " +
                            "Antes, o app guarda uma cópia de segurança do estado atual.",
                        color = Color.White.copy(0.8f), fontSize = 13.sp
                    )
                    Text("Atual → backup", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    b.contagens.forEach { (chave, n) ->
                        Text("${BackupRetencao.rotulo(chave)}: ${atuais?.get(chave) ?: "…"} → $n", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        restaurando = null
                        autenticarParaLancar(context, exigirBio, "Restaurar backup", "Confirme que é você para substituir os dados") {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        BackupArmazenamento.restaurarComSeguranca(
                                            arm, arm.ler(b), gerarAtual = { repository.gerarBackup() },
                                            restaurar = { repository.restaurarBackupCompleto(it) }, agora = System.currentTimeMillis()
                                        )
                                        atualizar()
                                    }
                                    Toast.makeText(context, "Backup restaurado. Uma cópia de segurança do estado anterior foi guardada.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Não foi possível restaurar: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed, contentColor = DeepSpaceBlue)
                ) { Text("Substituir meus dados") }
            },
            dismissButton = { TextButton(onClick = { restaurando = null }) { Text("Cancelar", color = Color.White.copy(0.6f)) } }
        )
    }
}

/** R66 — trilha local de alterações de lançamentos, com filtro por período/ação e busca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoricoAlteracoesScreen(repository: MainRepository, onBack: () -> Unit) {
    var busca by remember { mutableStateOf("") }
    var acao by remember { mutableStateOf<String?>(null) }
    var dias by remember { mutableStateOf<Int?>(null) }
    val todas by produceState(emptyList<EntradaTrilha>()) { value = withContext(Dispatchers.IO) { repository.lerTrilha() } }
    val agora = remember { System.currentTimeMillis() }
    val lista = remember(todas, busca, acao, dias) {
        Trilha.filtrar(todas, de = dias?.let { agora - it * 86_400_000L }, acao = acao, busca = busca)
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Histórico de alterações", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Registro local (só neste aparelho) das últimas ${com.meudinheiro.domain.TrilhaArquivo.LIMITE_PADRAO} alterações.", color = Color.White.copy(0.55f), fontSize = 12.sp)
            }
            item {
                OutlinedTextField(
                    value = busca, onValueChange = { busca = it }, singleLine = true,
                    placeholder = { Text("Buscar por descrição, campo ou valor", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan,
                        focusedPlaceholderColor = Color.White.copy(0.4f), unfocusedPlaceholderColor = Color.White.copy(0.4f)
                    )
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf<Int?>(null, 7, 30, 90)) { d ->
                        FilterChip(selected = dias == d, onClick = { dias = d }, label = { Text(if (d == null) "Todo o período" else "Últimos $d dias", fontSize = 12.sp) })
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf<String?>(null) + AcaoTrilha.TODAS) { a ->
                        FilterChip(selected = acao == a, onClick = { acao = a }, label = { Text(if (a == null) "Todas as ações" else AcaoTrilha.rotulo(a), fontSize = 12.sp) })
                    }
                }
            }
            if (lista.isEmpty()) item { Text("Nada registrado para este filtro.", color = Color.White.copy(0.7f), modifier = Modifier.padding(top = 12.dp)) }
            items(lista) { e ->
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(AcaoTrilha.rotulo(e.acao) + " · " + dataHora.format(java.util.Date(e.quando)), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.descricao + (e.valor?.let { " · " + formatarMoedaBR(it, false) } ?: ""), color = Color.White, fontSize = 14.sp)
                    e.alteracoes.forEach { m ->
                        Text("${m.campo}: ${m.antigo.ifBlank { "—" }} → ${m.novo.ifBlank { "—" }}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
