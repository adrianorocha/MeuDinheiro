import { csvCelula, formatData } from "./format";
import type { Despesa } from "./finance/types";

const CABECALHO = ["Data", "Descrição", "Categoria", "Conta", "Tipo", "Valor", "Pago", "Natureza", "Moeda original", "Valor original", "Cotação"];

/** CSV (separador `;`, decimais com vírgula, BOM UTF-8 para abrir corretamente no Excel). */
export function lancamentosParaCsv(lista: readonly Despesa[], nomeCartao?: (id: number) => string): string {
  const linhas = [CABECALHO.map(csvCelula).join(";")];
  for (const d of lista) {
    const valor = (d.tipo === "DEBITO" ? -d.valor : d.valor).toFixed(2).replace(".", ",");
    linhas.push(
      [
        formatData(d.data),
        d.descricao,
        d.categoria,
        d.cartaoId && nomeCartao ? `Cartão ${nomeCartao(d.cartaoId)}` : d.conta,
        d.tipo === "CREDITO" ? "Receita" : "Despesa",
        valor,
        d.pago ? "Sim" : "Não",
        d.natureza,
        d.moedaOriginal,
        d.valorOriginal.toFixed(2).replace(".", ","),
        String(d.cotacaoNaData).replace(".", ","),
      ]
        .map(csvCelula)
        .join(";"),
    );
  }
  return `﻿${linhas.join("\r\n")}\r\n`;
}
