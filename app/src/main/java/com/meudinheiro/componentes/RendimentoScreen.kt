package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.domain.IndicesEconomicos.pctParaFracao
import com.meudinheiro.domain.Rendimentos
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange

/**
 * R98-B — "Onde o dinheiro rende mais?": poupança × produto atrelado ao CDI (IR regressivo) × dinheiro parado. É uma simulação
 * com premissas simples; o texto é neutro (não há recomendação personalizada de investimento).
 */
@Composable
fun RendimentoScreen(userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val c = rememberIndicesLocais(userPrefs).conjunto
    val cdiPadrao = IndicesEconomicos.buscar(c, "cdi").valorMensalPct
    val poupPadrao = IndicesEconomicos.buscar(c, "poupanca").valorMensalPct
    val ipcaPadrao = IndicesEconomicos.buscar(c, "ipca").valorMensalPct
    val trPadrao = IndicesEconomicos.buscar(c, "tr").valorMensalPct
    val selicPadrao = IndicesEconomicos.buscar(c, "selic").valorMensalPct

    var inicial by remember { mutableStateOf("10.000,00") }
    var aporte by remember { mutableStateOf("0") }
    var meses by remember { mutableStateOf("12") }
    var pctCdi by remember { mutableStateOf("100") }
    var cdi by remember { mutableStateOf<String?>(null) }
    var poup by remember { mutableStateOf<String?>(null) }
    var infl by remember { mutableStateOf<String?>(null) }

    val cdiTxt = cdi ?: campoNum(cdiPadrao)
    val poupTxt = poup ?: campoNum(poupPadrao)
    val inflTxt = infl ?: campoNum(ipcaPadrao)

    val entrada = Rendimentos.Entrada(
        valorInicial = if (inicial.isBlank()) 0.0 else lerNum(inicial),
        aporteMensal = if (aporte.isBlank()) 0.0 else lerNum(aporte),
        meses = meses.trim().toIntOrNull() ?: 0,
        cdiMensal = pctParaFracao(lerNum(cdiTxt)),
        pctCdi = lerNum(pctCdi) / 100,
        poupancaMensal = pctParaFracao(lerNum(poupTxt)),
        inflacaoMensal = pctParaFracao(lerNum(inflTxt))
    )
    val erro = Rendimentos.validar(entrada)
    val r = remember(entrada, erro) { if (erro == null) Rendimentos.comparar(entrada) else null }

    val selicAnual = c.extras.selicMetaAnualPct?.let { it / 100 } ?: IndicesEconomicos.anualDeMensal(pctParaFracao(selicPadrao))
    val poupRegra = Rendimentos.poupancaPelaRegra(selicAnual, pctParaFracao(trPadrao))

    PlanTela("Onde o dinheiro rende mais?", onBack) {
        item { PlanAviso() }
        item {
            PlanCartao("Simulação de rendimento") {
                CampoR58(inicial, { inicial = it }, "Valor inicial (R$)", numerico = true, maxChars = 14)
                CampoR58(aporte, { aporte = it }, "Aporte mensal (R$)", numerico = true, maxChars = 14)
                CampoR58(meses, { meses = it.filter(Char::isDigit) }, "Prazo (meses, 1 a 600)", numerico = true, maxChars = 3)
                CampoR58(pctCdi, { pctCdi = it }, "% do CDI do produto", numerico = true, maxChars = 7, apoio = "100 = rende o mesmo que o CDI")
                CampoR58(cdiTxt, { cdi = it }, "CDI (% a.m.)", numerico = true, maxChars = 8, apoio = "Padrão: valor da tela de índices")
                CampoR58(
                    poupTxt, { poup = it }, "Poupança (% a.m.)", numerico = true, maxChars = 8,
                    apoio = "Pela regra com Selic de ${fmtPct(selicAnual)} a.a.: ${fmtPct(poupRegra, 4)} a.m."
                )
                OutlinedButton(onClick = { poup = campoNum(poupRegra * 100, 4) }) { Text("Usar a taxa da regra da poupança", fontSize = 12.sp, color = NeonCyan) }
                CampoR58(inflTxt, { infl = it }, "Inflação (% a.m.)", numerico = true, maxChars = 8, apoio = "Padrão: IPCA mais recente")
            }
        }
        if (erro != null || r == null) {
            item { Text(erro ?: "", color = Color.White.copy(0.6f), fontSize = 13.sp) }
        } else {
            item {
                PlanCartao("Resultado em ${entrada.meses} meses") {
                    PlanLinha("Total aplicado", moedaC(r.totalAplicado, isPrivate))
                    Produto("Poupança (isenta de IR)", r.poupanca, isPrivate, NeonGreen)
                    Produto("Produto a ${fmtNum(entrada.pctCdi * 100, 0)}% do CDI (IR ${fmtPct(Rendimentos.aliquotaIR(30 * entrada.meses), 1)} no prazo)", r.cdi, isPrivate, NeonCyan)
                    Produto("Dinheiro parado (sem rendimento)", r.parado, isPrivate, Color.White)
                    PlanLinha("Poupança − produto CDI (saldo líquido)", moedaC(r.poupanca.saldoFinal - r.cdi.saldoFinal, isPrivate))
                    PlanLinha("Aplicado corrigido pela inflação", moedaC(r.aplicadoCorrigido, isPrivate))
                    PlanLinha("Taxa anual equivalente do CDI informado", fmtPct(IndicesEconomicos.anualDeMensal(entrada.cdiMensal)))
                }
            }
            item {
                PlanCartao("Evolução do saldo líquido") {
                    PlanGrafico(
                        series = listOf(
                            SerieGrafico("Poupança", NeonGreen, r.serie.map { it.poupanca }),
                            SerieGrafico("Produto CDI (líquido)", NeonCyan, r.serie.map { it.cdiLiquido }, tracejada = true),
                            SerieGrafico("Parado", Color(0xFF9AA5B1), r.serie.map { it.parado }, tracejada = true),
                            SerieGrafico("Aplicado corrigido pela inflação", NeonOrange, r.serie.map { it.corrigidoInflacao })
                        ),
                        descricao = "Evolução do saldo líquido mês a mês em ${entrada.meses} meses", privado = isPrivate
                    )
                }
            }
        }
        item {
            PlanCartao("Premissas e limitações") {
                val itens = listOf(
                    "Aporte no fim de cada mês; o do último mês não rende. Mês de 30 dias para o prazo do IR.",
                    "Produto atrelado ao CDI: taxa mensal = % do CDI × CDI mensal; IR regressivo sobre o rendimento de cada aporte (22,5% até 180 dias, 20% de 181 a 360, 17,5% de 361 a 720, 15% acima). Sem come-cotas, IOF, taxas ou garantias.",
                    "Poupança isenta de IR, rendendo todo mês (sem data de aniversário). Regra: Selic acima de 8,5% a.a. = 0,5% a.m. + TR; senão 70% da Selic + TR.",
                    "Ganho real = saldo final − valor aplicado corrigido pela inflação informada. Resultados são projeções e não garantem rentabilidade futura."
                )
                itens.forEach { PlanNota("• $it") }
            }
        }
    }
}

@Composable
private fun Produto(nome: String, p: Rendimentos.Produto, privado: Boolean, cor: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(nome, color = cor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        PlanLinha("Saldo final líquido", moedaC(p.saldoFinal, privado))
        PlanLinha("Ganho líquido", moedaC(p.ganhoLiquido, privado), if (p.ganhoLiquido < 0) CorNegativa else Color.White)
        PlanLinha("IR no resgate", moedaC(p.ir, privado))
        PlanLinha("Ganho real (descontada a inflação)", moedaC(p.ganhoReal, privado), if (p.ganhoReal < 0) CorNegativa else CorPositiva)
    }
}
