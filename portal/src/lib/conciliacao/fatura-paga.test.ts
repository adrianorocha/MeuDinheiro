import { describe, expect, it } from "vitest";
import { limiteGrupo } from "../finance/calc";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import { pagarFatura } from "../finance/operations";
import { creditosDaFatura, faturasPagasComPendencias, pagamentosNoCartao, pareceCreditoDePagamento, reprocessarFatura, reprocessarSaldos } from "../finance/reprocessar";
import type { Dataset } from "../finance/types";
import { executarLote, OPCOES_LOTE_PADRAO, type ArquivoParaLote } from "./lote-multiplo";
import { casar, lancamentosDoDestino } from "./matching";
import { aplicarConciliacao, detectarFaturaPaga, ehPagamentoDeFatura, montarPlano } from "./plano";
import type { Destino, TransacaoBanco } from "./tipos";

const CARTAO: Destino = { tipo: "CARTAO", cartaoId: 10 };
const APOS_VENCIMENTO = dt(2025, 4, 10); // fatura de março: fecha 25/03, vence 05/04
const ANTES_VENCIMENTO = dt(2025, 4, 1);
const T = (data: number, valor: number, descricao: string, fitid: string): TransacaoBanco => ({ fitid, data, valor, descricao });

function base(extra: Dataset["despesas"] = []): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    cartoes: [cartao({ id: 10, contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000 })],
    despesas: extra,
  });
}
function ok<R extends { ok: boolean }>(r: R): Extract<R, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<R, { ok: true }>;
}

describe("R70 - detecção de fatura fechada e paga", () => {
  const ds = base();
  const extrato = { transacoes: [T(dt(2025, 3, 10), -100, "LOJA", "a")] };
  it("fechada e vencida => sugere paga; fechada mas a vencer => não sugere", () => {
    expect(detectarFaturaPaga(ds, CARTAO, extrato, APOS_VENCIMENTO)).toMatchObject({ fechada: true, vencida: true, sugerirPaga: true });
    expect(detectarFaturaPaga(ds, CARTAO, extrato, ANTES_VENCIMENTO)).toMatchObject({ fechada: true, vencida: false, sugerirPaga: false });
  });
  it("linha de pagamento no extrato sugere paga mesmo antes do vencimento; fatura aberta nunca", () => {
    const comPgto = { transacoes: [...extrato.transacoes, T(dt(2025, 3, 30), 100, "PAGAMENTO EFETUADO", "p")] };
    expect(detectarFaturaPaga(ds, CARTAO, comPgto, ANTES_VENCIMENTO)?.sugerirPaga).toBe(false); // 30/03 cai na fatura de abril (aberta)
    const so = { transacoes: [...extrato.transacoes, T(dt(2025, 3, 20), 100, "PAGAMENTO EFETUADO", "p")] };
    expect(detectarFaturaPaga(ds, CARTAO, so, ANTES_VENCIMENTO)).toMatchObject({ temPagamento: true, sugerirPaga: true });
    expect(detectarFaturaPaga(ds, CARTAO, { transacoes: [T(dt(2025, 4, 2), -5, "X", "z")] }, APOS_VENCIMENTO)?.fechada).toBe(false);
  });
  it("conta não tem fatura", () => {
    expect(detectarFaturaPaga(ds, { tipo: "CONTA", conta: "111" }, extrato, APOS_VENCIMENTO)).toBeNull();
  });
  it("reconhece o pagamento da fatura", () => {
    expect(ehPagamentoDeFatura("PAGAMENTO EFETUADO")).toBe(true);
    expect(ehPagamentoDeFatura("Pagto Fatura Cartão")).toBe(true);
    expect(ehPagamentoDeFatura("PAGAMENTO LOJA XYZ")).toBe(false);
  });
});

