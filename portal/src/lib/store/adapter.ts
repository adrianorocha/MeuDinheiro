import type { Dataset } from "../finance/types";

export type StorageMode = "local" | "firebase";

/** Contrato comum aos dois modos de persistência. */
export interface PersistenceAdapter {
  readonly mode: StorageMode;
  /**
   * Carrega os dados e passa a notificar alterações externas (Firestore: onSnapshot).
   * O primeiro `onData` ocorre quando todas as coleções foram lidas.
   * Retorna a função de cancelamento.
   */
  start(onData: (ds: Dataset) => void, onError: (e: Error) => void): () => void;
  /** Persiste a diferença entre `prev` e `next`. */
  save(prev: Dataset, next: Dataset): Promise<void>;
  /** Substitui tudo por `next` (importação de backup). */
  replaceAll(prev: Dataset, next: Dataset): Promise<void>;
  /** Apaga todos os dados do usuário neste modo. */
  clear(): Promise<void>;
}
