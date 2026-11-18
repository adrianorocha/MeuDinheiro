package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.meudinheiro.data.RegraCategoria
import com.meudinheiro.domain.Regras
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

private val CardFundo = Color(0xFF1B263B)
private val TextoClaro = Color(0xFFE0E1DD)

@Composable
private fun chipCores() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(0.05f), labelColor = Color.White.copy(0.7f),
    selectedContainerColor = NeonCyan.copy(0.2f), selectedLabelColor = NeonCyan
)

@Composable
private fun camposCores() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan, focusedLabelColor = NeonCyan,
    unfocusedLabelColor = Color.White.copy(0.5f)
)

/**
 * R55 — regras de categorização: CRUD, ativar/desativar, prioridade, teste rápido ("digite uma descrição e veja a
 * categoria") e "Aplicar às existentes" com PRÉVIA, confirmação e "Desfazer" imediato (só lançamentos comuns).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegrasCategoriaScreen(repository: MainRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val regras by repository.obterRegrasFlow().collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val custom by repository.obterCategoriasCustom().collectAsState(initial = emptyList())
    val categorias = remember(custom) { (repository.categorias + custom).map { it.title }.distinctBy { it.lowercase() }.sorted() }

    var editando by remember { mutableStateOf<RegraCategoria?>(null) }
    var criando by remember { mutableStateOf(false) }
    var excluir by remember { mutableStateOf<RegraCategoria?>(null) }
    var previa by remember { mutableStateOf<List<Regras.Mudanca>?>(null) }
    var desfazer by remember { mutableStateOf<List<MainRepository.CategoriaAnterior>?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var testeDesc by remember { mutableStateOf("") }

    fun tentar(bloco: suspend () -> Unit) {
        scope.launch {
            try { bloco() } catch (e: RegraFinanceiraException) { aviso = e.message }
        }
    }

    val ordenadas = remember(regras) { regras.sortedWith(compareByDescending<RegraCategoria> { it.prioridade }.thenByDescending { it.padrao.length }.thenBy { it.id }) }
    val teste = remember(testeDesc, regras) {
        if (testeDesc.isBlank()) null else Regras.aplicar(regras, Regras.Entrada(testeDesc, null, null))
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Regras de categoria", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                actions = { IconButton(onClick = { criando = true }) { Icon(Icons.Default.Add, "Nova regra", tint = NeonCyan) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Categorizam sozinhos pela descrição: valem para lançamentos novos, lançamento rápido e importações, sem trocar a categoria que você escolher à mão.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
            }
            aviso?.let { msg -> item { Text(msg, color = NeonRed, fontSize = 13.sp) } }
            desfazer?.let { lista ->
                item {
                    Row(
                        Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${lista.size} lançamento(s) recategorizado(s).", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { tentar { repository.desfazerRegrasExistentes(lista); desfazer = null } }) { Text("Desfazer", color = NeonCyan) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Teste rápido", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = testeDesc, onValueChange = { testeDesc = it }, singleLine = true,
                        label = { Text("Digite uma descrição") }, modifier = Modifier.fillMaxWidth(), colors = camposCores()
                    )
                    if (testeDesc.isNotBlank()) {
                        Text(
                            teste?.let { "Categoria: ${it.categoria} (regra: ${Regras.rotuloMatch(it.tipoMatch)} “${it.padrao}”, prioridade ${it.prioridade})" }
                                ?: "Nenhuma regra se aplica a esta descrição.",
                            color = if (teste != null) NeonCyan else Color.White.copy(0.5f), fontSize = 12.sp
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = { tentar { previa = repository.previaRegrasExistentes() } },
                    enabled = regras.isNotEmpty()
                ) { Text("Aplicar às existentes…", color = NeonCyan) }
            }
            if (ordenadas.isEmpty()) {
                item { Text("Nenhuma regra ainda. Toque em + para criar (ex.: contém “uber” → Transporte).", color = Color.White.copy(0.5f), fontSize = 13.sp) }
            }
            items(ordenadas, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text(
                        "${Regras.rotuloMatch(r.tipoMatch)} “${r.padrao}” → ${r.categoria}",
                        color = if (r.ativa) Color.White else Color.White.copy(0.4f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    val detalhes = listOfNotNull(
                        "prioridade ${r.prioridade}",
                        r.conta?.let { "conta $it" },
                        r.tipo?.let { if (it == "DEBITO") "só despesas" else "só receitas" },
                        if (!r.ativa) "desativada" else null
                    ).joinToString(" · ")
                    Text(detalhes, color = Color.White.copy(0.55f), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { tentar { repository.ajustarPrioridadeRegra(r.id, 1) } }) { Icon(Icons.Default.ArrowUpward, "Aumentar prioridade", tint = NeonCyan) }
                        IconButton(onClick = { tentar { repository.ajustarPrioridadeRegra(r.id, -1) } }) { Icon(Icons.Default.ArrowDownward, "Diminuir prioridade", tint = NeonCyan) }
                        Switch(
                            checked = r.ativa, onCheckedChange = { tentar { repository.alternarRegra(r.id) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan.copy(0.5f), checkedThumbColor = NeonCyan)
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { editando = r }) { Icon(Icons.Default.Edit, "Editar", tint = Color.White.copy(0.7f)) }
                        IconButton(onClick = { excluir = r }) { Icon(Icons.Default.Delete, "Excluir", tint = NeonRed) }
                    }
                }
            }
        }
    }

    if (criando || editando != null) {
        FormularioRegra(
            inicial = editando, categorias = categorias, contas = contas.map { it.conta to it.banco },
            onDismiss = { criando = false; editando = null },
            onSalvar = { regra ->
                tentar { repository.salvarRegra(regra); aviso = null; criando = false; editando = null }
            }
        )
    }

    excluir?.let { r ->
        AlertDialog(
            onDismissRequest = { excluir = null }, containerColor = DeepSpaceBlue,
            title = { Text("Excluir regra", color = Color.White) },
            text = { Text("Excluir a regra “${r.padrao}”? Lançamentos já categorizados não mudam.", color = Color.White.copy(0.8f)) },
            confirmButton = { TextButton(onClick = { tentar { repository.excluirRegra(r.id) }; excluir = null }) { Text("Excluir", color = NeonRed) } },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }

    previa?.let { lista ->
        AlertDialog(
            onDismissRequest = { previa = null }, containerColor = DeepSpaceBlue,
            title = { Text("Aplicar às existentes", color = Color.White) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lista.isEmpty()) Text("Nenhum lançamento comum mudaria de categoria.", color = Color.White.copy(0.7f))
                    else {
                        Text("${lista.size} lançamento(s) comum(ns) mudariam de categoria. Nada foi alterado ainda.", color = Color.White)
                        lista.take(100).forEach { m ->
                            Text("${m.descricao}: ${m.de.ifBlank { "(sem categoria)" }} → ${m.para}", color = Color.White.copy(0.7f), fontSize = 12.sp)
                        }
                        if (lista.size > 100) Text("Mostrando 100 de ${lista.size}.", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = lista.isNotEmpty(),
                    onClick = { tentar { desfazer = repository.aplicarRegrasExistentes(lista) }; previa = null }
                ) { Text("Aplicar a ${lista.size}", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { previa = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioRegra(
    inicial: RegraCategoria?, categorias: List<String>, contas: List<Pair<String, String>>,
    onDismiss: () -> Unit, onSalvar: (RegraCategoria) -> Unit
) {
    var padrao by remember { mutableStateOf(inicial?.padrao.orEmpty()) }
    var tipoMatch by remember { mutableStateOf(inicial?.tipoMatch ?: Regras.CONTEM) }
    var categoria by remember { mutableStateOf(inicial?.categoria.orEmpty()) }
    var conta by remember { mutableStateOf(inicial?.conta) }
    var tipo by remember { mutableStateOf(inicial?.tipo) }
    var prioridade by remember { mutableStateOf((inicial?.prioridade ?: 0).toString()) }
    var ativa by remember { mutableStateOf(inicial?.ativa ?: true) }
    val opcoesCategoria = remember(categorias, inicial) {
        if (inicial != null && categorias.none { it.equals(inicial.categoria, true) }) listOf(inicial.categoria) + categorias else categorias
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (inicial == null) "Nova regra" else "Editar regra", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quando a descrição…", color = Color.White.copy(0.6f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Regras.TIPOS_MATCH.forEach { t ->
                        FilterChip(selected = tipoMatch == t, onClick = { tipoMatch = t }, label = { Text(Regras.rotuloMatch(t)) }, colors = chipCores())
                    }
                }
                OutlinedTextField(
                    value = padrao, onValueChange = { padrao = it.take(80) }, singleLine = true,
                    label = { Text("Texto (sem diferenciar acento/caixa)") }, modifier = Modifier.fillMaxWidth(), colors = camposCores()
                )
                Text("Usar a categoria", color = Color.White.copy(0.6f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    opcoesCategoria.forEach { c ->
                        FilterChip(selected = categoria.equals(c, true), onClick = { categoria = c }, label = { Text(c) }, colors = chipCores())
                    }
                }
                Text("Só na conta", color = Color.White.copy(0.6f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = conta == null, onClick = { conta = null }, label = { Text("Qualquer") }, colors = chipCores())
                    contas.forEach { (numero, banco) ->
                        FilterChip(selected = conta == numero, onClick = { conta = numero }, label = { Text(banco) }, colors = chipCores())
                    }
                }
                Text("Só em", color = Color.White.copy(0.6f), fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tipo == null, onClick = { tipo = null }, label = { Text("Despesas e receitas") }, colors = chipCores())
                    FilterChip(selected = tipo == "DEBITO", onClick = { tipo = "DEBITO" }, label = { Text("Despesas") }, colors = chipCores())
                    FilterChip(selected = tipo == "CREDITO", onClick = { tipo = "CREDITO" }, label = { Text("Receitas") }, colors = chipCores())
                }
                OutlinedTextField(
                    value = prioridade, onValueChange = { prioridade = it.filter { ch -> ch.isDigit() || ch == '-' }.take(6) }, singleLine = true,
                    label = { Text("Prioridade (maior vence)") }, modifier = Modifier.fillMaxWidth(), colors = camposCores(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Regra ativa", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ativa, onCheckedChange = { ativa = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan.copy(0.5f), checkedThumbColor = NeonCyan)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSalvar(
                    RegraCategoria(
                        id = inicial?.id ?: 0, padrao = padrao, tipoMatch = tipoMatch, categoria = categoria, conta = conta, tipo = tipo,
                        ativa = ativa, prioridade = prioridade.toIntOrNull() ?: 0, criadaEm = inicial?.criadaEm ?: 0L
                    )
                )
            }) { Text("Salvar", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
