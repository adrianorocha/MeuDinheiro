/** Geração de ids conforme o contrato (long = Date.now(); int = aleatório em [1e9, 2e9)). */

export function novoIdInt(existentes: Iterable<number>, rng: () => number = Math.random): number {
  const usados = new Set(existentes);
  for (;;) {
    const id = 1_000_000_000 + Math.floor(rng() * 1_000_000_000);
    if (!usados.has(id)) return id;
  }
}

export function novoIdLong(existentes: Iterable<number>, base: number = Date.now()): number {
  const usados = new Set(existentes);
  let id = base;
  while (usados.has(id)) id += 1;
  return id;
}

export function novoUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}
