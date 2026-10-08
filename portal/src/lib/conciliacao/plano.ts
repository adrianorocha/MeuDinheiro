import { cartaoIdDe, recalcularTudo } from "../finance/calc";
import { novoIdLong } from "../finance/ids";
import { mesAnoDe } from "../finance/dates";
import { montaDespesa } from "../finance/operations";
import { sugerirCategoria } from "../finance/analises";
import { round2 } from "../finance/money";
import type { Dataset, Despesa, Result } from "../finance/types";
import { lancamentosDoDestino } from "./matching";
import { detectarParcela, limparDescricaoBanco } from "./texto-banco";
import type { Destino, ItemMatch, ResultadoMatching, TransacaoBanco } from "./tipos";

/** Opções de mescla (R38), independentes entre si. */
export interface Mescla {
  data: boolean;
  valor: boolean;
  descricao: boolean;
  pago: boolean;
}

export type AcaoPlano =
  | { tipo: "CONCILIAR"; transacao: TransacaoBanco; lancamentoId: number; mescla: Mescla }
  | { tipo: "CRIAR"; transacao: TransacaoBanco; categoria: string };

export interface Plano {
  destino: Destino;
  acoes: AcaoPlano[];
}

/** Plano inverso: restaura os lançamentos alterados e remove os criados. */
export interface PlanoInverso {
  restaurar: Despesa[];
  remover: number[];
}

export const AUTOR_IMPORTACAO = "Importação";

/** Padrão de mescla: data do banco; valor do banco só em DIFERENCA; pago só em conta (cartão é quitado por R7). */
export function mesclaPadrao(item: Pick<ItemMatch, "tipo">, destino: Destino): Mescla {
  return { data: true, valor: item.tipo === "DIFERENCA", descricao: false, pago: destino.tipo === "CONTA" };
}

export function categoriaSugerida(ds: Pick<Dataset, "despesas">, descricaoBanco: string): string {
  return sugerirCategoria(limparDescricaoBanco(descricaoBanco), ds.despesas) ?? "Outros";
}

export function acaoConciliar(item: ItemMatch, destino: Destino, lancamentoId: number, tipo: ItemMatch["tipo"] = item.tipo): AcaoPlano {
  return { tipo: "CONCILIAR", transacao: item.transacao, lancamentoId, mescla: mesclaPadrao({ tipo }, destino) };
}

export function acaoCriar(item: ItemMatch, ds: Pick<Dataset, "despesas">, categoria?: string): AcaoPlano {
  return { tipo: "CRIAR", transacao: item.transacao, categoria: categoria ?? categoriaSugerida(ds, item.transacao.descricao) };
}

/** Plano padrão: concilia os AUTOMATICOS e (opcional) os SUGERIDOS; cria os SO_NO_EXTRATO se pedido. */
export function montarPlano(
  resultado: ResultadoMatching,
  ds: Pick<Dataset, "despesas">,
  destino: Destino,
  opcoes: { sugeridos?: boolean; criar?: boolean } = {},
): Plano {
  const acoes: AcaoPlano[] = [];
  for (const it of resultado.itens) {
    if (it.lancamentoId !== undefined && (it.classe === "AUTOMATICO" || (opcoes.sugeridos && it.classe === "SUGERIDO"))) {
      acoes.push(acaoConciliar(it, destino, it.lancamentoId));
    } else if (opcoes.criar && it.classe === "SO_NO_EXTRATO") acoes.push(acaoCriar(it, ds));
  }
  return { destino, acoes };
}

export interface ResumoAplicacao {
  conciliados: number;
  criados: number;
  valoresAlterados: number;
  datasAlteradas: number;
  pagosMarcados: number;
}

type Op<T extends object> = Result<{ ds: Dataset } & T>;

/**
 * R38 - aplica o plano de forma atômica (tudo ou nada). Recalcula saldo/limite uma única vez
 * e devolve o plano inverso para desfazer o lote.
 */
