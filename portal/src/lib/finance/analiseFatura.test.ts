import { describe, expect, it } from "vitest";
import { analisarFatura, paresDuplicados, subconjuntosQueSomam } from "./analiseFatura";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import type { ArquivoExtrato } from "../conciliacao/tipos";
import type { Despesa } from "./types";

// cartão fecha dia 25: compras de 26/02 a 25/03 caem na fatura 03/2025
const ds = (despesas: Despesa[], extra: Partial<ReturnType<typeof cartao>> = {}) =>
  dataset({ contas: [conta({ id: 1, conta: "111" })], cartoes: [cartao({ id: 10, contaId: 1, diaFechamento: 25, diaVencimento: 5, ...extra })], despesas });
const c = (over: Partial<Despesa>) => desp({ cartaoId: 10, pago: false, conta: "111", data: dt(2025, 3, 10), ...over });
const analisa = (despesas: Despesa[], totalBanco: number, extra: Partial<ReturnType<typeof cartao>> = {}, extrato?: ArquivoExtrato) =>
  analisarFatura({ ds: ds(despesas, extra), cartaoId: 10, mes: 3, ano: 2025, totalBanco, extrato })!;
const tipos = (r: ReturnType<typeof analisa>) => r.hipoteses.map((h) => h.tipo);

describe("analisarFatura (sem extrato)", () => {
  it("bate: nenhuma hipótese", () => {
    const r = analisa([c({ valor: 100 }), c({ valor: 50 })], 150);
    expect(r.situacao).toBe("BATE");
    expect(r.diferenca).toBe(0);
    expect(r.hipoteses).toEqual([]);
  });

  it("app a mais: duplicado com o valor exato da diferença é alta confiança", () => {
    const a = c({ id: 1001, descricao: "Padaria Central", valor: 40, data: dt(2025, 3, 10) });
    const b = c({ id: 1002, descricao: "PADARIA CENTRAL", valor: 40, data: dt(2025, 3, 11) });
    const r = analisa([a, b, c({ valor: 100 })], 140);
    expect(r.situacao).toBe("APP_MAIOR");
    expect(r.diferenca).toBe(-40);
    expect(r.hipoteses[0].tipo).toBe("DUPLICADO");
    expect(r.hipoteses[0].confianca).toBe("alta");
    expect(r.hipoteses[0].acao).toEqual({ tipo: "REMOVER", id: 1002, rotulo: expect.any(String) });
  });

  it("estorno lançado como compra: diferença = 2 × valor", () => {
    const r = analisa([c({ id: 2001, descricao: "Estorno loja X", valor: 30 }), c({ valor: 100 })], 70);
    expect(r.diferenca).toBe(-60);
    const h = r.hipoteses.find((x) => x.tipo === "ESTORNO_INVERTIDO");
    expect(h?.confianca).toBe("alta");
    expect(h?.lancamentoIds).toEqual([2001]);
  });

  it("banco a mais: item marcado como pago que o banco ainda cobra", () => {
    const r = analisa([c({ id: 3001, valor: 80, pago: true }), c({ valor: 100 })], 180);
    expect(r.situacao).toBe("BANCO_MAIOR");
    expect(r.diferenca).toBe(80);
    const h = r.hipoteses.find((x) => x.tipo === "PAGO_INDEVIDO");
    expect(h?.acao).toEqual({ tipo: "MARCAR_ABERTO", id: 3001, rotulo: expect.any(String) });
  });

  it("banco a mais: pagamento da fatura anterior lançado como crédito no cartão", () => {
    const r = analisa([c({ valor: 500 }), c({ id: 4001, tipo: "CREDITO", descricao: "Pagamento efetuado", valor: 200 })], 500);
    expect(r.diferenca).toBe(200);
    expect(tipos(r)).toContain("PAGAMENTO_NO_CARTAO");
  });

  it("banco a mais: IOF de 3,5% sobre compra internacional", () => {
    const r = analisa([c({ id: 5001, valor: 200, moedaOriginal: "USD" })], 207);
    expect(r.diferenca).toBe(7);
    expect(r.hipoteses.find((x) => x.tipo === "ENCARGO_IOF")?.confianca).toBe("alta");
  });

  it("banco a mais: parcela seguinte não lançada", () => {
    const anterior = c({ id: 6001, descricao: "Geladeira (2/5)", valor: 120, data: dt(2025, 2, 10) });
    const r = analisa([anterior, c({ valor: 100 })], 220);
    const h = r.hipoteses.find((x) => x.tipo === "PARCELA_FALTANTE");
    expect(h?.confianca).toBe("alta");
    expect(h?.titulo).toContain("3/5");
  });

  it("fechamento diferente: aponta o dia que faria o total bater", () => {
    const itens = [c({ valor: 100, data: dt(2025, 3, 10) }), c({ valor: 60, data: dt(2025, 3, 27) })]; // 27/03 cai na fatura 04
    const r = analisa(itens, 160);
    expect(r.diferenca).toBe(60);
    expect(r.hipoteses.find((x) => x.tipo === "FECHAMENTO_DIFERENTE")).toBeTruthy();
  });

  it("combinação: dois itens somam a diferença (sem outra explicação)", () => {
    const r = analisa(
      [c({ id: 7001, valor: 37.1, descricao: "Loja A", data: dt(2025, 3, 3) }), c({ id: 7002, valor: 12.9, descricao: "Cafeteria Z", data: dt(2025, 3, 15) }), c({ valor: 200, descricao: "Mercado" })],
      200,
    );
    const h = r.hipoteses.find((x) => x.tipo === "COMBINACAO");
    expect([...(h?.lancamentoIds ?? [])].sort()).toEqual([7001, 7002]);
  });

  it("não altera o dataset de entrada", () => {
    const d = ds([c({ valor: 100 })]);
    const antes = JSON.stringify(d);
    analisarFatura({ ds: d, cartaoId: 10, mes: 3, ano: 2025, totalBanco: 90 });
    expect(JSON.stringify(d)).toBe(antes);
  });

  it("cartão inexistente: null", () => {
    expect(analisarFatura({ ds: ds([]), cartaoId: 99, mes: 3, ano: 2025, totalBanco: 1 })).toBeNull();
  });
});

