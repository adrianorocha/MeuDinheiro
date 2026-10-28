import { describe, expect, it } from "vitest";
import { criarDatasetDemo } from "../demo";
import { dt } from "../finance/fixtures";
import { gerarOfxExemplo } from "./exemplo";
import { casar, lancamentosDoDestino } from "./matching";
import { parseOfx } from "./ofx";
import { aplicarConciliacao, montarPlano } from "./plano";
import type { Destino } from "./tipos";

describe("arquivo de exemplo gerado", () => {
  const agora = dt(2026, 10, 7);
  const ds = criarDatasetDemo(agora);
  const conta = ds.contas[0].conta;
  const texto = gerarOfxExemplo(ds, conta, agora)!;
  const destino: Destino = { tipo: "CONTA", conta };

  it("gera um OFX legível com a conta do app e saldo", () => {
    const a = parseOfx(texto);
    expect(a.acctId).toBe(conta);
    expect(a.transacoes.length).toBeGreaterThanOrEqual(6);
    expect(a.saldoFinal).toBeDefined();
  });
  it("produz automáticos, diferença e linhas só no extrato; reimportar vira duplicado", () => {
    const a = parseOfx(texto);
    const r = casar(a.transacoes, lancamentosDoDestino(ds, destino), { janelaDias: 3 });
    const classes = r.itens.map((i) => i.classe);
    expect(classes.filter((c) => c === "AUTOMATICO").length).toBeGreaterThanOrEqual(3);
    expect(r.itens.some((i) => i.tipo === "DIFERENCA")).toBe(true);
    expect(classes.filter((c) => c === "SO_NO_EXTRATO").length).toBeGreaterThanOrEqual(2);
    const ap = aplicarConciliacao(ds, montarPlano(r, ds, destino, { sugeridos: true, criar: true }), agora);
    if (!ap.ok) throw new Error(ap.erro);
    const r2 = casar(a.transacoes, lancamentosDoDestino(ap.ds, destino), { janelaDias: 3 });
    expect(r2.itens.every((i) => i.classe === "DUPLICADO")).toBe(true);
  });
  it("sem lançamentos recentes não gera exemplo", () => {
    expect(gerarOfxExemplo({ ...ds, despesas: [] }, conta, agora)).toBeNull();
  });
});
