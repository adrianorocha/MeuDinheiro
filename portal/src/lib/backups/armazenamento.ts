import type { Dataset } from "../finance/types";
import { limitarHistorico, type RegistroBackup } from "./historico";
import { aplicarLimite, entradasDoDiff, LIMITE_TRILHA, type EntradaTrilha } from "./trilha";

/**
 * R66 - armazenamento local (IndexedDB) do histórico de backups do navegador e da trilha de alterações.
 * Tudo aqui é "melhor esforço": falha de armazenamento NUNCA derruba a operação que está sendo registrada.
 */

export interface KV {
  get(chave: string): Promise<unknown>;
  set(chave: string, valor: unknown): Promise<void>;
}

const NOME_DB = "meudinheiro-backups";
const STORE = "kv";

function abrir(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(NOME_DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB indisponível."));
  });
}

/** KV sobre IndexedDB (indisponível, ex.: janela anônima antiga ou testes em Node: lança erro, tratado por quem chama). */
export const kvIndexedDb: KV = {
  async get(chave) {
    const db = await abrir();
    try {
      return await new Promise<unknown>((resolve, reject) => {
        const r = db.transaction(STORE, "readonly").objectStore(STORE).get(chave);
        r.onsuccess = () => resolve(r.result);
        r.onerror = () => reject(r.error);
      });
    } finally {
      db.close();
    }
  },
  async set(chave, valor) {
    const db = await abrir();
    try {
      await new Promise<void>((resolve, reject) => {
        const t = db.transaction(STORE, "readwrite");
        t.objectStore(STORE).put(valor, chave);
        t.oncomplete = () => resolve();
        t.onerror = () => reject(t.error);
        t.onabort = () => reject(t.error);
      });
    } finally {
      db.close();
    }
  },
};

/** Trilha de alterações (2000 entradas, mais antigas primeiro no armazenamento). */
export function criarTrilhaStore(kv: KV, escopo: () => string) {
  let fila: Promise<unknown> = Promise.resolve();
  const chave = () => `trilha:${escopo()}`;

  async function registrar(novas: readonly EntradaTrilha[]): Promise<boolean> {
    if (novas.length === 0) return true;
    const k = chave();
    const op = fila.then(async () => {
      const atuais = ((await kv.get(k)) as EntradaTrilha[] | undefined) ?? [];
      await kv.set(k, aplicarLimite(Array.isArray(atuais) ? atuais : [], novas, LIMITE_TRILHA));
    });
    fila = op.catch(() => undefined);
    try {
      await op;
      return true;
    } catch {
      return false;
    }
  }

  return {
    registrar,
    /** Registra o que mudou entre [antes] e [depois]; nunca lança. */
    registrarDiff(antes: Dataset, depois: Dataset, quando: number, dica?: "RESTAURAR_LIXEIRA"): void {
      try {
        void registrar(entradasDoDiff(antes, depois, quando, dica));
      } catch {
        /* auditoria é informativa */
      }
    },
    /** Mais recentes primeiro. */
    async ler(): Promise<EntradaTrilha[]> {
      try {
        const v = (await kv.get(chave())) as EntradaTrilha[] | undefined;
        return Array.isArray(v) ? [...v].reverse() : [];
      } catch {
        return [];
      }
    },
  };
}

/** Histórico dos backups gerados no navegador (últimos 7). */
export function criarHistoricoStore(kv: KV, escopo: () => string) {
  const chave = () => `backups:${escopo()}`;
  async function listar(): Promise<RegistroBackup[]> {
    try {
      const v = (await kv.get(chave())) as RegistroBackup[] | undefined;
      return Array.isArray(v) ? [...v].sort((a, b) => b.criadoEm - a.criadoEm) : [];
    } catch {
      return [];
    }
  }
  return {
    listar,
    async adicionar(r: RegistroBackup): Promise<boolean> {
      try {
        const { manter } = limitarHistorico([r, ...(await listar())]);
        await kv.set(chave(), manter);
        return true;
      } catch {
        return false;
      }
    },
    async excluir(id: string): Promise<boolean> {
      try {
        await kv.set(chave(), (await listar()).filter((x) => x.id !== id));
        return true;
      } catch {
        return false;
      }
    },
  };
}

const CHAVE_ULTIMO = "meudinheiro:backup:ultimo";
const CHAVE_LEMBRETE = "meudinheiro:backup:lembrete";

function lerLocal(chave: string): string | null {
  try {
    return window.localStorage.getItem(chave);
  } catch {
    return null;
  }
}
function gravarLocal(chave: string, valor: string): void {
  try {
    window.localStorage.setItem(chave, valor);
  } catch {
    /* ignorado */
  }
}

export const lembrete = {
  ultimoBackup: (): number | null => {
    const n = Number(lerLocal(CHAVE_ULTIMO));
    return Number.isFinite(n) && n > 0 ? n : null;
  },
  marcarBackup: (quando: number) => gravarLocal(CHAVE_ULTIMO, String(quando)),
  ativo: (): boolean => lerLocal(CHAVE_LEMBRETE) !== "off",
  definirAtivo: (v: boolean) => gravarLocal(CHAVE_LEMBRETE, v ? "on" : "off"),
};
