import { describe, expect, it } from "vitest";
import {
  agruparPorRaiz,
  dispositivoObsoleto,
  dispositivosDaRaiz,
  dispositivosParaRemover,
  filtrarPorPrefs,
  lerDispositivo,
  lotes,
  raizAutorizada,
} from "../src/dominio/dispositivos";
import { montarMensagemFcm } from "../src/dominio/mensagens";
import {
  chaveAviso,
  decidirAvisoOrcamento,
  gastoDaCategoria,
  idDaMarca,
  montarAvisoOrcamento,
} from "../src/dominio/orcamento";
import { calcularResumoSemanal, centavos, janelaSemana, montarResumoSemanal } from "../src/dominio/resumoSemanal";
import { chaveMes, inicioDoDiaMs, limitesDoMes, numeroDoDia } from "../src/dominio/tempo";
import type { DespesaDoc, Dispositivo } from "../src/dominio/tipos";
import { entraNoAPagar, montarAvisoVencimentos, selecionarVencimentos } from "../src/dominio/vencimentos";

/** Instante em São Paulo (UTC-3, sem horário de verão desde 2019) a partir de data/hora local. */
const sp = (a: number, m: number, d: number, h = 12, min = 0) => Date.UTC(a, m - 1, d, h + 3, min);

function desp(p: Partial<DespesaDoc> & { data: number }): DespesaDoc {
  return {
    id: "x", descricao: "Conta", valor: 100, categoria: "Mercado", tipo: "DEBITO", cartaoId: null, pago: false, natureza: "NORMAL", ...p,
  };
}

function disp(p: Partial<Dispositivo> = {}): Dispositivo {
  return { id: "d1", uid: "u1", token: "t".repeat(30), plataforma: "android", prefs: {}, ...p };
}

describe("tempo (America/Sao_Paulo)", () => {
  it("o dia civil usa o fuso, não o UTC", () => {
    const cedo = Date.UTC(2026, 9, 14, 1, 30); // 13/10 22:30 em SP
    expect(chaveMes(cedo)).toBe("2026-10");
    expect(numeroDoDia(cedo)).toBe(numeroDoDia(sp(2026, 10, 13, 22)));
    expect(numeroDoDia(cedo)).not.toBe(numeroDoDia(sp(2026, 10, 14, 1)));
  });
  it("virada de mês no fuso", () => {
    expect(chaveMes(Date.UTC(2026, 10, 1, 2, 0))).toBe("2026-10"); // 31/10 23:00 em SP
    expect(chaveMes(Date.UTC(2026, 10, 1, 3, 0))).toBe("2026-11");
  });
  it("início do dia e limites do mês", () => {
    expect(inicioDoDiaMs(numeroDoDia(sp(2026, 10, 13, 15)))).toBe(sp(2026, 10, 13, 0));
    const l = limitesDoMes("2026-12");
    expect(l.inicio).toBe(sp(2026, 12, 1, 0));
    expect(l.fimExclusivo).toBe(sp(2027, 1, 1, 0));
  });
});

