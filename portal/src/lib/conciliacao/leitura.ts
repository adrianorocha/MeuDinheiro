import { analisarCsv, parseCsv } from "./csv";
import { recuperarMapeamento } from "./mapeamento";
import { parseOfx, pareceOfx } from "./ofx";
import type { ArquivoExtrato } from "./tipos";

export type LeituraTexto =
  | { estado: "PRONTO"; formato: "OFX" | "CSV"; extrato: ArquivoExtrato }
  | { estado: "REVISAR"; formato: "CSV" }
  | { estado: "ERRO"; erro: string };

/**
 * R43 - interpreta o texto de um extrato para a fila de lote. OFX: direto. CSV: usa o mapeamento lembrado
 * (ou o detectado) se ele produzir transações; senão pede para mapear as colunas (`REVISAR`).
 */
export function interpretarTexto(texto: string): LeituraTexto {
  if (pareceOfx(texto)) {
    const extrato = parseOfx(texto);
    return extrato.transacoes.length > 0 ? { estado: "PRONTO", formato: "OFX", extrato } : { estado: "ERRO", erro: "Nenhuma transação encontrada. Confira se o arquivo é um extrato OFX válido." };
  }
  const analise = analisarCsv(texto);
  if (analise.colunas === 0) return { estado: "ERRO", erro: "Arquivo vazio ou em formato não reconhecido." };
  const lembrado = recuperarMapeamento(analise.assinatura, analise.colunas);
  for (const config of lembrado ? [lembrado, analise.config] : [analise.config]) {
    const extrato = parseCsv(texto, config);
    if (extrato.transacoes.length > 0) return { estado: "PRONTO", formato: "CSV", extrato };
  }
  return { estado: "REVISAR", formato: "CSV" };
}
