/**
 * R63 - funções puras da importação de arquivos: validação, hash do conteúdo (arquivo já processado),
 * registro de arquivos processados e detecção de arquivos novos de uma pasta. Nada aqui envia dados a servidores.
 */

export const MAX_BYTES_EXTRATO = 25 * 1024 * 1024;
export const EXTENSOES_EXTRATO = [".ofx", ".qfx", ".csv", ".txt"] as const;

/** Extensões listadas ao varrer uma pasta (PDF não é lido pelo portal, então não entra na lista automática). */
export const EXTENSOES_PASTA = [".ofx", ".qfx", ".csv"] as const;

const extensaoDe = (nome: string): string => {
  const i = nome.lastIndexOf(".");
  return i < 0 ? "" : nome.slice(i).toLowerCase();
};

export type ValidacaoArquivo = { ok: true } | { ok: false; motivo: string };

/** Valida nome/tamanho/tipo MIME. O tipo é só uma dica (navegadores variam); a extensão decide. */
export function validarArquivoExtrato(a: { nome: string; tamanho: number; tipo?: string }, max = MAX_BYTES_EXTRATO): ValidacaoArquivo {
  const ext = extensaoDe(a.nome);
  if (ext === ".pdf") return { ok: false, motivo: `${a.nome}: PDF ainda não é lido. Exporte o extrato em OFX ou CSV no app do banco.` };
  if (!(EXTENSOES_EXTRATO as readonly string[]).includes(ext)) return { ok: false, motivo: `${a.nome}: formato não suportado (use OFX, QFX ou CSV).` };
  if (a.tamanho <= 0) return { ok: false, motivo: `${a.nome}: arquivo vazio.` };
  if (a.tamanho > max) return { ok: false, motivo: `${a.nome}: arquivo grande demais (máx. ${Math.round(max / 1024 / 1024)} MB).` };
  const tipo = (a.tipo ?? "").toLowerCase();
  if (tipo && /^(image|video|audio)\//.test(tipo)) return { ok: false, motivo: `${a.nome}: tipo de arquivo inesperado (${tipo}).` };
  return { ok: true };
}

/** SHA-256 (hex) do conteúdo, via Web Crypto. */
export async function hashArquivo(bytes: Uint8Array): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", bytes.slice().buffer);
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export interface RegistroArquivo {
  nome: string;
  /** epoch ms em que foi processado */
  em: number;
}
export type MapaHashes = Record<string, RegistroArquivo>;

const MAX_REGISTROS = 500;

export function jaProcessado(mapa: MapaHashes, hash: string): RegistroArquivo | null {
  return mapa[hash] ?? null;
}

/** Devolve um novo mapa com o arquivo registrado, mantendo só os `MAX_REGISTROS` mais recentes. */
export function registrarHash(mapa: MapaHashes, hash: string, nome: string, em: number): MapaHashes {
  const novo: MapaHashes = { ...mapa, [hash]: { nome, em } };
  const chaves = Object.keys(novo);
  if (chaves.length <= MAX_REGISTROS) return novo;
  chaves.sort((a, b) => novo[b].em - novo[a].em);
  return Object.fromEntries(chaves.slice(0, MAX_REGISTROS).map((k) => [k, novo[k]]));
}

const CHAVE_HASHES = "meudinheiro:extratos-processados";

export function lerHashes(ler: (k: string) => string | null): MapaHashes {
  try {
    const o = JSON.parse(ler(CHAVE_HASHES) ?? "{}") as unknown;
    if (typeof o !== "object" || o === null || Array.isArray(o)) return {};
    const r: MapaHashes = {};
    for (const [k, v] of Object.entries(o as Record<string, Partial<RegistroArquivo>>)) if (/^[0-9a-f]{64}$/.test(k) && typeof v?.em === "number" && typeof v.nome === "string") r[k] = { nome: v.nome, em: v.em };
    return r;
  } catch {
    return {};
  }
}

export function gravarHashes(gravar: (k: string, v: string | null) => void, mapa: MapaHashes): void {
  gravar(CHAVE_HASHES, JSON.stringify(mapa));
}

// ---------------------------------------------------------------- pasta monitorada

export interface ArquivoDePasta {
  nome: string;
  tamanho: number;
  /** lastModified (epoch ms) */
  modificado: number;
}

/** Carimbo estável de um arquivo da pasta: nome + tamanho + lastModified. */
export const carimboArquivo = (a: ArquivoDePasta): string => `${a.nome}|${a.tamanho}|${a.modificado}`;

export function ehExtratoDePasta(nome: string): boolean {
  return (EXTENSOES_PASTA as readonly string[]).includes(extensaoDe(nome));
}

/** Arquivos .ofx/.qfx/.csv cujo carimbo ainda não foi visto (ordem: mais antigos primeiro). */
export function arquivosNovos(arquivos: ArquivoDePasta[], vistos: ReadonlySet<string>): ArquivoDePasta[] {
  return arquivos
    .filter((a) => ehExtratoDePasta(a.nome) && a.tamanho > 0 && a.tamanho <= MAX_BYTES_EXTRATO && !vistos.has(carimboArquivo(a)))
    .sort((x, y) => x.modificado - y.modificado || x.nome.localeCompare(y.nome));
}
