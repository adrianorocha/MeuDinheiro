package com.meudinheiro.componentes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Cartao
import com.meudinheiro.data.ContaSaldo

// Cores
private val DeepSpaceBlue = Color(0xFF131E29)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFF7000FF)
private val CardGlass = Color(0xFF1B263B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioCartaoBottomSheet(
    contasDisponiveis: List<ContaSaldo>, // A lista de contas correntes cadastradas
    onDismiss: () -> Unit,
    onSalvar: (Cartao) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Estados dos campos
    var nome by remember { mutableStateOf("") }
    var finalCartao by remember { mutableStateOf("") }
    var tipoSelecionado by remember { mutableStateOf("CRÉDITO") }
    var limite by remember { mutableStateOf("") }
    var diaFechamento by remember { mutableStateOf("") }
    var diaVencimento by remember { mutableStateOf("") }

    // Estado para a conta vinculada (ID)
    var contaVinculadaId by remember { mutableStateOf<Int?>(contasDisponiveis.firstOrNull()?.id) }
    var menuContasExpandido by remember { mutableStateOf(false) }

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
                text = "Novo Cartão",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // NOME DO CARTÃO E FINAL
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

            Spacer(modifier = Modifier.height(16.dp))

            // SELETOR DE TIPO (CRÉDITO / DÉBITO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.05f))
                    .padding(4.dp)
            ) {
                listOf("CRÉDITO", "DÉBITO").forEach { tipo ->
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
                        Text(tipo, color = corTexto, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SELETOR DE CONTA VINCULADA (Dropdown)
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
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardGlass,
                        unfocusedContainerColor = CardGlass
                    )
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

            // DADOS ESPECÍFICOS DE CRÉDITO
            if (tipoSelecionado == "CRÉDITO") {
                Spacer(modifier = Modifier.height(16.dp))
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

            Spacer(modifier = Modifier.height(32.dp))

            // BOTÃO SALVAR
            Button(
                onClick = {
                    // 1. Validação Simples
                    if (nome.isBlank() || finalCartao.length < 4 || contaVinculadaId == null) {
                        Toast.makeText(context, "Preencha o nome, os 4 últimos dígitos e vincule uma conta.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 2. Converte valores
                    val limiteDouble = limite.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val fechamentoInt = diaFechamento.toIntOrNull() ?: 1
                    val vencimentoInt = diaVencimento.toIntOrNull() ?: 1

                    // 3. Monta o Objeto e envia
                    val novoCartao = Cartao(
                        nome = nome,
                        finalCartao = finalCartao,
                        tipo = tipoSelecionado,
                        limiteTotal = limiteDouble,
                        diaFechamento = fechamentoInt,
                        diaVencimento = vencimentoInt,
                        contaId = contaVinculadaId!! // Já validamos que não é nulo
                    )

                    onSalvar(novoCartao)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (tipoSelecionado == "CRÉDITO") NeonCyan else NeonPurple)
            ) {
                Text(
                    text = "ADICIONAR CARTÃO",
                    color = DeepSpaceBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}