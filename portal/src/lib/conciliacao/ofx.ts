import { atribuirFitids } from "./texto-banco";
import type { ArquivoExtrato, SaldoArquivo } from "./tipos";

/** Verdadeiro se o texto parece um OFX (v1 SGML ou v2 XML). */
export function pareceOfx(texto: string): boolean {
  const ini = texto.slice(0, 2000).toUpperCase();
  return ini.includes("OFXHEADER") || ini.includes("<OFX") || /<STMTTRN>/i.test(texto);
}

function decodificarEntidades(s: string): string {
  return s
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&apos;/gi, "'")
    .replace(/&#(\d+);/g, (_m, n: string) => String.fromCharCode(Number(n)))
    .replace(/&amp;/gi, "&");
}

/** Valor da primeira tag `<NOME>valor` (v1 sem fechamento ou v2 com fechamento). */
function campo(bloco: string, nome: string): string | undefined {
  const m = new RegExp(`<${nome}>\\s*([^<\\r\\n]*)`, "i").exec(bloco);
  if (!m) return undefined;
  const v = decodificarEntidades(m[1]).trim();
  return v === "" ? undefined : v;
}

/** `AAAAMMDD[HHMMSS[.XXX][fuso]]` -> meio-dia LOCAL do dia (o fuso nunca desloca o dia). */
export function dataOfx(texto: string): number | null {
  const m = /^\s*(\d{4})(\d{2})(\d{2})/.exec(texto);
  if (!m) return null;
  const [a, me, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const dt = new Date(a, me - 1, d, 12, 0, 0, 0);
  return dt.getFullYear() === a && dt.getMonth() === me - 1 && dt.getDate() === d ? dt.getTime() : null;
}

function valorOfx(s: string): number | null {
  let t = s.replace(/\s/g, "");
  if (t.includes(",") && !t.includes(".")) t = t.replace(",", ".");
  else if (t.includes(",") && t.includes(".")) t = t.lastIndexOf(",") > t.lastIndexOf(".") ? t.replace(/\./g, "").replace(",", ".") : t.replace(/,/g, "");
  const n = Number(t);
  return Number.isFinite(n) ? n : null;
}

/** Une NAME e MEMO sem repetir o mesmo texto. */
function descricaoOfx(name?: string, memo?: string): string {
  const n = name?.trim() ?? "";
  const m = memo?.trim() ?? "";
  if (n && m && n.toLowerCase() !== m.toLowerCase()) {
    return m.toLowerCase().includes(n.toLowerCase()) ? m : n.toLowerCase().includes(m.toLowerCase()) ? n : `${n} ${m}`;
  }
  return n || m || "Sem descrição";
}

function periodoDe(datas: number[]): { inicio: number; fim: number } | null {
  if (datas.length === 0) return null;
  let inicio = Infinity;
  let fim = -Infinity;
  for (const d of datas) {
    if (d < inicio) inicio = d;
    if (d > fim) fim = d;
  }
  return { inicio, fim };
}

export function parseOfx(texto: string): ArquivoExtrato {
  const avisos: string[] = [];
  const blocos: string[] = [];
  const re = /<STMTTRN>([\s\S]*?)(?=<\/STMTTRN>|<STMTTRN>|<\/BANKTRANLIST>|<\/CCSTMTRS>|<\/STMTRS>|$)/gi;
  for (let m = re.exec(texto); m !== null; m = re.exec(texto)) blocos.push(m[1]);

  const linhas: { fitid?: string; data: number; valor: number; descricao: string }[] = [];
  for (const b of blocos) {
    const data = dataOfx(campo(b, "DTPOSTED") ?? "");
    const valor = valorOfx(campo(b, "TRNAMT") ?? "");
    if (data === null || valor === null) {
      avisos.push("Uma transação sem data ou valor válido foi ignorada.");
      continue;
    }
    linhas.push({ fitid: campo(b, "FITID"), data, valor, descricao: descricaoOfx(campo(b, "NAME"), campo(b, "MEMO")) });
  }

  const ehCartao = /<CREDITCARDMSGSRSV1>|<CCSTMTRS>|<CCACCTFROM>/i.test(texto);
  const conta = /<(?:BANKACCTFROM|CCACCTFROM)>([\s\S]*?)(?:<\/(?:BANKACCTFROM|CCACCTFROM)>|<BANKTRANLIST>|<DTSTART>|$)/i.exec(texto);
  const acctId = conta ? campo(conta[1], "ACCTID") : campo(texto, "ACCTID");

  let saldoFinal: SaldoArquivo | undefined;
  const led = /<LEDGERBAL>([\s\S]*?)(?:<\/LEDGERBAL>|<AVAILBAL>|<\/STMTRS>|<\/CCSTMTRS>|$)/i.exec(texto);
  if (led) {
    const v = valorOfx(campo(led[1], "BALAMT") ?? "");
    const d = dataOfx(campo(led[1], "DTASOF") ?? "");
    if (v !== null && d !== null) saldoFinal = { valor: v, data: d };
  }

  const transacoes = atribuirFitids(linhas);
  return {
    formato: "OFX",
    transacoes,
    acctId,
    ehCartao,
    saldoFinal,
    periodo: periodoDe(transacoes.map((t) => t.data)),
    avisos,
  };
}
