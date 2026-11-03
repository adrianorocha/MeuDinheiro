package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.domain.Bcb
import com.meudinheiro.domain.IndicesEconomicos
import com.meudinheiro.domain.IndicesEconomicos.Item
import com.meudinheiro.domain.IndicesEconomicos.Origem
import com.meudinheiro.funcoes.BcbCachePrefs
import com.meudinheiro.funcoes.BcbClient
import com.meudinheiro.funcoes.BcbControle
import com.meudinheiro.funcoes.HttpBcb
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.temRede
import com.meudinheiro.ui.theme.DeepSpaceBlue
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonOrange
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * R98-A / R100 — Índices e taxas (Selic, CDI, TR, poupança, inflação, salário mínimo): valor mensal, anual equivalente,
 * competência, selo de origem (BCB / BCB · parcial do mês / manual / semente (Debit)), edição local que SEMPRE prevalece,
 * "Usar automático" por campo e "Restaurar padrão" (descarta só as edições manuais; o cache do BCB permanece).
 *
 * R100: ao ABRIR a tela, consulta a API pública do Banco Central se o cache local tiver mais de 6 h; "Atualizar agora" força a
 * consulta. Nunca há consulta em segundo plano nem ao abrir outras telas. Nenhum dado do usuário é enviado.
 */
