import { normalizar } from "../finance/texto";
import { toCents } from "../finance/money";
import type { TransacaoBanco } from "./tipos";

/** R35 - decodifica UTF-8; se aparecer U+FFFD, relê como Windows-1252 (bancos brasileiros). */
export function decodificarTexto(bytes: Uint8Array): string {
  const utf8 = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
  if (!utf8.includes("�")) return utf8.replace(/^﻿/, "");
  return new TextDecoder("windows-1252").decode(bytes).replace(/^﻿/, "");
}

const PREFIXOS = [
  /^compra\s+(no\s+)?(debito|credito|cartao)(\s+em)?\b/i,
  /^compra\s+com\s+cartao\b/i,
  /^compra\s+aprovada\b/i,
  /^pix\s+(enviado|recebido|transf(erencia)?)\b/i,
  /^pix\b/i,
  /^transf(erencia)?\s+(enviada|recebida|pix|ted|doc)?\b/i,
  /^ted\b/i,
  /^doc\b/i,
  /^pagto\.?\b/i,
  /^pagamento\s+(de\s+)?(titulo|conta|boleto)?\b/i,
  /^debito\s+(automatico|em\s+conta)?\b/i,
  /^credito\s+(em\s+conta)?\b/i,
  /^saque\b/i,
  /^tarifa\b/i,
  /^rendimento\b/i,
];

/** Remove ruído de descrição bancária e devolve um texto legível (Title Case). */
export function limparDescricaoBanco(bruta: string): string {
  let s = bruta.normalize("NFC").replace(/\s+/g, " ").trim();
  // datas dd/mm(/aa) e horários
  s = s.replace(/(?<!\(\s*)(?<!parc\w*\.?\s*)\b\d{1,2}\/\d{1,2}(\/\d{2,4})?\b(?!\s*\))/gi, " ").replace(/\b\d{1,2}:\d{2}(:\d{2})?\b/g, " ");
  // códigos longos (≥ 5 dígitos), asteriscos e marcadores de parcela mantidos
  s = s.replace(/\b\d{5,}\b/g, " ").replace(/\*+/g, " ");
  for (let i = 0; i < 2; i++) {
    const sem = normalizar(s);
    for (const re of PREFIXOS) {
      const m = re.exec(sem);
      if (m && m[0].length > 0 && m[0].length < sem.length) {
        // remove o mesmo número de caracteres do original (a normalização só tira diacríticos)
        s = s.slice(m[0].length).trim();
        break;
      }
    }
  }
  s = s.replace(/^[\s\-–:.,/]+|[\s\-–:.,/]+$/g, "").replace(/\s+/g, " ");
  if (!s) return bruta.trim();
  return s
    .toLowerCase()
    .replace(/(^|[\s\-/(])(\p{L})/gu, (_m, a: string, b: string) => a + b.toUpperCase())
    .replace(/\b(Do|Da|De|Dos|Das|E)\b/g, (w) => w.toLowerCase())
    .replace(/^./, (c) => c.toUpperCase());
}

/** Descrição normalizada (sem acento/dígitos/pontuação) usada em matching e fitid. */
export function normalizarDescricaoBanco(bruta: string): string {
  return normalizar(limparDescricaoBanco(bruta))
    .replace(/[^a-z\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** Parcela reconhecida no texto ("(2/5)", "PARC 2/5"): informativo, não expande. */
export function detectarParcela(descricao: string): { i: number; n: number } | null {
  const m = /\(\s*(\d{1,2})\s*\/\s*(\d{1,2})\s*\)|\bparc(?:ela)?\.?\s*(\d{1,2})\s*(?:\/|de)\s*(\d{1,2})\b/i.exec(descricao);
  if (!m) return null;
  const i = Number(m[1] ?? m[3]);
  const n = Number(m[2] ?? m[4]);
  return i >= 1 && n >= i ? { i, n } : null;
}

const DIA_MS = 86_400_000;

/**
 * R44 - parcela i/n do extrato de CARTÃO: formas explícitas ("(2/5)", "PARC 2/5") ou o marcador "i/n"
 * solto no texto ("COMPRA 03/10"), este só quando NÃO parece a data da transação (dd/mm a ≤ 10 dias da data do extrato)
 * e com i ≤ n, n ≥ 2.
 */
export function detectarParcelaExtrato(descricao: string, dataMs: number): { i: number; n: number } | null {
  const explicita = detectarParcela(descricao);
  if (explicita) return explicita.n >= 2 ? explicita : null;
  const re = /(?<![\d/])(\d{1,2})\/(\d{1,2})(?![\d/]|:)/g;
  let achado: { i: number; n: number } | null = null;
  for (const m of descricao.matchAll(re)) {
    const i = Number(m[1]);
    const n = Number(m[2]);
    if (!(i >= 1 && n >= 2 && i <= n)) continue;
    if (n <= 12 && i <= 31) {
      const ano = new Date(dataMs).getFullYear();
      const pareceData = [ano - 1, ano, ano + 1].some((a) => Math.abs(new Date(a, n - 1, i, 12).getTime() - dataMs) <= 10 * DIA_MS);
      if (pareceData) continue;
    }
    achado = { i, n };
  }
  return achado;
}

/** Remove marcadores de parcela ("(2/5)", "Parc 2/5") de uma descrição já limpa. */
export function semMarcadorParcela(descricao: string): string {
  return descricao
    .replace(/\(\s*\d{1,2}\s*\/\s*\d{1,2}\s*\)|\bparc(?:ela)?\.?\s*\d{1,2}\s*(?:\/|de)\s*\d{1,2}\b/gi, " ")
    .replace(/\s+/g, " ")
    .replace(/^[\s\-–:.,/]+|[\s\-–:.,/]+$/g, "")
    .trim();
}

function fnv(s: string, seed: number): number {
  let h = seed >>> 0;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

function ymd(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
}

/** R35 - fitid sintético estável: "h:" + hash(data|centavos|descrição normalizada|n). */
export function fitidSintetico(data: number, valor: number, descricao: string, n: number): string {
  const chave = `${ymd(data)}|${toCents(valor)}|${normalizarDescricaoBanco(descricao)}|${n}`;
  return `h:${fnv(chave, 2166136261).toString(16).padStart(8, "0")}${fnv(chave, 5381).toString(16).padStart(8, "0")}`;
}

/**
 * Atribui fitid sintético às transações sem id; `n` é a ordem da ocorrência idêntica no arquivo.
 * FITIDs repetidos dentro do arquivo recebem sufixo "#n" para permanecerem únicos.
 */
export function atribuirFitids(linhas: { fitid?: string; data: number; valor: number; descricao: string; saldo?: number }[]): TransacaoBanco[] {
  const ocorrencias = new Map<string, number>();
  const vistos = new Map<string, number>();
  return linhas.map((l) => {
    let fitid = l.fitid?.trim();
    if (fitid) {
      const k = (vistos.get(fitid) ?? 0) + 1;
      vistos.set(fitid, k);
      if (k > 1) fitid = `${fitid}#${k}`;
    } else {
      const chave = `${ymd(l.data)}|${toCents(l.valor)}|${normalizarDescricaoBanco(l.descricao)}`;
      const n = (ocorrencias.get(chave) ?? 0) + 1;
      ocorrencias.set(chave, n);
      fitid = fitidSintetico(l.data, l.valor, l.descricao, n);
    }
    return { fitid, data: l.data, valor: l.valor, descricao: l.descricao, ...(l.saldo !== undefined ? { saldo: l.saldo } : {}) };
  });
}
