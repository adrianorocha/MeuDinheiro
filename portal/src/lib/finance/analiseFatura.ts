import { casar, janelaPadrao } from "../conciliacao/matching";
import { ehPagamentoDeFatura } from "../conciliacao/plano";
import { detectarParcela, normalizarDescricaoBanco } from "../conciliacao/texto-banco";
import type { ArquivoExtrato, TransacaoBanco } from "../conciliacao/tipos";
import { similaridadeDescricao } from "../conciliacao/matching";
import { cartaoIdDe, cartoesDoGrupo, faturaDaCompra, itensFatura, principalDe, resumoFatura } from "./calc";
import { formatBRL } from "../format";
import { fromCents, toCents } from "./money";
import { pareceCreditoDePagamento } from "./reprocessar";
import type { Cartao, Dataset, Despesa } from "./types";

/**
 * R107 - "Analisar fatura": por que a fatura EM ABERTO do app difere do valor que o app do banco mostra?
 *
 * Puro (não altera nada). Parte do valor informado pelo usuário e testa, do mais provável ao menos, as causas clássicas de
 * divergência em fatura de cartão: item duplicado, estorno lançado com o sinal trocado, pagamento da fatura anterior dentro do
 * cartão, item marcado como pago que o banco ainda cobra, compra na fronteira do fechamento (outro ciclo), dia de fechamento
 * diferente do banco, parcela não lançada, IOF/encargo não lançado e, por fim, combinações de itens que somam a diferença.
 * Com o arquivo do extrato (OFX/CSV) a análise passa a ser linha a linha: o que só existe no banco, o que só existe no app,
 * o que difere no valor e o que o app colocou em outra fatura.
 *
 * Convenção: diferença = banco − app (em aberto). Positiva: o banco cobra MAIS (falta algo no app). Negativa: o app tem a MAIS.
 */

export type TipoHipotese =
  | "DUPLICADO"
  | "ITEM_UNICO"
  | "ESTORNO_INVERTIDO"
  | "ESTORNO_PENDENTE"
  | "PAGAMENTO_NO_CARTAO"
  | "PAGO_INDEVIDO"
  | "FRONTEIRA_CICLO"
  | "FECHAMENTO_DIFERENTE"
  | "PARCELA_FALTANTE"
  | "ENCARGO_IOF"
  | "COMBINACAO"
  | "NAO_LANCADO"
  | "SO_NO_APP"
  | "VALOR_DIFERENTE"
  | "OUTRA_FATURA";

export type Confianca = "alta" | "media" | "baixa";

export type AcaoHipotese =
  | { tipo: "REMOVER"; id: number }
  | { tipo: "MARCAR_ABERTO"; id: number }
  | { tipo: "MARCAR_PAGO"; id: number };

export interface Hipotese {
  /** estável entre análises (chave de lista) */
  chave: string;
  tipo: TipoHipotese;
  confianca: Confianca;
  titulo: string;
  detalhe: string;
  /** lançamentos envolvidos (apontados na tela) */
  lancamentoIds: number[];
  /** quanto desta hipótese explica da diferença, em centavos, com o sinal da diferença (banco − app); 0 = informativo */
  explicaCentavos: number;
  /** ação de um clique sobre UM lançamento (nunca em lote; o usuário confirma) */
  acao: (AcaoHipotese & { rotulo: string }) | null;
}

export interface LinhaExtrato {
  transacao: TransacaoBanco;
  lancamentoId?: number;
}

export interface AnaliseExtrato {
  /** transações consideradas (a linha de pagamento da fatura fica de fora) */
  total: number;
  conciliadas: number;
  /** soma do extrato (compras − estornos), em R$ */
  totalExtrato: number;
  /** total do extrato − valor informado do banco (≠ 0: o arquivo não é exatamente a fatura informada) */
  extratoMenosBanco: number;
  soNoExtrato: LinhaExtrato[];
  soNoApp: Despesa[];
  valorDiferente: { transacao: TransacaoBanco; lancamento: Despesa; deltaCentavos: number }[];
  outraFatura: { transacao: TransacaoBanco; lancamento: Despesa }[];
  /** parte da diferença (extrato × app) que as linhas acima explicam, em centavos */
  explicadoCentavos: number;
  /** o que sobra sem explicação, em centavos (0 = o extrato explica tudo) */
  restanteCentavos: number;
  /** o extrato (sem as linhas de pagamento) bate com o valor informado do banco */
  extratoBateBanco: boolean;
}

