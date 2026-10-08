import { create } from "zustand";
import type { PlanoInverso, ResumoAplicacao } from "./plano";

export interface UltimoLote {
  inverso: PlanoInverso;
  resumo: ResumoAplicacao;
  rotuloDestino: string;
  em: number;
}

interface Estado {
  ultimo: UltimoLote | null;
  definir: (l: UltimoLote | null) => void;
}

/** Último lote aplicado (somente em memória): permite "Desfazer último lote" enquanto a aba estiver aberta. */
export const useUltimoLote = create<Estado>((set) => ({
  ultimo: null,
  definir: (ultimo) => set({ ultimo }),
}));