describe("R70 - aplicar conciliação com fatura paga", () => {
  const existente = desp({ id: 1, descricao: "Loja", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false });
  const t = [T(dt(2025, 3, 10), -100, "LOJA", "f1"), T(dt(2025, 3, 12), -40, "PADARIA", "f2"), T(dt(2025, 3, 20), 300, "PAGAMENTO EFETUADO", "f3")];

  it("concilia e cria itens pagos, sem mexer no saldo da conta; pagamento do extrato não é criado", () => {
    const ds = base([existente]);
    const r = casar(t, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const plano = montarPlano(r, ds, CARTAO, { criar: true, faturaPaga: true });
    expect(plano.faturaPaga).toBe(true);
    expect(plano.acoes.map((a) => a.transacao.fitid).sort()).toEqual(["f1", "f2"]);
    const ap = ok(aplicarConciliacao(ds, plano, APOS_VENCIMENTO));
    expect(ap.ds.despesas.every((d) => d.pago)).toBe(true);
    expect(ap.ds.despesas).toHaveLength(2);
    expect(ap.ds.contas[0].saldo).toBe(ds.contas[0].saldo);
    expect(limiteGrupo(ap.ds.cartoes[0], ap.ds.cartoes, ap.ds.despesas)).toBe(5000);
    expect(ap.resumo.pagosMarcados).toBe(1);
  });

  it("sem faturaPaga mantém o comportamento anterior (pendente na fatura)", () => {
    const ds = base([existente]);
    const r = casar(t, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const ap = ok(aplicarConciliacao(ds, montarPlano(r, ds, CARTAO, { criar: true }), APOS_VENCIMENTO));
    expect(ap.ds.despesas.filter((d) => d.pago)).toHaveLength(0);
  });

  it("faturaPaga é ignorado em conta", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [] });
    const r = casar([T(dt(2025, 3, 10), -10, "X", "k")], [], { janelaDias: 3 });
    const plano = montarPlano(r, ds, { tipo: "CONTA", conta: "111" }, { criar: true, faturaPaga: true });
    expect(plano.faturaPaga).toBeUndefined();
  });

  it("lote AUTO marca como pago só quando fechada e vencida; NAO nunca", async () => {
    const arq = (): ArquivoParaLote => ({ id: "a", nome: "fat.ofx", destino: CARTAO, extrato: { formato: "OFX", transacoes: [t[0]], periodo: null, avisos: [] } });
    const rodar = async (agora: number, faturaPaga: "AUTO" | "SIM" | "NAO") => {
      let atual = base([{ ...existente }]);
      await executarLote({
        arquivos: [arq()],
        opcoes: { ...OPCOES_LOTE_PADRAO, faturaPaga },
        agora,
        getDs: () => atual,
        aplicar: (plano) => {
          const x = aplicarConciliacao(atual, plano, agora);
          if (x.ok) atual = x.ds;
          return x.ok ? { ok: true, inverso: x.inverso, resumo: x.resumo } : x;
        },
        ceder: async () => {},
      });
      return atual.despesas[0].pago;
    };
    expect(await rodar(APOS_VENCIMENTO, "AUTO")).toBe(true);
    expect(await rodar(ANTES_VENCIMENTO, "AUTO")).toBe(false);
    expect(await rodar(ANTES_VENCIMENTO, "SIM")).toBe(true);
    expect(await rodar(APOS_VENCIMENTO, "NAO")).toBe(false);
  });
});

describe("R70 - reprocessar saldos", () => {
  it("marca como pagos itens que sobraram em fatura já quitada e restaura o limite; idempotente", () => {
    let ds = base([desp({ id: 1, descricao: "A", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false })]);
    ds = ok(pagarFatura(ds, 10, 3, 2025, { agora: dt(2025, 4, 3), usados: new Set() } as never)).ds;
    // item importado depois, mas da mesma fatura (data anterior ao pagamento) e em aberto
    ds = { ...ds, despesas: [...ds.despesas, desp({ id: 2, descricao: "B", valor: 50, data: dt(2025, 3, 15), cartaoId: 10, pago: false })] };
    const antes = faturasPagasComPendencias(ds);
    expect(antes).toHaveLength(1);
    expect(antes[0]).toMatchObject({ mes: 3, ano: 2025, liquido: 50 });
    const r = reprocessarSaldos(ds);
    expect(r.itensMarcados).toBe(1);
    expect(r.ds.despesas.find((d) => d.id === 2)?.pago).toBe(true);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(5000);
    expect(r.ds.contas[0].saldo).toBe(ds.contas[0].saldo);
    const de = reprocessarSaldos(r.ds);
    expect(de.itensMarcados).toBe(0);
    expect(de.correcoes).toEqual([]);
    expect(reprocessarSaldos(ds, { corrigirFaturas: false }).itensMarcados).toBe(0);
  });

  it("não toca em fatura sem pagamento completo nem em itens posteriores ao pagamento", () => {
    const ds = base([desp({ id: 1, descricao: "A", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false })]);
    expect(faturasPagasComPendencias(ds)).toEqual([]);
  });
});

