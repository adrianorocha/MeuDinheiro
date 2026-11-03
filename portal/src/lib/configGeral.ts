import { create } from "zustand";
import { alterarConfig, mesclarConfig, normalizarGeral, type ConfigGeral } from "./finance/configuracoes";
import { ler, gravar } from "./session";

/**
 * R57 - configurações sincronizadas (painel 50-30-20 e período do limite diário).
 * O local (localStorage) continua sendo a fonte imediata e funciona offline / sem Firebase; com a nuvem ligada,
 * toda alteração também vai para `users/{uid}/configuracoes/geral` e o que chega da nuvem só vale se for mais novo.
 */

export const CHAVE_CONFIG_GERAL = "meudinheiro:config:geral:v1";
/** Chaves antigas (R51/R54): lidas só como retrocompatibilidade quando ainda não há config geral. */
const CHAVE_LEGADA_PAINEL = "meudinheiro:painel503020:v1";
const CHAVE_LEGADA_LIMITE = "md.limiteDiario.ateProximaReceita";

/** Lê o armazenamento local (nunca lança). Dados antigos entram com `atualizadoEm` ausente (= nunca alterada). */
export function lerConfigLocal(): ConfigGeral {
  try {
    const raw = ler(CHAVE_CONFIG_GERAL);
    const base = raw ? normalizarGeral(JSON.parse(raw)) : {};
    if (base.painel503020 === undefined) {
      const p = ler(CHAVE_LEGADA_PAINEL);
      if (p) base.painel503020 = normalizarGeral({ painel503020: JSON.parse(p) }).painel503020;
    }
    if (base.limiteDiarioAteReceita === undefined) {
      const l = ler(CHAVE_LEGADA_LIMITE);
      if (l === "1" || l === "0") base.limiteDiarioAteReceita = l === "1";
    }
    return base;
  } catch {
    return {};
  }
}

function gravarLocal(c: ConfigGeral): void {
  gravar(CHAVE_CONFIG_GERAL, JSON.stringify(c));
}

interface Estado {
  config: ConfigGeral;
  /** Altera (carimba atualizadoEm), grava local e envia à nuvem se ligada. */
  alterar: (patch: Partial<Omit<ConfigGeral, "atualizadoEm">>) => void;
  /** Recebeu o documento da nuvem: aplica só se for mais novo. Devolve se deve enviar o local. */
  receberDaNuvem: (nuvem: unknown) => "NADA" | "APLICOU" | "ENVIAR_LOCAL";
  /** Importação de backup: mesma regra (mais novo vence). */
  importar: (config: ConfigGeral) => boolean;
}

let enviador: ((c: ConfigGeral) => void) | null = null;

/** Liga/desliga o envio para a nuvem (chamado pelo provedor de sessão). */
export function definirEnviadorNuvem(fn: ((c: ConfigGeral) => void) | null): void {
  enviador = fn;
}

export const useConfigGeral = create<Estado>((set, get) => ({
  config: typeof window === "undefined" ? {} : lerConfigLocal(),

  alterar: (patch) => {
    const novo = alterarConfig(get().config, patch, Date.now());
    gravarLocal(novo);
    set({ config: novo });
    enviador?.(novo);
  },

  receberDaNuvem: (nuvem) => {
    const n = normalizarGeral(nuvem);
    const r = mesclarConfig(get().config, n);
    if (r.acao === "APLICAR_NUVEM") {
      gravarLocal(r.config);
      set({ config: r.config });
      return "APLICOU";
    }
    return r.acao === "ENVIAR_LOCAL" ? "ENVIAR_LOCAL" : "NADA";
  },

  importar: (cfg) => {
    const r = mesclarConfig(get().config, cfg);
    if (r.acao !== "APLICAR_NUVEM") return false;
    gravarLocal(r.config);
    set({ config: r.config });
    enviador?.(r.config);
    return true;
  },
}));
