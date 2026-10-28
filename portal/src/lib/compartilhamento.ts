"use client";

import { create } from "zustand";
import { listarMembros, meuPapelEm, type Membro } from "./firebase/compartilhar";
import { podeExecutar, type Acao, type Papel } from "./finance/permissoes";
import { useSession } from "./session";

/**
 * R67 - contexto de compartilhamento da conta em uso: meu papel e se há outros membros.
 * Só serve para a UI esconder/bloquear botões e mostrar "por <nome>"; as regras do Firestore/Storage são quem decide.
 * Conta própria ou modo local = DONO (tudo liberado). Membro, até carregar (ou se falhar) = LEITOR (o mais restritivo).
 */
interface Estado {
  papel: Papel;
  temMembros: boolean;
  membros: Membro[];
  carregado: boolean;
  carregar: () => Promise<void>;
  reiniciar: () => void;
}

export const useCompartilhamento = create<Estado>((set) => ({
  papel: "DONO",
  temMembros: false,
  membros: [],
  carregado: false,

  reiniciar: () => set({ papel: "DONO", temMembros: false, membros: [], carregado: false }),

  carregar: async () => {
    const { modo, user, raizUid } = useSession.getState();
    if (modo !== "firebase" || !user) {
      set({ papel: "DONO", temMembros: false, membros: [], carregado: true });
      return;
    }
    const dono = raizUid ?? user.uid;
    if (dono !== user.uid) set({ papel: "LEITOR", carregado: false }); // restritivo até confirmar
    try {
      const [papel, membros] = await Promise.all([meuPapelEm(dono, user.uid), listarMembros(dono).catch(() => [] as Membro[])]);
      set({ papel: papel ?? (dono === user.uid ? "DONO" : "LEITOR"), membros, temMembros: dono !== user.uid || membros.length > 0, carregado: true });
    } catch {
      set({ papel: dono === user.uid ? "DONO" : "LEITOR", membros: [], temMembros: dono !== user.uid, carregado: true });
    }
  },
}));

export function usePapel(): Papel {
  return useCompartilhamento((s) => s.papel);
}

/** Pode executar a ação? (apenas conveniência de UI; o servidor decide). */
export function usePode(acao: Acao): boolean {
  return useCompartilhamento((s) => podeExecutar(s.papel, acao));
}
