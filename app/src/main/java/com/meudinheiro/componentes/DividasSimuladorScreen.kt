package com.meudinheiro.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Divida
import com.meudinheiro.domain.CustoRotativo
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Dividas
import com.meudinheiro.domain.DividasSimulacao
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.repository.MainRepository
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import java.util.Calendar

private data class AjusteTxt(val taxa: String? = null, val minimo: String? = null)

/**
 * R98-D — Dívidas: (1) quanto custa ficar no rotativo/cheque especial (1 e 3 meses) e (2) simulador de quitação avalanche × bola de
 * neve × só mínimos sobre as dívidas já cadastradas, com valor extra mensal. Taxa e mínimo faltantes são informados AQUI e valem só
 * na simulação: a dívida cadastrada nunca é alterada.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DividasSimuladorScreen(repository: MainRepository, userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val c = rememberIndicesLocais(userPrefs).conjunto
    val dividas by repository.obterDividasFlow().collectAsState(initial = emptyList())
    val ativas = remember(dividas) { dividas.filter { Dividas.paraSim(it) != null }.sortedBy { it.nome.lowercase() } }

    // --- rotativo / cheque especial
    var cheque by remember { mutableStateOf(false) }
    var saldoTxt by remember { mutableStateOf("") }
    var taxaTxt by remember { mutableStateOf<String?>(null) }
    val padrao = if (cheque) c.extras.taxaChequeEspecialMensalPct else c.extras.taxaRotativoMensalPct
    val taxaMostrada = taxaTxt ?: campoNum(padrao)
    val custo = if (saldoTxt.isBlank()) null else CustoRotativo.custo(lerNum(saldoTxt), lerNum(taxaMostrada) / 100)

    // --- quitação
    var extraTxt by remember { mutableStateOf("0") }
    var ajustes by remember { mutableStateOf<Map<Int, AjusteTxt>>(emptyMap()) }
    val ajustesNum = remember(ajustes) {
        ajustes.mapValues { (_, a) ->
            val t = a.taxa?.takeIf { it.isNotBlank() }?.let { lerNum(it) }?.takeIf { it.isFinite() }
            val m = a.minimo?.takeIf { it.isNotBlank() }?.let { lerNum(it) }?.takeIf { it.isFinite() }
            DividasSimulacao.Ajuste(t, m)
        }
    }
    val extra = lerNum(extraTxt).takeIf { it.isFinite() } ?: 0.0
    val comp = remember(ativas, ajustesNum, extra) { DividasSimulacao.compararComAjustes(ativas, ajustesNum, extra) }
    val hoje = remember { Calendar.getInstance() }
    val anoRef = hoje.get(Calendar.YEAR)
    val mesRef = hoje.get(Calendar.MONTH) + 1

    fun moeda(c: Long) = moedaC(c, isPrivate)
    fun mostrar(r: Dividas.ResultadoSim): String =
        if (r.nuncaQuita) "Não quita em ${Dividas.MAX_MESES} meses"
        else {
            val q = Dividas.dataQuitacao(anoRef, mesRef, r.meses)
            "${r.meses} mes(es)" + (q?.let { " · quita em ${mesAnoExtenso(it.ano, it.mes)}" } ?: "") + " · juros ${moeda(r.jurosTotal)} · total pago ${moeda(r.pagoTotal)}"
        }

    PlanTela("Dívidas: custo e quitação", onBack) {
        item { PlanAviso("Simulação sobre as dívidas cadastradas; os ajustes de taxa e mínimo valem só aqui e não alteram o cadastro.") }
        item {
            PlanCartao("Quanto custa ficar no rotativo ou no cheque especial?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false to "Rotativo do cartão", true to "Cheque especial").forEach { (ch, nome) ->
                        FilterChip(
                            selected = cheque == ch, onClick = { cheque = ch; taxaTxt = null }, label = { Text(nome, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(0.25f), selectedLabelColor = Color.White, labelColor = Color.White.copy(0.7f))
                        )
                    }
                }
                CampoR58(saldoTxt, { saldoTxt = it }, "Saldo devedor (R$)", numerico = true, maxChars = 14)
                CampoR58(taxaMostrada, { taxaTxt = it }, "Taxa (% a.m.)", numerico = true, maxChars = 8, apoio = "Estimativa de referência: edite com a taxa do seu contrato")
                if (custo == null) {
                    PlanNota("Informe o saldo devedor e a taxa para ver quanto a dívida cresce em 1 e em 3 meses sem pagamento.")
                } else {
                    PlanLinha("1 mês sem pagar: juros", moeda(Dinheiro.centavos(custo.m1.juros)), NeonOrange)
                    PlanLinha("1 mês: dívida", moeda(Dinheiro.centavos(custo.m1.total)))
                    PlanLinha("3 meses sem pagar: juros", moeda(Dinheiro.centavos(custo.m3.juros)), NeonOrange)
                    PlanLinha("3 meses: dívida", moeda(Dinheiro.centavos(custo.m3.total)))
                    PlanLinha("Taxa anual equivalente", fmtPct(custo.taxaAnual, 1))
                }
                PlanNota("Juros compostos mensais sobre o saldo, sem multa, IOF ou outros encargos; a regra do seu contrato pode limitar ou acrescentar custos.")
            }
        }
        item { Text("SIMULADOR DE QUITAÇÃO", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        if (ativas.isEmpty()) {
            item {
                PlanCartao("Nenhuma dívida ativa cadastrada") {
                    PlanNota("Cadastre suas dívidas em Ferramentas > Dívidas para comparar as formas de quitação e ver quanto de juros dá para economizar.", Color.White.copy(0.7f))
                }
            }
        } else {
            items(ativas, key = { it.id }) { d -> LinhaAjuste(d, ajustes[d.id] ?: AjusteTxt(), isPrivate) { ajustes = ajustes + (d.id to it) } }
            item {
                PlanCartao("Valor extra e resultado") {
                    CampoR58(extraTxt, { extraTxt = it }, "Valor extra por mês (além dos mínimos)", numerico = true, maxChars = 14)
                    comp.avisos.forEach { PlanNota("⚠ Atenção: $it", NeonOrange) }
                    listOf(comp.minimos to "Só os mínimos", comp.bolaDeNeve to "Bola de neve (menor saldo primeiro)", comp.avalanche to "Avalanche (maior juro primeiro)").forEach { (r, nome) ->
                        Text(nome, color = if (r.nuncaQuita) CorNegativa else CorDestaque, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        PlanNota(mostrar(r), Color.White.copy(0.85f))
                    }
                    comp.economia.avalancheVsBola?.let { PlanNota("Diferença de juros entre avalanche e bola de neve: ${moeda(it)} (positivo = avalanche paga menos juros).", NeonGreen) }
                    comp.economia.avalancheVsMinimos?.let { PlanNota("Avalanche versus pagar só o mínimo: ${moeda(it)} a menos em juros.", NeonGreen) }
                    comp.economia.bolaVsMinimos?.let { PlanNota("Bola de neve versus pagar só o mínimo: ${moeda(it)} a menos em juros.", NeonGreen) }
                    PlanNota(
                        "Juros compostos mensais em centavos; o mínimo de uma dívida quitada é repassado às demais. A bola de neve pode ter apelo de motivação (quita dívidas pequenas cedo) mesmo pagando mais juros. O cronograma completo está em Ferramentas > Dívidas."
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaAjuste(d: Divida, a: AjusteTxt, privado: Boolean, aoMudar: (AjusteTxt) -> Unit) {
    val semTaxa = d.taxaJurosMensal <= 0 && a.taxa.isNullOrBlank()
    PlanCartao {
        Text(d.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        PlanNota((Dividas.ROTULOS[d.tipo] ?: "Outra") + " · saldo " + moedaC(Dinheiro.centavos(d.saldoDevedor), privado))
        if (semTaxa) PlanNota("Sem taxa informada: informe a taxa abaixo só para simular (o cadastro não muda).", NeonOrange)
        CampoR58(a.taxa ?: campoNum(d.taxaJurosMensal), { aoMudar(a.copy(taxa = it)) }, "Juros de ${d.nome} (% a.m.)", numerico = true, maxChars = 8)
        CampoR58(a.minimo ?: campoNum(d.parcelaMinima ?: 0.0, 2), { aoMudar(a.copy(minimo = it)) }, "Mínimo de ${d.nome} (R$)", numerico = true, maxChars = 14)
    }
}
