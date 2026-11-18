package com.meudinheiro.domain

/**
 * R98 — comparativo de rendimento: poupança × produto atrelado ao CDI (IR regressivo) × dinheiro parado.
 * Paridade com `portal/src/lib/finance/rendimento.ts` (R97): mesmas fórmulas, mesmas convenções e mesmos vetores de teste.
 *
 * É uma SIMULAÇÃO com premissas simples (não é recomendação de investimento). Taxas como frações decimais (0,0108 = 1,08% a.m.);
 * entradas em reais (Double) e saídas em CENTAVOS (Long). Convenções:
 *  - valor inicial em t=0; aporte mensal no FIM de cada mês t=1..N (o aporte do mês N não rende); aplicado = inicial + N × aporte;
 *  - mês = 30 dias para o prazo do IR (lote aplicado em t e resgatado em N tem 30 × (N − t) dias);
 *  - produto atrelado ao CDI: taxa mensal bruta = (% do CDI) × CDI mensal (linear, simplificação documentada);
 *  - IR regressivo por LOTE (inicial e cada aporte têm prazo próprio) sobre o rendimento; sem come-cotas, IOF ou custos;
 *  - poupança: isenta de IR, taxa mensal informada (padrão da semente, 0,6624%); rende todo mês (sem "aniversário");
 *  - cálculo em ponto flutuante; arredondamento para centavos só na saída (meio para cima);
 *  - ganho real = saldo final − (aplicado corrigido pela inflação até N).
 */
object Rendimentos {

    /** Alíquota regressiva do IR sobre o rendimento: ≤180 dias 22,5%; 181–360 20%; 361–720 17,5%; >720 15%. */
    fun aliquotaIR(dias: Int): Double = when {
        dias <= 180 -> 0.225
        dias <= 360 -> 0.2
        dias <= 720 -> 0.175
        else -> 0.15
    }

    /**
     * Regra da poupança: Selic anual > 8,5% => 0,5% a.m. + TR (composto); senão 70% da Selic anual convertida em mensal + TR.
     * [selicAnual] e [trMensal] em fração.
     */
    fun poupancaPelaRegra(selicAnual: Double, trMensal: Double): Double {
        val base = if (selicAnual > 0.085) 0.005 else Math.pow(1 + 0.7 * selicAnual, 1.0 / 12) - 1
        return (1 + base) * (1 + trMensal) - 1
    }

    /** Taxa mensal bruta do produto: [pctCdi] (1 = 100%) × CDI mensal. */
    fun taxaProdutoMensal(cdiMensal: Double, pctCdi: Double): Double = cdiMensal * pctCdi

    /** Fator líquido de IR de 1 real aplicado por [meses] meses à taxa mensal bruta: 1 + (F−1)(1−alíq(30×meses)). */
    fun fatorLiquido(taxa: Double, meses: Int): Double {
        val bruto = Math.pow(1 + taxa, meses.toDouble())
        return 1 + (bruto - 1) * (1 - aliquotaIR(30 * meses))
    }

    /** Taxa mensal líquida equivalente para um prazo de [meses] meses (≥ 1). */
    fun taxaLiquidaMensal(taxa: Double, meses: Int): Double {
        val m = maxOf(1, meses)
        return Math.pow(fatorLiquido(taxa, m), 1.0 / m) - 1
    }

    data class Entrada(
        val valorInicial: Double,
        val aporteMensal: Double,
        val meses: Int,
        val cdiMensal: Double,
        /** 1 = 100% do CDI. */
        val pctCdi: Double,
        val poupancaMensal: Double,
        val inflacaoMensal: Double
    )

    /** Ponto da evolução (centavos). [corrigidoInflacao] = aplicado corrigido pela inflação (referência de poder de compra). */
    data class Ponto(val mes: Int, val poupanca: Long, val cdiLiquido: Long, val parado: Long, val corrigidoInflacao: Long)

    data class Produto(val saldoFinal: Long, val ganhoLiquido: Long, val ganhoReal: Long, val ir: Long)

    data class Resultado(
        val totalAplicado: Long,
        val poupanca: Produto,
        val cdi: Produto,
        val parado: Produto,
        val aplicadoCorrigido: Long,
        val serie: List<Ponto>,
        val aliquotaInicial: Double
    )

