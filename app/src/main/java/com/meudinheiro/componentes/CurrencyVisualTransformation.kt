package com.meudinheiro.componentes

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // 1. Transforma o texto puro (só números) em um Double (centavos)
        val digits = text.text.filter { it.isDigit() }
        val value = if (digits.isEmpty()) 0.0 else digits.toDouble() / 100.0

        // 2. Formata para o padrão brasileiro
        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

        val annotatedString = AnnotatedString(formatted)

        // 3. Mapeia a posição do cursor (OffsetMapping)
        // Isso impede que o cursor fique "preso" dentro do prefixo R$
        val currencyOffsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = text.length
        }

        return TransformedText(annotatedString, currencyOffsetMapping)
    }
}