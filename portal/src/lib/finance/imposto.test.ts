import { describe, expect, it } from "vitest";
import { kpisPeriodo } from "./calc";
import { alterarConfig, normalizarGeral } from "./configuracoes";
import { dataset, desp, dt } from "./fixtures";
import {
  CONFIG_IR_PADRAO,
  LIMITE_EDUCACAO_PADRAO_CENTAVOS,
  classificarDeducao,
  classificarRendimento,
  documentoIR,
  normalizarConfigIR,
  pareceNaoDedutivel,
  relatorioIR,
  saldoContaEm,
} from "./imposto";
import { csvDeDocumento } from "./relatorios";
import type { Despesa } from "./types";

// Mesmos cenários e números de app/src/test/java/com/meudinheiro/domain/ImpostoRendaTest.kt.
const AGORA = dt(2026, 2, 10);
let n = 100;
const d = (id: number | null, data: number, descricao: string, categoria: string, valor: number, over: Partial<Despesa> = {}) =>
  desp({ id: id ?? n++, data, descricao, categoria, valor, ...over });
const rec = (data: number, descricao: string, categoria: string, valor: number, over: Partial<Despesa> = {}) =>
  d(null, data, descricao, categoria, valor, { tipo: "CREDITO", ...over });

function cenario() {
  const despesas: Despesa[] = [
    rec(dt(2024, 12, 1), "Saldo inicial", "Saldo", 1000, { natureza: "SALDO_INICIAL" }),
    rec(dt(2024, 12, 5), "Salario dez/24", "Salário", 4000),
    rec(dt(2025, 1, 5), "Salario jan", "Salário", 5000),
    rec(dt(2025, 2, 5), "Salario fev", "Salário", 5000),
    rec(dt(2025, 12, 20), "13o", "13º Salário", 2500),
    rec(dt(2025, 3, 1), "Reembolso viagem", "Reembolso", 300),
    rec(dt(2025, 6, 30), "CDB", "Rendimentos", 120.55),
    rec(dt(2025, 4, 10), "Freela site", "Extra", 800),
    rec(dt(2025, 5, 2), "Pix de amigo", "Outros", 50),
    rec(dt(2025, 12, 30), "Salario previsto", "Salário", 700, { pago: false }),
    rec(dt(2026, 1, 5), "Salario jan/26", "Salário", 5100),
    // despesas
    d(null, dt(2025, 3, 10), "Dentista", "Saúde", 450),
    d(null, dt(2025, 3, 12), "Farmácia Pague Menos", "Saúde", 80),
    d(null, dt(2025, 5, 1), "Plano", "Plano de Saúde", 600),
    d(null, dt(2025, 2, 10), "Faculdade", "Educação", 3000),
    d(null, dt(2025, 8, 10), "Escola", "Educação", 2000),
    d(null, dt(2025, 7, 1), "PGBL", "Previdência", 700),
    d(null, dt(2025, 12, 15), "Dentista pendente", "Saúde", 200, { pago: false }),
    d(null, dt(2025, 4, 1), "Mercado", "Supermercado", 500),
    d(null, dt(2025, 9, 9), "Exame", "Saúde", 150, { cartaoId: 7 }),
    d(null, dt(2025, 12, 10), "Transferência", "Transferência", 1000, { natureza: "TRANSFERENCIA" }),
    rec(dt(2025, 12, 10), "Transferência", "Transferência", 1000, { natureza: "TRANSFERENCIA", conta: "222" }),
    d(null, dt(2026, 1, 10), "Despesa de 2026", "Supermercado", 300),
  ];
  return dataset({
    despesas,
    contas: [
      { id: 1, saldo: 0, banco: "Nubank", pic: "", agencia: "1", conta: "111", titular: "T" },
      { id: 2, saldo: 0, banco: "Itaú", pic: "", agencia: "1", conta: "222", titular: "T" },
    ],
    investimentos: [{ id: 1, nome: "CDB Banco X", tipo: "Renda Fixa", valorInvestido: 1000, valorAtual: 1100.5 }],
    metas: [{ id: 1, nome: "Viagem", valorObjetivo: 5000, valorGuardado: 750, icone: "", dataAlvo: null }],
    dividas: [
      { id: 1, nome: "Financiamento", tipo: "FINANCIAMENTO", credor: "Banco Y", saldoDevedor: 20000, ativa: true },
      { id: 2, nome: "Quitada", tipo: "OUTRA", saldoDevedor: 0, ativa: false },
    ],
  });
}

