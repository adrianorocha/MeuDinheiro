import {
  cartaoIdDe,
  cartoesDoGrupo,
  faturaDaCompra,
  patrimonioLiquido,
  recalcularTudo,
  saldoConta,
} from "./calc";
import { clampDia, mesAnoDe, MESES_ABREV, normalizaMes, somaMeses } from "./dates";
import { novoIdInt, novoIdLong, novoUuid } from "./ids";
import { fromCents, round2, toCents } from "./money";
import type {
  Cartao,
  Conta,
  Dataset,
  Despesa,
  DespesaFixa,
  Entity,
  Lixeira,
  Meta,
  Natureza,
  Result,
  Tipo,
} from "./types";

/** Contexto injetável para tornar as operações determinísticas nos testes. */
export interface Ctx {
  agora: number;
  uuid?: () => string;
  rng?: () => number;
}

const uuidDe = (ctx: Ctx) => (ctx.uuid ?? novoUuid)();

function erro(mensagem: string): { ok: false; erro: string } {
  return { ok: false, erro: mensagem };
}

function finaliza<T extends object>(ds: Dataset, extra: T): { ok: true; ds: Dataset } & T {
  return { ok: true, ds: recalcularTudo(ds), ...extra };
}

type Op<T extends object = object> = Result<{ ds: Dataset } & T>;

// ------------------------------------------------------------ helpers de lista

export function inserirItem<T extends Entity>(
  lista: readonly T[],
  item: Omit<T, "id">,
  ctx: Ctx,
): { lista: T[]; item: T } {
  const novo = { ...item, id: novoIdInt(lista.map((i) => i.id), ctx.rng) } as T;
  return { lista: [...lista, novo], item: novo };
}

export function atualizarItem<T extends Entity>(lista: readonly T[], id: number, patch: Partial<Omit<T, "id">>): T[] {
  return lista.map((i) => (i.id === id ? { ...i, ...patch } : i));
}

export function removerItem<T extends Entity>(lista: readonly T[], id: number): T[] {
  return lista.filter((i) => i.id !== id);
}

export function contaPorNumero(ds: Dataset, numero: string): Conta | undefined {
  return ds.contas.find((c) => c.conta === numero);
}

export function contaDoCartao(ds: Dataset, cartao: Cartao): Conta | undefined {
  return ds.contas.find((c) => c.id === cartao.contaId);
}

// ------------------------------------------------------------ montagem de despesa

interface BaseDespesa {
  descricao: string;
  valor: number;
  data: number;
  categoria: string;
  conta: string;
  pic?: string;
  tipo: Tipo;
  cartaoId?: number | null;
  valorOriginal?: number;
  moedaOriginal?: string;
  cotacaoNaData?: number;
  pago: boolean;
  natureza?: Natureza;
  grupoId?: string | null;
  autor?: string | null;
  fitid?: string | null;
  conciliadoEm?: number | null;
}

export function montaDespesa(b: BaseDespesa, id: number): Despesa {
  const valor = round2(b.valor);
  const { mes, ano } = mesAnoDe(b.data);
  const moeda = b.moedaOriginal?.trim() || "BRL";
  return {
    id,
    descricao: b.descricao.trim(),
    valor,
    data: b.data,
    categoria: b.categoria.trim(),
    conta: b.conta,
    pic: b.pic ?? "",
    tipo: b.tipo,
    mes,
    ano,
    cartaoId: b.cartaoId ? b.cartaoId : null,
    valorOriginal: round2(b.valorOriginal && b.valorOriginal > 0 ? b.valorOriginal : valor),
    moedaOriginal: moeda,
    cotacaoNaData: b.cotacaoNaData && b.cotacaoNaData > 0 ? b.cotacaoNaData : 1,
    pago: b.pago,
    natureza: b.natureza ?? "NORMAL",
    grupoId: b.grupoId ?? null,
    autor: b.autor ?? null,
    fitid: b.fitid ?? null,
    conciliadoEm: b.conciliadoEm ?? null,
  };
}

function idsDespesas(ds: Dataset): number[] {
  return ds.despesas.map((d) => d.id);
}

// ------------------------------------------------------------ R8 parcelamento

/** R8 - divide em centavos: parcelas base = floor(total/n), a ultima recebe o resto. */
export function dividirParcelas(total: number, n: number): number[] {
  const totalCents = toCents(total);
  const base = Math.floor(totalCents / n);
  const out: number[] = Array.from({ length: n }, () => base);
  out[n - 1] = totalCents - base * (n - 1);
  return out.map(fromCents);
}

export interface NovoLancamento {
  descricao: string;
  valor: number;
  data: number;
  categoria: string;
  /** Obrigatório quando não é compra no cartão. */
  conta?: string;
  pic?: string;
  tipo: Tipo;
  cartaoId?: number | null;
  valorOriginal?: number;
  moedaOriginal?: string;
  cotacaoNaData?: number;
  pago?: boolean;
  parcelas?: number;
  autor?: string | null;
}

export const MAX_PARCELAS = 120;

/** Cria lançamento simples, parcelado (R8) ou compra no cartão (R2). */
export function adicionarLancamento(ds: Dataset, input: NovoLancamento, ctx: Ctx): Op<{ criados: Despesa[] }> {
  if (!input.descricao.trim()) return erro("Informe a descrição.");
  if (!(round2(input.valor) > 0)) return erro("O valor deve ser maior que zero.");
  if (!input.categoria.trim()) return erro("Informe a categoria.");
  if (!Number.isFinite(input.data)) return erro("Data inválida.");
  const n = Math.min(MAX_PARCELAS, Math.max(1, Math.trunc(input.parcelas ?? 1)));

  let conta = input.conta ?? "";
  let cartaoId: number | null = null;
  if (input.cartaoId) {
    const cartao = ds.cartoes.find((c) => c.id === input.cartaoId);
    if (!cartao) return erro("Cartão não encontrado.");
    const contaCartao = contaDoCartao(ds, cartao);
    if (!contaCartao) return erro("A conta vinculada ao cartão não existe.");
    conta = contaCartao.conta;
    cartaoId = cartao.id;
  } else if (!contaPorNumero(ds, conta)) {
    return erro("Conta não encontrada.");
  }

  const valores = n > 1 ? dividirParcelas(input.valor, n) : [round2(input.valor)];
  const originais =
    input.valorOriginal && input.valorOriginal > 0
      ? n > 1
        ? dividirParcelas(input.valorOriginal, n)
        : [input.valorOriginal]
      : [];
  const grupoId = n > 1 ? `parc:${uuidDe(ctx)}` : null;
  const usados = new Set(idsDespesas(ds));
  const criados: Despesa[] = [];
  for (let i = 0; i < n; i++) {
    const data = i === 0 ? input.data : somaMeses(input.data, i);
    const id = novoIdLong(usados, ctx.agora);
    usados.add(id);
    const pago = cartaoId !== null ? false : n > 1 ? data <= ctx.agora : (input.pago ?? data <= ctx.agora);
    criados.push(
      montaDespesa(
        {
          descricao: n > 1 ? `${input.descricao.trim()} (${i + 1}/${n})` : input.descricao,
          valor: valores[i],
          data,
          categoria: input.categoria,
          conta,
          pic: input.pic,
          tipo: input.tipo,
          cartaoId,
          valorOriginal: originais[i],
          moedaOriginal: input.moedaOriginal,
          cotacaoNaData: input.cotacaoNaData,
          pago,
          grupoId,
          autor: input.autor,
        },
        id,
      ),
    );
  }
  return finaliza({ ...ds, despesas: [...ds.despesas, ...criados] }, { criados });
}

