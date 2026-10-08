import { create } from "zustand";
import { recalcularTudo } from "../finance/calc";
import {
  executarAgendadasVencidas,
  processarDespesasFixas,
  purgarLixeira,
  registrarPatrimonio,
  type Ctx,
} from "../finance/operations";
import { emptyDataset } from "../finance/types";
import type { Dataset, Result } from "../finance/types";
import type { PersistenceAdapter, StorageMode } from "./adapter";

export type Status = "idle" | "carregando" | "pronto" | "erro";

export interface Aviso {
  id: number;
  tipo: "info" | "sucesso" | "erro";
  texto: string;
}

interface Estado {
  ds: Dataset;
  status: Status;
  modo: StorageMode | null;
  erro: string | null;
  erroSync: string | null;
  avisos: Aviso[];
  iniciar: (adapter: PersistenceAdapter) => void;
  parar: () => void;
  /** Aplica uma operação pura, atualiza o estado e persiste a diferença. */
  executar: <T extends object>(fn: (ds: Dataset, ctx: Ctx) => Result<{ ds: Dataset } & T>) => Result<{ ds: Dataset } & T>;
  /** Importação: substitui todo o conteúdo. */
  substituirTudo: (novo: Dataset) => Promise<void>;
  limparTudo: () => Promise<void>;
  avisar: (tipo: Aviso["tipo"], texto: string) => void;
  dispensarAviso: (id: number) => void;
}

let adapterAtivo: PersistenceAdapter | null = null;
let cancelar: (() => void) | null = null;
let fila: Promise<void> = Promise.resolve();
let seqAviso = 1;

function mensagem(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export const useStore = create<Estado>((set, get) => {
  const persistir = (prev: Dataset, next: Dataset, tipo: "save" | "replace" = "save") => {
    const adapter = adapterAtivo;
    if (!adapter) return;
    fila = fila
      .then(() => (tipo === "save" ? adapter.save(prev, next) : adapter.replaceAll(prev, next)))
      .then(() => {
        if (get().erroSync) set({ erroSync: null });
      })
      .catch((e: unknown) => set({ erroSync: `Falha ao salvar: ${mensagem(e)}` }));
  };

  const avisar: Estado["avisar"] = (tipo, texto) =>
    set((s) => ({ avisos: [...s.avisos.slice(-3), { id: seqAviso++, tipo, texto }] }));

  const executar: Estado["executar"] = (fn) => {
    const prev = get().ds;
    const ctx: Ctx = { agora: Date.now() };
    const r = fn(prev, ctx);
    if (!r.ok) return r;
    const next = registrarPatrimonio(r.ds, ctx);
    set({ ds: next });
    persistir(prev, next);
    return { ...r, ds: next };
  };

  /** Rotinas de abertura: despesas fixas (R16), transferências agendadas vencidas (R9) e snapshot (R14). */
  const rotinasDeAbertura = () => {
    // R31: remove da lixeira o que passou de 30 dias.
    executar((ds, ctx) => ({ ok: true as const, ds: purgarLixeira(ds, ctx.agora) }));
    const fixas = executar(processarDespesasFixas);
    if (fixas.ok && fixas.criados.length > 0) {
      avisar("info", `${fixas.criados.length} despesa(s) fixa(s) lançada(s) automaticamente.`);
    }
    const agendadas = executar(executarAgendadasVencidas);
    if (agendadas.ok) {
      if (agendadas.executadas > 0) avisar("sucesso", `${agendadas.executadas} transferência(s) agendada(s) executada(s).`);
      for (const f of agendadas.falhas) avisar("erro", `Transferência agendada não executada: ${f.erro}`);
    }
  };

  return {
    ds: emptyDataset(),
    status: "idle",
    modo: null,
    erro: null,
    erroSync: null,
    avisos: [],

    iniciar: (adapter) => {
      get().parar();
      adapterAtivo = adapter;
      set({ ds: emptyDataset(), status: "carregando", modo: adapter.mode, erro: null, erroSync: null });
      let primeira = true;
      cancelar = adapter.start(
        (dados) => {
          if (adapterAtivo !== adapter) return;
          const calculado = recalcularTudo(dados);
          set({ ds: calculado, status: "pronto", erro: null });
          // O portal mantém os caches contas.saldo / cartoes.limiteDisponivel atualizados.
          if (calculado !== dados) persistir(dados, calculado);
          if (primeira) {
            primeira = false;
            rotinasDeAbertura();
          }
        },
        (e) => {
          if (adapterAtivo !== adapter) return;
          set({ status: "erro", erro: mensagem(e) });
        },
      );
    },

    parar: () => {
      cancelar?.();
      cancelar = null;
      adapterAtivo = null;
      set({ ds: emptyDataset(), status: "idle", modo: null });
    },

    executar,

    substituirTudo: async (novo) => {
      const prev = get().ds;
      const ds = recalcularTudo(novo);
      set({ ds });
      persistir(prev, ds, "replace");
      await fila;
    },

    limparTudo: async () => {
      const adapter = adapterAtivo;
      if (!adapter) return;
      set({ ds: emptyDataset() });
      fila = fila
        .then(() => adapter.clear())
        .catch((e: unknown) => set({ erroSync: `Falha ao apagar: ${mensagem(e)}` }));
      await fila;
    },

    avisar,
    dispensarAviso: (id) => set((s) => ({ avisos: s.avisos.filter((a) => a.id !== id) })),
  };
});
