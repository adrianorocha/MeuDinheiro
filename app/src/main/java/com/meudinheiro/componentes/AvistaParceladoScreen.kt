package com.meudinheiro.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.AvistaParcelado
import com.meudinheiro.domain.AvistaParcelado.MenorCusto
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.domain.IndicesEconomicos.pctParaFracao
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange

/**
 * R98-C — À vista × parcelado: taxa implícita mensal/anual (bisseção, tolerância 1e-9), total pago, juros embutidos, valor presente
 * das parcelas a CDI líquido de IR e desconto à vista de equilíbrio. Simulação; não é recomendação.
 */
@Composable
fun AvistaParceladoScreen(userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val c = rememberIndicesLocais(userPrefs).conjunto
    val cdiPadrao = IndicesEconomicos.buscar(c, "cdi").valorMensalPct
    var aVista by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("10") }
    var parcela by remember { mutableStateOf("") }
    var entrada by remember { mutableStateOf("") }
    var pctCdi by remember { mutableStateOf("100") }
    var cdi by remember { mutableStateOf<String?>(null) }
    val cdiTxt = cdi ?: campoNum(cdiPadrao)

    val e = AvistaParcelado.Entrada(lerNum(aVista), n.trim().toIntOrNull() ?: 0, lerNum(parcela), if (entrada.isBlank()) 0.0 else lerNum(entrada))
    val preenchido = aVista.isNotBlank() && parcela.isNotBlank()
    val erro = if (preenchido) AvistaParcelado.validar(e) else null
    val r = if (preenchido && erro == null) AvistaParcelado.comparar(e, pctParaFracao(lerNum(cdiTxt)), lerNum(pctCdi) / 100) else null

    fun moeda(v: Double) = formatarMoedaBR(v, isPrivate)

    PlanTela("À vista × parcelado", onBack) {
        item { PlanAviso() }
        item {
            PlanCartao("Dados da compra") {
                CampoR58(aVista, { aVista = it }, "Preço à vista (R$)", numerico = true, maxChars = 14)
                CampoR58(n, { n = it.filter(Char::isDigit) }, "Número de parcelas", numerico = true, maxChars = 3)
                CampoR58(parcela, { parcela = it }, "Valor da parcela (R$)", numerico = true, maxChars = 14)
                CampoR58(entrada, { entrada = it }, "Entrada (R$, opcional)", numerico = true, maxChars = 14)
                CampoR58(cdiTxt, { cdi = it }, "CDI (% a.m.)", numerico = true, maxChars = 8, apoio = "Padrão: tela de índices")
                CampoR58(pctCdi, { pctCdi = it }, "% do CDI da aplicação", numerico = true, maxChars = 7)
            }
        }
        item {
            when {
                erro != null -> Text(erro, color = CorNegativa, fontSize = 13.sp)
                r == null -> Text(
                    "Preencha o preço à vista, o número de parcelas e o valor da parcela para ver a taxa embutida e a comparação.",
                    color = Color.White.copy(0.6f), fontSize = 13.sp
                )
                else -> PlanCartao("Resultado") {
                    PlanLinha("Taxa implícita", fmtPct(r.taxaMensal, 3) + " a.m.", NeonCyan)
                    PlanLinha("Taxa anual equivalente", fmtPct(r.taxaAnual, 2) + " a.a.")
                    PlanLinha("Total pago parcelado", moeda(r.totalPago))
                    PlanLinha("Juros embutidos", moeda(r.juros), if (r.juros > 0) NeonOrange else Color.White)
                    val (rotulo, cor) = when (r.menorCusto) {
                        MenorCusto.AVISTA -> "à vista custa menos em valor presente" to NeonGreen
                        MenorCusto.PARCELADO -> "parcelado custa menos em valor presente" to NeonGreen
                        MenorCusto.EMPATE -> "empate em valor presente" to Color.White
                    }
                    Text("Resultado da simulação: $rotulo", color = cor, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    PlanNota(
                        "Valor presente das parcelas (dinheiro que você precisaria aplicar hoje, líquido de IR, para pagá-las): ${moeda(r.valorPresente)} contra ${moeda(e.aVista)} à vista.",
                        Color.White.copy(0.8f)
                    )
                    PlanLinha(
                        "Diferença (" + (if (r.diferenca > 0) "parcelar custa mais" else if (r.diferenca < 0) "parcelar custa menos" else "iguais") + ", em valor presente)",
                        moeda(r.diferenca)
                    )
                    PlanLinha("Desconto à vista oferecido sobre o total parcelado", fmtPct(r.descontoOferecido, 2))
                    PlanLinha("Desconto de equilíbrio (break-even)", fmtPct(maxOf(0.0, r.descontoBreakEven), 2))
                    PlanNota(
                        "A partir do desconto de equilíbrio, pagar à vista passa a custar menos que parcelar (preço à vista de equilíbrio: ${moeda(r.valorPresente)}).",
                        Color.White.copy(0.7f)
                    )
                    PlanNota(
                        "Considera parcelas mensais (a primeira 30 dias após a compra), rendimento do dinheiro a ${fmtNum(lerNum(pctCdi), 0)}% do CDI com IR regressivo, sem custos adicionais. " +
                            "Não considera valor de revenda, risco de crédito nem outras vantagens (frete, cashback). É uma simulação, não uma recomendação."
                    )
                }
            }
        }
    }
}
