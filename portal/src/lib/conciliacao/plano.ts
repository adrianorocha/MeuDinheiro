import { cartaoIdDe, cartoesDoGrupo, recalcularTudo } from "../finance/calc";
import { novoIdLong } from "../finance/ids";
import { mesAnoDe, somaMeses } from "../finance/dates";
import { montaDespesa } from "../finance/operations";
import { sugerirCategoria } from "../finance/analises";
import { round2, toCents } from "../finance/money";
import { limparDescricao } from "../finance/texto";
import type { Dataset, Despesa, Result } from "../finance/types";
import { lancamentosDoDestino } from "./matching";
import { detectarParcela, detectarParcelaExtrato, limparDescricaoBanco, semMarcadorParcela } from "./texto-banco";
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
  | { tipo: "CRIAR"; transacao: TransacaoBanco; categoria: string; /** R44 - em cartão, cria também as parcelas i+1..n (em aberto) */ parcelasRestantes?: boolean };

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

export function acaoCriar(item: ItemMatch, ds: Pick<Dataset, "despesas">, categoria?: string, parcelasRestantes?: boolean): AcaoPlano {
  return {
    tipo: "CRIAR",
    transacao: item.transacao,
    categoria: categoria ?? categoriaSugerida(ds, item.transacao.descricao),
    ...(parcelasRestantes ? { parcelasRestantes: true } : {}),
  };
}

/** Plano padrão: concilia os AUTOMATICOS e (opcional) os SUGERIDOS; cria os SO_NO_EXTRATO se pedido. */
export function montarPlano(
  resultado: ResultadoMatching,
  ds: Pick<Dataset, "despesas">,
  destino: Destino,
  opcoes: { sugeridos?: boolean; criar?: boolean; parcelasRestantes?: boolean } = {},
): Plano {
  const acoes: AcaoPlano[] = [];
  for (const it of resultado.itens) {
    if (it.lancamentoId !== undefined && (it.classe === "AUTOMATICO" || (opcoes.sugeridos && it.classe === "SUGERIDO"))) {
      acoes.push(acaoConciliar(it, destino, it.lancamentoId));
    } else if (opcoes.criar && it.classe === "SO_NO_EXTRATO") acoes.push(acaoCriar(it, ds, undefined, opcoes.parcelasRestantes));
  }
  return { destino, acoes };
}

export interface ResumoAplicacao {
  conciliados: number;
  criados: number;
  valoresAlterados: number;
  datasAlteradas: number;
  pagosMarcados: number;
  /** R44 - parcelas futuras (i+1..n) criadas além da parcela do extrato */
  parcelasRestantes: number;
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
  const resumo: ResumoAplicacao = { conciliados: 0, criados: 0, valoresAlterados: 0, datasAlteradas: 0, pagosMarcados: 0, parcelasRestantes: 0 };
  const extras: Despesa[] = [];
  const cartaoObj = cartaoDestino !== null ? ds.cartoes.find((c) => c.id === cartaoDestino) : undefined;
  const idsCartoesDestino = new Set(cartaoObj ? cartoesDoGrupo(cartaoObj, ds.cartoes).map((c) => c.id) : []);

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
      const parcela = a.parcelasRestantes && cartaoDestino !== null && t.valor < 0 ? detectarParcelaExtrato(t.descricao, t.data) : null;
      const expandir = parcela !== null && parcela.i < parcela.n;
      const baseDesc = expandir ? semMarcadorParcela(limparDescricaoBanco(t.descricao)) || "Compra parcelada" : "";
      let grupoParcelas: string | null = null;
      if (expandir && parcela) {
        const valorParc = round2(Math.abs(t.valor));
        const chaveBase = limparDescricao(baseDesc);
        const mesmoPlano = [...ds.despesas, ...criados, ...extras];
        const jaExiste = (data: number) => {
          const { mes, ano } = mesAnoDe(data);
          return mesmoPlano.find(
            (d) =>
              cartaoIdDe(d) !== null &&
              idsCartoesDestino.has(cartaoIdDe(d) as number) &&
              toCents(d.valor) === toCents(valorParc) &&
              d.mes === mes &&
              d.ano === ano &&
              limparDescricao(semMarcadorParcela(d.descricao)) === chaveBase,
          );
        };
        const existentes = mesmoPlano.filter((d) => d.grupoId?.startsWith("parc:") && limparDescricao(semMarcadorParcela(d.descricao)) === chaveBase && cartaoIdDe(d) !== null && idsCartoesDestino.has(cartaoIdDe(d) as number));
        grupoParcelas = existentes[0]?.grupoId ?? `parc:imp-${t.fitid}`;
        for (let j = parcela.i + 1; j <= parcela.n; j++) {
          const dataJ = somaMeses(t.data, j - parcela.i);
          if (jaExiste(dataJ)) continue;
          const idJ = novoIdLong(usadosIds, agora);
          usadosIds.add(idJ);
          extras.push(
            montaDespesa(
              {
                descricao: `${baseDesc} (${j}/${parcela.n})`,
                valor: valorParc,
                data: dataJ,
                categoria: a.categoria.trim(),
                conta: contaDestino,
                tipo: "DEBITO",
                cartaoId: cartaoDestino,
                pago: false,
                grupoId: grupoParcelas,
                autor: AUTOR_IMPORTACAO,
              },
              idJ,
            ),
          );
          resumo.parcelasRestantes++;
        }
      }
      criados.push(
        montaDespesa(
          {
            descricao: expandir && parcela ? `${baseDesc} (${parcela.i}/${parcela.n})` : limparDescricaoBanco(t.descricao),
            grupoId: grupoParcelas,
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
    // as parcelas restantes (R44) vão depois das criadas pelo extrato: o índice i de `remover` segue a i-ésima ação CRIAR
    remover: [...criados.map((d) => d.id), ...extras.map((d) => d.id)],
  };
  const despesas = [...ds.despesas.map((d) => alterados.get(d.id) ?? d), ...criados, ...extras];
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
