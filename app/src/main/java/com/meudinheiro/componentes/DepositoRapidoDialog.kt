package com.meudinheiro.componentes

import androidx.benchmark.traceprocessor.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.MetaPremium
import androidx.compose.ui.graphics.SolidColor

@Composable
fun DepositoRapidoDialog(
    meta: MetaPremium,
    onConfirmar: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var valorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PremiumDarkBlue.copy(alpha = 0.98f), // Um pouco mais opaco para ler melhor
        modifier = Modifier.border(1.dp, meta.corDestaque.copy(0.3f), RoundedCornerShape(28.dp)),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🚀 Depósito Rápido", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(meta.nome.uppercase(), color = meta.corDestaque, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                // 🚀 O CAMPO REFORMADO
                BasicTextField(
                    value = valorText,
                    onValueChange = {
                        // Aceita apenas números e um ponto/vírgula
                        if (it.all { char -> char.isDigit() || char == ',' || char == '.' } && it.length <= 10) {
                            valorText = it
                        }
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(meta.corDestaque),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    decorationBox = { innerTextField ->
                        // 🚀 O SEGREDO: Box para sobrepor o placeholder e o campo real
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (valorText.isEmpty()) {
                                Text(
                                    text = "R$ 0,00",
                                    color = Color.White.copy(alpha = 0.15f),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            // O R$ aparece apenas quando começa a digitar
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (valorText.isNotEmpty()) {
                                    Text(
                                        text = "R$ ",
                                        color = meta.corDestaque,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 💡 O innerTextField() PRECISA estar fora de qualquer 'if'
                                // para que o campo de digitação esteja sempre ativo!
                                innerTextField()
                            }
                        }
                    }
                )

                Text(
                    text = "Quanto vamos lançar ao espaço?",
                    color = Color.White.copy(0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Converte vírgula em ponto para não quebrar o Double
                    val valor = valorText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    onConfirmar(valor)
                },
                colors = ButtonDefaults.buttonColors(containerColor = meta.corDestaque),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.7f).padding(bottom = 8.dp)
            ) {
                Text("CONFIRMAR", color = PremiumDarkBlue, fontWeight = FontWeight.ExtraBold)
            }
        }
    )
}