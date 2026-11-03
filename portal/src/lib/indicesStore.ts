import { create } from "zustand";
import {
  buscarIndicesBcb,
  cacheExpirado,
  CHAVE_CACHE_INDICES,
  lerSnapshotIndices,
  mesclarSnapshots,
  snapshotVazio,
  type SnapshotIndices,
} from "./finance/bcb";
import {
  aplicarBcb,
  carregarIndices,
  indicesPadrao,
  paraSalvar,
  restaurarIndices,
  salvarIndices,
  type ConjuntoIndices,
  type IndiceId,
} from "./finance/indices";

/** O que "usar automático" pode devolver ao Banco Central. */
export type AlvoAutomatico = IndiceId | "selicMeta" | "salarioMinimo";

interface Estado {
  /** Valores EFETIVOS (base + Banco Central onde o usuário não editou). É o que as telas consomem. */
  conjunto: ConjuntoIndices;
  /** Só semente + edições manuais: é o que vai para o localStorage dos índices. */
  base: ConjuntoIndices;
  /** Último snapshot do Banco Central (cache local), se houver. */
  bcb: SnapshotIndices | null;
  carregado: boolean;
  carregando: boolean;
  erro: string | null;
  /** Atualização parcial: séries que não responderam (mantém o último valor conhecido). */
  avisoParcial: string | null;
  offline: boolean;
  carregar: () => void;
  /** Aplica a edição à base e grava em localStorage (só local; nunca vai ao backup/Firestore). */
  atualizar: (fn: (atual: ConjuntoIndices) => ConjuntoIndices) => void;
  /** Descarta as edições manuais (volta à semente + Banco Central). */
  restaurar: () => void;
  /** Volta um campo editado manualmente para o valor automático. */
  usarAutomatico: (alvo: AlvoAutomatico) => void;
  /** Consulta o Banco Central. `forcar`=false respeita o TTL de 6 h (uma busca automática a cada 6 h). */
  atualizarBcb: (forcar: boolean) => Promise<void>;
}

function armazem(): Storage | null {
  try {
    return typeof window !== "undefined" ? window.localStorage : null;
  } catch {
    return null;
  }
}

function lerCache(): SnapshotIndices | null {
  try {
    const raw = armazem()?.getItem(CHAVE_CACHE_INDICES);
    return raw ? lerSnapshotIndices(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

function gravarCache(s: SnapshotIndices): void {
  try {
    armazem()?.setItem(CHAVE_CACHE_INDICES, JSON.stringify({ ...s, falhas: [] }));
  } catch {
    // sem storage: vale só na sessão
  }
}

/** R97/R99 - índices/taxas (localStorage `meudinheiro:indices:v1` = edições; `meudinheiro:bcb-indices:v1` = cache do BCB). */
export const useIndices = create<Estado>((set, get) => ({
  conjunto: indicesPadrao(),
  base: indicesPadrao(),
  bcb: null,
  carregado: false,
  carregando: false,
  erro: null,
  avisoParcial: null,
  offline: false,
  carregar: () => {
    if (get().carregado) return;
    const base = carregarIndices();
    const bcb = lerCache();
    set({ base, bcb, conjunto: aplicarBcb(base, bcb), carregado: true });
  },
  atualizar: (fn) => {
    const base = fn(structuredClone(get().base));
    salvarIndices(paraSalvar(base));
    set({ base, conjunto: aplicarBcb(base, get().bcb) });
  },
  restaurar: () => {
    const base = restaurarIndices();
    set({ base, conjunto: aplicarBcb(base, get().bcb) });
  },
  usarAutomatico: (alvo) => {
    get().atualizar((c) => {
      const padrao = indicesPadrao();
      if (alvo === "selicMeta") c.extras.selicMetaAnual = null;
      else if (alvo === "salarioMinimo") c.salarioMinimo = padrao.salarioMinimo;
      else {
        const i = c.itens.findIndex((x) => x.id === alvo);
        const p = padrao.itens.find((x) => x.id === alvo);
        if (i >= 0 && p) c.itens[i] = p;
      }
      return c;
    });
  },
  atualizarBcb: async (forcar) => {
    const st = get();
    if (st.carregando) return;
    if (!forcar && !cacheExpirado(st.bcb?.atualizadoEm, Date.now())) return;
    if (typeof navigator !== "undefined" && navigator.onLine === false) {
      set({ offline: true, erro: null });
      return;
    }
    set({ carregando: true, offline: false, erro: null, avisoParcial: null });
    try {
      const novo = await buscarIndicesBcb(new Date());
      if (snapshotVazio(novo)) {
        const motivo = novo.falhas[0]?.motivo ?? "sem resposta";
        set({ carregando: false, erro: motivo });
        return;
      }
      const junto = mesclarSnapshots(get().bcb, novo);
      gravarCache(junto);
      set({
        carregando: false,
        bcb: junto,
        conjunto: aplicarBcb(get().base, junto),
        avisoParcial: novo.falhas.length ? `Sem resposta do Banco Central para: ${novo.falhas.map((f) => f.fonte.replace(/ \(SGS.*$/, "")).join(", ")}. Mantidos os últimos valores conhecidos.` : null,
      });
    } catch (e) {
      set({ carregando: false, erro: e instanceof Error ? e.message : "falha de rede" });
    }
  },
}));
