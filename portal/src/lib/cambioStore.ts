import { create } from "zustand";
import {
  buscarCotacoes,
  buscarHistorico,
  cacheExpirado,
  CHAVE_CACHE_CAMBIO,
  lerSnapshotCambio,
  type CotacaoMoeda,
  type Falha,
  type PontoHistorico,
} from "./finance/bcb";

interface EstadoHistorico {
  carregando: boolean;
  erro: string | null;
  pontos: PontoHistorico[];
}

interface Estado {
  cotacoes: CotacaoMoeda[];
  atualizadoEm: number | null;
  falhas: Falha[];
  carregado: boolean;
  carregando: boolean;
  erro: string | null;
  offline: boolean;
  /** Histórico por moeda (só em memória, consultado apenas quando o usuário pede). */
  historico: Record<string, EstadoHistorico>;
  carregar: () => void;
  atualizar: (forcar: boolean) => Promise<void>;
  carregarHistorico: (moeda: string) => Promise<void>;
}

function armazem(): Storage | null {
  try {
    return typeof window !== "undefined" ? window.localStorage : null;
  } catch {
    return null;
  }
}

/** R99 - cotações PTAX do Banco Central. Cache local `meudinheiro:bcb-cambio:v1` (TTL 6 h); nunca vai ao backup/Firestore. */
export const useCambio = create<Estado>((set, get) => ({
  cotacoes: [],
  atualizadoEm: null,
  falhas: [],
  carregado: false,
  carregando: false,
  erro: null,
  offline: false,
  historico: {},
  carregar: () => {
    if (get().carregado) return;
    try {
      const raw = armazem()?.getItem(CHAVE_CACHE_CAMBIO);
      const s = raw ? lerSnapshotCambio(JSON.parse(raw)) : null;
      if (s) set({ cotacoes: s.cotacoes, atualizadoEm: s.atualizadoEm });
    } catch {
      // sem cache
    }
    set({ carregado: true });
  },
  atualizar: async (forcar) => {
    const st = get();
    if (st.carregando) return;
    if (!forcar && !cacheExpirado(st.atualizadoEm, Date.now())) return;
    if (typeof navigator !== "undefined" && navigator.onLine === false) {
      set({ offline: true, erro: null });
      return;
    }
    set({ carregando: true, offline: false, erro: null });
    try {
      const novo = await buscarCotacoes(new Date());
      if (!novo.cotacoes.length) {
        set({ carregando: false, erro: novo.falhas[0]?.motivo ?? "sem resposta" });
        return;
      }
      // moedas que falharam agora mantêm a última cotação salva
      const antigas = get().cotacoes.filter((c) => !novo.cotacoes.some((n) => n.moeda === c.moeda));
      const juntas = [...novo.cotacoes, ...antigas];
      try {
        armazem()?.setItem(CHAVE_CACHE_CAMBIO, JSON.stringify({ atualizadoEm: novo.atualizadoEm, cotacoes: juntas, falhas: [] }));
      } catch {
        // sem storage
      }
      set({ carregando: false, cotacoes: juntas, atualizadoEm: novo.atualizadoEm, falhas: novo.falhas });
    } catch (e) {
      set({ carregando: false, erro: e instanceof Error ? e.message : "falha de rede" });
    }
  },
  carregarHistorico: async (moeda) => {
    const atual = get().historico[moeda];
    if (atual?.carregando) return;
    if (typeof navigator !== "undefined" && navigator.onLine === false) {
      set({ historico: { ...get().historico, [moeda]: { carregando: false, erro: "sem conexão", pontos: atual?.pontos ?? [] } } });
      return;
    }
    set({ historico: { ...get().historico, [moeda]: { carregando: true, erro: null, pontos: atual?.pontos ?? [] } } });
    try {
      const pontos = await buscarHistorico(moeda, new Date());
      if (!pontos.length) throw new Error("sem cotações no período");
      set({ historico: { ...get().historico, [moeda]: { carregando: false, erro: null, pontos } } });
    } catch (e) {
      set({ historico: { ...get().historico, [moeda]: { carregando: false, erro: e instanceof Error ? e.message : "falha de rede", pontos: atual?.pontos ?? [] } } });
    }
  },
}));
