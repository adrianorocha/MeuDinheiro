package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.Despesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.centavosAssinados
import com.meudinheiro.funcoes.formatarMoedaBR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Fundo = Color(0xFF131E29)
private val Ciano = Color(0xFF00E5FF)
private val Vidro = Color(0xFF1B263B).copy(alpha = 0.8f)
private val Vermelho = Color(0xFFFF5252)
private val Amarelo = Color(0xFFFFD54F)

private val MESES_CURTOS = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

/**
 * Pagamento seletivo da fatura: lista todos os itens em aberto do grupo agrupados por fatura, com total em tempo
 * real, limite restaurado e saldo da conta após o pagamento. Abre com a fatura exibida inteira selecionada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagarFaturaSheet(
    principal: CartaoComConta,
    idsDoGrupo: Set<Int>,
    despesasDoGrupo: List<Despesa>,
    saldoConta: Double?,
    faturaInicial: Financas.FaturaRef,
    onDismiss: () -> Unit,
    onPagar: (List<Long>, Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cartao = remember(principal) { principal.paraCartao() }
    val faturas = remember(despesasDoGrupo, cartao) { Financas.faturasEmAberto(cartao, despesasDoGrupo, idsDoGrupo) }
    val atual = remember(cartao) { Financas.faturaDaCompra(System.currentTimeMillis(), cartao.diaFechamento) }
    val fmtDia = remember { SimpleDateFormat("dd/MM", Locale("pt", "BR")) }

    fun idsDe(f: Financas.ResumoFatura) = f.itensPendentes.map { it.id }
    val daFaturaInicial = faturas.firstOrNull { it.ref == faturaInicial }?.let { idsDe(it) }.orEmpty()
    var selecionados by remember { mutableStateOf(daFaturaInicial.toSet()) }

    val todosItens = remember(faturas) { faturas.flatMap { it.itensPendentes } }
    val liquidoC = todosItens.filter { it.id in selecionados }.sumOf { -it.centavosAssinados }
    val liquido = Dinheiro.reais(liquidoC)
    val saldoApos = saldoConta?.let { Dinheiro.reais(Dinheiro.centavos(it) - liquidoC) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Fundo,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Payments, null, tint = Ciano, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pagar fatura", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtalhoChip("Pagar toda a fatura do mês", selecionados == daFaturaInicial.toSet() && daFaturaInicial.isNotEmpty()) {
                    selecionados = daFaturaInicial.toSet()
                }
                AtalhoChip("Selecionar itens", false) { selecionados = emptySet() }
                val futuras = faturas.filter { it.ref.ano * 12 + it.ref.mes > atual.ano * 12 + atual.mes }.flatMap { idsDe(it) }
                if (futuras.isNotEmpty()) {
                    AtalhoChip("Antecipar parcelas futuras", selecionados == futuras.toSet()) { selecionados = futuras.toSet() }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { selecionados = todosItens.map { it.id }.toSet() }) { Text("Selecionar tudo", color = Ciano, fontSize = 12.sp) }
                TextButton(onClick = { selecionados = daFaturaInicial.toSet() }) { Text("Só esta fatura", color = Ciano, fontSize = 12.sp) }
                TextButton(onClick = { selecionados = emptySet() }) { Text("Limpar", color = Color.White.copy(0.6f), fontSize = 12.sp) }
            }

            if (faturas.isEmpty()) {
                Text("Nenhum item em aberto.", color = Color.White.copy(0.5f), modifier = Modifier.padding(vertical = 24.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                faturas.forEach { f ->
                    val ids = idsDe(f)
                    val marcados = ids.count { it in selecionados }
                    item(key = "h-${f.ref.ano}-${f.ref.mes}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Vidro)
                                .clickable {
                                    selecionados = if (marcados == ids.size) selecionados - ids.toSet() else selecionados + ids
                                }.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = marcados == ids.size,
                                onCheckedChange = { c -> selecionados = if (c) selecionados + ids else selecionados - ids.toSet() },
                                colors = CheckboxDefaults.colors(checkedColor = Ciano, checkmarkColor = Fundo)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${MESES_CURTOS[f.ref.mes - 1]}/${f.ref.ano}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("vence ${fmtDia.format(Date(f.vencimento))} • $marcados/${ids.size} itens", color = Color.White.copy(0.5f), fontSize = 11.sp)
                            }
                            Text(formatarMoedaBR(f.pendente, false), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    items(f.itensPendentes, key = { it.id }) { d ->
                        val estorno = d.centavosAssinados > 0
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selecionados = if (d.id in selecionados) selecionados - d.id else selecionados + d.id
                            }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = d.id in selecionados,
                                onCheckedChange = { c -> selecionados = if (c) selecionados + d.id else selecionados - d.id },
                                colors = CheckboxDefaults.colors(checkedColor = Ciano, checkmarkColor = Fundo)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(d.descricao, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fmtDia.format(d.data), color = Color.White.copy(0.45f), fontSize = 11.sp)
                            }
                            Text(
                                (if (estorno) "-" else "") + formatarMoedaBR(d.valor, false),
                                color = if (estorno) Color(0xFF69F0AE) else Color.White.copy(0.9f),
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Selecionado", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(formatarMoedaBR(liquido, false), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Limite restaurado", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text(formatarMoedaBR(maxOf(liquido, 0.0), false), color = Ciano, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (saldoApos != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo da conta após", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Text(formatarMoedaBR(saldoApos, false), color = if (saldoApos < 0) Vermelho else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (saldoApos < 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Rounded.Warning, null, tint = Amarelo, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("O saldo da conta ficará negativo.", color = Amarelo, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onPagar(selecionados.toList(), liquido) },
                enabled = liquidoC > 0,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ciano, disabledContainerColor = Color.White.copy(0.1f))
            ) {
                Text("Pagar ${formatarMoedaBR(maxOf(liquido, 0.0), false)}", color = Fundo, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtalhoChip(rotulo: String, ativo: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = ativo,
        onClick = onClick,
        label = { Text(rotulo, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = Color.White.copy(0.8f),
            selectedContainerColor = Ciano.copy(0.25f),
            selectedLabelColor = Ciano
        )
    )
}
