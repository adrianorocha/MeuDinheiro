/**
 * R59 - rateios e dívidas entre pessoas (app e portal, mesmas regras e mesmos números nos testes).
 *
 * "Dividir esta despesa" NUNCA altera o lançamento original (continua com o valor TOTAL que saiu da conta) nem saldo:
 * só cria `acertos` A_RECEBER vinculados a ele. "Quanto você realmente gastou" (total − a receber de terceiros) é um
 * indicador à parte, mostrado só na tela de pessoas/rateio. Lógica em centavos inteiros; a sobra do arredondamento
 * fica sempre com o pagador (você).
 */
import { normalizaNome, recalcularTudo } from "./calc";
import { brlCentavos } from "./centavos";
import { novoIdLong } from "./ids";
import { fromCents, toCents } from "./money";
import { atualizarItem, inserirItem, montaDespesa, removerItem, type Ctx } from "./operations";
import type { Acerto, Dataset, Despesa, Pessoa, Result, Tipo, TipoAcerto } from "./types";

export const CATEGORIA_REEMBOLSO = "Reembolso";
export const CATEGORIA_ACERTOS = "Acertos";
export const CATEGORIA_EMPRESTIMOS = "Empréstimos";

export const grupoRateioDe = (despesaId: number) => `rateio:${despesaId}`;

// ------------------------------------------------------------ rateio puro

export type ModoRateio = "IGUAL" | "VALOR" | "PERCENTUAL";

export interface ParticipanteRateio {
  pessoaId: number;
  /** modo VALOR: cota em centavos */
  valor?: number;
  /** modo PERCENTUAL: décimos de ponto percentual (250 = 25,0%) */
  percentual?: number;
}

export type ResultadoRateio =
  | { ok: true; cotas: { pessoaId: number; centavos: number }[]; voce: number }
  | { ok: false; erro: string };

const falha = (erro: string) => ({ ok: false as const, erro });

/**
 * Divide `total` (centavos) entre as pessoas e você. `IGUAL`: total ÷ (pessoas + você), sobra em você.
 * `VALOR`: cada pessoa tem a sua cota; você fica com o resto (nunca negativo). `PERCENTUAL`: pessoas + `vocePercentual`
 * (décimos) precisam somar exatamente 100%; cota = piso(total × %), a sobra fica com você.
 * Soma exata: Σ cotas + voce = total, sempre.
 */
export function ratear(total: number, modo: ModoRateio, participantes: readonly ParticipanteRateio[], vocePercentual?: number): ResultadoRateio {
  if (!Number.isInteger(total) || total <= 0) return falha("O valor a dividir deve ser maior que zero.");
  if (participantes.length === 0) return falha("Escolha ao menos uma pessoa.");
  if (new Set(participantes.map((p) => p.pessoaId)).size !== participantes.length) return falha("A mesma pessoa foi escolhida duas vezes.");

  let cotas: number[];
  if (modo === "IGUAL") {
    const base = Math.floor(total / (participantes.length + 1));
    cotas = participantes.map(() => base);
  } else if (modo === "VALOR") {
    if (participantes.some((p) => !Number.isInteger(p.valor) || (p.valor as number) <= 0)) return falha("Informe um valor maior que zero para cada pessoa.");
    cotas = participantes.map((p) => p.valor as number);
    if (cotas.reduce((s, c) => s + c, 0) > total) return falha("A soma das cotas passa do valor da despesa.");
  } else {
    if (participantes.some((p) => !Number.isInteger(p.percentual) || (p.percentual as number) <= 0)) return falha("Informe um percentual maior que zero para cada pessoa.");
    const soma = participantes.reduce((s, p) => s + (p.percentual as number), 0) + (vocePercentual ?? 0);
    if (!Number.isInteger(vocePercentual) || (vocePercentual as number) < 0 || soma !== 1000) {
      return falha(`Os percentuais precisam somar 100% (somam ${Math.floor(soma / 10)},${Math.abs(soma) % 10}%).`);
    }
    cotas = participantes.map((p) => Math.floor((total * (p.percentual as number)) / 1000));
  }
  if (cotas.some((c) => c <= 0)) return falha("Valor pequeno demais para dividir: alguma cota ficou zerada.");
  const voce = total - cotas.reduce((s, c) => s + c, 0);
  return { ok: true, cotas: participantes.map((p, i) => ({ pessoaId: p.pessoaId, centavos: cotas[i] })), voce };
}

