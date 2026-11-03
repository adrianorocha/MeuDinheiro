package com.meudinheiro.componentes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.DestinoSugerido
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.ImportacaoExtrato
import com.meudinheiro.domain.Natureza
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.importacao.ExtratosPendentes
import com.meudinheiro.importacao.PastaExtratos
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Claro = Color(0xFFE0E1DD)
private val FundoCartao = Color(0xFF1B263B)

/**
 * R63 — revisão de extratos recebidos (compartilhados ou da pasta monitorada). Lê OFX; NUNCA cria lançamento sem
 * o usuário marcar as transações e confirmar. A idempotência por `fitid` (R38) impede reimportar o que já entrou.
 * A conciliação completa (casar com lançamentos existentes, CSV, cartão) continua no portal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportacaoExtratosScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    var aberto by remember { mutableStateOf<ExtratosPendentes.Pendente?>(null) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pendentes = remember(versao) { ExtratosPendentes.listar(ctx) }

    BackHandler { if (aberto != null) aberto = null else onBack() }

    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch {
            val r = withContext(Dispatchers.IO) { uris.take(ImportacaoExtrato.MAX_ARQUIVOS_POR_ENVIO).map { ExtratosPendentes.guardarDeUri(ctx, it, null) } }
            val recusados = r.filterIsInstance<ExtratosPendentes.Resultado.Recusado>()
            mensagem = if (recusados.isEmpty()) null else recusados.joinToString("\n") { "${it.nome}: ${it.motivo}" }
            versao++
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text(if (aberto == null) "Importar extratos" else "Revisar extrato", color = Claro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { if (aberto != null) aberto = null else onBack() }) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Claro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val atual = aberto
        if (atual != null) {
            RevisaoExtrato(
                repository = repository, pendente = atual, isPrivate = isPrivate, modifier = Modifier.padding(padding),
                onConcluido = { aberto = null; versao++ }
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Compartilhe um extrato (OFX) do app do banco, do e-mail ou do Drive para o MeuDinheiro, ou escolha o arquivo abaixo. " +
                            "Nada é lançado sem a sua confirmação.",
                        color = Claro.copy(0.7f), fontSize = 13.sp
                    )
                }
                item {
                    Button(
                        onClick = { escolher.launch(arrayOf("application/x-ofx", "application/ofx", "text/csv", "text/plain", "application/octet-stream")) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Escolher arquivos") }
                }
                mensagem?.let { m -> item { Text(m, color = NeonOrange, fontSize = 12.sp) } }
                if (pendentes.isEmpty()) item { Text("Nenhum extrato aguardando revisão.", color = Claro.copy(0.6f)) }
                items(pendentes, key = { it.id }) { p ->
                    Column(
                        Modifier.fillMaxWidth().background(FundoCartao, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(p.nome, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${p.tamanho / 1024 + 1} KB", color = Claro.copy(0.5f), fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { aberto = p }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)) { Text("Revisar") }
                            OutlinedButton(
                                onClick = { ExtratosPendentes.remover(ctx, p.id); versao++ },
                                border = BorderStroke(1.dp, NeonRed.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                            ) { Text("Descartar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisaoExtrato(
    repository: MainRepository,
    pendente: ExtratosPendentes.Pendente,
    isPrivate: Boolean,
    modifier: Modifier,
    onConcluido: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exigirBio by remember { UserPreferences(ctx).biometriaLancarFlow }.collectAsState(initial = true)
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())

    val texto = remember(pendente.id) { ExtratosPendentes.ler(ctx, pendente.id)?.let { ImportacaoExtrato.decodificar(it) } }
    val ofx = texto != null && ImportacaoExtrato.pareceOfx(texto)
    val extrato = remember(texto) { if (texto != null && ofx) ImportacaoExtrato.lerOfx(texto) else null }
    val identificacao = remember(texto, contas, cartoes) {
        if (texto == null) null
        else DestinoSugerido.identificar(
            contas.map { DestinoSugerido.ContaRef(it.id, it.banco, it.agencia, it.conta) },
            cartoes.map { DestinoSugerido.CartaoRef(it.id, it.nome, it.finalCartao, it.contaId, it.cartaoPrincipalId) },
            DestinoSugerido.pistasDoArquivo(pendente.nome, texto)
        )
    }
    var contaEscolhida by remember(identificacao) { mutableStateOf<String?>((identificacao?.destino as? DestinoSugerido.Destino.Conta)?.conta) }
    val sugeriuCartao = identificacao?.destino is DestinoSugerido.Destino.Cartao || (extrato?.ehCartao == true)
    val existentes = remember(despesas) {
        despesas.map { ImportacaoExtrato.LancamentoRef(it.conta, it.valor, it.tipo == TipoDespesa.CREDITO, it.data.time, it.fitid, it.cartaoId) }
    }
    val situacoes = remember(extrato, contaEscolhida, existentes) {
        val c = contaEscolhida
        extrato?.transacoes?.associate { t -> t.fitid to if (c == null) ImportacaoExtrato.Situacao.NOVA else ImportacaoExtrato.situacao(t, c, existentes) } ?: emptyMap()
    }
    val marcadas = remember(pendente.id, contaEscolhida) { mutableStateMapOf<String, Boolean>() }
    fun marcada(fitid: String) = marcadas[fitid] ?: (situacoes[fitid] == ImportacaoExtrato.Situacao.NOVA)

    var importando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    fun m(v: Double) = formatarMoedaBR(v, isPrivate)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(pendente.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (texto == null) {
            item { Text("Não foi possível abrir este arquivo.", color = NeonRed) }
        } else if (extrato == null) {
            item { Text("Este arquivo é CSV: o app ainda não lê CSV. Abra a conciliação no portal (aba Conciliação) para importá-lo com o mapeamento de colunas.", color = NeonOrange, fontSize = 13.sp) }
        } else {
            item {
                Text(
                    "${extrato.transacoes.size} transação(ões)" + (extrato.periodo?.let { " · ${fmt.format(Date(it.first))} a ${fmt.format(Date(it.second))}" } ?: ""),
                    color = Claro.copy(0.8f), fontSize = 13.sp
                )
                if (extrato.semFitid > 0) Text("${extrato.semFitid} transação(ões) sem identificador (FITID) não são importadas aqui; use o portal.", color = NeonOrange, fontSize = 12.sp)
            }
            if (sugeriuCartao) {
                item { Text("Este extrato é de cartão de crédito. A conciliação de fatura é feita no portal; aqui só importamos extrato de conta.", color = NeonOrange, fontSize = 13.sp) }
            } else {
                item {
                    Text("Conta de destino", color = Claro.copy(0.6f), fontSize = 12.sp)
                    when (identificacao?.estado) {
                        DestinoSugerido.Estado.UNICO -> Text("Identificada pelo arquivo. Confirme.", color = NeonGreen, fontSize = 12.sp)
                        DestinoSugerido.Estado.AMBIGUO -> Text("Mais de uma conta combina com o arquivo: escolha.", color = NeonOrange, fontSize = 12.sp)
                        else -> Text("Não deu para identificar a conta pelo arquivo: escolha.", color = NeonOrange, fontSize = 12.sp)
                    }
                }
                items(contas, key = { it.id }) { c ->
                    val sel = contaEscolhida == c.conta
                    OutlinedButton(
                        onClick = { contaEscolhida = c.conta },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (sel) NeonCyan else Claro.copy(0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (sel) NeonCyan else Claro)
                    ) { Text("${c.banco} · ${c.conta}") }
                }
                if (contaEscolhida != null) {
                    items(extrato.transacoes, key = { it.fitid }) { t ->
                        val s = situacoes[t.fitid] ?: ImportacaoExtrato.Situacao.NOVA
                        Row(
                            Modifier.fillMaxWidth().background(FundoCartao, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(checked = marcada(t.fitid) && s != ImportacaoExtrato.Situacao.JA_IMPORTADA, enabled = s != ImportacaoExtrato.Situacao.JA_IMPORTADA, onCheckedChange = { marcadas[t.fitid] = it })
                            Column(Modifier.weight(1f)) {
                                Text(t.descricao, color = Color.White, fontSize = 13.sp, maxLines = 2)
                                Text(
                                    fmt.format(Date(t.dataMs)) + when (s) {
                                        ImportacaoExtrato.Situacao.JA_IMPORTADA -> " · já importada"
                                        ImportacaoExtrato.Situacao.POSSIVEL_DUPLICADA -> " · parece um lançamento que você já fez"
                                        else -> ""
                                    },
                                    color = if (s == ImportacaoExtrato.Situacao.NOVA) Claro.copy(0.5f) else NeonOrange, fontSize = 11.sp
                                )
                            }
                            Text(m(t.valor), color = if (t.valor < 0) NeonRed else NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    item {
                        val selecionadas = extrato.transacoes.filter { marcada(it.fitid) && situacoes[it.fitid] != ImportacaoExtrato.Situacao.JA_IMPORTADA }
                        erro?.let { Text(it, color = NeonRed, fontSize = 12.sp) }
                        Button(
                            enabled = selecionadas.isNotEmpty() && !importando,
                            onClick = {
                                val conta = contaEscolhida ?: return@Button
                                autenticarParaLancar(ctx, exigirBio, "Importar extrato", "Autentique para lançar as transações marcadas") {
                                    scope.launch {
                                        importando = true; erro = null
                                        var criados = 0
                                        val agora = System.currentTimeMillis()
                                        for (t in selecionadas) {
                                            runCatching {
                                                val tipo = if (t.valor < 0) TipoDespesa.DEBITO else TipoDespesa.CREDITO
                                                val categoria = runCatching { repository.categoriaPorRegras(t.descricao, conta, tipo) }.getOrNull() ?: "Outros"
                                                repository.registrarLancamento(
                                                    Despesa(
                                                        descricao = t.descricao, valor = kotlin.math.abs(t.valor), data = Date(t.dataMs), categoria = categoria,
                                                        conta = conta, pic = repository.getPicCategoria(categoria), tipo = tipo,
                                                        mes = Financas.mesDe(t.dataMs), ano = Financas.anoDe(t.dataMs), pago = true,
                                                        natureza = Natureza.NORMAL, autor = "Importação", fitid = t.fitid, conciliadoEm = agora
                                                    )
                                                )
                                            }.onSuccess { criados++ }.onFailure { erro = it.message ?: "Falha ao lançar uma transação." }
                                        }
                                        importando = false
                                        if (criados == selecionadas.size) {
                                            ExtratosPendentes.remover(ctx, pendente.id)
                                            onConcluido()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (importando) "Importando…" else "Lançar ${selecionadas.size} transação(ões)") }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { ExtratosPendentes.remover(ctx, pendente.id); onConcluido() },
                border = BorderStroke(1.dp, NeonRed.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Descartar arquivo") }
        }
    }
}

/** Configurações > "Importação de extratos": pasta monitorada (opcional) e acesso à revisão. */
@Composable
fun SecaoImportacaoExtratos(repository: MainRepository, userPrefs: UserPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var versao by remember { mutableIntStateOf(0) }
    var revisando by remember { mutableStateOf(false) }
    val isPrivate by userPrefs.privateModeFlow.collectAsState(initial = false)
    val pasta = remember(versao) { PastaExtratos.nomePasta(ctx) }
    val ativo = remember(versao) { PastaExtratos.ativo(ctx) }
    val pendentes = remember(versao, revisando) { ExtratosPendentes.contar(ctx) }

    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) scope.launch { PastaExtratos.definirPasta(ctx, uri); versao++ }
    }

    PremiumConfigCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Compartilhe um extrato OFX para o MeuDinheiro ou escolha uma pasta (onde o banco/navegador salva) para ser verificada a cada 6 horas. " +
                    "O app só avisa; nunca lança nada sozinho. Ele lê apenas a pasta escolhida.",
                color = TextWhite.copy(0.6f), fontSize = 12.sp
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Verificar pasta automaticamente", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    Text(pasta?.let { "Pasta: $it" } ?: "Nenhuma pasta escolhida", color = TextWhite.copy(0.5f), fontSize = 12.sp)
                }
                Switch(
                    checked = ativo, enabled = pasta != null,
                    onCheckedChange = { PastaExtratos.ligar(ctx, it); versao++ },
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepSpaceBlue, checkedTrackColor = NeonCyan)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { escolherPasta.launch(null) }) { Text(if (pasta == null) "Escolher pasta" else "Trocar pasta") }
                if (pasta != null) OutlinedButton(onClick = { PastaExtratos.limparPasta(ctx); versao++ }) { Text("Limpar pasta") }
            }
            Button(
                onClick = { revisando = true },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (pendentes > 0) "Revisar extratos ($pendentes)" else "Importar extratos") }
        }
    }
    if (revisando) {
        Dialog(onDismissRequest = { revisando = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = DeepSpaceBlue) {
                ImportacaoExtratosScreen(repository = repository, isPrivate = isPrivate, onBack = { revisando = false })
            }
        }
    }
}