// ------------------------------------------------------------ edição / exclusão (R11, R12)

const NATUREZAS_BLOQUEADAS_EDICAO: ReadonlySet<Natureza> = new Set([
  "PAGAMENTO_FATURA",
  "TRANSFERENCIA",
  "APORTE_META",
  "RESGATE_META",
]);

export interface EdicaoLancamento {
  descricao?: string;
  valor?: number;
  data?: number;
  categoria?: string;
  conta?: string;
  tipo?: Tipo;
  cartaoId?: number | null;
  pago?: boolean;
  valorOriginal?: number;
  moedaOriginal?: string;
  cotacaoNaData?: number;
}

export function editarLancamento(ds: Dataset, id: number, patch: EdicaoLancamento): Op {
  const atual = ds.despesas.find((d) => d.id === id);
  if (!atual) return erro("Lançamento não encontrado.");
  if (NATUREZAS_BLOQUEADAS_EDICAO.has(atual.natureza)) {
    return erro("Este lançamento é gerado automaticamente e não pode ser editado. Exclua-o (se permitido) e refaça a operação.");
  }
  const descricao = (patch.descricao ?? atual.descricao).trim();
  const valor = round2(patch.valor ?? atual.valor);
  if (!descricao) return erro("Informe a descrição.");
  if (!(valor > 0)) return erro("O valor deve ser maior que zero.");
  const data = patch.data ?? atual.data;
  const { mes, ano } = mesAnoDe(data);

  const cartaoIdNovo = patch.cartaoId === undefined ? cartaoIdDe(atual) : patch.cartaoId || null;
  let conta = patch.conta ?? atual.conta;
  let pago = patch.pago ?? atual.pago;
  if (cartaoIdNovo !== null) {
    const cartao = ds.cartoes.find((c) => c.id === cartaoIdNovo);
    if (!cartao) return erro("Cartão não encontrado.");
    const contaCartao = contaDoCartao(ds, cartao);
    if (!contaCartao) return erro("A conta vinculada ao cartão não existe.");
    conta = contaCartao.conta;
    if (cartaoIdNovo !== cartaoIdDe(atual)) pago = false;
  } else if (!contaPorNumero(ds, conta)) {
    return erro("Conta não encontrada.");
  }
  if (atual.natureza === "SALDO_INICIAL") pago = true;

  const novo: Despesa = {
    ...atual,
    descricao,
    valor,
    data,
    mes,
    ano,
    categoria: (patch.categoria ?? atual.categoria).trim(),
    conta,
    tipo: patch.tipo ?? atual.tipo,
    cartaoId: cartaoIdNovo,
    pago,
    valorOriginal: round2(patch.valorOriginal && patch.valorOriginal > 0 ? patch.valorOriginal : valor),
    moedaOriginal: patch.moedaOriginal?.trim() || atual.moedaOriginal || "BRL",
    cotacaoNaData: patch.cotacaoNaData && patch.cotacaoNaData > 0 ? patch.cotacaoNaData : atual.cotacaoNaData || 1,
  };
  return finaliza({ ...ds, despesas: ds.despesas.map((d) => (d.id === id ? novo : d)) }, {});
}

export function excluirLancamento(
  ds: Dataset,
  id: number,
  opcoes: { grupoParcelas?: boolean } = {},
  ctx: Ctx = { agora: Date.now() },
): Op<{ removidos: number }> {
  const alvo = ds.despesas.find((d) => d.id === id);
  if (!alvo) return erro("Lançamento não encontrado.");
  if (alvo.natureza === "PAGAMENTO_FATURA") {
    return erro("O pagamento de fatura não pode ser excluído diretamente.");
  }
  if (alvo.natureza === "APORTE_META" || alvo.natureza === "RESGATE_META") {
    return erro("Aportes e resgates de metas são gerenciados na tela de Metas.");
  }
  let ids = new Set([id]);
  if (alvo.natureza === "TRANSFERENCIA" && alvo.grupoId) {
    ids = new Set(ds.despesas.filter((d) => d.grupoId === alvo.grupoId).map((d) => d.id));
  } else if (opcoes.grupoParcelas && alvo.grupoId?.startsWith("parc:")) {
    ids = new Set(ds.despesas.filter((d) => d.grupoId === alvo.grupoId).map((d) => d.id));
  }
  // R31: vai para a lixeira (exceto saldo inicial).
  const usados = new Set(ds.lixeira.map((l) => l.id));
  const novos: Lixeira[] = [];
  for (const d of ds.despesas) {
    if (!ids.has(d.id) || d.natureza === "SALDO_INICIAL") continue;
    const lid = novoIdInt(usados, ctx.rng);
    usados.add(lid);
    novos.push({ id: lid, tipo: "DESPESA", descricao: d.descricao, valor: d.valor, excluidoEm: ctx.agora, payload: JSON.stringify(d) });
  }
  return finaliza(
    { ...ds, despesas: ds.despesas.filter((d) => !ids.has(d.id)), lixeira: [...ds.lixeira, ...novos] },
    { removidos: ids.size },
  );
}

/** R12 */
export function alternarPago(ds: Dataset, id: number): Op {
  const d = ds.despesas.find((x) => x.id === id);
  if (!d) return erro("Lançamento não encontrado.");
  if (d.natureza !== "NORMAL" && d.natureza !== "AJUSTE") {
    return erro("Este lançamento é sempre efetivado e não pode ser alternado.");
  }
  return finaliza({ ...ds, despesas: ds.despesas.map((x) => (x.id === id ? { ...x, pago: !x.pago } : x)) }, {});
}

/** "Dar baixa" em um conjunto de lançamentos. */
export function marcarPago(ds: Dataset, id: number): Op {
  const d = ds.despesas.find((x) => x.id === id);
  if (!d) return erro("Lançamento não encontrado.");
  if (d.pago) return finaliza(ds, {});
  return alternarPago(ds, id);
}

// ------------------------------------------------------------ contas

export interface NovaConta {
  banco: string;
  pic?: string;
  agencia: string;
  conta: string;
  titular: string;
  saldoInicial?: number;
}

export function criarConta(ds: Dataset, input: NovaConta, ctx: Ctx): Op<{ conta: Conta }> {
  if (!input.banco.trim()) return erro("Informe o banco.");
  const numero = input.conta.trim();
  if (!numero) return erro("Informe o número da conta.");
  if (contaPorNumero(ds, numero)) return erro("Já existe uma conta com esse número.");
  const { lista, item } = inserirItem<Conta>(
    ds.contas,
    {
      saldo: 0,
      banco: input.banco.trim(),
      pic: input.pic ?? "",
      agencia: input.agencia.trim(),
      conta: numero,
      titular: input.titular.trim(),
    },
    ctx,
  );
  let despesas = ds.despesas;
  const inicial = round2(input.saldoInicial ?? 0);
  if (inicial !== 0) {
    const id = novoIdLong(idsDespesas(ds), ctx.agora);
    despesas = [
      ...despesas,
      montaDespesa(
        {
          descricao: "Saldo inicial",
          valor: Math.abs(inicial),
          data: ctx.agora,
          categoria: "Saldo inicial",
          conta: numero,
          tipo: inicial > 0 ? "CREDITO" : "DEBITO",
          pago: true,
          natureza: "SALDO_INICIAL",
        },
        id,
      ),
    ];
  }
  return finaliza({ ...ds, contas: lista, despesas }, { conta: item });
}

