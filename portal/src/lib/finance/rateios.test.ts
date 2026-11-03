import { describe, expect, it } from "vitest";
import { exportarBackup, importarBackup } from "../store/backup";
import { recalcularTudo } from "./calc";
import { conta, dataset, dt, desp } from "./fixtures";
import { excluirLancamento } from "./operations";
import {
  comprometidoAReceber,
  criarAcertoAvulso,
  desfazerRateio,
  dividirDespesa,
  excluirAcerto,
  excluirPessoa,
  gastoLiquido,
  quitarAcerto,
  ratear,
  resumoRateios,
  salvarPessoa,
  saldosPorPessoa,
  textoCobranca,
  vincularLancamentos,
} from "./rateios";
import type { Acerto, Dataset } from "./types";

// Mesmos cenários e números de RateiosTest.kt (app).
describe("R59 - rateio puro (centavos, sobra no pagador)", () => {
  it("divisão igual: sobra fica com você", () => {
    expect(ratear(10000, "IGUAL", [{ pessoaId: 1 }, { pessoaId: 2 }])).toEqual({
      ok: true,
      cotas: [
        { pessoaId: 1, centavos: 3333 },
        { pessoaId: 2, centavos: 3333 },
      ],
      voce: 3334,
    });
    expect(ratear(10001, "IGUAL", [{ pessoaId: 1 }])).toEqual({ ok: true, cotas: [{ pessoaId: 1, centavos: 5000 }], voce: 5001 });
  });

  it("por valor: você fica com o resto; recusa soma acima do total e valor zerado", () => {
    expect(ratear(10000, "VALOR", [{ pessoaId: 1, valor: 3000 }, { pessoaId: 2, valor: 2500 }])).toEqual({
      ok: true,
      cotas: [
        { pessoaId: 1, centavos: 3000 },
        { pessoaId: 2, centavos: 2500 },
      ],
      voce: 4500,
    });
    expect(ratear(10000, "VALOR", [{ pessoaId: 1, valor: 6000 }, { pessoaId: 2, valor: 5000 }])).toMatchObject({ ok: false });
    expect(ratear(10000, "VALOR", [{ pessoaId: 1, valor: 10000 }])).toMatchObject({ ok: true, voce: 0 });
    expect(ratear(10000, "VALOR", [{ pessoaId: 1, valor: 0 }])).toMatchObject({ ok: false });
  });

  it("por percentual (décimos): soma exata, piso por pessoa, sobra no pagador", () => {
    const r = ratear(10001, "PERCENTUAL", [{ pessoaId: 1, percentual: 333 }, { pessoaId: 2, percentual: 333 }], 334);
    expect(r).toEqual({
      ok: true,
      cotas: [
        { pessoaId: 1, centavos: 3330 },
        { pessoaId: 2, centavos: 3330 },
      ],
      voce: 3341,
    });
    if (r.ok) expect(r.cotas.reduce((s, c) => s + c.centavos, r.voce)).toBe(10001);
  });

  it("recusa percentuais que não fecham 100%", () => {
    const duas = (a: number, b: number, voce: number) => ratear(10000, "PERCENTUAL", [{ pessoaId: 1, percentual: a }, { pessoaId: 2, percentual: b }], voce);
    expect(duas(500, 400, 0)).toEqual({ ok: false, erro: "Os percentuais precisam somar 100% (somam 90,0%)." });
    expect(duas(600, 500, 0)).toMatchObject({ ok: false });
    expect(duas(500, 400, 100)).toMatchObject({ ok: true, voce: 1000 });
    expect(duas(600, 600, -200)).toMatchObject({ ok: false });
    expect(ratear(10000, "PERCENTUAL", [{ pessoaId: 1, percentual: 500 }])).toMatchObject({ ok: false });
  });

  it("recusa entradas inválidas", () => {
    expect(ratear(0, "IGUAL", [{ pessoaId: 1 }])).toMatchObject({ ok: false });
    expect(ratear(100, "IGUAL", [])).toMatchObject({ ok: false });
    expect(ratear(100, "IGUAL", [{ pessoaId: 1 }, { pessoaId: 1 }])).toMatchObject({ ok: false });
    expect(ratear(2, "IGUAL", [{ pessoaId: 1 }, { pessoaId: 2 }])).toMatchObject({ ok: false }); // cota zerada
  });
});

