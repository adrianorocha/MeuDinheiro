import { describe, expect, it } from "vitest";
import { exportarBackup, importarBackup } from "../store/backup";
import { conta, dataset, desp, dt } from "./fixtures";
import {
  adicionarLancamento,
  anteciparPagamento,
  duplicarLancamento,
  editarLancamento,
  excluirLancamento,
  repetirLancamento,
  restaurarDaLixeira,
  type Ctx,
} from "./operations";
import { filtroPadrao, gerarRelatorio, totaisPorTag } from "./relatorios";
import { chaveTag, normalizarTags, resumoProjeto, rotuloTag, tagsUsadas, temTag, validarProjeto } from "./tags";
import type { Projeto } from "./types";

const ctx = (agora: number): Ctx => ({ agora, uuid: () => "u1", rng: (() => { let s = 0.1; return () => (s = (s + 0.137) % 1); })() });
const HOJE = dt(2026, 10, 7);

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

/** MESMOS cenários de app/src/test/.../TagsProjetosTest.kt */
describe("R56 normalização de tags", () => {
  it("limpa, deduplica pela chave, limita a 5 e a 30 caracteres", () => {
    expect(normalizarTags(["  #Viagem ", "viagem", "Reforma  Casa", "ÁRVORE", "a", "b", "c"])).toEqual(["Viagem", "Reforma Casa", "ÁRVORE", "a", "b"]);
    expect(rotuloTag("x".repeat(40))).toHaveLength(30);
    expect(normalizarTags(["", "#", "   "])).toEqual([]);
    expect(normalizarTags(undefined)).toEqual([]);
  });
  it("chave: minúscula, sem acento, sem #", () => {
    expect(chaveTag("#Café  Bom")).toBe("cafe bom");
    expect(temTag({ tags: ["Árvore"] }, "#ARVORE")).toBe(true);
    expect(temTag({}, "a")).toBe(false);
  });
  it("tags usadas, por uso", () => {
    const u = tagsUsadas([desp({ tags: ["Viagem", "Hotel"] }), desp({ tags: ["viagem"] }), desp()]);
    expect(u.map((t) => [t.chave, t.rotulo, t.usos])).toEqual([["viagem", "Viagem", 2], ["hotel", "Hotel", 1]]);
  });
});

describe("R56 resumo de projeto", () => {
  const despesas = [
    desp({ id: 1, tags: ["Reforma"], valor: 100, categoria: "Casa", data: dt(2025, 3, 10) }),
    desp({ id: 2, tags: ["reforma", "extra"], valor: 50.5, categoria: "Casa", data: dt(2025, 4, 5) }),
    desp({ id: 3, tags: ["REFORMA"], valor: 10, tipo: "CREDITO", cartaoId: 10, categoria: "Casa", data: dt(2025, 4, 6) }), // estorno abate
    desp({ id: 4, tags: ["reforma"], valor: 77, tipo: "CREDITO", categoria: "Casa", data: dt(2025, 4, 7) }), // receita comum: fora
    desp({ id: 5, tags: ["reforma"], valor: 33, natureza: "TRANSFERENCIA", data: dt(2025, 4, 8) }), // interno: fora
    desp({ id: 6, tags: ["viagem"], valor: 999 }),
    desp({ id: 7, valor: 5 }),
  ];
  const proj = (over: Partial<Projeto> = {}): Projeto => ({ id: 1, nome: "Reforma", tag: "reforma", ativo: true, ...over });

  it("soma NORMAL com a tag; estorno de cartão abate; por categoria e por mês", () => {
    const r = resumoProjeto(proj({ orcamento: 200 }), despesas);
    expect(r.gasto).toBe(140.5);
    expect(r.quantidade).toBe(3);
    expect(r.porCategoria).toEqual([{ nome: "Casa", total: 140.5 }]);
    expect(r.porMes).toEqual([{ mes: "2025-03", total: 100 }, { mes: "2025-04", total: 40.5 }]);
    expect(r.itens.map((d) => d.id)).toEqual([3, 2, 1]);
    expect(r.percentual).toBeCloseTo(0.7025, 4);
    expect(r.status).toBe("NO_ORCAMENTO");
  });
  it("status do orçamento", () => {
    expect(resumoProjeto(proj({ orcamento: 150 }), despesas).status).toBe("ATENCAO");
    expect(resumoProjeto(proj({ orcamento: 100 }), despesas).status).toBe("ESTOUROU");
    expect(resumoProjeto(proj({ orcamento: null }), despesas).status).toBe("SEM_ORCAMENTO");
    expect(resumoProjeto(proj(), despesas).percentual).toBeNull();
  });
  it("período do projeto restringe os lançamentos", () => {
    const r = resumoProjeto(proj({ inicio: dt(2025, 4, 1), fim: dt(2025, 4, 30) }), despesas);
    expect(r.gasto).toBe(40.5);
    expect(r.quantidade).toBe(2);
  });
  it("validação", () => {
    expect(validarProjeto({ nome: " ", tag: "a" })).toMatch(/nome/);
    expect(validarProjeto({ nome: "A", tag: "#" })).toMatch(/tag/);
    expect(validarProjeto({ nome: "A", tag: "Reforma" }, [{ id: 9, tag: "reforma" }])).toMatch(/já existe/i);
    expect(validarProjeto({ nome: "A", tag: "Reforma" }, [{ id: 9, tag: "reforma" }], 9)).toBeNull();
    expect(validarProjeto({ nome: "A", tag: "x", inicio: 10, fim: 5 })).toMatch(/final/);
  });
});

