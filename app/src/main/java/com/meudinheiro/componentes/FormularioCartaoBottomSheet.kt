package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.CartaoComConta
import com.meudinheiro.data.ContaSaldo
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import java.util.Locale

// Cores
private val DeepSpaceBlue = Color(0xFF131E29)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFF7000FF)
private val CardGlass = Color(0xFF1B263B)
private val ErroVermelho = Color(0xFFFF8A80)

private fun dinheiroParaTexto(v: Double?): String =
    if (v == null || v <= 0.0) "" else String.format(Locale("pt", "BR"), "%.2f", v)

/** Resultado da leitura de um campo numérico: vazio = `null` sem erro; texto inválido = erro. */
private sealed interface Leitura {
    data class Valor(val v: Double?) : Leitura
    data object Invalido : Leitura
}

private fun lerDinheiro(texto: String): Leitura {
    val t = texto.trim().replace(",", ".")
    if (t.isEmpty()) return Leitura.Valor(null)
    val v = t.toDoubleOrNull()
    return if (v == null || !v.isFinite() || v < 0.0) Leitura.Invalido else Leitura.Valor(Dinheiro.arredondar(v))
}

/**
 * Cria OU edita um cartão (R42). Em edição, [cartaoEdicao] preenche os campos e o vínculo com o físico
 * ("Compartilha o saldo do cartão físico") pode ser trocado: virtual vira físico e vice-versa.
 * [todosCartoes] são todos os cartões (para listar os físicos e saber se o editado tem virtuais).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioCartaoBottomSheet(
    contasDisponiveis: List<ContaSaldo>,
    todosCartoes: List<CartaoComConta> = emptyList(),
    cartaoEdicao: CartaoComConta? = null,
    onDismiss: () -> Unit,
    onSalvar: (Cartao) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editando = cartaoEdicao != null

    // Físicos que podem ser "principal": nunca o próprio cartão.
    val fisicos = remember(todosCartoes, cartaoEdicao) {
        todosCartoes.filter { !it.ehVirtual && it.id != cartaoEdicao?.id }
    }
    val temVirtuais = remember(todosCartoes, cartaoEdicao) {
        cartaoEdicao != null && todosCartoes.any { it.cartaoPrincipalId == cartaoEdicao.id }
    }

    var nome by remember { mutableStateOf(cartaoEdicao?.nomeCartao.orEmpty()) }
    var finalCartao by remember { mutableStateOf(cartaoEdicao?.finalCartao.orEmpty()) }
    var tipoSelecionado by remember { mutableStateOf(cartaoEdicao?.tipo ?: "CRÉDITO") }
    var limite by remember { mutableStateOf(dinheiroParaTexto(cartaoEdicao?.limiteTotal)) }
    var diaFechamento by remember { mutableStateOf(cartaoEdicao?.diaFechamento?.toString().orEmpty()) }
    var diaVencimento by remember { mutableStateOf(cartaoEdicao?.diaVencimento?.toString().orEmpty()) }
    var limiteProprio by remember { mutableStateOf(dinheiroParaTexto(cartaoEdicao?.limiteProprio)) }
    var erro by remember { mutableStateOf<String?>(null) }

    var contaVinculadaId by remember { mutableStateOf(cartaoEdicao?.contaId ?: contasDisponiveis.firstOrNull()?.id) }
    var menuContasExpandido by remember { mutableStateOf(false) }

    // Vínculo: null = físico independente; senão compartilha o saldo (limite/fatura) daquele físico.
    var principalId by remember { mutableStateOf(cartaoEdicao?.cartaoPrincipalId) }
    var menuPrincipalExpandido by remember { mutableStateOf(false) }
    val principal = fisicos.find { it.id == principalId }
    val virtual = principalId != null
    val orfao = virtual && principal == null // principal inexistente: precisa ser corrigido aqui

    // Valores herdados (travados) quando compartilha o saldo de um físico.
    val tipoEfetivo = principal?.tipo ?: tipoSelecionado
    val usaLimite = Financas.tipoDeCartao(tipoEfetivo) != "DEBITO"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSpaceBlue,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    editando -> "Editar Cartão"
                    virtual -> "Novo Cartão Virtual"
                    else -> "Novo Cartão"
                },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(2f)) {
                    NeonTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = "Apelido (Ex: Nubank)",
                        icon = Icons.Rounded.CreditCard
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    NeonTextField(
                        value = finalCartao,
                        onValueChange = { if (it.length <= 4) finalCartao = it.filter { char -> char.isDigit() } },
                        label = "Final",
                        icon = Icons.Rounded.Numbers,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // VÍNCULO: "Compartilha o saldo do cartão físico"
            if (fisicos.isNotEmpty() || virtual) {
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = menuPrincipalExpandido,
                    onExpandedChange = { if (!temVirtuais) menuPrincipalExpandido = it }
                ) {
                    OutlinedTextField(
                        value = when {
                            orfao -> "Cartão físico não encontrado: escolha um"
                            principal != null -> "${principal.nomeCartao} •••• ${principal.finalCartao}"
                            else -> "Nenhum (cartão físico independente)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        enabled = !temVirtuais,
                        label = { Text("Compartilha o saldo do cartão físico", color = Color.White.copy(0.7f)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuPrincipalExpandido) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = campoColors(NeonPurple)
                    )
                    ExposedDropdownMenu(
                        expanded = menuPrincipalExpandido,
                        onDismissRequest = { menuPrincipalExpandido = false },
                        modifier = Modifier.background(CardGlass)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nenhum (cartão físico independente)", color = Color.White) },
                            onClick = {
                                principalId = null
                                menuPrincipalExpandido = false
                            }
                        )
                        fisicos.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("💳 ${c.nomeCartao} •••• ${c.finalCartao}", color = Color.White) },
                                onClick = {
                                    principalId = c.id
                                    menuPrincipalExpandido = false
                                    erro = null
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when {
                        temVirtuais -> "Este cartão tem cartões virtuais ligados a ele e não pode compartilhar o saldo de outro. " +
                            "Mova ou exclua os virtuais antes."
                        virtual -> "Usa o mesmo limite, a mesma conta e a mesma fatura do cartão físico. " +
                            "As compras consomem o limite do físico e aparecem na fatura dele."
                        else -> "Escolha um cartão físico para que este passe a compartilhar o limite e a fatura dele."
                    },
                    color = Color.White.copy(0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TIPO (CRÉDITO / DÉBITO / MÚLTIPLO) — herdado do físico quando compartilha o saldo.
            if (virtual && principal != null) {
                CampoTravado("Tipo (herdado do físico)", principal.tipo)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.05f))
                        .padding(4.dp)
                ) {
                    listOf("CRÉDITO", "DÉBITO", "MÚLTIPLO").forEach { tipo ->
                        val isSelected = tipoSelecionado == tipo
                        val corFundo = if (isSelected) (if (tipo == "CRÉDITO") NeonCyan else NeonPurple) else Color.Transparent
                        val corTexto = if (isSelected) DeepSpaceBlue else Color.White.copy(0.6f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(corFundo)
                                .clickable { tipoSelecionado = tipo }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tipo, color = corTexto, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    when (Financas.tipoDeCartao(tipoSelecionado)) {
                        "DEBITO" -> "Compras no débito saem direto da conta (não usam limite nem fatura)."
                        "MULTIPLO" -> "Em cada compra você escolhe crédito (fatura) ou débito (sai da conta)."
                        else -> "Compras no crédito usam o limite e entram na fatura."
                    },
                    color = Color.White.copy(0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CONTA VINCULADA — herdada do físico quando compartilha o saldo.
            if (virtual && principal != null) {
                CampoTravado("Conta vinculada (herdada)", principal.nomeConta)
            } else {
                ExposedDropdownMenuBox(
                    expanded = menuContasExpandido,
                    onExpandedChange = { menuContasExpandido = it }
                ) {
                    val contaAtual = contasDisponiveis.find { it.id == contaVinculadaId }?.banco ?: "Selecione uma conta"
                    OutlinedTextField(
                        value = contaAtual,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Conta Vinculada", color = Color.White.copy(0.7f)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuContasExpandido) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = campoColors(NeonCyan)
                    )
                    ExposedDropdownMenu(
                        expanded = menuContasExpandido,
                        onDismissRequest = { menuContasExpandido = false },
                        modifier = Modifier.background(CardGlass)
                    ) {
                        contasDisponiveis.forEach { conta ->
                            DropdownMenuItem(
                                text = { Text("🏦 ${conta.banco}", color = Color.White) },
                                onClick = {
                                    contaVinculadaId = conta.id
                                    menuContasExpandido = false
                                }
                            )
                        }
                    }
                }
            }

            // LIMITE E CICLO DA FATURA — herdados do físico quando compartilha o saldo.
            if (usaLimite) {
                Spacer(modifier = Modifier.height(16.dp))
                if (virtual && principal != null) {
                    CampoTravado("Limite total (herdado)", dinheiroParaTexto(principal.limiteTotal).ifEmpty { "0,00" })
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { CampoTravado("Dia Fechamento", principal.diaFechamento.toString()) }
                        Box(modifier = Modifier.weight(1f)) { CampoTravado("Dia Vencimento", principal.diaVencimento.toString()) }
                    }
                } else {
                    NeonTextField(
                        value = limite,
                        onValueChange = { limite = it },
                        label = "Limite Total (R$)",
                        icon = Icons.Rounded.AttachMoney,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            NeonTextField(
                                value = diaFechamento,
                                onValueChange = { if (it.length <= 2) diaFechamento = it.filter { char -> char.isDigit() } },
                                label = "Dia Fechamento",
                                icon = Icons.Rounded.Event,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            NeonTextField(
                                value = diaVencimento,
                                onValueChange = { if (it.length <= 2) diaVencimento = it.filter { char -> char.isDigit() } },
                                label = "Dia Vencimento",
                                icon = Icons.Rounded.EventAvailable,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                NeonTextField(
                    value = limiteProprio,
                    onValueChange = { limiteProprio = it; erro = null },
                    label = "Limite próprio (opcional)",
                    icon = Icons.Rounded.AttachMoney,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Teto de gasto deste cartão dentro do limite compartilhado. Deixe vazio para usar todo o limite disponível do grupo.",
                    color = Color.White.copy(0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            erro?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = ErroVermelho, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    fun falha(msg: String) { erro = msg }

                    if (nome.isBlank() || finalCartao.length < 4) {
                        return@Button falha("Preencha o apelido e os 4 últimos dígitos do cartão.")
                    }
                    if (virtual && principal == null) {
                        return@Button falha("Escolha o cartão físico cujo saldo este cartão compartilha (ou \"Nenhum\").")
                    }
                    if (temVirtuais && virtual) {
                        return@Button falha("Este cartão tem virtuais ligados a ele e não pode compartilhar o saldo de outro.")
                    }
                    val proprio = when (val l = lerDinheiro(limiteProprio)) {
                        Leitura.Invalido -> return@Button falha("Limite próprio inválido. Use apenas números, ex.: 500,00.")
                        is Leitura.Valor -> l.v?.takeIf { it > 0.0 }
                    }

                    val cartao: Cartao = if (principal != null) {
                        if (proprio != null && proprio > principal.limiteTotal) {
                            return@Button falha("O limite próprio não pode passar do limite total do cartão físico (${dinheiroParaTexto(principal.limiteTotal)}).")
                        }
                        // Conta, limite, datas e tipo são herdados do físico (e reforçados pelo repositório).
                        Cartao(
                            id = cartaoEdicao?.id ?: 0, nome = nome.trim(), finalCartao = finalCartao, tipo = principal.tipo,
                            limiteDisponivel = principal.limiteTotal, limiteTotal = principal.limiteTotal,
                            diaFechamento = principal.diaFechamento, diaVencimento = principal.diaVencimento,
                            contaId = principal.contaId, cartaoPrincipalId = principal.id, limiteProprio = proprio
                        )
                    } else {
                        val conta = contaVinculadaId ?: return@Button falha("Vincule uma conta ao cartão.")
                        val limiteTotal = when (val l = lerDinheiro(limite)) {
                            Leitura.Invalido -> return@Button falha("Limite total inválido. Use apenas números, ex.: 5000,00.")
                            is Leitura.Valor -> l.v ?: 0.0
                        }
                        val fecha = if (usaLimite) diaFechamento.toIntOrNull() else (diaFechamento.toIntOrNull() ?: 1)
                        val vence = if (usaLimite) diaVencimento.toIntOrNull() else (diaVencimento.toIntOrNull() ?: 1)
                        if (fecha == null || vence == null || fecha !in 1..31 || vence !in 1..31) {
                            return@Button falha("Informe os dias de fechamento e vencimento (entre 1 e 31).")
                        }
                        if (usaLimite && limiteTotal <= 0.0) {
                            return@Button falha("Informe o limite total do cartão (maior que zero).")
                        }
                        if (proprio != null && proprio > limiteTotal) {
                            return@Button falha("O limite próprio não pode passar do limite total do cartão.")
                        }
                        Cartao(
                            id = cartaoEdicao?.id ?: 0, nome = nome.trim(), finalCartao = finalCartao, tipo = tipoSelecionado,
                            limiteDisponivel = limiteTotal, limiteTotal = limiteTotal,
                            diaFechamento = fecha, diaVencimento = vence, contaId = conta,
                            limiteProprio = if (usaLimite) proprio else null
                        )
                    }
                    erro = null
                    onSalvar(cartao)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (virtual || tipoSelecionado != "CRÉDITO") NeonPurple else NeonCyan)
            ) {
                Text(
                    text = if (editando) "SALVAR ALTERAÇÕES" else if (virtual) "ADICIONAR CARTÃO VIRTUAL" else "ADICIONAR CARTÃO",
                    color = DeepSpaceBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun campoColors(destaque: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = destaque,
    unfocusedBorderColor = Color.White.copy(0.2f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = CardGlass,
    unfocusedContainerColor = CardGlass,
    disabledTextColor = Color.White.copy(0.5f),
    disabledBorderColor = Color.White.copy(0.1f),
    disabledLabelColor = Color.White.copy(0.4f),
    disabledContainerColor = CardGlass,
    disabledTrailingIconColor = Color.White.copy(0.3f)
)

/** Campo somente leitura que mostra um valor herdado do cartão físico. */
@Composable
private fun CampoTravado(rotulo: String, valor: String) {
    OutlinedTextField(
        value = valor,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        singleLine = true,
        label = { Text(rotulo, color = Color.White.copy(0.5f)) },
        modifier = Modifier.fillMaxWidth(),
        colors = campoColors(NeonPurple)
    )
}