const ctx = { agora: dt(2025, 6, 15), uuid: () => "u" };

function base(): Dataset {
  const ds = dataset({
    contas: [conta({ id: 1, conta: "111" })],
    despesas: [
      desp({ id: 100, descricao: "Saldo inicial", valor: 1000, tipo: "CREDITO", natureza: "SALDO_INICIAL", conta: "111" }),
      desp({ id: 200, descricao: "Pizza", valor: 100, data: dt(2025, 6, 10), conta: "111", categoria: "Alimentação" }),
    ],
    pessoas: [
      { id: 1, nome: "Ana", contato: null },
      { id: 2, nome: "Bia", contato: null },
    ],
  });
  return recalcularTudo(ds);
}

describe("R59 - dividir esta despesa", () => {
  it("cria acertos A_RECEBER sem alterar o lançamento nem os saldos", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }, { pessoaId: 2 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.voce).toBe(3334);
    expect(r.ds.despesas).toBe(ds.despesas); // mesma referência: nada foi tocado
    expect(r.ds.contas).toBe(ds.contas);
    expect(r.ds.contas[0].saldo).toBe(900);
    expect(r.ds.acertos).toHaveLength(2);
    expect(r.ds.acertos[0]).toMatchObject({ pessoaId: 1, tipo: "A_RECEBER", valor: 33.33, despesaId: 200, quitado: false, grupoRateio: "rateio:200", descricao: "Pizza", data: dt(2025, 6, 10) });

    const resumo = resumoRateios(r.ds.despesas, r.ds.acertos);
    expect(resumo).toMatchObject({ totalRateado: 10000, aReceberTerceiros: 6666, liquido: 3334 });
    expect(resumo.itens[0]).toMatchObject({ despesaId: 200, total: 10000, aReceber: 6666, liquido: 3334, pendente: 6666, orfao: false });
    expect(gastoLiquido(ds.despesas[1], r.ds.acertos)).toBe(3334);
  });

  it("recusa dividir duas vezes, despesa que não é comum, pessoa inexistente e cotas inválidas", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(dividirDespesa(r.ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 2 }] }, ctx)).toMatchObject({ ok: false });
    expect(dividirDespesa(ds, { despesaId: 100, modo: "IGUAL", participantes: [{ pessoaId: 1 }] }, ctx)).toMatchObject({ ok: false });
    expect(dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 99 }] }, ctx)).toMatchObject({ ok: false, erro: "Pessoa não encontrada." });
    expect(dividirDespesa(ds, { despesaId: 200, modo: "PERCENTUAL", participantes: [{ pessoaId: 1, percentual: 500 }], vocePercentual: 400 }, ctx)).toMatchObject({ ok: false });
    expect(dividirDespesa(ds, { despesaId: 999, modo: "IGUAL", participantes: [{ pessoaId: 1 }] }, ctx)).toMatchObject({ ok: false });
  });

  it("desfazer a divisão remove os acertos; com algum quitado, recusa", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }, { pessoaId: 2 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const q = quitarAcerto(r.ds, { acertoId: r.acertos[0].id }, ctx);
    if (!q.ok) throw new Error(q.erro);
    expect(desfazerRateio(q.ds, 200)).toMatchObject({ ok: false });
    const d = desfazerRateio(r.ds, 200);
    if (!d.ok) throw new Error(d.erro);
    expect(d.ds.acertos).toEqual([]);
    expect(d.removidos).toBe(2);
  });

  it("lançamento de origem excluído: acerto fica órfão e tolerado", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }, { pessoaId: 2 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const x = excluirLancamento(r.ds, 200);
    if (!x.ok) throw new Error(x.erro);
    expect(x.ds.acertos).toHaveLength(2);
    const resumo = resumoRateios(x.ds.despesas, x.ds.acertos);
    expect(resumo.itens).toHaveLength(1);
    expect(resumo.itens[0]).toMatchObject({ orfao: true, despesaId: 200, aReceber: 6666 });
    expect(resumo).toMatchObject({ totalRateado: 0, aReceberTerceiros: 0, liquido: 0 });
    expect(saldosPorPessoa(x.ds.pessoas, x.ds.acertos)[0].aReceber).toBe(3333);
  });

  it("resumo respeita o período (data do lançamento)", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(resumoRateios(r.ds.despesas, r.ds.acertos, { inicio: dt(2025, 6, 1), fim: dt(2025, 6, 30) }).itens).toHaveLength(1);
    expect(resumoRateios(r.ds.despesas, r.ds.acertos, { inicio: dt(2025, 7, 1), fim: dt(2025, 7, 31) }).itens).toHaveLength(0);
  });
});

