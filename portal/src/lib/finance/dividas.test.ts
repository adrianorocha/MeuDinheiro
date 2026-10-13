import { describe, expect, it } from "vitest";
import { exportarBackup, importarBackup } from "../store/backup";
import { recalcularTudo } from "./calc";
import {
  aplicarPagamento,
  compararEstrategias,
  dataQuitacao,
  excluirDivida,
  jurosDoMes,
  pagarDivida,
  paraSim,
  progressoQuitacao,
  registrarJurosDivida,
  salvarDivida,
  simular,
  type DividaSim,
} from "./dividas";
import { conta, dataset, dt, desp } from "./fixtures";
import type { Dataset, Divida } from "./types";

// Mesmos cenários e números de DividasTest.kt (app).
const S1: DividaSim[] = [{ id: 1, nome: "A", saldo: 10000, taxaBp: 100, minimo: 5000 }];
const S2: DividaSim[] = [
  { id: 1, nome: "Cartão", saldo: 100000, taxaBp: 1000, minimo: 10000 },
  { id: 2, nome: "Empréstimo", saldo: 50000, taxaBp: 200, minimo: 5000 },
];

describe("R58 - simulador de quitação", () => {
  it("juros mensais em centavos, meio para cima", () => {
    expect(jurosDoMes(12345, 250)).toBe(309); // 308,625
    expect(jurosDoMes(100, 50)).toBe(1); // 0,5 -> 1
    expect(jurosDoMes(30, 50)).toBe(0); // 0,15 -> 0
    expect(jurosDoMes(100000, 0)).toBe(0);
    expect(jurosDoMes(0, 1000)).toBe(0);
  });

  it("uma dívida só com mínimos: cronograma e último pagamento ajustado", () => {
    const r = simular(S1, 0, "MINIMOS");
    expect(r.meses).toBe(3);
    expect(r.jurosTotal).toBe(153);
    expect(r.pagoTotal).toBe(10153);
    expect(r.cronograma).toEqual([
      { mes: 1, juros: 100, pago: 5000, saldoFinal: 5100 },
      { mes: 2, juros: 51, pago: 5000, saldoFinal: 151 },
      { mes: 3, juros: 2, pago: 153, saldoFinal: 0 },
    ]);
    expect(r.nuncaQuita).toBe(false);
    expect(r.quitacoes).toEqual([{ id: 1, nome: "A", mes: 3 }]);
  });

  it("bola de neve x avalanche com orçamento extra", () => {
    const c = compararEstrategias(S2, 20000);
    expect(c.bolaDeNeve.meses).toBe(6);
    expect(c.bolaDeNeve.jurosTotal).toBe(46039);
    expect(c.bolaDeNeve.pagoTotal).toBe(196039);
    expect(c.bolaDeNeve.quitacoes).toEqual([
      { id: 2, nome: "Empréstimo", mes: 3 },
      { id: 1, nome: "Cartão", mes: 6 },
    ]);
    expect(c.bolaDeNeve.cronograma[0]).toEqual({ mes: 1, juros: 11000, pago: 35000, saldoFinal: 126000 });
    expect(c.bolaDeNeve.cronograma[1]).toEqual({ mes: 2, juros: 10520, pago: 35000, saldoFinal: 101520 });

    expect(c.avalanche.meses).toBe(6);
    expect(c.avalanche.jurosTotal).toBe(32223);
    expect(c.avalanche.pagoTotal).toBe(182223);
    expect(c.avalanche.quitacoes).toEqual([
      { id: 1, nome: "Cartão", mes: 5 },
      { id: 2, nome: "Empréstimo", mes: 6 },
    ]);
    expect(c.avalanche.cronograma[1]).toEqual({ mes: 2, juros: 8920, pago: 35000, saldoFinal: 99920 });

    // soma paga = principal + juros
    expect(c.bolaDeNeve.pagoTotal).toBe(150000 + c.bolaDeNeve.jurosTotal);
    expect(c.avalanche.pagoTotal).toBe(150000 + c.avalanche.jurosTotal);
    expect(c.economia.avalancheVsBola).toBe(46039 - 32223);
  });

  it("só mínimos que não cobrem os juros: nunca quita, com aviso e limite de 600 meses", () => {
    const c = compararEstrategias(S2, 20000);
    expect(c.minimos.nuncaQuita).toBe(true);
    expect(c.minimos.meses).toBeNull();
    expect(c.minimos.cronograma.length).toBe(600);
    expect(c.minimos.quitacoes).toEqual([{ id: 2, nome: "Empréstimo", mes: 12 }]);
    expect(c.minimos.saldoRestante).toBe(100000);
    expect(c.economia.bolaVsMinimos).toBeNull();
    expect(c.economia.avalancheVsMinimos).toBeNull();
    expect(c.avisos).toEqual([
      "Cartão: o pagamento mínimo (R$ 100,00) não supera os juros do mês (R$ 100,00).",
      "Pagando só os mínimos, as dívidas nunca são quitadas (limite de 600 meses).",
    ]);
  });

  it("juros que explodem param antes dos 600 meses e marcam 'nunca quita'", () => {
    const r = simular([{ id: 1, nome: "X", saldo: 100000, taxaBp: 1000, minimo: 5000 }], 0, "MINIMOS");
    expect(r.nuncaQuita).toBe(true);
    expect(r.meses).toBeNull();
    expect(r.saldoRestante).toBeGreaterThan(100000);
    expect(r.cronograma.length).toBeLessThan(600);
  });

  it("sem juros e sem dívidas", () => {
    const r = simular([{ id: 1, nome: "Z", saldo: 30000, taxaBp: 0, minimo: 10000 }], 0, "MINIMOS");
    expect(r.meses).toBe(3);
    expect(r.jurosTotal).toBe(0);
    expect(r.pagoTotal).toBe(30000);
    const vazio = simular([], 5000, "AVALANCHE");
    expect(vazio.meses).toBe(0);
    expect(vazio.nuncaQuita).toBe(false);
    // sem mínimo informado, só o extra paga (estratégia) ou nada paga (mínimos)
    const semMin = [{ id: 1, nome: "Z", saldo: 30000, taxaBp: 0, minimo: 0 }];
    expect(simular(semMin, 10000, "BOLA_DE_NEVE").meses).toBe(3);
    expect(simular(semMin, 10000, "MINIMOS").nuncaQuita).toBe(true);
  });

  it("data estimada de quitação", () => {
    expect(dataQuitacao({ ano: 2025, mes: 11 }, 3)).toEqual({ ano: 2026, mes: 1 });
    expect(dataQuitacao({ ano: 2025, mes: 11 }, 1)).toEqual({ ano: 2025, mes: 11 });
    expect(dataQuitacao({ ano: 2025, mes: 1 }, 25)).toEqual({ ano: 2027, mes: 1 });
    expect(dataQuitacao({ ano: 2025, mes: 1 }, null)).toBeNull();
  });

  it("converte a dívida cadastrada (reais, % a.m.) para centavos e basis points", () => {
    const d: Divida = { id: 9, nome: "Cheque", tipo: "CHEQUE_ESPECIAL", saldoDevedor: 1234.56, taxaJurosMensal: 2.5, parcelaMinima: 100.1, ativa: true };
    expect(paraSim(d)).toEqual({ id: 9, nome: "Cheque", saldo: 123456, taxaBp: 250, minimo: 10010 });
    expect(paraSim({ ...d, ativa: false })).toBeNull();
    expect(paraSim({ ...d, saldoDevedor: 0 })).toBeNull();
    expect(paraSim({ ...d, taxaJurosMensal: null, parcelaMinima: null })).toMatchObject({ taxaBp: 0, minimo: 0 });
  });
});

