import { describe, expect, it } from "vitest";
import {
  buscarCotacoes,
  buscarIndicesBcb,
  buscarJson,
  cacheExpirado,
  calcularCotacao,
  dataBrParaIso,
  dataPtax,
  descreverEstado,
  fechamentosPorDia,
  indiceMensal,
  lerSnapshotIndices,
  maisRecenteAteHoje,
  mesclarSnapshots,
  moedaParaReais,
  parseBoletins,
  parsePontosSgs,
  reaisParaMoeda,
  salarioDe,
  selicMetaDe,
  TTL_MS,
  urlPtax,
  FAIXAS,
  type FetchLike,
  type SnapshotIndices,
} from "./bcb";
import { aplicarBcb, indicesPadrao, mesclarSalvo, paraSalvar } from "./indices";

const HOJE = "2026-10-28";
const AGORA = new Date(2026, 9, 28, 14, 30, 0);

const SELIC_META = [
  { data: "02/11/2026", valor: "13.75" },
  { data: "03/11/2026", valor: "13.75" },
  { data: "04/11/2026", valor: "13.75" },
  { data: "27/10/2026", valor: "13.75" },
];

describe("parsing SGS", () => {
  it("lê itens reais (valor string, dataFim ignorada) e descarta lixo", () => {
    const r = parsePontosSgs([
      { data: "25/10/2026", dataFim: "25/11/2026", valor: "0.9259" },
      { data: "31/02/2026", valor: "1" },
      { data: "01/10/2026", valor: "abc" },
      { data: "01/10/2026", valor: "" },
      null,
      { valor: "1" },
    ]);
    expect(r).toEqual([{ data: "2026-10-25", valor: 0.9259 }]);
    expect(parsePontosSgs({ erro: true })).toEqual([]);
  });

  it("converte data dd/mm/aaaa", () => {
    expect(dataBrParaIso("01/09/2026")).toBe("2026-09-01");
    expect(dataBrParaIso("2026-09-01")).toBeNull();
    expect(dataBrParaIso(5)).toBeNull();
  });

  it("IPCA 433 (amostra real): últimos 3 meses, competência 09/2026, não parcial", () => {
    const r = indiceMensal(
      parsePontosSgs([
        { data: "01/07/2026", valor: "0.07" },
        { data: "01/08/2026", valor: "-0.32" },
        { data: "01/09/2026", valor: "0.82" },
      ]),
      HOJE,
    );
    expect(r).toEqual({ serie: [0.07, -0.32, 0.82], competencia: "09/2026", parcial: false });
  });

  it("Selic 4390 em ordem decrescente: ordena e marca o mês corrente como parcial", () => {
    const r = indiceMensal(
      parsePontosSgs([
        { data: "01/10/2026", valor: "0.92" },
        { data: "01/09/2026", valor: "1.08" },
        { data: "01/08/2026", valor: "1.09" },
      ]),
      HOJE,
    );
    expect(r).toEqual({ serie: [1.09, 1.08, 0.92], competencia: "10/2026", parcial: true });
  });

  it("TR 7811, poupança 196 e IGP-DI 190 (amostras reais)", () => {
    expect(indiceMensal(parsePontosSgs([{ data: "01/08/2026", valor: "0.1693" }, { data: "01/09/2026", valor: "0.1690" }, { data: "01/10/2026", valor: "0.1616" }]), HOJE)?.serie).toEqual([0.1693, 0.169, 0.1616]);
    expect(indiceMensal(parsePontosSgs([{ data: "01/10/2026", valor: "0.6624" }, { data: "01/09/2026", valor: "0.6698" }, { data: "01/08/2026", valor: "0.6701" }]), HOJE)?.serie).toEqual([0.6701, 0.6698, 0.6624]);
    expect(indiceMensal(parsePontosSgs([{ data: "01/07/2026", valor: "-0.86" }, { data: "01/08/2026", valor: "0.06" }, { data: "01/09/2026", valor: "1.50" }]), HOJE)?.competencia).toBe("09/2026");
  });

  it("datas futuras são inválidas: usa o último <= hoje (Selic meta 432)", () => {
    const p = parsePontosSgs(SELIC_META);
    expect(maisRecenteAteHoje(p, HOJE, FAIXAS.selicMeta)).toEqual({ data: "2026-10-27", valor: 13.75 });
    expect(selicMetaDe(p, HOJE)).toEqual({ valor: 13.75, data: "2026-10-27" });
    expect(selicMetaDe(parsePontosSgs([{ data: "03/11/2026", valor: "13.75" }]), HOJE)).toBeNull();
  });

  it("salário mínimo 1619: ignora 11/2026 e 12/2026 (futuros)", () => {
    const p = parsePontosSgs([
      { data: "01/10/2026", valor: "1621.00" },
      { data: "01/11/2026", valor: "1621.00" },
      { data: "01/12/2026", valor: "1700.00" },
    ]);
    expect(salarioDe(p, HOJE)).toEqual({ centavos: 162100, competencia: "10/2026" });
  });

  it("valida faixa plausível e descarta lixo", () => {
    const lixo = parsePontosSgs([
      { data: "01/09/2026", valor: "0.82" },
      { data: "01/10/2026", valor: "999" },
    ]);
    expect(indiceMensal(lixo, HOJE)?.competencia).toBe("09/2026");
    expect(indiceMensal(parsePontosSgs([{ data: "01/10/2026", valor: "-9" }]), HOJE)).toBeNull();
    expect(selicMetaDe(parsePontosSgs([{ data: "27/10/2026", valor: "130" }]), HOJE)).toBeNull();
    expect(salarioDe(parsePontosSgs([{ data: "01/10/2026", valor: "5" }]), HOJE)).toBeNull();
  });
});