export type EdicaoConta = Partial<Pick<Conta, "banco" | "pic" | "agencia" | "conta" | "titular">>;

export function editarConta(ds: Dataset, id: number, patch: EdicaoConta): Op {
  const atual = ds.contas.find((c) => c.id === id);
  if (!atual) return erro("Conta não encontrada.");
  const numero = (patch.conta ?? atual.conta).trim();
  if (!numero) return erro("Informe o número da conta.");
  if (numero !== atual.conta && contaPorNumero(ds, numero)) return erro("Já existe uma conta com esse número.");
  const novo: Conta = { ...atual, ...patch, conta: numero };
  const despesas =
    numero === atual.conta
      ? ds.despesas
      : ds.despesas.map((d) => (d.conta === atual.conta ? { ...d, conta: numero } : d));
  const fixas =
    numero === atual.conta
      ? ds.despesasFixas
      : ds.despesasFixas.map((f) => (f.conta === atual.conta ? { ...f, conta: numero } : f));
  const agendadas =
    numero === atual.conta
      ? ds.transferenciasAgendadas
      : ds.transferenciasAgendadas.map((t) => ({
          ...t,
          contaOrigem: t.contaOrigem === atual.conta ? numero : t.contaOrigem,
          contaDestino: t.contaDestino === atual.conta ? numero : t.contaDestino,
        }));
  return finaliza(
    {
      ...ds,
      contas: ds.contas.map((c) => (c.id === id ? novo : c)),
      despesas,
      despesasFixas: fixas,
      transferenciasAgendadas: agendadas,
    },
    {},
  );
}

export function contaTemDependencias(ds: Dataset, id: number): boolean {
  const c = ds.contas.find((x) => x.id === id);
  if (!c) return false;
  return ds.despesas.some((d) => d.conta === c.conta) || ds.cartoes.some((k) => k.contaId === id);
}

/** Sem `forcar`, bloqueia a exclusão de conta com lançamentos ou cartões. */
export function excluirConta(ds: Dataset, id: number, opcoes: { forcar?: boolean } = {}): Op {
  const c = ds.contas.find((x) => x.id === id);
  if (!c) return erro("Conta não encontrada.");
  if (!opcoes.forcar && contaTemDependencias(ds, id)) {
    return erro("A conta possui lançamentos ou cartões. Confirme a exclusão para removê-los também.");
  }
  const cartaoIds = new Set(ds.cartoes.filter((k) => k.contaId === id).map((k) => k.id));
  const grupos = new Set(
    ds.despesas.filter((d) => d.conta === c.conta && d.natureza === "TRANSFERENCIA" && d.grupoId).map((d) => d.grupoId),
  );
  const despesas = ds.despesas.filter(
    (d) =>
      d.conta !== c.conta &&
      !(d.cartaoId && cartaoIds.has(d.cartaoId)) &&
      !(d.natureza === "TRANSFERENCIA" && grupos.has(d.grupoId)),
  );
  return finaliza(
    {
      ...ds,
      contas: ds.contas.filter((x) => x.id !== id),
      cartoes: ds.cartoes.filter((k) => k.contaId !== id),
      despesas,
      despesasFixas: ds.despesasFixas.filter((f) => f.conta !== c.conta),
      transferenciasAgendadas: ds.transferenciasAgendadas.filter(
        (t) => t.contaOrigem !== c.conta && t.contaDestino !== c.conta,
      ),
    },
    {},
  );
}

// ------------------------------------------------------------ cartões (R18: físicos e virtuais)

export type NovoCartao = Omit<Cartao, "id" | "limiteDisponivel" | "cartaoPrincipalId"> & { cartaoPrincipalId?: number | null };

function validaCartao(ds: Dataset, c: NovoCartao): string | null {
  if (!c.nome.trim()) return "Informe o nome do cartão.";
  if (!(c.limiteTotal >= 0)) return "Limite inválido.";
  if (!Number.isInteger(c.diaFechamento) || c.diaFechamento < 1 || c.diaFechamento > 31) return "Dia de fechamento inválido (1 a 31).";
  if (!Number.isInteger(c.diaVencimento) || c.diaVencimento < 1 || c.diaVencimento > 31) return "Dia de vencimento inválido (1 a 31).";
  if (!ds.contas.some((x) => x.id === c.contaId)) return "Selecione a conta vinculada.";
  return null;
}

/** Campos que o virtual herda do principal. */
function herdadosDe(p: Cartao): Pick<Cartao, "tipo" | "limiteTotal" | "diaFechamento" | "diaVencimento" | "contaId"> {
  return { tipo: p.tipo, limiteTotal: p.limiteTotal, diaFechamento: p.diaFechamento, diaVencimento: p.diaVencimento, contaId: p.contaId };
}

function principalValido(ds: Dataset, id: number): Cartao | string {
  const p = ds.cartoes.find((c) => c.id === id);
  if (!p) return "Cartão físico não encontrado.";
  if (p.cartaoPrincipalId != null) return "Um cartão virtual não pode ser principal de outro virtual.";
  return p;
}

export function criarCartao(ds: Dataset, input: NovoCartao, ctx: Ctx): Op<{ cartao: Cartao }> {
  if (input.cartaoPrincipalId) {
    const p = principalValido(ds, input.cartaoPrincipalId);
    if (typeof p === "string") return erro(p);
    if (!input.nome.trim()) return erro("Informe o nome do cartão.");
    const { lista, item } = inserirItem<Cartao>(
      ds.cartoes,
      { nome: input.nome.trim(), finalCartao: input.finalCartao, ...herdadosDe(p), limiteDisponivel: p.limiteDisponivel, cartaoPrincipalId: p.id },
      ctx,
    );
    return finaliza({ ...ds, cartoes: lista }, { cartao: item });
  }
  const e = validaCartao(ds, input);
  if (e) return erro(e);
  const { lista, item } = inserirItem<Cartao>(
    ds.cartoes,
    { ...input, nome: input.nome.trim(), limiteTotal: round2(input.limiteTotal), limiteDisponivel: round2(input.limiteTotal), cartaoPrincipalId: null },
    ctx,
  );
  return finaliza({ ...ds, cartoes: lista }, { cartao: item });
}

