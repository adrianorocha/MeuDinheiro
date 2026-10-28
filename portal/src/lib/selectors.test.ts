import { describe, expect, it } from "vitest";
import { cartao, dataset, desp, dt } from "./finance/fixtures";
import { contasAPagar, despesasPorCategoria, periodoDe, serieMensal, ultimosSnapshots } from "./selectors";

describe("selectors", () => {
  const agora = dt(2025, 1, 15);
  it("período do mês passado cruza o ano", () => {
    const p = periodoDe("anterior", agora);
    expect(new Date(p.inicio).getMonth()).toBe(11);
    expect(new Date(p.inicio).getFullYear()).toBe(2024);
  });
  it("despesas por categoria abatem estorno e ignoram receitas/transferências", () => {
    const p = periodoDe("mes", agora);
    const lista = [
      desp({ categoria: "Compras", valor: 100, data: dt(2025, 1, 5) }),
      desp({ categoria: "compras ", valor: 50, data: dt(2025, 1, 6) }),
      desp({ categoria: "Compras", valor: 30, tipo: "CREDITO", cartaoId: 10, data: dt(2025, 1, 7), pago: false }),
      desp({ categoria: "Salário", valor: 999, tipo: "CREDITO", data: dt(2025, 1, 7) }),
      desp({ categoria: "Transferência", valor: 500, natureza: "TRANSFERENCIA", data: dt(2025, 1, 8) }),
    ];
    expect(despesasPorCategoria(lista, p.inicio, p.fim)).toEqual([{ categoria: "Compras", valor: 120 }]);
  });
  it("série mensal tem n pontos em ordem cronológica", () => {
    const s = serieMensal([desp({ data: dt(2024, 12, 10), valor: 10 })], 3, agora);
    expect(s.map((x) => [x.mes, x.ano])).toEqual([[11, 2024], [12, 2024], [1, 2025]]);
    expect(s[1].despesasTotal).toBe(10);
  });
  it("contas a pagar marca atrasadas e ignora cartão", () => {
    const r = contasAPagar(
      [
        desp({ pago: false, data: dt(2025, 1, 10) }),
        desp({ pago: false, data: dt(2025, 1, 20) }),
        desp({ pago: false, data: dt(2025, 1, 20), cartaoId: cartao().id }),
        desp({ pago: true, data: dt(2025, 1, 12) }),
      ],
      agora,
    );
    expect(r.map((x) => x.atrasada)).toEqual([true, false]);
  });
  it("últimos snapshots", () => {
    const ds = dataset({ patrimonio: [3, 1, 2].map((i) => ({ id: i, dataMillis: i, valorTotal: i, mesReferencia: "X" })) });
    expect(ultimosSnapshots(ds, 2).map((p) => p.id)).toEqual([2, 3]);
  });
});
