import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import type { Dataset } from "../finance/types";
import { conferirFatura } from "./fatura-total";
import { casar, lancamentosDoDestino } from "./matching";
import { aplicarConciliacao, montarPlano } from "./plano";
import { resumoConciliacao } from "./relatorio";
import { diferencaSaldo } from "./saldo";
import type { Destino, TransacaoBanco } from "./tipos";

const CARTAO: Destino = { tipo: "CARTAO", cartaoId: 10 };
const T = (fitid: string, dia: number, valor: number, descricao: string): TransacaoBanco => ({ fitid, data: dt(2025, 3, dia), valor, descricao });

// fatura de março: fecha 25/03 (compras de 26/02 a 25/03)
function base(extra: Dataset["despesas"] = []): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    cartoes: [cartao({ id: 10, contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000 }), cartao({ id: 11, finalCartao: "9999", contaId: 1, cartaoPrincipalId: 10, limiteTotal: 5000, limiteDisponivel: 5000 })],
    despesas: extra,
  });
}
const extrato = [T("a", 10, -100, "LOJA A"), T("b", 12, -50.5, "LOJA B"), T("c", 15, 20, "ESTORNO LOJA B")];

describe("R102 - total da fatura × soma dos lançamentos", () => {
  it("soma o grupo inteiro (principal + virtual), pagos e em aberto, só das faturas do extrato", () => {
    const ds = base([
      desp({ id: 1, descricao: "Loja A", valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: true }),
      desp({ id: 2, descricao: "Loja B", valor: 50.5, data: dt(2025, 3, 12), cartaoId: 11, pago: false }),
      desp({ id: 3, descricao: "Estorno", valor: 20, tipo: "CREDITO", data: dt(2025, 3, 15), cartaoId: 10, pago: false }),
      desp({ id: 4, descricao: "Outra fatura", valor: 999, data: dt(2025, 4, 10), cartaoId: 10, pago: false }),
    ]);
    const c = conferirFatura(ds, CARTAO, extrato)!;
    expect(c).toMatchObject({ totalExtrato: 130.5, totalApp: 130.5, emAbertoApp: 30.5, diferenca: 0, bate: true });
    expect(c.faturas).toHaveLength(1);
    expect(c.faturas[0]).toMatchObject({ mes: 3, ano: 2025, itens: 3 });
  });

  it("marcar a fatura como paga não altera o total (só o em aberto)", () => {
    const ds = base([desp({ id: 1, valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: false })]);
    const t = [T("a", 10, -100, "LOJA A")];
    const r = casar(t, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const ap = aplicarConciliacao(ds, { ...montarPlano(r, ds, CARTAO), faturaPaga: true }, dt(2025, 4, 10));
    if (!ap.ok) throw new Error(ap.erro);
    expect(conferirFatura(ds, CARTAO, t)).toMatchObject({ totalApp: 100, emAbertoApp: 100, bate: true });
    expect(conferirFatura(ap.ds, CARTAO, t)).toMatchObject({ totalApp: 100, emAbertoApp: 0, bate: true });
  });

  it("aponta a diferença quando falta lançamento e passa a bater depois de criar os que faltam", () => {
    const ds = base([desp({ id: 1, valor: 100, data: dt(2025, 3, 10), cartaoId: 10 })]);
    const antes = conferirFatura(ds, CARTAO, extrato)!;
    expect(antes).toMatchObject({ totalExtrato: 130.5, totalApp: 100, diferenca: 30.5, bate: false });
    const r = casar(extrato, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const ap = aplicarConciliacao(ds, montarPlano(r, ds, CARTAO, { criar: true }), dt(2025, 4, 1));
    if (!ap.ok) throw new Error(ap.erro);
    expect(conferirFatura(ap.ds, CARTAO, extrato)).toMatchObject({ diferenca: 0, bate: true });
  });

  it("a linha de pagamento da fatura no extrato não entra no total; conta e extrato vazio dão null", () => {
    const ds = base([desp({ id: 1, valor: 100, data: dt(2025, 3, 10), cartaoId: 10 })]);
    expect(conferirFatura(ds, CARTAO, [T("a", 10, -100, "LOJA A"), T("p", 20, 100, "PAGAMENTO EFETUADO")])?.totalExtrato).toBe(100);
    expect(conferirFatura(ds, { tipo: "CONTA", conta: "111" }, extrato)).toBeNull();
    expect(conferirFatura(ds, CARTAO, [])).toBeNull();
  });

  it("saldo final do cartão compara com o total da fatura (inclusive paga) e o resumo traz a conferência", () => {
    const ds = base([desp({ id: 1, valor: 100, data: dt(2025, 3, 10), cartaoId: 10, pago: true })]);
    const t = [T("a", 10, -100, "LOJA A")];
    expect(diferencaSaldo(ds, CARTAO, { valor: -100, data: dt(2025, 3, 25) }, t)).toEqual({ saldoBanco: -100, saldoApp: -100, diferenca: 0 });
    const res = resumoConciliacao(casar(t, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 }), ds, CARTAO, { periodo: { inicio: t[0].data, fim: t[0].data }, saldoFinal: { valor: -100, data: dt(2025, 3, 25) } });
    expect(res.fatura?.bate).toBe(true);
    expect(res.saldo?.diferenca).toBe(0);
  });
});
