import { describe, expect, it } from "vitest";
import { aplicarCorrecoesCartao, auditarCartoes, corrigirSeguras } from "./auditoriaCartao";
import { limiteGrupo } from "./calc";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { toCents } from "./money";
import type { Dataset, Despesa } from "./types";

const AGORA = dt(2025, 4, 20);

function ds(despesas: Despesa[], extra: Partial<Dataset> = {}, limiteDisponivel = 1000): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" }), conta({ id: 2, conta: "222", banco: "Banco B" })],
    cartoes: [cartao({ id: 10, contaId: 1, limiteTotal: 1000, limiteDisponivel, diaFechamento: 25, diaVencimento: 5 })],
    despesas,
    ...extra,
  });
}

const compra = (over: Partial<Despesa>) => desp({ cartaoId: 10, pago: false, conta: "111", data: dt(2025, 3, 10), ...over });
const tipos = (d: Dataset) => auditarCartoes(d).grupos[0].achados.map((a) => a.tipo);

describe("auditarCartoes", () => {
  it("cartão sem problemas: nenhum achado", () => {
    const d = ds([compra({ valor: 100 })], {}, 900);
    const r = auditarCartoes(d);
    expect(r.grupos[0].achados).toEqual([]);
    expect(r.grupos[0].ok).toBe(true);
    expect(r.totalAchados).toBe(0);
  });

  it("ignora cartão de débito", () => {
    const d = ds([]);
    d.cartoes[0].tipo = "DÉBITO";
    expect(auditarCartoes(d).grupos).toHaveLength(0);
  });

  it("limite gravado errado: aponta e a correção segura regrava", () => {
    const d = ds([compra({ valor: 100 })], {}, 1000); // deveria ser 900
    const g = auditarCartoes(d).grupos[0];
    expect(g.achados.map((a) => a.tipo)).toEqual(["LIMITE_GRAVADO"]);
    expect(g.limiteCalculado).toBe(900);
    const r = corrigirSeguras(d, { agora: AGORA });
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(900);
    expect(r.correcoes).toHaveLength(1);
    expect(tipos(r.ds)).toEqual([]);
  });

  it("item em aberto de fatura já paga: aponta o impacto e marca como pago", () => {
    const pg = desp({ natureza: "PAGAMENTO_FATURA", valor: 100, conta: "111", grupoId: "fatura:10:2025-03", data: dt(2025, 4, 5) });
    const d = ds([compra({ id: 501, valor: 100, pago: true }), compra({ id: 502, valor: 40, data: dt(2025, 3, 12) }), pg], {}, 960);
    const a = auditarCartoes(d).grupos[0].achados.find((x) => x.tipo === "ITEM_EM_FATURA_PAGA")!;
    expect(a.lancamentoIds).toEqual([502]);
    expect(a.impactoLimite).toBe(40);
    const r = corrigirSeguras(d, { agora: AGORA });
    expect(r.ds.despesas.find((x) => x.id === 502)!.pago).toBe(true);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(1000);
    expect(r.ds.despesas.filter((x) => x.id !== 502).every((x, i) => x === d.despesas.filter((y) => y.id !== 502)[i])).toBe(true); // só mudou o item
  });

  it("possível duplicidade: sugere remover só as cópias e vai para a lixeira", () => {
    const d = ds([compra({ id: 601, valor: 55.5, descricao: "Mercado X" }), compra({ id: 602, valor: 55.5, descricao: "MERCADO x" })], {}, 889);
    const a = auditarCartoes(d).grupos[0].achados.find((x) => x.tipo === "POSSIVEL_DUPLICIDADE")!;
    expect(a.lancamentoIds).toEqual([601, 602]);
    expect(a.acao).toEqual({ tipo: "REMOVER", ids: [602] });
    expect(a.segura).toBe(false);
    const r = aplicarCorrecoesCartao(d, [a], { agora: AGORA });
    expect(r.ds.despesas.some((x) => x.id === 602)).toBe(false);
    expect(r.ds.despesas.some((x) => x.id === 601)).toBe(true);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(944.5);
    expect(r.ds.lixeira.some((l) => JSON.stringify(l).includes("602"))).toBe(true);
  });

  it("não acusa duplicidade quando o banco distinguiu (FITIDs diferentes) nem parcelas/recorrências", () => {
    const a = ds([compra({ valor: 10, descricao: "Café", fitid: "A" }), compra({ valor: 10, descricao: "Café", fitid: "B" })], {}, 980);
    expect(tipos(a)).not.toContain("POSSIVEL_DUPLICIDADE");
    const b = ds([compra({ valor: 10, descricao: "Curso (1/2)", grupoId: "parc:x" }), compra({ valor: 10, descricao: "Curso (1/2)", grupoId: "parc:x" })], {}, 980);
    expect(tipos(b)).not.toContain("POSSIVEL_DUPLICIDADE");
  });

  it("pagamento de fatura importado dentro do cartão: aponta e remove", () => {
    const p = compra({ id: 701, tipo: "CREDITO", valor: 300, descricao: "PAGAMENTO FATURA", autor: "Importação" });
    const d = ds([compra({ valor: 500 }), p], {}, 800);
    const a = auditarCartoes(d).grupos[0].achados.find((x) => x.tipo === "PAGAMENTO_NO_CARTAO")!;
    expect(a.lancamentoIds).toEqual([701]);
    const r = aplicarCorrecoesCartao(d, [a], { agora: AGORA });
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(500);
  });

  it("compra em conta diferente da do cartão: ajusta só a conta", () => {
    const d = ds([compra({ id: 801, valor: 20, conta: "222" })], {}, 980);
    const a = auditarCartoes(d).grupos[0].achados.find((x) => x.tipo === "CONTA_DIFERENTE")!;
    expect(a.severidade).toBe("info");
    const r = aplicarCorrecoesCartao(d, [a], { agora: AGORA });
    expect(r.ds.despesas[0].conta).toBe("111");
    expect(r.ds.despesas[0].valor).toBe(20);
  });

  it("pagamento da fatura diferente da soma dos itens: informa sem ação", () => {
    const pg = desp({ natureza: "PAGAMENTO_FATURA", valor: 90, conta: "111", grupoId: "fatura:10:2025-03", data: dt(2025, 4, 5) });
    const d = ds([compra({ valor: 100, pago: true }), pg], {}, 1000);
    const a = auditarCartoes(d).grupos[0].achados.find((x) => x.tipo === "FATURA_PAGA_DIVERGENTE")!;
    expect(a.acao).toBeNull();
    expect(a.detalhe).toContain("abaixo");
  });

  it("limite disponível acima do total e negativo são sinalizados", () => {
    const acima = ds([compra({ tipo: "CREDITO", valor: 50, descricao: "Estorno" })], {}, 1050);
    expect(tipos(acima)).toContain("LIMITE_FORA_DA_FAIXA");
    const neg = ds([compra({ valor: 1200 })], {}, -200);
    expect(tipos(neg)).toContain("LIMITE_FORA_DA_FAIXA");
  });

  it("grupo com virtual: considera compras do virtual e grava o mesmo limite nos dois", () => {
    const base = ds([compra({ valor: 100 }), compra({ cartaoId: 11, valor: 50, data: dt(2025, 3, 11) })], {}, 1000);
    base.cartoes.push(cartao({ id: 11, contaId: 1, cartaoPrincipalId: 10, limiteTotal: 1000, limiteDisponivel: 900 }));
    const r = corrigirSeguras(base, { agora: AGORA });
    expect(r.ds.cartoes.map((c) => c.limiteDisponivel)).toEqual([850, 850]);
  });
});

