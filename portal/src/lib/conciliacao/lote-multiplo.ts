import type { Dataset } from "../finance/types";
import { casar, janelaPadrao, lancamentosDoDestino } from "./matching";
import { acaoConciliar, aplicarConciliacao, montarPlano, type AcaoPlano, type Plano, type PlanoInverso, type ResumoAplicacao } from "./plano";
import { limparDescricaoBanco } from "./texto-banco";
import type { ArquivoExtrato, ClasseMatch, Destino, ResultadoMatching } from "./tipos";

/** R43 - conciliação de vários arquivos em sequência. Módulo puro: não depende de React nem do store. */

/** Sugerido de "alta confiança": valor exato, score >= este limiar e sem concorrente próximo. */
export const LIMIAR_SUGERIDO_ALTO = 0.7;
const FOLGA_CONCORRENTE = 0.05;

export type EtapaArquivo = "FILA" | "LENDO" | "COMPARANDO" | "APLICANDO" | "CONCLUIDO" | "ERRO" | "CANCELADO";

/** Fração (0..1) do arquivo concluída ao ENTRAR em cada etapa. */
const FRACAO_ETAPA: Record<EtapaArquivo, number> = { FILA: 0, LENDO: 0.1, COMPARANDO: 0.4, APLICANDO: 0.75, CONCLUIDO: 1, ERRO: 1, CANCELADO: 0 };

export function percentualArquivo(etapa: EtapaArquivo): number {
  return Math.round(FRACAO_ETAPA[etapa] * 100);
}

/** Percentual geral (0..100) com `indice` arquivos já terminados (base 0) e o corrente na `etapa`. */
export function percentualGeral(indice: number, total: number, etapa: EtapaArquivo): number {
  if (total <= 0) return 0;
  const bruto = ((indice + FRACAO_ETAPA[etapa]) / total) * 100;
  return Math.max(0, Math.min(100, Math.round(bruto)));
}

export interface ArquivoParaLote {
  id: string;
  nome: string;
  extrato: ArquivoExtrato;
  destino: Destino;
  rotuloDestino?: string;
}

export interface OpcoesLote {
  /** inclui SUGERIDOS de alta confiança (padrão false) */
  sugeridos: boolean;
  /** cria lançamentos que só estão no extrato (padrão false) */
  criar: boolean;
  /** R44 - ao criar parcela i/n de cartão, lança também i+1..n em aberto (reserva o limite). Padrão ligado. */
  parcelasRestantes?: boolean;
  /** janela em dias; undefined = padrão do destino (conta 3, cartão 5) */
  janelaDias?: number;
  /** tolerância relativa (0,02 = 2%) */
  toleranciaPct?: number;
}

export const OPCOES_LOTE_PADRAO: OpcoesLote = { sugeridos: false, criar: false, parcelasRestantes: true };

export interface Contadores {
  transacoes: number;
  conciliados: number;
  criados: number;
  valoresAlterados: number;
  datasAlteradas: number;
  pagosMarcados: number;
  duplicados: number;
  pendentes: number;
  erros: number;
}

export function contadoresVazios(): Contadores {
  return { transacoes: 0, conciliados: 0, criados: 0, valoresAlterados: 0, datasAlteradas: 0, pagosMarcados: 0, duplicados: 0, pendentes: 0, erros: 0 };
}

export function somarContadores(a: Contadores, b: Contadores): Contadores {
  return {
    transacoes: a.transacoes + b.transacoes,
    conciliados: a.conciliados + b.conciliados,
    criados: a.criados + b.criados,
    valoresAlterados: a.valoresAlterados + b.valoresAlterados,
    datasAlteradas: a.datasAlteradas + b.datasAlteradas,
    pagosMarcados: a.pagosMarcados + b.pagosMarcados,
    duplicados: a.duplicados + b.duplicados,
    pendentes: a.pendentes + b.pendentes,
    erros: a.erros + b.erros,
  };
}