export function editarCartao(ds: Dataset, id: number, patch: Partial<NovoCartao>): Op {
  const atual = ds.cartoes.find((c) => c.id === id);
  if (!atual) return erro("Cartão não encontrado.");
  if (patch.cartaoPrincipalId !== undefined && (patch.cartaoPrincipalId ?? null) !== atual.cartaoPrincipalId) {
    return erro("Não é possível alterar o vínculo físico/virtual de um cartão existente.");
  }

  if (atual.cartaoPrincipalId != null) {
    const p = principalValido(ds, atual.cartaoPrincipalId);
    if (typeof p === "string") return erro(p);
    const nome = (patch.nome ?? atual.nome).trim();
    if (!nome) return erro("Informe o nome do cartão.");
    const novo: Cartao = { ...atual, nome, finalCartao: patch.finalCartao ?? atual.finalCartao, ...herdadosDe(p) };
    return finaliza({ ...ds, cartoes: ds.cartoes.map((c) => (c.id === id ? novo : c)) }, {});
  }

  const novo: Cartao = { ...atual, ...patch, cartaoPrincipalId: null, limiteDisponivel: atual.limiteDisponivel };
  const e = validaCartao(ds, novo);
  if (e) return erro(e);
  novo.nome = novo.nome.trim();
  novo.limiteTotal = round2(novo.limiteTotal);
  // Propaga ao grupo: virtuais herdam os campos; compras do grupo acompanham a conta.
  const contaNova = ds.contas.find((c) => c.id === novo.contaId);
  const idsGrupo = new Set(ds.cartoes.filter((c) => c.id === id || c.cartaoPrincipalId === id).map((c) => c.id));
  const cartoes = ds.cartoes.map((c) => (c.id === id ? novo : c.cartaoPrincipalId === id ? { ...c, ...herdadosDe(novo) } : c));
  const despesas =
    contaNova && novo.contaId !== atual.contaId
      ? ds.despesas.map((d) => {
          const cid = cartaoIdDe(d);
          return cid !== null && idsGrupo.has(cid) ? { ...d, conta: contaNova.conta } : d;
        })
      : ds.despesas;
  const despesasFixas =
    contaNova && novo.contaId !== atual.contaId
      ? ds.despesasFixas.map((f) => (f.cartaoId !== null && idsGrupo.has(f.cartaoId) ? { ...f, conta: contaNova.conta } : f))
      : ds.despesasFixas;
  return finaliza({ ...ds, cartoes, despesas, despesasFixas }, {});
}

/** Regras de recorrência que pagam com o cartão (ou, no principal, com qualquer cartão do grupo). */
export function recorrenciasDoCartao(ds: Dataset, id: number): DespesaFixa[] {
  const ids = new Set(ds.cartoes.filter((c) => c.id === id || c.cartaoPrincipalId === id).map((c) => c.id));
  return ds.despesasFixas.filter((f) => f.cartaoId !== null && ids.has(f.cartaoId));
}

/** Compras do cartão (no principal, inclui as dos virtuais do grupo). */
export function cartaoTemCompras(ds: Dataset, id: number): boolean {
  const ids = new Set(ds.cartoes.filter((c) => c.id === id || c.cartaoPrincipalId === id).map((c) => c.id));
  return ds.despesas.some((d) => {
    const cid = cartaoIdDe(d);
    return cid !== null && ids.has(cid);
  });
}

/**
 * R18 - excluir. Virtual: só sem compras em aberto; o histórico pago passa ao principal.
 * Principal: só se o grupo não tem compras em aberto; remove virtuais e compras (histórico pago exige `forcar`).
 */
export function excluirCartao(ds: Dataset, id: number, opcoes: { forcar?: boolean } = {}): Op {
  const cartao = ds.cartoes.find((c) => c.id === id);
  if (!cartao) return erro("Cartão não encontrado.");

  // Mais seguro: bloqueia em vez de apagar/desvincular regras em silêncio.
  const regras = recorrenciasDoCartao(ds, id);
  if (regras.length > 0) {
    return erro(`Há recorrências que pagam com este cartão (${regras.map((r) => r.descricao).join(", ")}). Troque a forma de pagamento delas antes de excluir.`);
  }
  if (cartao.cartaoPrincipalId != null) {
    if (ds.despesas.some((d) => cartaoIdDe(d) === id && !d.pago)) {
      return erro("O cartão virtual possui compras em aberto. Pague a fatura antes de excluí-lo.");
    }
    const principal = cartao.cartaoPrincipalId;
    return finaliza(
      {
        ...ds,
        cartoes: ds.cartoes.filter((c) => c.id !== id),
        despesas: ds.despesas.map((d) => (cartaoIdDe(d) === id ? { ...d, cartaoId: principal } : d)),
      },
      {},
    );
  }

  const idsGrupo = new Set(ds.cartoes.filter((c) => c.id === id || c.cartaoPrincipalId === id).map((c) => c.id));
  const doGrupo = (d: Despesa) => {
    const cid = cartaoIdDe(d);
    return cid !== null && idsGrupo.has(cid);
  };
  if (ds.despesas.some((d) => doGrupo(d) && !d.pago)) {
    return erro("O cartão (ou algum virtual) possui compras em aberto. Pague a fatura antes de excluí-lo.");
  }
  if (!opcoes.forcar && ds.despesas.some(doGrupo)) {
    return erro("O cartão possui histórico de compras. Confirme a exclusão para removê-las também.");
  }
  return finaliza(
    { ...ds, cartoes: ds.cartoes.filter((c) => !idsGrupo.has(c.id)), despesas: ds.despesas.filter((d) => !doGrupo(d)) },
    {},
  );
}

// ------------------------------------------------------------ R7 pagar fatura

/** Paga a fatura única do grupo (R18); aceita o id de qualquer cartão do grupo. */
export function pagarFatura(ds: Dataset, cartaoId: number, mes: number, ano: number, ctx: Ctx): Op<{ pagamento: Despesa }> {
  const escolhido = ds.cartoes.find((c) => c.id === cartaoId);
  if (!escolhido) return erro("Cartão não encontrado.");
  const grupo = cartoesDoGrupo(escolhido, ds.cartoes);
  const cartao = grupo[0];
  const idsGrupo = new Set(grupo.map((c) => c.id));
  const conta = contaDoCartao(ds, cartao);
  if (!conta) return erro("A conta vinculada ao cartão não existe.");
  const itens = ds.despesas.filter((d) => {
    const cid = cartaoIdDe(d);
    if (cid === null || !idsGrupo.has(cid) || d.pago) return false;
    const f = faturaDaCompra(cartao, d.data);
    return f.mes === mes && f.ano === ano;
  });
  let cents = 0;
  for (const d of itens) cents += d.tipo === "DEBITO" ? toCents(d.valor) : -toCents(d.valor);
  if (cents <= 0) return erro("Não há valor em aberto nesta fatura.");
  const ids = new Set(itens.map((d) => d.id));
  const pagamento = montaDespesa(
    {
      descricao: `Fatura ${cartao.nome} ${String(mes).padStart(2, "0")}/${ano}`,
      valor: fromCents(cents),
      data: ctx.agora,
      categoria: "Cartão",
      conta: conta.conta,
      pic: "credit_card",
      tipo: "DEBITO",
      pago: true,
      natureza: "PAGAMENTO_FATURA",
      cartaoId: null,
      grupoId: `fatura:${cartao.id}:${ano}-${String(mes).padStart(2, "0")}`,
    },
    novoIdLong(idsDespesas(ds), ctx.agora),
  );
  const despesas = ds.despesas.map((d) => (ids.has(d.id) ? { ...d, pago: true } : d));
  return finaliza({ ...ds, despesas: [...despesas, pagamento] }, { pagamento });
}

// ------------------------------------------------------------ R9 transferências

export interface NovaTransferencia {
  origem: string;
  destino: string;
  valor: number;
  data?: number;
}

