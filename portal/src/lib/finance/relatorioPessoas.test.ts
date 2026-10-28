import { describe, expect, it } from "vitest";
import { cartao, desp, dt } from "./fixtures";
import {
  corIndiceDaPessoa,
  dataBR,
  iniciaisDoNome,
  montarRelatorioPessoas,
  nomeArquivoPessoas,
  periodoPorAtalho,
  saldoTextoDe,
  type OpcoesRelatorioPessoas,
} from "./relatorioPessoas";
import type { Acerto, Pessoa } from "./types";

// Mesmos cenários e números de RelatorioPessoasTest.kt (app).
const agora = dt(2026, 9, 30, 15);
const pessoas: Pessoa[] = [
  { id: 1, nome: "Ana", contato: "11 99999-0000" },
  { id: 2, nome: "Bruno", contato: null },
  { id: 3, nome: "Carla" },
];
const despesas = [
  desp({ id: 100, descricao: "Jantar", valor: 120, data: dt(2026, 9, 5) }),
  desp({ id: 101, descricao: "Mercado", valor: 80, data: dt(2026, 9, 12) }),
];
function ac(id: number, pessoaId: number, tipo: Acerto["tipo"], valor: number, descricao: string, data: number, extra: Partial<Acerto> = {}): Acerto {
  return { id, pessoaId, tipo, valor, descricao, data, despesaId: null, quitado: false, quitadoEm: null, grupoRateio: null, ...extra };
}
const acertos: Acerto[] = [
  ac(1, 1, "A_RECEBER", 40, "Jantar", dt(2026, 9, 5), { despesaId: 100, grupoRateio: "rateio:100" }),
  ac(2, 2, "A_RECEBER", 40, "Jantar", dt(2026, 9, 5), { despesaId: 100, grupoRateio: "rateio:100", quitado: true, quitadoEm: dt(2026, 9, 20) }),
  ac(3, 1, "A_PAGAR", 15.5, "Café", dt(2026, 9, 10)),
  ac(4, 2, "A_PAGAR", 100, "Empréstimo", dt(2026, 8, 20)),
  ac(5, 1, "A_RECEBER", 20, "Mercado", dt(2026, 9, 12), { despesaId: 101, grupoRateio: "vinculo:101" }),
  ac(6, 2, "A_RECEBER", 10, "Antigo", dt(2026, 9, 15), { despesaId: 999, grupoRateio: "rateio:999" }),
];
const base: OpcoesRelatorioPessoas = { pessoaIds: [], periodo: null, situacao: "TODOS", incluirDetalhes: true, agrupar: "POR_PESSOA", agora };
const gerar = (o: Partial<OpcoesRelatorioPessoas> = {}) => montarRelatorioPessoas(pessoas, acertos, despesas, { ...base, ...o });
const setembro = periodoPorAtalho("ESTE_MES", agora);

