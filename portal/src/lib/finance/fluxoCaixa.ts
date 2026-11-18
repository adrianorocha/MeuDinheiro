/**
 * R97 - projeção de fluxo de caixa de 6 a 12 meses (a partir do mês atual, inclusive) e "quanto cortar por mês".
 *
 * Tudo em CENTAVOS inteiros, calculado sobre os dados existentes (nada é gravado). Componentes:
 *  1) Saldo inicial = saldo atual das contas (`saldoCentavos`, R3).
 *  2) Eventos conhecidos = `eventosPrevistos` (R52): pendências (inclui parcelas futuras ainda não pagas), faturas de cartão
 *     lançadas (no vencimento) e recorrências (`despesasFixas`) ainda não lançadas, até o fim do horizonte. Pendência/fatura
 *     vencida entra no mês atual.
 *  3) Despesa variável estimada: média dos 3 meses COMPLETOS anteriores de débitos NORMAL, sem recorrências lançadas
 *     (`grupoId` "fixa:") nem parcelas ("parc:"), líquida de estornos de cartão; cada mês antigo é corrigido pela inflação
 *     mensal informada até o mês atual ((1+π)^k, k = meses atrás) e a média projetada para o mês j por (1+π)^j.
 *     No mês atual conta só o que falta: max(0, estimativa − já gasto variável do mês até agora).
 *  4) Resultado do mês = entradas conhecidas − saídas conhecidas − variável; saldo acumulado mês a mês.
 *  5) Corte necessário por mês: menor c (centavos) tal que saldo_j + c×j ≥ 0 para todo j=1..N, ou seja
 *     c = max_j ceil(−saldo_j / j) entre os meses com saldo negativo (0 se nenhum).
 * Limitações: ignora receita variável não cadastrada, 13º/imprevistos e atraso entre compra no cartão e pagamento da fatura.
 */
import { cartaoIdDe } from "./calc";
import { inicioDoMes, fimDoMes, normalizaMes, mesAnoDe } from "./dates";
import { toCents } from "./money";
import { dataDeSerial, eventosPrevistos, saldoCentavos, ultimoDiaSerial } from "./projecao";
import type { Dataset, Despesa } from "./types";

export const MESES_MIN = 6;
export const MESES_MAX = 12;

export interface MesFluxo {
  /** 0 = mês atual. */
  indice: number;
  mes: number;
  ano: number;
  /** Centavos (todos). */
  entradas: number;
  saidas: number;
  variavel: number;
  resultado: number;
  saldoFinal: number;
  negativo: boolean;
}

export interface ResultadoFluxo {
  saldoInicial: number;
  meses: MesFluxo[];
  /** Média mensal variável já corrigida para preços do mês atual (centavos). */
  variavelBase: number;
  /** Quantos meses completos tinham dados (0..3) — 0 = sem base para estimar. */
  mesesComDados: number;
  /** Índice do primeiro mês com saldo negativo; null = nenhum. */
  primeiroNegativo: number | null;
  /** Centavos por mês necessários para fechar o déficit (0 se não há). */
  cortePorMes: number;
}

/** Débito NORMAL "variável": não vem de recorrência lançada nem de parcelamento. */
export function ehVariavel(d: Despesa): boolean {
  if (d.natureza !== "NORMAL") return false;
  const g = d.grupoId ?? "";
  return !(g.startsWith("fixa:") || g.startsWith("parc:"));
}

/** Gasto variável líquido (centavos) de cada mês, dado o filtro de data. */
function somaVariavel(despesas: readonly Despesa[], ini: number, fim: number): number {
  let s = 0;
  for (const d of despesas) {
    if (d.data < ini || d.data > fim || !ehVariavel(d)) continue;
    const cents = toCents(d.valor);
    if (d.tipo === "DEBITO") s += cents;
    else if (cartaoIdDe(d) !== null) s -= cents; // estorno de cartão abate o gasto
  }
  return s;
}

/** Média mensal variável corrigida para preços do mês atual e nº de meses com gasto. */
export function variavelBase(despesas: readonly Despesa[], agora: number, inflacaoMensal: number): { base: number; mesesComDados: number } {
  const { mes, ano } = mesAnoDe(agora);
  let soma = 0;
  let com = 0;
  for (let k = 1; k <= 3; k++) {
    const p = normalizaMes(mes - k, ano);
    const v = somaVariavel(despesas, inicioDoMes(p.mes, p.ano), fimDoMes(p.mes, p.ano));
    if (v > 0) com++;
    soma += Math.max(0, v) * Math.pow(1 + inflacaoMensal, k);
  }
  return { base: Math.round(soma / 3), mesesComDados: com };
}

export function projetarFluxo(ds: Dataset, agora: number, meses: number, inflacaoMensal: number): ResultadoFluxo {
  const n = Math.min(MESES_MAX, Math.max(MESES_MIN, Math.trunc(meses)));
  const cur = mesAnoDe(agora);
  const ultimo = normalizaMes(cur.mes + n - 1, cur.ano);
  const ate = ultimoDiaSerial(ultimo.mes, ultimo.ano);
  const eventos = eventosPrevistos(ds, agora, { ate });
  const entradas = new Array<number>(n).fill(0);
  const saidas = new Array<number>(n).fill(0);
  const idx0 = cur.ano * 12 + (cur.mes - 1);
  for (const e of eventos) {
    const d = dataDeSerial(e.dia);
    const k = d.ano * 12 + (d.mes - 1) - idx0;
    if (k < 0 || k >= n) continue;
    if (e.centavos >= 0) entradas[k] += e.centavos;
    else saidas[k] += -e.centavos;
  }
  const { base, mesesComDados } = variavelBase(ds.despesas, agora, inflacaoMensal);
  const jaGasto = Math.max(0, somaVariavel(ds.despesas, inicioDoMes(cur.mes, cur.ano), agora));
  const saldoInicial = saldoCentavos(ds);
  let saldo = saldoInicial;
  const out: MesFluxo[] = [];
  let primeiro: number | null = null;
  let corte = 0;
  for (let j = 0; j < n; j++) {
    const p = normalizaMes(cur.mes + j, cur.ano);
    const estimativa = Math.round(base * Math.pow(1 + inflacaoMensal, j));
    const variavel = j === 0 ? Math.max(0, estimativa - jaGasto) : estimativa;
    const resultado = entradas[j] - saidas[j] - variavel;
    saldo += resultado;
    const negativo = saldo < 0;
    if (negativo) {
      if (primeiro === null) primeiro = j;
      corte = Math.max(corte, Math.ceil(-saldo / (j + 1)));
    }
    out.push({ indice: j, mes: p.mes, ano: p.ano, entradas: entradas[j], saidas: saidas[j], variavel, resultado, saldoFinal: saldo, negativo });
  }
  return { saldoInicial, meses: out, variavelBase: base, mesesComDados, primeiroNegativo: primeiro, cortePorMes: corte };
}

/** true quando não há nenhum dado a projetar (sem eventos, sem histórico e saldo zero). */
export function fluxoVazio(r: ResultadoFluxo): boolean {
  return r.saldoInicial === 0 && r.mesesComDados === 0 && r.meses.every((m) => m.entradas === 0 && m.saidas === 0);
}