export function aplicarConciliacao(ds: Dataset, plano: Plano, agora: number): Op<{ inverso: PlanoInverso; resumo: ResumoAplicacao }> {
  const { destino } = plano;
  let contaDestino: string;
  let cartaoDestino: number | null = null;
  if (destino.tipo === "CONTA") {
    if (!ds.contas.some((c) => c.conta === destino.conta)) return { ok: false, erro: "Conta de destino não encontrada." };
    contaDestino = destino.conta;
  } else {
    const cartao = ds.cartoes.find((c) => c.id === destino.cartaoId);
    const conta = cartao ? ds.contas.find((c) => c.id === cartao.contaId) : undefined;
    if (!cartao || !conta) return { ok: false, erro: "Cartão de destino não encontrado." };
    contaDestino = conta.conta;
    cartaoDestino = cartao.id;
  }

  const doDestino = new Map(lancamentosDoDestino(ds, destino).map((d) => [d.id, d]));
  const fitidsUsados = new Set(ds.despesas.filter((d) => d.conciliadoEm != null && d.fitid && doDestino.has(d.id)).map((d) => d.fitid as string));
  const vistosL = new Set<number>();
  const vistosF = new Set<string>();
  const alterados = new Map<number, Despesa>();
  const criados: Despesa[] = [];
  const usadosIds = new Set(ds.despesas.map((d) => d.id));
  const resumo: ResumoAplicacao = { conciliados: 0, criados: 0, valoresAlterados: 0, datasAlteradas: 0, pagosMarcados: 0 };

  for (const a of plano.acoes) {
    const t = a.transacao;
    if (vistosF.has(t.fitid) || fitidsUsados.has(t.fitid)) return { ok: false, erro: `A transação "${t.descricao}" já foi conciliada ou está repetida no plano.` };
    vistosF.add(t.fitid);
    if (a.tipo === "CONCILIAR") {
      const d = doDestino.get(a.lancamentoId);
      if (!d) return { ok: false, erro: "Um lançamento do plano não pertence ao destino ou não existe mais." };
      if (d.conciliadoEm != null) return { ok: false, erro: `O lançamento "${d.descricao}" já está conciliado.` };
      if (vistosL.has(d.id)) return { ok: false, erro: `O lançamento "${d.descricao}" aparece mais de uma vez no plano.` };
      if ((t.valor < 0) !== (d.tipo === "DEBITO")) return { ok: false, erro: `Direção incompatível entre "${t.descricao}" e "${d.descricao}".` };
      vistosL.add(d.id);
      const valor = a.mescla.valor ? round2(Math.abs(t.valor)) : d.valor;
      const data = a.mescla.data ? t.data : d.data;
      const { mes, ano } = mesAnoDe(data);
      const pago = a.mescla.pago && cartaoIdDe(d) === null ? true : d.pago;
      if (valor !== d.valor) resumo.valoresAlterados++;
      if (data !== d.data) resumo.datasAlteradas++;
      if (pago && !d.pago) resumo.pagosMarcados++;
      alterados.set(d.id, {
        ...d,
        valor,
        valorOriginal: a.mescla.valor && d.moedaOriginal === "BRL" ? valor : d.valorOriginal,
        data,
        mes,
        ano,
        descricao: a.mescla.descricao ? limparDescricaoBanco(t.descricao) : d.descricao,
        pago,
        fitid: t.fitid,
        conciliadoEm: agora,
      });
      resumo.conciliados++;
    } else {
      if (!a.categoria.trim()) return { ok: false, erro: "Informe a categoria dos lançamentos a criar." };
      const id = novoIdLong(usadosIds, agora);
      usadosIds.add(id);
      criados.push(
        montaDespesa(
          {
            descricao: limparDescricaoBanco(t.descricao),
            valor: Math.abs(t.valor),
            data: t.data,
            categoria: a.categoria.trim(),
            conta: contaDestino,
            tipo: t.valor < 0 ? "DEBITO" : "CREDITO",
            cartaoId: cartaoDestino,
            pago: cartaoDestino === null,
            autor: AUTOR_IMPORTACAO,
            fitid: t.fitid,
            conciliadoEm: agora,
          },
          id,
        ),
      );
      resumo.criados++;
    }
  }

  const inverso: PlanoInverso = {
    restaurar: [...alterados.keys()].map((id) => doDestino.get(id) as Despesa),
    remover: criados.map((d) => d.id),
  };
  const despesas = [...ds.despesas.map((d) => alterados.get(d.id) ?? d), ...criados];
  return { ok: true, ds: recalcularTudo({ ...ds, despesas }), inverso, resumo };
}

/** Desfaz o último lote: restaura os lançamentos alterados e remove os criados. */
export function desfazerLote(ds: Dataset, inverso: PlanoInverso): Dataset {
  const restaurar = new Map(inverso.restaurar.map((d) => [d.id, d]));
  const remover = new Set(inverso.remover);
  const despesas = ds.despesas.filter((d) => !remover.has(d.id)).map((d) => restaurar.get(d.id) ?? d);
  return recalcularTudo({ ...ds, despesas });
}

/** Zera fitid/conciliadoEm de um lançamento (não reverte valor/data já mesclados). */
export function desfazerConciliacao(ds: Dataset, id: number): Op<object> {
  const d = ds.despesas.find((x) => x.id === id);
  if (!d) return { ok: false, erro: "Lançamento não encontrado." };
  if (d.conciliadoEm == null && !d.fitid) return { ok: false, erro: "Este lançamento não está conciliado." };
  return { ok: true, ds: { ...ds, despesas: ds.despesas.map((x) => (x.id === id ? { ...x, fitid: null, conciliadoEm: null } : x)) } };
}

export function ehParcelaNoExtrato(t: TransacaoBanco): boolean {
  return detectarParcela(t.descricao) !== null;
}