export interface ResultadoAnalise {
  cartao: Cartao;
  mes: number;
  ano: number;
  /** soma dos itens da fatura (compras − estornos), pagos ou não */
  totalApp: number;
  emAberto: number;
  totalBanco: number;
  /** banco − app em aberto (R$) */
  diferenca: number;
  /** "BATE" | "BANCO_MAIOR" (falta algo no app) | "APP_MAIOR" (sobra algo no app) */
  situacao: "BATE" | "BANCO_MAIOR" | "APP_MAIOR";
  hipoteses: Hipotese[];
  extrato: AnaliseExtrato | null;
  /** frase de abertura para a tela */
  resumo: string;
}

export interface EntradaAnalise {
  ds: Pick<Dataset, "despesas" | "cartoes">;
  cartaoId: number;
  mes: number;
  ano: number;
  /** valor da fatura no app do banco (R$, positivo = a pagar) */
  totalBanco: number;
  extrato?: ArquivoExtrato | null;
}

const DIA_MS = 86_400_000;
const MAX_HIPOTESES = 14;
const REGEX_ESTORNO = /(estorn|devolu|cancel|reembols|cr[eé]dito|chargeback|ajuste a cr)/i;
const TAXAS_IOF = [
  { pct: 3.5, rotulo: "IOF de 3,5% (compra internacional)" },
  { pct: 6.38, rotulo: "IOF de 6,38% (compra internacional, alíquota anterior)" },
  { pct: 4.38, rotulo: "IOF de 4,38% (compra internacional parcelada/anterior)" },
];

const brl = (c: number) => formatBRL(fromCents(c));
const dataCurta = (ms: number) => new Date(ms).toLocaleDateString("pt-BR");
const assinado = (d: Despesa) => (d.tipo === "DEBITO" ? toCents(d.valor) : -toCents(d.valor));
const diaInicio = (ms: number) => {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
};
const rotulo = (d: Despesa) => `"${d.descricao}" ${brl(toCents(d.valor))} em ${dataCurta(d.data)}`;

/** Menores subconjuntos (tamanho 2 a 4) de `itens` cuja soma é exatamente `alvo`; no máximo `limite` resultados. */
export function subconjuntosQueSomam(itens: readonly { id: number; cents: number }[], alvo: number, tamMax = 4, limite = 6): number[][] {
  const out: number[][] = [];
  const lista = itens.filter((i) => i.cents > 0 && i.cents < alvo);
  const n = lista.length;
  if (alvo <= 0 || n < 2) return out;
  const vistos = new Set<string>();
  const adiciona = (idx: number[]) => {
    const ids = idx.map((i) => lista[i].id).sort((a, b) => a - b);
    const chave = ids.join(",");
    if (vistos.has(chave)) return false;
    vistos.add(chave);
    out.push(ids);
    return out.length >= limite;
  };
  // pares
  for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) if (lista[i].cents + lista[j].cents === alvo && adiciona([i, j])) return out;
  // trios (índice por valor para o terceiro)
  if (tamMax >= 3) {
    const porValor = new Map<number, number[]>();
    lista.forEach((it, i) => porValor.set(it.cents, [...(porValor.get(it.cents) ?? []), i]));
    for (let i = 0; i < n; i++)
      for (let j = i + 1; j < n; j++) {
        const falta = alvo - lista[i].cents - lista[j].cents;
        if (falta <= 0) continue;
        for (const k of porValor.get(falta) ?? []) if (k > j && adiciona([i, j, k])) return out;
      }
  }
  // quartetos: pares × pares (só em listas pequenas, para manter o custo baixo)
  if (tamMax >= 4 && n <= 60) {
    const pares = new Map<number, [number, number][]>();
    for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) pares.set(lista[i].cents + lista[j].cents, [...(pares.get(lista[i].cents + lista[j].cents) ?? []), [i, j]]);
    for (const [soma, ps] of pares) {
      const resto = pares.get(alvo - soma);
      if (!resto) continue;
      for (const [a, b] of ps) for (const [c, d] of resto) if (a < b && b < c && c < d && adiciona([a, b, c, d])) return out;
    }
  }
  return out;
}

