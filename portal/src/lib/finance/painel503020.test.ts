import { describe, expect, it } from "vitest";
import { kpisDoMes } from "./calc";
import { dataset, desp, dt } from "./fixtures";
import { toCents } from "./money";
import {
  CONFIG_PADRAO,
  classificarCategoria,
  metasDeReservaCentavos,
  normalizarConfig,
  painel503020,
  reservaEssenciais,
  type GrupoPainel,
} from "./painel503020";
import type { Despesa } from "./types";

// Mesmos cenários e números de app/src/test/java/com/meudinheiro/domain/Painel503020Test.kt.
let n = 9000;
const d = (data: number, descricao: string, categoria: string, valor: number, over: Partial<Despesa> = {}) =>
  desp({ id: n++, data, descricao, categoria, valor, ...over });

const CHAVES_R50 = [
  "mercado", "padaria", "cafe", "pizza", "delivery", "bar", "sorvete", "fastfood", "acougue", "hortifruti",
  "aluguel", "condominio", "luz", "agua", "gas", "internet", "telefone", "moveis", "reforma", "ferramentas", "jardim", "limpeza", "eletrodomesticos",
  "onibus", "taxi", "uber", "estacionamento", "pedagio", "manutencao_carro", "bicicleta", "metro",
  "farmacia", "dentista", "exercicio", "beleza", "cabelo", "terapia", "consulta", "oculos",
  "escola", "livros", "cursos", "material_escolar",
  "festa", "musica", "esporte", "camera", "streaming", "shows", "parque",
  "roupas", "calcados", "eletronicos", "acessorios", "compras_online",
  "pet", "veterinario", "crianca", "familia", "brinquedos",
  "investimento", "emprestimo", "juros", "escritorio",
  "assinatura", "lavanderia", "correios", "cartorio", "advogado", "contador", "seguro", "nuvem", "aplicativos",
  "viagem", "hotel", "aviao", "praia", "passeios", "camping",
  "presente", "doacao", "igreja", "flores", "aniversario",
  "imposto", "ipva", "iptu", "taxas_bancarias", "multa", "irpf",
  "favorito", "imprevisto",
];

describe("R54 classificação por nome", () => {
  it("casa o nome sem acento e sem diferenciar maiúsculas", () => {
    expect(classificarCategoria("Supermercado")).toBe("NECESSIDADES");
    expect(classificarCategoria("ALIMENTAÇÃO")).toBe("NECESSIDADES");
    expect(classificarCategoria("Restaurantes")).toBe("DESEJOS");
    expect(classificarCategoria("Lazer")).toBe("DESEJOS");
    expect(classificarCategoria("Reserva")).toBe("POUPANCA");
    expect(classificarCategoria("Taxas bancárias")).toBe("NECESSIDADES");
    expect(classificarCategoria("manutencao_carro")).toBe("NECESSIDADES");
    expect(classificarCategoria("  Streaming ")).toBe("DESEJOS");
  });

  it("todas as 15 padrão e as chaves de ícones R50 que são despesa têm grupo", () => {
    for (const nome of ["Combustível", "Alimentação", "Transporte", "Compras", "Cinema", "Saúde", "Educação", "Oficina", "Supermercado", "Academia", "Jogos", "Bebidas", "Lanche", "Reserva"]) {
      expect(classificarCategoria(nome), nome).not.toBeNull();
    }
    expect(classificarCategoria("Salário")).toBeNull(); // receita
    for (const k of CHAVES_R50) expect(classificarCategoria(k), k).not.toBeNull();
  });

  it("palavra isolada só vale se não houver conflito; sem match = não classificada", () => {
    expect(classificarCategoria("Pet shop da esquina")).toBe("NECESSIDADES");
    expect(classificarCategoria("Casa de praia")).toBeNull(); // casa x praia
    expect(classificarCategoria("Outros")).toBeNull();
    expect(classificarCategoria("")).toBeNull();
    expect(classificarCategoria("Coisas diversas")).toBeNull();
  });

  it("a sobrescrita do usuário vence o mapa padrão", () => {
    const ov: Record<string, GrupoPainel> = { outros: "DESEJOS", lazer: "NECESSIDADES" };
    expect(classificarCategoria("Outros", ov)).toBe("DESEJOS");
    expect(classificarCategoria("LAZER", ov)).toBe("NECESSIDADES");
  });

  it("normalizarConfig rejeita lixo e mantém padrões", () => {
    expect(normalizarConfig(null)).toEqual(CONFIG_PADRAO);
    const c = normalizarConfig({ metas: { necessidades: 40, desejos: 200, poupanca: "x" }, mesesReserva: 99, overrides: { Outros: "DESEJOS", ruim: "XYZ" } });
    expect(c.metas).toEqual({ necessidades: 40, desejos: 30, poupanca: 20 });
    expect(c.mesesReserva).toBe(6);
    expect(c.overrides).toEqual({ outros: "DESEJOS" });
  });
});