/** Taxa (0..1) de transações do extrato resolvidas: conciliadas + criadas + já conciliadas antes (duplicadas). */
export function taxaConciliacao(c: Pick<Contadores, "transacoes" | "conciliados" | "criados" | "duplicados">): number {
  return c.transacoes > 0 ? Math.min(1, (c.conciliados + c.criados + c.duplicados) / c.transacoes) : 0;
}

/** Linha do relatório: o que aconteceu com cada transação do extrato. */
export interface LinhaLote {
  indice: number;
  data: number;
  descricao: string;
  valor: number;
  classe: ClasseMatch;
  acao: "conciliar" | "criar" | "nenhuma";
  lancamentoId?: number;
}

/** Linha do mostrador "o que está sendo atualizado". */
export interface LinhaAtualizacao {
  arquivo: string;
  descricaoBanco: string;
  lancamento: string;
  valor: number;
  data: number;
  acao: "conciliar" | "criar";
  /** o que mudou no lançamento existente */
  mudancas: ("valor" | "data" | "pago")[];
}

export interface ResultadoArquivo {
  id: string;
  nome: string;
  rotuloDestino: string;
  status: "CONCLUIDO" | "ERRO" | "CANCELADO";
  erro?: string;
  contadores: Contadores;
  /** contagem do casamento (antes de aplicar) */
  classes: Record<ClasseMatch, number>;
  resumo: ResumoAplicacao;
  inverso?: PlanoInverso;
  destino: Destino;
  linhas: LinhaLote[];
}

export interface ResultadoLote {
  arquivos: ResultadoArquivo[];
  contadores: Contadores;
  cancelado: boolean;
}

export type EventoLote =
  | { tipo: "inicio"; total: number; pctGeral: number; contadores: Contadores }
  | { tipo: "etapa"; indice: number; total: number; id: string; nome: string; etapa: EtapaArquivo; pctArquivo: number; pctGeral: number; contadores: Contadores }
  | { tipo: "linhas"; indice: number; id: string; linhas: LinhaAtualizacao[]; contadores: Contadores; pctGeral: number }
  | { tipo: "arquivo"; indice: number; total: number; resultado: ResultadoArquivo; contadores: Contadores; pctGeral: number }
  | { tipo: "fim"; resultado: ResultadoLote; pctGeral: number };

type RespostaAplicar = { ok: true; inverso: PlanoInverso; resumo: ResumoAplicacao } | { ok: false; erro: string };

export interface ParametrosLote {
  arquivos: readonly ArquivoParaLote[];
  opcoes?: OpcoesLote;
  /** dataset ATUAL (lido de forma síncrona a cada arquivo) */
  getDs: () => Dataset;
  /** aplica o plano de forma atômica sobre o estado atual */
  aplicar: (plano: Plano) => RespostaAplicar;
  onProgresso?: (e: EventoLote) => void;
  /** consultado entre as etapas; true = parar após o arquivo corrente */
  sinalCancelar?: () => boolean;
  /** cede ao navegador entre etapas (padrão: setTimeout 0) */
  ceder?: () => Promise<void>;
}

const cederPadrao = () => new Promise<void>((r) => setTimeout(r, 0));

function classesVazias(): Record<ClasseMatch, number> {
  return { AUTOMATICO: 0, SUGERIDO: 0, SO_NO_EXTRATO: 0, DUPLICADO: 0 };
}

const RESUMO_VAZIO = (): ResumoAplicacao => ({ conciliados: 0, criados: 0, valoresAlterados: 0, datasAlteradas: 0, pagosMarcados: 0, parcelasRestantes: 0 });

/** Casa o arquivo contra `ds` com as opções do lote (janela/tolerância padrão por destino). */
export function casarArquivo(ds: Dataset, arq: Pick<ArquivoParaLote, "extrato" | "destino">, opcoes: OpcoesLote): ResultadoMatching {
  return casar(arq.extrato.transacoes, lancamentosDoDestino(ds, arq.destino), {
    janelaDias: opcoes.janelaDias ?? janelaPadrao(arq.destino),
    toleranciaPct: opcoes.toleranciaPct,
  });
}

