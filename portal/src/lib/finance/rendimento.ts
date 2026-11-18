/**
 * R97 - comparativo de rendimento: poupança × produto atrelado ao CDI (IR regressivo) × dinheiro parado.
 *
 * É uma SIMULAÇÃO com premissas simples (não é recomendação de investimento). Taxas como frações decimais (0,0108 = 1,08% a.m.).
 * Convenções (iguais no app):
 *  - Valor inicial em t=0; aporte mensal no FIM de cada mês t=1..N (o aporte do mês N não rende); total aplicado = inicial + N×aporte.
 *  - Mês = 30 dias para o prazo do IR (lote aplicado em t e resgatado em N tem 30×(N−t) dias).
 *  - Produto atrelado ao CDI: taxa mensal bruta = (% do CDI) × CDI mensal (linear, simplificação documentada).
 *  - IR regressivo por LOTE (inicial e cada aporte têm prazo próprio) sobre o rendimento; sem come-cotas/IOF/custos.
 *  - Poupança: isenta de IR, taxa mensal informada (padrão da semente, 0,6624%); sem "aniversário" (rende todo mês).
 *  - Cálculo em ponto flutuante; arredondamento para centavos só na saída (meio para cima).
 *  - Ganho real = saldo final − (aplicado corrigido pela inflação até N), i.e. quanto sobrou além de manter o poder de compra.
 */

/** Alíquota regressiva do IR sobre o rendimento por prazo em dias: ≤180 22,5%; 181–360 20%; 361–720 17,5%; >720 15%. */
export function aliquotaIR(dias: number): number {
  if (dias <= 180) return 0.225;
  if (dias <= 360) return 0.2;
  if (dias <= 720) return 0.175;
  return 0.15;
}

/**
 * Regra (padrão) da poupança: Selic anual > 8,5% => 0,5% a.m. + TR (composto); senão 70% da Selic anual convertida em mensal + TR.
 * `selicAnual` e `trMensal` em fração.
 */
export function poupancaPelaRegra(selicAnual: number, trMensal: number): number {
  const base = selicAnual > 0.085 ? 0.005 : Math.pow(1 + 0.7 * selicAnual, 1 / 12) - 1;
  return (1 + base) * (1 + trMensal) - 1;
}

/** Taxa mensal bruta do produto: `pctCdi` (1 = 100%) × CDI mensal. */
export function taxaProdutoMensal(cdiMensal: number, pctCdi: number): number {
  return cdiMensal * pctCdi;
}

/** Fator líquido de IR de 1 real aplicado por `meses` meses à taxa mensal bruta `taxa`: 1 + (F−1)(1−alíq(30×meses)). */
export function fatorLiquido(taxa: number, meses: number): number {
  const bruto = Math.pow(1 + taxa, meses);
  return 1 + (bruto - 1) * (1 - aliquotaIR(30 * meses));
}

/** Taxa mensal líquida equivalente para um prazo de `meses` meses (meses ≥ 1). */
export function taxaLiquidaMensal(taxa: number, meses: number): number {
  const m = Math.max(1, meses);
  return Math.pow(fatorLiquido(taxa, m), 1 / m) - 1;
}

export interface EntradaRendimento {
  /** Reais. */
  valorInicial: number;
  aporteMensal: number;
  meses: number;
  cdiMensal: number;
  /** 1 = 100% do CDI. */
  pctCdi: number;
  poupancaMensal: number;
  inflacaoMensal: number;
}

export interface PontoEvolucao {
  mes: number;
  /** Centavos. */
  poupanca: number;
  cdiLiquido: number;
  parado: number;
  /** Total aplicado corrigido pela inflação (referência de poder de compra). */
  corrigidoInflacao: number;
}

export interface ResultadoProduto {
  /** Saldo líquido final (centavos). */
  saldoFinal: number;
  ganhoLiquido: number;
  ganhoReal: number;
  /** IR total pago no resgate (centavos); 0 na poupança e no dinheiro parado. */
  ir: number;
}

export interface ResultadoRendimento {
  totalAplicado: number;
  poupanca: ResultadoProduto;
  cdi: ResultadoProduto;
  parado: ResultadoProduto;
  aplicadoCorrigido: number;
  serie: PontoEvolucao[];
  /** Alíquota do lote inicial no resgate (referência). */
  aliquotaInicial: number;
}

const c = (x: number): number => Math.round(x * 100 + 1e-7 * Math.sign(x));

