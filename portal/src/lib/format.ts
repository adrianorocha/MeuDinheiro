const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dec = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = new Intl.NumberFormat("pt-BR", { style: "percent", minimumFractionDigits: 0, maximumFractionDigits: 1 });
const data = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
const mesAno = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric" });

export const MASCARA = "R$ ••••••";

export function formatBRL(valor: number): string {
  return brl.format(Number.isFinite(valor) ? valor : 0);
}

export function formatDecimal(valor: number): string {
  return dec.format(Number.isFinite(valor) ? valor : 0);
}

/** `razao` 0.25 -> "25%". */
export function formatPercentual(razao: number): string {
  return pct.format(Number.isFinite(razao) ? razao : 0);
}

export function formatData(ms: number): string {
  return data.format(new Date(ms));
}

export function formatMesAno(mes: number, ano: number): string {
  const t = mesAno.format(new Date(ano, mes - 1, 1));
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** "1.234,56", "1234,56", "1234.56" -> 1234.56; vazio/inválido -> NaN. */
export function parseValorBR(texto: string): number {
  let t = texto.trim().replace(/[R$\s]/g, "");
  if (!t) return Number.NaN;
  if (t.includes(",")) t = t.replace(/\./g, "").replace(",", ".");
  else if ((t.match(/\./g) ?? []).length > 1) t = t.replace(/\./g, "");
  if (!/^-?\d*\.?\d+$/.test(t) && !/^-?\d+\.$/.test(t)) return Number.NaN;
  return Number(t);
}

export function valorParaCampo(valor: number): string {
  return Number.isFinite(valor) ? String(valor).replace(".", ",") : "";
}

/** epoch ms -> "AAAA-MM-DD" (fuso local) para `<input type="date">`. */
export function paraInputData(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** "AAAA-MM-DD" -> epoch ms ao meio-dia local (evita problemas de fuso); inválido -> NaN. */
export function deInputData(texto: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
  if (!m) return Number.NaN;
  const [, a, me, di] = m;
  const d = new Date(Number(a), Number(me) - 1, Number(di), 12, 0, 0, 0);
  return d.getMonth() === Number(me) - 1 ? d.getTime() : Number.NaN;
}

export function csvCelula(valor: string | number | boolean | null): string {
  const t = valor === null ? "" : String(valor);
  return /[";\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
}