describe("R61 classificação padrão de rendimentos", () => {
  it("salário é tributável; 13º e PLR são exclusivos; reembolso/empréstimo/resgate/aporte ficam fora", () => {
    expect(classificarRendimento("Salário")).toBe("TRIBUTAVEL");
    expect(classificarRendimento("Férias")).toBe("TRIBUTAVEL");
    expect(classificarRendimento("Pró-labore")).toBe("TRIBUTAVEL");
    expect(classificarRendimento("13º Salário")).toBe("EXCLUSIVO");
    expect(classificarRendimento("Décimo terceiro")).toBe("EXCLUSIVO");
    expect(classificarRendimento("PLR")).toBe("EXCLUSIVO");
    expect(classificarRendimento("Reembolso")).toBe("FORA");
    expect(classificarRendimento("Empréstimo recebido")).toBe("FORA");
    expect(classificarRendimento("Resgate de meta")).toBe("FORA");
    expect(classificarRendimento("Aporte")).toBe("FORA");
    expect(classificarRendimento("Rendimentos")).toBe("INVESTIMENTO");
    expect(classificarRendimento("Dividendos")).toBe("INVESTIMENTO");
    expect(classificarRendimento("Herança")).toBe("ISENTO");
    expect(classificarRendimento("Doação recebida")).toBe("ISENTO");
    expect(classificarRendimento("Outros")).toBe("REVISAR");
  });
  it("sem pista na categoria usa a descrição; a escolha do usuário vence tudo", () => {
    expect(classificarRendimento("Extra", "Freela site")).toBe("TRIBUTAVEL");
    expect(classificarRendimento("Outros", "Pix de amigo")).toBe("REVISAR");
    expect(classificarRendimento("Salário", "", { salario: "ISENTO" })).toBe("ISENTO");
    expect(classificarRendimento("Outros", "", { outros: "FORA" })).toBe("FORA");
  });
});

describe("R61 classificação de deduções", () => {
  it("por nome, com sobrescrita e 'NAO'", () => {
    expect(classificarDeducao("Saúde")).toBe("SAUDE");
    expect(classificarDeducao("Plano de Saúde")).toBe("SAUDE");
    expect(classificarDeducao("Dentista")).toBe("SAUDE");
    expect(classificarDeducao("Educação")).toBe("EDUCACAO");
    expect(classificarDeducao("Faculdade")).toBe("EDUCACAO");
    expect(classificarDeducao("Pensão alimentícia")).toBe("PENSAO");
    expect(classificarDeducao("PGBL")).toBe("PREVIDENCIA");
    expect(classificarDeducao("Doações")).toBe("DOACAO");
    expect(classificarDeducao("Supermercado")).toBeNull();
    expect(classificarDeducao("Lazer", { lazer: "SAUDE" })).toBe("SAUDE");
    expect(classificarDeducao("Saúde", { saude: "NAO" })).toBeNull();
  });
  it("remédio e óculos são marcados como provável não dedutível", () => {
    expect(pareceNaoDedutivel("Farmácia Pague Menos")).toBe(true);
    expect(pareceNaoDedutivel("Óculos novos")).toBe(true);
    expect(pareceNaoDedutivel("Dentista")).toBe(false);
  });
});

