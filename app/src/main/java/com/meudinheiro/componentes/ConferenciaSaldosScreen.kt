package com.meudinheiro.componentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.ConferenciaSaldos
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.repository.ResultadoRecalculo
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch

private val Claro = Color(0xFFE0E1DD)
private val Fundo = Color(0xFF1B263B)

/**
 * Conferência de saldos: mostra, por conta e cartão, o valor gravado × o calculado do extrato, a decomposição do
 * saldo, os totais (Entradas × Saídas) e inconsistências de dados; permite recalcular os caches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenciaSaldosScreen(repository: MainRepository, isPrivate: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exigirBio by remember { UserPreferences(ctx).biometriaLancarFlow }.collectAsState(initial = true)
    BackHandler(onBack = onBack)

    // Reativo: reconfere a cada mudança do extrato, das contas ou dos cartões.
    val despesas by repository.todasDespesasFlow.collectAsState(initial = emptyList())
    val contasFlow by repository.getTodasContas().collectAsState(initial = emptyList())
    val cartoesFlow by repository.cartoesFlow().collectAsState(initial = emptyList())
    var relatorio by remember { mutableStateOf<ConferenciaSaldos.Relatorio?>(null) }
    LaunchedEffect(despesas, contasFlow, cartoesFlow) { relatorio = runCatching { repository.conferirSaldos() }.getOrNull() }

    var confirmar by remember { mutableStateOf(false) }
    var rodando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<ResultadoRecalculo?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    fun m(v: Double) = formatarMoedaBR(v, isPrivate)

    Scaffold(
        containerColor = DeepSpaceBlue,
        topBar = {
            TopAppBar(
                title = { Text("Conferência de saldos", color = Claro, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Claro) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val r = relatorio
        if (r == null) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ------------------------------------------------ resultado do último recálculo
            resultado?.let { res ->
                item {
                    Bloco {
                        Titulo(Icons.Default.CheckCircle, "Resultado do recálculo", NeonGreen)
                        if (res.nenhumaDiferenca) Texto("Tudo conferido: nenhuma diferença.", NeonGreen)
                        else {
                            Texto("${res.saldosCorrigidos} saldos e ${res.limitesCorrigidos} limites corrigidos.", Claro)
                            res.contas.forEach { Texto("${it.banco}: ${m(it.antes)} → ${m(it.depois)}", Claro.copy(0.8f), 12) }
                            res.cartoes.forEach { Texto("${it.nome}: limite ${m(it.antes)} → ${m(it.depois)}", Claro.copy(0.8f), 12) }
                        }
                    }
                }
            }
            erro?.let { e -> item { Bloco { Titulo(Icons.Default.Warning, "Não foi possível recalcular", NeonRed); Texto(e, Claro) } } }

            // ------------------------------------------------ contas
            item { Secao("Contas: gravado × calculado") }
            if (r.contas.isEmpty()) item { Texto("Nenhuma conta cadastrada.", Claro.copy(0.6f)) }
            r.contas.forEach { c ->
                item {
                    Bloco {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.banco, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("conta ${c.conta}", color = Claro.copy(0.5f), fontSize = 11.sp)
                            }
                            Selo(!c.divergente)
                        }
                        Linha("Saldo gravado", m(c.saldoGravado))
                        Linha("Saldo calculado", m(c.saldoCalculado), destaque = true)
                        if (c.divergente) Linha("Diferença", m(c.diferenca), cor = NeonOrange)

                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Text("De onde vem o saldo (lançamentos pagos)", color = Claro.copy(0.6f), fontSize = 11.sp)
                        c.decomposicao.forEach {
                            val sinal = if (it.centavos > 0) "+ " else if (it.centavos < 0) "− " else ""
                            Linha("${it.tipo.rotulo} (${it.quantidade})", sinal + m(kotlin.math.abs(it.valor)), cor = if (it.centavos >= 0) NeonGreen else NeonRed)
                        }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Linha("Total = saldo calculado", m(com.meudinheiro.domain.Dinheiro.reais(c.somaDecomposicaoC)), destaque = true)

                        val p = c.pendencias
                        val temPend = p.receitasPrevistasC != 0L || p.despesasPendentesC != 0L || p.comprasCartaoEmAbertoC != 0L
                        if (temPend) {
                            HorizontalDivider(color = Color.White.copy(0.1f))
                            Text("Ainda fora do saldo", color = Claro.copy(0.6f), fontSize = 11.sp)
                            fun mc(v: Long) = m(com.meudinheiro.domain.Dinheiro.reais(v))
                            if (p.receitasPrevistasC != 0L) Linha("Receitas previstas", mc(p.receitasPrevistasC))
                            if (p.despesasAtrasadasC != 0L) Linha("Despesas atrasadas", mc(p.despesasAtrasadasC))
                            if (p.despesasAVencerC != 0L) Linha("Despesas a vencer (até ${ConferenciaSaldos.DIAS_A_VENCER} dias)", mc(p.despesasAVencerC))
                            if (p.despesasFuturasC != 0L) Linha("Despesas futuras", mc(p.despesasFuturasC))
                            if (p.comprasCartaoEmAbertoC != 0L) Linha("Compras de cartão em aberto", mc(p.comprasCartaoEmAbertoC))
                        }
                    }
                }
            }

            // ------------------------------------------------ cartões
            if (r.cartoes.isNotEmpty()) {
                item { Secao("Cartões: limite gravado × calculado") }
                r.cartoes.forEach { g ->
                    item {
                        Bloco {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${g.nome} ••${g.finalCartao}" + if (g.gravados.size > 1) " (+${g.gravados.size - 1} virtual)" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("limite total ${m(g.limiteTotal)}", color = Claro.copy(0.5f), fontSize = 11.sp)
                                }
                                Selo(!g.divergente)
                            }
                            Linha("Limite gravado", m(g.limiteGravado))
                            Linha("Limite calculado", m(g.limiteCalculado), destaque = true)
                            if (g.divergente) Linha("Diferença", m(g.diferenca), cor = NeonOrange)
                            Linha("Em aberto na fatura atual", m(g.emAbertoFatura))
                            Linha("Parcelas futuras", m(g.parcelasFuturas))
                        }
                    }
                }
            }

            // ------------------------------------------------ totais
            item { Secao("Entradas × Saídas × Saldo") }
            item {
                val t = r.totais
                Bloco {
                    Linha("Entradas realizadas", m(t.entradasRealizadas), cor = NeonGreen)
                    Linha("Entradas previstas (ainda não recebidas)", m(t.entradasPrevistas))
                    Linha("Saídas pagas", m(t.saidasPagas), cor = NeonRed)
                    Linha("Saídas pendentes / futuras / cartão em aberto", m(t.saidasPendentes), cor = NeonRed)
                    Linha("Saídas total (o que o cabeçalho mostra)", m(t.saidasTotal), destaque = true)
                    HorizontalDivider(color = Color.White.copy(0.1f))
                    Linha("Entradas − Saídas", m(t.entradasMenosSaidas))
                    Linha("Saldo das contas (calculado)", m(t.saldoCalculado), destaque = true)
                    Spacer(Modifier.height(2.dp))
                    Text(r.explicacao { m(it) }, color = Claro.copy(0.75f), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }

            // ------------------------------------------------ inconsistências
            item { Secao("Inconsistências de dados (${r.inconsistencias.size})") }
            if (r.inconsistencias.isEmpty()) item { Texto("Nenhuma inconsistência encontrada.", NeonGreen) }
            else r.inconsistencias.forEach { i ->
                item {
                    Bloco {
                        val cor = when (i.severidade) {
                            ConferenciaSaldos.Severidade.ERRO -> NeonRed
                            ConferenciaSaldos.Severidade.AVISO -> NeonOrange
                            ConferenciaSaldos.Severidade.INFO -> NeonCyan
                        }
                        Titulo(if (i.severidade == ConferenciaSaldos.Severidade.INFO) Icons.Default.Info else Icons.Default.Warning, "${i.tipo.rotulo} · id ${i.id}", cor)
                        Texto(i.descricao, Claro, 12)
                    }
                }
            }
            if (r.inconsistencias.isNotEmpty()) item { Texto("Nada foi apagado. Corrija ou exclua o lançamento pelo extrato, se for o caso.", Claro.copy(0.5f), 11) }

            // ------------------------------------------------ ação
            item {
                Button(
                    onClick = { confirmar = true }, enabled = !rodando, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rodando) "Recalculando…" else "Recalcular saldos agora", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = Fundo,
            title = { Text("Recalcular saldos?", color = Color.White) },
            text = {
                Text(
                    "Os saldos das contas e os limites dos cartões serão regravados a partir do extrato. Nenhum lançamento é alterado ou apagado.",
                    color = Claro.copy(0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = false
                    autenticarParaLancar(ctx, exigirBio, "Recalcular saldos", "Autentique para recalcular saldos e limites") {
                        scope.launch {
                            rodando = true; erro = null
                            runCatching { repository.recalcularSaldos() }
                                .onSuccess { resultado = it }
                                .onFailure { erro = it.message ?: "Erro inesperado." }
                            rodando = false
                        }
                    }
                }) { Text("Recalcular", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar", color = Claro.copy(0.7f)) } }
        )
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(titulo, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Bloco(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Fundo, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
private fun Titulo(icone: androidx.compose.ui.graphics.vector.ImageVector, texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, color = cor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Texto(t: String, cor: Color, tamanho: Int = 13) {
    Text(t, color = cor, fontSize = tamanho.sp)
}

@Composable
private fun Linha(rotulo: String, valor: String, destaque: Boolean = false, cor: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = Claro.copy(if (destaque) 0.95f else 0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(valor, color = cor, fontSize = 13.sp, fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Selo(ok: Boolean) {
    val cor = if (ok) NeonGreen else NeonOrange
    Row(
        Modifier.background(cor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = cor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (ok) "OK" else "DIVERGENTE", color = cor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
