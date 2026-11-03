import type { CollectionName, Dataset, Entity } from "../finance/types";
import { COLLECTIONS } from "../finance/types";

export interface CollectionDiff {
  colecao: CollectionName;
  upserts: Entity[];
  deletes: number[];
}

/** Compara por identidade de referência (as operações puras preservam objetos inalterados). */
export function diffDataset(prev: Dataset, next: Dataset): CollectionDiff[] {
  const out: CollectionDiff[] = [];
  for (const colecao of COLLECTIONS) {
    const antes = prev[colecao] as readonly Entity[];
    const depois = next[colecao] as readonly Entity[];
    if (antes === depois) continue;
    const mapaAntes = new Map(antes.map((i) => [i.id, i]));
    const idsDepois = new Set<number>();
    const upserts: Entity[] = [];
    for (const item of depois) {
      idsDepois.add(item.id);
      if (mapaAntes.get(item.id) !== item) upserts.push(item);
    }
    const deletes = antes.filter((i) => !idsDepois.has(i.id)).map((i) => i.id);
    if (upserts.length > 0 || deletes.length > 0) out.push({ colecao, upserts, deletes });
  }
  return out;
}
