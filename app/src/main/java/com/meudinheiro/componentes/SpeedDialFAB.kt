package com.meudinheiro.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.ui.theme.NeonCyan

@Composable
fun SpeedDialFAB(
    onNovoGasto: () -> Unit,
    onNovoInvestimento: () -> Unit,
    onTransferencia: () -> Unit,
    onScanBoleto: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        // Atalhos secundários (aparecem quando expandido)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(spring(stiffness = 300f)),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Opção: Novo Investimento
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Investir",
                        color = Color.White,
                        fontSize = 12.dp.value.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onNovoInvestimento()
                        },
                        containerColor = Color(0xFF12E7FF), // Ciano
                        contentColor = Color(0xFF1B263B),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null)
                    }
                }

                // Opção: Novo Gasto
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nova Despesa",
                        color = Color.White,
                        fontSize = 12.dp.value.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onNovoGasto()
                        },
                        containerColor = Color(0xFF69F0AE), // Verde
                        contentColor = Color(0xFF1B263B),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                    }
                }

                // Opção: Transferência entre Contas
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Transferência",
                        color = Color.White,
                        fontSize = 12.dp.value.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onTransferencia()
                        },
                        containerColor = Color(0xFFEF7354), // Vermelho
                        contentColor = Color(0xFF1B263B),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Escanear Boletos",
                        color = Color.White,
                        fontSize = 12.dp.value.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onScanBoleto()
                        },
                        containerColor = Color(0xFF1B263B),
                        contentColor = NeonCyan, // Cor de destaque para o Scanner
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Botão Principal (+)
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = if (expanded) Color(0xFFEF5350) else Color(0xFF00E5FF),
            contentColor = Color(0xFF1B263B),
            shape = CircleShape,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}