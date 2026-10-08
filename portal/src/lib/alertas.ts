import { create } from "zustand";
import { alertasOrcamento, type AlertaOrcamento } from "./finance/analises";
import type { Dataset } from "./finance/types";
import { ler, gravar } from "./session";

const CHAVE = "meudinheiro:alertas-orcamento:v1";

interface Estado {
  avisados: Set<string>;
  carregado: boolean;
  carregar: () => void;
  marcar: (chaves: readonly string[]) => void;
}

/** R22 - estado "já avisado" por (categoria, mês, limiar) em localStorage. */
export const useAlertas = create<Estado>((set, get) => ({
  avisados: new Set(),
  carregado: false,
  carregar: () => {
    if (get().carregado) return;
    let lista: string[] = [];
    try {
      const raw = ler(CHAVE);
      const j: unknown = raw ? JSON.parse(raw) : [];
      if (Array.isArray(j)) lista = j.filter((x): x is string => typeof x === "string");
    } catch {
      lista = [];
    }
    set({ avisados: new Set(lista), carregado: true });
  },
  marcar: (chaves) => {
    const novo = new Set(get().avisados);
    for (const c of chaves) novo.add(c);
    // mantém só o necessário (últimos 500)
    gravar(CHAVE, JSON.stringify([...novo].slice(-500)));
    set({ avisados: novo });
  },
}));

export function alertasPendentes(ds: Pick<Dataset, "orcamentos" | "despesas">, agora: number, avisados: ReadonlySet<string>): { alertas: AlertaOrcamento[]; marcar: string[] } {
  return alertasOrcamento(ds, agora, avisados);
}