function sugeridoAltaConfianca(it: ResultadoMatching["itens"][number]): boolean {
  if (it.classe !== "SUGERIDO" || it.lancamentoId === undefined || it.tipo !== "EXATO") return false;
  if ((it.score ?? 0) < LIMIAR_SUGERIDO_ALTO) return false;
  const melhorAlt = it.alternativas[0]?.score ?? -1;
  return (it.score ?? 0) > melhorAlt + FOLGA_CONCORRENTE;
}

/** Plano do lote: automáticos (sempre), sugeridos de alta confiança e criações conforme as opções; sem fitid repetido. */
export function planoDoLote(resultado: ResultadoMatching, ds: Dataset, destino: Destino, opcoes: OpcoesLote): Plano {
  const base = montarPlano(resultado, ds, destino, { criar: opcoes.criar, parcelasRestantes: opcoes.parcelasRestantes !== false });
  const acoes: AcaoPlano[] = [...base.acoes];
  if (opcoes.sugeridos) {
    for (const it of resultado.itens) if (sugeridoAltaConfianca(it) && it.lancamentoId !== undefined) acoes.push(acaoConciliar(it, destino, it.lancamentoId));
  }
  const vistos = new Set<string>();
  const unicas = acoes.filter((a) => (vistos.has(a.transacao.fitid) ? false : (vistos.add(a.transacao.fitid), true)));
  return { destino, acoes: unicas };
}

function montarLinhas(resultado: ResultadoMatching, plano: Plano, aplicado: PlanoInverso | null): LinhaLote[] {
  const porFitid = new Map(plano.acoes.map((a) => [a.transacao.fitid, a]));
  const criadosOrdem = plano.acoes.filter((a) => a.tipo === "CRIAR");
  const idCriado = new Map(criadosOrdem.map((a, i) => [a.transacao.fitid, aplicado?.remover[i]]));
  return resultado.itens.map((it) => {
    const a = aplicado ? porFitid.get(it.transacao.fitid) : undefined;
    const t = it.transacao;
    if (!a) return { indice: it.indice, data: t.data, descricao: t.descricao, valor: t.valor, classe: it.classe, acao: "nenhuma", lancamentoId: it.lancamentoId };
    return a.tipo === "CONCILIAR"
      ? { indice: it.indice, data: t.data, descricao: t.descricao, valor: t.valor, classe: it.classe, acao: "conciliar", lancamentoId: a.lancamentoId }
      : { indice: it.indice, data: t.data, descricao: t.descricao, valor: t.valor, classe: it.classe, acao: "criar", lancamentoId: idCriado.get(t.fitid) };
  });
}

function linhasAtualizacao(ds: Dataset, plano: Plano, arquivo: string): LinhaAtualizacao[] {
  const porId = new Map(ds.despesas.map((d) => [d.id, d]));
  return plano.acoes.map((a) => {
    const t = a.transacao;
    if (a.tipo === "CRIAR") return { arquivo, descricaoBanco: t.descricao, lancamento: limparDescricaoBanco(t.descricao), valor: t.valor, data: t.data, acao: "criar", mudancas: [] };
    const d = porId.get(a.lancamentoId);
    const mudancas: LinhaAtualizacao["mudancas"] = [];
    if (d) {
      if (a.mescla.valor && Math.round(Math.abs(t.valor) * 100) !== Math.round(d.valor * 100)) mudancas.push("valor");
      if (a.mescla.data && t.data !== d.data) mudancas.push("data");
      if (a.mescla.pago && !d.pago) mudancas.push("pago");
    }
    return { arquivo, descricaoBanco: t.descricao, lancamento: d?.descricao ?? "—", valor: t.valor, data: t.data, acao: "conciliar", mudancas };
  });
}