describe("R61 relatório do ano", () => {
  const ds = cenario();
  const r = relatorioIR(ds, 2025, CONFIG_IR_PADRAO, AGORA);

  it("rendimentos realizados por classe; previsto e outros anos ficam de fora", () => {
    const v = (c: string) => r.rendimentos.porClasse.find((t) => t.classe === c)!;
    expect(v("TRIBUTAVEL").totalCentavos).toBe(1_080_000); // 5000 + 5000 + 800 (freela pela descrição)
    expect(v("TRIBUTAVEL").quantidade).toBe(3);
    expect(v("EXCLUSIVO").totalCentavos).toBe(250_000);
    expect(v("INVESTIMENTO").totalCentavos).toBe(12_055);
    // só o reembolso: a transferência (natureza TRANSFERENCIA) nunca entra nos rendimentos
    expect(v("FORA").totalCentavos).toBe(30_000);
  });
  it("REVISAR, previsto e totais", () => {
    expect(r.rendimentos.porClasse.find((t) => t.classe === "REVISAR")!.totalCentavos).toBe(5_000);
    expect(r.rendimentos.previstoCentavos).toBe(70_000);
    expect(r.rendimentos.totalRealizadoCentavos).toBe(1_080_000 + 250_000 + 12_055 + 30_000 + 5_000);
  });
  it("totais fecham com os KPIs (receitas realizadas) e com a soma das classes", () => {
    const k = kpisPeriodo(ds.despesas, new Date(2025, 0, 1).getTime(), new Date(2025, 11, 31, 23, 59, 59, 999).getTime());
    // KPIs contam só natureza NORMAL: as transferências ficam fora dos dois lados
    expect(r.checagem.receitasRealizadasCentavos).toBe(Math.round(k.receitasRealizadas * 100));
    expect(r.checagem.confere).toBe(true);
    expect(r.checagem.deducoesConferem).toBe(true);
  });
  it("deduções: saúde sem limite, educação limitada por pessoa, PGBL até 12% do tributável", () => {
    const t = (x: string) => r.deducoes.porTipo.find((p) => p.tipo === x)!;
    expect(t("SAUDE").totalCentavos).toBe(45_000 + 60_000 + 15_000); // dentista + plano + exame (cartão pago); farmácia fora
    expect(t("SAUDE").limiteCentavos).toBeNull();
    expect(t("EDUCACAO").totalCentavos).toBe(500_000);
    expect(t("EDUCACAO").limiteCentavos).toBe(LIMITE_EDUCACAO_PADRAO_CENTAVOS);
    expect(t("EDUCACAO").deduzivelCentavos).toBe(356_150);
    expect(t("EDUCACAO").excessoCentavos).toBe(143_850);
    expect(r.deducoes.limitePgblCentavos).toBe(129_600); // 12% de 10.800,00
    expect(t("PREVIDENCIA").deduzivelCentavos).toBe(70_000);
    expect(r.deducoes.totalDeduzivelCentavos).toBe(120_000 + 356_150 + 70_000);
    expect(r.indicativoBaseCentavos).toBe(1_080_000 - (120_000 + 356_150 + 70_000));
  });
  it("não dedutíveis, pendentes e comprovantes", () => {
    expect(r.deducoes.naoDedutiveis.map((i) => i.descricao)).toEqual(["Farmácia Pague Menos"]);
    expect(r.deducoes.naoDedutiveisCentavos).toBe(8_000);
    expect(r.deducoes.pendentesCentavos).toBe(20_000);
    expect(r.deducoes.quantidade).toBe(6); // dentista, plano, exame, faculdade, escola, pgbl
    expect(r.deducoes.semComprovante).toBe(6); // sem o campo `anexos`, nenhum tem comprovante
  });
  it("reconhece `anexos` quando o campo existir", () => {
    const comAnexo = cenario();
    (comAnexo.despesas.find((x) => x.descricao === "Dentista") as unknown as { anexos: unknown[] }).anexos = [{ id: "a" }];
    const r2 = relatorioIR(comAnexo, 2025, CONFIG_IR_PADRAO, AGORA);
    expect(r2.deducoes.semComprovante).toBe(5);
    expect(r2.deducoes.grupos.flatMap((g) => g.itens).find((i) => i.descricao === "Dentista")!.comprovante).toBe(true);
  });
  it("saldo das contas em 31/12 (lançamentos de 2026 e transferências entram só até a data)", () => {
    // conta 111: +1000 +4000 +5000 +5000 +2500 +300 +120,55 +800 +50 -450 -80 -600 -3000 -2000 -700 -500 -1000 (cartão e 2026 fora)
    expect(r.bens.contas.find((c) => c.detalhe === "conta 111")!.centavos).toBe(1_044_055);
    expect(r.bens.contas.find((c) => c.detalhe === "conta 222")!.centavos).toBe(100_000);
    expect(r.bens.totalContasCentavos).toBe(1_044_055 + 100_000);
    expect(saldoContaEm(ds.despesas, "111", new Date(2025, 11, 31, 23, 59, 59, 999).getTime())).toBe(1_044_055);
    expect(saldoContaEm(ds.despesas, "111", new Date(2026, 0, 31, 23, 59, 59, 999).getTime())).toBe(1_044_055 - 30_000 + 510_000);
  });
  it("investimentos, metas e dívidas (ativas) com aviso de saldo atual fora de ano-1", () => {
    expect(r.bens.totalInvestimentosCentavos).toBe(110_050);
    expect(r.bens.totalMetasCentavos).toBe(75_000);
    expect(r.bens.dividas.map((x) => x.nome)).toEqual(["Financiamento"]);
    expect(r.bens.totalDividasCentavos).toBe(2_000_000);
    expect(r.bens.avisoSaldoAtual).toBeNull(); // 2025 = ano corrente (2026) - 1
    const r24 = relatorioIR(ds, 2024, CONFIG_IR_PADRAO, AGORA);
    expect(r24.bens.avisoSaldoAtual).toContain("ATUAL");
  });
});

