/**
 * R69 - relatório detalhado de Pessoas (app e portal: mesmas regras, mesmos números e mesmos textos nos testes).
 *
 * Modelo PURO, em centavos inteiros: nada de arredondamento fora dos centavos, nada de Locale (datas e moeda formatadas
 * à mão, em horário local). Não altera nenhum dado; só apresenta `pessoas` + `acertos` (+ `despesas` para descrever o
 * lançamento vinculado). Os renderizadores (PDF/PNG/JPEG) só desenham este modelo.
 *
 * Definições (sempre sobre o conjunto FILTRADO: pessoas escolhidas, data do acerto no período, situação):
 * - a receber / a pagar = acertos NÃO quitados; quitado = acertos quitados (a receber / a pagar separados);
 * - saldo líquido = a receber − a pagar (positivo = a pessoa te deve);
 * - Σ subtotais das pessoas = totais do resumo (garantido por construção e por teste).
 */
import { brlCentavos } from "./centavos";
import { cartaoIdDe, faturaDaCompra, principalDe, vencimentoFatura } from "./calc";
import { toCents } from "./money";
import { resumoRateios, type ItemRateio } from "./rateios";
import type { Acerto, Cartao, Despesa, Pessoa, TipoAcerto } from "./types";

export type SituacaoRelatorio = "TODOS" | "PENDENTES" | "QUITADOS";
export type AgruparRelatorio = "POR_PESSOA" | "CRONOLOGICO";
export type SentidoSaldo = "TE_DEVE" | "VOCE_DEVE" | "QUITES";
export type StatusLinha = "PENDENTE" | "QUITADO";

export interface OpcoesRelatorioPessoas {
  /** vazio = todas */
  pessoaIds: readonly number[];
  /** sobre a data do acerto, inclusivo; null = tudo */
  periodo: { inicio: number; fim: number } | null;
  situacao: SituacaoRelatorio;
  incluirDetalhes: boolean;
  agrupar: AgruparRelatorio;
  /** bloco informativo "Rateios" (quanto você realmente gastou) */
  incluirRateios?: boolean;
  /** epoch ms da emissão */
  agora: number;
  /** cartões, para destacar o vencimento da fatura dos lançamentos no crédito */
  cartoes?: readonly Cartao[];
  /** acertos que NÃO entram neste relatório (ficam para o próximo; nada é alterado nos dados) */
  excluirAcertoIds?: readonly number[];
}

export interface LinhaRelatorioPessoas {
  acertoId: number;
  pessoaId: number;
  pessoaNome: string;
  data: number;
  dataTexto: string;
  descricao: string;
  /** "lançamento de dd/mm — <descrição>" | "avulso" | "lançamento removido" */
  origem: string;
  origemTipo: "LANCAMENTO" | "AVULSO" | "REMOVIDO";
  tipo: TipoAcerto;
  tipoTexto: string;
  centavos: number;
  status: StatusLinha;
  /** "Pendente" | "Quitado em dd/mm" */
  statusTexto: string;
  /** só com `grupoRateio` e lançamento existente */
  rateio: { totalDespesa: number; cota: number; texto: string } | null;
  /** só A receber pendente de lançamento no cartão de crédito: data limite para acertar (vencimento da fatura) */
  vencimento: { ms: number; texto: string; vencido: boolean } | null;
}

export interface PessoaRelatorio {
  pessoaId: number;
  nome: string;
  contato: string | null;
  iniciais: string;
  /** índice estável na paleta de avatares (derivado do nome) */
  corIndice: number;
  aReceber: number;
  aPagar: number;
  liquido: number;
  sentido: SentidoSaldo;
  /** "a acertar R$ 10,00" | "a compensar R$ 10,00" | "contas em dia" */
  saldoTexto: string;
  quitadoAReceber: number;
  quitadoAPagar: number;
  nAcertos: number;
  /** vencimento de fatura mais próximo (ou o já vencido mais antigo) entre os pendentes, com o valor ligado a faturas */
  proximoVencimento: { ms: number; texto: string; vencido: boolean; centavos: number } | null;
  /** vazio quando `incluirDetalhes` é falso */
  linhas: LinhaRelatorioPessoas[];
}

export interface ResumoRelatorioPessoas {
  aReceberPendente: number;
  aPagarPendente: number;
  saldoLiquido: number;
  quitadoAReceber: number;
  quitadoAPagar: number;
  nAcertos: number;
  /** pessoas com saldo líquido pendente diferente de zero */
  nPessoasComSaldo: number;
  nPessoas: number;
}

