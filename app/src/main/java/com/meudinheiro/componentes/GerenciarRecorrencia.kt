package com.meudinheiro.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meudinheiro.R
import com.meudinheiro.data.DespesaFixa
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.viewModel.ContaSaldoViewModel

// Cores locais
private val DialogBg = Color(0xFF1E2B3E)
private val RedAlert = Color(0xFFEF5350)

@Composable
fun GerenciarRecorrenciaDialog(
    viewModel: ContaSaldoViewModel,
    onDismiss: () -> Unit
) {
    val recorrencias by viewModel.recorrencias.collectAsState()

    // Carrega os dados ao abrir o dialog
    LaunchedEffect(Unit) {
        viewModel.carregarRecorrencias()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Ocupa quase toda a tela
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DialogBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Assinaturas & Fixas",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextWhite
                        )
                        Text(
                            text = "Gerencie seus lançamentos automáticos",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = TextWhite)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (recorrencias.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nenhuma despesa recorrente ativa.",
                            color = TextWhite.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recorrencias) { item ->
                            RecorrenciaItem(
                                item = item,
                                onCancelar = { viewModel.cancelarRecorrencia(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecorrenciaItem(
    item: DespesaFixa,
    onCancelar: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Tenta pegar o ícone
    val resId = remember(item.pic) {
        val id = context.resources.getIdentifier(item.pic, "drawable", context.packageName)
        if (id != 0) id else R.drawable.sim_chip_2
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = DialogBg,
            title = { Text("Cancelar Assinatura?", color = TextWhite) },
            text = {
                Text(
                    "Isso impedirá que novos lançamentos de '${item.descricao}' sejam criados automaticamente nos próximos meses.",
                    color = TextWhite.copy(0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCancelar(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) { Text("Sim, Cancelar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Voltar", color = TextWhite) }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(resId),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Informações
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descricao,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Repeat,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = TextWhite.copy(0.6f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Todo dia ${item.diaVencimento}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextWhite.copy(alpha = 0.6f)
                    )
                }
            }

            // Valor e Delete
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatarMoedaBR(
                        item.valor,
                        false
                    ), // Fixas geralmente não ocultamos aqui, ou passe isPrivate
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = RedAlert.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { showConfirm = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(12.dp), tint = RedAlert)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Cancelar",
                            fontSize = 10.sp,
                            color = RedAlert,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}