    private fun c(x: Double): Long = Math.round(x * 100 + 1e-7 * Math.signum(x))

    private fun saldoNoMes(e: Entrada, taxaBruta: Double, t: Int): Pair<Double, Double> {
        var saldo = 0.0
        var ir = 0.0
        fun lote(valor: Double, deposito: Int) {
            val prazo = t - deposito
            val bruto = valor * Math.pow(1 + taxaBruta, prazo.toDouble())
            val imposto = (bruto - valor) * aliquotaIR(30 * prazo)
            saldo += bruto - imposto
            ir += imposto
        }
        if (e.valorInicial > 0) lote(e.valorInicial, 0)
        if (e.aporteMensal > 0) for (k in 1..t) lote(e.aporteMensal, k)
        return saldo to ir
    }

    private fun saldoPoupanca(e: Entrada, t: Int): Double {
        var s = e.valorInicial
        for (k in 1..t) s = s * (1 + e.poupancaMensal) + e.aporteMensal
        return s
    }

    private fun aplicadoNoMes(e: Entrada, t: Int): Double = e.valorInicial + e.aporteMensal * t

    private fun aplicadoCorrigido(e: Entrada, t: Int): Double {
        var v = e.valorInicial * Math.pow(1 + e.inflacaoMensal, t.toDouble())
        for (k in 1..t) v += e.aporteMensal * Math.pow(1 + e.inflacaoMensal, (t - k).toDouble())
        return v
    }

    /** Mensagem de erro das entradas; `null` = válidas. */
    fun validar(e: Entrada): String? = when {
        e.meses < 1 || e.meses > 600 -> "Informe um prazo de 1 a 600 meses."
        !(e.valorInicial >= 0) || !(e.aporteMensal >= 0) -> "Valor inicial e aporte não podem ser negativos."
        e.valorInicial == 0.0 && e.aporteMensal == 0.0 -> "Informe um valor inicial ou um aporte mensal."
        !e.cdiMensal.isFinite() || !e.poupancaMensal.isFinite() || !e.inflacaoMensal.isFinite() || !(e.pctCdi >= 0) -> "Confira as taxas informadas."
        e.cdiMensal <= -1 || e.poupancaMensal <= -1 || e.inflacaoMensal <= -1 -> "Taxa mensal inválida."
        else -> null
    }

    fun comparar(e: Entrada): Resultado {
        val taxa = taxaProdutoMensal(e.cdiMensal, e.pctCdi)
        val n = e.meses
        val serie = ArrayList<Ponto>(n + 1)
        for (t in 0..n) {
            serie += Ponto(t, c(saldoPoupanca(e, t)), c(saldoNoMes(e, taxa, t).first), c(aplicadoNoMes(e, t)), c(aplicadoCorrigido(e, t)))
        }
        val aplicado = c(aplicadoNoMes(e, n))
        val corrigido = c(aplicadoCorrigido(e, n))
        val fim = serie[n]
        val irFinal = c(saldoNoMes(e, taxa, n).second)
        fun produto(saldo: Long, ir: Long) = Produto(saldo, saldo - aplicado, saldo - corrigido, ir)
        return Resultado(
            aplicado, produto(fim.poupanca, 0), produto(fim.cdiLiquido, irFinal), produto(fim.parado, 0),
            corrigido, serie, aliquotaIR(30 * n)
        )
    }

    /**
     * Aporte mensal necessário (CENTAVOS) para chegar a [objetivo] (reais) em [meses] meses, com saldo atual [guardado] (reais) e
     * taxa mensal líquida [rn] (aportes no fim do mês). 0 se só o rendimento já basta. `rn` = 0 => (objetivo − guardado) / meses.
     */
    fun aporteParaMeta(objetivo: Double, guardado: Double, meses: Int, rn: Double): Long {
        val m = maxOf(1, meses)
        val falta = objetivo - guardado * Math.pow(1 + rn, m.toDouble())
        if (falta <= 0) return 0
        val fator = if (Math.abs(rn) < 1e-12) m.toDouble() else (Math.pow(1 + rn, m.toDouble()) - 1) / rn
        return Math.ceil(falta / fator * 100 - 1e-7).toLong()
    }
}