describe("R59 - acertos avulsos, quitação e saldo por pessoa", () => {
  it("'Emprestei' sem lançamento só cria o acerto; com lançamento gera a saída na conta", () => {
    const ds = base();
    const sem = criarAcertoAvulso(ds, { pessoaId: 1, tipo: "A_RECEBER", valor: 50 }, ctx);
    if (!sem.ok) throw new Error(sem.erro);
    expect(sem.despesa).toBeNull();
    expect(sem.ds.despesas).toBe(ds.despesas);
    expect(sem.acerto).toMatchObject({ tipo: "A_RECEBER", valor: 50, descricao: "Empréstimo", despesaId: null, grupoRateio: null, quitado: false });

    const com = criarAcertoAvulso(ds, { pessoaId: 1, tipo: "A_RECEBER", valor: 50, lancamento: { conta: "111" } }, ctx);
    if (!com.ok) throw new Error(com.erro);
    expect(com.despesa).toMatchObject({ tipo: "DEBITO", categoria: "Empréstimos", pago: true, valor: 50, natureza: "NORMAL" });
    expect(com.acerto.despesaId).toBe(com.despesa?.id);
    expect(com.ds.contas[0].saldo).toBe(850);
  });

  it("'Devo' com lançamento gera a entrada; valor inválido e pessoa inexistente são recusados", () => {
    const ds = base();
    const r = criarAcertoAvulso(ds, { pessoaId: 2, tipo: "A_PAGAR", valor: 20, descricao: "Emprestado", lancamento: { conta: "111" } }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.despesa).toMatchObject({ tipo: "CREDITO", categoria: "Empréstimos" });
    expect(r.ds.contas[0].saldo).toBe(920);
    expect(criarAcertoAvulso(ds, { pessoaId: 2, tipo: "A_PAGAR", valor: 0 }, ctx)).toMatchObject({ ok: false });
    expect(criarAcertoAvulso(ds, { pessoaId: 9, tipo: "A_PAGAR", valor: 5 }, ctx)).toMatchObject({ ok: false });
    expect(criarAcertoAvulso(ds, { pessoaId: 1, tipo: "A_PAGAR", valor: 5, lancamento: { conta: "999" } }, ctx)).toMatchObject({ ok: false, erro: "Conta não encontrada." });
  });

  it("receber o acerto: marca quitado; com lançamento cria receita 'Reembolso' paga e o saldo bate", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }, { pessoaId: 2 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const a = r.acertos[0];
    const sem = quitarAcerto(r.ds, { acertoId: a.id, data: dt(2025, 6, 20) }, ctx);
    if (!sem.ok) throw new Error(sem.erro);
    expect(sem.despesa).toBeNull();
    expect(sem.ds.acertos.find((x) => x.id === a.id)).toMatchObject({ quitado: true, quitadoEm: dt(2025, 6, 20) });
    expect(sem.ds.contas[0].saldo).toBe(900);

    const com = quitarAcerto(r.ds, { acertoId: a.id, lancamento: { conta: "111" } }, ctx);
    if (!com.ok) throw new Error(com.erro);
    expect(com.despesa).toMatchObject({ tipo: "CREDITO", categoria: "Reembolso", pago: true, valor: 33.33, natureza: "NORMAL" });
    expect(com.ds.contas[0].saldo).toBe(933.33);
    expect(quitarAcerto(com.ds, { acertoId: a.id }, ctx)).toMatchObject({ ok: false, erro: "Este acerto já está quitado." });
    // o lançamento original continua com o valor total
    expect(com.ds.despesas.find((d) => d.id === 200)?.valor).toBe(100);
  });

  it("pagar o que devo gera despesa 'Acertos'", () => {
    const ds = base();
    const r = criarAcertoAvulso(ds, { pessoaId: 1, tipo: "A_PAGAR", valor: 40 }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const q = quitarAcerto(r.ds, { acertoId: r.acerto.id, lancamento: { conta: "111" } }, ctx);
    if (!q.ok) throw new Error(q.erro);
    expect(q.despesa).toMatchObject({ tipo: "DEBITO", categoria: "Acertos", valor: 40 });
    expect(q.ds.contas[0].saldo).toBe(860);
  });

  it("saldo líquido por pessoa = a receber − a pagar (só pendentes)", () => {
    const acertos: Acerto[] = [
      { id: 1, pessoaId: 1, tipo: "A_RECEBER", valor: 33.33, descricao: "x", data: 1, quitado: false },
      { id: 2, pessoaId: 1, tipo: "A_PAGAR", valor: 10, descricao: "y", data: 2, quitado: false },
      { id: 3, pessoaId: 1, tipo: "A_RECEBER", valor: 99, descricao: "z", data: 3, quitado: true, quitadoEm: 4 },
      { id: 4, pessoaId: 2, tipo: "A_PAGAR", valor: 5, descricao: "w", data: 5, quitado: false },
    ];
    const s = saldosPorPessoa([{ id: 1, nome: "Ana" }, { id: 2, nome: "Bia" }], acertos);
    expect(s[0]).toMatchObject({ aReceber: 3333, aPagar: 1000, liquido: 2333 });
    expect(s[0].pendentes).toHaveLength(2);
    expect(s[0].quitados).toHaveLength(1);
    expect(s[1]).toMatchObject({ aReceber: 0, aPagar: 500, liquido: -500 });
  });

  it("texto de cobrança: itens por data, total e PIX só se informado", () => {
    const acertos: Acerto[] = [
      { id: 1, pessoaId: 1, tipo: "A_RECEBER", valor: 33.33, descricao: "Pizza", data: dt(2025, 6, 10), quitado: false },
      { id: 2, pessoaId: 1, tipo: "A_RECEBER", valor: 20, descricao: "Uber", data: dt(2025, 6, 2), quitado: false },
      { id: 3, pessoaId: 1, tipo: "A_RECEBER", valor: 50, descricao: "Pago", data: dt(2025, 6, 3), quitado: true },
      { id: 4, pessoaId: 1, tipo: "A_PAGAR", valor: 7, descricao: "Eu devo", data: dt(2025, 6, 4), quitado: false },
    ];
    expect(textoCobranca("Ana", acertos, "ana@pix.com")).toBe(
      "Olá, Ana! Segue o que ficou pendente entre nós:\n- 02/06 Uber: R$ 20,00\n- 10/06 Pizza: R$ 33,33\n\nTotal: R$ 53,33\nChave PIX: ana@pix.com",
    );
    expect(textoCobranca("Ana", acertos, "  ")).toBe(
      "Olá, Ana! Segue o que ficou pendente entre nós:\n- 02/06 Uber: R$ 20,00\n- 10/06 Pizza: R$ 33,33\n\nTotal: R$ 53,33",
    );
    expect(textoCobranca("Ana", [acertos[2], acertos[3]])).toBeNull();
  });

  it("pessoas: nome único, exclusão só sem acertos; excluir acerto", () => {
    const ds = base();
    expect(salvarPessoa(ds, { nome: " ana " }, undefined, ctx)).toMatchObject({ ok: false });
    expect(salvarPessoa(ds, { nome: " " }, undefined, ctx)).toMatchObject({ ok: false });
    const n = salvarPessoa(ds, { nome: "Carlos", contato: " 11 9999 " }, undefined, ctx);
    if (!n.ok) throw new Error(n.erro);
    expect(n.pessoa).toMatchObject({ nome: "Carlos", contato: "11 9999" });
    const a = criarAcertoAvulso(n.ds, { pessoaId: n.pessoa.id, tipo: "A_RECEBER", valor: 10 }, ctx);
    if (!a.ok) throw new Error(a.erro);
    expect(excluirPessoa(a.ds, n.pessoa.id)).toMatchObject({ ok: false });
    const e = excluirAcerto(a.ds, a.acerto.id);
    if (!e.ok) throw new Error(e.erro);
    expect(excluirPessoa(e.ds, n.pessoa.id)).toMatchObject({ ok: true });
  });
});

