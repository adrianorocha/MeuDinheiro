/**
 * R97 - calculadora de reajuste (aluguel/mensalidade) por índice acumulado.
 * Acumulado = composição dos últimos N meses da série do índice (Π(1+i)−1); se a série tiver menos de N meses, usa os
 * disponíveis e avisa (`incompleto`). O usuário pode informar o acumulado oficial de 12 meses (substitui a composição).
 * Novo valor = valor × (1 + acumulado), arredondado para centavos (meio para cima) só na saída. Índice negativo REDUZ o valor
 * (a calculadora não aplica piso; contratos podem prever que não há redução).
 */
import { acumuladoDosUltimos, acumular, pctParaFracao } from "./indices";

export interface IndiceReajuste {
  id: string;
  nome: string;
  /** % ao mês, o mais recente por último. */
  serie: readonly number[];
  /** Acumulado informado pelo usuário (%), substitui a composição. */
  acumuladoInformadoPct?: number | null;
}

export interface LinhaReajuste {
  id: string;
  nome: string;
  /** Fração. */
  acumulado: number;
  mesesUsados: number;
  incompleto: boolean;
  informado: boolean;
  /** Centavos. */
  novoValor: number;
  diferenca: number;
}

const c = (x: number): number => Math.round(x * 100 + 1e-7 * Math.sign(x));

/** Reajuste direto por um percentual acumulado (fração). `valor` em reais; retorna centavos. */
export function aplicarReajuste(valor: number, acumulado: number): number {
  return c(valor * (1 + acumulado));
}

export function calcularReajustes(valor: number, meses: number, indices: readonly IndiceReajuste[]): LinhaReajuste[] | null {
  if (!(valor > 0) || !Number.isInteger(meses) || meses < 1 || meses > 120) return null;
  const base = c(valor);
  return indices.map((i) => {
    const informado = i.acumuladoInformadoPct != null && Number.isFinite(i.acumuladoInformadoPct);
    const r = informado ? { acumulado: pctParaFracao(i.acumuladoInformadoPct as number), mesesUsados: meses, incompleto: false } : acumuladoDosUltimos(i.serie, meses);
    const novo = aplicarReajuste(valor, r.acumulado);
    return { id: i.id, nome: i.nome, acumulado: r.acumulado, mesesUsados: r.mesesUsados, incompleto: r.incompleto, informado, novoValor: novo, diferenca: novo - base };
  });
}

/** Composição de uma lista de percentuais mensais (%) em fração. */
export const acumuladoDePcts = (pcts: readonly number[]): number => acumular(pcts.map(pctParaFracao));
