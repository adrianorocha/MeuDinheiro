import { cartoesDoGrupo, faturaDaCompra, resumoFatura } from "../finance/calc";
import { fromCents, toCents } from "../finance/money";
import type { Dataset } from "../finance/types";
import { ehPagamentoDeFatura } from "./plano";
import type { Destino, TransacaoBanco } from "./tipos";

/** R102 - uma fatura (ciclo do cartão principal do grupo) tocada pelo extrato. */
export interface FaturaConferida {
  mes: number;
  ano: number;
  /** soma dos lançamentos do ciclo (compras − estornos), pagos ou não */
  total: number;
  /** parte do total ainda não paga */
  emAberto: number;
  itens: number;
}

export interface ConferenciaFatura {
  faturas: FaturaConferida[];
  /** total que o extrato cobra (compras − estornos; a linha de pagamento da fatura fica de fora), positivo = a pagar */
  totalExtrato: number;
  /** soma dos lançamentos do app nas mesmas faturas */
  totalApp: number;
  /** parte de `totalApp` ainda em aberto */
  emAbertoApp: number;
  /** totalExtrato − totalApp (0 = bate) */
  diferenca: number;
  bate: boolean;
}

/**
 * R102 - o total da fatura do extrato × a soma dos lançamentos do app nas mesmas faturas (R18: grupo inteiro, ciclo do principal).
 * O total do app conta lançamentos pagos e em aberto, então não muda ao marcar a fatura como paga (R70) nem ao conciliar:
 * só deixa de bater quando falta/sobra lançamento, ou o valor/data/cartão difere. Puro; devolve null para conta ou extrato vazio.
 */
export function conferirFatura(ds: Pick<Dataset, "despesas" | "cartoes">, destino: Destino, transacoes: readonly Pick<TransacaoBanco, "data" | "valor" | "descricao">[]): ConferenciaFatura | null {
  if (destino.tipo !== "CARTAO") return null;
  const escolhido = ds.cartoes.find((c) => c.id === destino.cartaoId);
  if (!escolhido) return null;
  const grupo = cartoesDoGrupo(escolhido, ds.cartoes);
  const principal = grupo[0];

  let extrato = 0;
  const ciclos = new Map<number, { mes: number; ano: number }>();
  for (const t of transacoes) {
    if (t.valor > 0 && ehPagamentoDeFatura(t.descricao)) continue;
    extrato -= toCents(t.valor);
    const f = faturaDaCompra(principal, t.data);
    ciclos.set(f.ano * 12 + f.mes, f);
  }
  if (ciclos.size === 0) return null;

  let app = 0;
  let aberto = 0;
  const faturas = [...ciclos.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([, f]) => {
      const r = resumoFatura(principal, ds.despesas, f.mes, f.ano, ds.cartoes);
      app += toCents(r.total);
      aberto += toCents(r.emAberto);
      return { mes: f.mes, ano: f.ano, total: r.total, emAberto: r.emAberto, itens: r.itens.length };
    });
  const dif = extrato - app;
  return { faturas, totalExtrato: fromCents(extrato), totalApp: fromCents(app), emAbertoApp: fromCents(aberto), diferenca: fromCents(dif), bate: dif === 0 };
}
