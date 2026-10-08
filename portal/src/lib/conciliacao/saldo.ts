import { cartaoIdDe } from "../finance/calc";
import { fimDoDia } from "../finance/dates";
import { fromCents, toCents } from "../finance/money";
import type { Dataset } from "../finance/types";
import { lancamentosDoDestino } from "./matching";
import type { Destino, SaldoArquivo } from "./tipos";

/**
 * R36 - saldo do app até a data `ate` (inclusive).
 * Conta: R3 (só pagos, sem cartão) com data ≤ `ate`.
 * Cartão: valor em aberto do grupo (compras − estornos não pagos) com sinal negativo, como nos extratos de cartão.
 */
export function saldoAppAte(ds: Pick<Dataset, "despesas" | "cartoes">, destino: Destino, ate: number): number {
  const limite = fimDoDia(ate);
  let cents = 0;
  for (const d of lancamentosDoDestino(ds, destino)) {
    if (d.data > limite) continue;
    const c = toCents(d.valor);
    if (destino.tipo === "CONTA") {
      if (d.pago && cartaoIdDe(d) === null) cents += d.tipo === "CREDITO" ? c : -c;
    } else if (!d.pago) cents += d.tipo === "CREDITO" ? c : -c;
  }
  return fromCents(cents);
}

export interface DiferencaSaldo {
  saldoBanco: number;
  saldoApp: number;
  /** saldoBanco − saldoApp */
  diferenca: number;
}

export function diferencaSaldo(ds: Pick<Dataset, "despesas" | "cartoes">, destino: Destino, saldo: SaldoArquivo): DiferencaSaldo {
  const saldoApp = saldoAppAte(ds, destino, saldo.data);
  return { saldoBanco: saldo.valor, saldoApp, diferenca: fromCents(toCents(saldo.valor) - toCents(saldoApp)) };
}