describe("auditarCartoes - limite informado pelo banco", () => {
  it("diferença com candidato exato: sugere marcar como pago e passa a bater", () => {
    // app: usado 100 + 37,9 = 137,9 → disponível 862,1; banco mostra 900 (já pagou 37,9)
    const d = ds([compra({ id: 901, valor: 100 }), compra({ id: 902, valor: 37.9, descricao: "Já paga" })], {}, 862.1);
    const g = auditarCartoes(d, { 10: 900 }).grupos[0];
    expect(g.diferencaBanco).toBe(-37.9);
    const cand = g.achados.find((x) => x.tipo === "CANDIDATO_BANCO")!;
    expect(cand.lancamentoIds).toEqual([902]);
    const r = aplicarCorrecoesCartao(d, [cand], { agora: AGORA });
    expect(limiteGrupo(r.ds.cartoes[0], r.ds.cartoes, r.ds.despesas)).toBe(900);
    expect(auditarCartoes(r.ds, { 10: 900 }).grupos[0].achados).toEqual([]);
  });

  it("banco com mais usado: candidato é item marcado como pago; reabrir faz bater", () => {
    const d = ds([compra({ id: 911, valor: 25, pago: true }), compra({ valor: 100 })], {}, 900);
    const g = auditarCartoes(d, { 10: 875 }).grupos[0];
    expect(g.diferencaBanco).toBe(25);
    const cand = g.achados.find((x) => x.tipo === "CANDIDATO_BANCO")!;
    expect(cand.acao).toEqual({ tipo: "MARCAR_ABERTOS", ids: [911] });
    const r = aplicarCorrecoesCartao(d, [cand], { agora: AGORA });
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(875);
  });

  it("sem candidato: oferece ajustar o limite total para o banco", () => {
    const d = ds([compra({ valor: 100 })], {}, 900);
    const g = auditarCartoes(d, { 10: 950 }).grupos[0];
    const a = g.achados.find((x) => x.tipo === "DIFERENCA_BANCO")!;
    expect(a.acao).toEqual({ tipo: "AJUSTAR_LIMITE_TOTAL", cartaoId: 10, limiteTotal: 1050 });
    const r = aplicarCorrecoesCartao(d, [a], { agora: AGORA });
    expect(r.ds.cartoes[0].limiteTotal).toBe(1050);
    expect(r.ds.cartoes[0].limiteDisponivel).toBe(950);
  });

  it("limite do banco igual ao calculado: sem achado de diferença", () => {
    const d = ds([compra({ valor: 100 })], {}, 900);
    const g = auditarCartoes(d, { 10: 900 }).grupos[0];
    expect(g.diferencaBanco).toBe(0);
    expect(g.achados).toEqual([]);
  });
});

describe("aplicarCorrecoesCartao", () => {
  it("é idempotente e nunca altera valores/datas", () => {
    const pg = desp({ natureza: "PAGAMENTO_FATURA", valor: 100, conta: "111", grupoId: "fatura:10:2025-03", data: dt(2025, 4, 5) });
    const d = ds([compra({ id: 1001, valor: 100, pago: true }), compra({ id: 1002, valor: 40 }), pg], {}, 500);
    const r1 = corrigirSeguras(d, { agora: AGORA });
    const r2 = corrigirSeguras(r1.ds, { agora: AGORA });
    expect(r2.ds.despesas).toEqual(r1.ds.despesas);
    expect(r2.correcoes).toEqual([]);
    for (const x of d.despesas) {
      const y = r1.ds.despesas.find((z) => z.id === x.id)!;
      expect(toCents(y.valor)).toBe(toCents(x.valor));
      expect(y.data).toBe(x.data);
    }
  });
});
