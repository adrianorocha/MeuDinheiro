import { describe, expect, it } from "vitest";
import { auditarSaldos, recalcularSaldos } from "./conferencia";
import { cartao, conta, dataset, desp, dt } from "./fixtures";
import { toCents } from "./money";
import type { Despesa } from "./types";

const AGORA = dt(2025, 3, 15);

function base(extra: Partial<Despesa>[] = []) {
  return dataset({
    contas: [conta({ id: 1, conta: "111", saldo: 0 }), conta({ id: 2, conta: "222", saldo: 0, banco: "Banco B" })],
    cartoes: [cartao({ id: 10, contaId: 1, limiteTotal: 1000, limiteDisponivel: 1000 })],
    despesas: [
      desp({ natureza: "SALDO_INICIAL", tipo: "CREDITO", valor: 500, descricao: "Saldo inicial" }),
      desp({ tipo: "CREDITO", valor: 3000.1, descricao: "Salário" }),
      desp({ tipo: "DEBITO", valor: 120.35 }),
      desp({ tipo: "DEBITO", valor: 80, natureza: "PAGAMENTO_FATURA" }),
      desp({ tipo: "CREDITO", valor: 200, natureza: "TRANSFERENCIA", grupoId: "transf:a" }),
      desp({ tipo: "DEBITO", valor: 50, natureza: "TRANSFERENCIA", grupoId: "transf:b" }),
      desp({ tipo: "DEBITO", valor: 70, natureza: "APORTE_META" }),
      desp({ tipo: "CREDITO", valor: 30, natureza: "RESGATE_META" }),
      desp({ tipo: "CREDITO", valor: 0.55, natureza: "AJUSTE" }),
      // pendências
      desp({ tipo: "CREDITO", valor: 100, pago: false, data: dt(2025, 3, 20) }),
      desp({ tipo: "DEBITO", valor: 10, pago: false, data: dt(2025, 3, 1) }),
      desp({ tipo: "DEBITO", valor: 20, pago: false, data: dt(2025, 3, 25) }),
      desp({ tipo: "DEBITO", valor: 40, pago: false, data: dt(2025, 8, 25) }),
      // cartão em aberto (não entra no saldo)
      desp({ tipo: "DEBITO", valor: 300, pago: false, cartaoId: 10, data: dt(2025, 3, 10) }),
      ...extra.map((e) => desp(e)),
    ],
  });
}

describe("auditarSaldos - conta", () => {
  it("decomposição fecha com o saldo calculado", () => {
    const r = auditarSaldos(base(), AGORA);
    const c = r.contas[0];
    expect(c.totalDecomposicaoCentavos).toBe(toCents(c.saldoCalculado));
    expect(c.saldoCalculado).toBe(3410.3);
    const chaves = c.decomposicao.map((l) => l.chave);
    expect(chaves).toEqual([
      "SALDO_INICIAL",
      "RECEITAS",
      "DESPESAS",
      "PAGAMENTOS_FATURA",
      "TRANSFERENCIAS_ENTRADA",
      "TRANSFERENCIAS_SAIDA",
      "APORTES_META",
      "RESGATES_META",
      "AJUSTES",
    ]);
    expect(c.decomposicao.find((l) => l.chave === "DESPESAS")?.centavos).toBe(-12035);
  });

  it("classifica pendências", () => {
    const p = auditarSaldos(base(), AGORA).contas[0].pendencias;
    expect(p).toEqual({ receitasPrevistas: 100, despesasAtrasadas: 10, despesasAVencer30d: 20, despesasFuturas: 40, comprasCartaoEmAberto: 300 });
  });

  it("detecta divergência de cache e recalcularSaldos corrige", () => {
    const ds = base();
    const r0 = auditarSaldos(ds, AGORA);
    expect(r0.contas[0].ok).toBe(false);
    expect(r0.contas[0].diferenca).toBe(-3410.3);
    expect(r0.cartoes[0].ok).toBe(false);
    expect(r0.divergencias).toBe(2);
    const { ds: novo, correcoes } = recalcularSaldos(ds);
    expect(correcoes.map((c) => `${c.tipo}:${c.id}`)).toEqual(["conta:1", "cartao:10"]);
    expect(correcoes[0]).toMatchObject({ antes: 0, depois: 3410.3 });
    expect(correcoes[1]).toMatchObject({ antes: 1000, depois: 700 });
    const r1 = auditarSaldos(novo, AGORA);
    expect(r1.divergencias).toBe(0);
    expect(r1.contas[1].ok).toBe(true);
  });

  it("é idempotente", () => {
    const um = recalcularSaldos(base());
    const dois = recalcularSaldos(um.ds);
    expect(dois.correcoes).toEqual([]);
    expect(dois.ds).toBe(um.ds);
  });

  it("natureza desconhecida fica em OUTROS e a soma ainda fecha", () => {
    const ds = base([{ tipo: "CREDITO", valor: 5, natureza: "XYZ" as never }]);
    const c = auditarSaldos(ds, AGORA).contas[0];
    expect(c.decomposicao.at(-1)?.chave).toBe("OUTROS");
    expect(c.totalDecomposicaoCentavos).toBe(toCents(c.saldoCalculado));
  });
});

