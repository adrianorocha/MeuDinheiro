import { normalizar } from "../finance/texto";
import { atribuirFitids } from "./texto-banco";
import type { ArquivoExtrato } from "./tipos";

export type Delimitador = ";" | "," | "\t";

export interface ConfigCsv {
  delimitador: Delimitador;
  /** separador decimal dos valores */
  decimal: "," | ".";
  temCabecalho: boolean;
  colData: number;
  colDescricao: number;
  /** valor único com sinal (ou débito+crédito) */
  colValor: number | null;
  colDebito: number | null;
  colCredito: number | null;
  colSaldo: number | null;
  /** CSV de fatura de cartão costuma trazer compra positiva */
  inverter: boolean;
}

export interface AnaliseCsv {
  config: ConfigCsv;
  linhas: string[][];
  cabecalho: string[];
  /** identifica o "formato do banco" para lembrar o mapeamento */
  assinatura: string;
  colunas: number;
}

/** Quebra o texto em células respeitando aspas (inclusive quebras de linha dentro de aspas). */
export function tokenizarCsv(texto: string, delim: Delimitador): string[][] {
  const out: string[][] = [];
  let linha: string[] = [];
  let cel = "";
  let aspas = false;
  const fecharCelula = () => {
    linha.push(cel.trim());
    cel = "";
  };
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (aspas) {
      if (c === '"') {
        if (texto[i + 1] === '"') {
          cel += '"';
          i++;
        } else aspas = false;
      } else cel += c;
    } else if (c === '"') aspas = true;
    else if (c === delim) fecharCelula();
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && texto[i + 1] === "\n") i++;
      fecharCelula();
      if (linha.some((x) => x !== "")) out.push(linha);
      linha = [];
    } else cel += c;
  }
  fecharCelula();
  if (linha.some((x) => x !== "")) out.push(linha);
  return out;
}

/** Descarta preâmbulo (títulos do banco) antes da primeira linha com o número de colunas predominante. */
export function linhasUteis(texto: string, delim: Delimitador): string[][] {
  const todas = tokenizarCsv(texto, delim);
  const cont = new Map<number, number>();
  for (const l of todas.slice(0, 60)) if (l.length >= 2) cont.set(l.length, (cont.get(l.length) ?? 0) + 1);
  let moda = 0;
  let max = 0;
  for (const [n, c] of cont) if (c > max || (c === max && n > moda)) [moda, max] = [n, c];
  if (moda < 2) return todas;
  const i = todas.findIndex((l) => l.length === moda);
  return i > 0 ? todas.slice(i) : todas;
}

export function detectarDelimitador(texto: string): Delimitador {
  const amostra = texto.split(/\r?\n/).filter((l) => l.trim() !== "").slice(0, 15);
  let melhor: Delimitador = ";";
  let melhorPontos = -1;
  for (const d of [";", "\t", ","] as const) {
    const contagens = amostra.map((l) => tokenizarCsv(l, d)[0]?.length ?? 1);
    const moda = [...contagens].sort((a, b) => contagens.filter((x) => x === b).length - contagens.filter((x) => x === a).length)[0] ?? 1;
    if (moda < 2) continue;
    const consistentes = contagens.filter((n) => n === moda).length;
    const pontos = consistentes * 100 + moda;
    if (pontos > melhorPontos) {
      melhorPontos = pontos;
      melhor = d;
    }
  }
  return melhor;
}

/** dd/MM/aaaa, dd/MM/aa, dd-MM-aaaa, aaaa-MM-dd (com ou sem hora) -> meio-dia local; inválida -> null. */
export function parseDataCsv(s: string): number | null {
  const t = s.trim();
  let a: number;
  let m: number;
  let d: number;
  let r = /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[T\s].*)?$/.exec(t);
  if (r) {
    [a, m, d] = [Number(r[1]), Number(r[2]), Number(r[3])];
  } else {
    r = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2}|\d{4})(?:[T\s].*)?$/.exec(t);
    if (!r) return null;
    d = Number(r[1]);
    m = Number(r[2]);
    a = Number(r[3]);
    if (r[3].length === 2) a += a >= 70 ? 1900 : 2000;
  }
  const dt = new Date(a, m - 1, d, 12, 0, 0, 0);
  return dt.getFullYear() === a && dt.getMonth() === m - 1 && dt.getDate() === d ? dt.getTime() : null;
}