describe("cache", () => {
  it("TTL de 6 h", () => {
    const t = 1_000_000_000_000;
    expect(cacheExpirado(null, t)).toBe(true);
    expect(cacheExpirado(t - 1000, t)).toBe(false);
    expect(cacheExpirado(t - TTL_MS + 1, t)).toBe(false);
    expect(cacheExpirado(t - TTL_MS, t)).toBe(true);
    expect(cacheExpirado(t + 5000, t)).toBe(true); // relógio no futuro
  });

  it("lê snapshot salvo com validação", () => {
    const ok = lerSnapshotIndices({ atualizadoEm: 5, itens: { ipca: { serie: [0.1, 0.2], competencia: "09/2026", parcial: false }, xxx: { serie: [1], competencia: "09/2026" }, cdi: { serie: [99], competencia: "09/2026" } }, selicMeta: { valor: 13.75, data: "2026-10-27" }, salarioMinimo: { centavos: 162100, competencia: "10/2026" } });
    expect(Object.keys(ok!.itens)).toEqual(["ipca"]);
    expect(ok!.selicMeta?.valor).toBe(13.75);
    expect(lerSnapshotIndices("lixo")).toBeNull();
    expect(lerSnapshotIndices({ itens: {} })).toBeNull();
  });

  it("texto de estado", () => {
    const ms = new Date(2026, 9, 28, 14, 5).getTime();
    expect(descreverEstado({ atualizadoEm: ms, carregando: false, erro: null, offline: false })).toBe("Atualizado em 28/10 14:05 — Banco Central");
    expect(descreverEstado({ atualizadoEm: ms, carregando: false, erro: null, offline: true })).toBe("Offline: usando valores salvos de 28/10 14:05");
    expect(descreverEstado({ atualizadoEm: null, carregando: false, erro: "HTTP 500", offline: false })).toContain("Não foi possível atualizar: HTTP 500");
  });
});

