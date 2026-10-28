import { describe, expect, it } from "vitest";
import { limiteGrupo } from "../finance/calc";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import type { Dataset } from "../finance/types";
import { executarLote, OPCOES_LOTE_PADRAO } from "./lote-multiplo";
import { casar, lancamentosDoDestino } from "./matching";
import { aplicarConciliacao, desfazerLote, montarPlano } from "./plano";
import { detectarParcelaExtrato } from "./texto-banco";
import type { Destino, TransacaoBanco } from "./tipos";

const CARTAO: Destino = { tipo: "CARTAO", cartaoId: 10 };
const AGORA = dt(2025, 4, 1);

function base(extra: Dataset["despesas"] = []): Dataset {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    cartoes: [cartao({ id: 10, contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000 }), cartao({ id: 11, nome: "V", contaId: 1, limiteTotal: 5000, cartaoPrincipalId: 10 })],
    despesas: extra,
  });
}
function ok<R extends { ok: boolean }>(r: R): Extract<R, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<R, { ok: true }>;
}
const T = (data: number, valor: number, descricao: string, fitid: string): TransacaoBanco => ({ fitid, data, valor, descricao });

function plano(ds: Dataset, t: TransacaoBanco[], parcelasRestantes: boolean) {
  const r = casar(t, lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
  return montarPlano(r, ds, CARTAO, { criar: true, parcelasRestantes });
}

describe("R44 - detecção de parcela no extrato de cartão", () => {
  it("formas explícitas e i/n solto que não parece data", () => {
    expect(detectarParcelaExtrato("LOJA (3/10)", dt(2025, 3, 5))).toEqual({ i: 3, n: 10 });
    expect(detectarParcelaExtrato("LOJA PARC 3/10", dt(2025, 3, 5))).toEqual({ i: 3, n: 10 });
    expect(detectarParcelaExtrato("COMPRA 03/10", dt(2025, 11, 20))).toEqual({ i: 3, n: 10 });
  });
  it("ignora datas e anos: dd/mm perto da data do extrato, dd/mm/aaaa, i > n", () => {
    expect(detectarParcelaExtrato("COMPRA 03/10", dt(2025, 10, 5))).toBeNull();
    expect(detectarParcelaExtrato("COMPRA 03/10/2025", dt(2025, 11, 20))).toBeNull();
    expect(detectarParcelaExtrato("COMPRA 28/09", dt(2025, 11, 20))).toBeNull();
    expect(detectarParcelaExtrato("COMPRA 12:30 LOJA", dt(2025, 11, 20))).toBeNull();
    expect(detectarParcelaExtrato("LOJA (1/1)", dt(2025, 3, 5))).toBeNull();
  });
});

describe("R44 - criar parcelas restantes na conciliação de cartão", () => {
  const ds0 = base();
  const t = [T(dt(2025, 3, 10), -100, "MERCADO LIVRE PARC 03/10", "f1")];

  it("cria i..n: só a do banco tem fitid, restantes em aberto, mesmo grupo, valor igual e limite reservado", () => {
    const ap = ok(aplicarConciliacao(ds0, plano(ds0, t, true), AGORA));
    const novas = ap.ds.despesas;
    expect(novas).toHaveLength(8);
    expect(ap.resumo).toMatchObject({ criados: 1, parcelasRestantes: 7 });
    const [p3, ...resto] = novas;
    expect(p3.descricao).toBe("Mercado Livre (3/10)");
    expect(p3.fitid).toBe("f1");
    expect(p3.data).toBe(dt(2025, 3, 10));
    expect(resto.map((d) => d.descricao)).toEqual(["Mercado Livre (4/10)", "Mercado Livre (5/10)", "Mercado Livre (6/10)", "Mercado Livre (7/10)", "Mercado Livre (8/10)", "Mercado Livre (9/10)", "Mercado Livre (10/10)"]);
    expect(resto.every((d) => d.fitid === null && d.conciliadoEm === null && !d.pago && d.valor === 100 && d.cartaoId === 10 && d.grupoId === p3.grupoId)).toBe(true);
    expect(p3.grupoId).toMatch(/^parc:/);
    expect(resto[0].data).toBe(dt(2025, 4, 10));
    expect(limiteGrupo(ap.ds.cartoes[0], ap.ds.cartoes, ap.ds.despesas)).toBe(4200);
    // desfazer remove todas as criadas
    const volta = desfazerLote(ap.ds, ap.inverso);
    expect(volta.despesas).toHaveLength(0);
    expect(volta.cartoes[0].limiteDisponivel).toBe(5000);
  });

  it("opção desligada mantém o comportamento antigo (só a parcela do extrato)", () => {
    const ap = ok(aplicarConciliacao(ds0, plano(ds0, t, false), AGORA));
    expect(ap.ds.despesas).toHaveLength(1);
    expect(ap.resumo.parcelasRestantes).toBe(0);
  });

  it("não duplica parcelas futuras já existentes (mesma descrição, valor e mês)", () => {
    const existentes = [
      desp({ id: 500, descricao: "Mercado Livre (4/10)", valor: 100, cartaoId: 10, pago: false, data: dt(2025, 4, 10), grupoId: "parc:velho" }),
      desp({ id: 501, descricao: "Mercado Livre (5/10)", valor: 100, cartaoId: 11, pago: false, data: dt(2025, 5, 12), grupoId: "parc:velho" }),
    ];
    const ds = base(existentes);
    const ap = ok(aplicarConciliacao(ds, plano(ds, t, true), AGORA));
    expect(ap.resumo.parcelasRestantes).toBe(5);
    expect(ap.ds.despesas).toHaveLength(2 + 1 + 5);
    expect(ap.ds.despesas.filter((d) => d.descricao === "Mercado Livre (4/10)")).toHaveLength(1);
    expect(ap.ds.despesas.find((d) => d.fitid === "f1")!.grupoId).toBe("parc:velho");
  });

  it("não expande conta, parcela final, crédito nem transação sem marcador", () => {
    const dsConta = base();
    const rConta = casar(t, lancamentosDoDestino(dsConta, { tipo: "CONTA", conta: "111" }), { janelaDias: 3 });
    const pc = montarPlano(rConta, dsConta, { tipo: "CONTA", conta: "111" }, { criar: true, parcelasRestantes: true });
    expect(ok(aplicarConciliacao(dsConta, pc, AGORA)).ds.despesas).toHaveLength(1);
    const final = [T(dt(2025, 3, 10), -100, "LOJA (10/10)", "f2"), T(dt(2025, 3, 11), -50, "LOJA SEM PARCELA", "f3"), T(dt(2025, 3, 12), 30, "ESTORNO (2/5)", "f4")];
    expect(ok(aplicarConciliacao(ds0, plano(ds0, final, true), AGORA)).ds.despesas).toHaveLength(3);
  });

  it("lote: padrão liga as parcelas restantes; desfazer remove tudo; reimportar não duplica", async () => {
    let ds = base();
    const arq = { id: "a", nome: "fatura.ofx", destino: CARTAO, rotuloDestino: "F", extrato: { formato: "OFX" as const, transacoes: t, periodo: null, avisos: [] } };
    const env = {
      getDs: () => ds,
      aplicar: (p: Parameters<typeof aplicarConciliacao>[1]) => {
        const r = aplicarConciliacao(ds, p, AGORA);
        if (r.ok) ds = r.ds;
        return r.ok ? { ok: true as const, inverso: r.inverso, resumo: r.resumo } : r;
      },
    };
    expect(OPCOES_LOTE_PADRAO.parcelasRestantes).toBe(true);
    const r = await executarLote({ arquivos: [arq], opcoes: { ...OPCOES_LOTE_PADRAO, criar: true }, ceder: async () => {}, ...env });
    expect(ds.despesas).toHaveLength(8);
    expect(r.arquivos[0].contadores.criados).toBe(1);
    expect(r.arquivos[0].linhas[0].lancamentoId).toBe(ds.despesas[0].id);
    const r2 = await executarLote({ arquivos: [arq], opcoes: { ...OPCOES_LOTE_PADRAO, criar: true }, ceder: async () => {}, ...env });
    expect(ds.despesas).toHaveLength(8);
    expect(r2.arquivos[0].contadores.duplicados).toBe(1);
    const inv = r.arquivos[0].inverso!;
    ds = desfazerLote(ds, inv);
    expect(ds.despesas).toHaveLength(0);
    // desligado no lote
    ds = base();
    await executarLote({ arquivos: [arq], opcoes: { ...OPCOES_LOTE_PADRAO, criar: true, parcelasRestantes: false }, ceder: async () => {}, ...env });
    expect(ds.despesas).toHaveLength(1);
  });
});