describe("R69 - relatório de pessoas (modelo puro, centavos)", () => {
  it("todas as pessoas, sem filtro: subtotais, sentido e pessoa sem acertos omitida", () => {
    const r = gerar();
    expect(r.vazio).toBe(false);
    expect(r.pessoas.map((p) => p.nome)).toEqual(["Ana", "Bruno"]);
    const [ana, bruno] = r.pessoas;
    expect([ana.aReceber, ana.aPagar, ana.liquido, ana.nAcertos]).toEqual([6000, 1550, 4450, 3]);
    expect(ana.saldoTexto).toBe("a acertar R$ 44,50");
    expect(ana.sentido).toBe("TE_DEVE");
    expect(ana.contato).toBe("11 99999-0000");
    expect([bruno.aReceber, bruno.aPagar, bruno.liquido, bruno.quitadoAReceber, bruno.nAcertos]).toEqual([1000, 10000, -9000, 4000, 3]);
    expect(bruno.saldoTexto).toBe("a compensar R$ 90,00");
    expect(bruno.contato).toBeNull();
    expect(r.resumo).toEqual({
      aReceberPendente: 7000,
      aPagarPendente: 11550,
      saldoLiquido: -4550,
      quitadoAReceber: 4000,
      quitadoAPagar: 0,
      nAcertos: 6,
      nPessoasComSaldo: 2,
      nPessoas: 2,
    });
    expect(r.periodoTexto).toBe("Todo o período");
    expect(r.emitidoEm).toBe("30/09/2026 15:00");
  });

  it("subtotais das pessoas fecham com o resumo e com as linhas, em qualquer filtro", () => {
    for (const o of [{}, { periodo: setembro }, { situacao: "PENDENTES" as const }, { situacao: "QUITADOS" as const }, { pessoaIds: [2] }]) {
      const r = gerar(o);
      const soma = (f: (p: (typeof r.pessoas)[number]) => number) => r.pessoas.reduce((s, p) => s + f(p), 0);
      expect(soma((p) => p.aReceber)).toBe(r.resumo.aReceberPendente);
      expect(soma((p) => p.aPagar)).toBe(r.resumo.aPagarPendente);
      expect(soma((p) => p.liquido)).toBe(r.resumo.saldoLiquido);
      expect(soma((p) => p.nAcertos)).toBe(r.resumo.nAcertos);
      for (const p of r.pessoas) {
        const l = p.linhas;
        const s = (tipo: string, st: string) => l.filter((x) => x.tipo === tipo && x.status === st).reduce((t, x) => t + x.centavos, 0);
        expect(s("A_RECEBER", "PENDENTE")).toBe(p.aReceber);
        expect(s("A_PAGAR", "PENDENTE")).toBe(p.aPagar);
        expect(s("A_RECEBER", "QUITADO")).toBe(p.quitadoAReceber);
        expect(s("A_PAGAR", "QUITADO")).toBe(p.quitadoAPagar);
      }
    }
  });

  it("período (data do acerto, inclusivo) e atalhos", () => {
    expect(setembro && [dataBR(setembro.inicio), dataBR(setembro.fim)]).toEqual(["01/09/2026", "30/09/2026"]);
    const m = periodoPorAtalho("MES_PASSADO", agora);
    expect(m && [dataBR(m.inicio), dataBR(m.fim)]).toEqual(["01/08/2026", "31/08/2026"]);
    const t = periodoPorAtalho("TRES_MESES", agora);
    expect(t && [dataBR(t.inicio), dataBR(t.fim)]).toEqual(["01/07/2026", "30/09/2026"]);
    const a = periodoPorAtalho("ANO", agora);
    expect(a && [dataBR(a.inicio), dataBR(a.fim)]).toEqual(["01/01/2026", "31/12/2026"]);
    expect(periodoPorAtalho("TUDO", agora)).toBeNull();

    const r = gerar({ periodo: setembro });
    expect(r.periodoTexto).toBe("01/09/2026 a 30/09/2026");
    expect(r.resumo).toMatchObject({ aReceberPendente: 7000, aPagarPendente: 1550, saldoLiquido: 5450, nAcertos: 5, quitadoAReceber: 4000 });
    const bruno = r.pessoas[1];
    expect([bruno.aPagar, bruno.liquido, bruno.nAcertos]).toEqual([0, 1000, 2]);
    // bordas inclusivas
    const exato = gerar({ periodo: { inicio: dt(2026, 9, 5), fim: dt(2026, 9, 5) } });
    expect(exato.resumo.nAcertos).toBe(2);
  });

  it("situação: pendentes, quitados e todos", () => {
    const p = gerar({ situacao: "PENDENTES" });
    expect(p.resumo).toMatchObject({ aReceberPendente: 7000, aPagarPendente: 11550, quitadoAReceber: 0, nAcertos: 5 });
    const q = gerar({ situacao: "QUITADOS" });
    expect(q.pessoas.map((x) => x.nome)).toEqual(["Bruno"]);
    expect(q.resumo).toEqual({
      aReceberPendente: 0,
      aPagarPendente: 0,
      saldoLiquido: 0,
      quitadoAReceber: 4000,
      quitadoAPagar: 0,
      nAcertos: 1,
      nPessoasComSaldo: 0,
      nPessoas: 1,
    });
    expect(q.pessoas[0].saldoTexto).toBe("contas em dia");
    expect(q.pessoas[0].sentido).toBe("QUITES");
    expect(q.filtros).toContain("Situação: Somente quitados");
  });

  it("escolher pessoas; pessoa sem acertos no filtro não aparece; nada a mostrar = vazio", () => {
    const r = gerar({ pessoaIds: [2] });
    expect(r.pessoas.map((p) => p.nome)).toEqual(["Bruno"]);
    expect(r.filtros[0]).toBe("Pessoas: Bruno");
    expect(gerar().filtros[0]).toBe("Pessoas: todas");
    const v = gerar({ pessoaIds: [3] });
    expect(v.vazio).toBe(true);
    expect(v.pessoas).toEqual([]);
    expect(v.resumo.nAcertos).toBe(0);
    expect(gerar({ periodo: { inicio: dt(2020, 1, 1), fim: dt(2020, 1, 31) } }).vazio).toBe(true);
    expect(montarRelatorioPessoas([], [], [], base).vazio).toBe(true);
  });

  it("linhas detalhadas: ordenadas por data, origem, status, rateio e órfão", () => {
    const [ana, bruno] = gerar().pessoas;
    expect(ana.linhas.map((l) => l.acertoId)).toEqual([1, 3, 5]);
    expect(ana.linhas[0]).toMatchObject({
      dataTexto: "05/09/2026",
      origem: "lançamento de 05/09 — Jantar",
      origemTipo: "LANCAMENTO",
      tipoTexto: "A receber",
      centavos: 4000,
      status: "PENDENTE",
      statusTexto: "Pendente",
    });
    expect(ana.linhas[0].rateio).toEqual({ totalDespesa: 12000, cota: 4000, texto: "Rateio: despesa R$ 120,00 · cota R$ 40,00" });
    expect(ana.linhas[1]).toMatchObject({ origem: "avulso", origemTipo: "AVULSO", tipoTexto: "A pagar", centavos: 1550, rateio: null });
    expect(ana.linhas[2].rateio?.texto).toBe("Vinculado: despesa R$ 80,00 · cota R$ 20,00");
    expect(bruno.linhas.map((l) => l.acertoId)).toEqual([4, 2, 6]);
    expect(bruno.linhas[1]).toMatchObject({ status: "QUITADO", statusTexto: "Quitado em 20/09" });
    expect(bruno.linhas[2]).toMatchObject({ origem: "lançamento removido", origemTipo: "REMOVIDO", rateio: null });
  });

  it("agrupar cronológico: lista única por data; sem detalhes, nada de linhas", () => {
    const c = gerar({ agrupar: "CRONOLOGICO" });
    expect(c.cronologico.map((l) => l.acertoId)).toEqual([4, 1, 2, 3, 5, 6]);
    expect(c.cronologico[0].pessoaNome).toBe("Bruno");
    expect(gerar().cronologico).toEqual([]);
    const s = gerar({ incluirDetalhes: false, agrupar: "CRONOLOGICO" });
    expect(s.cronologico).toEqual([]);
    expect(s.pessoas.every((p) => p.linhas.length === 0)).toBe(true);
    expect(s.resumo.nAcertos).toBe(6);
    expect(s.filtros).toContain("Detalhes: apenas totais");
  });

  it("bloco opcional Rateios: quanto você realmente gastou (total − a receber de terceiros)", () => {
    expect(gerar().rateios).toBeNull();
    const r = gerar({ incluirRateios: true }).rateios;
    expect(r?.itens.map((i) => i.despesaId)).toEqual([999, 101, 100]);
    expect(r).toMatchObject({ totalRateado: 20000, aReceberTerceiros: 10000, liquido: 10000 });
    expect(r?.itens.find((i) => i.despesaId === 100)).toMatchObject({ total: 12000, aReceber: 8000, liquido: 4000, pendente: 4000 });
    expect(r?.itens.find((i) => i.despesaId === 999)?.orfao).toBe(true);
    // filtrando por Ana: só lançamentos em que ela participa, com o a receber de TODOS (o líquido nunca é parcial)
    const a = gerar({ pessoaIds: [1], incluirRateios: true }).rateios;
    expect(a?.itens.map((i) => i.despesaId)).toEqual([101, 100]);
    expect(a).toMatchObject({ totalRateado: 20000, aReceberTerceiros: 10000, liquido: 10000 });
    // sem despesas divididas no filtro: bloco ausente
    expect(gerar({ pessoaIds: [1], situacao: "PENDENTES", periodo: { inicio: dt(2026, 9, 14), fim: dt(2026, 9, 30) }, incluirRateios: true }).rateios).toBeNull();
  });

  it("avatar estável: iniciais e cor derivadas do nome", () => {
    expect(iniciaisDoNome("Ana")).toBe("A");
    expect(iniciaisDoNome("  ana maria souza ")).toBe("AS");
    expect(iniciaisDoNome("")).toBe("?");
    expect(corIndiceDaPessoa("Ana")).toBe(5);
    expect(corIndiceDaPessoa("Bruno")).toBe(7);
    expect(corIndiceDaPessoa("Ana")).toBe(corIndiceDaPessoa("Ana"));
    const r = gerar();
    expect(r.pessoas.map((p) => [p.iniciais, p.corIndice])).toEqual([["A", 5], ["B", 7]]);
  });

  it("textos de saldo e nome do arquivo", () => {
    expect(saldoTextoDe(123456)).toEqual({ sentido: "TE_DEVE", texto: "a acertar R$ 1.234,56" });
    expect(saldoTextoDe(-5)).toEqual({ sentido: "VOCE_DEVE", texto: "a compensar R$ 0,05" });
    expect(saldoTextoDe(0).texto).toBe("contas em dia");
    expect(nomeArquivoPessoas(agora, "pdf")).toBe("pessoas_2026-09-30.pdf");
    expect(nomeArquivoPessoas(agora, "png", 2)).toBe("pessoas_2026-09-30-2.png");
  });
});