export function transferir(ds: Dataset, input: NovaTransferencia, ctx: Ctx): Op<{ grupoId: string }> {
  const valor = round2(input.valor);
  if (!(valor > 0)) return erro("O valor da transferência deve ser maior que zero.");
  if (input.origem === input.destino) return erro("Origem e destino devem ser contas diferentes.");
  const origem = contaPorNumero(ds, input.origem);
  const destino = contaPorNumero(ds, input.destino);
  if (!origem || !destino) return erro("Conta de origem ou destino não encontrada.");
  if (toCents(saldoConta(ds.despesas, origem.conta)) < toCents(valor)) {
    return erro("Saldo insuficiente na conta de origem.");
  }
  const grupoId = `transf:${uuidDe(ctx)}`;
  const data = input.data ?? ctx.agora;
  const usados = new Set(idsDespesas(ds));
  const idSaida = novoIdLong(usados, ctx.agora);
  usados.add(idSaida);
  const idEntrada = novoIdLong(usados, ctx.agora);
  const base = { valor, data, categoria: "Transferência", pic: "transferencia", pago: true, natureza: "TRANSFERENCIA" as const, grupoId };
  const saida = montaDespesa({ ...base, descricao: `Transferência para ${destino.banco}`, conta: origem.conta, tipo: "DEBITO" }, idSaida);
  const entrada = montaDespesa({ ...base, descricao: `Transferência de ${origem.banco}`, conta: destino.conta, tipo: "CREDITO" }, idEntrada);
  return finaliza({ ...ds, despesas: [...ds.despesas, saida, entrada] }, { grupoId });
}

export function agendarTransferencia(
  ds: Dataset,
  input: { dataAgendada: number; contaOrigem: string; contaDestino: string; valor: number },
  ctx: Ctx,
): Op {
  if (!(round2(input.valor) > 0)) return erro("O valor da transferência deve ser maior que zero.");
  if (input.contaOrigem === input.contaDestino) return erro("Origem e destino devem ser contas diferentes.");
  if (!contaPorNumero(ds, input.contaOrigem) || !contaPorNumero(ds, input.contaDestino)) {
    return erro("Conta de origem ou destino não encontrada.");
  }
  const { lista } = inserirItem(
    ds.transferenciasAgendadas,
    { ...input, valor: round2(input.valor), executada: false },
    ctx,
  );
  return finaliza({ ...ds, transferenciasAgendadas: lista }, {});
}

export interface ResultadoAgendadas {
  executadas: number;
  falhas: { id: number; erro: string }[];
}

/** Executa transferências agendadas vencidas (R9). As que falham permanecem pendentes. */
export function executarAgendadasVencidas(ds: Dataset, ctx: Ctx): Op<ResultadoAgendadas> {
  const vencidas = ds.transferenciasAgendadas
    .filter((t) => !t.executada && t.dataAgendada <= ctx.agora)
    .sort((a, b) => a.dataAgendada - b.dataAgendada || a.id - b.id);
  let atual = ds;
  let executadas = 0;
  const falhas: { id: number; erro: string }[] = [];
  for (const t of vencidas) {
    const r = transferir(atual, { origem: t.contaOrigem, destino: t.contaDestino, valor: t.valor, data: t.dataAgendada }, ctx);
    if (!r.ok) {
      falhas.push({ id: t.id, erro: r.erro });
      continue;
    }
    atual = {
      ...r.ds,
      transferenciasAgendadas: r.ds.transferenciasAgendadas.map((x) => (x.id === t.id ? { ...x, executada: true } : x)),
    };
    executadas++;
  }
  return finaliza(atual, { executadas, falhas });
}

// ------------------------------------------------------------ R10 metas

export function criarMeta(ds: Dataset, input: { nome: string; valorObjetivo: number; icone?: string; dataAlvo?: number | null }, ctx: Ctx): Op<{ meta: Meta }> {
  if (!input.nome.trim()) return erro("Informe o nome da meta.");
  if (!(input.valorObjetivo > 0)) return erro("O objetivo deve ser maior que zero.");
  const { lista, item } = inserirItem<Meta>(
    ds.metas,
    { nome: input.nome.trim(), valorObjetivo: round2(input.valorObjetivo), valorGuardado: 0, icone: input.icone ?? "ic_savings", dataAlvo: input.dataAlvo ?? null },
    ctx,
  );
  return finaliza({ ...ds, metas: lista }, { meta: item });
}

export function aportarMeta(ds: Dataset, input: { metaId: number; conta: string; valor: number }, ctx: Ctx): Op {
  const meta = ds.metas.find((m) => m.id === input.metaId);
  if (!meta) return erro("Meta não encontrada.");
  const valor = round2(input.valor);
  if (!(valor > 0)) return erro("O valor do aporte deve ser maior que zero.");
  const conta = contaPorNumero(ds, input.conta);
  if (!conta) return erro("Conta não encontrada.");
  if (toCents(saldoConta(ds.despesas, conta.conta)) < toCents(valor)) return erro("Saldo insuficiente na conta.");
  const lanc = montaDespesa(
    {
      descricao: `Aporte: ${meta.nome}`,
      valor,
      data: ctx.agora,
      categoria: "Reserva",
      conta: conta.conta,
      pic: "reserva",
      tipo: "DEBITO",
      pago: true,
      natureza: "APORTE_META",
      grupoId: `meta:${meta.id}`,
    },
    novoIdLong(idsDespesas(ds), ctx.agora),
  );
  return finaliza(
    {
      ...ds,
      metas: ds.metas.map((m) => (m.id === meta.id ? { ...m, valorGuardado: fromCents(toCents(m.valorGuardado) + toCents(valor)) } : m)),
      despesas: [...ds.despesas, lanc],
    },
    {},
  );
}

/** Exclui a meta; se houver valor guardado, exige conta destino e cria o RESGATE_META. */
export function excluirMeta(ds: Dataset, metaId: number, contaDestino: string | null, ctx: Ctx): Op {
  const meta = ds.metas.find((m) => m.id === metaId);
  if (!meta) return erro("Meta não encontrada.");
  let despesas = ds.despesas;
  if (toCents(meta.valorGuardado) > 0) {
    const conta = contaDestino ? contaPorNumero(ds, contaDestino) : undefined;
    if (!conta) return erro("Selecione a conta que receberá o valor guardado.");
    despesas = [
      ...despesas,
      montaDespesa(
        {
          descricao: `Resgate: ${meta.nome}`,
          valor: meta.valorGuardado,
          data: ctx.agora,
          categoria: "Reserva",
          conta: conta.conta,
          pic: "reserva",
          tipo: "CREDITO",
          pago: true,
          natureza: "RESGATE_META",
          grupoId: `meta:${meta.id}`,
        },
        novoIdLong(idsDespesas(ds), ctx.agora),
      ),
    ];
  }
  return finaliza({ ...ds, despesas, metas: ds.metas.filter((m) => m.id !== metaId) }, {});
}

// ------------------------------------------------------------ R16 despesas fixas

export type NovaDespesaFixa = Omit<DespesaFixa, "id" | "ultimaDataLancamento" | "cartaoId"> & { cartaoId?: number | null };

