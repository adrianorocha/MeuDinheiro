import { describe, expect, it } from "vitest";
import { cartao, conta, dataset, desp, dt } from "../finance/fixtures";
import { parseCsv, analisarCsv } from "./csv";
import { cartaoDoFinal, finalDaCelula, finalNaDescricao } from "./final-cartao";
import { casar, cartoesDoDestino, lancamentosDoDestino } from "./matching";
import { parseOfx } from "./ofx";
import { aplicarConciliacao, montarPlano } from "./plano";
import type { Destino, TransacaoBanco } from "./tipos";

const CARTAO: Destino = { tipo: "CARTAO", cartaoId: 10 };
const T = (fitid: string, valor: number, descricao: string, finalCartao?: string, dia = 10): TransacaoBanco => ({
  fitid,
  data: dt(2025, 3, dia),
  valor,
  descricao,
  ...(finalCartao ? { finalCartao } : {}),
});

function base(extra: ReturnType<typeof desp>[] = []) {
  return dataset({
    contas: [conta({ id: 1, conta: "111" })],
    cartoes: [
      cartao({ id: 10, nome: "Visa", finalCartao: "1111", contaId: 1, limiteTotal: 5000, limiteDisponivel: 5000 }),
      cartao({ id: 11, nome: "Adicional", finalCartao: "2222", contaId: 1, cartaoPrincipalId: 10, limiteTotal: 5000, limiteDisponivel: 5000 }),
    ],
    despesas: extra,
  });
}
function ok<R extends { ok: boolean }>(r: R): Extract<R, { ok: true }> {
  if (!r.ok) throw new Error(`esperava ok: ${(r as { erro?: string }).erro}`);
  return r as Extract<R, { ok: true }>;
}

describe("R101 - final do cartão na linha", () => {
  it("lê o final de textos com palavra-chave ou máscara, nunca de números soltos", () => {
    expect(finalNaDescricao("MERCADO ALFA CARTAO FINAL 2222")).toBe("2222");
    expect(finalNaDescricao("Compra no cartão final: 1234")).toBe("1234");
    expect(finalNaDescricao("LOJA **** 5678")).toBe("5678");
    expect(finalNaDescricao("LOJA XXXX.XXXX.XXXX.9012")).toBe("9012");
    expect(finalNaDescricao("card ending in 3456")).toBe("3456");
    expect(finalNaDescricao("MERCADO ALFA 2025")).toBeUndefined();
    expect(finalNaDescricao("PARC 3/12 LOJA 1234")).toBeUndefined();
    expect(finalNaDescricao("CARTAO 12345")).toBeUndefined();
  });
  it("célula dedicada: últimos 4 dígitos de qualquer formato", () => {
    expect(finalDaCelula("**** 4321")).toBe("4321");
    expect(finalDaCelula("5502 **** **** 1234")).toBe("1234");
    expect(finalDaCelula("Virtual")).toBeUndefined();
    expect(finalDaCelula("")).toBeUndefined();
  });
  it("resolve o cartão do grupo só quando há exatamente um com aquele final", () => {
    const g = base().cartoes;
    expect(cartaoDoFinal("2222", g)?.id).toBe(11);
    expect(cartaoDoFinal("9999", g)).toBeNull();
    expect(cartaoDoFinal(undefined, g)).toBeNull();
    expect(cartaoDoFinal("4321", [...g, { ...g[1], id: 12, finalCartao: "1111" }])).toBeNull();
    expect(cartaoDoFinal("1111", [...g, { ...g[1], id: 12, finalCartao: "1111" }])).toBeNull(); // repetido: ambíguo
  });
});

describe("R101 - leitura dos arquivos", () => {
  it("OFX: final no texto da transação", () => {
    const ofx = `<OFX><CCSTMTRS><BANKTRANLIST>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250310<TRNAMT>-50.00<FITID>1<MEMO>MERCADO FINAL 2222</STMTTRN>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20250311<TRNAMT>-20.00<FITID>2<MEMO>PADARIA</STMTTRN>
</BANKTRANLIST></CCSTMTRS></OFX>`;
    const r = parseOfx(ofx);
    expect(r.transacoes[0].finalCartao).toBe("2222");
    expect(r.transacoes[1].finalCartao).toBeUndefined();
  });
  it("CSV: detecta a coluna do cartão pelo cabeçalho e usa o texto como reserva", () => {
    const csv = "Data;Descrição;Cartão;Valor\n10/03/2025;MERCADO;**** 2222;-50,00\n11/03/2025;PADARIA CARTAO FINAL 1111;;-20,00\n12/03/2025;LOJA;;-5,00\n";
    const a = analisarCsv(csv);
    expect(a.config.colCartao).toBe(2);
    const r = parseCsv(csv, a.config);
    expect(r.transacoes.map((t) => t.finalCartao)).toEqual(["2222", "1111", undefined]);
  });
});

