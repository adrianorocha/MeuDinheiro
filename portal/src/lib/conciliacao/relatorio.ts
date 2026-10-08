import { fromCents, toCents } from "../finance/money";
import type { Dataset } from "../finance/types";
import { diferencaSaldo, type DiferencaSaldo } from "./saldo";
import { lancamentosDoDestino } from "./matching";
import type { ArquivoExtrato, ClasseMatch, Destino, ResultadoMatching } from "./tipos";

export interface ResumoConciliacao {
  contagens: Record<ClasseMatch | "SO_NO_APP", number>;
  extratoEntradas: number;
  extratoSaidas: number;
  appEntradas: number;
  appSaidas: number;
  saldo: DiferencaSaldo | null;
}

/** R39 - resumo: contagens por classe, entradas/saídas do extrato × do app no período e diferença de saldo. */
export function resumoConciliacao(
  resultado: ResultadoMatching,
  ds: Pick<Dataset, "despesas" | "cartoes">,
  destino: Destino,
  arquivo: Pick<ArquivoExtrato, "saldoFinal" | "periodo">,
): ResumoConciliacao {
  const contagens: ResumoConciliacao["contagens"] = { AUTOMATICO: 0, SUGERIDO: 0, SO_NO_EXTRATO: 0, DUPLICADO: 0, SO_NO_APP: resultado.soNoApp.length };
  let eIn = 0;
  let eOut = 0;
  for (const it of resultado.itens) {
    contagens[it.classe]++;
    const c = toCents(it.transacao.valor);
    if (c > 0) eIn += c;
    else eOut += -c;
  }
  let aIn = 0;
  let aOut = 0;
  const jp = resultado.janelaPeriodo;
  if (jp && arquivo.periodo) {
    for (const d of lancamentosDoDestino(ds, destino)) {
      if (d.data < arquivo.periodo.inicio - 43_200_000 || d.data > arquivo.periodo.fim + 43_200_000) continue;
      const c = toCents(d.valor);
      if (d.tipo === "CREDITO") aIn += c;
      else aOut += c;
    }
  }
  return {
    contagens,
    extratoEntradas: fromCents(eIn),
    extratoSaidas: fromCents(eOut),
    appEntradas: fromCents(aIn),
    appSaidas: fromCents(aOut),
    saldo: arquivo.saldoFinal ? diferencaSaldo(ds, destino, arquivo.saldoFinal) : null,
  };
}

const SITUACAO: Record<ClasseMatch, string> = {
  AUTOMATICO: "automatico",
  SUGERIDO: "sugerido",
  SO_NO_EXTRATO: "so_no_extrato",
  DUPLICADO: "duplicado",
};

function celula(v: string): string {
  return /[";\n\r]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
}

function dataBr(ms: number): string {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`;
}

/**
 * R39 - CSV (`;`, BOM): uma linha por transação do extrato:
 * `data;descricao;valor;situacao;lancamento_id;acao`. `acoes` mapeia o índice da transação ao que foi feito.
 */
export function csvRelatorio(resultado: ResultadoMatching, acoes: ReadonlyMap<number, { acao: string; lancamentoId?: number }> = new Map()): string {
  const linhas = ["data;descricao;valor;situacao;lancamento_id;acao"];
  for (const it of resultado.itens) {
    const a = acoes.get(it.indice);
    const id = a?.lancamentoId ?? it.lancamentoId;
    linhas.push(
      [dataBr(it.transacao.data), celula(it.transacao.descricao), it.transacao.valor.toFixed(2).replace(".", ","), SITUACAO[it.classe], id !== undefined ? String(id) : "", a?.acao ?? "nenhuma"].join(";"),
    );
  }
  for (const d of resultado.soNoApp) {
    linhas.push([dataBr(d.data), celula(d.descricao), (d.tipo === "DEBITO" ? -d.valor : d.valor).toFixed(2).replace(".", ","), "so_no_app", String(d.id), "nenhuma"].join(";"));
  }
  return `﻿${linhas.join("\r\n")}\r\n`;
}

/** Linha mínima para o relatório consolidado do lote (R43). */
export interface LinhaConsolidada {
  data: number;
  descricao: string;
  valor: number;
  classe: ClasseMatch;
  acao: string;
  lancamentoId?: number;
}

/**
 * R43 - CSV consolidado (`;`, BOM): uma linha por transação de cada arquivo, com a coluna `arquivo` primeiro:
 * `arquivo;data;descricao;valor;situacao;lancamento_id;acao`. Arquivos com erro/cancelados saem com `acao` informada.
 */
export function csvRelatorioConsolidado(arquivos: readonly { nome: string; linhas: readonly LinhaConsolidada[] }[]): string {
  const linhas = ["arquivo;data;descricao;valor;situacao;lancamento_id;acao"];
  for (const a of arquivos) {
    for (const l of a.linhas) {
      linhas.push(
        [celula(a.nome), dataBr(l.data), celula(l.descricao), l.valor.toFixed(2).replace(".", ","), SITUACAO[l.classe], l.lancamentoId !== undefined ? String(l.lancamentoId) : "", l.acao].join(";"),
      );
    }
  }
  return `﻿${linhas.join("\r\n")}\r\n`;
}
