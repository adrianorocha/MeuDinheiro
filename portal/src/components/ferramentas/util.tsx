"use client";

import { Info } from "lucide-react";
import type { ReactNode } from "react";
import { FONTE_INDICES } from "@/lib/finance/indices";
import { parseValorBR } from "@/lib/format";

/** Número em pt-BR com casas fixas ("1,0800"). */
export function fmtNum(n: number, casas = 2): string {
  return Number.isFinite(n) ? n.toLocaleString("pt-BR", { minimumFractionDigits: casas, maximumFractionDigits: casas }) : "—";
}

/** Fração -> "13,76%" . */
export function fmtPctFracao(f: number, casas = 2): string {
  return Number.isFinite(f) ? `${fmtNum(f * 100, casas)}%` : "—";
}

/** Texto digitado em pt-BR -> número; vazio/inválido -> NaN. */
export const lerNum = (t: string): number => parseValorBR(t);

/** Texto de um número para campo editável ("1,08"), sem zeros à direita desnecessários. */
export function paraCampo(n: number, maxCasas = 4): string {
  if (!Number.isFinite(n)) return "";
  return n.toLocaleString("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: maxCasas, useGrouping: false });
}

export function AvisoReferencia({ children }: { children?: ReactNode }) {
  return (
    <p className="mb-4 flex gap-2 rounded-xl border border-warn/40 bg-warn-soft px-3 py-2 text-sm text-warn" role="note">
      <Info size={16} className="mt-0.5 shrink-0" aria-hidden />
      <span>
        {children ?? "Simulação com valores de referência editáveis. Não é recomendação de investimento nem oferta de crédito."} {FONTE_INDICES}.
      </span>
    </p>
  );
}

export function Resultado({ rotulo, children, destaque }: { rotulo: string; children: ReactNode; destaque?: boolean }) {
  return (
    <div className={`rounded-xl px-3 py-2 ${destaque ? "bg-primary-soft" : "bg-surface-2"}`}>
      <p className="text-xs text-muted">{rotulo}</p>
      <p className="text-base font-semibold">{children}</p>
    </div>
  );
}