describe("R54 painel do mês (outubro/2026)", () => {
  const despesas = [
    d(dt(2026, 10, 2), "Salário", "Salário", 5000, { tipo: "CREDITO" }),
    d(dt(2026, 10, 3), "Freela", "Freelance", 1000, { tipo: "CREDITO" }),
    d(dt(2026, 10, 4), "A receber", "Salário", 500, { tipo: "CREDITO", pago: false }),
    d(dt(2026, 10, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 10, 6), "Mercado", "Supermercado", 800),
    d(dt(2026, 10, 7), "Conta de luz", "Luz", 200),
    d(dt(2026, 10, 8), "Almoço", "Restaurantes", 400),
    d(dt(2026, 10, 9), "Estorno", "Restaurantes", 50, { tipo: "CREDITO", cartaoId: 10, pago: false }),
    d(dt(2026, 10, 10), "Netflix", "Streaming", 100),
    d(dt(2026, 10, 11), "Cinema", "Lazer", 300),
    d(dt(2026, 10, 12), "CDB", "Investimentos", 600),
    d(dt(2026, 10, 13), "Ração", "Pet shop da esquina", 150),
    d(dt(2026, 10, 14), "Diversos", "Coisas Diversas", 120),
    d(dt(2026, 10, 15), "Aporte: Viagem", "Meta", 500, { natureza: "APORTE_META" }),
    d(dt(2026, 10, 16), "Resgate: Viagem", "Meta", 100, { natureza: "RESGATE_META", tipo: "CREDITO" }),
    d(dt(2026, 10, 17), "Fatura cartão", "Cartão", 1200, { natureza: "PAGAMENTO_FATURA" }),
    d(dt(2026, 10, 18), "Transf", "Transferência", 300, { natureza: "TRANSFERENCIA" }),
  ];
  const p = painel503020(despesas, 10, 2026);

  it("classifica e fecha as somas em centavos (fatura/transferência não contam)", () => {
    expect(p.receitaCentavos).toBe(600000);
    expect(p.necessidades.totalCentavos).toBe(265000);
    expect(p.desejos.totalCentavos).toBe(75000); // estorno de cartão abate Restaurantes (400 - 50)
    expect(p.poupancaCategoriasCentavos).toBe(60000);
    expect(p.poupancaMetasCentavos).toBe(40000);
    expect(p.poupanca.totalCentavos).toBe(100000);
    expect(p.naoClassificadoCentavos).toBe(12000);
    expect(p.despesasCentavos).toBe(412000);
    // nec + des + poupança de categorias + não classificado == despesas comuns do mês (KPI R5)
    expect(p.necessidades.totalCentavos + p.desejos.totalCentavos + p.poupancaCategoriasCentavos + p.naoClassificadoCentavos).toBe(toCents(kpisDoMes(despesas, 10, 2026).despesasTotal));
  });

  it("percentuais em décimos e status contra 50-30-20", () => {
    expect(p.necessidades.percentualDecimos).toBe(442);
    expect(p.desejos.percentualDecimos).toBe(125);
    expect(p.poupanca.percentualDecimos).toBe(167);
    expect([p.necessidades.status, p.desejos.status, p.poupanca.status]).toEqual(["OK", "OK", "ABAIXO"]);
    expect(p.necessidades.categorias.map((c) => c.nome)).toEqual(["Moradia", "Supermercado", "Luz", "Pet shop da esquina"]);
  });

  it("lista a categoria sem classificação e dá dicas", () => {
    expect(p.naoClassificadas).toEqual([{ nome: "Coisas Diversas", centavos: 12000 }]);
    expect(p.dicas).toEqual([
      "Poupança abaixo de 20% da receita: faltam R$ 200,00 para chegar à meta. Automatize um aporte logo após receber.",
      "1 categoria(s) sem classificação (R$ 120,00): Coisas Diversas. Classifique-as para os percentuais ficarem completos.",
    ]);
  });

  it("sobrescrita e metas personalizadas mudam o resultado", () => {
    const cfg = normalizarConfig({ metas: { necessidades: 40, desejos: 30, poupanca: 20 }, overrides: { "coisas diversas": "DESEJOS", streaming: "NECESSIDADES" } });
    const q = painel503020(despesas, 10, 2026, cfg);
    expect(q.naoClassificadas).toEqual([]);
    expect(q.desejos.totalCentavos).toBe(75000 + 12000 - 10000);
    expect(q.necessidades.totalCentavos).toBe(275000);
    expect(q.necessidades.status).toBe("ACIMA"); // 45,8% > 40%
    expect(q.necessidades.metaPercentual).toBe(40);
  });

  it("mês sem receita: percentuais nulos, sem divisão por zero", () => {
    const q = painel503020([d(dt(2026, 10, 5), "Mercado", "Supermercado", 800)], 10, 2026);
    expect(q.receitaCentavos).toBe(0);
    expect(q.necessidades.percentualDecimos).toBeNull();
    expect(q.necessidades.status).toBe("SEM_RECEITA");
    expect(q.dicas[0]).toBe("Sem receita realizada neste mês: registre (ou marque como recebidas) suas entradas para ver os percentuais.");
    expect(JSON.stringify(q)).not.toMatch(/NaN|Infinity/);
  });

  it("resgate maior que aporte não deixa a poupança negativa", () => {
    const q = painel503020(
      [d(dt(2026, 10, 2), "Salário", "Salário", 1000, { tipo: "CREDITO" }), d(dt(2026, 10, 3), "Resgate", "Meta", 300, { natureza: "RESGATE_META", tipo: "CREDITO" })],
      10,
      2026,
    );
    expect(q.poupancaMetasCentavos).toBe(-30000);
    expect(q.poupanca.totalCentavos).toBe(0);
  });
});