@Composable
fun IndicesEconomicosScreen(userPrefs: UserPreferences, isPrivate: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val locais = rememberIndicesLocais(userPrefs)
    val c = locais.conjunto // efetivo (base + BCB)
    val b = locais.base // só semente + edições manuais (é o que se grava)
    val controle = remember(userPrefs) { BcbControle(BcbCachePrefs(userPrefs), BcbClient(HttpBcb())) }
    val estado by controle.indices.collectAsState()
    val uri = LocalUriHandler.current
    var editando by remember { mutableStateOf<Item?>(null) }
    var editandoSalario by remember { mutableStateOf(false) }
    var confirmaRestaurar by remember { mutableStateOf(false) }

    // só ao abrir esta tela: respeita o TTL de 6 h (sem cache vencido, nenhuma requisição)
    LaunchedEffect(controle) { controle.atualizarIndices(forcar = false, conectado = temRede(ctx)) }

    fun abrir(url: String?) {
        if (url != null) runCatching { uri.openUri(url) }
    }

    fun usarAutomatico(id: String) {
        val padrao = IndicesEconomicos.padrao().itens.firstOrNull { it.id == id } ?: return
        locais.salvar(b.copy(itens = b.itens.map { if (it.id == id) padrao else it }))
    }

    val atualizadoEm = estado.snapshot?.atualizadoEm ?: locais.bcb?.atualizadoEm
    val textoEstado = Bcb.descreverEstado(Bcb.EstadoAtualizacao(atualizadoEm, estado.carregando, estado.erro, estado.offline))

    PlanTela("Índices e taxas", onBack) {
        item {
            PlanAviso(
                "Valores de referência do Banco Central, atualizados ao abrir esta tela (no máximo a cada 6 horas) ou em \"Atualizar agora\"; " +
                    "você pode editar qualquer valor e a sua edição prevalece. Confira o histórico antes de decidir."
            )
        }
        item {
            PlanCartao {
                Text(
                    textoEstado, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                estado.avisoParcial?.let { PlanNota(it, NeonOrange) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { scope.launch { controle.atualizarIndices(forcar = true, conectado = temRede(ctx)) } },
                        enabled = !estado.carregando,
                        modifier = Modifier.semantics { contentDescription = "Atualizar índices agora com o Banco Central" }
                    ) { Text("Atualizar agora", fontSize = 12.sp, color = NeonCyan) }
                    if (estado.carregando) {
                        CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Atualizando" }, color = NeonCyan, strokeWidth = 2.dp)
                    }
                }
            }
        }
        item {
            PlanCartao("Índices e taxas") {
                PlanNota(
                    IndicesEconomicos.FONTE + ". Selos: BCB = valor do Banco Central (competência real); manual = editado por você (não é sobrescrito); " +
                        "semente (Debit) = valor de reserva de 28/10/2026, com competência aproximada. IGP-DI e IPCA-E vêm do BCB; IVAR, TBF e TJLP " +
                        "não têm série confirmada e ficam como a semente/edição. Selic e CDI do mês corrente são acumulados parciais. " +
                        "Anual equivalente = (1 + taxa mensal)^12 − 1; para a inflação é só o mês repetido por 12 vezes, não o acumulado de 12 meses."
                )
                PlanLinha("Conferido em", dataBr(c.conferidoEm))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { locais.salvar(b.copy(conferidoEm = hojeIso())) }) { Text("Marcar conferido hoje", fontSize = 12.sp, color = NeonCyan) }
                    OutlinedButton(onClick = { confirmaRestaurar = true }) { Text("Restaurar padrão", fontSize = 12.sp, color = NeonCyan) }
                }
            }
        }
        item { Text("TAXAS", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        items(c.itens.filter { it.grupo == IndicesEconomicos.Grupo.TAXA }, key = { it.id }) {
            CartaoIndice(it, locais.bcb?.itens?.containsKey(it.id) == true, { editando = it }, { usarAutomatico(it.id) }, ::abrir)
        }
        item { Text("INFLAÇÃO E PREÇOS", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        items(c.itens.filter { it.grupo == IndicesEconomicos.Grupo.INFLACAO }, key = { it.id }) {
            CartaoIndice(it, locais.bcb?.itens?.containsKey(it.id) == true, { editando = it }, { usarAutomatico(it.id) }, ::abrir)
        }
        item { DicaPoupanca(c) }
        item {
            PlanCartao("Salário mínimo") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Valor vigente (${c.salarioMinimo.competencia.ifBlank { "—" }})", color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Selo(c.salarioMinimo.origem, false)
                }
                PlanLinha("Valor", moedaC(c.salarioMinimo.centavos, isPrivate), NeonCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { editandoSalario = true }) { Text("Editar salário mínimo", fontSize = 12.sp, color = NeonCyan) }
                    if (c.salarioMinimo.origem == Origem.MANUAL && locais.bcb?.salarioMinimo != null) {
                        TextButton(
                            onClick = { locais.salvar(b.copy(salarioMinimo = IndicesEconomicos.padrao().salarioMinimo)) },
                            modifier = Modifier.semantics { contentDescription = "Usar valor automático do salário mínimo" }
                        ) { Text("Usar automático", color = NeonCyan, fontSize = 12.sp) }
                    }
                    TextButton(onClick = { abrir(IndicesEconomicos.urlHistorico(IndicesEconomicos.SLUG_SALARIO_MINIMO)) }) {
                        Text("ver histórico", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            ExtrasIndices(c.extras, locais.bcb?.selicMeta) { novo -> locais.salvar(b.copy(extras = novo)) }
        }
        item {
            Column(Modifier.padding(bottom = 24.dp)) {
                PlanNota(
                    "Fonte geral de reserva: ${IndicesEconomicos.URL_FONTE}. Os links \"ver histórico\" abrem a página do site no navegador. Os valores automáticos " +
                        "vêm de consultas públicas, somente leitura, a api.bcb.gov.br (SGS); nenhum dado seu é enviado. As edições e o cache ficam só neste aparelho " +
                        "(não vão ao backup nem à nuvem)."
                )
            }
        }
    }

    editando?.let { item ->
        DialogoEditarIndice(item, onDismiss = { editando = null }) { serie, comp ->
            locais.salvar(
                b.copy(itens = b.itens.map {
                    if (it.id == item.id) it.copy(serie = serie, competencia = comp, aproximada = false, origem = Origem.MANUAL, parcial = false) else it
                })
            )
            editando = null
        }
    }
    if (editandoSalario) {
        DialogoSalario(c.salarioMinimo.centavos, c.salarioMinimo.competencia, onDismiss = { editandoSalario = false }) { cts, comp ->
            locais.salvar(b.copy(salarioMinimo = IndicesEconomicos.SalarioMinimo(cts, comp, Origem.MANUAL)))
            editandoSalario = false
        }
    }
    if (confirmaRestaurar) {
        AlertDialog(
            onDismissRequest = { confirmaRestaurar = false }, containerColor = DeepSpaceBlue,
            title = { Text("Restaurar padrão?", color = Color.White) },
            text = {
                Text(
                    "Descarta TODAS as edições manuais (índices, salário mínimo, meta da Selic, rotativo e cheque especial). Os valores do Banco Central já " +
                        "salvos no aparelho permanecem e voltam a valer onde você não editou.",
                    color = Color.White.copy(0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = { locais.salvar(IndicesEconomicos.padrao()); confirmaRestaurar = false }) { Text("Restaurar", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { confirmaRestaurar = false }) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
        )
    }
}

private fun hojeIso(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** "2026-10-28" -> "28/10/2026". */
private fun dataBr(iso: String): String = iso.split('-').takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" } ?: iso

/** Selo de origem do valor: BCB, BCB · parcial do mês, manual ou semente (Debit). Sempre texto (a cor nunca é a única pista). */
@Composable
private fun Selo(origem: Origem, parcial: Boolean) {
    val txt = when (origem) {
        Origem.BCB -> if (parcial) "BCB · parcial do mês" else "BCB"
        Origem.MANUAL -> "manual"
        Origem.SEMENTE -> "semente (Debit)"
    }
    Text(
        txt, color = if (origem == Origem.SEMENTE) Color.White.copy(0.65f) else NeonCyan, fontSize = 11.sp,
        modifier = Modifier.background(Color.White.copy(0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "Origem do valor: $txt" }
    )
}

@Composable
private fun CartaoIndice(item: Item, bcbDisponivel: Boolean, onEditar: () -> Unit, onUsarAuto: () -> Unit, onAbrir: (String?) -> Unit) {
    val mensal = item.valorMensalPct
    val anual = IndicesEconomicos.anualDeMensal(IndicesEconomicos.pctParaFracao(mensal))
    val acum = if (item.serie.size > 1) IndicesEconomicos.acumuladoDosUltimos(item.serie, item.serie.size) else null
    val url = IndicesEconomicos.urlHistorico(item.slug)
    PlanCartao {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Selo(item.origem, item.parcial)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${fmtNum(mensal, 4)}% a.m.", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("${fmtPct(anual)} a.a. equiv.", color = Color.White.copy(0.7f), fontSize = 13.sp)
        }
        PlanNota(
            "Competência: " + (if (item.competencia.isBlank()) "—" else IndicesEconomicos.competenciaAnterior(item.competencia, 0)) +
                (if (item.aproximada) " (aproximada)" else "") + (if (item.parcial) " · acumulado parcial do mês" else "")
        )
        if (item.serie.size > 1) {
            val linhas = item.serie.mapIndexed { k, v ->
                val comp = if (item.competencia.isBlank()) "" else IndicesEconomicos.competenciaAnterior(item.competencia, item.serie.size - 1 - k) + ": "
                comp + fmtNum(v, 2)
            }
            PlanNota("Últimos meses (%): " + linhas.joinToString(" · ") + (acum?.let { " · acumulado ${it.mesesUsados}m: ${fmtPct(it.acumulado)}" } ?: ""))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onEditar, modifier = Modifier.semantics { contentDescription = "Editar ${item.nome}" }) {
                Text("Editar", fontSize = 12.sp, color = NeonCyan)
            }
            if (item.origem == Origem.MANUAL && bcbDisponivel) {
                TextButton(onClick = onUsarAuto, modifier = Modifier.semantics { contentDescription = "Usar valor automático de ${item.nome}" }) {
                    Text("Usar automático", color = NeonCyan, fontSize = 12.sp)
                }
            }
            if (url != null) TextButton(onClick = { onAbrir(url) }, modifier = Modifier.semantics { contentDescription = "Ver histórico de ${item.nome} no navegador" }) {
                Text("ver histórico", color = NeonCyan, fontSize = 12.sp)
            }
        }
    }
}

/** Poupança: a taxa real do BCB (série 196) prevalece; a regra da Selic aparece só como "dica calculada". */
@Composable
private fun DicaPoupanca(c: IndicesEconomicos.Conjunto) {
    val poup = IndicesEconomicos.buscar(c, "poupanca")
    val dica = Bcb.dicaPoupanca(c) ?: return
    val selicAnual = (c.extras.selicMetaAnualPct ?: return) / 100
    PlanCartao {
        PlanNota(
            "Poupança: taxa do mês ${fmtNum(poup.valorMensalPct, 4)}% a.m." + (if (poup.origem == Origem.BCB) " (BCB, série 196)" else "") +
                ". Dica calculada pela regra (" +
                (if (selicAnual > 0.085) "Selic acima de 8,5% a.a.: 0,5% a.m. + TR" else "Selic até 8,5% a.a.: 70% da Selic + TR") +
                ", com Selic meta de ${fmtPct(selicAnual)} a.a.): ${fmtPct(dica, 4)} a.m. A taxa real prevalece; a dica serve só de conferência."
        )
    }
}

@Composable
private fun DialogoEditarIndice(item: Item, onDismiss: () -> Unit, onSalvar: (List<Double>, String) -> Unit) {
    var serieTxt by remember(item.id) { mutableStateOf(item.serie.joinToString("; ") { campoNum(it) }) }
    var compTxt by remember(item.id) { mutableStateOf(item.competencia) }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Editar ${item.nome}", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(serieTxt, { serieTxt = it }, "Últimos valores mensais em % (o último é o vigente)", maxChars = 120, apoio = "Separe por ponto e vírgula, do mais antigo ao mais recente. Ex.: 0,07; -0,32; 0,82")
                CampoR58(compTxt, { compTxt = it }, "Competência do último valor (MM/AAAA)", maxChars = 7)
                erro?.let { Text(it, color = CorNegativa, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val partes = serieTxt.split(';').map { lerNum(it) }
                val comp = compTxt.trim()
                when {
                    partes.isEmpty() || partes.size > 24 || partes.any { !it.isFinite() || Math.abs(it) >= 1000 } ->
                        erro = "Informe os valores em % separados por ponto e vírgula (ex.: 0,07; -0,32; 0,82)."
                    comp.isNotEmpty() && !Regex("^\\d{2}/\\d{4}$").matches(comp) -> erro = "Competência no formato MM/AAAA."
                    else -> onSalvar(partes, comp)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun DialogoSalario(centavos: Long, competencia: String, onDismiss: () -> Unit, onSalvar: (Long, String) -> Unit) {
    var valor by remember { mutableStateOf(campoNum(centavos / 100.0, 2)) }
    var comp by remember { mutableStateOf(competencia) }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DeepSpaceBlue,
        title = { Text("Salário mínimo", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoR58(valor, { valor = it }, "Valor (R$)", numerico = true, maxChars = 12)
                CampoR58(comp, { comp = it }, "Vigência (ex.: 2026)", maxChars = 12)
                erro?.let { Text(it, color = CorNegativa, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = lerNum(valor)
                if (!v.isFinite() || v <= 0 || v >= 10_000_000) erro = "Informe um valor maior que zero." else onSalvar(Math.round(v * 100), comp.trim())
            }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue)) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

/**
 * [extras] = EFETIVOS (a meta da Selic pode vir do BCB). Ao salvar, a meta só vira "manual" se o usuário a alterou: se o campo
 * ainda mostra o valor do BCB, grava vazio (continua automático).
 */
@Composable
private fun ExtrasIndices(extras: IndicesEconomicos.Extras, metaBcb: Bcb.SelicMetaBcb?, onSalvar: (IndicesEconomicos.Extras) -> Unit) {
    var selic by remember(extras) { mutableStateOf(extras.selicMetaAnualPct?.let { campoNum(it) }.orEmpty()) }
    var infl by remember(extras) { mutableStateOf(extras.inflacao12mPct?.let { campoNum(it) }.orEmpty()) }
    var rot by remember(extras) { mutableStateOf(campoNum(extras.taxaRotativoMensalPct)) }
    var chq by remember(extras) { mutableStateOf(campoNum(extras.taxaChequeEspecialMensalPct)) }
    var erro by remember { mutableStateOf<String?>(null) }
    val origemSelic = extras.selicMetaOrigem
    val prefixoSelic = when (origemSelic) {
        Origem.BCB -> "Banco Central (série 432). "
        Origem.MANUAL -> "Valor informado por você. "
        else -> ""
    }
    PlanCartao("Outros valores (informe ou use o automático)") {
        CampoR58(
            selic, { selic = it }, "Meta da Selic (% a.a.)", numerico = true, maxChars = 8,
            apoio = prefixoSelic + "Usada na regra da poupança (acima de 8,5% a.a.). Em branco: usa o valor do BCB ou a Selic mensal anualizada."
        )
        if (origemSelic == Origem.MANUAL && metaBcb != null) {
            TextButton(
                onClick = { onSalvar(extras.copy(selicMetaAnualPct = null, selicMetaOrigem = null)) },
                modifier = Modifier.semantics { contentDescription = "Usar valor automático da meta da Selic" }
            ) { Text("Usar automático (${fmtNum(metaBcb.valor, 2)}% a.a.)", color = NeonCyan, fontSize = 12.sp) }
        }
        CampoR58(infl, { infl = it }, "Inflação acumulada em 12 meses (%)", numerico = true, maxChars = 8, apoio = "Opcional; referência para ganho real.")
        CampoR58(rot, { rot = it }, "Juros do rotativo do cartão (% a.m.) — estimativa, edite", numerico = true, maxChars = 8, apoio = "Estimativa de referência; confira a taxa no seu contrato/fatura.")
        CampoR58(chq, { chq = it }, "Juros do cheque especial (% a.m.) — estimativa, edite", numerico = true, maxChars = 8, apoio = "Estimativa de referência; confira a taxa no seu contrato.")
        erro?.let { Text(it, color = CorNegativa, fontSize = 12.sp) }
        Button(onClick = {
            fun opc(t: String): Pair<Boolean, Double?> = if (t.isBlank()) true to null else lerNum(t).let { (it.isFinite() && Math.abs(it) < 1000) to it }
            val (okS, s) = opc(selic)
            val (okI, i) = opc(infl)
            val r = lerNum(rot)
            val q = lerNum(chq)
            if (!okS || !okI || !r.isFinite() || r < 0 || r >= 1000 || !q.isFinite() || q < 0 || q >= 1000) {
                erro = "Confira os números informados (as taxas de rotativo e cheque especial não podem ficar vazias nem negativas)."
            } else {
                erro = null
                // valor ainda igual ao do BCB (não alterado): continua automático (nulo na base)
                val selicManual = if (origemSelic == Origem.BCB && s != null && s == extras.selicMetaAnualPct) null else s
                onSalvar(IndicesEconomicos.Extras(selicManual, i, r, q, if (selicManual != null) Origem.MANUAL else null))
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpaceBlue), modifier = Modifier.fillMaxWidth()) {
            Text("Salvar valores")
        }
    }
}