// ------------------------------------------------------------ saldos, indicador líquido, cobrança

export interface SaldoPessoa {
  pessoa: Pessoa;
  /** centavos pendentes */
  aReceber: number;
  aPagar: number;
  /** aReceber − aPagar: positivo = a pessoa te deve */
  liquido: number;
  pendentes: Acerto[];
  quitados: Acerto[];
}

export function saldosPorPessoa(pessoas: readonly Pessoa[], acertos: readonly Acerto[]): SaldoPessoa[] {
  return pessoas.map((pessoa) => {
    const meus = acertos.filter((a) => a.pessoaId === pessoa.id);
    const pendentes = meus.filter((a) => !a.quitado);
    const aReceber = pendentes.filter((a) => a.tipo === "A_RECEBER").reduce((s, a) => s + toCents(a.valor), 0);
    const aPagar = pendentes.filter((a) => a.tipo === "A_PAGAR").reduce((s, a) => s + toCents(a.valor), 0);
    return { pessoa, aReceber, aPagar, liquido: aReceber - aPagar, pendentes, quitados: meus.filter((a) => a.quitado) };
  });
}

export interface ItemRateio {
  despesaId: number;
  descricao: string;
  data: number;
  /** total do lançamento, centavos (0 quando o lançamento foi removido) */
  total: number;
  /** a receber de terceiros (quitado ou não) */
  aReceber: number;
  /** quanto você realmente gastou = total − aReceber */
  liquido: number;
  /** ainda não recebido */
  pendente: number;
  /** lançamento de origem removido: o acerto continua valendo */
  orfao: boolean;
}

export interface ResumoRateios {
  itens: ItemRateio[];
  totalRateado: number;
  aReceberTerceiros: number;
  liquido: number;
}

/** Itens rateados (acertos com `grupoRateio`) por lançamento, no período (data do lançamento) se informado. Órfãos listados, mas fora dos totais. */
export function resumoRateios(despesas: readonly Despesa[], acertos: readonly Acerto[], periodo?: { inicio: number; fim: number }): ResumoRateios {
  const porId = new Map(despesas.map((d) => [d.id, d]));
  const grupos = new Map<number, Acerto[]>();
  for (const a of acertos) {
    if (a.tipo !== "A_RECEBER" || !a.grupoRateio || a.despesaId == null) continue;
    grupos.set(a.despesaId, [...(grupos.get(a.despesaId) ?? []), a]);
  }
  const itens: ItemRateio[] = [];
  for (const [despesaId, lista] of grupos) {
    const d = porId.get(despesaId);
    const data = d ? d.data : Math.min(...lista.map((a) => a.data));
    if (periodo && (data < periodo.inicio || data > periodo.fim)) continue;
    const aReceber = lista.reduce((s, a) => s + toCents(a.valor), 0);
    const pendente = lista.filter((a) => !a.quitado).reduce((s, a) => s + toCents(a.valor), 0);
    const total = d ? toCents(d.valor) : 0;
    itens.push({
      despesaId,
      descricao: d ? d.descricao : lista[0].descricao,
      data,
      total,
      aReceber,
      liquido: d ? total - aReceber : 0,
      pendente,
      orfao: !d,
    });
  }
  itens.sort((a, b) => b.data - a.data || b.despesaId - a.despesaId);
  const validos = itens.filter((i) => !i.orfao);
  const totalRateado = validos.reduce((s, i) => s + i.total, 0);
  const aReceberTerceiros = validos.reduce((s, i) => s + i.aReceber, 0);
  return { itens, totalRateado, aReceberTerceiros, liquido: totalRateado - aReceberTerceiros };
}

