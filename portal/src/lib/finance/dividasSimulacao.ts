/**
 * R97 - simulador de quitação com taxa/mínimo informados no simulador (sem alterar a dívida cadastrada).
 * Reaproveita o motor da R58 (`dividas.ts`): mesmas regras, mesmos números. Os ajustes ficam só em memória/tela.
 */
import { compararEstrategias, paraSim, TAXA_MAXIMA_PERCENTUAL, type Comparativo, type DividaSim } from "./dividas";
import { toCents } from "./money";
import type { Divida } from "./types";

export interface AjusteDivida {
  /** % ao mês informado no simulador; undefined = usa a da dívida. */
  taxaPct?: number;
  /** Mínimo mensal em reais; undefined = usa o da dívida. */
  minimo?: number;
}

/** Dívidas ativas com saldo, já com os ajustes do simulador aplicados (taxa limitada a 100% a.m.). */
export function dividasParaSimular(dividas: readonly Divida[], ajustes: Readonly<Record<number, AjusteDivida>>): DividaSim[] {
  const out: DividaSim[] = [];
  for (const d of dividas) {
    const base = paraSim(d);
    if (!base) continue;
    const aj = ajustes[d.id];
    let taxaBp = base.taxaBp;
    let minimo = base.minimo;
    if (aj?.taxaPct !== undefined && Number.isFinite(aj.taxaPct) && aj.taxaPct >= 0) taxaBp = Math.round(Math.min(aj.taxaPct, TAXA_MAXIMA_PERCENTUAL) * 100);
    if (aj?.minimo !== undefined && Number.isFinite(aj.minimo) && aj.minimo >= 0) minimo = toCents(aj.minimo);
    out.push({ ...base, taxaBp, minimo });
  }
  return out;
}

export function compararComAjustes(dividas: readonly Divida[], ajustes: Readonly<Record<number, AjusteDivida>>, extraReais: number): Comparativo {
  return compararEstrategias(dividasParaSimular(dividas, ajustes), toCents(Math.max(0, Number.isFinite(extraReais) ? extraReais : 0)));
}