describe("vencimentos", () => {
  const agora = sp(2026, 10, 15, 8); // quinta, 08:00
  it("a pagar = não paga, débito, sem cartão, NORMAL", () => {
    expect(entraNoAPagar(desp({ data: agora }))).toBe(true);
    expect(entraNoAPagar(desp({ data: agora, pago: true }))).toBe(false);
    expect(entraNoAPagar(desp({ data: agora, tipo: "CREDITO" }))).toBe(false);
    expect(entraNoAPagar(desp({ data: agora, cartaoId: 3 }))).toBe(false); // fatura cobre
    expect(entraNoAPagar(desp({ data: agora, natureza: "TRANSFERENCIA" }))).toBe(false);
  });
  it("seleciona atrasadas, hoje e amanhã; ignora depois de amanhã", () => {
    const sel = selecionarVencimentos(
      [
        desp({ id: "a", data: sp(2026, 10, 10) }),
        desp({ id: "b", data: sp(2026, 10, 15, 23) }),
        desp({ id: "c", data: sp(2026, 10, 16, 0) }),
        desp({ id: "d", data: sp(2026, 10, 17) }),
        desp({ id: "e", data: sp(2026, 10, 16), pago: true }),
        desp({ id: "f", data: sp(2026, 10, 16), cartaoId: 1 }),
      ],
      agora,
    );
    expect(sel.atrasadas.map((x) => x.id)).toEqual(["a"]);
    expect(sel.hoje.map((x) => x.id)).toEqual(["b"]);
    expect(sel.amanha.map((x) => x.id)).toEqual(["c"]);
  });
  it("texto agrupado em pt-BR, sem valores", () => {
    const sel = selecionarVencimentos(
      [desp({ data: sp(2026, 10, 1), valor: 987.65 }), desp({ data: sp(2026, 10, 1) }), desp({ data: sp(2026, 10, 15) }), desp({ data: sp(2026, 10, 16) }), desp({ data: sp(2026, 10, 16) }), desp({ data: sp(2026, 10, 16) })],
      agora,
    )!;
    const m = montarAvisoVencimentos(sel)!;
    expect(m.titulo).toBe("6 contas a pagar");
    expect(m.corpo).toBe("2 em atraso, 1 vence hoje e 3 vencem amanhã. Toque para ver.");
    expect(m.extra).toEqual({ atrasadas: "2", hoje: "1", amanha: "3" });
    expect(`${m.titulo} ${m.corpo}`).not.toMatch(/R\$|987/);
  });
  it("títulos de casos simples e vazio", () => {
    const um = (data: number) => montarAvisoVencimentos(selecionarVencimentos([desp({ data })], agora))!;
    expect(um(sp(2026, 10, 16)).titulo).toBe("1 conta vence amanhã");
    expect(um(sp(2026, 10, 1)).titulo).toBe("1 conta atrasada");
    expect(montarAvisoVencimentos(selecionarVencimentos([], agora))).toBeNull();
    expect(montarAvisoVencimentos(selecionarVencimentos([desp({ data: sp(2026, 10, 30) })], agora))).toBeNull();
  });
});

describe("resumo semanal em centavos", () => {
  const agora = sp(2026, 10, 18, 18); // domingo 18:00
  it("janela de 7 dias terminando hoje", () => {
    const j = janelaSemana(agora);
    expect(j.ultimo - j.primeiro).toBe(6);
    expect(numeroDoDia(sp(2026, 10, 12))).toBe(j.primeiro);
  });
  it("soma entradas/saídas NORMAL, sem erro de ponto flutuante, e acha a categoria top", () => {
    const r = calcularResumoSemanal(
      [
        desp({ data: sp(2026, 10, 13), valor: 0.1, tipo: "DEBITO", pago: true, categoria: "Mercado" }),
        desp({ data: sp(2026, 10, 14), valor: 0.2, tipo: "DEBITO", pago: true, categoria: "mercado " }),
        desp({ data: sp(2026, 10, 15), valor: 50.5, tipo: "DEBITO", pago: true, categoria: "Lazer" }),
        desp({ data: sp(2026, 10, 16), valor: 1000, tipo: "CREDITO", pago: true, categoria: "Salário" }),
        desp({ data: sp(2026, 10, 16), valor: 70, tipo: "DEBITO", pago: false, categoria: "Aluguel" }), // em aberto: fora
        desp({ data: sp(2026, 10, 16), valor: 30, tipo: "DEBITO", cartaoId: 2, categoria: "Lazer" }), // compra de cartão: conta
        desp({ data: sp(2026, 10, 16), valor: 999, tipo: "DEBITO", pago: true, natureza: "TRANSFERENCIA" }),
        desp({ data: sp(2026, 10, 5), valor: 999, tipo: "DEBITO", pago: true }), // fora da janela
        desp({ data: sp(2026, 10, 17), valor: 20, tipo: "CREDITO", cartaoId: 1, pago: true }), // estorno de cartão: não é receita
      ],
      agora,
    );
    expect(r.entradasCentavos).toBe(100_000);
    expect(r.saidasCentavos).toBe(centavos(0.1) + centavos(0.2) + 5050 + 3000);
    expect(r.topCategoria).toBe("Lazer");
    expect(r.topCentavos).toBe(8050);
    expect(r.qtd).toBe(5);
  });
  it("mensagem sem valores no texto; semana vazia não envia", () => {
    const m = montarResumoSemanal({ entradasCentavos: 123456, saidasCentavos: 7890, topCategoria: "Mercado", topCentavos: 5000, qtd: 3 })!;
    expect(m.extra).toMatchObject({ entradasCentavos: "123456", saidasCentavos: "7890", topCategoria: "Mercado" });
    expect(`${m.titulo} ${m.corpo}`).not.toMatch(/\d/);
    expect(montarResumoSemanal({ entradasCentavos: 0, saidasCentavos: 0, topCategoria: null, topCentavos: 0, qtd: 0 })).toBeNull();
  });
  it("empate de categoria é determinístico (alfabético)", () => {
    const r = calcularResumoSemanal(
      [desp({ data: sp(2026, 10, 13), pago: true, categoria: "Zeta" }), desp({ data: sp(2026, 10, 13), pago: true, categoria: "Alfa" })],
      agora,
    );
    expect(r.topCategoria).toBe("Alfa");
  });
});