describe("R56 tags preservadas nas operações", () => {
  const base = () => dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 1, tipo: "CREDITO", valor: 5000, pago: true })] });

  it("compra parcelada: todas as parcelas herdam as tags (normalizadas)", () => {
    const r = ok(adicionarLancamento(base(), { descricao: "TV", valor: 900, data: dt(2026, 11, 10), categoria: "Casa", conta: "111", tipo: "DEBITO", parcelas: 3, tags: ["#Casa Nova", "casa nova", "Eletro"] }, ctx(HOJE)));
    expect(r.criados).toHaveLength(3);
    expect(r.criados.every((d) => d.tags?.join("|") === "Casa Nova|Eletro")).toBe(true);
  });

  it("sem tags informadas, o lançamento nasce sem o campo (dados como antes)", () => {
    const r = ok(adicionarLancamento(base(), { descricao: "Pão", valor: 5, data: HOJE, categoria: "Padaria", conta: "111", tipo: "DEBITO" }, ctx(HOJE)));
    expect("tags" in r.criados[0]).toBe(false);
  });

  it("duplicar e repetir preservam as tags", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 7, tags: ["Viagem"], pago: false })] });
    const d = ok(duplicarLancamento(ds, 7, ctx(HOJE)));
    expect(d.criado.tags).toEqual(["Viagem"]);
    const rep = ok(repetirLancamento(ds, 7, { n: 2, intervalo: 1, unidade: "MESES" }, ctx(HOJE)));
    expect(rep.criados.map((c) => c.tags)).toEqual([["Viagem"], ["Viagem"]]);
  });

  it("antecipar (inclusive o pagamento parcial, que divide o lançamento) preserva as tags", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 1, tipo: "CREDITO", valor: 5000, pago: true }), desp({ id: 2, valor: 200, pago: false, data: dt(2026, 12, 10), tags: ["Divida"] })] });
    const r = ok(anteciparPagamento(ds, { ids: [2], valorPago: 80 }, ctx(HOJE)));
    const doisLados = r.ds.despesas.filter((d) => d.id !== 1);
    expect(doisLados).toHaveLength(2);
    expect(doisLados.every((d) => d.tags?.[0] === "Divida")).toBe(true);
  });

  it("editar: substitui, remove (lista vazia) e mantém quando ausente", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 3, tags: ["a", "b"] })] });
    const trocado = ok(editarLancamento(ds, 3, { tags: ["#Nova", "nova", "Outra"] }, ctx(HOJE)));
    expect(trocado.ds.despesas[0].tags).toEqual(["Nova", "Outra"]);
    const mantido = ok(editarLancamento(ds, 3, { descricao: "x" }, ctx(HOJE)));
    expect(mantido.ds.despesas[0].tags).toEqual(["a", "b"]);
    const removido = ok(editarLancamento(ds, 3, { tags: [] }, ctx(HOJE)));
    expect("tags" in removido.ds.despesas[0]).toBe(false);
  });

  it("lixeira: excluir e restaurar mantém as tags", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 3, tags: ["a", "b"] })] });
    const ex = ok(excluirLancamento(ds, 3, undefined, ctx(HOJE)));
    expect(ex.ds.despesas).toHaveLength(0);
    const re = ok(restaurarDaLixeira(ex.ds, ex.ds.lixeira[0].id, ctx(HOJE)));
    expect(re.restaurados[0].tags).toEqual(["a", "b"]);
  });
});

