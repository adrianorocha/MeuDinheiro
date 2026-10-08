import { describe, expect, it } from "vitest";
import { recalcularTudo } from "../finance/calc";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import { exportarBackup, importarBackup } from "../store/backup";
import type { Dataset, Despesa } from "../finance/types";
import { casar, janelaPadrao, lancamentosDoDestino } from "./matching";
import { aplicarConciliacao, desfazerConciliacao, desfazerLote, montarPlano, acaoConciliar, type Plano } from "./plano";
import { csvRelatorio, resumoConciliacao } from "./relatorio";
import { diferencaSaldo, saldoAppAte } from "./saldo";
import { atribuirFitids } from "./texto-banco";
import type { Destino, TransacaoBanco } from "./tipos";

const T = (data: number, valor: number, descricao: string, fitid?: string): TransacaoBanco => ({ fitid: fitid ?? `f-${data}-${valor}-${descricao}`, data, valor, descricao });
const CONTA: Destino = { tipo: "CONTA", conta: "111" };
const OPC = { janelaDias: 3 };

function base(extra: Despesa[] = []): Dataset {
  return recalcularTudo(
    dataset({
      contas: [conta({ id: 1, conta: "111" })],
      cartoes: [cartao({ id: 10, contaId: 1 }), cartao({ id: 11, nome: "Virtual", contaId: 1, cartaoPrincipalId: 10 })],
      despesas: [desp({ id: 1, tipo: "CREDITO", valor: 1000, natureza: "SALDO_INICIAL", data: dt(2025, 2, 1), descricao: "Saldo inicial" }), ...extra],
    }),
  );
}

function ok<R extends { ok: boolean }>(r: R): Extract<R, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<R, { ok: true }>;
}