describe("orçamento 80% / 100% com idempotência", () => {
  const orc = { categoria: "Mercado", valorLimite: 500 };
  const mes = "2026-10";
  it("gasto: mês e categoria certos, estorno de cartão subtrai, receita comum fora", () => {
    const g = gastoDaCategoria(
      [
        desp({ data: sp(2026, 10, 2), valor: 200 }),
        desp({ data: sp(2026, 10, 3), valor: 250, cartaoId: 1, categoria: " MERCADO" }),
        desp({ data: sp(2026, 10, 4), valor: 50, tipo: "CREDITO", cartaoId: 1 }),
        desp({ data: sp(2026, 10, 5), valor: 900, tipo: "CREDITO" }),
        desp({ data: sp(2026, 9, 30), valor: 900 }),
        desp({ data: sp(2026, 10, 6), valor: 900, natureza: "AJUSTE" }),
        desp({ data: sp(2026, 10, 7), valor: 900, categoria: "Lazer" }),
      ],
      "mercado",
      mes,
    );
    expect(g).toBe(40_000);
  });
  it("80% avisa uma vez; 100% avisa depois e marca ambos", () => {
    const vazio = new Set<string>();
    expect(decidirAvisoOrcamento(orc, 39_999, mes, vazio)).toBeNull();
    const a80 = decidirAvisoOrcamento(orc, 40_000, mes, vazio)!;
    expect(a80.limiar).toBe(80);
    expect(a80.marcar).toEqual([chaveAviso("Mercado", mes, 80)]);
    // repetir com a marca: nada
    expect(decidirAvisoOrcamento(orc, 45_000, mes, new Set(a80.marcar))).toBeNull();
    // estourou depois do 80%
    const a100 = decidirAvisoOrcamento(orc, 50_000, mes, new Set(a80.marcar))!;
    expect(a100.limiar).toBe(100);
    expect(a100.marcar).toContain(chaveAviso("Mercado", mes, 80));
    expect(decidirAvisoOrcamento(orc, 90_000, mes, new Set(a100.marcar))).toBeNull();
  });
  it("salto direto para 100% não manda também o 80%", () => {
    const a = decidirAvisoOrcamento(orc, 60_000, mes, new Set())!;
    expect(a.limiar).toBe(100);
    expect(decidirAvisoOrcamento(orc, 45_000, mes, new Set([chaveAviso("Mercado", mes, 100)]))).toBeNull(); // caiu abaixo: não reavisa 80
  });
  it("meses diferentes têm chaves diferentes; limite zero não avisa", () => {
    expect(chaveAviso("Mercado", "2026-10", 80)).not.toBe(chaveAviso("Mercado", "2026-11", 80));
    expect(decidirAvisoOrcamento({ categoria: "x", valorLimite: 0 }, 10_000, mes, new Set())).toBeNull();
  });
  it("id da marca nunca tem barra e é estável; texto sem valores", () => {
    const id = idDaMarca(chaveAviso("Casa/Reforma.2", mes, 100));
    expect(id).not.toMatch(/[/.]/);
    expect(id).toBe(idDaMarca(chaveAviso("casa/reforma.2 ", mes, 100)));
    const m = montarAvisoOrcamento({ categoria: "Mercado", limiar: 100, chave: "k", marcar: [] });
    expect(m.titulo).toBe("Orçamento de Mercado estourado");
    expect(m.extra).toEqual({ categoria: "Mercado", limiar: "100" });
    expect(montarAvisoOrcamento({ categoria: "Mercado", limiar: 80, chave: "k", marcar: [] }).titulo).toContain("80%");
  });
});

