package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun ScannerBoletoScreen(
    onResult: (String, Double?) -> Unit,
    onClose: () -> Unit
) {
    var codigoDetectado by remember { mutableStateOf<Pair<String, Double?>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. A Câmera no fundo
        ScannerCameraPreview(onCodigoDetectado = { codigo, valor ->
            if (codigoDetectado == null) { // Trava a primeira leitura
                codigoDetectado = codigo to valor
            }
        })

        // 2. O Overlay Neon por cima
        ScannerNeonOverlay(
            onClose = onClose,
            onToggleFlash = { /* Lógica de Flash se desejar */ }
        )

        // 3. Dialog de Confirmação (Só aparece quando lê)
        codigoDetectado?.let { (codigo, valor) ->
            BoletoConfirmacaoDialog(
                codigoLido = codigo,
                valorExtraido = valor,
                onConfirmar = { valorFinal ->
                    onResult(codigo, valorFinal)
                },
                onDescartar = { codigoDetectado = null }
            )
        }
    }
}