/** Resolve a origem do pagamento: com cartão, a conta é sempre a do cartão (R16). */
function origemFixa(ds: Dataset, conta: string, cartaoId: number | null | undefined): { conta: string; cartaoId: number | null } | string {
  if (cartaoId) {
    const cartao = ds.cartoes.find((c) => c.id === cartaoId);
    if (!cartao) return "Cartão não encontrado.";
    const c = contaDoCartao(ds, cartao);
    if (!c) return "A conta vinculada ao cartão não existe.";
    return { conta: c.conta, cartaoId: cartao.id };
  }
  if (!contaPorNumero(ds, conta)) return "Conta não encontrada.";
  return { conta, cartaoId: null };
}

export function editarDespesaFixa(
  ds: Dataset,
  id: number,
  patch: Partial<Omit<DespesaFixa, "id" | "ultimaDataLancamento">>,
): Op {
  const atual = ds.despesasFixas.find((f) => f.id === id);
  if (!atual) return erro("Recorrência não encontrada.");
  if (patch.diaVencimento !== undefined && (!Number.isInteger(patch.diaVencimento) || patch.diaVencimento < 1 || patch.diaVencimento > 31)) {
    return erro("Dia de vencimento inválido (1 a 31).");
  }
  if (patch.valor !== undefined && !(round2(patch.valor) > 0)) return erro("O valor deve ser maior que zero.");
  if (patch.descricao !== undefined && !patch.descricao.trim()) return erro("Informe a descrição.");
  const cartaoId = patch.cartaoId === undefined ? atual.cartaoId : patch.cartaoId || null;
  const origem = origemFixa(ds, patch.conta ?? atual.conta, cartaoId);
  if (typeof origem === "string") return erro(origem);
  const novo: DespesaFixa = {
    ...atual,
    ...patch,
    valor: patch.valor !== undefined ? round2(patch.valor) : atual.valor,
    descricao: patch.descricao !== undefined ? patch.descricao.trim() : atual.descricao,
    ...origem,
  };
  return finaliza({ ...ds, despesasFixas: ds.despesasFixas.map((f) => (f.id === id ? novo : f)) }, {});
}

export function criarDespesaFixa(ds: Dataset, input: NovaDespesaFixa, ctx: Ctx): Op<{ fixa: DespesaFixa }> {
  if (!input.descricao.trim()) return erro("Informe a descrição.");
  if (!(round2(input.valor) > 0)) return erro("O valor deve ser maior que zero.");
  if (!Number.isInteger(input.diaVencimento) || input.diaVencimento < 1 || input.diaVencimento > 31) {
    return erro("Dia de vencimento inválido (1 a 31).");
  }
  const origem = origemFixa(ds, input.conta, input.cartaoId);
  if (typeof origem === "string") return erro(origem);
  const { lista, item } = inserirItem<DespesaFixa>(
    ds.despesasFixas,
    { ...input, ...origem, descricao: input.descricao.trim(), valor: round2(input.valor), ultimaDataLancamento: null },
    ctx,
  );
  return finaliza({ ...ds, despesasFixas: lista }, { fixa: item });
}

const MAX_MESES_CATCHUP = 12;

/**
 * R16 - lança as despesas fixas pendentes (catch-up de até 12 meses). Idempotente:
 * o grupoId `fixa:<id>:<AAAA-MM>` evita duplicar um mês já lançado.
 */
export function processarDespesasFixas(ds: Dataset, ctx: Ctx): Op<{ criados: Despesa[] }> {
  const hoje = mesAnoDe(ctx.agora);
  const idxHoje = hoje.ano * 12 + hoje.mes - 1;
  const usados = new Set(idsDespesas(ds));
  const jaLancados = new Set(ds.despesas.map((d) => d.grupoId).filter((g): g is string => !!g));
  const criados: Despesa[] = [];
  const fixas = ds.despesasFixas.map((regra) => {
    const ultima = regra.ultimaDataLancamento;
    const idxUltima = ultima === null ? null : (() => {
      const u = mesAnoDe(ultima);
      return u.ano * 12 + u.mes - 1;
    })();
    let idxIni = idxUltima ?? idxHoje;
    idxIni = Math.max(idxIni, idxHoje - (MAX_MESES_CATCHUP - 1));
    let novaUltima = ultima;
    for (let idx = idxIni; idx <= idxHoje; idx++) {
      if (idxUltima !== null && idx <= idxUltima) continue;
      const { mes, ano } = normalizaMes((idx % 12) + 1, Math.floor(idx / 12));
      const dia = clampDia(regra.diaVencimento, mes, ano);
      const data = new Date(ano, mes - 1, dia).getTime();
      if (data > ctx.agora) continue;
      const grupoId = `fixa:${regra.id}:${ano}-${String(mes).padStart(2, "0")}`;
      if (jaLancados.has(grupoId)) {
        novaUltima = Math.max(novaUltima ?? 0, data);
        continue;
      }
      const id = novoIdLong(usados, ctx.agora);
      usados.add(id);
      // Regra com cartão: a ocorrência é uma compra no cartão, na conta dele (R16/R18).
      const cartaoRegra = regra.cartaoId ? ds.cartoes.find((c) => c.id === regra.cartaoId) : undefined;
      const contaRegra = cartaoRegra ? contaDoCartao(ds, cartaoRegra) : undefined;
      const cartaoOcorrencia = cartaoRegra && contaRegra ? cartaoRegra.id : null;
      const contaOcorrencia = cartaoOcorrencia !== null && contaRegra ? contaRegra.conta : regra.conta;
      criados.push(
        montaDespesa(
          {
            descricao: regra.descricao,
            valor: regra.valor,
            data,
            categoria: regra.categoria,
            conta: contaOcorrencia,
            pic: regra.pic,
            tipo: regra.tipo,
            cartaoId: cartaoOcorrencia,
            pago: false,
            grupoId,
          },
          id,
        ),
      );
      jaLancados.add(grupoId);
      novaUltima = Math.max(novaUltima ?? 0, data);
    }
    return novaUltima === ultima ? regra : { ...regra, ultimaDataLancamento: novaUltima };
  });
  if (criados.length === 0 && fixas.every((f, i) => f === ds.despesasFixas[i])) return finaliza(ds, { criados });
  return finaliza({ ...ds, despesasFixas: fixas, despesas: [...ds.despesas, ...criados] }, { criados });
}

// ------------------------------------------------------------ R14 snapshot

/** Um registro por mês/ano, atualizado no mês. */
export function registrarPatrimonio(ds: Dataset, ctx: Ctx): Dataset {
  const valorTotal = patrimonioLiquido(ds);
  // Sem nenhum dado financeiro não há o que registrar (evita um ponto zerado ao abrir vazio).
  if (valorTotal === 0 && ds.patrimonio.length === 0) return ds;
  const { mes, ano } = mesAnoDe(ctx.agora);
  const existente = ds.patrimonio.find((p) => {
    const m = mesAnoDe(p.dataMillis);
    return m.mes === mes && m.ano === ano;
  });
  if (existente) {
    if (existente.valorTotal === valorTotal) return ds;
    return {
      ...ds,
      patrimonio: ds.patrimonio.map((p) => (p.id === existente.id ? { ...p, valorTotal, dataMillis: ctx.agora } : p)),
    };
  }
  const { lista } = inserirItem(
    ds.patrimonio,
    { dataMillis: ctx.agora, valorTotal, mesReferencia: MESES_ABREV[mes - 1] },
    ctx,
  );
  return { ...ds, patrimonio: lista };
}

