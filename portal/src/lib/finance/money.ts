/** R1 – valores monetários em 2 casas (meio para cima, simétrico) e somas em centavos inteiros. */

export function toCents(valor: number): number {
  if (!Number.isFinite(valor)) return 0;
  const sinal = valor < 0 ? -1 : 1;
  // 1e-9 compensa representações binárias como 1.005 -> 1.00499999...
  return sinal * Math.round(Math.abs(valor) * 100 + 1e-9);
}

export function fromCents(cents: number): number {
  return Math.round(cents) / 100;
}

export function round2(valor: number): number {
  return fromCents(toCents(valor));
}

export function somaCents(valores: Iterable<number>): number {
  let total = 0;
  for (const v of valores) total += toCents(v);
  return total;
}

export function soma(valores: Iterable<number>): number {
  return fromCents(somaCents(valores));
}