describe("R70 - pagamento da fatura dentro do cartão (total negativo)", () => {
  it("a conciliação nunca cria o crédito de pagamento da fatura no cartão", () => {
    const ds = base();
    const r = casar([T(dt(2025, 3, 10), -100, "LOJA", "a"), T(dt(2025, 3, 12), 2400, "Pagamento recebido", "b")], lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const plano = montarPlano(r, ds, CARTAO, { criar: true });
    expect(plano.acoes.map((a) => a.transacao.fitid)).toEqual(["a"]);
  });

  it("reprocessar remove (para a lixeira) créditos de pagamento já importados e o total da fatura volta a bater", () => {
    const ds = base([
      desp({ id: 1, descricao: "Loja", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false }),
      desp({ id: 2, descricao: "Pagamento recebido", valor: 2400, tipo: "CREDITO", data: dt(2025, 3, 12), cartaoId: 10, pago: false, autor: "Importação" }),
    ]);
    expect(pagamentosNoCartao(ds).map((d) => d.id)).toEqual([2]);
    const r = reprocessarSaldos(ds, { removerPagamentosNoCartao: true, agora: APOS_VENCIMENTO });
    expect(r.pagamentosRemovidos).toBe(1);
    expect(r.ds.despesas.map((d) => d.id)).toEqual([1]);
    expect(r.ds.lixeira).toHaveLength(1);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(4900);
    expect(reprocessarSaldos(r.ds, { removerPagamentosNoCartao: true }).pagamentosRemovidos).toBe(0);
  });
});

describe("R71 - reprocessar uma fatura", () => {
  const ds0 = () =>
    base([
      desp({ id: 1, descricao: "Loja", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false }),
      desp({ id: 2, descricao: "Pagamento recebido", valor: 400, tipo: "CREDITO", data: dt(2025, 3, 12), cartaoId: 10, pago: false, autor: "Importação" }),
      // outra fatura (abril): não deve ser tocada
      desp({ id: 3, descricao: "Pagamento recebido", valor: 50, tipo: "CREDITO", data: dt(2025, 4, 12), cartaoId: 10, pago: false, autor: "Importação" }),
    ]);

  it("só mexe na fatura escolhida e mostra antes/depois", () => {
    const r = reprocessarFatura(ds0(), { cartaoId: 10, mes: 3, ano: 2025 }, { removerPagamentosNoCartao: true, agora: APOS_VENCIMENTO });
    expect(r.pagamentosRemovidos).toBe(1);
    expect(r.ds.despesas.map((d) => d.id)).toEqual([1, 3]);
    expect(r.resumo).toMatchObject({ totalAntes: -300, totalDepois: 100, emAbertoAntes: -300, emAbertoDepois: 100 });
    expect(r.ds.lixeira).toHaveLength(1);
  });

  it("sem remover pagamentos não altera nada além do recálculo", () => {
    const r = reprocessarFatura(ds0(), { cartaoId: 10, mes: 3, ano: 2025 }, { removerPagamentosNoCartao: false });
    expect(r.pagamentosRemovidos).toBe(0);
    expect(r.ds.despesas).toHaveLength(3);
  });

  it("marca como pagos itens sobrados de fatura quitada, só dessa fatura", () => {
    let ds = base([desp({ id: 1, descricao: "A", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false })]);
    ds = ok(pagarFatura(ds, 10, 3, 2025, { agora: dt(2025, 4, 3), usados: new Set() } as never)).ds;
    ds = { ...ds, despesas: [...ds.despesas, desp({ id: 2, descricao: "B", valor: 50, data: dt(2025, 3, 15), cartaoId: 10, pago: false })] };
    expect(reprocessarFatura(ds, { cartaoId: 10, mes: 2, ano: 2025 }).itensMarcados).toBe(0);
    const r = reprocessarFatura(ds, { cartaoId: 10, mes: 3, ano: 2025 });
    expect(r.itensMarcados).toBe(1);
    expect(r.resumo.emAbertoDepois).toBe(0);
  });
});

describe("R71 - créditos escolhidos na fatura (total negativo)", () => {
  it("remove só os créditos marcados, de qualquer autor, dentro da fatura", () => {
    const ds = base([
      desp({ id: 1, descricao: "Loja", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false }),
      desp({ id: 2, descricao: "Pagamento em 25 MAR", valor: 400, tipo: "CREDITO", data: dt(2025, 3, 12), cartaoId: 10, pago: true }),
      desp({ id: 3, descricao: "Estorno loja", valor: 20, tipo: "CREDITO", data: dt(2025, 3, 13), cartaoId: 10, pago: false }),
      desp({ id: 4, descricao: "Pagamento fora", valor: 99, tipo: "CREDITO", data: dt(2025, 4, 12), cartaoId: 10, pago: false }),
    ]);
    const alvo = { cartaoId: 10, mes: 3, ano: 2025 };
    expect(creditosDaFatura(ds, alvo).map((d) => d.id)).toEqual([2, 3]);
    expect(pareceCreditoDePagamento({ descricao: "Pagamento em 25 MAR" })).toBe(true);
    expect(pareceCreditoDePagamento({ descricao: "Estorno loja" })).toBe(false);
    const r = reprocessarFatura(ds, alvo, { removerIds: [2, 4, 99], agora: APOS_VENCIMENTO });
    expect(r.pagamentosRemovidos).toBe(1);
    expect(r.ds.despesas.map((d) => d.id)).toEqual([1, 3, 4]);
    expect(r.resumo.totalAntes).toBe(-320);
    expect(r.resumo.totalDepois).toBe(80);
  });
});