// ------------------------------------------------------------ R31 lixeira

export const DIAS_LIXEIRA = 30;

function lerPayload(l: Lixeira): Despesa | null {
  try {
    const d = JSON.parse(l.payload) as Partial<Despesa>;
    if (typeof d !== "object" || d === null || typeof d.id !== "number" || typeof d.valor !== "number") return null;
    return { autor: null, grupoId: null, cartaoId: null, ...d } as Despesa;
  } catch {
    return null;
  }
}

/** Restaura o item (e o par, se for transferência). Mesmo id, ou novo id se estiver ocupado. */
export function restaurarDaLixeira(ds: Dataset, lixeiraId: number, ctx: Ctx): Op<{ restaurados: Despesa[] }> {
  const item = ds.lixeira.find((l) => l.id === lixeiraId);
  if (!item) return erro("Item não encontrado na lixeira.");
  const base = lerPayload(item);
  if (!base) return erro("O conteúdo deste item está corrompido e não pode ser restaurado.");
  let itens = [item];
  if (base.natureza === "TRANSFERENCIA" && base.grupoId) {
    itens = ds.lixeira.filter((l) => lerPayload(l)?.grupoId === base.grupoId);
  }
  const usados = new Set(ds.despesas.map((d) => d.id));
  const restaurados: Despesa[] = [];
  for (const l of itens) {
    const d = lerPayload(l);
    if (!d) return erro("O conteúdo deste item está corrompido e não pode ser restaurado.");
    if (!contaPorNumero(ds, d.conta)) return erro("A conta deste lançamento não existe mais.");
    const cid = cartaoIdDe(d);
    if (cid !== null && !ds.cartoes.some((c) => c.id === cid)) return erro("O cartão deste lançamento não existe mais.");
    const idFinal = usados.has(d.id) ? novoIdLong(usados, ctx.agora) : d.id;
    usados.add(idFinal);
    restaurados.push({ ...d, id: idFinal });
  }
  const remover = new Set(itens.map((l) => l.id));
  return finaliza(
    { ...ds, despesas: [...ds.despesas, ...restaurados], lixeira: ds.lixeira.filter((l) => !remover.has(l.id)) },
    { restaurados },
  );
}

export function esvaziarLixeira(ds: Dataset): Dataset {
  return ds.lixeira.length === 0 ? ds : { ...ds, lixeira: [] };
}

/** Remove itens com mais de 30 dias (preserva a referência se nada mudou). */
export function purgarLixeira(ds: Dataset, agora: number): Dataset {
  const limite = DIAS_LIXEIRA * 86_400_000;
  const mantidos = ds.lixeira.filter((l) => agora - l.excluidoEm <= limite);
  return mantidos.length === ds.lixeira.length ? ds : { ...ds, lixeira: mantidos };
}

// ------------------------------------------------------------ R23 duplicar e repetir

export type UnidadeRepeticao = "DIAS" | "SEMANAS" | "MESES";

function copiaBase(d: Despesa, id: number, data: number, grupoId: string | null): Despesa {
  const { mes, ano } = mesAnoDe(data);
  return { ...d, id, data, mes, ano, pago: false, grupoId, fitid: null, conciliadoEm: null };
}

/** Duplica um lançamento NORMAL: data = agora, pendente, novo id, sem grupo. */
export function duplicarLancamento(ds: Dataset, id: number, ctx: Ctx): Op<{ criado: Despesa }> {
  const d = ds.despesas.find((x) => x.id === id);
  if (!d) return erro("Lançamento não encontrado.");
  if (d.natureza !== "NORMAL") return erro("Transferências, faturas e aportes não podem ser duplicados.");
  const criado = copiaBase(d, novoIdLong(idsDespesas(ds), ctx.agora), ctx.agora, null);
  return finaliza({ ...ds, despesas: [...ds.despesas, criado] }, { criado });
}

export const MAX_REPETICOES = 120;

/** Repete a partir da data base: ocorrência k (1..n) em base + k·intervalo. */
export function repetirLancamento(
  ds: Dataset,
  id: number,
  opcoes: { n: number; intervalo: number; unidade: UnidadeRepeticao },
  ctx: Ctx,
): Op<{ criados: Despesa[] }> {
  const d = ds.despesas.find((x) => x.id === id);
  if (!d) return erro("Lançamento não encontrado.");
  if (d.natureza !== "NORMAL") return erro("Transferências, faturas e aportes não podem ser repetidos.");
  const { n, intervalo, unidade } = opcoes;
  if (!Number.isInteger(n) || n < 1 || n > MAX_REPETICOES) return erro(`Informe de 1 a ${MAX_REPETICOES} repetições.`);
  if (!Number.isInteger(intervalo) || intervalo < 1) return erro("O intervalo deve ser um inteiro maior que zero.");
  const grupoId = `rep:${uuidDe(ctx)}`;
  const usados = new Set(idsDespesas(ds));
  const criados: Despesa[] = [];
  for (let k = 1; k <= n; k++) {
    let data: number;
    if (unidade === "MESES") data = somaMeses(d.data, k * intervalo);
    else {
      const t = new Date(d.data);
      t.setDate(t.getDate() + k * intervalo * (unidade === "SEMANAS" ? 7 : 1));
      data = t.getTime();
    }
    const nid = novoIdLong(usados, ctx.agora);
    usados.add(nid);
    criados.push(copiaBase(d, nid, data, grupoId));
  }
  return finaliza({ ...ds, despesas: [...ds.despesas, ...criados] }, { criados });
}

// ------------------------------------------------------------ R40 antecipação de pagamento

const dataCurta = (ms: number) => new Date(ms).toLocaleDateString("pt-BR");

export interface Antecipacao {
  /** Lançamentos em aberto, na ordem em que o valor quitado é consumido. */
  ids: number[];
  /** Dinheiro que realmente sai da conta (já com o desconto abatido). */
  valorPago: number;
  /** Desconto concedido (juros abatidos). A dívida quitada é `valorPago + desconto`. */
  desconto?: number;
  /** Data do pagamento; padrão = agora. */
  data?: number;
}

export interface ResultadoAntecipacao {
  pagos: Despesa[];
  /** Soma dos descontos efetivamente aplicados. */
  economia: number;
  /** Quanto ainda falta pagar dos lançamentos selecionados (parcial). */
  restante: number;
}

/**
 * R40 - paga adiantado um ou mais lançamentos em aberto (parcelas de empréstimo, boletos, contas...).
 * A dívida quitada (E = pago + desconto) consome os lançamentos na ordem recebida; o desconto é
 * rateado proporcionalmente. Cada lançamento quitado passa a valer só o que foi pago (o desconto
 * não é receita nem despesa), fica pago na data do pagamento e guarda o vencimento original na
 * descrição. Se E < total, o último lançamento é dividido: a parte paga vira um lançamento pago e o
 * restante continua em aberto. Compras de cartão ficam de fora: são pagas pela fatura.
 */