/** Pares de itens que parecem o mesmo lançamento duas vezes (mesmo valor, descrição parecida, até 3 dias de distância). */
export function paresDuplicados(itens: readonly Despesa[]): [Despesa, Despesa][] {
  const out: [Despesa, Despesa][] = [];
  const ordenados = [...itens].sort((a, b) => a.data - b.data || a.id - b.id);
  for (let i = 0; i < ordenados.length; i++)
    for (let j = i + 1; j < ordenados.length; j++) {
      const a = ordenados[i];
      const b = ordenados[j];
      if (diaInicio(b.data) - diaInicio(a.data) > 3 * DIA_MS) break;
      if (a.tipo !== b.tipo || toCents(a.valor) !== toCents(b.valor)) continue;
      if (similaridadeDescricao(a.descricao, b.descricao) >= 0.6) out.push([a, b]);
    }
  return out;
}

function chaveParcela(d: Despesa): { base: string; i: number; n: number } | null {
  const p = detectarParcela(d.descricao);
  if (!p) return null;
  return { base: normalizarDescricaoBanco(d.descricao.replace(/\(?\s*\d{1,2}\s*\/\s*\d{1,2}\s*\)?/g, " ")), i: p.i, n: p.n };
}

export function analisarFatura(e: EntradaAnalise): ResultadoAnalise | null {
  const escolhido = e.ds.cartoes.find((c) => c.id === e.cartaoId);
  if (!escolhido) return null;
  const grupo = cartoesDoGrupo(escolhido, e.ds.cartoes);
  const principal = principalDe(escolhido, e.ds.cartoes);
  const ids = new Set(grupo.map((c) => c.id));
  const doGrupo = e.ds.despesas.filter((d) => {
    const cid = cartaoIdDe(d);
    return cid !== null && ids.has(cid);
  });
  const fat = resumoFatura(principal, e.ds.despesas, e.mes, e.ano, e.ds.cartoes);
  const bancoC = toCents(e.totalBanco);
  const abertoC = toCents(fat.emAberto);
  const totalC = toCents(fat.total);
  const dif = bancoC - abertoC;
  const alvo = Math.abs(dif);
  const situacao: ResultadoAnalise["situacao"] = dif === 0 ? "BATE" : dif > 0 ? "BANCO_MAIOR" : "APP_MAIOR";

  const itens = fat.itens;
  const abertos = itens.filter((d) => !d.pago);
  const debitosAbertos = abertos.filter((d) => d.tipo === "DEBITO");
  const creditos = itens.filter((d) => d.tipo === "CREDITO");
  const hip: Hipotese[] = [];
  const usado = new Set<string>();
  const add = (h: Hipotese) => {
    if (usado.has(h.chave)) return;
    usado.add(h.chave);
    hip.push(h);
  };

  // ------------------------------------------------------------------ extrato linha a linha
  let extrato: AnaliseExtrato | null = null;
  if (e.extrato && e.extrato.transacoes.length > 0) {
    const trans = e.extrato.transacoes.filter((t) => !(t.valor > 0 && ehPagamentoDeFatura(t.descricao)));
    const noCiclo = new Set(itens.map((d) => d.id));
    const r = casar(trans, doGrupo, { janelaDias: janelaPadrao({ tipo: "CARTAO", cartaoId: principal.id }), cartoes: grupo });
    const soNoExtrato: LinhaExtrato[] = [];
    const valorDiferente: AnaliseExtrato["valorDiferente"] = [];
    const outraFatura: AnaliseExtrato["outraFatura"] = [];
    let conciliadas = 0;
    let explicado = 0;
    const porId = new Map(doGrupo.map((d) => [d.id, d]));
    for (const it of r.itens) {
      if (it.classe === "DUPLICADO") {
        conciliadas++;
        continue;
      }
      if (it.classe === "SO_NO_EXTRATO" || !it.lancamentoId) {
        soNoExtrato.push({ transacao: it.transacao });
        explicado += -toCents(it.transacao.valor); // compra (valor negativo no extrato) = banco cobra a mais
        continue;
      }
      const l = porId.get(it.lancamentoId);
      if (!l) continue;
      conciliadas++;
      if (!noCiclo.has(l.id)) {
        outraFatura.push({ transacao: it.transacao, lancamento: l });
        explicado += -toCents(it.transacao.valor);
        continue;
      }
      const delta = -toCents(it.transacao.valor) - assinado(l);
      if (it.tipo === "DIFERENCA" && delta !== 0) {
        valorDiferente.push({ transacao: it.transacao, lancamento: l, deltaCentavos: delta });
        explicado += delta;
      }
    }
    const casados = new Set(r.itens.filter((i) => i.lancamentoId).map((i) => i.lancamentoId as number));
    const soNoApp = r.soNoApp.filter((d) => noCiclo.has(d.id) && !casados.has(d.id));
    for (const d of soNoApp) explicado -= assinado(d);
    const totalExtrato = trans.reduce((s, t) => s - toCents(t.valor), 0);
    const difExtratoApp = totalExtrato - totalC;
    extrato = {
      total: trans.length,
      conciliadas,
      totalExtrato: fromCents(totalExtrato),
      extratoMenosBanco: fromCents(totalExtrato - bancoC),
      soNoExtrato,
      soNoApp,
      valorDiferente,
      outraFatura,
      explicadoCentavos: explicado,
      restanteCentavos: difExtratoApp - explicado,
      extratoBateBanco: totalExtrato === bancoC,
    };

    for (const l of soNoExtrato)
      add({
        chave: `ext-falta:${l.transacao.fitid}`,
        tipo: "NAO_LANCADO",
        confianca: "alta",
        titulo: `Está no extrato e falta no app: "${l.transacao.descricao}" ${brl(Math.abs(toCents(l.transacao.valor)))}`,
        detalhe: `Compra de ${dataCurta(l.transacao.data)} que o banco cobra e o app não tem. Lance-a (ou concilie o extrato em Conciliação) e a fatura passa a bater.`,
        lancamentoIds: [],
        explicaCentavos: -toCents(l.transacao.valor),
        acao: null,
      });
    for (const d of soNoApp)
      add({
        chave: `ext-sobra:${d.id}`,
        tipo: "SO_NO_APP",
        confianca: "alta",
        titulo: `Está no app e não aparece no extrato: ${rotulo(d)}`,
        detalhe: "O banco não cobra este item nesta fatura: pode ser duplicado, lançado com a data errada, já estornado ou de outra fatura.",
        lancamentoIds: [d.id],
        explicaCentavos: -assinado(d),
        acao: { tipo: "REMOVER", id: d.id, rotulo: "Remover (vai para a lixeira)" },
      });
    for (const v of valorDiferente)
      add({
        chave: `ext-valor:${v.lancamento.id}`,
        tipo: "VALOR_DIFERENTE",
        confianca: "alta",
        titulo: `Valor diferente: app ${brl(toCents(v.lancamento.valor))}, banco ${brl(Math.abs(toCents(v.transacao.valor)))} (${v.lancamento.descricao})`,
        detalhe: `Compra de ${dataCurta(v.transacao.data)}. Costuma ser arredondamento, IOF/câmbio de compra internacional ou valor digitado errado. Corrija o lançamento para o valor do banco.`,
        lancamentoIds: [v.lancamento.id],
        explicaCentavos: v.deltaCentavos,
        acao: null,
      });
    for (const o of outraFatura)
      add({
        chave: `ext-outra:${o.lancamento.id}`,
        tipo: "OUTRA_FATURA",
        confianca: "alta",
        titulo: `O banco põe nesta fatura, mas o app a coloca em outra: ${rotulo(o.lancamento)}`,
        detalhe: `Pelo fechamento cadastrado (dia ${principal.diaFechamento}) a compra cai em outro ciclo. Confira o dia de fechamento do cartão ou a data da compra.`,
        lancamentoIds: [o.lancamento.id],
        explicaCentavos: -toCents(o.transacao.valor),
        acao: null,
      });
  }

  if (dif !== 0) {
    const sinalBanco = dif > 0;

    // ---------------------------------------------------------------- app tem a MAIS
    if (!sinalBanco) {
      const iguais = debitosAbertos.filter((d) => toCents(d.valor) === alvo);
      const duplas = paresDuplicados(itens.filter((d) => d.tipo === "DEBITO"));
      for (const [a, b] of duplas) {
        const exato = toCents(a.valor) === alvo;
        add({
          chave: `dup:${a.id}:${b.id}`,
          tipo: "DUPLICADO",
          confianca: exato ? "alta" : "media",
          titulo: `Possível lançamento duplicado: ${rotulo(a)} e ${rotulo(b)}`,
          detalhe: exato
            ? `Os dois têm o mesmo valor e datas próximas, e esse valor é exatamente a diferença de ${brl(alvo)}. Remover um deles faz a fatura bater.`
            : "Mesmo valor, descrição parecida e datas próximas: se o banco cobrou uma vez só, um deles é duplicado.",
          lancamentoIds: [a.id, b.id],
          explicaCentavos: exato ? -toCents(b.valor) : 0,
          acao: exato ? { tipo: "REMOVER", id: b.id, rotulo: "Remover o segundo (lixeira)" } : null,
        });
      }
      for (const d of iguais) {
        add({
          chave: `unico:${d.id}`,
          tipo: "ITEM_UNICO",
          confianca: iguais.length === 1 ? "alta" : "media",
          titulo: `Um item tem exatamente o valor da diferença: ${rotulo(d)}`,
          detalhe: `${iguais.length > 1 ? `${iguais.length} itens têm esse valor; confira qual o banco não cobra. ` : ""}Pode estar duplicado, já pago por outro meio, estornado ou pertencer a outra fatura.`,
          lancamentoIds: [d.id],
          explicaCentavos: -toCents(d.valor),
          acao: { tipo: "MARCAR_PAGO", id: d.id, rotulo: "Marcar como pago" },
        });
      }
      for (const d of debitosAbertos)
        if (toCents(d.valor) * 2 === alvo)
          add({
            chave: `estorno-inv:${d.id}`,
            tipo: "ESTORNO_INVERTIDO",
            confianca: REGEX_ESTORNO.test(d.descricao) ? "alta" : "media",
            titulo: `Parece um estorno lançado como compra: ${rotulo(d)}`,
            detalhe: `A diferença é exatamente o DOBRO deste valor (${brl(alvo)} = 2 × ${brl(toCents(d.valor))}): é o que acontece quando um crédito entra como compra. Troque o tipo do lançamento para estorno/crédito.`,
            lancamentoIds: [d.id],
            explicaCentavos: -2 * toCents(d.valor),
            acao: null,
          });
    }

    // ---------------------------------------------------------------- banco tem a MAIS
    if (sinalBanco) {
      const pagos = itens.filter((d) => d.pago && d.tipo === "DEBITO");
      const pagosIguais = pagos.filter((d) => toCents(d.valor) === alvo);
      for (const d of pagosIguais)
        add({
          chave: `pago:${d.id}`,
          tipo: "PAGO_INDEVIDO",
          confianca: pagosIguais.length === 1 ? "alta" : "media",
          titulo: `Marcado como pago, mas o banco ainda cobra: ${rotulo(d)}`,
          detalhe: "O valor é exatamente a diferença. Marque como em aberto para a fatura em aberto do app voltar a contar este item.",
          lancamentoIds: [d.id],
          explicaCentavos: toCents(d.valor),
          acao: { tipo: "MARCAR_ABERTO", id: d.id, rotulo: "Marcar como em aberto" },
        });
      if (pagosIguais.length === 0) {
        const comb = subconjuntosQueSomam(pagos.map((d) => ({ id: d.id, cents: toCents(d.valor) })), alvo, 3, 2);
        for (const ids2 of comb)
          add({
            chave: `pagos:${ids2.join(",")}`,
            tipo: "PAGO_INDEVIDO",
            confianca: "media",
            titulo: `${ids2.length} itens marcados como pagos somam a diferença`,
            detalhe: ids2.map((id) => rotulo(itens.find((x) => x.id === id) as Despesa)).join("; "),
            lancamentoIds: ids2,
            explicaCentavos: alvo,
            acao: null,
          });
      }
      // pagamento da fatura anterior que entrou como crédito dentro do cartão (abate em dobro)
      const pagamentos = creditos.filter((d) => ehPagamentoDeFatura(d.descricao) || pareceCreditoDePagamento(d));
      const somaPag = pagamentos.reduce((s, d) => s + toCents(d.valor), 0);
      if (pagamentos.length > 0 && somaPag === alvo)
        add({
          chave: "pagamento-no-cartao",
          tipo: "PAGAMENTO_NO_CARTAO",
          confianca: "alta",
          titulo: `Pagamento de fatura dentro do cartão: ${pagamentos.map((d) => rotulo(d)).join("; ")}`,
          detalhe: "O crédito é exatamente a diferença. O pagamento da fatura anterior já está na conta; dentro do cartão ele abate a fatura em dobro. Remova o crédito (vai para a lixeira).",
          lancamentoIds: pagamentos.map((d) => d.id),
          explicaCentavos: somaPag,
          acao: pagamentos.length === 1 ? { tipo: "REMOVER", id: pagamentos[0].id, rotulo: "Remover o crédito (lixeira)" } : null,
        });
      else if (pagamentos.length > 0)
        add({
          chave: "pagamento-no-cartao-parcial",
          tipo: "PAGAMENTO_NO_CARTAO",
          confianca: "baixa",
          titulo: `Há ${pagamentos.length} crédito(s) que parecem pagamento de fatura nesta fatura`,
          detalhe: `${pagamentos.map((d) => rotulo(d)).join("; ")}. Se forem o pagamento da fatura anterior, abatem em dobro e inflam a diferença.`,
          lancamentoIds: pagamentos.map((d) => d.id),
          explicaCentavos: 0,
          acao: null,
        });
      // estorno que o banco ainda não processou
      for (const d of creditos)
        if (toCents(d.valor) === alvo && !pagamentos.includes(d))
          add({
            chave: `estorno-pend:${d.id}`,
            tipo: "ESTORNO_PENDENTE",
            confianca: REGEX_ESTORNO.test(d.descricao) ? "alta" : "media",
            titulo: `Crédito lançado que o banco ainda não aplicou: ${rotulo(d)}`,
            detalhe: "O crédito tem exatamente o valor da diferença. Estornos costumam demorar uma ou duas faturas para aparecer: se o banco ainda não o mostra, é isto.",
            lancamentoIds: [d.id],
            explicaCentavos: toCents(d.valor),
            acao: null,
          });
      // IOF / encargos sobre compra
      for (const d of debitosAbertos)
        for (const t of TAXAS_IOF) {
          const calc = Math.round((toCents(d.valor) * t.pct) / 100);
          if (Math.abs(calc - alvo) <= 1)
            add({
              chave: `iof:${d.id}:${t.pct}`,
              tipo: "ENCARGO_IOF",
              confianca: d.moedaOriginal && d.moedaOriginal !== "BRL" ? "alta" : "media",
              titulo: `${t.rotulo} sobre ${rotulo(d)}`,
              detalhe: `${t.pct.toString().replace(".", ",")}% de ${brl(toCents(d.valor))} é ${brl(calc)}, a diferença. Bancos cobram o IOF numa linha à parte: lance-o como despesa do cartão (ou acrescente ao valor da compra).`,
              lancamentoIds: [d.id],
              explicaCentavos: alvo,
              acao: null,
            });
        }
      // parcela do mês anterior sem a seguinte nesta fatura
      const mesAnt = e.mes === 1 ? { mes: 12, ano: e.ano - 1 } : { mes: e.mes - 1, ano: e.ano };
      const anterior = resumoFatura(principal, e.ds.despesas, mesAnt.mes, mesAnt.ano, e.ds.cartoes).itens;
      const aqui = new Set(itens.map((d) => chaveParcela(d)).filter((x): x is NonNullable<typeof x> => x !== null).map((x) => `${x.base}|${x.i}|${x.n}`));
      for (const d of anterior) {
        const p = chaveParcela(d);
        if (!p || p.i >= p.n || aqui.has(`${p.base}|${p.i + 1}|${p.n}`)) continue;
        const exato = toCents(d.valor) === alvo;
        add({
          chave: `parcela:${d.id}`,
          tipo: "PARCELA_FALTANTE",
          confianca: exato ? "alta" : "baixa",
          titulo: `Falta a parcela ${p.i + 1}/${p.n} de "${d.descricao.replace(/\s*\(?\d{1,2}\s*\/\s*\d{1,2}\)?\s*/, " ").trim()}"`,
          detalhe: exato
            ? `A parcela ${p.i}/${p.n} está na fatura anterior (${brl(toCents(d.valor))}) e a seguinte não existe nesta; o valor é exatamente a diferença. Lance a parcela faltante.`
            : `A parcela ${p.i}/${p.n} está na fatura anterior e a seguinte não aparece nesta. Se o banco cobra, falta lançá-la.`,
          lancamentoIds: [d.id],
          explicaCentavos: exato ? alvo : 0,
          acao: null,
        });
      }
    }

    // ---------------------------------------------------------------- compra na fronteira do fechamento (outro ciclo)
    const proximaRef = e.mes === 12 ? { mes: 1, ano: e.ano + 1 } : { mes: e.mes + 1, ano: e.ano };
    const anteriorRef = e.mes === 1 ? { mes: 12, ano: e.ano - 1 } : { mes: e.mes - 1, ano: e.ano };
    const bordaIni = fat.fechamento - 5 * DIA_MS;
    const bordaFim = fat.fechamento + 5 * DIA_MS;
    const perto = (d: Despesa) => d.data >= bordaIni && d.data <= bordaFim;
    if (!sinalBanco) {
      for (const d of debitosAbertos.filter(perto))
        if (toCents(d.valor) === alvo)
          add({
            chave: `fronteira:${d.id}`,
            tipo: "FRONTEIRA_CICLO",
            confianca: "media",
            titulo: `Compra perto do fechamento: ${rotulo(d)}`,
            detalhe: `Foi feita a ${Math.round(Math.abs(d.data - fat.fechamento) / DIA_MS)} dia(s) do fechamento (${dataCurta(fat.fechamento)}) e vale exatamente a diferença. Se o banco a põe na fatura de ${String(proximaRef.mes).padStart(2, "0")}/${proximaRef.ano}, o dia de fechamento do app está diferente do do banco.`,
            lancamentoIds: [d.id],
            explicaCentavos: -toCents(d.valor),
            acao: null,
          });
    } else {
      const vizinhas = [anteriorRef, proximaRef].flatMap((r) => resumoFatura(principal, e.ds.despesas, r.mes, r.ano, e.ds.cartoes).itens.filter((d) => d.tipo === "DEBITO" && perto(d)));
      for (const d of vizinhas)
        if (toCents(d.valor) === alvo)
          add({
            chave: `fronteira:${d.id}`,
            tipo: "FRONTEIRA_CICLO",
            confianca: "media",
            titulo: `Compra de outra fatura, perto do fechamento: ${rotulo(d)}`,
            detalhe: `O valor é exatamente a diferença e a data está a ${Math.round(Math.abs(d.data - fat.fechamento) / DIA_MS)} dia(s) do fechamento (${dataCurta(fat.fechamento)}). O banco pode tê-la incluído nesta fatura: confira o dia de fechamento do cartão.`,
            lancamentoIds: [d.id],
            explicaCentavos: alvo,
            acao: null,
          });
    }

    // ---------------------------------------------------------------- outro dia de fechamento faria bater?
    const ref = { mes: e.mes, ano: e.ano };
    const todos = new Set(grupo.map((c) => c.id));
    for (const melhor of [false, true])
      for (let k = 1; k <= 31; k++) {
        if (k === principal.diaFechamento && melhor === !!principal.fechamentoMelhorDia) continue;
        const alt = { ...principal, diaFechamento: k, fechamentoMelhorDia: melhor };
        const its = itensFatura(alt, e.ds.despesas, ref.mes, ref.ano, todos);
        const abertoAlt = its.filter((d) => !d.pago).reduce((s, d) => s + assinado(d), 0);
        if (abertoAlt !== bancoC || its.length === 0) continue;
        add({
          chave: `fecha:${k}:${melhor ? 1 : 0}`,
          tipo: "FECHAMENTO_DIFERENTE",
          confianca: "media",
          titulo: `Com fechamento no dia ${k}${melhor ? " (melhor dia de compra)" : ""} a fatura bateria`,
          detalhe: `O cartão está cadastrado com fechamento no dia ${principal.diaFechamento}${principal.fechamentoMelhorDia ? " (melhor dia de compra)" : ""}. Trocando para ${k}${melhor ? " com melhor dia" : ""}, o total em aberto fica exatamente ${brl(bancoC)}. Confirme no app do banco a data de fechamento e ajuste o cartão.`,
          lancamentoIds: [],
          explicaCentavos: dif,
          acao: null,
        });
      }

    // ---------------------------------------------------------------- combinações de itens
    const base = (sinalBanco ? itens.filter((d) => d.pago && d.tipo === "DEBITO") : debitosAbertos).map((d) => ({ id: d.id, cents: toCents(d.valor) }));
    const jaCobertos = new Set(hip.flatMap((h) => (h.explicaCentavos !== 0 ? h.lancamentoIds : [])));
    if (hip.filter((h) => h.confianca === "alta").length === 0 && !sinalBanco) {
      for (const idsC of subconjuntosQueSomam(base, alvo, 4, 4)) {
        if (idsC.every((id) => jaCobertos.has(id))) continue;
        add({
          chave: `comb:${idsC.join(",")}`,
          tipo: "COMBINACAO",
          confianca: idsC.length === 2 ? "media" : "baixa",
          titulo: `${idsC.length} itens somam exatamente a diferença`,
          detalhe: `${idsC.map((id) => rotulo(itens.find((x) => x.id === id) as Despesa)).join("; ")}. É uma combinação possível, não uma prova: use quando fizer sentido (mesma loja, mesmo dia, mesma compra dividida).`,
          lancamentoIds: idsC,
          explicaCentavos: -alvo,
          acao: null,
        });
      }
    }
  }

  const ordem: Record<Confianca, number> = { alta: 0, media: 1, baixa: 2 };
  hip.sort((a, b) => ordem[a.confianca] - ordem[b.confianca] || Math.abs(b.explicaCentavos) - Math.abs(a.explicaCentavos));
  const final = hip.slice(0, MAX_HIPOTESES);

  let resumo: string;
  if (dif === 0) resumo = "A fatura em aberto do app bate com a do banco.";
  else {
    const quem = dif > 0 ? "O banco cobra" : "O app tem";
    resumo = `${quem} ${brl(alvo)} a mais. ${final.length > 0 ? `Encontrei ${final.length} possível(is) causa(s), da mais provável para a menos.` : "Nenhuma causa óbvia: veja as dicas abaixo ou envie o extrato da fatura para uma análise linha a linha."}`;
  }
  return { cartao: principal, mes: e.mes, ano: e.ano, totalApp: fat.total, emAberto: fat.emAberto, totalBanco: e.totalBanco, diferenca: fromCents(dif), situacao, hipoteses: final, extrato, resumo };
}

/** Fatura do ciclo em que `agoraMs` cai (a que está "em aberto" agora). */
export function faturaAtual(principal: Pick<Cartao, "diaFechamento" | "fechamentoMelhorDia">, agoraMs: number) {
  return faturaDaCompra(principal, agoraMs);
}