function contadoresDe(classes: Record<ClasseMatch, number>, resumo: ResumoAplicacao, transacoes: number, aplicadoOk: boolean): Contadores {
  const resolvidos = aplicadoOk ? resumo.conciliados + resumo.criados : 0;
  return {
    transacoes,
    conciliados: resumo.conciliados,
    criados: resumo.criados,
    valoresAlterados: resumo.valoresAlterados,
    datasAlteradas: resumo.datasAlteradas,
    pagosMarcados: resumo.pagosMarcados,
    duplicados: classes.DUPLICADO,
    pendentes: Math.max(0, transacoes - classes.DUPLICADO - resolvidos),
    erros: 0,
  };
}

export interface ProcessamentoArquivo {
  resultado: ResultadoArquivo;
  plano?: Plano;
  /** dataset antes de aplicar (para o mostrador) */
  dsAntes?: Dataset;
}

/**
 * Processa UM arquivo de forma síncrona contra o estado atual: casa, monta o plano e aplica.
 * Um erro do `aplicar` vira resultado ERRO (nada é aplicado para este arquivo).
 */
export function processarArquivo(arq: ArquivoParaLote, opcoes: OpcoesLote, getDs: () => Dataset, aplicar: ParametrosLote["aplicar"]): ProcessamentoArquivo {
  const rotuloDestino = arq.rotuloDestino ?? "";
  const n = arq.extrato.transacoes.length;
  try {
    const dsAntes = getDs();
    const casado = casarArquivo(dsAntes, arq, opcoes);
    const classes = classesVazias();
    for (const it of casado.itens) classes[it.classe]++;
    const plano = planoDoLote(casado, dsAntes, arq.destino, opcoes);
    const r = plano.acoes.length > 0 ? aplicar(plano) : { ok: true as const, inverso: { restaurar: [], remover: [] }, resumo: RESUMO_VAZIO() };
    const base = { id: arq.id, nome: arq.nome, rotuloDestino, classes, destino: arq.destino };
    if (!r.ok) {
      const c = contadoresDe(classes, RESUMO_VAZIO(), n, false);
      c.erros = 1;
      return { resultado: { ...base, status: "ERRO", erro: r.erro, contadores: c, resumo: RESUMO_VAZIO(), linhas: montarLinhas(casado, plano, null) } };
    }
    return {
      resultado: { ...base, status: "CONCLUIDO", contadores: contadoresDe(classes, r.resumo, n, true), resumo: r.resumo, inverso: r.inverso, linhas: montarLinhas(casado, plano, r.inverso) },
      plano,
      dsAntes,
    };
  } catch (e) {
    const classes = classesVazias();
    const c = { ...contadoresVazios(), transacoes: n, pendentes: n, erros: 1 };
    return { resultado: { id: arq.id, nome: arq.nome, rotuloDestino, destino: arq.destino, status: "ERRO", erro: e instanceof Error ? e.message : "Falha inesperada.", contadores: c, classes, resumo: RESUMO_VAZIO(), linhas: [] } };
  }
}

function cancelado(arq: ArquivoParaLote): ResultadoArquivo {
  const n = arq.extrato.transacoes.length;
  return {
    id: arq.id,
    nome: arq.nome,
    rotuloDestino: arq.rotuloDestino ?? "",
    destino: arq.destino,
    status: "CANCELADO",
    contadores: { ...contadoresVazios(), transacoes: n, pendentes: n },
    classes: classesVazias(),
    resumo: RESUMO_VAZIO(),
    linhas: [],
  };
}

/**
 * Executa o lote em sequência (a ordem importa: o arquivo B casa contra o estado já atualizado pelo A).
 * Atômico por arquivo; falha de um arquivo não aborta os demais; cancelar para após o arquivo corrente.
 */