function base(): Dataset {
  const divida: Divida = { id: 5, nome: "Empréstimo Banco", tipo: "EMPRESTIMO", saldoDevedor: 1000, valorOriginal: 1000, taxaJurosMensal: 2, parcelaMinima: 200, ativa: true };
  return recalcularTudo(
    dataset({
      contas: [conta({ id: 1, conta: "111", saldo: 0 })],
      despesas: [desp({ id: 100, descricao: "Saldo inicial", valor: 2000, tipo: "CREDITO", natureza: "SALDO_INICIAL", conta: "111" })],
      dividas: [divida],
    }),
  );
}
const ctx = { agora: dt(2025, 6, 15), uuid: () => "u" };

describe("R58 - pagamento da dívida", () => {
  it("cria lançamento NORMAL em 'Dívidas' com grupoId e abate o saldo devedor; saldo da conta cai uma vez", () => {
    const ds = base();
    expect(ds.contas[0].saldo).toBe(2000);
    const r = pagarDivida(ds, { dividaId: 5, valor: 300, conta: "111" }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.pagamento).toMatchObject({ tipo: "DEBITO", natureza: "NORMAL", categoria: "Dívidas", grupoId: "divida:5", pago: true, valor: 300, conta: "111" });
    expect(r.ds.dividas[0].saldoDevedor).toBe(700);
    expect(r.ds.dividas[0].ativa).toBe(true);
    expect(r.ds.contas[0].saldo).toBe(1700);
    expect(r.quitada).toBe(false);
  });

  it("juros do mês acrescentam ao saldo antes de abater", () => {
    const r = pagarDivida(base(), { dividaId: 5, valor: 200, conta: "111", juros: 20 }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.dividas[0].saldoDevedor).toBe(820); // 1000 + 20 - 200
    expect(r.ds.contas[0].saldo).toBe(1800); // juros não saem da conta por si só
  });

  it("quitação total marca a dívida como inativa e aceita categoria escolhida", () => {
    const r = pagarDivida(base(), { dividaId: 5, valor: 1000, conta: "111", categoria: "Banco" }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.quitada).toBe(true);
    expect(r.ds.dividas[0]).toMatchObject({ saldoDevedor: 0, ativa: false });
    expect(r.pagamento.categoria).toBe("Banco");
  });

  it("recusa pagamento maior que o devido, valor zero, conta ou dívida inexistente", () => {
    expect(pagarDivida(base(), { dividaId: 5, valor: 1000.01, conta: "111" }, ctx)).toMatchObject({ ok: false });
    expect(pagarDivida(base(), { dividaId: 5, valor: 0, conta: "111" }, ctx)).toMatchObject({ ok: false });
    expect(pagarDivida(base(), { dividaId: 5, valor: 10, conta: "999" }, ctx)).toMatchObject({ ok: false, erro: "Conta não encontrada." });
    expect(pagarDivida(base(), { dividaId: 6, valor: 10, conta: "111" }, ctx)).toMatchObject({ ok: false, erro: "Dívida não encontrada." });
    expect(aplicarPagamento(100000, 2000, 102001)).toMatchObject({ ok: false });
    expect(aplicarPagamento(100000, 2000, 102000)).toEqual({ ok: true, saldo: 0 });
  });

  it("registrar juros acrescenta ao saldo devedor sem tocar na conta", () => {
    const ds = base();
    const r = registrarJurosDivida(ds, 5, 33.33);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.dividas[0].saldoDevedor).toBe(1033.33);
    expect(r.ds.contas).toBe(ds.contas);
    expect(registrarJurosDivida(ds, 5, 0)).toMatchObject({ ok: false });
  });

  it("cadastro: valida, guarda o valor original e exclui", () => {
    const ds = dataset();
    expect(salvarDivida(ds, { nome: " ", tipo: "OUTRA", saldoDevedor: 10, ativa: true }, undefined, ctx)).toMatchObject({ ok: false });
    expect(salvarDivida(ds, { nome: "X", tipo: "OUTRA", saldoDevedor: 10, taxaJurosMensal: 101, ativa: true }, undefined, ctx)).toMatchObject({ ok: false });
    const r = salvarDivida(ds, { nome: " Carro ", tipo: "FINANCIAMENTO", saldoDevedor: 5000, taxaJurosMensal: 1.5, parcelaMinima: 600, diaVencimento: 10, ativa: true }, undefined, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.divida).toMatchObject({ nome: "Carro", valorOriginal: 5000, taxaJurosMensal: 1.5, parcelaMinima: 600, conta: null });
    const e = salvarDivida(r.ds, { nome: "Carro", tipo: "FINANCIAMENTO", saldoDevedor: 4000, ativa: true }, r.divida.id, ctx);
    if (!e.ok) throw new Error(e.erro);
    expect(e.divida.valorOriginal).toBe(5000); // preserva o original
    expect(progressoQuitacao(e.divida)).toBeCloseTo(0.2, 5);
    const x = excluirDivida(e.ds, r.divida.id);
    if (!x.ok) throw new Error(x.erro);
    expect(x.ds.dividas).toEqual([]);
  });
});