describe("R37 matching", () => {
  it("valor exato, mesma data e descrição parecida -> AUTOMATICO", () => {
    const ds = base([desp({ id: 2, descricao: "Supermercado Extra", valor: 123.45, data: dt(2025, 3, 10) })]);
    const r = casar([T(dt(2025, 3, 10), -123.45, "COMPRA NO DEBITO 10/03 SUPERMERCADO EXTRA")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0]).toMatchObject({ classe: "AUTOMATICO", tipo: "EXATO", lancamentoId: 2, dias: 0 });
    expect(r.itens[0].score).toBeGreaterThan(0.95);
  });
  it("data diferente e descrição distinta -> SUGERIDO (score < 0,80)", () => {
    const ds = base([desp({ id: 2, descricao: "Compra", valor: 50, data: dt(2025, 3, 13) })]);
    const r = casar([T(dt(2025, 3, 10), -50, "XPTO LTDA")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0].classe).toBe("SUGERIDO");
    expect(r.itens[0].dias).toBe(3);
  });
  it("janela de datas: 3 dias casa, 4 dias não", () => {
    const ds = base([desp({ id: 2, valor: 10, data: dt(2025, 3, 14) })]);
    expect(casar([T(dt(2025, 3, 11), -10, "x")], lancamentosDoDestino(ds, CONTA), OPC).itens[0].classe).toBe("SUGERIDO");
    const fora = casar([T(dt(2025, 3, 10), -10, "x")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(fora.itens[0].classe).toBe("SO_NO_EXTRATO");
    expect(fora.soNoApp).toEqual([]); // 14/03 está fora de [07/03, 13/03]
    expect(casar([T(dt(2025, 3, 10), -10, "x")], lancamentosDoDestino(ds, CONTA), { janelaDias: 5 }).itens[0].lancamentoId).toBe(2);
    expect(janelaPadrao({ tipo: "CARTAO", cartaoId: 1 })).toBe(5);
  });
  it("DIFERENCA por IOF/tarifa dentro de max(1,00; 2%) e nunca automático", () => {
    const ds = base([desp({ id: 2, descricao: "Loja Exterior", valor: 100, data: dt(2025, 3, 10) })]);
    const r = casar([T(dt(2025, 3, 10), -101.5, "LOJA EXTERIOR")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0]).toMatchObject({ classe: "SUGERIDO", tipo: "DIFERENCA", lancamentoId: 2 });
    const longe = casar([T(dt(2025, 3, 10), -103, "LOJA EXTERIOR")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(longe.itens[0].classe).toBe("SO_NO_EXTRATO"); // 3,00 > max(1; 2,06)
    const pequeno = base([desp({ id: 2, valor: 10, data: dt(2025, 3, 10) })]);
    expect(casar([T(dt(2025, 3, 10), -10.9, "x")], lancamentosDoDestino(pequeno, CONTA), OPC).itens[0].tipo).toBe("DIFERENCA"); // 0,90 ≤ 1,00
    expect(casar([T(dt(2025, 3, 10), -11.1, "x")], lancamentosDoDestino(pequeno, CONTA), OPC).itens[0].classe).toBe("SO_NO_EXTRATO");
  });
  it("direção: entrada do extrato não casa com despesa", () => {
    const ds = base([desp({ id: 2, valor: 10, data: dt(2025, 3, 10) })]);
    expect(casar([T(dt(2025, 3, 10), 10, "x")], lancamentosDoDestino(ds, CONTA), OPC).itens[0].classe).toBe("SO_NO_EXTRATO");
  });
  it("duas linhas idênticas no mesmo dia casam um-para-um com dois lançamentos", () => {
    const ds = base([desp({ id: 2, descricao: "Café", valor: 5, data: dt(2025, 3, 10) }), desp({ id: 3, descricao: "Café", valor: 5, data: dt(2025, 3, 10) })]);
    const tr = atribuirFitids([
      { data: dt(2025, 3, 10), valor: -5, descricao: "CAFE" },
      { data: dt(2025, 3, 10), valor: -5, descricao: "CAFE" },
    ]);
    const r = casar(tr, lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens.map((i) => i.lancamentoId)).toEqual([2, 3]); // determinístico: menor id primeiro
    expect(r.soNoApp).toHaveLength(0);
  });
  it("empate de score com um só da linha: escolhe o menor id e classifica SUGERIDO", () => {
    const ds = base([desp({ id: 7, descricao: "Café", valor: 5, data: dt(2025, 3, 10) }), desp({ id: 4, descricao: "Café", valor: 5, data: dt(2025, 3, 10) })]);
    const r = casar([T(dt(2025, 3, 10), -5, "CAFE")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0]).toMatchObject({ classe: "SUGERIDO", lancamentoId: 4 });
    expect(r.itens[0].alternativas.map((a) => a.lancamentoId)).toEqual([7]);
    expect(r.soNoApp.map((d) => d.id)).toEqual([7]);
  });
  it("mesmo valor: o lançamento mais próximo da data vence", () => {
    const ds = base([desp({ id: 2, descricao: "Pagamento", valor: 80, data: dt(2025, 3, 12) }), desp({ id: 3, descricao: "Pagamento", valor: 80, data: dt(2025, 3, 10) })]);
    const r = casar([T(dt(2025, 3, 10), -80, "PAGAMENTO")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0].lancamentoId).toBe(3);
    expect(r.itens[0].classe).toBe("AUTOMATICO");
  });
  it("conta usa só lançamentos sem cartão; cartão usa o grupo (virtual no extrato do físico)", () => {
    const ds = base([
      desp({ id: 2, descricao: "Compra loja", valor: 60, data: dt(2025, 3, 10), cartaoId: 11, pago: false }),
      desp({ id: 3, descricao: "Compra loja", valor: 60, data: dt(2025, 3, 10) }),
    ]);
    expect(lancamentosDoDestino(ds, CONTA).map((d) => d.id).sort()).toEqual([1, 3]);
    expect(lancamentosDoDestino(ds, { tipo: "CARTAO", cartaoId: 10 }).map((d) => d.id)).toEqual([2]);
    const r = casar([T(dt(2025, 3, 10), -60, "COMPRA LOJA")], lancamentosDoDestino(ds, { tipo: "CARTAO", cartaoId: 10 }), { janelaDias: 5 });
    expect(r.itens[0]).toMatchObject({ classe: "AUTOMATICO", lancamentoId: 2 });
  });
  it("transferência e pagamento de fatura (internos) também conciliam", () => {
    const ds = base([
      desp({ id: 2, descricao: "Transferência para B2", valor: 300, data: dt(2025, 3, 10), natureza: "TRANSFERENCIA" }),
      desp({ id: 3, descricao: "Fatura Cartão A 03/2025", valor: 800, data: dt(2025, 3, 12), natureza: "PAGAMENTO_FATURA" }),
    ]);
    const r = casar([T(dt(2025, 3, 10), -300, "TED ENVIADA B2"), T(dt(2025, 3, 12), -800, "PAGTO FATURA CARTAO A")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens.map((i) => i.lancamentoId)).toEqual([2, 3]);
  });
  it("lançamentos conciliados não são candidatos; fitid repetido -> DUPLICADO", () => {
    const ds = base([desp({ id: 2, valor: 10, data: dt(2025, 3, 10), fitid: "F1", conciliadoEm: 5 }), desp({ id: 3, valor: 10, data: dt(2025, 3, 10) })]);
    const r = casar([T(dt(2025, 3, 10), -10, "x", "F1"), T(dt(2025, 3, 10), -10, "x", "F2")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens.map((i) => i.classe)).toEqual(["DUPLICADO", expect.stringMatching(/AUTOMATICO|SUGERIDO/)]);
    expect(r.itens[1].lancamentoId).toBe(3);
  });
  it("SO_NO_APP só dentro do período do arquivo ± janela", () => {
    const ds = base([desp({ id: 2, valor: 10, data: dt(2025, 3, 10) }), desp({ id: 3, valor: 20, data: dt(2025, 3, 25) }), desp({ id: 4, valor: 30, data: dt(2025, 3, 6) })]);
    const r = casar([T(dt(2025, 3, 10), -10, "x"), T(dt(2025, 3, 12), 5, "y")], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.soNoApp.map((d) => d.id)).toEqual([]); // 25/03 e 06/03 fora de [07/03, 15/03]; id 1 (01/02) fora
    const r2 = casar([T(dt(2025, 3, 10), -10, "x"), T(dt(2025, 3, 12), 5, "y")], lancamentosDoDestino(ds, CONTA), { janelaDias: 5 });
    expect(r2.soNoApp.map((d) => d.id)).toEqual([4]); // 06/03 está em [05/03, 17/03]
  });
  it("é determinístico independentemente da ordem dos lançamentos", () => {
    const ls = [2, 3, 4, 5].map((id) => desp({ id, descricao: "Item", valor: 9, data: dt(2025, 3, 10) }));
    const tr = atribuirFitids([1, 2, 3].map(() => ({ data: dt(2025, 3, 10), valor: -9, descricao: "ITEM" })));
    const a = casar(tr, ls, OPC).itens.map((i) => i.lancamentoId);
    const b = casar(tr, [...ls].reverse(), OPC).itens.map((i) => i.lancamentoId);
    expect(a).toEqual(b);
  });
  it("desempenho: 15 mil linhas × 15 mil lançamentos", () => {
    const ls: Despesa[] = [];
    const tr: TransacaoBanco[] = [];
    for (let i = 0; i < 15000; i++) {
      const dia = dt(2024, 1, 1) + (i % 365) * 86_400_000;
      ls.push(desp({ id: 1000 + i, descricao: `Compra ${i % 50}`, valor: 10 + (i % 997) / 10, data: dia }));
      tr.push({ fitid: `f${i}`, data: dia, valor: -(10 + (i % 997) / 10), descricao: `COMPRA ${i % 50}` });
    }
    const t0 = performance.now();
    const r = casar(tr, ls, OPC);
    const ms = performance.now() - t0;
    expect(r.itens.filter((i) => i.lancamentoId !== undefined).length).toBeGreaterThan(14000);
    expect(ms).toBeLessThan(5000);
  });
});

describe("R38 aplicar, mesclar e desfazer", () => {
  const tr = T(dt(2025, 3, 11), -101.5, "LOJA EXTERIOR", "FX1");

  function cenario() {
    const ds = base([
      desp({ id: 2, descricao: "Loja Exterior", valor: 100, data: dt(2025, 3, 10), pago: false }),
      desp({ id: 3, descricao: "Mercado", valor: 40, data: dt(2025, 3, 12) }),
    ]);
    return ds;
  }

  it("conciliar com mescla de data, valor e pago recalcula o saldo", () => {
    const ds = cenario();
    const r = casar([tr], lancamentosDoDestino(ds, CONTA), OPC);
    const plano: Plano = { destino: CONTA, acoes: [acaoConciliar(r.itens[0], CONTA, 2)] };
    expect(plano.acoes[0]).toMatchObject({ mescla: { data: true, valor: true, descricao: false, pago: true } });
    const ap = ok(aplicarConciliacao(ds, plano, 999));
    const d = ap.ds.despesas.find((x) => x.id === 2)!;
    expect(d).toMatchObject({ valor: 101.5, pago: true, fitid: "FX1", conciliadoEm: 999, descricao: "Loja Exterior" });
    expect(new Date(d.data).getDate()).toBe(11);
    expect(ap.ds.contas[0].saldo).toBe(1000 - 40 - 101.5);
    expect(ap.resumo).toMatchObject({ conciliados: 1, valoresAlterados: 1, datasAlteradas: 1, pagosMarcados: 1 });
  });
  it("mescla independente: sem valor/data/pago só marca o vínculo; descrição do banco opcional", () => {
    const ds = cenario();
    const plano: Plano = { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: tr, lancamentoId: 2, mescla: { data: false, valor: false, descricao: true, pago: false } }] };
    const d = ok(aplicarConciliacao(ds, plano, 1)).ds.despesas.find((x) => x.id === 2)!;
    expect(d).toMatchObject({ valor: 100, pago: false, descricao: "Loja Exterior", conciliadoEm: 1 });
  });
  it("cartão: não marca pago e consome limite do grupo", () => {
    const ds = base([desp({ id: 2, descricao: "Loja", valor: 100, data: dt(2025, 3, 10), cartaoId: 11, pago: false })]);
    const destino: Destino = { tipo: "CARTAO", cartaoId: 10 };
    const t = T(dt(2025, 3, 10), -100, "LOJA", "C1");
    const r = casar([t], lancamentosDoDestino(ds, destino), { janelaDias: 5 });
    const plano = montarPlano(r, ds, destino);
    expect(plano.acoes[0]).toMatchObject({ mescla: { pago: false } });
    const ap = ok(aplicarConciliacao(ds, plano, 1));
    const d = ap.ds.despesas.find((x) => x.id === 2)!;
    expect(d.pago).toBe(false);
    expect(d.cartaoId).toBe(11);
    expect(ap.ds.cartoes.map((c) => c.limiteDisponivel)).toEqual([900, 900]);
  });
  it("criar lançamento: categoria sugerida (R21), autor Importação, conta paga / cartão pendente", () => {
    const ds = base([desp({ id: 2, descricao: "Padaria Central", categoria: "Lanche", valor: 8, data: dt(2025, 2, 10) })]);
    const t = T(dt(2025, 3, 5), -12.3, "COMPRA NO DEBITO 05/03 PADARIA CENTRAL", "N1");
    const r = casar([t], lancamentosDoDestino(ds, CONTA), OPC);
    expect(r.itens[0].classe).toBe("SO_NO_EXTRATO");
    const plano = montarPlano(r, ds, CONTA, { criar: true });
    expect(plano.acoes[0]).toMatchObject({ tipo: "CRIAR", categoria: "Lanche" });
    const ap = ok(aplicarConciliacao(ds, plano, 50));
    const novo = ap.ds.despesas.find((x) => x.fitid === "N1")!;
    expect(novo).toMatchObject({ descricao: "Padaria Central", valor: 12.3, tipo: "DEBITO", pago: true, natureza: "NORMAL", autor: "Importação", conciliadoEm: 50, conta: "111", cartaoId: null });
    expect(ap.ds.contas[0].saldo).toBe(1000 - 8 - 12.3);

    const dsC = base();
    const rc = casar([T(dt(2025, 3, 5), -30, "LOJA Y (2/5)", "N2")], [], { janelaDias: 5 });
    const apc = ok(aplicarConciliacao(dsC, montarPlano(rc, dsC, { tipo: "CARTAO", cartaoId: 10 }, { criar: true }), 7));
    const nc = apc.ds.despesas.find((x) => x.fitid === "N2")!;
    expect(nc).toMatchObject({ cartaoId: 10, pago: false, descricao: "Loja Y (2/5)", categoria: "Outros" });
    expect(apc.ds.despesas.filter((x) => x.fitid === "N2")).toHaveLength(1); // parcela não é expandida
    expect(apc.ds.cartoes[0].limiteDisponivel).toBe(970);
  });
  it("é atômico: um item inválido não altera nada", () => {
    const ds = cenario();
    const plano: Plano = {
      destino: CONTA,
      acoes: [
        { tipo: "CONCILIAR", transacao: tr, lancamentoId: 2, mescla: { data: true, valor: true, descricao: false, pago: true } },
        { tipo: "CONCILIAR", transacao: T(dt(2025, 3, 12), -40, "M", "FX2"), lancamentoId: 999, mescla: { data: true, valor: false, descricao: false, pago: true } },
      ],
    };
    const r = aplicarConciliacao(ds, plano, 1);
    expect(r.ok).toBe(false);
    expect(ds.despesas.find((x) => x.id === 2)!.conciliadoEm).toBeNull();
  });
  it("rejeita lançamento repetido, fitid repetido, já conciliado e direção incompatível", () => {
    const ds = cenario();
    const m = { data: true, valor: false, descricao: false, pago: true };
    expect(aplicarConciliacao(ds, { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), -100, "a", "A"), lancamentoId: 2, mescla: m }, { tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), -100, "b", "B"), lancamentoId: 2, mescla: m }] }, 1).ok).toBe(false);
    expect(aplicarConciliacao(ds, { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), -100, "a", "A"), lancamentoId: 2, mescla: m }, { tipo: "CONCILIAR", transacao: T(dt(2025, 3, 12), -40, "b", "A"), lancamentoId: 3, mescla: m }] }, 1).ok).toBe(false);
    expect(aplicarConciliacao(ds, { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), 100, "a", "A"), lancamentoId: 2, mescla: m }] }, 1).ok).toBe(false);
    const feito = ok(aplicarConciliacao(ds, { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), -100, "a", "A"), lancamentoId: 2, mescla: m }] }, 1)).ds;
    expect(aplicarConciliacao(feito, { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: T(dt(2025, 3, 10), -100, "a", "Z"), lancamentoId: 2, mescla: m }] }, 2).ok).toBe(false);
    expect(aplicarConciliacao(ds, { destino: { tipo: "CONTA", conta: "zzz" }, acoes: [] }, 1).ok).toBe(false);
  });
  it("desfazer o último lote restaura valores e remove criados", () => {
    const ds = cenario();
    const r = casar([tr, T(dt(2025, 3, 20), -9, "NOVO", "NV")], lancamentosDoDestino(ds, CONTA), OPC);
    const plano: Plano = { destino: CONTA, acoes: [acaoConciliar(r.itens[0], CONTA, 2), ...montarPlano(r, ds, CONTA, { criar: true }).acoes.filter((a) => a.tipo === "CRIAR")] };
    const ap = ok(aplicarConciliacao(ds, plano, 1));
    expect(ap.ds.despesas.length).toBe(ds.despesas.length + 1);
    const volta = desfazerLote(ap.ds, ap.inverso);
    expect(volta.despesas).toEqual(ds.despesas);
    expect(volta.contas[0].saldo).toBe(ds.contas[0].saldo);
  });
  it("reimportar o mesmo arquivo: tudo DUPLICADO", () => {
    const ds = cenario();
    const t = [T(dt(2025, 3, 10), -100, "Loja Exterior", "R1"), T(dt(2025, 3, 12), -40, "Mercado", "R2")];
    const r1 = casar(t, lancamentosDoDestino(ds, CONTA), OPC);
    const ap = ok(aplicarConciliacao(ds, montarPlano(r1, ds, CONTA), 5));
    const r2 = casar(t, lancamentosDoDestino(ap.ds, CONTA), OPC);
    expect(r2.itens.map((i) => i.classe)).toEqual(["DUPLICADO", "DUPLICADO"]);
    expect(r2.soNoApp).toHaveLength(0);
  });
  it("desfazer conciliação de um lançamento zera fitid e data de conciliação (mantém valor mesclado)", () => {
    const ds = cenario();
    const plano: Plano = { destino: CONTA, acoes: [{ tipo: "CONCILIAR", transacao: tr, lancamentoId: 2, mescla: { data: false, valor: true, descricao: false, pago: false } }] };
    const ap = ok(aplicarConciliacao(ds, plano, 1)).ds;
    const d = ok(desfazerConciliacao(ap, 2)).ds.despesas.find((x) => x.id === 2)!;
    expect(d).toMatchObject({ fitid: null, conciliadoEm: null, valor: 101.5 });
    expect(desfazerConciliacao(ap, 3).ok).toBe(false);
    expect(desfazerConciliacao(ap, 999).ok).toBe(false);
  });
});

