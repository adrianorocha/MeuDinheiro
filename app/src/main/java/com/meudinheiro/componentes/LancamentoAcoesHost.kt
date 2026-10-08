package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.TipoDespesa
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.LancamentoAcoes
import com.meudinheiro.domain.LancamentoAcoes.Acao
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.compartilharComprovante
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.viewModel.ContaSaldoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** Lançamento alvo do menu de ações. [excluirDireto] abre direto a confirmação de exclusão (ex.: swipe). */
data class AlvoLancamento(val id: Long, val excluirDireto: Boolean = false)

private enum class Etapa { MENU, EXCLUIR, REPETIR, ANTECIPAR, EDITAR, BLOQUEADO }

private val SheetBg = Color(0xFF1B263B)
private val Perigo = Color(0xFFEF5350)

/**
 * Menu de ações de um lançamento (conta OU cartão): marcar pago/pendente, antecipar, duplicar, repetir, editar e
 * excluir (com "só esta" × "todas as parcelas"), mostrando só o que a natureza/estado permite (portal: EDITAVEL/ALTERNAVEL).
 * Um host por tela: `var alvo by remember { mutableStateOf<AlvoLancamento?>(null) }` e `LancamentoAcoesHost(alvo, { alvo = null }, ...)`.
 * Exclusão, antecipação, duplicação, repetição e edição de valor pedem a biometria quando `biometriaLancarFlow` está ativo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoAcoesHost(
    alvo: AlvoLancamento?,
    onFechar: () -> Unit,
    viewModel: ContaSaldoViewModel,
    categorias: List<String>,
    getPicCategoria: (String) -> String
) {
    if (alvo == null) return
    val context = LocalContext.current
    val carregado by produceState<Pair<Boolean, Despesa?>>(false to null, alvo.id) {
        value = true to withContext(Dispatchers.IO) { viewModel.obterLancamento(alvo.id) }
    }
    val despesa = carregado.second
    LaunchedEffect(carregado) { if (carregado.first && despesa == null) onFechar() }
    if (despesa == null) return

    val contas by viewModel.contasParaRecorrencia.collectAsState()
    val cartoes by viewModel.cartoesParaRecorrencia.collectAsState()
    val exigirBio by remember { UserPreferences(context).biometriaLancarFlow }.collectAsState(initial = true)
    var etapa by remember(alvo) {
        mutableStateOf(
            if (!alvo.excluirDireto) Etapa.MENU
            else if (LancamentoAcoes.motivoExclusaoBloqueada(despesa.natureza) != null) Etapa.BLOQUEADO else Etapa.EXCLUIR
        )
    }
    val titulo = despesa.descricao

    fun comBio(titulo: String, sub: String, acao: () -> Unit) =
        autenticarParaLancar(context, exigirBio, titulo, sub, onAutorizado = acao)

    when (etapa) {
        Etapa.MENU -> {
            val disponiveis = LancamentoAcoes.acoes(despesa)
            ModalBottomSheet(
                onDismissRequest = onFechar,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = SheetBg
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
                    Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sinal = if (despesa.tipo == TipoDespesa.CREDITO) "+" else "-"
                    Text(
                        "$sinal${formatarMoedaBR(despesa.valor, false)} · ${SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(despesa.data)} · " +
                            (if (despesa.cartaoId != null && despesa.cartaoId != 0) "Cartão" else if (despesa.pago) "Pago" else "Pendente"),
                        color = Color.White.copy(0.6f), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(0.08f))

                    disponiveis.forEach { a ->
                        when (a) {
                            Acao.PAGO_PENDENTE -> LinhaAcao(
                                if (despesa.pago) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle,
                                if (despesa.pago) "Marcar como pendente" else "Marcar como pago", NeonCyan
                            ) { viewModel.alternarPago(despesa.id, !despesa.pago); onFechar() }

                            Acao.ANTECIPAR -> LinhaAcao(Icons.Rounded.FastForward, "Antecipar pagamento", NeonCyan) { etapa = Etapa.ANTECIPAR }

                            Acao.DUPLICAR -> LinhaAcao(Icons.Rounded.ContentCopy, "Duplicar (hoje, em aberto)", NeonCyan) {
                                comBio("Confirmar duplicação", "Autentique para duplicar \"$titulo\"") {
                                    viewModel.duplicarLancamento(despesa.id); onFechar()
                                }
                            }

                            Acao.REPETIR -> LinhaAcao(Icons.Rounded.Repeat, "Repetir…", NeonCyan) { etapa = Etapa.REPETIR }
                            Acao.EDITAR -> LinhaAcao(Icons.Rounded.Edit, "Editar", NeonCyan) { etapa = Etapa.EDITAR }
                            Acao.EXCLUIR -> LinhaAcao(Icons.Rounded.Delete, "Excluir", Perigo) {
                                etapa = if (LancamentoAcoes.motivoExclusaoBloqueada(despesa.natureza) != null) Etapa.BLOQUEADO else Etapa.EXCLUIR
                            }
                        }
                    }
                    LinhaAcao(Icons.Rounded.Share, "Ver comprovante", Color.White.copy(0.8f)) {
                        val nomeCartao = cartoes.firstOrNull { it.id == despesa.cartaoId }?.nome
                        compartilharComprovante(context, despesa, nomeCartao, despesa.conta)
                        onFechar()
                    }
                    if (disponiveis.none { it == Acao.EDITAR }) {
                        Text(LancamentoAcoes.MSG_NAO_EDITAVEL, color = Color.White.copy(0.45f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Etapa.EXCLUIR -> {
            val parcelado = LancamentoAcoes.ehGrupoParcelas(despesa.natureza, despesa.grupoId)
            AlertDialog(
                onDismissRequest = onFechar,
                containerColor = Color(0xFF1E2B3E),
                title = { Text("Excluir lançamento", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Excluir \"$titulo\"?", color = Color.White.copy(0.9f))
                        Text(
                            LancamentoAcoes.avisoExclusao(despesa.natureza, despesa.tipo, despesa.pago, despesa.cartaoId) +
                                " Ele fica 30 dias na Lixeira, onde pode ser restaurado.",
                            color = Color.White.copy(0.6f), fontSize = 13.sp
                        )
                        if (parcelado) TextButton(onClick = {
                            comBio("Confirmar exclusão", "Autentique para excluir todas as parcelas") {
                                viewModel.excluirLancamento(despesa.id, true); onFechar()
                            }
                        }) { Text("Excluir todas as parcelas deste parcelamento", color = Perigo) }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            comBio("Confirmar exclusão", "Autentique para excluir \"$titulo\"") {
                                viewModel.excluirLancamento(despesa.id, false); onFechar()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Perigo)
                    ) { Text(if (parcelado) "Só esta parcela" else "Excluir") }
                },
                dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar", color = Color.White) } }
            )
        }

        Etapa.BLOQUEADO -> AlertDialog(
            onDismissRequest = onFechar,
            containerColor = DeepSpaceBlue,
            title = { Text("Não é possível excluir", color = Color.White) },
            text = { Text(LancamentoAcoes.motivoExclusaoBloqueada(despesa.natureza).orEmpty(), color = Color.White.copy(0.8f)) },
            confirmButton = { TextButton(onClick = onFechar) { Text("Entendi", color = NeonCyan) } }
        )

        Etapa.REPETIR -> RepetirDialog(
            descricao = titulo,
            onDismiss = onFechar,
            onConfirmar = { n, intervalo, unidade ->
                comBio("Confirmar repetição", "Autentique para repetir \"$titulo\" $n vez(es)") {
                    viewModel.repetirLancamento(despesa.id, n, intervalo, unidade); onFechar()
                }
            }
        )

        Etapa.ANTECIPAR -> {
            val candidatos by produceState<List<Despesa>?>(null, despesa.id) {
                value = withContext(Dispatchers.IO) { viewModel.antecipaveisDoGrupo(despesa.id) }
            }
            val lista = candidatos
            if (lista != null && lista.isEmpty()) {
                AlertDialog(
                    onDismissRequest = onFechar, containerColor = DeepSpaceBlue,
                    title = { Text("Não é possível antecipar", color = Color.White) },
                    text = { Text("Só despesas comuns de conta, em aberto, podem ser antecipadas (compras de cartão são pagas pela fatura).", color = Color.White.copy(0.8f)) },
                    confirmButton = { TextButton(onClick = onFechar) { Text("Entendi", color = NeonCyan) } }
                )
            } else if (lista != null) {
                AntecipacaoDialog(
                    base = despesa, candidatos = lista,
                    saldoConta = contas.firstOrNull { it.conta == despesa.conta }?.saldo,
                    onDismiss = onFechar,
                    onConfirmar = { ids, pago, desconto, data ->
                        comBio("Confirmar antecipação", "Autentique para pagar ${formatarMoedaBR(pago, false)} adiantado") {
                            viewModel.anteciparPagamento(ids, pago, desconto, data); onFechar()
                        }
                    }
                )
            }
        }

        Etapa.EDITAR -> EditarLancamentoDialog(
            despesa = despesa, contas = contas, cartoes = cartoes,
            categorias = categorias, getPicCategoria = getPicCategoria,
            onDismiss = onFechar,
            onSalvar = { editada, modalidade ->
                val mudouValor = Dinheiro.arredondar(editada.valor) != Dinheiro.arredondar(despesa.valor)
                val gravar = { viewModel.editarLancamento(editada, modalidade); onFechar() }
                if (mudouValor) comBio("Confirmar edição", "Autentique para alterar o valor de \"$titulo\"") { gravar() } else gravar()
            }
        )
    }
}

@Composable
private fun LinhaAcao(icone: ImageVector, texto: String, cor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(texto, color = if (cor == Perigo) Perigo else Color.White, fontSize = 15.sp)
    }
}