describe("R61 configuração do usuário", () => {
  const ds = cenario();
  it("classe escolhida pelo usuário muda os totais e a checagem continua fechando", () => {
    const cfg = normalizarConfigIR({ rendimentos: { "Outros": "ISENTO", reembolso: "TRIBUTAVEL" } });
    const r = relatorioIR(ds, 2025, cfg, AGORA);
    expect(r.rendimentos.porClasse.find((t) => t.classe === "ISENTO")!.totalCentavos).toBe(5_000);
    expect(r.rendimentos.porClasse.find((t) => t.classe === "TRIBUTAVEL")!.totalCentavos).toBe(1_080_000 + 30_000);
    expect(r.checagem.confere).toBe(true);
  });
  it("limite de educação e pessoas configuráveis; categoria marcada manualmente entra", () => {
    const cfg = normalizarConfigIR({ limiteEducacaoCentavos: 200_000, pessoasEducacao: 2, deducoes: { Supermercado: "DOACAO", Saúde: "NAO" } });
    const r = relatorioIR(ds, 2025, cfg, AGORA);
    const t = (x: string) => r.deducoes.porTipo.find((p) => p.tipo === x)!;
    expect(t("EDUCACAO").limiteCentavos).toBe(400_000);
    expect(t("EDUCACAO").deduzivelCentavos).toBe(400_000);
    expect(t("DOACAO").totalCentavos).toBe(50_000);
    expect(t("SAUDE").totalCentavos).toBe(60_000); // só "Plano de Saúde"; "Saúde" desligada
  });
  it("PGBL acima de 12% é limitado", () => {
    const base = cenario();
    base.despesas.push(d(null, dt(2025, 9, 1), "PGBL extra", "Previdência", 2000));
    const r = relatorioIR(base, 2025, CONFIG_IR_PADRAO, AGORA);
    const p = r.deducoes.porTipo.find((x) => x.tipo === "PREVIDENCIA")!;
    expect(p.totalCentavos).toBe(270_000);
    expect(p.deduzivelCentavos).toBe(129_600);
    expect(p.excessoCentavos).toBe(140_400);
  });
  it("valores inválidos voltam ao padrão", () => {
    const c = normalizarConfigIR({ limiteEducacaoCentavos: -5, pessoasEducacao: 0, rendimentos: { x: "LIXO" }, deducoes: { y: "LIXO" } });
    expect(c).toEqual(CONFIG_IR_PADRAO);
    expect(normalizarConfigIR(null)).toEqual(CONFIG_IR_PADRAO);
  });
  it("entra em configuracoes/geral (normalizar/alterar, mais novo vence como o resto)", () => {
    const g = alterarConfig({}, { ir: normalizarConfigIR({ pessoasEducacao: 3 }) }, 1000);
    expect(g.ir?.pessoasEducacao).toBe(3);
    expect(normalizarGeral(JSON.parse(JSON.stringify(g))).ir?.pessoasEducacao).toBe(3);
    expect(normalizarGeral({ painel503020: null }).ir).toBeUndefined();
  });
});

describe("R61 ano sem dados e exportação", () => {
  it("ano sem dados: zeros, tudo confere, sem quebrar", () => {
    const r = relatorioIR(dataset(), 2019, CONFIG_IR_PADRAO, AGORA);
    expect(r.rendimentos.totalRealizadoCentavos).toBe(0);
    expect(r.deducoes.totalDeduzivelCentavos).toBe(0);
    expect(r.bens.totalContasCentavos).toBe(0);
    expect(r.checagem.confere).toBe(true);
    expect(r.bens.avisoSaldoAtual).toBeNull(); // nada de saldo atual a mostrar
  });
  it("documento tem aviso em destaque, resumo e as listas; CSV com BOM", () => {
    const doc = documentoIR(relatorioIR(cenario(), 2025, CONFIG_IR_PADRAO, AGORA));
    expect(doc.rotuloFiltros).toBe("Aviso e premissas");
    expect(doc.filtros[0]).toContain("NÃO é a declaração oficial");
    expect(doc.linhas.length).toBeGreaterThan(10);
    expect(doc.linhas.some((l) => l[0] === "Deduções candidatas" && l[2] === "Dentista" && l[5] === "Não")).toBe(true);
    const csv = csvDeDocumento(doc);
    expect(csv.startsWith("﻿")).toBe(true);
    expect(csv).toContain("Saldo das contas em 31/12");
  });
});
