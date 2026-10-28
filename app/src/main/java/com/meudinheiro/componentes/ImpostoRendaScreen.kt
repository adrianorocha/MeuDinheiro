package com.meudinheiro.componentes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.ImpostoRenda
import com.meudinheiro.domain.ImpostoRenda.Classe
import com.meudinheiro.domain.ImpostoRenda.Deducao
import com.meudinheiro.domain.Painel503020
import com.meudinheiro.funcoes.ImpostoRendaExport
import com.meudinheiro.funcoes.RelatorioExport
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val TextoClaro = Color(0xFFE0E1DD)
private val CardFundo = Color(0xFF1B263B)
private val GSON = Gson()

/**
 * R61 — Apoio ao Imposto de Renda por ano-calendário (triagem; NÃO é a declaração oficial). Mesma função pura do portal
 * (`ImpostoRenda.relatorio`), sempre REALIZADO (pago) e com o que é estimativa separado. Escolhas do usuário (classe de cada
 * categoria, limite de educação) vão em `configuracoes/geral` pelo mesmo mecanismo do R57.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostoRendaScreen(repository: MainRepository, userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contas by repository.getTodasContas().collectAsState(initial = emptyList())
    val investimentos by repository.investimentosFlow().collectAsState(initial = emptyList())
    val metas by repository.getTodasMetas().collectAsState(initial = emptyList())
    val dividas by repository.obterDividasFlow().collectAsState(initial = emptyList())
    val irTexto by userPrefs.irConfigFlow.collectAsState(initial = "")
    val agora = remember { System.currentTimeMillis() }

    val cfg = remember(irTexto) {
        if (irTexto.isBlank()) ImpostoRenda.Config()
        else runCatching { ImpostoRenda.Config.deDoc(GSON.fromJson(irTexto, Map::class.java)) }.getOrDefault(ImpostoRenda.Config())
    }
    fun salvar(c: ImpostoRenda.Config) { scope.launch { userPrefs.salvarIrConfig(GSON.toJson(c.toDoc())) } }

    val anos = remember(despesas) { ImpostoRenda.anosComDados(despesas, agora) }
    val anoPadrao = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1 }
    var ano by remember { mutableStateOf(anoPadrao) }
    val r = remember(despesas, contas, investimentos, metas, dividas, ano, cfg) {
        ImpostoRenda.relatorio(despesas, contas, investimentos, metas, dividas, ano, cfg, agora)
    }
    val dataBr = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    fun m(c: Long) = if (isPrivate) "R$ ••••••" else FormatoCentavos.brl(c)

    fun exportar(pdf: Boolean) {
        try {
            val doc = ImpostoRenda.documento(r)
            val arq = if (pdf) ImpostoRendaExport.pdf(context, doc) else ImpostoRendaExport.csv(context, doc)
            RelatorioExport.compartilhar(context, arq, if (pdf) "application/pdf" else "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível gerar o arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    var educLimiteTexto by remember(cfg.limiteEducacaoCentavos) {
        mutableStateOf("%d,%02d".format(cfg.limiteEducacaoCentavos / 100, cfg.limiteEducacaoCentavos % 100))
    }

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Imposto de Renda", color = TextoClaro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = TextoClaro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(anos) { a -> FilterChip(selected = a == ano, onClick = { ano = a }, label = { Text("$a") }) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF4A3B12), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = NeonOrange)
                        Text("  Triagem, não é a declaração oficial", color = NeonOrange, fontWeight = FontWeight.Bold)
                    }
                    Text(ImpostoRenda.AVISO_IR, color = Color.White.copy(0.9f), fontSize = 12.sp)
                    r.avisos.forEach { Text(it, color = Color.White.copy(0.75f), fontSize = 11.sp) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportar(true) }) { Text("PDF", color = NeonCyan) }
                    TextButton(onClick = { exportar(false) }) { Text("CSV", color = NeonCyan) }
                }
            }

            // ---- 1) rendimentos
            item { Bloco("1. Rendimentos recebidos em $ano (realizado)") {
                r.rendimentos.porClasse.filter { it.totalCentavos != 0L || it.quantidade > 0 }.forEach {
                    Linha("${it.classe.rotulo} (${it.quantidade})", m(it.totalCentavos))
                }
                Linha("Total recebido", m(r.rendimentos.totalRealizadoCentavos), forte = true)
                Linha("Previsto e ainda não recebido (estimativa, fora dos totais)", m(r.rendimentos.previstoCentavos), fraco = true)
                Nota("Investimentos: informe conforme o informe de rendimentos do banco/corretora. 13º e PLR são classificados como tributação exclusiva na fonte; ajuste abaixo se o seu informe disser outra coisa.")
                r.rendimentos.grupos.map { it.chave to it.categoria }.distinct().forEach { (chave, nome) ->
                    val atual = cfg.rendimentos[chave]
                    val padrao = ImpostoRenda.classificarRendimento(nome)
                    Escolha(
                        rotulo = nome,
                        valor = atual?.rotulo ?: "Padrão (${padrao.rotulo})",
                        opcoes = listOf("Padrão (${padrao.rotulo})" to null) + ImpostoRenda.CLASSES_ESCOLHA.map { it.rotulo to it }
                    ) { c ->
                        val novo = cfg.rendimentos.toMutableMap()
                        if (c == null) novo.remove(chave) else novo[chave] = c
                        salvar(cfg.copy(rendimentos = novo))
                    }
                }
            } }

            // ---- 2) deduções
            item { Bloco("2. Despesas dedutíveis candidatas (realizado)") {
                r.deducoes.porTipo.forEach {
                    val extra = it.limiteCentavos?.let { l -> "limite ${m(l)}" + (if (it.excessoCentavos > 0) " · excesso ${m(it.excessoCentavos)} (não entra)" else "") }
                    Linha(it.tipo.rotulo, m(it.deduzivelCentavos), sub = extra)
                }
                Linha("Total candidato a dedução", m(r.deducoes.totalDeduzivelCentavos), forte = true)
                Linha("Ainda não pago (estimativa, fora dos totais)", m(r.deducoes.pendentesCentavos), fraco = true)
                OutlinedTextField(
                    value = educLimiteTexto, onValueChange = { educLimiteTexto = it }, singleLine = true,
                    label = { Text("Limite de educação por pessoa (R$)") },
                    supportingText = { Text("Valor de referência (R$ 3.561,50): confirme o limite vigente do ano-base na Receita.", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), colors = campoCores()
                )
                TextButton(onClick = {
                    val v = educLimiteTexto.replace(".", "").replace(",", ".").toDoubleOrNull()
                    if (v != null && v >= 0) salvar(cfg.copy(limiteEducacaoCentavos = Math.round(v * 100)))
                    else Toast.makeText(context, "Informe um valor válido para o limite.", Toast.LENGTH_SHORT).show()
                }) { Text("Aplicar limite", color = NeonCyan) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pessoas com educação: ${cfg.pessoasEducacao}", color = TextoClaro, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (cfg.pessoasEducacao > 1) salvar(cfg.copy(pessoasEducacao = cfg.pessoasEducacao - 1)) }) { Text("−", color = NeonCyan, fontSize = 20.sp) }
                    TextButton(onClick = { if (cfg.pessoasEducacao < 20) salvar(cfg.copy(pessoasEducacao = cfg.pessoasEducacao + 1)) }) { Text("+", color = NeonCyan, fontSize = 20.sp) }
                }
                Nota("Previdência privada (PGBL): até 12% da renda tributável (${m(r.deducoes.limitePgblCentavos)} sobre ${m(r.deducoes.baseTributavelCentavos)} tributáveis lançados). Doações: só fundos e projetos incentivados. Remédios e óculos não entram.")
                val catsDespesa = remember(despesas, ano) {
                    despesas.filter {
                        it.natureza == com.meudinheiro.domain.Natureza.NORMAL && it.tipo == com.meudinheiro.data.TipoDespesa.DEBITO &&
                            ImpostoRenda.inicioDoAno(ano) <= it.dataMs && it.dataMs <= ImpostoRenda.fimDoAno(ano) && it.categoria.isNotBlank()
                    }.map { Painel503020.chaveCategoria(it.categoria) to it.categoria.trim() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
                }
                Text("Escolher quais categorias entram (${catsDespesa.size})", color = TextoClaro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                catsDespesa.forEach { (chave, nome) ->
                    val padrao = ImpostoRenda.classificarDeducao(nome)
                    val padraoRot = "Padrão (${padrao?.rotulo ?: "não entra"})"
                    val atualRot = if (cfg.deducoes.containsKey(chave)) (cfg.deducoes[chave]?.rotulo ?: "Não entra") else padraoRot
                    Escolha(
                        rotulo = nome, valor = atualRot,
                        opcoes = listOf<Pair<String, Any?>>(padraoRot to "PADRAO", "Não entra" to "NAO") + Deducao.entries.map { it.rotulo to it }
                    ) { v ->
                        val novo = cfg.deducoes.toMutableMap()
                        when (v) {
                            "PADRAO" -> novo.remove(chave)
                            "NAO" -> novo[chave] = null
                            is Deducao -> novo[chave] = v
                        }
                        salvar(cfg.copy(deducoes = novo))
                    }
                }
            } }

            // ---- lançamentos candidatos
            item { Bloco("Lançamentos candidatos a dedução (${r.deducoes.quantidade})") {
                if (r.deducoes.quantidade > 0) Nota("${r.deducoes.semComprovante} sem comprovante anexado. Guarde recibos/notas com CPF/CNPJ do profissional: a Receita pode pedir.")
                if (r.deducoes.grupos.isEmpty()) Nota("Nenhuma despesa candidata paga neste ano.")
                r.deducoes.grupos.forEach { g ->
                    Linha("${g.categoria} · ${g.tipo?.rotulo}", m(g.totalCentavos), forte = true)
                    g.itens.forEach { i -> ItemLinha(dataBr.format(i.data), i.descricao, if (i.comprovante) "comprovante: sim" else "comprovante: não", m(i.centavos), i.atencao) }
                }
                if (r.deducoes.naoDedutiveis.isNotEmpty()) {
                    Linha("Provavelmente não dedutíveis (fora do total)", m(r.deducoes.naoDedutiveisCentavos), forte = true)
                    r.deducoes.naoDedutiveis.forEach { i -> ItemLinha(dataBr.format(i.data), i.descricao, if (i.comprovante) "comprovante: sim" else "comprovante: não", m(i.centavos), i.atencao) }
                }
            } }

            item { Bloco("Rendimentos detalhados") {
                if (r.rendimentos.grupos.isEmpty()) Nota("Nada recebido neste ano.")
                r.rendimentos.grupos.forEach { g ->
                    Linha("${g.categoria} · ${(g.classe ?: Classe.REVISAR).rotulo}", m(g.totalCentavos), forte = true)
                    g.itens.forEach { i -> ItemLinha(dataBr.format(i.data), i.descricao, null, m(i.centavos), null) }
                }
            } }

            // ---- 3) bens e dívidas
            item { Bloco("3. Bens e dívidas") {
                r.bens.avisoSaldoAtual?.let { Text(it, color = NeonOrange, fontSize = 12.sp) }
                Text("Saldo das contas em 31/12/$ano", color = TextoClaro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (r.bens.contas.isEmpty()) Nota("Nada cadastrado.")
                r.bens.contas.forEach { Linha("${it.nome} · ${it.detalhe}", m(it.centavos)) }
                Linha("Total", m(r.bens.totalContasCentavos), forte = true)
                Nota("Calculado a partir dos lançamentos pagos até a data. Contas com saldo muito baixo normalmente não precisam constar: confirme a regra vigente.")
                Lista("Investimentos (saldo atual)", r.bens.investimentos, r.bens.totalInvestimentosCentavos, ::m)
                Lista("Metas / cofrinhos (saldo atual)", r.bens.metas, r.bens.totalMetasCentavos, ::m)
                Lista("Dívidas ativas (saldo devedor atual)", r.bens.dividas, r.bens.totalDividasCentavos, ::m)
            } }

            // ---- 4) totais e checagem
            item { Bloco("4. Totais e checagem") {
                Linha("Rendimentos tributáveis lançados", m(r.deducoes.baseTributavelCentavos))
                Linha("Deduções candidatas", m(r.deducoes.totalDeduzivelCentavos))
                Linha("Diferença indicativa (NÃO é a base de cálculo oficial)", m(r.indicativoBaseCentavos), forte = true)
                val ok = r.checagem.confere && r.checagem.deducoesConferem
                Text(
                    (if (ok) "Totais conferem" else "Totais não conferem") +
                        ": receitas recebidas no ano ${m(r.checagem.receitasRealizadasCentavos)} = soma das classes ${m(r.checagem.somaClassesCentavos)}",
                    color = if (ok) NeonGreen else NeonOrange, fontSize = 12.sp
                )
            } }
        }
    }
}

@Composable
private fun campoCores() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color.White.copy(0.2f), cursorColor = NeonCyan,
    focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.6f)
)

@Composable
private fun Bloco(titulo: String, conteudo: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CardFundo, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        conteudo()
    }
}

@Composable
private fun Linha(rotulo: String, valor: String, forte: Boolean = false, fraco: Boolean = false, sub: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, color = if (fraco) Color.White.copy(0.55f) else TextoClaro, fontSize = 13.sp, fontWeight = if (forte) FontWeight.Bold else FontWeight.Normal)
            sub?.let { Text(it, color = Color.White.copy(0.5f), fontSize = 11.sp) }
        }
        Text(valor, color = if (fraco) Color.White.copy(0.55f) else Color.White, fontSize = 13.sp, fontWeight = if (forte) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ItemLinha(data: String, desc: String, info: String?, valor: String, atencao: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("$data · $desc", color = Color.White.copy(0.85f), fontSize = 12.sp)
            info?.let { Text(it, color = Color.White.copy(0.5f), fontSize = 10.sp) }
            atencao?.let { Text(it, color = NeonOrange, fontSize = 10.sp) }
        }
        Text(valor, color = Color.White.copy(0.85f), fontSize = 12.sp)
    }
}

@Composable
private fun Nota(texto: String) = Text(texto, color = Color.White.copy(0.55f), fontSize = 11.sp)

@Composable
private fun Lista(titulo: String, itens: List<ImpostoRenda.Saldo>, total: Long, m: (Long) -> String) {
    Text(titulo, color = TextoClaro, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    if (itens.isEmpty()) Nota("Nada cadastrado.")
    itens.forEach { Linha("${it.nome} · ${it.detalhe}", m(it.centavos)) }
    if (itens.isNotEmpty()) Linha("Total", m(total), forte = true)
}

/** Seletor simples (menu suspenso) para a classe/tipo de uma categoria. */
@Composable
private fun <T> Escolha(rotulo: String, valor: String, opcoes: List<Pair<String, T>>, onEscolher: (T) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { aberto = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rotulo, color = TextoClaro, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$valor ▾", color = NeonCyan, fontSize = 12.sp)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (r, v) ->
                DropdownMenuItem(text = { Text(r) }, onClick = { aberto = false; onEscolher(v) })
            }
        }
    }
}
