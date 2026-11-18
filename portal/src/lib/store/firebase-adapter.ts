import { collection, doc, getDocs, onSnapshot, setDoc, writeBatch, type DocumentData, type Firestore } from "firebase/firestore";
import { COLLECTIONS, emptyDataset } from "../finance/types";
import type { CollectionName, Dataset, Entity } from "../finance/types";
import { getDb } from "../firebase/client";
import type { PersistenceAdapter } from "./adapter";
import { backupSchema, normalizarBackup } from "./backup";
import { diffDataset, type CollectionDiff } from "./diff";
import { AgendadorSinal } from "./sinal";

const LIMITE_BATCH = 500;

/** R72 - coleções legadas do app que o portal não usa em nenhuma tela: só são lidas sob demanda (backup/exportação/restauração). */
export const COLECOES_SOB_DEMANDA: readonly CollectionName[] = ["transacoes"];

type Operacao =
  | { tipo: "set"; colecao: CollectionName; id: number; dados: DocumentData }
  | { tipo: "delete"; colecao: CollectionName; id: number };

/** Remove `undefined` (o Firestore recusa) e carimba `atualizadoEm`. */
function paraDocumento(item: Entity, agora: number): DocumentData {
  const out: DocumentData = { atualizadoEm: agora };
  for (const [k, v] of Object.entries(item)) if (v !== undefined) out[k] = v;
  return out;
}

/** Firestore em `users/{uid}/<colecao>/{id}`: onSnapshot por coleção e escrita em batches de até 500. */
export class FirebaseAdapter implements PersistenceAdapter {
  readonly mode = "firebase" as const;
  private readonly db: Firestore;

  constructor(private readonly uid: string) {
    this.db = getDb();
  }

  private col(nome: CollectionName) {
    return collection(this.db, "users", this.uid, nome);
  }

  start(onData: (ds: Dataset) => void, onError: (e: Error) => void): () => void {
    const docs = new Map<CollectionName, DocumentData[]>();
    const escutadas = COLLECTIONS.filter((n) => !COLECOES_SOB_DEMANDA.includes(n));
    const unsubs = escutadas.map((nome) =>
      onSnapshot(
        this.col(nome),
        (snap) => {
          docs.set(
            nome,
            snap.docs.map((d) => ({ ...d.data(), id: Number(d.id) })),
          );
          if (docs.size === escutadas.length) onData(this.montar(docs));
        },
        (e) => onError(e),
      ),
    );
    return () => unsubs.forEach((u) => u());
  }

  private montar(docs: Map<CollectionName, DocumentData[]>): Dataset {
    const bruto: Record<string, unknown> = { versaoBackup: 2 };
    for (const nome of COLLECTIONS) bruto[nome === "transacoes" ? "transacao" : nome] = docs.get(nome) ?? [];
    const r = backupSchema.safeParse(bruto);
    return r.success ? normalizarBackup(r.data, Date.now()) : emptyDataset();
  }

  private ocultos: Promise<Pick<Dataset, "transacoes">> | null = null;

  /** R72 - lê uma única vez (por sessão) as coleções sob demanda. */
  carregarDadosOcultos(): Promise<Pick<Dataset, "transacoes">> {
    this.ocultos ??= (async () => {
      const snap = await getDocs(this.col("transacoes"));
      const bruto = { versaoBackup: 2, transacao: snap.docs.map((d) => ({ ...d.data(), id: Number(d.id) })) };
      const r = backupSchema.safeParse(bruto);
      return { transacoes: r.success ? normalizarBackup(r.data, Date.now()).transacoes : [] };
    })().catch((e: unknown) => {
      this.ocultos = null;
      throw e;
    });
    return this.ocultos;
  }

  private operacoes(diffs: CollectionDiff[]): Operacao[] {
    const agora = Date.now();
    const ops: Operacao[] = [];
    for (const d of diffs) {
      for (const item of d.upserts) ops.push({ tipo: "set", colecao: d.colecao, id: item.id, dados: paraDocumento(item, agora) });
      for (const id of d.deletes) ops.push({ tipo: "delete", colecao: d.colecao, id });
    }
    return ops;
  }

  private async executar(ops: Operacao[]): Promise<void> {
    for (let i = 0; i < ops.length; i += LIMITE_BATCH) {
      const batch = writeBatch(this.db);
      for (const op of ops.slice(i, i + LIMITE_BATCH)) {
        const ref = doc(this.db, "users", this.uid, op.colecao, String(op.id));
        if (op.tipo === "set") batch.set(ref, op.dados);
        else batch.delete(ref);
      }
      await batch.commit();
    }
    if (ops.length > 0) this.sinal.pedir();
  }

  /** R86 - avisa (debounce) que houve gravação: a Cloud Function acorda o app. Melhor esforço; sem a Function nada muda. */
  private readonly sinal = new AgendadorSinal(() => setDoc(doc(this.db, "users", this.uid, "sinais", "portal"), { em: Date.now() }));

  async save(prev: Dataset, next: Dataset): Promise<void> {
    await this.executar(this.operacoes(diffDataset(prev, next)));
  }

  async replaceAll(prev: Dataset, next: Dataset): Promise<void> {
    const gravar = diffDataset(emptyDataset(), next);
    const apagar = diffDataset(prev, next).map((d) => ({ ...d, upserts: [] }));
    // R72 - o que existe no servidor nas coleções sob demanda e não está no novo conteúdo também sai (restauração = substituição).
    const sobra: CollectionDiff[] = [];
    for (const nome of COLECOES_SOB_DEMANDA) {
      const idsNovos = new Set((next[nome] as readonly Entity[]).map((i) => i.id));
      const snap = await getDocs(this.col(nome));
      const deletes = snap.docs.map((d) => Number(d.id)).filter((id) => !idsNovos.has(id));
      if (deletes.length > 0) sobra.push({ colecao: nome, upserts: [], deletes });
    }
    this.ocultos = null;
    await this.executar(this.operacoes([...gravar, ...apagar, ...sobra]));
  }

  async clear(): Promise<void> {
    // Lê o servidor para apagar também documentos que o cliente não conheça.
    const ops: Operacao[] = [];
    for (const nome of COLLECTIONS) {
      const snap = await getDocs(this.col(nome));
      for (const d of snap.docs) ops.push({ tipo: "delete", colecao: nome, id: Number(d.id) });
    }
    await this.executar(ops);
  }
}
