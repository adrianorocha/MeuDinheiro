import { create } from "zustand";
import { gravar, ler } from "./session";

const CHAVE = "meudinheiro:insights-dispensados:v1";

interface Estado {
  dispensados: Set<string>;
  carregado: boolean;
  carregar: () => void;
  dispensar: (id: string) => void;
  restaurar: () => void;
}

/** R97 - insights de economia dispensados pelo usuário (só localStorage; nada vai ao backup/Firestore). */
export const useInsightsDispensados = create<Estado>((set, get) => ({
  dispensados: new Set(),
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
    set({ dispensados: new Set(lista), carregado: true });
  },
  dispensar: (id) => {
    const novo = new Set(get().dispensados);
    novo.add(id);
    gravar(CHAVE, JSON.stringify([...novo].slice(-300)));
    set({ dispensados: novo });
  },
  restaurar: () => {
    gravar(CHAVE, null);
    set({ dispensados: new Set() });
  },
}));