describe("dispositivos: prefs, vínculo, limpeza", () => {
  it("lê documento válido e rejeita token/plataforma inúteis", () => {
    const ok = lerDispositivo("u1", "d1", { token: "a".repeat(40), plataforma: "web", prefs: { vencimentos: false, lixo: 1 }, raizUid: "dono", atualizadoEm: { toMillis: () => 5 } })!;
    expect(ok).toMatchObject({ uid: "u1", plataforma: "web", prefs: { vencimentos: false }, raizUid: "dono", atualizadoEmMs: 5 });
    expect(lerDispositivo("u1", "d", { token: "curto", plataforma: "android" })).toBeNull();
    expect(lerDispositivo("u1", "d", { token: "a".repeat(40), plataforma: "ios" })).toBeNull();
    expect(lerDispositivo("u1", "d", { token: 5, plataforma: "android" })).toBeNull();
  });
  it("filtra por prefs: só false explícito desliga", () => {
    const todos = [disp({ id: "a" }), disp({ id: "b", prefs: { orcamento: false } }), disp({ id: "c", prefs: { orcamento: true, vencimentos: false } })];
    expect(filtrarPorPrefs(todos, "ORCAMENTO").map((d) => d.id)).toEqual(["a", "c"]);
    expect(filtrarPorPrefs(todos, "VENCIMENTOS").map((d) => d.id)).toEqual(["a", "b"]);
    expect(filtrarPorPrefs(todos, "RESUMO_SEMANAL")).toHaveLength(3);
    expect(filtrarPorPrefs([disp({ prefs: { convites: false } })], "CONVITE")).toHaveLength(0);
  });
  it("só recebe avisos de uma conta alheia se for MEMBRO dela", () => {
    const dono = "dono";
    const membro = disp({ id: "m", uid: "m1", raizUid: dono });
    const intruso = disp({ id: "i", uid: "i1", raizUid: dono });
    const proprio = disp({ id: "p", uid: dono });
    const membros = new Set(["m1"]);
    expect(raizAutorizada(membro, membros)).toBe(true);
    expect(raizAutorizada(intruso, membros)).toBe(false);
    expect(dispositivosDaRaiz(dono, [membro, intruso, proprio], membros).map((d) => d.id)).toEqual(["m", "p"]);
    expect([...agruparPorRaiz([membro, intruso, proprio]).keys()]).toEqual([dono]);
  });
  it("limpeza de tokens inválidos pelos códigos do FCM", () => {
    const ds = [disp({ id: "a" }), disp({ id: "b" }), disp({ id: "c" }), disp({ id: "d" })];
    const r = dispositivosParaRemover(ds, [undefined, "messaging/registration-token-not-registered", "messaging/internal-error", "messaging/invalid-registration-token"]);
    expect(r.map((d) => d.id)).toEqual(["b", "d"]);
  });
  it("dispositivo obsoleto após 90 dias sem renovar", () => {
    const agora = Date.UTC(2026, 9, 15);
    expect(dispositivoObsoleto(disp({ atualizadoEmMs: agora - 91 * 86_400_000 }), agora)).toBe(true);
    expect(dispositivoObsoleto(disp({ atualizadoEmMs: agora - 89 * 86_400_000 }), agora)).toBe(false);
    expect(dispositivoObsoleto(disp(), agora)).toBe(false);
  });
  it("lotes de 500", () => {
    expect(lotes(Array.from({ length: 1001 }, (_, i) => i)).map((l) => l.length)).toEqual([500, 500, 1]);
  });
});

describe("mensagem FCM", () => {
  it("é só de dados, com tokens únicos e prioridade alta", () => {
    const m = montarMensagemFcm(
      { tipo: "VENCIMENTOS", titulo: "t", corpo: "c", extra: { atrasadas: "1" } },
      [disp({ token: "x".repeat(30) }), disp({ id: "b", token: "x".repeat(30) }), disp({ id: "c", token: "y".repeat(30) })],
    );
    expect(m.tokens).toHaveLength(2);
    expect(m.data).toEqual({ atrasadas: "1", tipo: "VENCIMENTOS", titulo: "t", corpo: "c" });
    expect(m.android.priority).toBe("high");
    expect(m).not.toHaveProperty("notification");
    for (const v of Object.values(m.data)) expect(typeof v).toBe("string");
  });
});