describe("R59 - backup", () => {
  it("ida e volta de pessoas e acertos (inclusive órfão) e import de backup antigo sem as coleções", () => {
    const ds = base();
    const r = dividirDespesa(ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 1 }, { pessoaId: 2 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    const volta = importarBackup(JSON.stringify(exportarBackup(r.ds)), ctx.agora);
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.pessoas).toEqual(r.ds.pessoas);
    expect(volta.dataset.acertos).toEqual(r.ds.acertos);

    const antigo = importarBackup(JSON.stringify({ versaoBackup: 2, despesas: [] }), ctx.agora);
    if (!antigo.ok) throw new Error(antigo.erro);
    expect(antigo.dataset.pessoas).toEqual([]);
    expect(antigo.dataset.acertos).toEqual([]);

    const parcial = importarBackup(JSON.stringify({ pessoas: [{ id: 3, nome: "Z" }], acertos: [{ id: 4, pessoaId: 3, valor: 12.345, descricao: "d", despesaId: 777 }] }), ctx.agora);
    if (!parcial.ok) throw new Error(parcial.erro);
    expect(parcial.dataset.acertos[0]).toMatchObject({ tipo: "A_RECEBER", valor: 12.35, quitado: false, despesaId: 777, quitadoEm: null, grupoRateio: null });
  });
});

