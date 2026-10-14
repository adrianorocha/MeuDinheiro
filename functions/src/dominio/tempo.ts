/** Datas no fuso de São Paulo (sem firebase-admin; puro e testável). */
export const FUSO = "America/Sao_Paulo";

export interface DiaCivil {
  a: number;
  m: number;
  d: number;
}

const fmt = new Intl.DateTimeFormat("en-CA", { timeZone: FUSO, year: "numeric", month: "2-digit", day: "2-digit" });

/** Dia civil (ano/mês/dia) do instante [ms] em America/Sao_Paulo. */
export function diaCivil(ms: number): DiaCivil {
  const p: Record<string, string> = {};
  for (const x of fmt.formatToParts(new Date(ms))) p[x.type] = x.value;
  return { a: Number(p.year), m: Number(p.month), d: Number(p.day) };
}

/** Número do dia (dias desde 1970-01-01 no calendário civil): permite comparar/subtrair dias sem se preocupar com o horário. */
export function numeroDoDia(ms: number): number {
  const c = diaCivil(ms);
  return Math.round(Date.UTC(c.a, c.m - 1, c.d) / 86_400_000);
}

/** "AAAA-MM" do mês civil de [ms]. */
export function chaveMes(ms: number): string {
  const c = diaCivil(ms);
  return `${c.a}-${String(c.m).padStart(2, "0")}`;
}

/** Instante (ms) do início do dia civil de número [n] em São Paulo (não assume UTC-3 fixo). */
export function inicioDoDiaMs(n: number): number {
  let ms = n * 86_400_000 + 12 * 3_600_000; // meio-dia UTC cai sempre dentro do dia civil de SP
  while (numeroDoDia(ms - 3_600_000) === n) ms -= 3_600_000;
  while (numeroDoDia(ms - 60_000) === n) ms -= 60_000;
  return ms;
}

/** Início do mês civil `AAAA-MM` e do mês seguinte (ms), para consultas por intervalo. */
export function limitesDoMes(chave: string): { inicio: number; fimExclusivo: number } {
  const [a, m] = chave.split("-").map(Number);
  const dia1 = (ano: number, mes: number) => Math.round(Date.UTC(ano, mes - 1, 1) / 86_400_000);
  return {
    inicio: inicioDoDiaMs(dia1(a, m)),
    fimExclusivo: inicioDoDiaMs(m === 12 ? dia1(a + 1, 1) : dia1(a, m + 1)),
  };
}
