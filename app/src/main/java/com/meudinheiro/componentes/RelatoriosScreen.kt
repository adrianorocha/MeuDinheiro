package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.FiltroRelatorio
import com.meudinheiro.domain.Relatorios
import com.meudinheiro.domain.TipoRelatorio
import com.meudinheiro.funcoes.DescricaoRelatorio
import com.meudinheiro.funcoes.RelatorioExport
import com.meudinheiro.funcoes.formatarMoedaBR
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class PeriodoRapido(val rotulo: String) { ESTE_MES("Este mês"), MES_PASSADO("Mês passado"), ANO("Este ano"), PERSONALIZADO("Personalizado") }

private val CardFundo = Color(0xFF1B263B)
private val TextoClaro = Color(0xFFE0E1DD)

/**
 * Construtor de relatórios (R19/R20): filtros combináveis (período, conta, cartão, categoria, tipo, pago, texto),
 * prévia ao vivo e exportação em PDF, PNG (imagem-resumo) e CSV.
 * Ex.: combustível + cartão Visa + 01/09 a 30/09 → PDF.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RelatoriosScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoes by repository.cartoesFlow().collectAsState(initial = emptyList())
    val custom by repository.obterCategoriasCustom().collectAsState(initial = emptyList())
    val categoriasTodas = remember(custom) { (repository.categorias + custom).map { it.title }.distinctBy { it.lowercase() }.sorted() }

    val hoje = remember { Calendar.getInstance() }
    var periodo by remember { mutableStateOf(PeriodoRapido.ESTE_MES) }
    var inicioCustom by remember { mutableStateOf(Financas.inicioDoMes(hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.YEAR))) }
    var fimCustom by remember { mutableStateOf(Financas.fimDoDia(System.currentTimeMillis())) }
    var escolhendo by remember { mutableStateOf<String?>(null) } // "inicio" | "fim"

    var contasSel by remember { mutableStateOf(setOf<String>()) }
    var cartoesSel by remember { mutableStateOf(setOf<Int>()) }
    var categoriasSel by remember { mutableStateOf(setOf<String>()) }
    var tipo by remember { mutableStateOf(TipoRelatorio.DESPESA) }
    var pago by remember { mutableStateOf<Boolean?>(null) }
    var texto by remember { mutableStateOf("") }
    var internos by remember { mutableStateOf(false) }
    var detalhar by remember { mutableStateOf(true) }
    var exportando by remember { mutableStateOf(false) }

    val (inicio, fim) = remember(periodo, inicioCustom, fimCustom) {
        val mes = hoje.get(Calendar.MONTH) + 1
        val ano = hoje.get(Calendar.YEAR)
        when (periodo) {
            PeriodoRapido.ESTE_MES -> Financas.inicioDoMes(mes, ano) to Financas.fimDoMes(mes, ano)
            PeriodoRapido.MES_PASSADO -> Financas.FaturaRef(mes, ano).anterior().let { Financas.inicioDoMes(it.mes, it.ano) to Financas.fimDoMes(it.mes, it.ano) }
            PeriodoRapido.ANO -> Financas.inicioDoMes(1, ano) to Financas.fimDoMes(12, ano)
            PeriodoRapido.PERSONALIZADO -> Financas.inicioDoDia(inicioCustom) to Financas.fimDoDia(fimCustom)
        }
    }
    val filtro = FiltroRelatorio(inicio, fim, contasSel, cartoesSel, categoriasSel, tipo, pago, texto, internos)
    val resultado = remember(filtro, despesas, cartoes) { Relatorios.gerar(filtro, despesas, cartoes) }

    val bancoPorConta = remember(contas) { contas.associate { it.conta to it.banco } }
    val formatoData = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    fun descricao(titulo: String): DescricaoRelatorio {
        val filtros = buildList {
            if (contasSel.isNotEmpty()) add("Contas: " + contasSel.joinToString { bancoPorConta[it] ?: it })
            if (cartoesSel.isNotEmpty()) add("Cartões: " + cartoes.filter { it.id in cartoesSel }.joinToString { it.nome + (if (it.cartaoPrincipalId != null) " (virtual)" else "") })
            if (categoriasSel.isNotEmpty()) add("Categorias: " + categoriasSel.joinToString())
            if (pago != null) add(if (pago == true) "Somente pagos" else "Somente pendentes")
            if (texto.isNotBlank()) add("Busca: \"$texto\"")
            if (internos) add("Inclui transferências, aportes e faturas")
        }
        return DescricaoRelatorio(titulo, "${formatoData.format(Date(inicio))} a ${formatoData.format(Date(fim))}", filtros)
    }

    fun tituloAuto(): String = when {
        categoriasSel.size == 1 -> "Gastos de ${categoriasSel.first()}"
        tipo == TipoRelatorio.RECEITA -> "Receitas"
        tipo == TipoRelatorio.TODOS -> "Receitas × Despesas"
        cartoesSel.size == 1 -> "Gastos do cartão " + (cartoes.firstOrNull { it.id in cartoesSel }?.nome ?: "")
        else -> "Despesas"
    }

    fun exportar(formato: String) {
        scope.launch {
            exportando = true
            try {
                val d = descricao(tituloAuto())
                val arq = withContext(Dispatchers.IO) {
                    when (formato) {
                        "pdf" -> RelatorioExport.pdf(context, resultado, d, detalhar, { id -> cartoes.firstOrNull { it.id == id }?.nome }) { bancoPorConta[it] ?: it }
                        "png" -> RelatorioExport.png(context, resultado, d, detalhar, { id -> cartoes.firstOrNull { it.id == id }?.nome }, nomeConta = { bancoPorConta[it] ?: it })
                        else -> RelatorioExport.csv(context, resultado, d) { bancoPorConta[it] ?: it }
                    }
                }
                RelatorioExport.compartilhar(context, arq, when (formato) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "text/csv" })
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível gerar o relatório: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                exportando = false
            }
        }
    }

    fun aplicarModelo(m: Relatorios.Modelo) {
        val mes = hoje.get(Calendar.MONTH) + 1
        val ano = hoje.get(Calendar.YEAR)
        val f = Relatorios.filtroDoModelo(
            m, mes, ano, conta = contas.firstOrNull()?.conta,
            cartaoId = cartoes.firstOrNull { it.cartaoPrincipalId == null }?.id
        )
        tipo = f.tipo; contasSel = f.contas; cartoesSel = f.cartoes; categoriasSel = f.categorias; internos = f.incluirInternos
        pago = null; texto = ""
        if (m == Relatorios.Modelo.ANUAL_IR) periodo = PeriodoRapido.ANO else periodo = PeriodoRapido.ESTE_MES
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Relatórios", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Secao("Modelos prontos") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Relatorios.Modelo.entries.forEach { m ->
                            FilterChip(selected = false, onClick = { aplicarModelo(m) }, label = { Text(m.titulo, fontSize = 12.sp) }, colors = chipColors())
                        }
                    }
                }
            }
            item {
                Secao("Período") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeriodoRapido.entries.forEach { p ->
                            FilterChip(selected = periodo == p, onClick = { periodo = p }, label = { Text(p.rotulo) }, colors = chipColors())
                        }
                    }
                    if (periodo == PeriodoRapido.PERSONALIZADO) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { escolhendo = "inicio" }, modifier = Modifier.weight(1f)) { Text("De ${formatoData.format(Date(inicio))}", color = NeonCyan) }
                            OutlinedButton(onClick = { escolhendo = "fim" }, modifier = Modifier.weight(1f)) { Text("Até ${formatoData.format(Date(fim))}", color = NeonCyan) }
                        }
                    }
                }
            }
            item {
                Secao("Tipo") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(TipoRelatorio.DESPESA to "Despesas", TipoRelatorio.RECEITA to "Receitas", TipoRelatorio.TODOS to "Receitas e despesas").forEach { (t, r) ->
                            FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(r) }, colors = chipColors())
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Pair<Boolean?, String>>(null to "Pagos e pendentes", true to "Só pagos", false to "Só pendentes").forEach { (v, r) ->
                            FilterChip(selected = pago == v, onClick = { pago = v }, label = { Text(r) }, colors = chipColors())
                        }
                    }
                }
            }
            if (contas.isNotEmpty()) item {
                Secao("Contas (vazio = todas)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        contas.forEach { c ->
                            FilterChip(
                                selected = c.conta in contasSel,
                                onClick = { contasSel = if (c.conta in contasSel) contasSel - c.conta else contasSel + c.conta },
                                label = { Text(c.banco) }, colors = chipColors()
                            )
                        }
                    }
                }
            }
            if (cartoes.isNotEmpty()) item {
                Secao("Cartões (vazio = todos) — o físico inclui os virtuais") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cartoes.forEach { c ->
                            FilterChip(
                                selected = c.id in cartoesSel,
                                onClick = { cartoesSel = if (c.id in cartoesSel) cartoesSel - c.id else cartoesSel + c.id },
                                label = { Text(c.nome + " ••" + c.finalCartao + if (c.cartaoPrincipalId != null) " · virtual" else "") },
                                colors = chipColors()
                            )
                        }
                    }
                }
            }
            item {
                Secao("Categorias (vazio = todas)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoriasTodas.forEach { cat ->
                            FilterChip(
                                selected = cat in categoriasSel,
                                onClick = { categoriasSel = if (cat in categoriasSel) categoriasSel - cat else categoriasSel + cat },
                                label = { Text(cat) }, colors = chipColors()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it }, singleLine = true,
                        label = { Text("Contém o texto…") }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    FilterChip(selected = internos, onClick = { internos = !internos }, label = { Text("Incluir transferências, aportes e faturas") }, colors = chipColors())
                    FilterChip(selected = detalhar, onClick = { detalhar = !detalhar }, label = { Text("Incluir detalhamento (PDF/imagem)") }, colors = chipColors())
                }
            }

            // ---------------- prévia
            item {
                Secao("Prévia") {
                    val corTotal = if (tipo == TipoRelatorio.TODOS && resultado.total < 0) NeonRed else NeonGreen
                    Text(
                        when (tipo) { TipoRelatorio.DESPESA -> "Total de despesas"; TipoRelatorio.RECEITA -> "Total de receitas"; TipoRelatorio.TODOS -> "Resultado" },
                        color = Color.White.copy(0.6f), fontSize = 12.sp
                    )
                    Text(formatarMoedaBR(resultado.total, isPrivate), color = corTotal, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${resultado.quantidade} lançamentos · média ${formatarMoedaBR(resultado.media, isPrivate)} · maior ${formatarMoedaBR(resultado.maior, isPrivate)}",
                        color = Color.White.copy(0.7f), fontSize = 12.sp
                    )
                    resultado.variacaoPercentual?.let {
                        val cor = if (tipo == TipoRelatorio.RECEITA) (if (it >= 0) NeonGreen else NeonRed) else (if (it > 0) NeonRed else NeonGreen)
                        Text(
                            (if (it >= 0) "▲ " else "▼ ") + "%.1f%% vs. período anterior (%s)".format(Locale("pt", "BR"), Math.abs(it), formatarMoedaBR(resultado.anterior.total, isPrivate)),
                            color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val max = resultado.porCategoria.firstOrNull()?.total ?: 0.0
                    resultado.porCategoria.take(8).forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(cat.nome, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(10.dp).background(Color.White.copy(0.08f), RoundedCornerShape(5.dp))) {
                                Box(
                                    Modifier.fillMaxHeight().fillMaxWidth(if (max > 0) (cat.total / max).toFloat().coerceIn(0.02f, 1f) else 0.02f)
                                        .background(NeonCyan, RoundedCornerShape(5.dp))
                                )
                            }
                            Text(" " + formatarMoedaBR(cat.total, isPrivate), color = Color.White.copy(0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Secao("Exportar") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BotaoExportar("PDF", Icons.Default.PictureAsPdf, !exportando && resultado.quantidade > 0, Modifier.weight(1f)) { exportar("pdf") }
                        BotaoExportar("PNG", Icons.Default.Image, !exportando && resultado.quantidade > 0, Modifier.weight(1f)) { exportar("png") }
                        BotaoExportar("CSV", Icons.Default.TableChart, !exportando && resultado.quantidade > 0, Modifier.weight(1f)) { exportar("csv") }
                    }
                    if (exportando) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(color = NeonCyan, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                    }
                    if (resultado.quantidade == 0) Text("Nenhum lançamento para estes filtros.", color = NeonOrange, fontSize = 12.sp)
                }
            }
            items(resultado.itens.take(60)) { d ->
                Row(
                    Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.descricao, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        Text("${formatoData.format(d.data)} · ${d.categoria} · ${bancoPorConta[d.conta] ?: d.conta}", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                    val entrada = d.tipo == TipoDespesa.CREDITO
                    Text((if (entrada) "+ " else "- ") + formatarMoedaBR(d.valor, isPrivate), color = if (entrada) NeonGreen else Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            if (resultado.itens.size > 60) item {
                Text("… e mais ${resultado.itens.size - 60} lançamentos (todos entram no arquivo exportado).", color = Color.White.copy(0.5f), fontSize = 12.sp)
            }
        }
    }

    if (escolhendo != null) {
        CustomCalendarDialog(
            onDismiss = { escolhendo = null },
            onDateSelected = { y, m, d ->
                val ms = Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0, 0) }.timeInMillis
                if (escolhendo == "inicio") inicioCustom = ms else fimCustom = ms
                escolhendo = null
            }
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    labelColor = Color.White.copy(0.8f),
    selectedContainerColor = NeonCyan.copy(0.25f),
    selectedLabelColor = NeonCyan
)

@Composable
internal fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardFundo.copy(0.8f), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(titulo, color = Color.White.copy(0.55f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        conteudo()
    }
}

@Composable
private fun BotaoExportar(rotulo: String, icone: androidx.compose.ui.graphics.vector.ImageVector, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = habilitado, modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue),
        border = BorderStroke(0.dp, Color.Transparent), contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icone, null, Modifier.height(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(rotulo, fontWeight = FontWeight.Bold)
    }
}