const RE_NUM = /^[-+(]?\s*(?:R\$)?\s*[-+]?\s*\d[\d.,]*\)?\s*-?$/;

export function pareceNumero(s: string): boolean {
  return RE_NUM.test(s.trim().replace(/\s/g, ""));
}

/** Converte "1.234,56", "1,234.56", "-12,5", "(12,50)", "12,50-" e "R$ 5,00" para número. */
export function parseValorCsv(s: string, decimal: "," | "."): number | null {
  let t = s.trim().replace(/R\$/gi, "").replace(/\s/g, "");
  if (!t || !pareceNumero(t)) return null;
  let neg = false;
  if (/^\(.*\)$/.test(t)) {
    neg = true;
    t = t.slice(1, -1);
  }
  if (t.endsWith("-")) {
    neg = true;
    t = t.slice(0, -1);
  }
  if (t.startsWith("-")) {
    neg = !neg;
    t = t.slice(1);
  } else if (t.startsWith("+")) t = t.slice(1);
  t = decimal === "," ? t.replace(/\./g, "").replace(",", ".") : t.replace(/,/g, "");
  const n = Number(t);
  if (!Number.isFinite(n)) return null;
  return neg ? -n : n;
}

export function detectarDecimal(valores: readonly string[]): "," | "." {
  let virgula = 0;
  let ponto = 0;
  for (const v of valores) {
    const t = v.trim().replace(/\s/g, "");
    if (/\d,\d{1,2}\)?-?$/.test(t)) virgula++;
    else if (/\d\.\d{1,2}\)?-?$/.test(t)) ponto++;
    else if (/\d\.\d{3}(?!\d)/.test(t) && !t.includes(",")) virgula++; // 1.234 -> milhar BR
    else if (/\d,\d{3}(?!\d)/.test(t) && !t.includes(".")) ponto++;
  }
  return ponto > virgula ? "." : ",";
}

function mapearPorCabecalho(cab: string[]): Partial<ConfigCsv> {
  const n = cab.map((c) => normalizar(c));
  const usado = new Set<number>();
  const achar = (re: RegExp, evitar?: RegExp): number | null => {
    const i = n.findIndex((c, idx) => !usado.has(idx) && re.test(c) && !(evitar && evitar.test(c)));
    if (i >= 0) usado.add(i);
    return i >= 0 ? i : null;
  };
  const colData = achar(/^(data|date|dt\b)|\bdata\b/);
  const colSaldo = achar(/saldo|balance/);
  const colDebito = achar(/debito|saida|retirada|\bdebit\b/);
  const colCredito = achar(/credito|entrada|deposito|\bcredit\b/);
  const colValor = achar(/^(valor|amount|montante|quantia)|\bvalor\b/);
  const colDescricao = achar(/descri|historico|estabelecimento|memo|detalhe|lancamento|\bnome\b|description/);
  return { colData: colData ?? undefined, colSaldo, colDebito, colCredito, colValor, colDescricao: colDescricao ?? undefined };
}