describe("R58 - backup", () => {
  it("backup antigo sem as coleções novas é importado como 'sem dívidas'", () => {
    const antigo = { versaoBackup: 2, contas: [{ id: 1, saldo: 0, banco: "X", pic: "", agencia: "1", conta: "10", titular: "T" }], despesas: [] };
    const r = importarBackup(JSON.stringify(antigo), dt(2025, 6, 15));
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.dividas).toEqual([]);
    expect(r.dataset.pessoas).toEqual([]);
    expect(r.dataset.acertos).toEqual([]);
  });

  it("ida e volta das dívidas, tolerando campos ausentes e tipo desconhecido", () => {
    const ds = base();
    const volta = importarBackup(JSON.stringify(exportarBackup(ds)), dt(2025, 6, 15));
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.dividas).toEqual(ds.dividas.map((d) => ({ ...d, credor: null, parcelasRestantes: null, diaVencimento: null, conta: null })));
    const parcial = importarBackup(JSON.stringify({ dividas: [{ id: 7, nome: "Y", tipo: "XYZ", saldoDevedor: 50 }] }), dt(2025, 6, 15));
    if (!parcial.ok) throw new Error(parcial.erro);
    expect(parcial.dataset.dividas[0]).toMatchObject({ id: 7, tipo: "OUTRA", saldoDevedor: 50, taxaJurosMensal: 0, ativa: true, valorOriginal: null });
  });
});