describe("analisarFatura (com extrato)", () => {
  const arquivo = (linhas: { fitid: string; dia: number; valor: number; descricao: string }[]): ArquivoExtrato => ({
    formato: "OFX",
    ehCartao: true,
    avisos: [],
    periodo: null,
    transacoes: linhas.map((l) => ({ fitid: l.fitid, data: dt(2025, 3, l.dia), valor: l.valor, descricao: l.descricao })),
  });

  it("aponta o que só existe no extrato, o que só existe no app e valor diferente", () => {
    const app = [
      c({ id: 1, descricao: "Mercado Bom", valor: 100, data: dt(2025, 3, 5) }),
      c({ id: 2, descricao: "Cinema", valor: 40, data: dt(2025, 3, 12) }),
      c({ id: 3, descricao: "Farmácia", valor: 50, data: dt(2025, 3, 15) }),
    ];
    const ext = arquivo([
      { fitid: "a", dia: 5, valor: -100, descricao: "MERCADO BOM" },
      { fitid: "b", dia: 15, valor: -50.5, descricao: "FARMACIA" },
      { fitid: "c", dia: 20, valor: -75, descricao: "POSTO XYZ" },
    ]);
    const r = analisa(app, 225.5, {}, ext);
    expect(r.extrato).not.toBeNull();
    expect(r.extrato!.soNoExtrato.map((l) => l.transacao.fitid)).toEqual(["c"]);
    expect(r.extrato!.soNoApp.map((d) => d.id)).toEqual([2]);
    expect(r.extrato!.valorDiferente.map((v) => v.lancamento.id)).toEqual([3]);
    expect(r.extrato!.restanteCentavos).toBe(0);
    expect(r.extrato!.extratoBateBanco).toBe(true);
    expect(tipos(r)).toEqual(expect.arrayContaining(["NAO_LANCADO", "SO_NO_APP", "VALOR_DIFERENTE"]));
  });

  it("ignora a linha de pagamento da fatura anterior no extrato", () => {
    const ext = arquivo([
      { fitid: "a", dia: 5, valor: -100, descricao: "MERCADO BOM" },
      { fitid: "p", dia: 6, valor: 300, descricao: "PAGAMENTO EFETUADO FATURA" },
    ]);
    const r = analisa([c({ id: 1, descricao: "Mercado Bom", valor: 100, data: dt(2025, 3, 5) })], 100, {}, ext);
    expect(r.extrato!.total).toBe(1);
    expect(r.extrato!.extratoBateBanco).toBe(true);
  });

  it("avisa quando o arquivo não soma o valor informado do banco", () => {
    const ext = arquivo([{ fitid: "a", dia: 5, valor: -100, descricao: "MERCADO BOM" }]);
    const r = analisa([c({ id: 1, descricao: "Mercado Bom", valor: 100, data: dt(2025, 3, 5) })], 130, {}, ext);
    expect(r.extrato!.extratoBateBanco).toBe(false);
    expect(r.extrato!.extratoMenosBanco).toBe(-30);
  });
});

describe("auxiliares", () => {
  it("subconjuntosQueSomam acha pares, trios e quartetos, sem repetir", () => {
    const it2 = [1, 2, 3, 4, 5, 6].map((n) => ({ id: n, cents: n * 100 }));
    expect(subconjuntosQueSomam(it2, 700, 2, 10)).toEqual([[1, 6], [2, 5], [3, 4]]);
    expect(subconjuntosQueSomam(it2, 600, 3, 10)).toContainEqual([1, 2, 3]);
    expect(subconjuntosQueSomam(it2, 1000, 4, 10)).toContainEqual([1, 2, 3, 4]);
    expect(subconjuntosQueSomam(it2, 1, 4)).toEqual([]);
  });

  it("paresDuplicados exige mesmo valor, tipo e datas próximas", () => {
    const a = desp({ id: 1, descricao: "Uber *Trip", valor: 20, data: dt(2025, 3, 1) });
    const b = desp({ id: 2, descricao: "UBER TRIP", valor: 20, data: dt(2025, 3, 2) });
    const longe = desp({ id: 3, descricao: "Uber Trip", valor: 20, data: dt(2025, 3, 20) });
    const outro = desp({ id: 4, descricao: "Uber Trip", valor: 21, data: dt(2025, 3, 2) });
    expect(paresDuplicados([a, b, longe, outro]).map(([x, y]) => [x.id, y.id])).toEqual([[1, 2]]);
  });
});