export interface RateiosRelatorio {
  itens: ItemRateio[];
  totalRateado: number;
  aReceberTerceiros: number;
  liquido: number;
}

export interface RelatorioPessoas {
  titulo: string;
  periodoTexto: string;
  filtros: string[];
  emitidoEm: string;
  agrupar: AgruparRelatorio;
  incluirDetalhes: boolean;
  resumo: ResumoRelatorioPessoas;
  pessoas: PessoaRelatorio[];
  /** só em CRONOLOGICO com detalhes: todas as linhas por data */
  cronologico: LinhaRelatorioPessoas[];
  rateios: RateiosRelatorio | null;
  /** quantos acertos ficaram de fora por escolha (para o próximo relatório) */
  adiados: number;
  /** nada a mostrar: não gerar arquivo */
  vazio: boolean;
}

export const MSG_SEM_ACERTOS = "Nenhum acerto no período";

const inicioDia = (ms: number) => {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
};
const dois = (n: number) => String(n).padStart(2, "0");
export const dataBR = (ms: number) => {
  const d = new Date(ms);
  return `${dois(d.getDate())}/${dois(d.getMonth() + 1)}/${d.getFullYear()}`;
};
const diaMes = (ms: number) => dataBR(ms).slice(0, 5);
export const dataHoraBR = (ms: number) => {
  const d = new Date(ms);
  return `${dataBR(ms)} ${dois(d.getHours())}:${dois(d.getMinutes())}`;
};

/** Hash estável (UTF-16, 31x) do nome -> índice na paleta; idêntico ao app. */
export function corIndiceDaPessoa(nome: string, tamanhoPaleta = 8): number {
  let h = 7;
  for (let i = 0; i < nome.length; i++) h = (h * 31 + nome.charCodeAt(i)) % 1000003;
  return h % tamanhoPaleta;
}

/** Até duas iniciais (primeira e última palavra), maiúsculas. */
export function iniciaisDoNome(nome: string): string {
  const partes = nome.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return "?";
  const a = Array.from(partes[0])[0] ?? "?";
  const b = partes.length > 1 ? (Array.from(partes[partes.length - 1])[0] ?? "") : "";
  return (a + b).toUpperCase();
}

export function saldoTextoDe(liquido: number): { sentido: SentidoSaldo; texto: string } {
  if (liquido > 0) return { sentido: "TE_DEVE", texto: `a acertar ${brlCentavos(liquido)}` };
  if (liquido < 0) return { sentido: "VOCE_DEVE", texto: `a compensar ${brlCentavos(-liquido)}` };
  return { sentido: "QUITES", texto: "contas em dia" };
}

export type AtalhoPeriodo = "ESTE_MES" | "MES_PASSADO" | "TRES_MESES" | "ANO" | "TUDO";

/** Atalhos do diálogo: devolvem {inicio, fim} (dias inteiros, horário local) ou null (= tudo). "3 meses" = mês atual e os 2 anteriores. */
export function periodoPorAtalho(atalho: AtalhoPeriodo, agora: number): { inicio: number; fim: number } | null {
  const d = new Date(agora);
  const a = d.getFullYear();
  const m = d.getMonth();
  const fimMes = (ano: number, mes: number) => new Date(ano, mes + 1, 0, 23, 59, 59, 999).getTime();
  switch (atalho) {
    case "ESTE_MES":
      return { inicio: new Date(a, m, 1).getTime(), fim: fimMes(a, m) };
    case "MES_PASSADO":
      return { inicio: new Date(a, m - 1, 1).getTime(), fim: fimMes(a, m - 1) };
    case "TRES_MESES":
      return { inicio: new Date(a, m - 2, 1).getTime(), fim: fimMes(a, m) };
    case "ANO":
      return { inicio: new Date(a, 0, 1).getTime(), fim: new Date(a, 11, 31, 23, 59, 59, 999).getTime() };
    default:
      return null;
  }
}

const ROTULO_SITUACAO: Record<SituacaoRelatorio, string> = { TODOS: "Todos", PENDENTES: "Somente pendentes", QUITADOS: "Somente quitados" };
const ROTULO_AGRUPAR: Record<AgruparRelatorio, string> = { POR_PESSOA: "Por pessoa", CRONOLOGICO: "Cronológico" };