/** Analisa o CSV: delimitador, cabeçalho, decimal e mapeamento sugerido de colunas. */
export function analisarCsv(texto: string, forcado?: Partial<ConfigCsv>): AnaliseCsv {
  const delimitador = forcado?.delimitador ?? detectarDelimitador(texto);
  const linhas = linhasUteis(texto, delimitador);
  const colunas = Math.max(0, ...linhas.slice(0, 50).map((l) => l.length));
  const primeira = linhas[0] ?? [];
  const temCabecalho = forcado?.temCabecalho ?? (primeira.length > 0 && !primeira.some((c) => parseDataCsv(c) !== null || pareceNumero(c)));
  const corpo = temCabecalho ? linhas.slice(1) : linhas;
  const amostra = corpo.slice(0, 200);

  const col = (i: number) => amostra.map((l) => l[i] ?? "").filter((v) => v !== "");
  const pontosData = Array.from({ length: colunas }, (_x, i) => col(i).filter((v) => parseDataCsv(v) !== null).length);
  const pontosNum = Array.from({ length: colunas }, (_x, i) => col(i).filter((v) => parseDataCsv(v) === null && pareceNumero(v)).length);
  const textoMedio = Array.from({ length: colunas }, (_x, i) => {
    const c = col(i).filter((v) => !pareceNumero(v) && parseDataCsv(v) === null);
    return c.length ? c.reduce((a, v) => a + v.length, 0) / c.length : 0;
  });

  const porNome = temCabecalho ? mapearPorCabecalho(primeira) : {};
  const colData = porNome.colData ?? (Math.max(...pontosData, 0) > 0 ? pontosData.indexOf(Math.max(...pontosData)) : 0);
  const numericas = pontosNum.map((p, i) => ({ p, i })).filter((x) => x.p > 0 && x.i !== colData).map((x) => x.i);
  const colDescricao = porNome.colDescricao ?? (textoMedio.indexOf(Math.max(...textoMedio)) >= 0 ? textoMedio.indexOf(Math.max(...textoMedio)) : 1);

  let colValor = porNome.colValor ?? null;
  const colDebito = porNome.colDebito ?? null;
  const colCredito = porNome.colCredito ?? null;
  let colSaldo = porNome.colSaldo ?? null;
  if (colValor === null && colDebito === null && colCredito === null) {
    colValor = numericas[0] ?? null;
    if (colSaldo === null) colSaldo = numericas[1] ?? null;
  }
  const colsValor = [colValor, colDebito, colCredito].filter((x): x is number => x !== null);
  const decimal = forcado?.decimal ?? detectarDecimal(colsValor.flatMap((c) => col(c)));

  const config: ConfigCsv = {
    delimitador,
    decimal,
    temCabecalho,
    colData,
    colDescricao,
    colValor,
    colDebito,
    colCredito,
    colSaldo,
    inverter: forcado?.inverter ?? false,
  };
  const cabecalho = temCabecalho ? primeira : Array.from({ length: colunas }, (_x, i) => `Coluna ${i + 1}`);
  const assinatura = temCabecalho ? `h:${primeira.map((c) => normalizar(c)).join("|")}` : `n:${colunas}:${delimitador}`;
  return { config, linhas, cabecalho, assinatura, colunas };
}

/** Converte o CSV em transações do extrato com a configuração de colunas. */
export function parseCsv(texto: string, config: ConfigCsv): ArquivoExtrato {
  const linhas = linhasUteis(texto, config.delimitador);
  const corpo = config.temCabecalho ? linhas.slice(1) : linhas;
  const avisos: string[] = [];
  const brutas: { data: number; valor: number; descricao: string; saldo?: number }[] = [];
  let ignoradas = 0;
  for (const l of corpo) {
    const data = parseDataCsv(l[config.colData] ?? "");
    let valor: number | null = null;
    if (config.colValor !== null) valor = parseValorCsv(l[config.colValor] ?? "", config.decimal);
    else {
      const deb = config.colDebito !== null ? parseValorCsv(l[config.colDebito] ?? "", config.decimal) : null;
      const cred = config.colCredito !== null ? parseValorCsv(l[config.colCredito] ?? "", config.decimal) : null;
      if (deb !== null || cred !== null) valor = (cred !== null ? Math.abs(cred) : 0) - (deb !== null ? Math.abs(deb) : 0);
    }
    if (data === null || valor === null || valor === 0) {
      if (l.some((c) => c !== "")) ignoradas++;
      continue;
    }
    const saldo = config.colSaldo !== null ? parseValorCsv(l[config.colSaldo] ?? "", config.decimal) : null;
    brutas.push({
      data,
      valor: config.inverter ? -valor : valor,
      descricao: (l[config.colDescricao] ?? "").trim() || "Sem descrição",
      ...(saldo !== null ? { saldo } : {}),
    });
  }
  if (ignoradas > 0) avisos.push(`${ignoradas} linha(s) sem data ou valor válido foram ignoradas.`);
  const transacoes = atribuirFitids(brutas);

  // saldo final: linha mais recente (arquivo em ordem decrescente => a primeira)
  let saldoFinal: ArquivoExtrato["saldoFinal"];
  const comSaldo = transacoes.filter((t) => t.saldo !== undefined);
  if (comSaldo.length > 0) {
    const decrescente = comSaldo.length > 1 && comSaldo[0].data > comSaldo[comSaldo.length - 1].data;
    const ref = decrescente ? comSaldo[0] : comSaldo[comSaldo.length - 1];
    saldoFinal = { valor: ref.saldo as number, data: ref.data };
  }
  let inicio = Infinity;
  let fim = -Infinity;
  for (const t of transacoes) {
    if (t.data < inicio) inicio = t.data;
    if (t.data > fim) fim = t.data;
  }
  return { formato: "CSV", transacoes, saldoFinal, periodo: transacoes.length ? { inicio, fim } : null, avisos };
}