export function anteciparPagamento(ds: Dataset, input: Antecipacao, ctx: Ctx): Op<ResultadoAntecipacao> {
  const unicos = [...new Set(input.ids)];
  if (unicos.length === 0) return erro("Selecione ao menos um lançamento.");
  const itens: Despesa[] = [];
  for (const id of unicos) {
    const d = ds.despesas.find((x) => x.id === id);
    if (!d) return erro("Lançamento não encontrado.");
    if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO") return erro("Só despesas comuns podem ser antecipadas.");
    if (d.pago) return erro(`"${d.descricao}" já está pago.`);
    if (cartaoIdDe(d) !== null) return erro("Compras de cartão são pagas pela fatura; antecipe o pagamento da fatura.");
    itens.push(d);
  }
  const conta = itens[0].conta;
  if (itens.some((d) => d.conta !== conta)) return erro("Os lançamentos devem ser da mesma conta.");
  if (!contaPorNumero(ds, conta)) return erro("Conta não encontrada.");

  const pagoC = toCents(input.valorPago);
  const descC = toCents(input.desconto ?? 0);
  if (!(pagoC > 0)) return erro("Informe o valor pago, maior que zero.");
  if (descC < 0) return erro("O desconto não pode ser negativo.");
  const quitadoC = pagoC + descC;
  const devidoC = itens.reduce((s, d) => s + toCents(d.valor), 0);
  if (quitadoC > devidoC) return erro("O valor pago mais o desconto excede o que está em aberto nos lançamentos selecionados.");

  const data = input.data ?? ctx.agora;
  if (!Number.isFinite(data)) return erro("Data inválida.");
  const { mes, ano } = mesAnoDe(data);

  // 1) consome a dívida quitada na ordem recebida
  let sobra = quitadoC;
  const partes: { d: Despesa; quitado: number }[] = [];
  for (const d of itens) {
    if (sobra <= 0) break;
    const q = Math.min(sobra, toCents(d.valor));
    partes.push({ d, quitado: q });
    sobra -= q;
  }

  // 2) rateia o desconto proporcionalmente (o último recebe o resto, em centavos)
  let descRestante = descC;
  const calculo = partes.map(({ d, quitado }, i) => {
    const desc = i === partes.length - 1 ? descRestante : Math.floor((descC * quitado) / quitadoC);
    descRestante -= desc;
    return { d, quitado, desc, pago: quitado - desc };
  });
  if (calculo.some((c) => c.pago <= 0)) return erro("O desconto é grande demais para ser distribuído entre os lançamentos.");

  // 3) aplica: quitado por inteiro vira pago; o último pode ser dividido
  const usados = new Set(idsDespesas(ds));
  const substituidos = new Map<number, Despesa>();
  const novos: Despesa[] = [];
  const pagos: Despesa[] = [];
  for (const { d, quitado, desc, pago } of calculo) {
    const sufixo = desc > 0 ? ` (antecipada de ${dataCurta(d.data)}, desc. ${fromCents(desc).toFixed(2).replace(".", ",")})` : ` (antecipada de ${dataCurta(d.data)})`;
    const base = { descricao: `${d.descricao}${sufixo}`, valor: fromCents(pago), data, mes, ano, pago: true, valorOriginal: fromCents(pago), conciliadoEm: null, fitid: null };
    if (quitado === toCents(d.valor)) {
      const novo = { ...d, ...base, fitid: d.fitid, conciliadoEm: d.conciliadoEm };
      substituidos.set(d.id, novo);
      pagos.push(novo);
    } else {
      // parcial: o que sobra continua em aberto, com o vencimento original
      const resto = toCents(d.valor) - quitado;
      substituidos.set(d.id, { ...d, valor: fromCents(resto), valorOriginal: fromCents(resto) });
      const id = novoIdLong(usados, ctx.agora);
      usados.add(id);
      const parte = { ...d, ...base, id, descricao: `${d.descricao} (adiantamento${desc > 0 ? `, desc. ${fromCents(desc).toFixed(2).replace(".", ",")}` : ""})`, fitid: null };
      novos.push(parte);
      pagos.push(parte);
    }
  }
  const despesas = ds.despesas.map((d) => substituidos.get(d.id) ?? d);
  const economia = fromCents(descC);
  return finaliza({ ...ds, despesas: [...despesas, ...novos] }, { pagos, economia, restante: fromCents(devidoC - quitadoC) });
}

export interface AdiantamentoFixa {
  fixaId: number;
  mes: number;
  ano: number;
  /** Quanto sai da conta; abaixo do valor da regra = desconto. */
  valorPago: number;
  data?: number;
}

/**
 * R40 - paga agora a ocorrência futura de uma recorrência (ex.: o aluguel do mês que vem). Cria o
 * lançamento do mês pago, com o `grupoId` da recorrência, de modo que o processamento mensal não o
 * lance de novo (R16 é idempotente por grupoId).
 */
export function adiantarOcorrenciaFixa(ds: Dataset, input: AdiantamentoFixa, ctx: Ctx): Op<ResultadoAntecipacao> {
  const regra = ds.despesasFixas.find((f) => f.id === input.fixaId);
  if (!regra) return erro("Recorrência não encontrada.");
  if (regra.tipo !== "DEBITO") return erro("Só despesas recorrentes podem ser antecipadas.");
  if (regra.cartaoId) return erro("Recorrência no cartão entra na fatura; antecipe o pagamento da fatura.");
  if (!Number.isInteger(input.mes) || input.mes < 1 || input.mes > 12 || !Number.isInteger(input.ano)) return erro("Mês inválido.");
  const venc = new Date(input.ano, input.mes - 1, clampDia(regra.diaVencimento, input.mes, input.ano)).getTime();
  const grupoId = `fixa:${regra.id}:${input.ano}-${String(input.mes).padStart(2, "0")}`;
  const existente = ds.despesas.find((d) => d.grupoId === grupoId);
  if (existente) {
    if (existente.pago) return erro("Esse mês já está pago.");
    return anteciparPagamento(ds, { ids: [existente.id], valorPago: input.valorPago, desconto: round2(existente.valor - input.valorPago), data: input.data }, ctx);
  }
  if (venc <= ctx.agora) return erro("Esse mês já venceu; use o lançamento pendente da recorrência.");
  const criado = montaDespesa(
    { descricao: regra.descricao, valor: regra.valor, data: venc, categoria: regra.categoria, conta: regra.conta, pic: regra.pic, tipo: "DEBITO", pago: false, grupoId },
    novoIdLong(idsDespesas(ds), ctx.agora),
  );
  const r = anteciparPagamento(
    { ...ds, despesas: [...ds.despesas, criado] },
    { ids: [criado.id], valorPago: input.valorPago, desconto: round2(regra.valor - input.valorPago), data: input.data },
    ctx,
  );
  return r;
}

/** Lançamentos em aberto, da mesma conta, que podem ser antecipados junto com `d` (mesmo parcelamento/repetição). */
export function antecipaveisDoGrupo(ds: Dataset, d: Despesa): Despesa[] {
  if (d.natureza !== "NORMAL" || d.tipo !== "DEBITO" || d.pago || cartaoIdDe(d) !== null) return [];
  const mesmoGrupo = d.grupoId && (d.grupoId.startsWith("parc:") || d.grupoId.startsWith("rep:"));
  return ds.despesas
    .filter((x) => (mesmoGrupo ? x.grupoId === d.grupoId : x.id === d.id) && !x.pago && x.natureza === "NORMAL" && x.tipo === "DEBITO" && cartaoIdDe(x) === null && x.conta === d.conta)
    .sort((a, b) => a.data - b.data || a.id - b.id);
}
