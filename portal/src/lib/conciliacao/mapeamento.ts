import { ler, gravar } from "../session";
import type { ConfigCsv } from "./csv";

const PREFIXO = "meudinheiro:csv-map:";

/** Lembra o mapeamento de colunas por "formato" (assinatura do cabeçalho), somente neste navegador. */
export function lembrarMapeamento(assinatura: string, config: ConfigCsv): void {
  gravar(PREFIXO + assinatura, JSON.stringify(config));
}

export function recuperarMapeamento(assinatura: string, colunas: number): ConfigCsv | null {
  const raw = ler(PREFIXO + assinatura);
  if (!raw) return null;
  try {
    const c = JSON.parse(raw) as Partial<ConfigCsv>;
    const indices = [c.colData, c.colDescricao, c.colValor, c.colDebito, c.colCredito, c.colSaldo];
    const valido =
      typeof c.colData === "number" &&
      typeof c.colDescricao === "number" &&
      indices.every((i) => i === null || i === undefined || (Number.isInteger(i) && i >= 0 && i < colunas)) &&
      (c.delimitador === ";" || c.delimitador === "," || c.delimitador === "\t") &&
      (c.decimal === "," || c.decimal === ".");
    return valido ? ({ colValor: null, colDebito: null, colCredito: null, colSaldo: null, inverter: false, temCabecalho: true, ...c } as ConfigCsv) : null;
  } catch {
    return null;
  }
}
