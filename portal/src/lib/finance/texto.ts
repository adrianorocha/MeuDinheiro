/** Normalização de texto para buscas/filtros: minúsculas, sem acento, espaços colapsados. */
export function normalizar(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

/** R21: minúsculas, sem acento, sem dígitos/pontuação. Devolve a descrição limpa. */
export function limparDescricao(s: string): string {
  return normalizar(s)
    .replace(/[^a-z\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** R21: tokens de ≥ 3 letras. */
export function tokensDescricao(s: string): string[] {
  return limparDescricao(s)
    .split(" ")
    .filter((t) => t.length >= 3);
}

export function jaccard(a: readonly string[], b: readonly string[]): number {
  const A = new Set(a);
  const B = new Set(b);
  if (A.size === 0 && B.size === 0) return 0;
  let inter = 0;
  for (const t of A) if (B.has(t)) inter++;
  return inter / (A.size + B.size - inter);
}
