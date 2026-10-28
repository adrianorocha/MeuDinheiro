import { cartaoIdDe, faturasPendentes, kpisDoMes, normalizaNome } from "./finance/calc";
import type { FaturaPendente, Kpis } from "./finance/calc";
import { fimDoMes, inicioDoDia, inicioDoMes, MESES_ABREV, normalizaMes, mesAnoDe } from "./finance/dates";
import { fromCents, toCents } from "./finance/money";
import type { Dataset, Despesa } from "./finance/types";

export type PeriodoId = "mes" | "anterior" | "total";

export interface Periodo {
  id: PeriodoId;
  rotulo: string;
  inicio: number;
  fim: number;
}

export function periodoDe(id: PeriodoId, agora: number): Periodo {
  const { mes, ano } = mesAnoDe(agora);
  if (id === "mes") return { id, rotulo: "Este mês", inicio: inicioDoMes(mes, ano), fim: fimDoMes(mes, ano) };
  if (id === "anterior") {
    const p = normalizaMes(mes - 1, ano);
    return { id, rotulo: "Mês passado", inicio: inicioDoMes(p.mes, p.ano), fim: fimDoMes(p.mes, p.ano) };
  }
  return { id, rotulo: "Total", inicio: Number.NEGATIVE_INFINITY, fim: Number.POSITIVE_INFINITY };
}

export interface FatiaCategoria {
  categoria: string;
  valor: number;
}

/** Despesas (R5: só NORMAL) por categoria no período, abatendo estornos de cartão. */
export function despesasPorCategoria(despesas: readonly Despesa[], inicio: number, fim: number): FatiaCategoria[] {
  const mapa = new Map<string, { nome: string; cents: number }>();
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || d.data < inicio || d.data > fim) continue;
    let delta = 0;
    if (d.tipo === "DEBITO") delta = toCents(d.valor);
    else if (cartaoIdDe(d) !== null) delta = -toCents(d.valor);
    else continue;
    const chave = normalizaNome(d.categoria);
    const atual = mapa.get(chave) ?? { nome: d.categoria.trim() || "Sem categoria", cents: 0 };
    atual.cents += delta;
    mapa.set(chave, atual);
  }
  return [...mapa.values()]
    .filter((c) => c.cents > 0)
    .map((c) => ({ categoria: c.nome, valor: fromCents(c.cents) }))
    .sort((a, b) => b.valor - a.valor);
}

export interface PontoMensal extends Kpis {
  mes: number;
  ano: number;
  rotulo: string;
}

/** Série dos últimos `n` meses (do mais antigo ao mais recente). */
export function serieMensal(despesas: readonly Despesa[], n: number, agora: number): PontoMensal[] {
  const { mes, ano } = mesAnoDe(agora);
  const out: PontoMensal[] = [];
  for (let i = n - 1; i >= 0; i--) {
    const p = normalizaMes(mes - i, ano);
    out.push({ ...kpisDoMes(despesas, p.mes, p.ano), ...p, rotulo: `${MESES_ABREV[p.mes - 1]}/${String(p.ano).slice(2)}` });
  }
  return out;
}

export interface ContaAPagar {
  despesa: Despesa;
  atrasada: boolean;
}

/** Pendências de conta (não cartão), não pagas, ordenadas por data. Inclui atrasadas. */
export function contasAPagar(despesas: readonly Despesa[], agora: number): ContaAPagar[] {
  const hoje = inicioDoDia(agora);
  return despesas
    .filter((d) => d.natureza === "NORMAL" && !d.pago && cartaoIdDe(d) === null && d.tipo === "DEBITO")
    .sort((a, b) => a.data - b.data)
    .map((d) => ({ despesa: d, atrasada: d.data < hoje }));
}

export function faturasEmAberto(ds: Dataset): FaturaPendente[] {
  return faturasPendentes(ds.cartoes, ds.despesas).filter((f) => f.total > 0);
}

/** Últimos `n` snapshots de patrimônio por data. */
export function ultimosSnapshots(ds: Dataset, n: number) {
  return [...ds.patrimonio].sort((a, b) => a.dataMillis - b.dataMillis).slice(-n);
}