/** Saldo líquido (reais, float) e IR de um resgate no mês `t` dos lotes aplicados até `t`. */
function saldoNoMes(e: EntradaRendimento, taxaBruta: number, t: number): { saldo: number; ir: number } {
  let saldo = 0;
  let ir = 0;
  const lote = (valor: number, deposito: number) => {
    const prazo = t - deposito;
    const bruto = valor * Math.pow(1 + taxaBruta, prazo);
    const imposto = (bruto - valor) * aliquotaIR(30 * prazo);
    saldo += bruto - imposto;
    ir += imposto;
  };
  if (e.valorInicial > 0) lote(e.valorInicial, 0);
  if (e.aporteMensal > 0) for (let k = 1; k <= t; k++) lote(e.aporteMensal, k);
  return { saldo, ir };
}

function saldoPoupanca(e: EntradaRendimento, t: number): number {
  let s = e.valorInicial;
  for (let k = 1; k <= t; k++) s = s * (1 + e.poupancaMensal) + e.aporteMensal;
  return s;
}

function aplicadoNoMes(e: EntradaRendimento, t: number): number {
  return e.valorInicial + e.aporteMensal * t;
}

function aplicadoCorrigido(e: EntradaRendimento, t: number): number {
  let v = e.valorInicial * Math.pow(1 + e.inflacaoMensal, t);
  for (let k = 1; k <= t; k++) v += e.aporteMensal * Math.pow(1 + e.inflacaoMensal, t - k);
  return v;
}

/** Valida as entradas; devolve mensagem de erro ou null. */
export function validarRendimento(e: EntradaRendimento): string | null {
  if (!Number.isInteger(e.meses) || e.meses < 1 || e.meses > 600) return "Informe um prazo de 1 a 600 meses.";
  if (!(e.valorInicial >= 0) || !(e.aporteMensal >= 0)) return "Valor inicial e aporte não podem ser negativos.";
  if (e.valorInicial === 0 && e.aporteMensal === 0) return "Informe um valor inicial ou um aporte mensal.";
  if (!Number.isFinite(e.cdiMensal) || !Number.isFinite(e.poupancaMensal) || !Number.isFinite(e.inflacaoMensal) || !(e.pctCdi >= 0)) return "Confira as taxas informadas.";
  if (e.cdiMensal <= -1 || e.poupancaMensal <= -1 || e.inflacaoMensal <= -1) return "Taxa mensal inválida.";
  return null;
}

export function compararRendimento(e: EntradaRendimento): ResultadoRendimento {
  const taxa = taxaProdutoMensal(e.cdiMensal, e.pctCdi);
  const n = e.meses;
  const serie: PontoEvolucao[] = [];
  for (let t = 0; t <= n; t++) {
    serie.push({
      mes: t,
      poupanca: c(saldoPoupanca(e, t)),
      cdiLiquido: c(saldoNoMes(e, taxa, t).saldo),
      parado: c(aplicadoNoMes(e, t)),
      corrigidoInflacao: c(aplicadoCorrigido(e, t)),
    });
  }
  const aplicado = c(aplicadoNoMes(e, n));
  const corrigido = c(aplicadoCorrigido(e, n));
  const fim = serie[n];
  const irFinal = c(saldoNoMes(e, taxa, n).ir);
  const produto = (saldo: number, ir: number): ResultadoProduto => ({ saldoFinal: saldo, ganhoLiquido: saldo - aplicado, ganhoReal: saldo - corrigido, ir });
  return {
    totalAplicado: aplicado,
    poupanca: produto(fim.poupanca, 0),
    cdi: produto(fim.cdiLiquido, irFinal),
    parado: produto(fim.parado, 0),
    aplicadoCorrigido: corrigido,
    serie,
    aliquotaInicial: aliquotaIR(30 * n),
  };
}

/**
 * Aporte mensal necessário para chegar a `objetivo` em `meses` meses, com saldo atual `guardado` e taxa mensal líquida `rn`
 * (aportes no fim do mês). Retorna centavos; 0 se só o rendimento já basta. `rn` = 0 => (objetivo − guardado) / meses.
 */
export function aporteParaMeta(objetivo: number, guardado: number, meses: number, rn: number): number {
  const m = Math.max(1, Math.trunc(meses));
  const falta = objetivo - guardado * Math.pow(1 + rn, m);
  if (falta <= 0) return 0;
  const fator = Math.abs(rn) < 1e-12 ? m : (Math.pow(1 + rn, m) - 1) / rn;
  return Math.ceil(falta / fator * 100 - 1e-7);
}