export function montarRelatorioPessoas(
  pessoas: readonly Pessoa[],
  acertos: readonly Acerto[],
  despesas: readonly Despesa[],
  op: OpcoesRelatorioPessoas,
): RelatorioPessoas {
  const porDespesa = new Map(despesas.map((d) => [d.id, d]));
  const escolhidas = new Set(op.pessoaIds);
  const alvo = pessoas.filter((p) => escolhidas.size === 0 || escolhidas.has(p.id));
  const adiados = new Set(op.excluirAcertoIds ?? []);
  const noPeriodo = (a: Acerto) => !op.periodo || (a.data >= op.periodo.inicio && a.data <= op.periodo.fim);
  const naSituacao = (a: Acerto) => op.situacao === "TODOS" || (op.situacao === "PENDENTES" ? !a.quitado : a.quitado);

  const linhaDe = (a: Acerto, p: Pessoa): LinhaRelatorioPessoas => {
    const cents = toCents(a.valor);
    const d = a.despesaId != null ? porDespesa.get(a.despesaId) : undefined;
    let vencimento: LinhaRelatorioPessoas["vencimento"] = null;
    if (d && a.tipo === "A_RECEBER" && !a.quitado && cartaoIdDe(d) !== null) {
      const cartao = op.cartoes?.find((c) => c.id === cartaoIdDe(d));
      if (cartao) {
        const principal = principalDe(cartao, op.cartoes ?? []);
        const ref = faturaDaCompra(principal, d.data);
        const ms = vencimentoFatura(principal, ref.mes, ref.ano);
        const vencido = ms < inicioDia(op.agora);
        vencimento = { ms, texto: `Fatura do cartão ${vencido ? "venceu" : "vence"} em ${dataBR(ms)}`, vencido };
      }
    }
    const origemTipo = a.despesaId == null ? "AVULSO" : d ? "LANCAMENTO" : "REMOVIDO";
    let rateio: LinhaRelatorioPessoas["rateio"] = null;
    if (a.grupoRateio && d) {
      const total = toCents(d.valor);
      const rotulo = a.grupoRateio.startsWith("vinculo:") ? "Vinculado" : "Rateio";
      rateio = { totalDespesa: total, cota: cents, texto: `${rotulo}: despesa ${brlCentavos(total)} · cota ${brlCentavos(cents)}` };
    }
    return {
      acertoId: a.id,
      pessoaId: p.id,
      pessoaNome: p.nome,
      data: a.data,
      dataTexto: dataBR(a.data),
      descricao: a.descricao,
      origem: origemTipo === "AVULSO" ? "avulso" : origemTipo === "REMOVIDO" ? "lançamento removido" : `lançamento de ${diaMes((d as Despesa).data)} — ${(d as Despesa).descricao}`,
      origemTipo,
      tipo: a.tipo,
      tipoTexto: a.tipo === "A_RECEBER" ? "A receber" : "A pagar",
      centavos: cents,
      status: a.quitado ? "QUITADO" : "PENDENTE",
      statusTexto: a.quitado ? (a.quitadoEm != null ? `Quitado em ${diaMes(a.quitadoEm)}` : "Quitado") : "Pendente",
      rateio,
      vencimento,
    };
  };
  const porData = (x: LinhaRelatorioPessoas, y: LinhaRelatorioPessoas) => x.data - y.data || x.acertoId - y.acertoId;

  const ordenadas = [...alvo].sort((a, b) => {
    const x = a.nome.toLowerCase();
    const y = b.nome.toLowerCase();
    return x < y ? -1 : x > y ? 1 : a.id - b.id;
  });
  const blocos: PessoaRelatorio[] = [];
  const todas: LinhaRelatorioPessoas[] = [];
  for (const p of ordenadas) {
    const meus = acertos.filter((a) => a.pessoaId === p.id && noPeriodo(a) && naSituacao(a) && !adiados.has(a.id));
    if (meus.length === 0) continue;
    const soma = (tipo: TipoAcerto, quitado: boolean) => meus.filter((a) => a.tipo === tipo && a.quitado === quitado).reduce((s, a) => s + toCents(a.valor), 0);
    const aReceber = soma("A_RECEBER", false);
    const aPagar = soma("A_PAGAR", false);
    const liquido = aReceber - aPagar;
    const saldo = saldoTextoDe(liquido);
    const linhas = meus.map((a) => linhaDe(a, p)).sort(porData);
    todas.push(...linhas);
    const comVenc = linhas.filter((l) => l.vencimento);
    const primeiro = comVenc.length > 0 ? comVenc.reduce((x, y) => (y.vencimento!.ms < x.vencimento!.ms ? y : x)) : null;
    const proximoVencimento = primeiro
      ? { ...primeiro.vencimento!, centavos: comVenc.reduce((s, l) => s + l.centavos, 0) }
      : null;
    blocos.push({
      pessoaId: p.id,
      nome: p.nome,
      contato: p.contato?.trim() ? p.contato.trim() : null,
      iniciais: iniciaisDoNome(p.nome),
      corIndice: corIndiceDaPessoa(p.nome),
      aReceber,
      aPagar,
      liquido,
      sentido: saldo.sentido,
      saldoTexto: saldo.texto,
      quitadoAReceber: soma("A_RECEBER", true),
      quitadoAPagar: soma("A_PAGAR", true),
      nAcertos: meus.length,
      proximoVencimento,
      linhas: op.incluirDetalhes ? linhas : [],
    });
  }

  const resumo: ResumoRelatorioPessoas = {
    aReceberPendente: blocos.reduce((s, b) => s + b.aReceber, 0),
    aPagarPendente: blocos.reduce((s, b) => s + b.aPagar, 0),
    saldoLiquido: 0,
    quitadoAReceber: blocos.reduce((s, b) => s + b.quitadoAReceber, 0),
    quitadoAPagar: blocos.reduce((s, b) => s + b.quitadoAPagar, 0),
    nAcertos: blocos.reduce((s, b) => s + b.nAcertos, 0),
    nPessoasComSaldo: blocos.filter((b) => b.liquido !== 0).length,
    nPessoas: blocos.length,
  };
  resumo.saldoLiquido = resumo.aReceberPendente - resumo.aPagarPendente;

  let rateios: RateiosRelatorio | null = null;
  if (op.incluirRateios && blocos.length > 0) {
    // Total e líquido precisam de TODOS os A_RECEBER do lançamento; o filtro de pessoas só decide quais lançamentos aparecem.
    const r = resumoRateios(despesas, acertos, op.periodo ? { inicio: op.periodo.inicio, fim: op.periodo.fim } : undefined);
    const idsBloco = new Set(blocos.map((b) => b.pessoaId));
    const comPessoa = new Set(acertos.filter((a) => idsBloco.has(a.pessoaId) && a.despesaId != null).map((a) => a.despesaId as number));
    const itens = escolhidas.size === 0 ? r.itens : r.itens.filter((i) => comPessoa.has(i.despesaId));
    const validos = itens.filter((i) => !i.orfao);
    const totalRateado = validos.reduce((s, i) => s + i.total, 0);
    const aReceberTerceiros = validos.reduce((s, i) => s + i.aReceber, 0);
    if (itens.length > 0) rateios = { itens, totalRateado, aReceberTerceiros, liquido: totalRateado - aReceberTerceiros };
  }

  const nomes = alvo.map((p) => p.nome);
  const filtros = [
    `Pessoas: ${escolhidas.size === 0 ? "todas" : nomes.join(", ") || "nenhuma"}`,
    `Situação: ${ROTULO_SITUACAO[op.situacao]}`,
    `Agrupamento: ${ROTULO_AGRUPAR[op.agrupar]}`,
    `Detalhes: ${op.incluirDetalhes ? "incluídos" : "apenas totais"}`,
  ];

  return {
    titulo: "Relatório de pessoas",
    periodoTexto: op.periodo ? `${dataBR(op.periodo.inicio)} a ${dataBR(op.periodo.fim)}` : "Todo o período",
    filtros,
    emitidoEm: dataHoraBR(op.agora),
    agrupar: op.agrupar,
    incluirDetalhes: op.incluirDetalhes,
    resumo,
    pessoas: blocos,
    cronologico: op.agrupar === "CRONOLOGICO" && op.incluirDetalhes ? [...todas].sort(porData) : [],
    rateios,
    adiados: acertos.filter((a) => adiados.has(a.id) && alvo.some((p) => p.id === a.pessoaId) && noPeriodo(a) && naSituacao(a)).length,
    vazio: blocos.length === 0,
  };
}

/** Nome do arquivo: `pessoas_AAAA-MM-DD` (+ `-N` em partes de imagem, a cargo de quem chama) + extensão. */
export function nomeArquivoPessoas(agora: number, ext: string, parte?: number): string {
  const d = new Date(agora);
  const base = `pessoas_${d.getFullYear()}-${dois(d.getMonth() + 1)}-${dois(d.getDate())}`;
  return `${base}${parte ? `-${parte}` : ""}.${ext}`;
}

