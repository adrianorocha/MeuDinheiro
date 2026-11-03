import { cartaoIdDe } from "../finance/calc";
import { fimDoDia } from "../finance/dates";
import { fromCents, toCents } from "../finance/money";
import type { Dataset } from "../finance/types";
import { conferirFatura } from "./fatura-total";
import { lancamentosDoDestino } from "./matching";
import type { Destino, SaldoArquivo, TransacaoBanco } from "./tipos";

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

export function diferencaSaldo(ds: Pick<Dataset, "despesas" | "cartoes">, destino: Destino, saldo: SaldoArquivo, transacoes?: readonly TransacaoBanco[]): DiferencaSaldo {
  // R102 - cartão: o saldo do extrato é o total da(s) fatura(s) do arquivo, pagas ou não (negativo = a pagar), não só o que está em aberto
  const fatura = destino.tipo === "CARTAO" && transacoes ? conferirFatura(ds, destino, transacoes.filter((t) => t.data <= fimDoDia(saldo.data))) : null;
  const saldoApp = fatura ? fromCents(-toCents(fatura.totalApp)) : saldoAppAte(ds, destino, saldo.data);
  return { saldoBanco: saldo.valor, saldoApp, diferenca: fromCents(toCents(saldo.valor) - toCents(saldoApp)) };
}