// ---------------------------------------------------------------- R68 (mesmos cenários de RateiosTest.kt)
function baseVinculo(): Dataset {
  const ds = dataset({
    contas: [conta({ id: 1, conta: "111" })],
    despesas: [
      desp({ id: 100, descricao: "Saldo inicial", valor: 1000, tipo: "CREDITO", natureza: "SALDO_INICIAL", conta: "111" }),
      desp({ id: 200, descricao: "Pizza", valor: 100, data: dt(2025, 6, 10), conta: "111", categoria: "Alimentação" }),
      desp({ id: 201, descricao: "Cinema", valor: 60, data: dt(2025, 6, 12), conta: "111", categoria: "Lazer" }),
      desp({ id: 202, descricao: "Transferência", valor: 30, natureza: "TRANSFERENCIA", conta: "111" }),
      desp({ id: 203, descricao: "Pagamento de fatura", valor: 30, natureza: "PAGAMENTO_FATURA", conta: "111" }),
      desp({ id: 204, descricao: "Aporte meta", valor: 30, natureza: "APORTE_META", conta: "111" }),
      desp({ id: 205, descricao: "Resgate meta", valor: 30, tipo: "CREDITO", natureza: "RESGATE_META", conta: "111" }),
      desp({ id: 206, descricao: "Ajuste", valor: 30, natureza: "AJUSTE", conta: "111" }),
      desp({ id: 300, descricao: "Salário", valor: 500, tipo: "CREDITO", data: dt(2025, 6, 5), conta: "111", categoria: "Salário" }),
    ],
    pessoas: [
      { id: 1, nome: "Ana", contato: null },
      { id: 2, nome: "Bia", contato: null },
    ],
  });
  return recalcularTudo(ds);
}

