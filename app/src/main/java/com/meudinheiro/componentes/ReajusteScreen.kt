package com.meudinheiro.componentes

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.domain.Reajustes
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange

private val IDS_REAJUSTE = listOf("igpm", "ipca", "inpc")

/**
 * R98-F — Reajuste (aluguel/mensalidade): composição dos últimos N meses de IGP-M, IPCA e INPC (ou acumulado oficial informado),
 * novo valor e diferença por índice. Índice negativo REDUZ o valor (sem piso).
 */
@Composable
fun ReajusteScreen(userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val c = rememberIndicesLocais(userPrefs).conjunto
    var valor by remember { mutableStateOf("") }
    var meses by remember { mutableStateOf("12") }
    var informados by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var manual by remember { mutableStateOf("") }

    val indices = ArrayList<Reajustes.Indice>()
    for (id in IDS_REAJUSTE) {
        val it = IndicesEconomicos.buscar(c, id)
        val v = informados[id]?.takeIf { t -> t.isNotBlank() }?.let { t -> lerNum(t) }?.takeIf { x -> x.isFinite() }
        indices += Reajustes.Indice(id, it.nome, it.serie, v)
    }
    lerNum(manual).takeIf { manual.isNotBlank() && it.isFinite() }?.let { indices += Reajustes.Indice("manual", "Percentual informado", emptyList(), it) }
    val n = meses.trim().toIntOrNull() ?: 0
    val linhas = if (valor.isBlank()) null else Reajustes.calcular(lerNum(valor), n, indices)
    val incompleta = linhas?.any { it.incompleto } ?: false

    PlanTela("Reajuste de aluguel e mensalidades", onBack) {
        item { PlanAviso("Reajuste calculado com os índices de referência editáveis; confira o índice e o período previstos no seu contrato.") }
        item {
            PlanCartao("Dados do reajuste") {
                CampoR58(valor, { valor = it }, "Valor atual (R$)", numerico = true, maxChars = 14)
                CampoR58(meses, { meses = it.filter(Char::isDigit) }, "Meses de acumulado (1 a 120)", numerico = true, maxChars = 3, apoio = "Composição dos últimos N meses disponíveis de cada índice")
                CampoR58(manual, { manual = it }, "Outro percentual acumulado (%)", numerico = true, maxChars = 8, apoio = "Opcional; ex.: 4,5 para um reajuste combinado")
                Text("Acumulado oficial de ${if (n > 0) n else "N"} meses (opcional; substitui a composição)", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IDS_REAJUSTE.forEach { id ->
                    CampoR58(
                        informados[id].orEmpty(), { informados = informados + (id to it) },
                        "${IndicesEconomicos.buscar(c, id).nome} acumulado (%)", numerico = true, maxChars = 8
                    )
                }
            }
        }
        item {
            when {
                valor.isBlank() -> Text("Informe o valor atual do aluguel ou da mensalidade para ver o reajuste por cada índice.", color = Color.White.copy(0.6f), fontSize = 13.sp)
                linhas == null -> Text("Informe um valor maior que zero e de 1 a 120 meses.", color = CorNegativa, fontSize = 13.sp)
                else -> PlanCartao("Reajuste por índice") {
                    PlanNota("Valor atual: " + moedaC(Math.round(lerNum(valor) * 100), isPrivate))
                }
            }
        }
        if (linhas != null) {
            items(linhas, key = { it.id }) { l ->
                PlanCartao {
                    Text(l.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    PlanNota(
                        if (l.informado) "acumulado informado" else if (l.incompleto) "${l.mesesUsados} de $n meses disponíveis" else "${l.mesesUsados} meses compostos",
                        if (l.incompleto && !l.informado) NeonOrange else Color.White.copy(0.55f)
                    )
                    PlanLinha("Acumulado", fmtPct(l.acumulado, 4))
                    PlanLinha("Novo valor", moedaC(l.novoValor, isPrivate), NeonCyan)
                    PlanLinha("Diferença", moedaC(l.diferenca, isPrivate))
                }
            }
            item {
                if (incompleta) PlanNota(
                    "Só há alguns meses de cada índice na tela de índices; o cálculo usa os meses disponíveis. Para o reajuste anual, informe o acumulado oficial de 12 meses acima ou edite a série de meses em Índices e taxas.",
                    NeonOrange
                )
            }
            item {
                PlanNota("Índice negativo reduz o valor; verifique se o contrato prevê piso. Cálculo por composição (1 + i1) × (1 + i2) × ... − 1, valor arredondado ao centavo.")
            }
        }
    }
}
