import { emptyDataset } from "../finance/types";
import type { Dataset } from "../finance/types";
import { backupSchema, normalizarBackup } from "./backup";
import type { PersistenceAdapter } from "./adapter";

export const LOCAL_STORAGE_KEY = "meudinheiro:local:dataset:v1";

function ler(): Dataset {
  try {
    const raw = window.localStorage.getItem(LOCAL_STORAGE_KEY);
    if (!raw) return emptyDataset();
    const r = backupSchema.safeParse(JSON.parse(raw));
    return r.success ? normalizarBackup(r.data, Date.now()) : emptyDataset();
  } catch {
    return emptyDataset();
  }
}

function gravar(ds: Dataset): void {
  const { transacoes, ...resto } = ds;
  window.localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify({ ...resto, transacao: transacoes }));
}

/**
 * Modo local: o dataset inteiro fica no localStorage do navegador.
 * Os dados NÃO saem do dispositivo e não sincronizam com o app.
 */
export class LocalAdapter implements PersistenceAdapter {
  readonly mode = "local" as const;

  start(onData: (ds: Dataset) => void): () => void {
    onData(ler());
    return () => undefined;
  }

  async save(_prev: Dataset, next: Dataset): Promise<void> {
    gravar(next);
  }

  async replaceAll(_prev: Dataset, next: Dataset): Promise<void> {
    gravar(next);
  }

  async clear(): Promise<void> {
    window.localStorage.removeItem(LOCAL_STORAGE_KEY);
  }
}
