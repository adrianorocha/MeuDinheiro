/*package com.meudinheiro.ui.screens // Ajuste para o seu pacote de telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meudinheiro.componentes.ResumoGeralCard
import com.meudinheiro.data.PieChartData
import com.meudinheiro.funcoes.CompactCategoryGrid
import com.meudinheiro.funcoes.HorizontalMonthSelector
import com.meudinheiro.funcoes.obterCorDaCategoria
import com.meudinheiro.viewModel.DespesasViewModel
import java.util.Calendar

@Composable
fun DashboardScreen(viewModel: DespesasViewModel) {
    // 1. Controle de Tempo (Mês e Ano)
    val calendar = Calendar.getInstance()
    var mesSelecionado by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    val anoAtual = calendar.get(Calendar.YEAR)

    // 2. Coleta de Dados do Banco de Dados (Reativo)
    // Supondo que você tenha essas funções no ViewModel
    val despesasDoMes by viewModel.getDespesasPorMes(mesSelecionado, anoAtual).collectAsState(initial = emptyList())
    val despesaAnterior by viewModel.getDespesaMesAnterior(mesSelecionado, anoAtual).collectAsState(initial = 0.0)

    // Status do App
    val isPrivate by viewModel.isPrivateMode.collectAsState(initial = false)
    val isLoading by viewModel.isLoading.collectAsState(initial = false) // Para o Shimmer effect

    // 3. Cálculos Dinâmicos
    val despesaTotal = despesasDoMes.sumOf { it.valor }

    // Transformação para o Gráfico de Pizza e Grade
    val dadosParaGrafico = remember(despesasDoMes) {
        despesasDoMes.groupBy { it.categoria }.map { (categoria, lista) ->
            PieChartData(
                categoria = categoria,
                valor = lista.sumOf { it.valor },
                cor = obterCorDaCategoria(categoria)
            )
        }
    }

    // 4. Estrutura Visual da Tela
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Fundo dark profundo padrão Blu Macaw Lab's
    ) {
        // --- HEADER DO USUÁRIO ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Olá, Adriano",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Seu resumo financeiro",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // Botão de Privacidade (Olhinho)
            IconButton(onClick = { viewModel.togglePrivateMode() }) {
                // Substitua pelo seu ícone de olho aberto/fechado
                Text(if (isPrivate) "🙈" else "👁️", fontSize = 24.sp)
            }
        }

        // --- SELETOR DE MESES ---
        HorizontalMonthSelector(
            selectedMonth = mesSelecionado,
            onMonthSelected = { novoMes ->
                mesSelecionado = novoMes
                // Opcional: viewModel.carregarDadosDoMes(novoMes)
            }
        )

        // --- ÁREA ROLÁVEL (Cards e Gráficos) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // Espaço no final para não esconder atrás da navegação
        ) {

            // 1. O CARD PRINCIPAL NEON
            ResumoGeralCard(
                receitaTotal = 7500.0, // Substitua pela sua variável de receita
                despesaTotal = despesaTotal,
                despesaMesAnterior = despesaAnterior,
                metasTotal = 1500.0,   // Substitua pela sua variável de metas
                isPrivate = isPrivate,
                dadosGrafico = dadosParaGrafico
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. A GRADE COMPACTA DE CATEGORIAS
            if (dadosParaGrafico.isNotEmpty() && !isLoading) {
                CompactCategoryGrid(
                    dados = dadosParaGrafico,
                    isPrivate = isPrivate
                )
            } else if (!isLoading) {
                // Estado Vazio (Empty State)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum gasto registrado em ${obterNomeDoMes(mesSelecionado)}.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. ESPAÇO PARA FUTUROS CARDS (Ex: Nubank, Objetivos)
            // CartaoNubank(saldo = 5400.0, isPrivate = isPrivate)
        }
    }
}

// Função auxiliar simples para o Empty State
fun obterNomeDoMes(mes: Int): String {
    val meses = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    return if (mes in 1..12) meses[mes - 1] else ""
}
*/