describe("R68 - vincular lançamentos existentes", () => {
  it("vincula 1 lançamento: só cria acerto, saldo e lançamentos intactos", () => {
    const ds = baseVinculo();
    const r = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.ds.despesas).toBe(ds.despesas);
    expect(r.ds.contas).toBe(ds.contas);
    expect(r.ds.cartoes).toBe(ds.cartoes);
    expect(r.acertos).toHaveLength(1);
    expect(r.acertos[0]).toMatchObject({ pessoaId: 1, tipo: "A_RECEBER", valor: 100, descricao: "Pizza", data: dt(2025, 6, 10), despesaId: 200, quitado: false, quitadoEm: null, grupoRateio: "vinculo:200" });
    expect(saldosPorPessoa(r.ds.pessoas, r.ds.acertos)[0]).toMatchObject({ aReceber: 10000, liquido: 10000 });
  });

  it("vincula vários de uma vez, com valor parcial e A_PAGAR", () => {
    const ds = baseVinculo();
    const r = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 40.5 }, { despesaId: 201 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.acertos.map((a) => a.valor)).toEqual([40.5, 60]);
    expect(new Set(r.acertos.map((a) => a.id)).size).toBe(2);
    // A_PAGAR não tem o teto dos A_RECEBER (mas é ≤ valor do lançamento)
    expect(vincularLancamentos(r.ds, { pessoaId: 2, tipo: "A_PAGAR", itens: [{ despesaId: 200 }] }, ctx)).toMatchObject({ ok: true });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 100.01 }] }, ctx)).toMatchObject({ ok: false });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 0 }] }, ctx)).toMatchObject({ ok: false });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_PAGAR", itens: [{ despesaId: 200, valor: 100.01 }] }, ctx)).toMatchObject({ ok: false });
  });

  it("teto: A_RECEBER de outras pessoas e divisões contam; indicador nunca fica negativo", () => {
    const ds = baseVinculo();
    const a = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 70 }] }, ctx);
    if (!a.ok) throw new Error(a.erro);
    expect(comprometidoAReceber(a.ds.acertos, 200)).toBe(7000);
    expect(vincularLancamentos(a.ds, { pessoaId: 2, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 30.01 }] }, ctx)).toMatchObject({ ok: false });
    const cabe = vincularLancamentos(a.ds, { pessoaId: 2, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 30 }] }, ctx);
    if (!cabe.ok) throw new Error(cabe.erro);
    expect(gastoLiquido(ds.despesas[1], cabe.ds.acertos)).toBe(0);
    // divisão que passaria do total com o vínculo existente é recusada; a que cabe segue funcionando
    expect(dividirDespesa(a.ds, { despesaId: 200, modo: "IGUAL", participantes: [{ pessoaId: 2 }] }, ctx)).toMatchObject({ ok: false });
    expect(dividirDespesa(a.ds, { despesaId: 200, modo: "VALOR", participantes: [{ pessoaId: 2, valor: 3000 }] }, ctx)).toMatchObject({ ok: true });
  });

  it("recusa naturezas internas, lançamento/pessoa inexistente e itens repetidos", () => {
    const ds = baseVinculo();
    for (const id of [100, 202, 203, 204, 205, 206]) {
      expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_PAGAR", itens: [{ despesaId: id }] }, ctx)).toMatchObject({ ok: false });
    }
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 999 }] }, ctx)).toEqual({ ok: false, erro: "Lançamento não encontrado." });
    expect(vincularLancamentos(ds, { pessoaId: 9, tipo: "A_RECEBER", itens: [{ despesaId: 200 }] }, ctx)).toEqual({ ok: false, erro: "Pessoa não encontrada." });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [] }, ctx)).toMatchObject({ ok: false });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200 }, { despesaId: 200 }] }, ctx)).toMatchObject({ ok: false });
  });

  it("duplicidade: mesma pessoa e mesmo tipo é recusada; tipo ou pessoa diferente passa", () => {
    const ds = baseVinculo();
    const a = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 10 }] }, ctx);
    if (!a.ok) throw new Error(a.erro);
    const dup = vincularLancamentos(a.ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 5 }] }, ctx);
    expect(dup).toMatchObject({ ok: false });
    if (!dup.ok) expect(dup.erro).toContain("já vinculado");
    expect(vincularLancamentos(a.ds, { pessoaId: 1, tipo: "A_PAGAR", itens: [{ despesaId: 200, valor: 5 }] }, ctx)).toMatchObject({ ok: true });
    expect(vincularLancamentos(a.ds, { pessoaId: 2, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 5 }] }, ctx)).toMatchObject({ ok: true });
  });

  it("atomicidade: um item inválido não cria nenhum acerto", () => {
    const ds = baseVinculo();
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200 }, { despesaId: 202 }, { despesaId: 201 }] }, ctx)).toMatchObject({ ok: false });
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200 }, { despesaId: 201, valor: 999 }] }, ctx)).toMatchObject({ ok: false });
    expect(ds.acertos).toEqual([]);
  });

  it("receita (CREDITO): só A_PAGAR", () => {
    const ds = baseVinculo();
    expect(vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 300 }] }, ctx)).toMatchObject({ ok: false });
    const r = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_PAGAR", itens: [{ despesaId: 300, valor: 250 }] }, ctx);
    if (!r.ok) throw new Error(r.erro);
    expect(r.acertos[0]).toMatchObject({ tipo: "A_PAGAR", valor: 250, despesaId: 300 });
    expect(saldosPorPessoa(r.ds.pessoas, r.ds.acertos)[0]).toMatchObject({ aPagar: 25000, liquido: -25000 });
  });

  it("desfazer a divisão NÃO apaga vínculos (grupo vinculo:); desvincular é excluirAcerto", () => {
    const ds = baseVinculo();
    const v = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 20 }] }, ctx);
    if (!v.ok) throw new Error(v.erro);
    const d = dividirDespesa(v.ds, { despesaId: 200, modo: "VALOR", participantes: [{ pessoaId: 2, valor: 3000 }] }, ctx);
    if (!d.ok) throw new Error(d.erro);
    const x = desfazerRateio(d.ds, 200);
    if (!x.ok) throw new Error(x.erro);
    expect(x.removidos).toBe(1);
    expect(x.ds.acertos).toEqual(v.ds.acertos);
    expect(excluirAcerto(x.ds, v.acertos[0].id)).toMatchObject({ ok: true });
  });

  it("indicador gastoLiquido/resumoRateios reflete o vínculo", () => {
    const ds = baseVinculo();
    const v = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200, valor: 35.5 }] }, ctx);
    if (!v.ok) throw new Error(v.erro);
    expect(gastoLiquido(ds.despesas[1], v.ds.acertos)).toBe(6450);
    const resumo = resumoRateios(v.ds.despesas, v.ds.acertos);
    expect(resumo).toMatchObject({ totalRateado: 10000, aReceberTerceiros: 3550, liquido: 6450 });
    expect(resumo.itens[0]).toMatchObject({ despesaId: 200, pendente: 3550, orfao: false });
  });

  it("backup: ida e volta com vínculo e backup antigo sem acertos continua importando", () => {
    const ds = baseVinculo();
    const v = vincularLancamentos(ds, { pessoaId: 1, tipo: "A_RECEBER", itens: [{ despesaId: 200 }] }, ctx);
    if (!v.ok) throw new Error(v.erro);
    const volta = importarBackup(JSON.stringify(exportarBackup(v.ds)), ctx.agora);
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.acertos).toEqual(v.ds.acertos);
    const antigo = importarBackup(JSON.stringify({ versaoBackup: 2, despesas: [] }), ctx.agora);
    if (!antigo.ok) throw new Error(antigo.erro);
    expect(antigo.dataset.acertos).toEqual([]);
  });
});
