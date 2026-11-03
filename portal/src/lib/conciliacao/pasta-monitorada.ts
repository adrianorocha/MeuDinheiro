import { arquivosNovos, carimboArquivo, type ArquivoDePasta } from "./importacao-arquivos";

/**
 * R63 - pasta monitorada pelo navegador (File System Access API, Chromium). O handle e os carimbos ficam
 * apenas no IndexedDB deste navegador; nenhum arquivo é enviado a servidores.
 * Tipos mínimos locais, porque a lib DOM do TypeScript não cobre toda a API.
 */
export interface PastaHandle {
  name: string;
  kind: "directory";
  queryPermission?: (o: { mode: "read" }) => Promise<"granted" | "denied" | "prompt">;
  requestPermission?: (o: { mode: "read" }) => Promise<"granted" | "denied" | "prompt">;
  values: () => AsyncIterable<{ kind: "file" | "directory"; name: string; getFile?: () => Promise<File> }>;
}

export function pastaSuportada(): boolean {
  return typeof window !== "undefined" && typeof (window as unknown as { showDirectoryPicker?: unknown }).showDirectoryPicker === "function";
}

const BANCO = "meudinheiro-pasta";
const LOJA = "kv";

function abrir(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(BANCO, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(LOJA);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function obter<T>(chave: string): Promise<T | undefined> {
  const db = await abrir();
  return new Promise((resolve, reject) => {
    const r = db.transaction(LOJA, "readonly").objectStore(LOJA).get(chave);
    r.onsuccess = () => resolve(r.result as T | undefined);
    r.onerror = () => reject(r.error);
  });
}

async function gravar(chave: string, valor: unknown): Promise<void> {
  const db = await abrir();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(LOJA, "readwrite");
    if (valor === undefined) tx.objectStore(LOJA).delete(chave);
    else tx.objectStore(LOJA).put(valor, chave);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function escolherPasta(): Promise<PastaHandle | null> {
  try {
    const h = await (window as unknown as { showDirectoryPicker: (o?: { mode: "read" }) => Promise<PastaHandle> }).showDirectoryPicker({ mode: "read" });
    await gravar("handle", h);
    await gravar("vistos", []);
    return h;
  } catch {
    return null; // cancelado pelo usuário
  }
}

export const pastaGuardada = async (): Promise<PastaHandle | null> => ((await obter<PastaHandle>("handle")) ?? null);
export const esquecerPasta = async (): Promise<void> => {
  await gravar("handle", undefined);
  await gravar("vistos", undefined);
};
export const carimbosVistos = async (): Promise<Set<string>> => new Set((await obter<string[]>("vistos")) ?? []);
export async function marcarVistos(arquivos: ArquivoDePasta[]): Promise<void> {
  const atuais = await carimbosVistos();
  for (const a of arquivos) atuais.add(carimboArquivo(a));
  await gravar("vistos", [...atuais].slice(-2000));
}

/** "granted" só se já autorizado; não pede nada (pedir exige gesto do usuário). */
export async function permissaoAtual(h: PastaHandle): Promise<"granted" | "denied" | "prompt"> {
  try {
    return (await h.queryPermission?.({ mode: "read" })) ?? "prompt";
  } catch {
    return "denied";
  }
}

export async function pedirPermissao(h: PastaHandle): Promise<boolean> {
  try {
    return (await h.requestPermission?.({ mode: "read" })) === "granted";
  } catch {
    return false;
  }
}

export interface ItemPasta {
  info: ArquivoDePasta;
  file: File;
}

/** Lista (só o nível da pasta, sem recursão) os arquivos de extrato ainda não vistos. */
export async function varrerPasta(h: PastaHandle): Promise<ItemPasta[]> {
  const vistos = await carimbosVistos();
  const todos: ItemPasta[] = [];
  for await (const e of h.values()) {
    if (e.kind !== "file" || !e.getFile) continue;
    try {
      const file = await e.getFile();
      todos.push({ info: { nome: file.name, tamanho: file.size, modificado: file.lastModified }, file });
    } catch {
      // arquivo sumiu ou sem permissão: ignora
    }
  }
  const novos = arquivosNovos(
    todos.map((t) => t.info),
    vistos,
  );
  const chaves = new Set(novos.map(carimboArquivo));
  return todos.filter((t) => chaves.has(carimboArquivo(t.info))).sort((a, b) => a.info.modificado - b.info.modificado);
}