describe("mesclagem manual x BCB", () => {
  const snap: SnapshotIndices = {
    atualizadoEm: 1,
    itens: { ipca: { serie: [0.07, -0.32, 0.82], competencia: "09/2026", parcial: false }, selic: { serie: [1.09, 1.08, 0.92], competencia: "10/2026", parcial: true } },
    selicMeta: { valor: 13.75, data: "2026-10-27" },
    salarioMinimo: { centavos: 162100, competencia: "10/2026" },
    falhas: [],
  };

  it("sem edição: BCB prevalece e traz competência real", () => {
    const ef = aplicarBcb(indicesPadrao(), snap);
    const selic = ef.itens.find((i) => i.id === "selic")!;
    expect(selic.origem).toBe("bcb");
    expect(selic.parcial).toBe(true);
    expect(selic.aproximada).toBe(false);
    expect(selic.competencia).toBe("10/2026");
    expect(ef.itens.find((i) => i.id === "tbf")!.origem).toBeUndefined(); // sem série: continua semente
    expect(ef.extras.selicMetaAnual).toBe(13.75);
    expect(ef.extras.selicMetaOrigem).toBe("bcb");
    expect(ef.salarioMinimo.origem).toBe("bcb");
  });

  it("edição manual prevalece e nunca é sobrescrita", () => {
    const base = indicesPadrao();
    const ipca = base.itens.find((i) => i.id === "ipca")!;
    ipca.serie = [0.5];
    ipca.origem = "manual";
    base.extras.selicMetaAnual = 12;
    base.salarioMinimo = { centavos: 170000, competencia: "2026", origem: "manual" };
    const ef = aplicarBcb(base, snap);
    expect(ef.itens.find((i) => i.id === "ipca")!.serie).toEqual([0.5]);
    expect(ef.itens.find((i) => i.id === "ipca")!.origem).toBe("manual");
    expect(ef.extras.selicMetaAnual).toBe(12);
    expect(ef.extras.selicMetaOrigem).toBe("manual");
    expect(ef.salarioMinimo.centavos).toBe(170000);
    expect(base.itens.find((i) => i.id === "selic")!.origem).toBeUndefined(); // base não é mutada
  });

  it("só semente + manual são salvos; valores do BCB não entram na base", () => {
    const ef = aplicarBcb(indicesPadrao(), snap);
    const salvo = JSON.parse(JSON.stringify(paraSalvar(indicesPadrao())));
    expect(salvo.itens.find((i: { id: string }) => i.id === "selic").origem).toBeUndefined();
    const recarregado = mesclarSalvo(salvo);
    expect(recarregado.itens.find((i) => i.id === "selic")!.serie).toEqual([1.09, 1.08]);
    expect(ef.itens.find((i) => i.id === "selic")!.serie).toEqual([1.09, 1.08, 0.92]);
  });

  it("dados da R97 já editados viram manual ao recarregar; sem edição não", () => {
    const antigo = JSON.parse(JSON.stringify(indicesPadrao()));
    antigo.itens.find((i: { id: string }) => i.id === "cdi").serie = [1.2];
    const r = mesclarSalvo(antigo);
    expect(r.itens.find((i) => i.id === "cdi")!.origem).toBe("manual");
    expect(r.itens.find((i) => i.id === "selic")!.origem).toBeUndefined();
  });

  it("mesclar snapshots: o que falhou mantém o último valor conhecido", () => {
    const novo: SnapshotIndices = { atualizadoEm: 9, itens: { ipca: { serie: [1], competencia: "10/2026", parcial: true } }, selicMeta: null, salarioMinimo: null, falhas: [{ fonte: "x", motivo: "HTTP 500" }] };
    const m = mesclarSnapshots(snap, novo);
    expect(m.atualizadoEm).toBe(9);
    expect(m.itens.ipca?.serie).toEqual([1]);
    expect(m.itens.selic?.competencia).toBe("10/2026");
    expect(m.selicMeta?.valor).toBe(13.75);
  });
});

function resp(json: unknown, ok = true, status = 200) {
  return Promise.resolve({ ok, status, json: () => Promise.resolve(json) });
}

