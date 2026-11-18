/**
 * R97 - mini-calculadora do custo de ficar no rotativo do cartão / cheque especial.
 * Juros compostos mensais sobre o saldo em aberto, sem multa/IOF/encargos (simplificação documentada).
 * Taxa como fração mensal; valores em reais com arredondamento para centavos só na saída.
 */
export interface CustoAtraso {
  meses: number;
  /** Juros acumulados no período (reais). */
  juros: number;
  /** Saldo devido no fim do período (reais). */
  total: number;
}

const r2 = (x: number): number => Math.round(x * 100 + 1e-7 * Math.sign(x)) / 100;

/** juros = saldo × ((1 + i)^meses − 1). Entradas inválidas => null. */
export function custoDoAtraso(saldo: number, taxaMensal: number, meses: number): CustoAtraso | null {
  if (!(saldo > 0) || !Number.isFinite(taxaMensal) || taxaMensal < 0 || !Number.isInteger(meses) || meses < 0) return null;
  const total = saldo * Math.pow(1 + taxaMensal, meses);
  return { meses, juros: r2(total - saldo), total: r2(total) };
}

/** Custos para 1 e 3 meses (e o que sobra em taxa anual equivalente). */
export function custoRotativo(saldo: number, taxaMensal: number): { m1: CustoAtraso; m3: CustoAtraso; taxaAnual: number } | null {
  const m1 = custoDoAtraso(saldo, taxaMensal, 1);
  const m3 = custoDoAtraso(saldo, taxaMensal, 3);
  if (!m1 || !m3) return null;
  return { m1, m3, taxaAnual: Math.pow(1 + taxaMensal, 12) - 1 };
}