describe("R36 saldo e R39 relatório", () => {
  it("saldoApp até a data considera só pagos com data ≤ d; diferença com saldo final", () => {
    const ds = base([desp({ id: 2, valor: 100, data: dt(2025, 3, 10) }), desp({ id: 3, valor: 50, data: dt(2025, 3, 20) }), desp({ id: 4, valor: 25, data: dt(2025, 3, 5), pago: false })]);
    expect(saldoAppAte(ds, CONTA, dt(2025, 3, 10))).toBe(900);
    expect(saldoAppAte(ds, CONTA, dt(2025, 3, 31))).toBe(850);
    const d = diferencaSaldo(ds, CONTA, { valor: 905.5, data: dt(2025, 3, 10) });
    expect(d).toEqual({ saldoBanco: 905.5, saldoApp: 900, diferenca: 5.5 });
  });
  it("saldo do cartão = fatura em aberto negativa", () => {
    const ds = base([desp({ id: 2, valor: 100, data: dt(2025, 3, 10), cartaoId: 11, pago: false }), desp({ id: 3, valor: 30, tipo: "CREDITO", data: dt(2025, 3, 11), cartaoId: 10, pago: false })]);
    expect(saldoAppAte(ds, { tipo: "CARTAO", cartaoId: 10 }, dt(2025, 3, 31))).toBe(-70);
  });
  it("resumo e CSV do relatório", () => {
    const ds = base([desp({ id: 2, descricao: "Mercado", valor: 40, data: dt(2025, 3, 12) }), desp({ id: 3, descricao: "Só no app", valor: 15, data: dt(2025, 3, 13) })]);
    const t = [T(dt(2025, 3, 12), -40, "MERCADO", "A"), T(dt(2025, 3, 13), 200, "Depósito; \"x\"", "B")];
    const r = casar(t, lancamentosDoDestino(ds, CONTA), OPC);
    const res = resumoConciliacao(r, ds, CONTA, { periodo: { inicio: t[0].data, fim: t[1].data }, saldoFinal: { valor: 1000, data: dt(2025, 3, 31) } });
    expect(res.contagens).toMatchObject({ AUTOMATICO: 1, SO_NO_EXTRATO: 1, SO_NO_APP: 1, DUPLICADO: 0, SUGERIDO: 0 });
    expect(res.extratoEntradas).toBe(200);
    expect(res.extratoSaidas).toBe(40);
    expect(res.appSaidas).toBe(55);
    expect(res.saldo?.diferenca).toBe(1000 - (1000 - 40 - 15));
    const csv = csvRelatorio(r, new Map([[0, { acao: "conciliado", lancamentoId: 2 }]]));
    const linhas = csv.slice(1).split("\r\n");
    expect(linhas[0]).toBe("data;descricao;valor;situacao;lancamento_id;acao");
    expect(linhas[1]).toBe("12/03/2025;MERCADO;-40,00;automatico;2;conciliado");
    expect(linhas[2]).toBe('13/03/2025;"Depósito; ""x""";200,00;so_no_extrato;;nenhuma');
    expect(linhas[3]).toBe("13/03/2025;Só no app;15,00;so_no_app;3;nenhuma".replace("15,00", "-15,00"));
  });
});

describe("backup com fitid/conciliadoEm", () => {
  it("round-trip e ausência = null", () => {
    const ds = base([desp({ id: 2, fitid: "ZZ", conciliadoEm: 123 })]);
    const r = importarBackup(JSON.stringify(exportarBackup(ds)), 0);
    if (!r.ok) throw new Error(r.erro);
    expect(r.dataset.despesas.find((d) => d.id === 2)).toMatchObject({ fitid: "ZZ", conciliadoEm: 123 });
    const antigo = importarBackup(JSON.stringify({ despesas: [{ id: 1, valor: 1, tipo: "DEBITO" }] }), 0);
    if (!antigo.ok) throw new Error(antigo.erro);
    expect(antigo.dataset.despesas[0]).toMatchObject({ fitid: null, conciliadoEm: null });
  });
});