describe("vencimento da fatura em destaque e lançamentos adiados", () => {
  const cartoes = [cartao({ id: 10, diaFechamento: 25, diaVencimento: 5 })];
  const dsp = [desp({ id: 300, descricao: "TV (1/2)", valor: 100, cartaoId: 10, data: dt(2026, 9, 10) }), desp({ id: 301, descricao: "Pix", valor: 30, data: dt(2026, 9, 11) })];
  const ac2 = [
    ac(20, 1, "A_RECEBER", 50, "TV", dt(2026, 9, 10), { despesaId: 300, grupoRateio: "rateio:300" }),
    ac(21, 1, "A_RECEBER", 15, "Pix", dt(2026, 9, 11), { despesaId: 301, grupoRateio: "rateio:301" }),
    ac(22, 1, "A_PAGAR", 5, "Café", dt(2026, 9, 12)),
  ];
  const g = (o: Partial<OpcoesRelatorioPessoas> = {}) => montarRelatorioPessoas(pessoas, ac2, dsp, { ...base, cartoes, ...o });

  it("só A receber pendente no crédito ganha vencimento; a pessoa recebe o destaque e o aviso", () => {
    const r = g();
    const [tv, pix, cafe] = r.pessoas[0].linhas;
    expect(tv.vencimento?.texto).toBe("Fatura do cartão vence em 05/10/2026");
    expect(tv.vencimento?.vencido).toBe(false);
    expect(pix.vencimento).toBeNull();
    expect(cafe.vencimento).toBeNull();
    expect(r.pessoas[0].proximoVencimento).toMatchObject({ texto: "Fatura do cartão vence em 05/10/2026", centavos: 5000 });
  });

  it("sem cartões informados ou depois do vencimento", () => {
    expect(g({ cartoes: undefined }).pessoas[0].proximoVencimento).toBeNull();
    const tarde = g({ agora: dt(2026, 10, 20) }).pessoas[0].proximoVencimento;
    expect(tarde?.vencido).toBe(true);
    expect(tarde?.texto).toBe("Fatura do cartão venceu em 05/10/2026");
  });

  it("acertos adiados saem do relatório e dos totais, sem tocar nos dados", () => {
    const r = g({ excluirAcertoIds: [20] });
    expect(r.pessoas[0].linhas.map((l) => l.acertoId)).toEqual([21, 22]);
    expect(r.pessoas[0].aReceber).toBe(1500);
    expect(r.pessoas[0].proximoVencimento).toBeNull();
    expect(r.adiados).toBe(1);
    expect(r.resumo.nAcertos).toBe(2);
    expect(g({ excluirAcertoIds: [20, 21, 22] }).vazio).toBe(true);
    expect(ac2).toHaveLength(3);
  });
});
