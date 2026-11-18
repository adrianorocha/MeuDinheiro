import { describe, expect, it } from "vitest";
import { melhorDiaCompra } from "./analises";
import { faturaDaCompra } from "./calc";
import { editarCartao, criarCartao } from "./operations";
import { analiseFaturasDoCartao, reprocessarSaldos } from "./reprocessar";
import { cartao, conta, dataset, desp, dt } from "./fixtures";

const caixa = { diaFechamento: 10, diaVencimento: 17, fechamentoMelhorDia: false };
const nubank = { diaFechamento: 10, diaVencimento: 17, fechamentoMelhorDia: true };
const ctx = { agora: dt(2025, 4, 1), uuid: () => "u" };

describe("R77 - dia do fechamento como melhor dia de compra", () => {
  it("padrão (Caixa): compra no dia do fechamento ainda entra na fatura que fecha", () => {
    expect(faturaDaCompra(caixa, dt(2025, 3, 9))).toEqual({ mes: 3, ano: 2025 });
    expect(faturaDaCompra(caixa, dt(2025, 3, 10))).toEqual({ mes: 3, ano: 2025 });
    expect(faturaDaCompra(caixa, dt(2025, 3, 11))).toEqual({ mes: 4, ano: 2025 });
    expect(melhorDiaCompra(caixa, 3, 2025).dia).toBe(11);
  });
  it("com a opção: compra no dia do fechamento vai para a próxima fatura", () => {
    expect(faturaDaCompra(nubank, dt(2025, 3, 9))).toEqual({ mes: 3, ano: 2025 });
    expect(faturaDaCompra(nubank, dt(2025, 3, 10))).toEqual({ mes: 4, ano: 2025 });
    expect(faturaDaCompra(nubank, dt(2025, 12, 10))).toEqual({ mes: 1, ano: 2026 });
    expect(melhorDiaCompra(nubank, 3, 2025).dia).toBe(10);
  });
  it("fechamento no dia 1 e no fim do mês", () => {
    expect(faturaDaCompra({ diaFechamento: 1, fechamentoMelhorDia: true }, dt(2025, 3, 1))).toEqual({ mes: 4, ano: 2025 });
    expect(faturaDaCompra({ diaFechamento: 31, fechamentoMelhorDia: true }, dt(2025, 4, 30))).toEqual({ mes: 5, ano: 2025 });
    expect(faturaDaCompra({ diaFechamento: 31, fechamentoMelhorDia: true }, dt(2025, 4, 29))).toEqual({ mes: 4, ano: 2025 });
    expect(melhorDiaCompra({ diaFechamento: 31, diaVencimento: 10, fechamentoMelhorDia: true }, 4, 2025).dia).toBe(30);
  });
  it("cartão sem o campo (dados antigos) mantém o comportamento atual", () => {
    expect(faturaDaCompra({ diaFechamento: 10 }, dt(2025, 3, 10))).toEqual({ mes: 3, ano: 2025 });
  });

  it("criar/editar propaga a opção aos virtuais", () => {
    let ds = dataset({ contas: [conta({ id: 1, conta: "111" })] });
    const r = criarCartao(ds, { nome: "F", finalCartao: "1", tipo: "CRÉDITO", limiteTotal: 1000, diaFechamento: 10, diaVencimento: 17, contaId: 1, fechamentoMelhorDia: true }, ctx);
    if (!r.ok) throw new Error(r.erro);
    ds = r.ds;
    const v = criarCartao(ds, { nome: "V", finalCartao: "2", tipo: "CRÉDITO", limiteTotal: 0, diaFechamento: 1, diaVencimento: 1, contaId: 1, cartaoPrincipalId: r.cartao.id }, ctx);
    if (!v.ok) throw new Error(v.erro);
    expect(v.cartao.fechamentoMelhorDia).toBe(true);
    const e = editarCartao(v.ds, r.cartao.id, { fechamentoMelhorDia: false });
    if (!e.ok) throw new Error(e.erro);
    expect(e.ds.cartoes.every((c) => !c.fechamentoMelhorDia)).toBe(true);
  });

  it("reprocessar o cartão: compra do dia do fechamento que volta para fatura paga fica paga; análise mostra cada fatura", () => {
    // fatura de março (fecha dia 10) paga; compra no dia 10 estava em abril (opção ligada) e a opção foi desligada
    const c = cartao({ id: 10, contaId: 1, diaFechamento: 10, diaVencimento: 17, fechamentoMelhorDia: false });
    const base = dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [c],
      despesas: [
        desp({ id: 1, valor: 100, conta: "111", cartaoId: 10, data: dt(2025, 3, 5), pago: true }),
        desp({ id: 2, valor: 50, conta: "111", cartaoId: 10, data: dt(2025, 3, 10), pago: false }),
        desp({ id: 3, descricao: "Pagamento", valor: 100, conta: "111", tipo: "DEBITO", natureza: "PAGAMENTO_FATURA", grupoId: "fatura:10:2025-03", data: dt(2025, 3, 17), pago: true }),
      ],
    });
    const linhas = analiseFaturasDoCartao(base, 10);
    expect(linhas).toHaveLength(1);
    expect(linhas[0]).toMatchObject({ mes: 3, ano: 2025, itens: 2, paga: true, pendentesEmFaturaPaga: 1, noDiaDoFechamento: 1 });
    const r = reprocessarSaldos(base, { fatura: { cartaoId: 10 }, agora: dt(2025, 4, 1) });
    expect(r.itensMarcados).toBe(1);
    expect(r.ds.despesas.find((d) => d.id === 2)?.pago).toBe(true);
    expect(reprocessarSaldos(r.ds, { fatura: { cartaoId: 10 } }).itensMarcados).toBe(0);
  });
});