describe("R54 reserva de emergência (despesas essenciais)", () => {
  const AGORA = dt(2026, 10, 15);
  const despesas = [
    d(dt(2026, 7, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 7, 6), "Mercado", "Supermercado", 500),
    d(dt(2026, 7, 7), "Cinema", "Lazer", 300),
    d(dt(2026, 8, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 8, 6), "Mercado", "Supermercado", 700),
    d(dt(2026, 9, 5), "Aluguel", "Moradia", 1500),
    d(dt(2026, 9, 6), "Jantar", "Restaurantes", 100),
    d(dt(2026, 10, 1), "Mês atual não conta", "Supermercado", 9999),
  ];
  const metas = [
    { nome: "Reserva de emergência", valorGuardado: 1900 },
    { nome: "Viagem", valorGuardado: 5000 },
  ];

  it("meses de cobertura = (contas + metas de reserva) / média das essenciais dos 3 meses anteriores", () => {
    expect(metasDeReservaCentavos(metas)).toBe(190000);
    const r = reservaEssenciais(despesas, 800000, metasDeReservaCentavos(metas), AGORA);
    expect(r.mediaEssenciaisCentavos).toBe(190000);
    expect(r.liquidezCentavos).toBe(990000);
    expect(r.mesesCobertos).not.toBeNull();
    expect(Math.round((r.mesesCobertos as number) * 100)).toBe(521);
    expect(r.metaCentavos).toBe(1140000);
    expect(r.faltanteCentavos).toBe(150000);
    expect(r.status).toBe("ATENCAO");
  });

  it("crítico abaixo de metade da meta; ok com meta menor", () => {
    expect(reservaEssenciais(despesas, 400000, 0, AGORA).status).toBe("CRITICO");
    const ok = reservaEssenciais(despesas, 800000, 190000, AGORA, normalizarConfig({ mesesReserva: 3 }));
    expect(ok.status).toBe("OK");
    expect(ok.faltanteCentavos).toBe(0);
    expect(ok.metaCentavos).toBe(570000);
  });

  it("sem lançamentos nos 3 meses anteriores: sem dados", () => {
    const r = reservaEssenciais([], 500000, 0, AGORA);
    expect(r.status).toBe("SEM_DADOS");
    expect(r.mesesCobertos).toBeNull();
    expect(r.metaCentavos).toBeNull();
    // só despesas desejos: média essencial 0 também é sem dados (evita dividir por zero)
    const s = reservaEssenciais([d(dt(2026, 9, 5), "Cinema", "Lazer", 50)], 500000, 0, AGORA);
    expect(s.status).toBe("SEM_DADOS");
    expect(dataset().despesas).toEqual([]);
  });
});