describe("R101 - conciliação vincula ao cartão do final", () => {
  it("desempata a favor do lançamento do cartão certo (mesmo valor, data e descrição)", () => {
    const ds = base([
      desp({ id: 1, descricao: "Mercado", valor: 80, data: dt(2025, 3, 10), cartaoId: 10 }),
      desp({ id: 2, descricao: "Mercado", valor: 80, data: dt(2025, 3, 10), cartaoId: 11 }),
    ]);
    const lanc = lancamentosDoDestino(ds, CARTAO);
    const cartoes = cartoesDoDestino(ds, CARTAO);
    const sem = casar([T("a", -80, "MERCADO")], lanc, { janelaDias: 5 });
    expect(sem.itens[0].classe).toBe("SUGERIDO"); // empate: sem o final não decide
    const com = casar([T("a", -80, "MERCADO", "2222")], lanc, { janelaDias: 5, cartoes });
    expect(com.itens[0]).toMatchObject({ classe: "AUTOMATICO", lancamentoId: 2 });
  });

  it("criar: o lançamento nasce no cartão do final; sem final ou ambíguo, no cartão do destino", () => {
    const ds = base();
    const r = casar([T("a", -50, "LOJA A", "2222"), T("b", -30, "LOJA B"), T("c", -20, "LOJA C", "9999")], [], { janelaDias: 5, cartoes: cartoesDoDestino(ds, CARTAO) });
    const plano = montarPlano(r, ds, CARTAO, { criar: true });
    const ap = ok(aplicarConciliacao(ds, plano, dt(2025, 4, 1)));
    const porDesc = (s: string) => ap.ds.despesas.find((d) => d.descricao.includes(s));
    expect(porDesc("Loja A")?.cartaoId).toBe(11);
    expect(porDesc("Loja B")?.cartaoId).toBe(10);
    expect(porDesc("Loja C")?.cartaoId).toBe(10);
    expect(ap.resumo.vinculadosPorFinal).toBe(1);
  });

  it("conciliar: move o lançamento para o cartão do final; desligado em mescla.cartao=false; desfazer restaura", () => {
    const ds = base([desp({ id: 1, descricao: "Mercado", valor: 80, data: dt(2025, 3, 10), cartaoId: 10 })]);
    const r = casar([T("a", -80, "MERCADO", "2222")], lancamentosDoDestino(ds, CARTAO), { janelaDias: 5 });
    const plano = montarPlano(r, ds, CARTAO);
    const ap = ok(aplicarConciliacao(ds, plano, dt(2025, 4, 1)));
    expect(ap.ds.despesas[0]).toMatchObject({ cartaoId: 11, conciliadoEm: expect.any(Number) });
    expect(ap.resumo.vinculadosPorFinal).toBe(1);

    const semMover = { ...plano, acoes: plano.acoes.map((a) => (a.tipo === "CONCILIAR" ? { ...a, mescla: { ...a.mescla, cartao: false } } : a)) };
    expect(ok(aplicarConciliacao(ds, semMover, dt(2025, 4, 1))).ds.despesas[0].cartaoId).toBe(10);
    expect(ap.inverso.restaurar[0].cartaoId).toBe(10);
  });

  it("conta não é afetada pelo final", () => {
    const ds = dataset({ contas: [conta({ id: 1, conta: "111" })], despesas: [desp({ id: 1, descricao: "Mercado", valor: 80, data: dt(2025, 3, 10), conta: "111" })] });
    const destino: Destino = { tipo: "CONTA", conta: "111" };
    const r = casar([T("a", -80, "MERCADO", "2222")], lancamentosDoDestino(ds, destino), { janelaDias: 3 });
    const ap = ok(aplicarConciliacao(ds, montarPlano(r, ds, destino), dt(2025, 4, 1)));
    expect(ap.ds.despesas[0].cartaoId ?? null).toBeNull();
    expect(ap.resumo.vinculadosPorFinal ?? 0).toBe(0);
  });
});
