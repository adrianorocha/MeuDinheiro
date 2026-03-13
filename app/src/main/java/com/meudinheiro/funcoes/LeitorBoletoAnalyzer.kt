package com.meudinheiro.funcoes

import android.view.View
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class LeitorBoletoAnalyzer(
    private val onCodigoLido: (String, Double?) -> Unit
) : ImageAnalysis.Analyzer {

    // Inicializa o scanner do ML Kit focado em códigos de barras lineares (EAN, ITF, etc)
    private val scanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { codigo ->
                            val view = (onCodigoLido as? View) // Se precisar de referência de View

                            val valor = extrairValorBoleto(codigo)
                            onCodigoLido(codigo, valor)
                        }
                    }
                }
                .addOnCompleteListener {
                    // Importante: Libera o frame para o próximo ciclo
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    // Lógica para extrair valor de Boletos Bancários (ITF-25 ou Linha Digitável)
    private fun extrairValorBoleto(codigo: String): Double? {
        return try {
            if (codigo.length >= 44) {
                // Em boletos bancários, os últimos 10 dígitos costumam ser o valor
                // Ex: 0000001500 -> R$ 15,00
                val valorStr = codigo.substring(codigo.length - 10)
                valorStr.toDouble() / 100.0
            } else null
        } catch (e: Exception) {
            null
        }
    }
}