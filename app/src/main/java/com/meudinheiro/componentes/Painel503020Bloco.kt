package com.meudinheiro.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.data.Despesa
import com.meudinheiro.data.Meta
import com.meudinheiro.domain.Dinheiro
import com.meudinheiro.domain.Financas
import com.meudinheiro.domain.FormatoCentavos
import com.meudinheiro.domain.Painel503020
import com.meudinheiro.domain.Painel503020.Grupo
import com.meudinheiro.domain.Painel503020.StatusGrupo
import com.meudinheiro.domain.Painel503020.StatusReserva
import com.meudinheiro.funcoes.UserPreferences
import com.meudinheiro.funcoes.formatarMoedaBR
import com.meudinheiro.ui.theme.NeonCyan
import com.meudinheiro.ui.theme.NeonGreen
import com.meudinheiro.ui.theme.NeonOrange
import com.meudinheiro.ui.theme.NeonRed
import kotlinx.coroutines.launch
import java.util.Locale

private val MESES_ROTULO = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

private fun nomeGrupo(g: Grupo) = when (g) {
    Grupo.NECESSIDADES -> "Necessidades"
    Grupo.DESEJOS -> "Desejos"
    Grupo.POUPANCA -> "Poupança e dívidas"
}

/**
 * R54 — Painel 50-30-20 por categoria (classificação por nome, com ajuste local) e reserva de emergência sobre as
 * despesas essenciais. Os ajustes ficam só neste aparelho (DataStore) e não sincronizam com o portal.
 */
@Composable
fun Painel503020Bloco(despesas: List<Despesa>, saldosContas: List<Double>, metas: List<Meta>, agora: Long, isPrivate: Boolean) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val scope = rememberCoroutineScope()
    val texto by prefs.painel503020Flow.collectAsState(initial = "")
    val config = remember(texto) { Painel503020.desserializar(texto) }
    fun salvar(c: Painel503020.Config) {
        scope.launch { prefs.salvarPainel503020(Painel503020.serializar(c)) }
    }

    var mes by remember { mutableStateOf(Financas.mesDe(agora)) }
    var ano by remember { mutableStateOf(Financas.anoDe(agora)) }
    var ajustando by remember { mutableStateOf(false) }

    val painel = remember(despesas, mes, ano, config) { Painel503020.painel(despesas, mes, ano, config) }
    val reserva = remember(despesas, saldosContas, metas, agora, config) {
        Painel503020.reservaEssenciais(
            despesas, saldosContas.sumOf { Dinheiro.centavos(it) }, Painel503020.metasDeReservaCentavos(metas), agora, config
        )
    }
    fun moeda(c: Long) = formatarMoedaBR(Dinheiro.reais(c), isPrivate)
    fun mascarar(t: String) = if (isPrivate) t.replace(Regex("-?R\\$ [\\d.]+,\\d{2}"), "••••••") else t

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Secao("PAINEL 50 / 30 / 20 POR CATEGORIA") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (mes == 1) { mes = 12; ano -= 1 } else mes -= 1
                }) { Text("‹ Anterior", color = NeonCyan, fontSize = 12.sp) }
                Text(
                    "${MESES_ROTULO[mes - 1]} de $ano", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                TextButton(onClick = {
                    if (mes == 12) { mes = 1; ano += 1 } else mes += 1
                }) { Text("Próximo ›", color = NeonCyan, fontSize = 12.sp) }
            }
            if (painel.receitaCentavos <= 0) {
                Linha("Sem receita realizada neste mês: os percentuais não podem ser calculados.", Color.White.copy(0.6f))
            } else {
                Linha("Sobre a receita realizada de ${moeda(painel.receitaCentavos)}. Fatura de cartão paga não é contada de novo: as compras já entram nas categorias.")
            }
            listOf(painel.necessidades, painel.desejos, painel.poupanca).forEach { g ->
                val cor = when (g.status) { StatusGrupo.OK -> NeonGreen; StatusGrupo.SEM_RECEITA -> Color.White.copy(0.6f); else -> NeonOrange }
                val situacao = when (g.status) {
                    StatusGrupo.OK -> "dentro da meta"
                    StatusGrupo.ACIMA -> "acima da meta"
                    StatusGrupo.ABAIXO -> "abaixo da meta"
                    StatusGrupo.SEM_RECEITA -> "sem receita"
                }
                val alvo = (if (g.grupo == Grupo.POUPANCA) "pelo menos " else "até ") + "${g.metaPercentual}%"
                val pct = g.percentualDecimos?.let { FormatoCentavos.decimos(it) + "%" } ?: "—"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("${nomeGrupo(g.grupo)} ($alvo)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${moeda(g.totalCentavos)} · $pct", color = cor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                val razao = g.percentualDecimos?.let { it / 1000.0 / (if (g.metaPercentual > 0) g.metaPercentual / 100.0 else 1.0) } ?: 0.0
                BarraAcessivel(razao.toFloat(), cor, "${nomeGrupo(g.grupo)}: ${if (g.percentualDecimos == null) "sem receita" else pct + " da receita"}, $situacao")
                // texto além da cor: situação por extenso e as maiores categorias do grupo
                Linha("Situação: $situacao." + if (g.categorias.isNotEmpty()) " " + g.categorias.take(4).joinToString(" · ") { "${it.nome} ${moeda(it.centavos)}" } else "", cor)
            }
            painel.dicas.forEach { Linha("• " + mascarar(it), NeonCyan) }

            if (painel.naoClassificadas.isNotEmpty()) {
                Linha("Categorias sem classificação (toque para escolher o grupo):", NeonOrange)
                painel.naoClassificadas.forEach { c ->
                    SeletorGrupo(c.nome + " (${moeda(c.centavos)})", null) { g ->
                        salvar(config.copy(overrides = config.overrides + (Painel503020.chaveCategoria(c.nome) to g)))
                    }
                }
            }

            TextButton(onClick = { ajustando = !ajustando }) {
                Text(if (ajustando) "Fechar ajustes" else "Ajustar metas e classificação", color = NeonCyan, fontSize = 12.sp)
            }
            if (ajustando) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoInteiro("Necessid. (% máx.)", config.metas.necessidades, Modifier.weight(1f)) { v ->
                        salvar(Painel503020.normalizarConfig(v, config.metas.desejos, config.metas.poupanca, config.mesesReserva, config.overrides))
                    }
                    CampoInteiro("Desejos (% máx.)", config.metas.desejos, Modifier.weight(1f)) { v ->
                        salvar(Painel503020.normalizarConfig(config.metas.necessidades, v, config.metas.poupanca, config.mesesReserva, config.overrides))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoInteiro("Poupança (% mín.)", config.metas.poupanca, Modifier.weight(1f)) { v ->
                        salvar(Painel503020.normalizarConfig(config.metas.necessidades, config.metas.desejos, v, config.mesesReserva, config.overrides))
                    }
                    CampoInteiro("Reserva (meses)", config.mesesReserva, Modifier.weight(1f)) { v ->
                        salvar(Painel503020.normalizarConfig(config.metas.necessidades, config.metas.desejos, config.metas.poupanca, v, config.overrides))
                    }
                }
                Linha("Classificação das categorias do mês:", Color.White)
                val todas = painel.necessidades.categorias + painel.desejos.categorias +
                    painel.poupanca.categorias.filter { !it.nome.startsWith("Metas (") } + painel.naoClassificadas
                todas.forEach { c ->
                    SeletorGrupo(c.nome + " (${moeda(c.centavos)})", config.overrides[Painel503020.chaveCategoria(c.nome)]) { g ->
                        salvar(config.copy(overrides = config.overrides + (Painel503020.chaveCategoria(c.nome) to g)))
                    }
                }
                TextButton(onClick = { salvar(Painel503020.CONFIG_PADRAO) }) {
                    Text("Restaurar padrão (50-30-20, 6 meses, automático)", color = NeonOrange, fontSize = 12.sp)
                }
                Linha("Estes ajustes ficam só neste aparelho: não sincronizam com o portal nem com outros aparelhos nesta versão.")
            }
        }

        Secao("RESERVA DE EMERGÊNCIA (DESPESAS ESSENCIAIS)") {
            val media = reserva.mediaEssenciaisCentavos
            val meses = reserva.mesesCobertos
            val meta = reserva.metaCentavos
            val falta = reserva.faltanteCentavos
            if (reserva.status == StatusReserva.SEM_DADOS || media == null || meses == null || meta == null || falta == null) {
                Linha("Registre despesas essenciais (moradia, mercado, saúde, transporte…) nos 3 meses anteriores para calcular a cobertura.", Color.White.copy(0.6f))
            } else {
                val cor = when (reserva.status) { StatusReserva.OK -> NeonGreen; StatusReserva.ATENCAO -> NeonOrange; else -> NeonRed }
                val rotulo = when (reserva.status) { StatusReserva.OK -> "Reserva completa"; StatusReserva.ATENCAO -> "Atenção"; else -> "Crítico" }
                Text("%.1f meses · %s".format(Locale("pt", "BR"), meses, rotulo), color = cor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                BarraAcessivel((meses / reserva.mesesMeta).toFloat(), cor, "Cobertura da reserva: %.1f de %d meses, %s".format(Locale("pt", "BR"), meses, reserva.mesesMeta, rotulo))
                Linha("Contas + metas de reserva ${moeda(reserva.liquidezCentavos)} ÷ essenciais ${moeda(media)}/mês (média de 3 meses).")
                Linha("Meta de ${reserva.mesesMeta} meses: ${moeda(meta)}." + if (falta > 0) " Faltam ${moeda(falta)}." else " Meta atingida.", if (falta > 0) NeonOrange else NeonGreen)
                Linha("Conta o saldo das contas e as metas cujo nome tem \"reserva\" ou \"emergência\". Investimentos não entram aqui.")
            }
        }
    }
}

