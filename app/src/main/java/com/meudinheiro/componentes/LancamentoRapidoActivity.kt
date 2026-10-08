package com.meudinheiro.componentes

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Orcamento
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Analises
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.LancamentoRapido
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lançamento rápido: abre como um painel flutuante por cima da tela inicial (o app principal não é aberto).
 * Usado pelos botões "+ Despesa" / "+ Receita" do widget.
 */
class LancamentoRapidoActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receitaInicial = intent?.getStringExtra(EXTRA_TIPO) == TipoDespesa.CREDITO.name
        val atalho = intent?.takeIf { it.hasExtra(EXTRA_VALOR) }?.let {
            AtalhoExtra(
                descricao = it.getStringExtra(EXTRA_DESCRICAO).orEmpty(),
                valor = it.getStringExtra(EXTRA_VALOR).orEmpty(),
                categoria = it.getStringExtra(EXTRA_CATEGORIA).orEmpty(),
                conta = it.getStringExtra(EXTRA_CONTA).orEmpty(),
                cartaoId = it.getIntExtra(EXTRA_CARTAO, -1).takeIf { id -> id > 0 }
            )
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = NeonCyan)) {
                PainelRapido(receitaInicial = receitaInicial, atalho = atalho, onFechar = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_TIPO = "tipo"
        const val EXTRA_DESCRICAO = "atalho_descricao"
        const val EXTRA_VALOR = "atalho_valor"
        const val EXTRA_CATEGORIA = "atalho_categoria"
        const val EXTRA_CONTA = "atalho_conta"
        const val EXTRA_CARTAO = "atalho_cartao"
        const val PREFS = "widget_prefs"
        const val KEY_ORIGEM = "ultima_origem" // "c:<numero da conta>" ou "k:<id do cartão>"
    }
}

/** Atalho de 1 toque do widget: abre já preenchido e lança logo após a autenticação. */
private class AtalhoExtra(val descricao: String, val valor: String, val categoria: String, val conta: String, val cartaoId: Int?)

private sealed interface Origem {
    val chave: String
    val rotulo: String

    data class DeConta(val conta: ContaSaldo) : Origem {
        override val chave = "c:${conta.conta}"
        override val rotulo = conta.banco
    }

    data class DeCartao(val cartao: Cartao) : Origem {
        override val chave = "k:${cartao.id}"
        override val rotulo = "💳 ${cartao.nome}"
    }
}

private class Dados(
    val origens: List<Origem>,
    val categoriasDespesa: List<Pair<String, String>>, // nome → pic
    val historico: List<Despesa>,
    val orcamentos: List<Orcamento>,
    val origemInicial: String?,
    val exigirBiometria: Boolean
)

private suspend fun carregar(context: Context): Dados = withContext(Dispatchers.IO) {
    val repo = MainRepository(context)
    val contas = repo.obterTodasStatic().sortedBy { it.banco }
    val cartoes = repo.cartoesFlow().first().sortedBy { it.nome }
    val custom = repo.obterCategoriasCustom().first().map { it.title to it.pic }
    val padrao = repo.categorias.map { it.title to it.pic }
    val historico = repo.todasDespesasFlow.first()
    val todas = (custom + padrao).distinctBy { it.first.trim().lowercase() }
    val ordem = LancamentoRapido.categoriasPorUso(todas.map { it.first }, historico, TipoDespesa.DEBITO)
    Dados(
        origens = contas.map { Origem.DeConta(it) } + cartoes.map { Origem.DeCartao(it) },
        categoriasDespesa = ordem.map { n -> todas.first { it.first.equals(n, true) } },
        historico = historico,
        orcamentos = repo.obterOrcamentosFlow().first(),
        origemInicial = context.getSharedPreferences(LancamentoRapidoActivity.PREFS, Context.MODE_PRIVATE)
            .getString(LancamentoRapidoActivity.KEY_ORIGEM, null),
        exigirBiometria = runCatching { UserPreferences(context).biometriaLancarFlow.first() }.getOrDefault(true)
    )
}

