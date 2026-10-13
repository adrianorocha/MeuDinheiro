/** Formatação determinística de centavos em pt-BR ("R$ 1.234,56"), idêntica ao app (domain/FormatoCentavos.kt). */
export function brlCentavos(cents: number): string {
  const neg = cents < 0;
  const abs = Math.abs(Math.round(cents));
  const inteiro = Math.floor(abs / 100);
  const frac = abs % 100;
  const milhar = String(inteiro).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${neg ? "-" : ""}R$ ${milhar},${String(frac).padStart(2, "0")}`;
}

/** Décimos de ponto percentual -> "12,5". */
export function decimosParaTexto(decimos: number): string {
  const abs = Math.abs(decimos);
  return `${Math.floor(abs / 10)},${abs % 10}`;
}

/** Percentual em décimos (meio para cima) de `num/den`; den <= 0 -> null. */
export function decimosDe(num: number, den: number): number | null {
  if (!(den > 0)) return null;
  const r = Math.round((num * 1000) / den);
  return r === 0 ? 0 : r; // evita -0
}
