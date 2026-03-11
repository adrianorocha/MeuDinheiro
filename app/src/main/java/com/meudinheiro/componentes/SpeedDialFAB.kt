package com.meudinheiro.componentes

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedDialFAB(
    onNovoGasto: () -> Unit,
    onNovoInvestimento: () -> Unit
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
                    Text("Investir", color = Color.White, fontSize = 12.dp.value.sp, modifier = Modifier.padding(end = 8.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onNovoInvestimento()
                        },
                        containerColor = Color(0xFF00E5FF), // Ciano
                        contentColor = Color(0xFF1B263B),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null)
                    }
                }

                // Opção: Novo Gasto
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gasto", color = Color.White, fontSize = 12.dp.value.sp, modifier = Modifier.padding(end = 8.dp))
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