describe("auditarSaldos - cartões", () => {
  it("compara o limite gravado de cada cartão do grupo e separa fatura atual x futuras", () => {
    const ds = base([{ tipo: "DEBITO", valor: 100, pago: false, cartaoId: 11, data: dt(2025, 6, 10) }]);
    ds.cartoes.push(cartao({ id: 11, nome: "Virtual", cartaoPrincipalId: 10, limiteDisponivel: 600, limiteTotal: 0 }));
    const g = auditarSaldos(ds, AGORA).cartoes;
    expect(g).toHaveLength(1);
    expect(g[0].limiteCalculado).toBe(600);
    expect(g[0].cartoes.map((c) => c.ok)).toEqual([false, true]);
    expect(g[0].emAbertoTotal).toBe(400);
    expect(g[0].faturaAtual + g[0].anteriores + g[0].parcelasFuturas).toBe(400);
    expect(g[0].parcelasFuturas).toBe(100);
  });
});

describe("auditarSaldos - totais e explicação", () => {
  it("separa pagas de pendentes e explica por que Entradas − Saídas ≠ Saldo", () => {
    const r = auditarSaldos(base(), AGORA);
    const t = r.totais;
    expect(t.entradasRealizadas).toBe(3000.1);
    expect(t.entradasPrevistas).toBe(100);
    expect(t.saidasPagas).toBe(120.35);
    expect(t.saidasPendentes).toBe(370);
    expect(t.saidasTotal).toBe(490.35);
    expect(t.resultado).toBe(2509.75);
    expect(t.saldoContas).toBe(3410.3);
    expect(t.resultado).not.toBe(t.saldoContas);
    expect(t.explicacao).toContain("não é o saldo");
    expect(t.explicacao).toContain("pendentes ou futuras");
  });
});

describe("auditarSaldos - inconsistências", () => {
  it("lista sem apagar nada", () => {
    const ds = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [cartao({ id: 10 }), cartao({ id: 12, cartaoPrincipalId: 99 })],
      despesas: [
        desp({ id: 500, conta: "999" }),
        desp({ id: 501, cartaoId: 77 }),
        desp({ id: 502, valor: 0 }),
        desp({ id: 503, valor: Number.NaN }),
        desp({ id: 504, natureza: "XYZ" as never }),
        desp({ id: 505 }),
        desp({ id: 505 }),
        desp({ id: 506, grupoId: "parc:1", descricao: "TV (1/3)" }),
        desp({ id: 507, grupoId: "parc:1", descricao: "TV (2/3)" }),
      ],
    });
    const antes = JSON.stringify(ds);
    const tipos = auditarSaldos(ds, AGORA).inconsistencias.map((i) => i.tipo);
    expect(JSON.stringify(ds)).toBe(antes);
    for (const t of ["CONTA_INEXISTENTE", "CARTAO_INEXISTENTE", "VALOR_INVALIDO", "NATUREZA_DESCONHECIDA", "ID_DUPLICADO", "VIRTUAL_SEM_PRINCIPAL", "PARCELAS_INCOMPLETAS"]) {
      expect(tipos).toContain(t);
    }
    expect(tipos.filter((t) => t === "VALOR_INVALIDO")).toHaveLength(2);
    const parc = auditarSaldos(ds, AGORA).inconsistencias.find((i) => i.tipo === "PARCELAS_INCOMPLETAS");
    expect(parc?.severidade).toBe("info");
  });

  it("dados saudáveis não geram inconsistências", () => {
    expect(auditarSaldos(base(), AGORA).inconsistencias).toEqual([]);
  });
});