describe("R56 backup", () => {
  const velho = {
    contas: [{ id: 3, saldo: 0, banco: "X", pic: "", agencia: "1", conta: "10", titular: "T" }],
    despesas: [{ id: 7, descricao: "Antiga", valor: 10, data: dt(2024, 3, 10), categoria: "A", conta: "10", pic: "", tipo: "DEBITO", mes: 3, ano: 2024, pago: true, natureza: "NORMAL" }],
  };

  it("backup antigo (sem tags, regras, projetos, configuracoes) importa sem falha", () => {
    const r = importarBackup(JSON.stringify({ ...velho, versaoBackup: 2 }), dt(2026, 1, 1));
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.despesas[0].tags).toBeUndefined();
    expect(r.dataset.regrasCategoria).toEqual([]);
    expect(r.dataset.projetos).toEqual([]);
    expect(r.configuracoes).toBeUndefined();
  });

  it("campos desconhecidos nunca quebram o import", () => {
    const r = importarBackup(JSON.stringify({ ...velho, colecaoFutura: [{ a: 1 }], despesas: [{ ...velho.despesas[0], campoFuturo: 1 }] }), dt(2026, 1, 1));
    expect(r.ok).toBe(true);
  });

  it("round-trip preserva tags, regras, projetos e configurações; tags inválidas são normalizadas", () => {
    const bruto = {
      ...velho,
      despesas: [{ ...velho.despesas[0], tags: ["#Viagem", "viagem", "Hotel"] }],
      regrasCategoria: [{ id: 5, padrao: "uber", tipoMatch: "COMECA_COM", categoria: "Transporte", conta: null, tipo: "DEBITO", ativa: true, prioridade: 2, criadaEm: 10 }],
      projetos: [{ id: 6, nome: "Viagem", tag: "viagem", orcamento: 1000, inicio: null, fim: null, ativo: true, cor: null }],
      configuracoes: { limiteDiarioAteReceita: true, atualizadoEm: 123 },
    };
    const r = importarBackup(JSON.stringify(bruto), dt(2026, 1, 1));
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.despesas[0].tags).toEqual(["Viagem", "Hotel"]);
    expect(r.dataset.regrasCategoria[0]).toMatchObject({ id: 5, tipoMatch: "COMECA_COM", tipo: "DEBITO", prioridade: 2 });
    expect(r.dataset.projetos[0]).toMatchObject({ id: 6, tag: "viagem", orcamento: 1000 });
    expect(r.configuracoes).toEqual({ limiteDiarioAteReceita: true, atualizadoEm: 123 });
    const volta = importarBackup(JSON.stringify(exportarBackup(r.dataset, r.configuracoes)), dt(2026, 1, 1));
    if (!volta.ok) throw new Error(volta.erro);
    expect(volta.dataset.despesas[0].tags).toEqual(["Viagem", "Hotel"]);
    expect(volta.dataset.regrasCategoria).toEqual(r.dataset.regrasCategoria);
    expect(volta.dataset.projetos).toEqual(r.dataset.projetos);
    expect(volta.configuracoes).toEqual(r.configuracoes);
  });
});

describe("R56 relatórios", () => {
  const despesas = [
    desp({ id: 1, valor: 100, tags: ["viagem", "hotel"], data: dt(2025, 3, 10) }),
    desp({ id: 2, valor: 50, tags: ["Viagem"], data: dt(2025, 3, 11) }),
    desp({ id: 3, valor: 30, data: dt(2025, 3, 12) }),
  ];
  const ds = dataset({ despesas });
  const f = filtroPadrao(dt(2025, 3, 1), dt(2025, 3, 31));

  it("sem filtro de tag o resultado é o mesmo de antes", () => {
    const sem = gerarRelatorio(ds, f);
    const comVazio = gerarRelatorio(ds, { ...f, tags: [] });
    expect(sem.total).toBe(180);
    expect(comVazio).toEqual(sem);
  });
  it("filtra por qualquer das tags (chave ou rótulo)", () => {
    expect(gerarRelatorio(ds, { ...f, tags: ["#VIAGEM"] }).total).toBe(150);
    expect(gerarRelatorio(ds, { ...f, tags: ["hotel"] }).total).toBe(100);
  });
  it("agrupamento por tag: multi-tag entra em cada uma; sem tag à parte", () => {
    const g = totaisPorTag(gerarRelatorio(ds, f));
    expect(g.map((x) => [x.rotulo, x.total, x.quantidade])).toEqual([["viagem", 150, 2], ["hotel", 100, 1], ["Sem tag", 30, 1]]);
  });
});