export async function executarLote(p: ParametrosLote): Promise<ResultadoLote> {
  const opcoes = p.opcoes ?? OPCOES_LOTE_PADRAO;
  const ceder = p.ceder ?? cederPadrao;
  const total = p.arquivos.length;
  const emitir = (e: EventoLote) => p.onProgresso?.(e);
  const resultados: ResultadoArquivo[] = [];
  let acumulado = contadoresVazios();
  let paradoPeloUsuario = false;

  emitir({ tipo: "inicio", total, pctGeral: 0, contadores: acumulado });
  for (let i = 0; i < total; i++) {
    const arq = p.arquivos[i];
    if (paradoPeloUsuario || p.sinalCancelar?.()) {
      paradoPeloUsuario = true;
      const c = cancelado(arq);
      resultados.push(c);
      acumulado = somarContadores(acumulado, c.contadores);
      emitir({ tipo: "arquivo", indice: i, total, resultado: c, contadores: acumulado, pctGeral: percentualGeral(i + 1, total, "FILA") });
      continue;
    }
    const etapa = (e: EtapaArquivo) =>
      emitir({ tipo: "etapa", indice: i, total, id: arq.id, nome: arq.nome, etapa: e, pctArquivo: percentualArquivo(e), pctGeral: percentualGeral(i, total, e), contadores: acumulado });
    etapa("LENDO");
    await ceder();
    etapa("COMPARANDO");
    await ceder();
    etapa("APLICANDO");
    await ceder();
    const proc = processarArquivo(arq, opcoes, p.getDs, p.aplicar);
    if (proc.plano && proc.dsAntes) {
      acumulado = somarContadores(acumulado, proc.resultado.contadores);
      emitir({ tipo: "linhas", indice: i, id: arq.id, linhas: linhasAtualizacao(proc.dsAntes, proc.plano, arq.nome), contadores: acumulado, pctGeral: percentualGeral(i, total, "APLICANDO") });
    } else acumulado = somarContadores(acumulado, proc.resultado.contadores);
    resultados.push(proc.resultado);
    emitir({ tipo: "arquivo", indice: i, total, resultado: proc.resultado, contadores: acumulado, pctGeral: percentualGeral(i + 1, total, "FILA") });
    await ceder();
  }
  const resultado: ResultadoLote = { arquivos: resultados, contadores: acumulado, cancelado: paradoPeloUsuario };
  emitir({ tipo: "fim", resultado, pctGeral: paradoPeloUsuario ? percentualGeral(resultados.filter((r) => r.status !== "CANCELADO").length, total, "FILA") : 100 });
  return resultado;
}

/** Prévia SEM aplicar: simula o lote em sequência sobre uma cópia local do dataset. */
export function preverLote(ds: Dataset, arquivos: readonly ArquivoParaLote[], opcoes: OpcoesLote, agora: number): ResultadoArquivo[] {
  let atual = ds;
  return arquivos.map((arq) => {
    return processarArquivo(
      arq,
      opcoes,
      () => atual,
      (plano) => {
        const r = aplicarConciliacao(atual, plano, agora);
        if (r.ok) atual = r.ds;
        return r.ok ? { ok: true, inverso: r.inverso, resumo: r.resumo } : r;
      },
    ).resultado;
  });
}

/** Inversos do lote na ordem em que devem ser desfeitos (do último arquivo ao primeiro). */
export function inversosEmOrdemInversa(r: Pick<ResultadoLote, "arquivos">): PlanoInverso[] {
  const out: PlanoInverso[] = [];
  for (let i = r.arquivos.length - 1; i >= 0; i--) {
    const inv = r.arquivos[i].inverso;
    if (inv && (inv.restaurar.length > 0 || inv.remover.length > 0)) out.push(inv);
  }
  return out;
}

/** Arquivos que terminaram com pendências (SUGERIDO / SO_NO_EXTRATO não resolvidos). */
export function arquivosComPendencias(r: Pick<ResultadoLote, "arquivos">): ResultadoArquivo[] {
  return r.arquivos.filter((a) => a.status !== "ERRO" && a.contadores.pendentes > 0);
}
