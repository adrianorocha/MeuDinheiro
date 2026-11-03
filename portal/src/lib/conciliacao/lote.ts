import { create } from "zustand";
import type { ResultadoLote } from "./lote-multiplo";
import type { PlanoInverso, ResumoAplicacao } from "./plano";

export interface UltimoLote {
  inverso: PlanoInverso;
  resumo: ResumoAplicacao;
  rotuloDestino: string;
  em: number;
}

/** Lote de vários arquivos (R43): resultado por arquivo, com os inversos para desfazer em ordem inversa. */
export interface LoteMultiplo {
  resultado: ResultadoLote;
  em: number;
  desfeito: boolean;
}

interface Estado {
  ultimo: UltimoLote | null;
  definir: (l: UltimoLote | null) => void;
  multiplo: LoteMultiplo | null;
  definirMultiplo: (l: LoteMultiplo | null) => void;
}

/** Último lote aplicado (somente em memória): permite "Desfazer último lote" enquanto a aba estiver aberta. */
export const useUltimoLote = create<Estado>((set) => ({
  ultimo: null,
  definir: (ultimo) => set({ ultimo }),
  multiplo: null,
  definirMultiplo: (multiplo) => set({ multiplo }),
}));
