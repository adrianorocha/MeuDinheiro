/** Utilitários de data em fuso local. Datas do contrato são epoch ms. */

export function diasNoMes(mes: number, ano: number): number {
  return new Date(ano, mes, 0).getDate();
}

export function clampDia(dia: number, mes: number, ano: number): number {
  return Math.max(1, Math.min(dia, diasNoMes(mes, ano)));
}

export function mesAnoDe(ms: number): { mes: number; ano: number } {
  const d = new Date(ms);
  return { mes: d.getMonth() + 1, ano: d.getFullYear() };
}

/** Normaliza (mes, ano) com mes fora de 1..12 (ex.: 13 -> jan do ano seguinte). */
export function normalizaMes(mes: number, ano: number): { mes: number; ano: number } {
  const idx = ano * 12 + (mes - 1);
  return { mes: (((idx % 12) + 12) % 12) + 1, ano: Math.floor(idx / 12) };
}

export function inicioDoMes(mes: number, ano: number): number {
  return new Date(ano, mes - 1, 1, 0, 0, 0, 0).getTime();
}

export function fimDoMes(mes: number, ano: number): number {
  return new Date(ano, mes, 0, 23, 59, 59, 999).getTime();
}

export function inicioDoDia(ms: number): number {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0).getTime();
}

/**
 * Soma `meses` à data ORIGINAL, limitando o dia ao último do mês (sem deriva:
 * 31/01 +1 = 28/02, +2 = 31/03). Preserva hora/minuto.
 */
export function somaMeses(ms: number, meses: number): number {
  const d = new Date(ms);
  const alvo = normalizaMes(d.getMonth() + 1 + meses, d.getFullYear());
  const dia = clampDia(d.getDate(), alvo.mes, alvo.ano);
  return new Date(
    alvo.ano,
    alvo.mes - 1,
    dia,
    d.getHours(),
    d.getMinutes(),
    d.getSeconds(),
    d.getMilliseconds(),
  ).getTime();
}

export function chaveMes(mes: number, ano: number): string {
  return `${ano}-${String(mes).padStart(2, "0")}`;
}

export const MESES_ABREV = [
  "JAN",
  "FEV",
  "MAR",
  "ABR",
  "MAI",
  "JUN",
  "JUL",
  "AGO",
  "SET",
  "OUT",
  "NOV",
  "DEZ",
] as const;

export const MESES_NOME = [
  "Janeiro",
  "Fevereiro",
  "Março",
  "Abril",
  "Maio",
  "Junho",
  "Julho",
  "Agosto",
  "Setembro",
  "Outubro",
  "Novembro",
  "Dezembro",
] as const;

export function fimDoDia(ms: number): number {
  return inicioDoDia(ms) + 86_399_999;
}
