import { describe, expect, it } from "vitest";
import { detectarAssinaturas } from "./analises";
import { conta, dataset, dt } from "./fixtures";
import {
  adiantarOcorrenciaFixa,
  criarDespesaFixa,
  editarDespesaFixa,
  pausarRecorrencia,
  processarDespesasFixas,
  recorrenciaPausada,
  retomarRecorrencia,
  type Ctx,
} from "./operations";
import type { Dataset } from "./types";

const ctx = (agora: number): Ctx => ({ agora, uuid: () => "u", rng: (() => { let s = 0.2; return () => (s = (s + 0.13) % 1); })() });

function ok<T extends { ok: boolean }>(r: T): Extract<T, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<T, { ok: true }>;
}

const d0 = (a: number, m: number, d: number) => new Date(a, m - 1, d).getTime();
const regra = { descricao: "Aluguel", valor: 1000, conta: "111", categoria: "Moradia", pic: "", tipo: "DEBITO" as const, diaVencimento: 5 };
const base = (): Dataset => dataset({ contas: [conta({ id: 1, conta: "111" })] });

/** Regra criada em jan/2025 e já processada até jan. */
function comRegra(): { ds: Dataset; id: number } {
  const c = ok(criarDespesaFixa(base(), regra, ctx(dt(2025, 1, 10))));
  const p = ok(processarDespesasFixas(c.ds, ctx(dt(2025, 1, 10))));
  expect(p.criados).toHaveLength(1);
  return { ds: p.ds, id: c.fixa.id };
}

describe("R47 pausar recorrência", () => {
  it("pausada não lança nada nem altera ultimaDataLancamento; lançamentos antigos ficam intactos", () => {
    const { ds, id } = comRegra();
    const pausado = ok(pausarRecorrencia(ds, id, null, ctx(dt(2025, 1, 11)))).ds;
    const r = ok(processarDespesasFixas(pausado, ctx(dt(2025, 4, 10))));
    expect(r.criados).toHaveLength(0);
    expect(r.ds.despesas).toHaveLength(1);
    expect(r.ds.despesasFixas[0].ultimaDataLancamento).toBe(d0(2025, 1, 5));
    expect(r.ds.despesasFixas[0].pausada).toBe(true);
  });

  it("retomar não recupera os meses pausados; só ocorrências futuras", () => {
    const { ds, id } = comRegra();
    const pausado = ok(pausarRecorrencia(ds, id, null, ctx(dt(2025, 1, 11)))).ds;
    const retomado = ok(retomarRecorrencia(pausado, id, ctx(dt(2025, 4, 10)))).ds;
    expect(retomado.despesasFixas[0]).toMatchObject({ pausada: false, pausadaAte: null, ultimaDataLancamento: d0(2025, 4, 5) });
    expect(ok(processarDespesasFixas(retomado, ctx(dt(2025, 4, 11)))).criados).toHaveLength(0);
    const maio = ok(processarDespesasFixas(retomado, ctx(dt(2025, 5, 6))));
    expect(maio.criados.map((d) => d.mes)).toEqual([5]);
  });

  it("retomar antes do dia de vencimento usa a ocorrência do mês anterior", () => {
    const { ds, id } = comRegra();
    const pausado = ok(pausarRecorrencia(ds, id, null, ctx(dt(2025, 1, 11)))).ds;
    const retomado = ok(retomarRecorrencia(pausado, id, ctx(dt(2025, 4, 2)))).ds;
    expect(retomado.despesasFixas[0].ultimaDataLancamento).toBe(d0(2025, 3, 5));
    expect(ok(processarDespesasFixas(retomado, ctx(dt(2025, 4, 6)))).criados.map((d) => d.mes)).toEqual([4]);
  });

  it("pausadaAte vencido retoma sozinho, sem catch-up", () => {
    const { ds, id } = comRegra();
    const pausado = ok(pausarRecorrencia(ds, id, dt(2025, 3, 20), ctx(dt(2025, 1, 11)))).ds;
    expect(recorrenciaPausada(pausado.despesasFixas[0], dt(2025, 3, 1))).toBe(true);
    expect(ok(processarDespesasFixas(pausado, ctx(dt(2025, 3, 10)))).criados).toHaveLength(0);
    const depois = ok(processarDespesasFixas(pausado, ctx(dt(2025, 4, 10))));
    expect(depois.criados).toHaveLength(0);
    expect(depois.ds.despesasFixas[0]).toMatchObject({ pausada: false, pausadaAte: null, ultimaDataLancamento: d0(2025, 4, 5) });
    expect(ok(processarDespesasFixas(depois.ds, ctx(dt(2025, 5, 6)))).criados.map((d) => d.mes)).toEqual([5]);
  });

  it("regra antiga, sem os campos, funciona como não pausada", () => {
    const { ds, id } = comRegra();
    const f = { ...ds.despesasFixas[0] };
    delete (f as { pausada?: boolean }).pausada;
    delete (f as { pausadaAte?: number | null }).pausadaAte;
    const antigo = { ...ds, despesasFixas: [f] };
    expect(recorrenciaPausada(f, dt(2025, 2, 1))).toBe(false);
    expect(ok(processarDespesasFixas(antigo, ctx(dt(2025, 3, 10)))).criados.map((d) => d.mes)).toEqual([2, 3]);
    expect(retomarRecorrencia(antigo, id, ctx(dt(2025, 3, 10))).ok).toBe(false);
  });

  it("valida data de retomada e existência", () => {
    const { ds, id } = comRegra();
    expect(pausarRecorrencia(ds, id, dt(2025, 1, 1), ctx(dt(2025, 1, 11))).ok).toBe(false);
    expect(pausarRecorrencia(ds, 999, null, ctx(dt(2025, 1, 11))).ok).toBe(false);
    expect(retomarRecorrencia(ds, 999, ctx(dt(2025, 1, 11))).ok).toBe(false);
  });

  it("antecipar ocorrência de regra pausada é recusado; editar continua permitido", () => {
    const { ds, id } = comRegra();
    const pausado = ok(pausarRecorrencia(ds, id, null, ctx(dt(2025, 1, 11)))).ds;
    const r = adiantarOcorrenciaFixa(pausado, { fixaId: id, mes: 3, ano: 2025, valorPago: 1000 }, ctx(dt(2025, 1, 12)));
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.erro).toMatch(/pausada/i);
    const ed = ok(editarDespesaFixa(pausado, id, { valor: 1200 }));
    expect(ed.ds.despesasFixas[0]).toMatchObject({ valor: 1200, pausada: true });
    const livre = ok(retomarRecorrencia(pausado, id, ctx(dt(2025, 1, 12)))).ds;
    expect(adiantarOcorrenciaFixa(livre, { fixaId: id, mes: 3, ano: 2025, valorPago: 1000 }, ctx(dt(2025, 1, 12))).ok).toBe(true);
  });

  it("previsão de assinaturas ignora regras pausadas e mantém as demais", () => {
    const { ds, id } = comRegra();
    const agora = dt(2025, 2, 10);
    const antes = detectarAssinaturas(ds, agora).filter((a) => a.origem === "FIXA");
    expect(antes).toHaveLength(1);
    const pausado = ok(pausarRecorrencia(ds, id, null, ctx(agora))).ds;
    expect(detectarAssinaturas(pausado, agora).filter((a) => a.origem === "FIXA")).toHaveLength(0);
    expect(detectarAssinaturas(ds, agora).filter((a) => a.origem === "FIXA")).toEqual(antes);
  });
});
