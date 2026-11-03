package com.meudinheiro.componentes

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meudinheiro.captura.CapturaConfig
import com.meudinheiro.captura.CategoriasAprendidas
import com.meudinheiro.captura.NotificadorSugestoes
import com.meudinheiro.captura.SugestoesStore
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.domain.CategoriaAprendida
import com.meudinheiro.domain.MemoriaCategorias
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.LancamentoRapido
import com.meudinheiro.domain.NotificacaoBancaria
import com.meudinheiro.domain.NotificacaoBancaria.Estado
import com.meudinheiro.domain.NotificacaoBancaria.SugestaoGuardada
import com.meudinheiro.domain.RevisaoLote
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Claro = Color(0xFFE0E1DD)
private val FundoCartao = Color(0xFF1B263B)

/** Estado editável de uma linha da revisão. */
@Stable
private class ItemRevisao(val g: SugestaoGuardada, descricao: String, categoria: String?, origem: CategoriaAprendida.Origem, destino: RevisaoLote.Destino) {
    var descricao by mutableStateOf(descricao)
    var categoria by mutableStateOf(categoria)
    var origemCategoria by mutableStateOf(origem)
    var destino by mutableStateOf(destino)
    var selecionado by mutableStateOf(false)
    var erro by mutableStateOf<String?>(null)
    fun edicao() = RevisaoLote.Edicao(descricao, categoria, destino)
}

private class DadosRevisao(
    val contas: List<ContaSaldo>,
    val cartoes: List<Cartao>,
    val categorias: List<Pair<String, String>>, // nome -> pic
    val regras: List<com.meudinheiro.data.RegraCategoria>,
    val historico: List<com.meudinheiro.data.Despesa>,
    val memoria: MemoriaCategorias,
    val ultimaChave: String?,
    val exigirBiometria: Boolean
) {
    fun rotuloDestino(d: RevisaoLote.Destino): String = when {
        d.cartaoId != null -> cartoes.firstOrNull { it.id == d.cartaoId }?.let { "💳 ${it.nome}" } ?: "Cartão removido"
        else -> contas.firstOrNull { it.conta == d.conta }?.banco ?: "Escolher conta"
    }
}

private suspend fun carregarDados(ctx: Context, repo: MainRepository): DadosRevisao = withContext(Dispatchers.IO) {
    val contas = repo.obterTodasStatic().sortedBy { it.banco }
    val cartoes = repo.cartoesFlow().first().sortedBy { it.nome }
    val custom = repo.obterCategoriasCustom().first().map { it.title to it.pic }
    val padrao = repo.categorias.map { it.title to it.pic }
    val historico = repo.todasDespesasFlow.first()
    val todas = (custom + padrao).distinctBy { it.first.trim().lowercase() }
    val ordem = LancamentoRapido.categoriasPorUso(todas.map { it.first }, historico, com.meudinheiro.data.TipoDespesa.DEBITO)
    DadosRevisao(
        contas = contas, cartoes = cartoes,
        categorias = ordem.map { n -> todas.first { it.first.equals(n, true) } },
        regras = runCatching { repo.obterRegras() }.getOrDefault(emptyList()),
        historico = historico,
        memoria = CategoriasAprendidas.memoria(ctx),
        ultimaChave = ctx.getSharedPreferences(LancamentoRapidoActivity.PREFS, Context.MODE_PRIVATE).getString(LancamentoRapidoActivity.KEY_ORIGEM, null),
        exigirBiometria = runCatching { UserPreferences(ctx).biometriaLancarFlow.first() }.getOrDefault(true)
    )
}

private fun novaLinha(g: SugestaoGuardada, d: DadosRevisao): ItemRevisao {
    val destino = RevisaoLote.destinoPadrao(g, d.contas.map { it.conta }, d.cartoes.map { it.id }, d.ultimaChave)
    val titulo = RevisaoLote.descricaoInicial(g)
    val escolha = if (g.entrada) CategoriaAprendida.Escolha(null, CategoriaAprendida.Origem.NENHUMA)
    else CategoriaAprendida.escolher(
        descricao = g.descricao, titulo = titulo, conta = destino.conta ?: d.cartoes.firstOrNull { it.id == destino.cartaoId }
            ?.let { c -> d.contas.firstOrNull { it.id == c.contaId }?.conta },
        regras = d.regras, memoria = d.memoria, historico = d.historico, categoriasValidas = d.categorias.map { it.first }
    )
    return ItemRevisao(g, titulo, escolha.categoria, escolha.origem, destino)
}

@Composable
private fun AoRetomarRevisao(acao: () -> Unit) {
    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) acao() }
        dono.lifecycle.addObserver(obs)
        onDispose { dono.lifecycle.removeObserver(obs) }
    }
}