/** Valor líquido de UM lançamento: total − a receber de terceiros vinculados por rateio (centavos). */
export function gastoLiquido(despesa: Pick<Despesa, "id" | "valor">, acertos: readonly Acerto[]): number {
  const terceiros = acertos
    .filter((a) => a.tipo === "A_RECEBER" && a.despesaId === despesa.id && !!a.grupoRateio)
    .reduce((s, a) => s + toCents(a.valor), 0);
  return toCents(despesa.valor) - terceiros;
}

function dm(ms: number): string {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/**
 * Texto pronto para WhatsApp/copiar com os acertos A_RECEBER pendentes da pessoa. A chave PIX só entra se digitada
 * (nunca inventada). `null` quando não há nada a cobrar.
 */
export function textoCobranca(nome: string, acertos: readonly Acerto[], pix?: string | null): string | null {
  const itens = acertos.filter((a) => a.tipo === "A_RECEBER" && !a.quitado).sort((a, b) => a.data - b.data || a.id - b.id);
  if (itens.length === 0) return null;
  const total = itens.reduce((s, a) => s + toCents(a.valor), 0);
  const linhas = [`Olá, ${nome.trim()}! Segue o que ficou pendente entre nós:`];
  for (const a of itens) linhas.push(`- ${dm(a.data)} ${a.descricao.trim()}: ${brlCentavos(toCents(a.valor))}`);
  linhas.push("", `Total: ${brlCentavos(total)}`);
  const chave = pix?.trim();
  if (chave) linhas.push(`Chave PIX: ${chave}`);
  return linhas.join("\n");
}

// ------------------------------------------------------------ operações sobre o dataset

type Op<T extends object = object> = Result<{ ds: Dataset } & T>;
const erro = (m: string) => ({ ok: false as const, erro: m });

export function salvarPessoa(ds: Dataset, dados: { nome: string; contato?: string | null }, id: number | undefined, ctx: Ctx): Op<{ pessoa: Pessoa }> {
  const nome = dados.nome.trim();
  if (!nome) return erro("Informe o nome da pessoa.");
  const alvo = normalizaNome(nome);
  if (ds.pessoas.some((p) => p.id !== id && normalizaNome(p.nome) === alvo)) return erro("Já existe uma pessoa com esse nome.");
  const limpo = { nome, contato: dados.contato?.trim() ? dados.contato.trim() : null };
  if (id !== undefined) {
    if (!ds.pessoas.some((p) => p.id === id)) return erro("Pessoa não encontrada.");
    const pessoas = atualizarItem(ds.pessoas, id, limpo);
    return { ok: true, ds: { ...ds, pessoas }, pessoa: pessoas.find((p) => p.id === id) as Pessoa };
  }
  const { lista, item } = inserirItem(ds.pessoas, limpo, ctx);
  return { ok: true, ds: { ...ds, pessoas: lista }, pessoa: item };
}

export function excluirPessoa(ds: Dataset, id: number): Op {
  if (!ds.pessoas.some((p) => p.id === id)) return erro("Pessoa não encontrada.");
  if (ds.acertos.some((a) => a.pessoaId === id)) return erro("Esta pessoa tem acertos. Exclua os acertos antes de removê-la.");
  return { ok: true, ds: { ...ds, pessoas: removerItem(ds.pessoas, id) } };
}

export interface DivisaoDespesa {
  despesaId: number;
  modo: ModoRateio;
  participantes: ParticipanteRateio[];
  /** modo PERCENTUAL: décimos para você */
  vocePercentual?: number;
}

/** Cria os acertos A_RECEBER do rateio. O lançamento e os saldos NÃO mudam (mesma referência de `despesas`/`contas`). */
export function dividirDespesa(ds: Dataset, input: DivisaoDespesa, ctx: Ctx): Op<{ acertos: Acerto[]; voce: number }> {
  const d = ds.despesas.find((x) => x.id === input.despesaId);
  if (!d) return erro("Lançamento não encontrado.");
  if (d.tipo !== "DEBITO" || d.natureza !== "NORMAL") return erro("Só despesas comuns podem ser divididas.");
  const grupo = grupoRateioDe(d.id);
  if (ds.acertos.some((a) => a.grupoRateio === grupo)) return erro("Este lançamento já foi dividido. Desfaça a divisão antes de dividir de novo.");
  for (const p of input.participantes) if (!ds.pessoas.some((x) => x.id === p.pessoaId)) return erro("Pessoa não encontrada.");
  const r = ratear(toCents(d.valor), input.modo, input.participantes, input.vocePercentual);
  if (!r.ok) return erro(r.erro);
  // R68: vínculos já feitos ("vinculo:") contam como comprometidos; a divisão não pode passar do total.
  const vinculado = ds.acertos.filter((a) => a.tipo === "A_RECEBER" && a.despesaId === d.id && a.grupoRateio === grupoVinculoDe(d.id)).reduce((s, a) => s + toCents(a.valor), 0);
  if (vinculado > 0 && r.cotas.reduce((s, c) => s + c.centavos, 0) + vinculado > toCents(d.valor)) {
    return erro(`Este lançamento já tem ${brlCentavos(vinculado)} vinculados a pessoas; a divisão passaria do valor da despesa.`);
  }
  let lista = ds.acertos;
  const criados: Acerto[] = [];
  for (const c of r.cotas) {
    const novo = inserirItem(lista, {
      pessoaId: c.pessoaId,
      tipo: "A_RECEBER" as TipoAcerto,
      valor: fromCents(c.centavos),
      descricao: d.descricao,
      data: d.data,
      despesaId: d.id,
      quitado: false,
      quitadoEm: null,
      grupoRateio: grupo,
    }, ctx);
    lista = novo.lista;
    criados.push(novo.item);
  }
  return { ok: true, ds: { ...ds, acertos: lista }, acertos: criados, voce: r.voce };
}

/** Remove os acertos do rateio (só se nenhum foi quitado). O lançamento não muda. */
export function desfazerRateio(ds: Dataset, despesaId: number): Op<{ removidos: number }> {
  const grupo = grupoRateioDe(despesaId);
  const doGrupo = ds.acertos.filter((a) => a.grupoRateio === grupo);
  if (doGrupo.length === 0) return erro("Este lançamento não foi dividido.");
  if (doGrupo.some((a) => a.quitado)) return erro("Há acertos já quitados neste rateio; não dá para desfazer a divisão.");
  return { ok: true, ds: { ...ds, acertos: ds.acertos.filter((a) => a.grupoRateio !== grupo) }, removidos: doGrupo.length };
}

export interface LancamentoOpcional {
  /** número da conta */
  conta: string;
  categoria?: string;
}

function criarLancamento(ds: Dataset, ctx: Ctx, base: { descricao: string; valor: number; data: number; categoria: string; conta: string; tipo: Tipo }): { despesa: Despesa } | { erro: string } {
  if (!ds.contas.some((c) => c.conta === base.conta)) return { erro: "Conta não encontrada." };
  const pic = ds.categorias.find((c) => normalizaNome(c.nome) === normalizaNome(base.categoria))?.pic;
  const id = novoIdLong(ds.despesas.map((x) => x.id), ctx.agora);
  return { despesa: montaDespesa({ ...base, pic, pago: true }, id) };
}

export interface AcertoAvulso {
  pessoaId: number;
  /** A_RECEBER = "Emprestei"; A_PAGAR = "Devo" */
  tipo: TipoAcerto;
  valor: number;
  descricao?: string;
  data?: number;
  /** Gera também o lançamento na conta (saída no "Emprestei", entrada no "Devo"). */
  lancamento?: LancamentoOpcional | null;
}

export function criarAcertoAvulso(ds: Dataset, input: AcertoAvulso, ctx: Ctx): Op<{ acerto: Acerto; despesa: Despesa | null }> {
  const pessoa = ds.pessoas.find((p) => p.id === input.pessoaId);
  if (!pessoa) return erro("Pessoa não encontrada.");
  if (input.tipo !== "A_RECEBER" && input.tipo !== "A_PAGAR") return erro("Tipo de acerto inválido.");
  const centavos = toCents(input.valor);
  if (!(centavos > 0)) return erro("O valor deve ser maior que zero.");
  const data = input.data ?? ctx.agora;
  const descricao = input.descricao?.trim() || (input.tipo === "A_RECEBER" ? "Empréstimo" : "Dívida");
  let despesa: Despesa | null = null;
  let despesas = ds.despesas;
  if (input.lancamento) {
    const l = criarLancamento(ds, ctx, {
      descricao: input.tipo === "A_RECEBER" ? `Empréstimo para ${pessoa.nome}` : `Empréstimo de ${pessoa.nome}`,
      valor: fromCents(centavos),
      data,
      categoria: input.lancamento.categoria?.trim() || CATEGORIA_EMPRESTIMOS,
      conta: input.lancamento.conta,
      tipo: input.tipo === "A_RECEBER" ? "DEBITO" : "CREDITO",
    });
    if ("erro" in l) return erro(l.erro);
    despesa = l.despesa;
    despesas = [...ds.despesas, despesa];
  }
  const { lista, item } = inserirItem(ds.acertos, {
    pessoaId: pessoa.id,
    tipo: input.tipo,
    valor: fromCents(centavos),
    descricao,
    data,
    despesaId: despesa ? despesa.id : null,
    quitado: false,
    quitadoEm: null,
    grupoRateio: null,
  }, ctx);
  const novo = { ...ds, acertos: lista, despesas };
  return { ok: true, ds: despesa ? recalcularTudo(novo) : novo, acerto: item, despesa };
}

export interface QuitacaoAcerto {
  acertoId: number;
  data?: number;
  /** Cria o lançamento: receita "Reembolso" ao receber, despesa "Acertos" ao pagar (já pago, para o saldo bater com o banco). */
  lancamento?: LancamentoOpcional | null;
}

export function quitarAcerto(ds: Dataset, input: QuitacaoAcerto, ctx: Ctx): Op<{ despesa: Despesa | null }> {
  const a = ds.acertos.find((x) => x.id === input.acertoId);
  if (!a) return erro("Acerto não encontrado.");
  if (a.quitado) return erro("Este acerto já está quitado.");
  const pessoa = ds.pessoas.find((p) => p.id === a.pessoaId);
  const data = input.data ?? ctx.agora;
  let despesa: Despesa | null = null;
  let despesas = ds.despesas;
  if (input.lancamento) {
    const nome = pessoa?.nome ?? "pessoa";
    const recebendo = a.tipo === "A_RECEBER";
    const l = criarLancamento(ds, ctx, {
      descricao: recebendo ? `Reembolso: ${nome} - ${a.descricao}` : `Acerto com ${nome}: ${a.descricao}`,
      valor: a.valor,
      data,
      categoria: input.lancamento.categoria?.trim() || (recebendo ? CATEGORIA_REEMBOLSO : CATEGORIA_ACERTOS),
      conta: input.lancamento.conta,
      tipo: recebendo ? "CREDITO" : "DEBITO",
    });
    if ("erro" in l) return erro(l.erro);
    despesa = l.despesa;
    despesas = [...ds.despesas, despesa];
  }
  const novo = { ...ds, despesas, acertos: atualizarItem(ds.acertos, a.id, { quitado: true, quitadoEm: data }) };
  return { ok: true, ds: despesa ? recalcularTudo(novo) : novo, despesa };
}

export function excluirAcerto(ds: Dataset, id: number): Op {
  if (!ds.acertos.some((a) => a.id === id)) return erro("Acerto não encontrado.");
  return { ok: true, ds: { ...ds, acertos: removerItem(ds.acertos, id) } };
}

// ------------------------------------------------------------ R68 - vincular lançamentos já existentes

export const grupoVinculoDe = (despesaId: number) => `vinculo:${despesaId}`;

/**
 * Centavos já comprometidos com acertos A_RECEBER de um lançamento (quitados ou não, qualquer pessoa, qualquer origem).
 */
export function comprometidoAReceber(acertos: readonly Acerto[], despesaId: number): number {
  return acertos.filter((a) => a.tipo === "A_RECEBER" && a.despesaId === despesaId).reduce((s, a) => s + toCents(a.valor), 0);
}

export interface ItemVinculo {
  despesaId: number;
  /** Em reais; ausente = valor do lançamento. Parcial permitido (≤ valor do lançamento). */
  valor?: number;
}

export interface VinculoLancamentos {
  pessoaId: number;
  /** A_RECEBER = "ela me deve"; A_PAGAR = "eu devo a ela" */
  tipo: TipoAcerto;
  itens: ItemVinculo[];
}

/**
 * Vincula lançamentos JÁ EXISTENTES a uma pessoa: cria SOMENTE acertos (`despesaId`, data e descrição do lançamento,
 * `grupoRateio = "vinculo:<despesaId>"`). Lançamentos, contas, cartões e KPIs não mudam (mesma referência). Tudo ou nada.
 *
 * Regras: só natureza NORMAL; receita (CREDITO) só aceita A_PAGAR; A_RECEBER ≤ total − A_RECEBER já comprometidos (para o "quanto você
 * realmente gastou" nunca ficar negativo); A_PAGAR ≤ valor do lançamento; mesmo lançamento + mesma pessoa + mesmo tipo = "já vinculado".
 * O grupo `vinculo:` é diferente de `rateio:`, então "Desfazer divisão" nunca apaga vínculos; desvincular = `excluirAcerto` (não quitado).
 */
export function vincularLancamentos(ds: Dataset, input: VinculoLancamentos, ctx: Ctx): Op<{ acertos: Acerto[] }> {
  const pessoa = ds.pessoas.find((p) => p.id === input.pessoaId);
  if (!pessoa) return erro("Pessoa não encontrada.");
  if (input.tipo !== "A_RECEBER" && input.tipo !== "A_PAGAR") return erro("Tipo de acerto inválido.");
  if (input.itens.length === 0) return erro("Escolha ao menos um lançamento.");
  if (new Set(input.itens.map((i) => i.despesaId)).size !== input.itens.length) return erro("O mesmo lançamento foi escolhido duas vezes.");

  const novos: { d: Despesa; centavos: number }[] = [];
  for (const item of input.itens) {
    const d = ds.despesas.find((x) => x.id === item.despesaId);
    if (!d) return erro("Lançamento não encontrado.");
    const rotulo = `“${d.descricao}”`;
    if (d.natureza !== "NORMAL") return erro(`${rotulo}: só lançamentos comuns podem ser vinculados (não vale transferência, fatura, meta, ajuste nem saldo inicial).`);
    if (input.tipo === "A_RECEBER" && d.tipo === "CREDITO") return erro(`${rotulo} é uma receita: só dá para registrar “Eu devo a ela” (repasse).`);
    const total = toCents(d.valor);
    const centavos = item.valor === undefined ? total : toCents(item.valor);
    if (!Number.isFinite(centavos) || !(centavos > 0)) return erro(`${rotulo}: o valor deve ser maior que zero.`);
    if (centavos > total) return erro(`${rotulo}: o valor passa do valor do lançamento (${brlCentavos(total)}).`);
    if (ds.acertos.some((a) => a.pessoaId === pessoa.id && a.tipo === input.tipo && a.despesaId === d.id)) {
      return erro(`${rotulo}: já vinculado a ${pessoa.nome}. Desvincule (exclua o acerto) para vincular de novo com outro valor.`);
    }
    if (input.tipo === "A_RECEBER") {
      const resta = total - comprometidoAReceber(ds.acertos, d.id);
      if (centavos > resta) return erro(`${rotulo}: só restam ${brlCentavos(Math.max(resta, 0))} para vincular (o resto já está com outros acertos a receber).`);
    }
    novos.push({ d, centavos });
  }

  let lista = ds.acertos;
  const criados: Acerto[] = [];
  for (const { d, centavos } of novos) {
    const novo = inserirItem(lista, {
      pessoaId: pessoa.id,
      tipo: input.tipo,
      valor: fromCents(centavos),
      descricao: d.descricao,
      data: d.data,
      despesaId: d.id,
      quitado: false,
      quitadoEm: null,
      grupoRateio: grupoVinculoDe(d.id),
    }, ctx);
    lista = novo.lista;
    criados.push(novo.item);
  }
  return { ok: true, ds: { ...ds, acertos: lista }, acertos: criados };
}
