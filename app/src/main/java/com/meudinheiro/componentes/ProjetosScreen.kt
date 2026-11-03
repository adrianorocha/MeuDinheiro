package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.meudinheiro.data.Projeto
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Projetos
import com.meudinheiro.domain.StatusProjeto
import com.meudinheiro.domain.Tags
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.RegraFinanceiraException
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardFundo = Color(0xFF1B263B)
private val TextoClaro = Color(0xFFE0E1DD)
private val FORMATO_DATA get() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }

private fun dataOuNulo(texto: String, fimDoDia: Boolean): Long? {
    if (texto.isBlank()) return null
    val d = runCatching { FORMATO_DATA.parse(texto.trim()) }.getOrNull() ?: return null
    return if (fimDoDia) d.time + 86_399_999L else d.time
}

/**
 * R56 — projetos (agrupador por tag): gasto total (lançamentos comuns com a tag; estorno de cartão abate), % do orçamento
 * com barra e status, por categoria, por mês e a lista de lançamentos. CRUD completo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjetosScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val projetos by repository.obterProjetosFlow().collectAsState(initial = emptyList())
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())

    var criando by remember { mutableStateOf(false) }
    var tagInicial by remember { mutableStateOf<String?>(null) }
    var editando by remember { mutableStateOf<Projeto?>(null) }
    var excluir by remember { mutableStateOf<Projeto?>(null) }
    var aberto by remember { mutableStateOf<Int?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val resumos = remember(projetos, despesas) { projetos.map { Projetos.resumo(it, despesas) } }
    val semProjeto = remember(projetos, despesas) {
        val com = projetos.map { Tags.chave(it.tag) }.toSet()
        Tags.usadas(despesas).filter { it.chave !in com }.take(8)
    }
    fun moeda(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Projetos", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                actions = { IconButton(onClick = { tagInicial = null; criando = true }) { Icon(Icons.Default.Add, "Novo projeto", tint = NeonCyan) } },
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
                    "Marque lançamentos com uma tag (ex.: #reforma) e crie um projeto com a mesma tag para acompanhar o total, o orçamento e a divisão por categoria e mês.",
                    color = Color.White.copy(0.6f), fontSize = 12.sp
                )
            }
            aviso?.let { msg -> item { Text(msg, color = NeonRed, fontSize = 13.sp) } }
            if (resumos.isEmpty()) item { Text("Nenhum projeto ainda. Toque em + para criar.", color = Color.White.copy(0.5f), fontSize = 13.sp) }
            items(resumos, key = { it.projeto.id }) { r ->
                val p = r.projeto
                val cor = when (r.status) {
                    StatusProjeto.ESTOUROU -> NeonRed
                    StatusProjeto.ATENCAO -> NeonOrange
                    else -> NeonGreen
                }
                Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.nome + if (!p.ativo) " (inativo)" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("#${p.tag}", color = NeonCyan, fontSize = 12.sp)
                        }
                        IconButton(onClick = { editando = p }) { Icon(Icons.Default.Edit, "Editar", tint = Color.White.copy(0.7f)) }
                        IconButton(onClick = { excluir = p }) { Icon(Icons.Default.Delete, "Excluir", tint = NeonRed) }
                    }
                    Text(
                        moeda(r.gastoCentavos) + (r.orcamentoCentavos?.let { " de ${moeda(it)}" } ?: ""),
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                    )
                    r.percentual?.let { pct ->
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(0.08f), RoundedCornerShape(5.dp))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(pct.toFloat().coerceIn(0.01f, 1f)).background(cor, RoundedCornerShape(5.dp)))
                        }
                        Text(
                            "${Math.round(pct * 100)}% do orçamento · " + when (r.status) {
                                StatusProjeto.ESTOUROU -> "orçamento estourado"
                                StatusProjeto.ATENCAO -> "perto do limite"
                                else -> "dentro do orçamento"
                            },
                            color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    } ?: Text("Sem orçamento definido", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    Text("${r.quantidade} lançamento(s)" + if (p.inicio != null || p.fim != null) " no período do projeto" else "", color = Color.White.copy(0.6f), fontSize = 11.sp)

                    if (r.quantidade > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Por categoria", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        r.porCategoria.forEach { (nome, c) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(nome, color = Color.White, fontSize = 12.sp)
                                Text(moeda(c), color = Color.White.copy(0.8f), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Por mês", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        r.porMes.forEach { (mes, c) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(mes, color = Color.White, fontSize = 12.sp)
                                Text(moeda(c), color = Color.White.copy(0.8f), fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { aberto = if (aberto == p.id) null else p.id }) {
                            Text(if (aberto == p.id) "Ocultar lançamentos" else "Ver lançamentos", color = NeonCyan)
                        }
                        if (aberto == p.id) {
                            val fmt = remember { SimpleDateFormat("dd/MM/yy", Locale("pt", "BR")) }
                            r.itens.take(200).forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${fmt.format(Date(d.dataMs))} · ${d.descricao}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(formatarMoedaBR(if (d.tipo.name == "CREDITO") -d.valor else d.valor, isPrivate), color = Color.White.copy(0.8f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (semProjeto.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text("Tags sem projeto", color = Color.White, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            semProjeto.forEach { t ->
                                AssistChip(onClick = { tagInicial = t.rotulo; criando = true }, label = { Text("#${t.rotulo} (${t.usos}) — criar projeto", fontSize = 12.sp) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (criando || editando != null) {
        FormularioProjeto(
            inicial = editando, tagInicial = tagInicial,
            onDismiss = { criando = false; editando = null; tagInicial = null },
            onSalvar = { projeto ->
                scope.launch {
                    try {
                        repository.salvarProjeto(projeto)
                        aviso = null; criando = false; editando = null; tagInicial = null
                    } catch (e: RegraFinanceiraException) { aviso = e.message }
                }
            }
        )
    }

    excluir?.let { p ->
        AlertDialog(
            onDismissRequest = { excluir = null }, containerColor = DeepSpaceBlue,
            title = { Text("Excluir projeto", color = Color.White) },
            text = { Text("Excluir o projeto “${p.nome}”? Os lançamentos e suas tags continuam como estão.", color = Color.White.copy(0.8f)) },
            confirmButton = { TextButton(onClick = { scope.launch { repository.excluirProjeto(p.id) }; excluir = null }) { Text("Excluir", color = NeonRed) } },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }
}

@Composable
private fun FormularioProjeto(inicial: Projeto?, tagInicial: String?, onDismiss: () -> Unit, onSalvar: (Projeto) -> Unit) {
    var nome by remember { mutableStateOf(inicial?.nome ?: tagInicial.orEmpty()) }
    var tag by remember { mutableStateOf(inicial?.tag ?: tagInicial.orEmpty()) }
    var orcamento by remember { mutableStateOf(inicial?.orcamento?.let { Math.round(it * 100).toString() }.orEmpty()) }
    var inicio by remember { mutableStateOf(inicial?.inicio?.let { FORMATO_DATA.format(Date(it)) }.orEmpty()) }
    var fim by remember { mutableStateOf(inicial?.fim?.let { FORMATO_DATA.format(Date(it)) }.orEmpty()) }
    var ativo by remember { mutableStateOf(inicial?.ativo ?: true) }
    var erro by remember { mutableStateOf<String?>(null) }
    val cores = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
        unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan, focusedLabelColor = NeonCyan,
        unfocusedLabelColor = Color.White.copy(0.5f)
    )

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text(if (inicial == null) "Novo projeto" else "Editar projeto", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it.take(60) }, singleLine = true, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), colors = cores)
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it.take(Tags.MAX_CHARS + 1) }, singleLine = true,
                    label = { Text("Tag do projeto") }, supportingText = { Text("Lançamentos com esta tag entram no projeto", color = Color.White.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth(), colors = cores
                )
                OutlinedTextField(
                    value = orcamento, onValueChange = { orcamento = it.filter(Char::isDigit).take(12) }, singleLine = true,
                    label = { Text("Orçamento em centavos (opcional)") }, modifier = Modifier.fillMaxWidth(), colors = cores,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { orcamento.toLongOrNull()?.let { Text(formatarMoedaBR(Dinheiro.reais(it), false), color = NeonCyan) } }
                )
                OutlinedTextField(value = inicio, onValueChange = { inicio = it.take(10) }, singleLine = true, label = { Text("Início dd/mm/aaaa (opcional)") }, modifier = Modifier.fillMaxWidth(), colors = cores)
                OutlinedTextField(value = fim, onValueChange = { fim = it.take(10) }, singleLine = true, label = { Text("Fim dd/mm/aaaa (opcional)") }, modifier = Modifier.fillMaxWidth(), colors = cores)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Projeto ativo", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = ativo, onCheckedChange = { ativo = it }, colors = SwitchDefaults.colors(checkedTrackColor = NeonCyan.copy(0.5f), checkedThumbColor = NeonCyan))
                }
                erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ini = dataOuNulo(inicio, false)
                val f = dataOuNulo(fim, true)
                if ((inicio.isNotBlank() && ini == null) || (fim.isNotBlank() && f == null)) {
                    erro = "Informe datas válidas (dd/mm/aaaa)."
                } else {
                    erro = null
                    onSalvar(
                        Projeto(
                            id = inicial?.id ?: 0, nome = nome, tag = tag, orcamento = orcamento.toLongOrNull()?.let { Dinheiro.reais(it) },
                            inicio = ini, fim = f, ativo = ativo, cor = inicial?.cor
                        )
                    )
                }
            }) { Text("Salvar", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}