/**
 * R89 — "Sugestões de lançamento" com REVISÃO EM LOTE: marque várias, ajuste descrição/categoria/destino na própria linha e
 * "Confirmar selecionadas" (uma biometria para o lote) ou "Descartar selecionadas". Nada vira lançamento sem esse toque.
 * "Formulário completo" mantém o fluxo individual do R64.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SugestoesLancamentoScreen(isPrivate: Boolean, repository: MainRepository? = null, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    val repo = remember { repository ?: MainRepository(ctx) }
    var versao by remember { mutableIntStateOf(0) }
    var dados by remember { mutableStateOf<DadosRevisao?>(null) }
    val itens = remember { mutableStateListOf<ItemRevisao>() }
    var ocupado by remember { mutableStateOf(false) }
    AoRetomarRevisao { versao++ }

    // Recarrega a fila preservando o que o usuário já editou nas linhas que continuam pendentes.
    LaunchedEffect(versao) {
        val d = carregarDados(ctx, repo)
        val pend = withContext(Dispatchers.IO) { SugestoesStore.pendentes(ctx) }
        dados = d
        val porId = itens.associateBy { it.g.id }
        val novos = pend.sortedByDescending { it.horarioMs }.map { porId[it.id] ?: novaLinha(it, d) }
        itens.clear(); itens.addAll(novos)
    }

    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }
    val capturaLigada = remember(versao) { CapturaConfig.ativo(ctx) }
    val selecionados = itens.filter { it.selecionado }

    fun descartar() {
        val sel = itens.filter { it.selecionado }
        if (sel.isEmpty()) return
        sel.forEach {
            SugestoesStore.marcar(ctx, it.g.id, Estado.IGNORADA)
            NotificadorSugestoes.cancelar(ctx, it.g.id)
        }
        itens.removeAll(sel.toSet())
        Toast.makeText(ctx, if (sel.size == 1) "1 sugestão descartada." else "${sel.size} sugestões descartadas.", Toast.LENGTH_SHORT).show()
    }

    fun confirmar() {
        val d = dados ?: return
        val sel = itens.filter { it.selecionado }
        if (sel.isEmpty() || ocupado) return
        sel.forEach { it.erro = RevisaoLote.validar(it.g, it.edicao()) }
        val validos = sel.filter { it.erro == null }
        if (validos.isEmpty()) {
            Toast.makeText(ctx, RevisaoLote.mensagemResultado(0, sel.size), Toast.LENGTH_LONG).show()
            return
        }
        ocupado = true
        autenticarParaLancar(
            ctx, d.exigirBiometria, "Confirmar lançamentos",
            "Autentique para lançar ${validos.size} sugestão(ões)",
            onNegado = { ocupado = false; Toast.makeText(ctx, "Autenticação necessária para lançar.", Toast.LENGTH_SHORT).show() }
        ) {
            escopo.launch {
                var criados = 0
                var erros = sel.size - validos.size
                for (it in validos) {
                    val e = it.edicao()
                    val pic = (if (it.g.entrada) d.categorias.firstOrNull { c -> c.first.equals(RevisaoLote.CATEGORIA_RECEITA, true) }
                    else d.categorias.firstOrNull { c -> c.first.equals(e.categoria, true) })?.second.orEmpty()
                    val lanc = RevisaoLote.montar(it.g, e, pic)
                    val modalidade = if (e.destino.cartaoId != null) RevisaoLote.modalidade(it.g) else null
                    val r = withContext(Dispatchers.IO) { runCatching { repo.registrarLancamento(lanc, modalidade) } }
                    if (r.isSuccess) {
                        criados++
                        SugestoesStore.marcar(ctx, it.g.id, Estado.ENCAMINHADA)
                        NotificadorSugestoes.cancelar(ctx, it.g.id)
                        RevisaoLote.paraAprender(it.g, e)?.let { (est, cat) -> CategoriasAprendidas.aprender(ctx, est, cat) }
                        itens.remove(it)
                    } else {
                        erros++
                        it.erro = r.exceptionOrNull()?.message ?: "Não foi possível salvar."
                    }
                }
                if (criados > 0) runCatching { SaldoWidget().updateAll(ctx) }
                Toast.makeText(ctx, RevisaoLote.mensagemResultado(criados, erros), Toast.LENGTH_LONG).show()
                ocupado = false
            }
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Sugestões de lançamento", color = Claro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Claro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (itens.isNotEmpty()) Column(Modifier.fillMaxWidth().background(FundoCartao).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selecionados.size == itens.size && itens.isNotEmpty(),
                        onCheckedChange = { v -> itens.forEach { it.selecionado = v } },
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan, uncheckedColor = Claro.copy(0.5f), checkmarkColor = DeepSpaceBlue)
                    )
                    Text("Selecionar todas", color = Claro, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = ::confirmar, enabled = selecionados.isNotEmpty() && !ocupado, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)
                    ) { Text(RevisaoLote.rotuloConfirmar(selecionados.size), fontSize = 12.sp, maxLines = 1) }
                    OutlinedButton(onClick = ::descartar, enabled = selecionados.isNotEmpty() && !ocupado, modifier = Modifier.weight(1f)) {
                        Text(RevisaoLote.rotuloDescartar(selecionados.size), color = NeonRed, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (capturaLigada) "Criadas a partir das notificações dos apps permitidos. Nada é lançado sem você confirmar; expiram em 7 dias. " +
                        "Ajuste descrição, categoria e conta na linha, marque as que quer e confirme."
                    else "A captura está desligada (Configurações > Captura de notificações do banco).",
                    color = Claro.copy(0.6f), fontSize = 12.sp
                )
            }
            if (itens.isEmpty()) item { Text("Nenhuma sugestão pendente.", color = Claro.copy(0.7f), modifier = Modifier.padding(top = 12.dp)) }
            val d = dados
            if (d != null) items(itens.toList(), key = { it.g.id }) { it ->
                LinhaSugestao(it, d, isPrivate, formato, rotuloDoApp(ctx, it.g.pacote)) {
                    runCatching { ctx.startActivity(NotificadorSugestoes.intentConfirmar(ctx, it.g)) }
                }
            }
            if (itens.isNotEmpty()) item {
                TextButton(onClick = {
                    NotificadorSugestoes.cancelarTodas(ctx, SugestoesStore.todas(ctx).map { it.id })
                    SugestoesStore.apagarTudo(ctx); itens.clear()
                }) { Text("Apagar todas as sugestões", color = NeonRed) }
            }
        }
    }
}

@Composable
private fun LinhaSugestao(it: ItemRevisao, d: DadosRevisao, isPrivate: Boolean, formato: SimpleDateFormat, app: String, abrirFormulario: () -> Unit) {
    val g = it.g
    var menuCategoria by remember { mutableStateOf(false) }
    var menuDestino by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(FundoCartao, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = it.selecionado, onCheckedChange = { v -> it.selecionado = v },
                colors = CheckboxDefaults.colors(checkedColor = NeonCyan, uncheckedColor = Claro.copy(0.5f), checkmarkColor = DeepSpaceBlue)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedTextField(
                    value = it.descricao, onValueChange = { v -> it.descricao = v.take(60); it.erro = null },
                    singleLine = true, label = { Text("Descrição") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f), cursorColor = NeonCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    g.tipo.rotulo + " · " + formato.format(Date(g.horarioMs)) + " · $app" + (g.finalCartao?.let { " · final $it" } ?: "") +
                        if (g.confianca == NotificacaoBancaria.Confianca.BAIXA) " · revise" else "",
                    color = Color.White.copy(0.5f), fontSize = 11.sp
                )
            }
            Text(
                (if (g.entrada) "+ " else "- ") + if (isPrivate) "R$ ••••" else FormatoCentavos.brl(g.valorCentavos),
                color = if (g.entrada) NeonGreen else Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 8.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!g.entrada) Box {
                OutlinedButton(onClick = { menuCategoria = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(it.categoria?.let { c -> "Categoria: $c ▾" } ?: "Categoria ▾", color = NeonCyan, fontSize = 12.sp, maxLines = 1)
                }
                DropdownMenu(expanded = menuCategoria, onDismissRequest = { menuCategoria = false }) {
                    d.categorias.forEach { (nome, _) ->
                        DropdownMenuItem(text = { Text(nome) }, onClick = {
                            it.categoria = nome; it.origemCategoria = CategoriaAprendida.Origem.NENHUMA; it.erro = null; menuCategoria = false
                        })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { menuDestino = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(d.rotuloDestino(it.destino) + " ▾", color = NeonGreen, fontSize = 12.sp, maxLines = 1)
                }
                DropdownMenu(expanded = menuDestino, onDismissRequest = { menuDestino = false }) {
                    d.contas.forEach { c ->
                        DropdownMenuItem(text = { Text(c.banco) }, onClick = { it.destino = RevisaoLote.Destino(conta = c.conta); it.erro = null; menuDestino = false })
                    }
                    if (!g.entrada) d.cartoes.forEach { c ->
                        DropdownMenuItem(text = { Text("💳 ${c.nome}") }, onClick = { it.destino = RevisaoLote.Destino(cartaoId = c.id); it.erro = null; menuDestino = false })
                    }
                }
            }
        }
        if (!g.entrada) when (it.origemCategoria) {
            CategoriaAprendida.Origem.APRENDIDA -> Text("categoria aprendida (troque se não for esta)", color = NeonGreen, fontSize = 11.sp)
            CategoriaAprendida.Origem.REGRA -> Text("categoria por regra automática", color = NeonCyan, fontSize = 11.sp)
            CategoriaAprendida.Origem.HISTORICO -> Text("categoria sugerida pelo histórico", color = Color.White.copy(0.5f), fontSize = 11.sp)
            CategoriaAprendida.Origem.NENHUMA -> Unit
        }
        it.erro?.let { msg -> Text(msg, color = Color(0xFFFF8A80), fontSize = 12.sp) }
        TextButton(onClick = abrirFormulario, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("Abrir formulário completo", color = Claro.copy(0.7f), fontSize = 12.sp)
        }
    }
}