describe("rede (fetch injetado)", () => {
  it("falha parcial: uma série com erro não derruba as outras", async () => {
    const chamadas: Array<{ url: string; init?: RequestInit }> = [];
    const f: FetchLike = (url, init) => {
      chamadas.push({ url, init });
      if (url.includes("sgs.433/")) return resp({}, false, 500);
      if (url.includes("sgs.432/")) return resp(SELIC_META);
      if (url.includes("sgs.1619/")) return resp([{ data: "01/10/2026", valor: "1621.00" }]);
      if (url.includes("sgs.190/")) return resp([]);
      return resp([{ data: "01/09/2026", valor: "0.5" }]);
    };
    const s = await buscarIndicesBcb(AGORA, { fetch: f });
    expect(s.itens.ipca).toBeUndefined();
    expect(s.itens.igpm?.serie).toEqual([0.5]);
    expect(s.itens.tr?.parcial).toBe(false); // 10/2026 não é "parcial" para a TR; só Selic/CDI
    expect(s.itens.selic?.parcial).toBe(false); // amostra de teste é 09/2026
    expect(s.selicMeta?.valor).toBe(13.75);
    expect(s.salarioMinimo?.centavos).toBe(162100);
    expect(s.falhas.map((x) => x.fonte).some((x) => x.includes("IPCA (SGS 433)"))).toBe(true);
    expect(s.falhas.some((x) => x.fonte.includes("IGP-DI"))).toBe(true);
    // somente GET público, sem cookies/credenciais e sem dados do usuário
    for (const c of chamadas) {
      expect(c.url.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.")).toBe(true);
      expect(c.init?.credentials).toBe("omit");
      expect(c.init?.method).toBe("GET");
      expect(c.init?.headers).toBeUndefined();
      expect(c.init?.body).toBeUndefined();
    }
  });

  it("timeout aborta a requisição", async () => {
    const f: FetchLike = (_u, init) =>
      new Promise((_res, rej) => {
        init?.signal?.addEventListener("abort", () => rej(new Error("abortado")));
      });
    await expect(buscarJson("https://api.bcb.gov.br/x", { fetch: f, timeoutMs: 20 })).rejects.toThrow("tempo esgotado");
  });
});

const B = (dataHora: string, venda: number, tipo: string, compra = venda - 0.002) => ({ cotacaoCompra: compra, cotacaoVenda: venda, paridadeCompra: 1, paridadeVenda: 1, dataHoraCotacao: `${dataHora}.600448`, tipoBoletim: tipo });

describe("PTAX", () => {
  it("monta a URL com datas MM-DD-AAAA e $orderby", () => {
    const u = urlPtax("GBP", new Date(2026, 9, 18), AGORA);
    expect(dataPtax(AGORA)).toBe("10-28-2026");
    expect(u).toContain("@moeda='GBP'&@dataInicial='10-18-2026'&@dataFinalCotacao='10-28-2026'");
    expect(u).toContain("$orderby=dataHoraCotacao%20desc&$format=json");
    expect(u.startsWith("https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/")).toBe(true);
  });

  it("variação do fechamento contra o fechamento anterior (amostra GBP)", () => {
    const json = {
      value: [
        B("2026-10-28 13:05:31", 7.1059, "Fechamento", 7.1041),
        B("2026-10-28 13:05:31", 7.1402, "Intermediário"),
        B("2026-10-27 13:05:00", 7.0, "Fechamento"),
        B("2026-10-27 10:05:00", 6.9, "Intermediário"),
        B("2026-10-26 13:05:00", 6.5, "Fechamento"),
      ],
    };
    const c = calcularCotacao("GBP", parseBoletins(json, HOJE))!;
    expect(c.tipo).toBe("Fechamento");
    expect(c.intermediario).toBe(false);
    expect(c.venda).toBe(7.1059);
    expect(c.compra).toBe(7.1041);
    expect(c.variacaoPct).toBeCloseTo((7.1059 / 7.0 - 1) * 100, 8);
    expect(c.dataHora).toBe("2026-10-28 13:05:31");
  });

  it("só intermediário no dia corrente: mostra rotulado, variação vs último fechamento", () => {
    const json = { value: [B("2026-10-28 11:07:12", 5.4, "Intermediário"), B("2026-10-27 13:05:00", 5.0, "Fechamento"), B("2026-10-26 13:05:00", 4.0, "Fechamento")] };
    const c = calcularCotacao("USD", parseBoletins(json, HOJE))!;
    expect(c.intermediario).toBe(true);
    expect(c.tipo).toBe("Intermediário");
    expect(c.variacaoPct).toBeCloseTo(8, 8);
  });

  it("sem fechamento anterior: variação nula; sem dados: null; descarta lixo e futuro", () => {
    expect(calcularCotacao("USD", parseBoletins({ value: [B("2026-10-27 13:05:00", 5.0, "Fechamento")] }, HOJE))!.variacaoPct).toBeNull();
    expect(calcularCotacao("USD", [])).toBeNull();
    const l = parseBoletins({ value: [B("2026-10-29 13:05:00", 5, "Fechamento"), B("2026-10-27 13:05:00", -1, "Fechamento"), B("2026-10-27 13:05:00", 0, "Fechamento"), B("2026-10-27 13:05:00", 5, "Outro"), B("2026-10-27 13:05:00", 5, "Fechamento")] }, HOJE);
    expect(l).toHaveLength(1);
    expect(parseBoletins("x", HOJE)).toEqual([]);
  });

  it("um fechamento por dia", () => {
    const b = parseBoletins({ value: [B("2026-10-27 13:05:00", 5.1, "Fechamento"), B("2026-10-27 13:00:00", 5.0, "Fechamento"), B("2026-10-26 13:00:00", 4.9, "Fechamento")] }, HOJE);
    expect(fechamentosPorDia(b).map((x) => x.venda)).toEqual([5.1, 4.9]);
  });

  it("buscarCotacoes: falha em uma moeda não derruba as outras", async () => {
    const f: FetchLike = (url) => {
      if (url.includes("'EUR'")) return resp({}, false, 503);
      if (url.includes("'JPY'")) return resp({ value: [] });
      return resp({ value: [B("2026-10-27 13:05:00", 5.34, "Fechamento"), B("2026-10-26 13:05:00", 5.3, "Fechamento")] });
    };
    const s = await buscarCotacoes(AGORA, { fetch: f });
    expect(s.cotacoes).toHaveLength(8);
    expect(s.falhas.map((x) => x.fonte)).toEqual(expect.arrayContaining([expect.stringContaining("EUR"), expect.stringContaining("JPY")]));
  });
});

describe("conversor", () => {
  it("R$ -> moeda e moeda -> R$ sem spread", () => {
    expect(reaisParaMoeda(1000, 5, 0)).toBeCloseTo(200, 10);
    expect(moedaParaReais(200, 5, 0)).toBeCloseTo(1000, 10);
  });
  it("com spread/IOF informado", () => {
    expect(moedaParaReais(100, 5.34, 6.38)).toBeCloseTo(100 * 5.34 * 1.0638, 8);
    expect(reaisParaMoeda(1000, 5, 10)).toBeCloseTo(1000 / 5.5, 10);
    expect(reaisParaMoeda(moedaParaReais(50, 7.1, 3), 7.1, 3)).toBeCloseTo(50, 10);
  });
  it("entradas inválidas dão NaN", () => {
    expect(reaisParaMoeda(100, 0, 0)).toBeNaN();
    expect(reaisParaMoeda(100, 5, -1)).toBeNaN();
    expect(reaisParaMoeda(100, 5, 101)).toBeNaN();
    expect(moedaParaReais(Number.NaN, 5, 0)).toBeNaN();
  });
});