@Composable
private fun Linha(t: String, cor: Color = Color.White.copy(0.7f)) {
    Text(t, color = cor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BarraAcessivel(fracao: Float, cor: Color, descricao: String) {
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp)
            .semantics { contentDescription = descricao }
            .background(Color.White.copy(0.08f), RoundedCornerShape(4.dp))
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fracao.coerceIn(0.01f, 1f)).background(cor, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun SeletorGrupo(rotulo: String, atual: Grupo?, onEscolher: (Grupo) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(rotulo, color = Color.White, fontSize = 12.sp)
        Row {
            Grupo.entries.forEach { g ->
                val sel = g == atual
                TextButton(onClick = { onEscolher(g) }) {
                    Text(
                        (if (sel) "✓ " else "") + when (g) { Grupo.NECESSIDADES -> "Necess."; Grupo.DESEJOS -> "Desejos"; Grupo.POUPANCA -> "Poupança" },
                        color = if (sel) NeonGreen else NeonCyan, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun CampoInteiro(rotulo: String, valor: Int, modifier: Modifier, onValor: (Int) -> Unit) {
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    OutlinedTextField(
        value = texto, onValueChange = { t -> texto = t.filter(Char::isDigit).take(3); texto.toIntOrNull()?.let(onValor) },
        singleLine = true, label = { Text(rotulo, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = NeonCyan,
            unfocusedBorderColor = Color.White.copy(0.2f), focusedLabelColor = NeonCyan, unfocusedLabelColor = Color.White.copy(0.5f)
        )
    )
}