@Composable
private fun PainelRapido(receitaInicial: Boolean, atalho: AtalhoExtra?, onFechar: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val escopo = rememberCoroutineScope()
    var dados by remember { mutableStateOf<Dados?>(null) }
    var receita by remember { mutableStateOf(receitaInicial) }
    var valorTxt by remember { mutableStateOf(atalho?.valor?.replace('.', ',').orEmpty()) }
    var descricao by remember { mutableStateOf(atalho?.descricao.orEmpty()) }
    var categoria by remember { mutableStateOf(atalho?.categoria?.ifBlank { null }) }
    var categoriaManual by remember { mutableStateOf(atalho != null) }
    var origemChave by remember { mutableStateOf<String?>(atalho?.let { a -> a.cartaoId?.let { "k:$it" } ?: "c:${a.conta}" }) }
    var erro by remember { mutableStateOf<String?>(null) }
    var modalidade by remember { mutableStateOf(Financas.Modalidade.CREDITO) } // R42: só vale para cartão MÚLTIPLO
    var salvando by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val d = carregar(context)
        dados = d
        if (atalho == null) {
            origemChave = d.origemInicial?.takeIf { k -> d.origens.any { it.chave == k } } ?: d.origens.firstOrNull()?.chave
            runCatching { foco.requestFocus() }
        }
    }

    // Sugere a categoria pelo histórico enquanto a pessoa digita a descrição (R21), até ela escolher uma.
    LaunchedEffect(descricao, dados) {
        val d = dados ?: return@LaunchedEffect
        if (!categoriaManual && !receita && descricao.isNotBlank()) {
            Analises.sugerirCategoria(descricao, d.historico)?.let { categoria = it }
        }
    }

    fun salvar() {
        val d = dados ?: return
        val valor = LancamentoRapido.parseValor(valorTxt) ?: return run { erro = "Informe um valor maior que zero." }
        val origem = d.origens.firstOrNull { it.chave == origemChave } ?: return run { erro = "Cadastre uma conta no app primeiro." }
        val tipo = if (receita) TipoDespesa.CREDITO else TipoDespesa.DEBITO
        if (receita && origem is Origem.DeCartao) return run { erro = "Receita entra em uma conta, não em um cartão." }
        val catNome = if (receita) "Receita" else categoria ?: return run { erro = "Escolha uma categoria." }
        val pic = d.categoriasDespesa.firstOrNull { it.first.equals(catNome, true) }?.second.orEmpty()
        salvando = true
        autenticarParaLancar(
            context, d.exigirBiometria, "Confirmar lançamento", "Autentique para lançar ${formatarMoedaBR(valor, false)}",
            onNegado = { salvando = false; if (atalho != null) onFechar() else erro = "Autenticação necessária para lançar." }
        ) { escopo.launch {
            val agora = System.currentTimeMillis()
            val lanc = LancamentoRapido.montar(
                valor, descricao, catNome, pic, tipo,
                conta = (origem as? Origem.DeConta)?.conta?.conta ?: "",
                cartaoId = (origem as? Origem.DeCartao)?.cartao?.id,
                agora = agora
            )
            val resultado = withContext(Dispatchers.IO) { runCatching { MainRepository(context).registrarLancamento(lanc, if (origem is Origem.DeCartao) modalidade else null) } }
            if (resultado.isFailure) {
                salvando = false
                erro = resultado.exceptionOrNull()?.message ?: "Não foi possível salvar."
                return@launch
            }
            context.getSharedPreferences(LancamentoRapidoActivity.PREFS, Context.MODE_PRIVATE).edit().putString(LancamentoRapidoActivity.KEY_ORIGEM, origem.chave).apply()
            runCatching { SaldoWidget().updateAll(context) }
            Toast.makeText(context, mensagemDeSucesso(lanc, d, tipo), Toast.LENGTH_LONG).show()
            onFechar()
        } }
    }

    // Atalho: com tudo preenchido, só falta confirmar (biometria) — lança assim que os dados carregam.
    LaunchedEffect(dados) {
        if (atalho != null && dados != null && !salvando) salvar()
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onFechar).statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(enabled = false) {}
                .background(DeepSpaceBlue, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Seletor("Despesa", !receita, NeonCyan, Modifier.weight(1f)) { receita = false }
                Seletor("Receita", receita, NeonGreen, Modifier.weight(1f)) { receita = true; categoriaManual = false }
            }

            OutlinedTextField(
                value = valorTxt, onValueChange = { valorTxt = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(14); erro = null },
                label = { Text("Valor (R$)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = camposEscuros(), modifier = Modifier.fillMaxWidth().focusRequester(foco)
            )
            OutlinedTextField(
                value = descricao, onValueChange = { descricao = it.take(60) },
                label = { Text(if (receita) "Descrição (opcional)" else "Descrição (opcional, sugere a categoria)") }, singleLine = true,
                colors = camposEscuros(), modifier = Modifier.fillMaxWidth()
            )

            val d = dados
            if (d == null) {
                Text("Carregando…", color = Color.White.copy(0.6f))
            } else {
                if (!receita) {
                    Text("Categoria", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(d.categoriasDespesa, key = { it.first }) { (nome, _) ->
                            FilterChip(
                                selected = categoria.equals(nome, true), onClick = { categoria = nome; categoriaManual = true; erro = null },
                                label = { Text(nome) },
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.White.copy(0.8f), selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                }
                Text(if (receita) "Entra na conta" else "Pagar com", color = Color.White.copy(0.6f), fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(d.origens.filter { !receita || it is Origem.DeConta }, key = { it.chave }) { o ->
                        FilterChip(
                            selected = origemChave == o.chave, onClick = { origemChave = o.chave; erro = null },
                            label = { Text(o.rotulo) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White.copy(0.8f), selectedContainerColor = NeonGreen.copy(0.25f), selectedLabelColor = NeonGreen
                            )
                        )
                    }
                }
                val cartaoOrigem = (d.origens.firstOrNull { it.chave == origemChave } as? Origem.DeCartao)?.cartao
                if (!receita && cartaoOrigem != null) {
                    when (Financas.tipoDeCartao(cartaoOrigem.tipo)) {
                        "MULTIPLO" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Financas.Modalidade.CREDITO to "Crédito", Financas.Modalidade.DEBITO to "Débito").forEach { (m, rotulo) ->
                                FilterChip(
                                    selected = modalidade == m, onClick = { modalidade = m },
                                    label = { Text(rotulo) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color.White.copy(0.8f), selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }
                        "DEBITO" -> Text("Compra no débito: sai direto da conta", color = NeonCyan, fontSize = 12.sp)
                    }
                }
                if (d.origens.isEmpty()) Text("Nenhuma conta cadastrada. Abra o app para criar a primeira.", color = Color(0xFFFF8A80), fontSize = 12.sp)
            }

            erro?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onFechar) { Text("Cancelar", color = Color.White.copy(0.6f)) }
                Button(
                    onClick = ::salvar, enabled = !salvando && dados != null,
                    colors = ButtonDefaults.buttonColors(containerColor = if (receita) NeonGreen else NeonCyan, contentColor = DeepSpaceBlue)
                ) { Text(if (receita) "Lançar receita" else "Lançar despesa", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Confirmação com contexto: o que foi lançado e como ficou o orçamento da categoria no mês. */
private fun mensagemDeSucesso(l: Despesa, d: Dados, tipo: TipoDespesa): String {
    val base = (if (tipo == TipoDespesa.CREDITO) "Receita de " else "Despesa de ") + formatarMoedaBR(l.valor, false) + " lançada"
    if (tipo == TipoDespesa.CREDITO) return base
    val orc = d.orcamentos.firstOrNull { it.categoria.trim().equals(l.categoria.trim(), true) } ?: return base
    val agora = l.data.time
    val p = Financas.progressoOrcamento(
        orc.categoria, orc.valorLimite, d.historico + l,
        Financas.inicioDoMes(Financas.mesDe(agora), Financas.anoDe(agora)), Financas.fimDoMes(Financas.mesDe(agora), Financas.anoDe(agora))
    )
    return "$base · ${l.categoria}: ${(p.percentual * 100).toInt()}% do orçamento"
}

@Composable
private fun Seletor(texto: String, ativo: Boolean, cor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (ativo) cor.copy(0.2f) else Color.White.copy(0.06f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(texto, color = if (ativo) cor else Color.White.copy(0.6f), fontWeight = FontWeight.Bold) }
}

@Composable
private fun camposEscuros() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f), cursorColor = NeonCyan
)
