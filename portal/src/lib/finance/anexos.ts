/**
 * R60 - Anexos (fotos/comprovantes) de lançamentos. Funções puras; espelham `domain/Anexos.kt` (mesmos cenários nos testes
 * `anexos.test.ts` e `AnexosTest`). Limites: até 5 por lançamento, 5 MB cada, só JPEG, PNG e PDF.
 */

export interface Anexo {
  /** uuid */
  id: string;
  nome: string;
  /** image/jpeg | image/png | application/pdf */
  tipo: string;
  /** bytes */
  tamanho: number;
  /** `usuarios/<uid>/anexos/<despesaId>/<id>.<ext>` (Storage) ou `local:…` (arquivo só no aparelho do app) */
  caminho: string;
  /** epoch ms */
  criadoEm: number;
}

export const MAX_ANEXOS = 5;
export const MAX_BYTES_ANEXO = 5 * 1024 * 1024;
export const TIPOS_ANEXO = ["image/jpeg", "image/png", "application/pdf"] as const;
export const PREFIXO_LOCAL = "local:";
const NOME_MAX = 120;

const EXTENSOES: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "application/pdf": "pdf" };

export function extensaoAnexo(tipo: string): string | null {
  return EXTENSOES[tipo.toLowerCase()] ?? null;
}

export const anexoEhImagem = (tipo: string) => tipo.startsWith("image/");
export const anexoEhLocal = (caminho: string) => caminho.startsWith(PREFIXO_LOCAL);

/** "512 B", "340 KB", "1,2 MB". */
export function formatarTamanho(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.floor(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}

/** Mensagem de erro (pt-BR) ou `null` se o arquivo pode ser anexado. [quantidadeAtual] = anexos que o lançamento já tem. */
export function validarNovoAnexo(tipo: string, tamanho: number, quantidadeAtual: number): string | null {
  if (quantidadeAtual >= MAX_ANEXOS) return `Limite de ${MAX_ANEXOS} anexos por lançamento.`;
  if (extensaoAnexo(tipo) === null) return "Tipo de arquivo não permitido. Use JPEG, PNG ou PDF.";
  if (tamanho <= 0) return "O arquivo está vazio.";
  if (tamanho > MAX_BYTES_ANEXO) return `Arquivo muito grande (${formatarTamanho(tamanho)}). O limite é 5 MB.`;
  return null;
}

/** Caminho no Storage: `usuarios/<uid>/anexos/<despesaId>/<id>.<ext>`; `null` para tipo inválido ou parte vazia/com "/". */
export function caminhoStorage(uid: string, despesaId: number, id: string, tipo: string): string | null {
  const ext = extensaoAnexo(tipo);
  if (!ext || !uid || !id || uid.includes("/") || id.includes("/")) return null;
  return `usuarios/${uid}/anexos/${despesaId}/${id}.${ext}`;
}

const RE_STORAGE = /^usuarios\/[^/]+\/anexos\/[^/]+\/[^/]+\.(jpg|png|pdf)$/;
const RE_LOCAL = /^local:anexos\/[^/]+\/[^/]+\.(jpg|png|pdf)$/;
export const caminhoAnexoValido = (c: string) => RE_STORAGE.test(c) || RE_LOCAL.test(c);

/** Nome exibido: sem barras, espaços colapsados, até 120 caracteres; vazio vira "anexo". */
export function nomeAnexoSeguro(nome: string | null | undefined): string {
  const limpo = (nome ?? "").replace(/[\\/]/g, "_").replace(/\s+/g, " ").trim().slice(0, NOME_MAX);
  return limpo || "anexo";
}

/**
 * Saneia a lista vinda de dados antigos/de fora: descarta itens sem id, com tipo ou caminho inválido, repetidos
 * (mesmo id; vale o primeiro) e o que passar de 5. Ausente/nulo = lista vazia.
 */
export function normalizarAnexos(entrada: unknown): Anexo[] {
  if (!Array.isArray(entrada)) return [];
  const vistos = new Set<string>();
  const saida: Anexo[] = [];
  for (const bruto of entrada) {
    if (!bruto || typeof bruto !== "object") continue;
    const a = bruto as Record<string, unknown>;
    const id = typeof a.id === "string" ? a.id.trim() : "";
    const tipo = typeof a.tipo === "string" ? a.tipo.toLowerCase() : "";
    const caminho = typeof a.caminho === "string" ? a.caminho : "";
    if (!id || extensaoAnexo(tipo) === null || !caminhoAnexoValido(caminho) || vistos.has(id)) continue;
    vistos.add(id);
    const tamanho = typeof a.tamanho === "number" && Number.isFinite(a.tamanho) ? Math.max(0, Math.trunc(a.tamanho)) : 0;
    const criadoEm = typeof a.criadoEm === "number" && Number.isFinite(a.criadoEm) ? Math.trunc(a.criadoEm) : 0;
    saida.push({ id, nome: nomeAnexoSeguro(typeof a.nome === "string" ? a.nome : null), tipo, tamanho, caminho, criadoEm });
    if (saida.length >= MAX_ANEXOS) break;
  }
  return saida;
}

/** Anexos de um item da lixeira (payload JSON do documento); lista vazia se ilegível. */
export function anexosDoPayload(payload: string): Anexo[] {
  try {
    const d = JSON.parse(payload) as { anexos?: unknown };
    return normalizarAnexos(d.anexos);
  } catch {
    return [];
  }
}
