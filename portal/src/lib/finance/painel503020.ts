import { cartaoIdDe } from "./calc";
import { brlCentavos, decimosDe, decimosParaTexto } from "./centavos";
import { fimDoMes, inicioDoMes, mesAnoDe, normalizaMes } from "./dates";
import { toCents } from "./money";
import { normalizar } from "./texto";
import type { Despesa, Meta } from "./types";

/**
 * R54 - Painel 50-30-20 por categoria e reserva de emergência (cobertura em meses das despesas essenciais).
 * Funções puras, em centavos; espelham `domain/Painel503020.kt`. A classificação é por NOME de categoria (sem
 * acento, sem diferenciar maiúsculas) com sobrescrita local do usuário. Não altera R25/R29.
 */

export type GrupoPainel = "NECESSIDADES" | "DESEJOS" | "POUPANCA";
export type StatusGrupoPainel = "OK" | "ACIMA" | "ABAIXO" | "SEM_RECEITA";

export const GRUPOS_PAINEL: readonly GrupoPainel[] = ["NECESSIDADES", "DESEJOS", "POUPANCA"];

// ---------------------------------------------------------------- mapa padrão (nomes normalizados)

const NECESSIDADES = [
  // categorias padrão do app/portal
  "alimentacao", "supermercado", "transporte", "combustivel", "oficina", "saude", "educacao", "casa",
  // alimentação básica e casa
  "mercado", "padaria", "acougue", "hortifruti", "feira", "aluguel", "condominio", "moradia", "habitacao", "luz",
  "energia", "energia eletrica", "agua", "esgoto", "gas", "internet", "telefone", "celular", "contas", "contas basicas",
  "contas da casa", "reforma", "limpeza",
  // transporte
  "onibus", "taxi", "uber", "app de transporte", "estacionamento", "pedagio", "manutencao do carro", "manutencao carro",
  "bicicleta", "metro", "metro e trem", "trem",
  // saúde
  "farmacia", "remedios", "medicamentos", "dentista", "consulta", "consulta medica", "plano de saude", "convenio",
  "terapia", "oculos",
  // educação
  "escola", "faculdade", "mensalidade", "livros", "cursos", "material escolar",
  // família, serviços e impostos
  "pet", "veterinario", "crianca", "familia", "filhos", "escritorio", "lavanderia", "correios", "cartorio", "advogado",
  "contador", "seguro", "seguros", "imposto", "impostos", "ipva", "iptu", "irpf", "taxas bancarias", "taxas", "tarifas",
  "multa", "multas", "imprevisto", "imprevistos",
];

const DESEJOS = [
  // categorias padrão
  "lazer", "compras", "cinema", "academia", "jogos", "bebidas", "lanche",
  // alimentação fora, bem-estar, casa supérflua
  "restaurante", "restaurantes", "lanches", "delivery", "ifood", "bar", "cafe", "pizza", "sorvete", "fast food", "fastfood",
  "bebida", "moveis", "eletrodomesticos", "ferramentas", "jardim", "exercicio", "beleza", "cabelo", "salao", "estetica",
  // lazer e compras
  "festa", "musica", "esporte", "esportes", "fotografia", "camera", "streaming", "shows", "shows e eventos", "parque",
  "parques e passeios", "roupas", "vestuario", "calcados", "eletronicos", "acessorios", "compras online", "brinquedos",
  "assinatura", "assinaturas", "nuvem", "nuvem e armazenamento", "aplicativos", "entretenimento", "hobbies", "favorito",
  // viagens, presentes e doações
  "viagem", "viagens", "hotel", "aviao", "praia", "passeios", "mapa e passeios", "camping", "presente", "presentes",
  "doacao", "doacoes", "igreja", "flores", "aniversario",
];

const POUPANCA = [
  "reserva", "poupanca", "investimento", "investimentos", "aporte", "aportes", "emprestimo", "emprestimos",
  "financiamento", "divida", "dividas", "quitacao de divida", "juros",
];

const MAPA: Map<string, GrupoPainel> = (() => {
  const m = new Map<string, GrupoPainel>();
  for (const n of NECESSIDADES) m.set(n, "NECESSIDADES");
  for (const n of DESEJOS) m.set(n, "DESEJOS");
  for (const n of POUPANCA) m.set(n, "POUPANCA");
  return m;
})();

/** Chave canônica de uma categoria (sem acento, minúsculas, `_` vira espaço). */
export function chaveCategoria(nome: string): string {
  return normalizar(nome.replace(/_/g, " "));
}

/**
 * Grupo da categoria: 1) sobrescrita do usuário; 2) nome inteiro no mapa padrão; 3) palavras do nome, só se todas
 * as que casam apontarem para o MESMO grupo; senão `null` (não classificada).
 */
export function classificarCategoria(nome: string, overrides: Readonly<Record<string, GrupoPainel>> = {}): GrupoPainel | null {
  const chave = chaveCategoria(nome);
  if (!chave) return null;
  const o = overrides[chave];
  if (o === "NECESSIDADES" || o === "DESEJOS" || o === "POUPANCA") return o;
  const direto = MAPA.get(chave);
  if (direto) return direto;
  const grupos = new Set<GrupoPainel>();
  for (const t of chave.split(/[^a-z]+/)) {
    if (t.length < 3) continue;
    const g = MAPA.get(t);
    if (g) grupos.add(g);
  }
  return grupos.size === 1 ? [...grupos][0] : null;
}

// ---------------------------------------------------------------- configuração local

export interface ConfigPainel {
  metas: { necessidades: number; desejos: number; poupanca: number };
  /** meta de cobertura da reserva, em meses (1 a 36) */
  mesesReserva: number;
  /** chave da categoria (`chaveCategoria`) -> grupo escolhido pelo usuário */
  overrides: Record<string, GrupoPainel>;
}

export const MESES_RESERVA_PADRAO = 6;

export const CONFIG_PADRAO: ConfigPainel = {
  metas: { necessidades: 50, desejos: 30, poupanca: 20 },
  mesesReserva: MESES_RESERVA_PADRAO,
  overrides: {},
};

function inteiroEntre(v: unknown, min: number, max: number, padrao: number): number {
  return typeof v === "number" && Number.isInteger(v) && v >= min && v <= max ? v : padrao;
}

/** Aceita qualquer coisa (JSON lido do armazenamento) e devolve uma configuração válida. */
export function normalizarConfig(entrada: unknown): ConfigPainel {
  const e = (entrada && typeof entrada === "object" ? entrada : {}) as Record<string, unknown>;
  const m = (e.metas && typeof e.metas === "object" ? e.metas : {}) as Record<string, unknown>;
  const overrides: Record<string, GrupoPainel> = {};
  if (e.overrides && typeof e.overrides === "object") {
    for (const [k, v] of Object.entries(e.overrides as Record<string, unknown>)) {
      const chave = chaveCategoria(k);
      if (chave && (v === "NECESSIDADES" || v === "DESEJOS" || v === "POUPANCA")) overrides[chave] = v;
    }
  }
  return {
    metas: {
      necessidades: inteiroEntre(m.necessidades, 0, 100, 50),
      desejos: inteiroEntre(m.desejos, 0, 100, 30),
      poupanca: inteiroEntre(m.poupanca, 0, 100, 20),
    },
    mesesReserva: inteiroEntre(e.mesesReserva, 1, 36, MESES_RESERVA_PADRAO),
    overrides,
  };
}

// ---------------------------------------------------------------- painel do mês

export interface CategoriaValor {
  nome: string;
  centavos: number;
}

export interface GrupoResultado {
  grupo: GrupoPainel;
  totalCentavos: number;
  /** % da receita realizada em décimos (250 = 25,0%); null sem receita */
  percentualDecimos: number | null;
  metaPercentual: number;
  status: StatusGrupoPainel;
  categorias: CategoriaValor[];
}

export interface Painel503020 {
  mes: number;
  ano: number;
  receitaCentavos: number;
  /** despesas comuns do mês (igual a `kpisDoMes().despesasTotal`), incluindo as não classificadas */
  despesasCentavos: number;
  necessidades: GrupoResultado;
  desejos: GrupoResultado;
  poupanca: GrupoResultado;
  /** parte da poupança vinda de categorias de poupança/dívida (já dentro de `despesasCentavos`) */
  poupancaCategoriasCentavos: number;
  /** aportes - resgates de metas (fora de `despesasCentavos`; pode ser negativo) */
  poupancaMetasCentavos: number;
  naoClassificadoCentavos: number;
  naoClassificadas: CategoriaValor[];
  dicas: string[];
}

function ordenarCategorias(m: Map<string, CategoriaValor>): CategoriaValor[] {
  return [...m.entries()]
    .filter(([, v]) => v.centavos !== 0)
    .sort(([ka, a], [kb, b]) => b.centavos - a.centavos || (ka < kb ? -1 : ka > kb ? 1 : 0))
    .map(([, v]) => v);
}

/** Despesas comuns do período por categoria: débito soma, estorno de cartão abate (mesma regra de R5/R19). */
function porCategoria(despesas: readonly Despesa[], ini: number, fim: number): Map<string, CategoriaValor> {
  const out = new Map<string, CategoriaValor>();
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || d.data < ini || d.data > fim) continue;
    const c = toCents(d.valor);
    let contrib: number;
    if (d.tipo === "DEBITO") contrib = c;
    else if (cartaoIdDe(d) !== null) contrib = -c;
    else continue;
    const k = chaveCategoria(d.categoria);
    const nome = d.categoria.trim() || "Sem categoria";
    const g = out.get(k) ?? { nome, centavos: 0 };
    g.centavos += contrib;
    out.set(k, g);
  }
  return out;
}

function receitaRealizada(despesas: readonly Despesa[], ini: number, fim: number): number {
  let r = 0;
  for (const d of despesas) {
    if (d.natureza === "NORMAL" && d.tipo === "CREDITO" && d.pago && cartaoIdDe(d) === null && d.data >= ini && d.data <= fim) r += toCents(d.valor);
  }
  return r;
}

export function painel503020(despesas: readonly Despesa[], mes: number, ano: number, config: ConfigPainel = CONFIG_PADRAO): Painel503020 {
  const ini = inicioDoMes(mes, ano);
  const fim = fimDoMes(mes, ano);
  const receita = receitaRealizada(despesas, ini, fim);
  const cats = porCategoria(despesas, ini, fim);

  const por: Record<GrupoPainel, Map<string, CategoriaValor>> = { NECESSIDADES: new Map(), DESEJOS: new Map(), POUPANCA: new Map() };
  const nao = new Map<string, CategoriaValor>();
  let despesasTotal = 0;
  for (const [k, v] of cats) {
    despesasTotal += v.centavos;
    const g = classificarCategoria(v.nome, config.overrides);
    (g ? por[g] : nao).set(k, v);
  }
  let metasNet = 0;
  for (const d of despesas) {
    if (d.data < ini || d.data > fim) continue;
    if (d.natureza === "APORTE_META") metasNet += toCents(d.valor);
    else if (d.natureza === "RESGATE_META") metasNet -= toCents(d.valor);
  }
  const soma = (m: Map<string, CategoriaValor>) => [...m.values()].reduce((a, v) => a + v.centavos, 0);
  const necC = soma(por.NECESSIDADES);
  const desC = soma(por.DESEJOS);
  const poupCatC = soma(por.POUPANCA);
  const naoC = soma(nao);
  const poupC = Math.max(0, poupCatC + metasNet);

  const metaDe = (g: GrupoPainel) => (g === "NECESSIDADES" ? config.metas.necessidades : g === "DESEJOS" ? config.metas.desejos : config.metas.poupanca);
  const grupo = (g: GrupoPainel, total: number, categorias: CategoriaValor[]): GrupoResultado => {
    const meta = metaDe(g);
    let status: StatusGrupoPainel;
    if (receita <= 0) status = "SEM_RECEITA";
    else if (g === "POUPANCA") status = total * 100 >= meta * receita ? "OK" : "ABAIXO";
    else status = total * 100 <= meta * receita ? "OK" : "ACIMA";
    return { grupo: g, totalCentavos: total, percentualDecimos: decimosDe(total, receita), metaPercentual: meta, status, categorias };
  };
  const catsPoup = ordenarCategorias(por.POUPANCA);
  if (metasNet !== 0) catsPoup.unshift({ nome: "Metas (aportes - resgates)", centavos: metasNet });
  const r: Painel503020 = {
    mes,
    ano,
    receitaCentavos: receita,
    despesasCentavos: despesasTotal,
    necessidades: grupo("NECESSIDADES", necC, ordenarCategorias(por.NECESSIDADES)),
    desejos: grupo("DESEJOS", desC, ordenarCategorias(por.DESEJOS)),
    poupanca: grupo("POUPANCA", poupC, catsPoup),
    poupancaCategoriasCentavos: poupCatC,
    poupancaMetasCentavos: metasNet,
    naoClassificadoCentavos: naoC,
    naoClassificadas: ordenarCategorias(nao),
    dicas: [],
  };
  r.dicas = dicasDoPainel(r);
  return r;
}

function dicasDoPainel(r: Painel503020): string[] {
  const d: string[] = [];
  if (r.receitaCentavos <= 0) {
    d.push("Sem receita realizada neste mês: registre (ou marque como recebidas) suas entradas para ver os percentuais.");
  } else {
    const g = r.necessidades;
    if (g.status === "ACIMA") {
      const excesso = g.totalCentavos - Math.floor((g.metaPercentual * r.receitaCentavos) / 100);
      const top = g.categorias[0];
      d.push(`Necessidades acima de ${g.metaPercentual}% da receita (${brlCentavos(excesso)} a mais).${top ? ` Maior peso: ${top.nome} (${brlCentavos(top.centavos)}).` : ""} Renegociar contas fixas costuma ajudar.`);
    }
    const ds = r.desejos;
    if (ds.status === "ACIMA") {
      const excesso = ds.totalCentavos - Math.floor((ds.metaPercentual * r.receitaCentavos) / 100);
      const top = ds.categorias[0];
      d.push(`Desejos acima de ${ds.metaPercentual}% da receita (${brlCentavos(excesso)} a mais).${top ? ` Comece revisando ${top.nome} (${brlCentavos(top.centavos)}).` : ""}`);
    }
    const p = r.poupanca;
    if (p.status === "ABAIXO") {
      const falta = Math.ceil((p.metaPercentual * r.receitaCentavos) / 100) - p.totalCentavos;
      d.push(`Poupança abaixo de ${p.metaPercentual}% da receita: faltam ${brlCentavos(falta)} para chegar à meta. Automatize um aporte logo após receber.`);
    }
    if (r.necessidades.status === "OK" && ds.status === "OK" && p.status === "OK") {
      d.push("Seu mês está dentro da regra 50-30-20. Continue assim!");
    }
  }
  if (r.naoClassificadas.length > 0) {
    d.push(`${r.naoClassificadas.length} categoria(s) sem classificação (${brlCentavos(r.naoClassificadoCentavos)}): ${r.naoClassificadas.map((c) => c.nome).join(", ")}. Classifique-as para os percentuais ficarem completos.`);
  }
  return d;
}

// ---------------------------------------------------------------- reserva de emergência (essenciais)

export type StatusReservaPainel = "CRITICO" | "ATENCAO" | "OK" | "SEM_DADOS";

export interface ReservaPainel {
  saldoContasCentavos: number;
  metasReservaCentavos: number;
  liquidezCentavos: number;
  /** média mensal de NECESSIDADES nos 3 meses anteriores com lançamentos; null sem dados */
  mediaEssenciaisCentavos: number | null;
  mesesCobertos: number | null;
  mesesMeta: number;
  metaCentavos: number | null;
  faltanteCentavos: number | null;
  status: StatusReservaPainel;
}

/** Soma (centavos) das metas cujo nome indica reserva/emergência (o modelo de Meta não tem tipo). */
export function metasDeReservaCentavos(metas: readonly Pick<Meta, "nome" | "valorGuardado">[]): number {
  return metas.filter((m) => /\b(reserva|emergencia)\b/.test(normalizar(m.nome))).reduce((a, m) => a + toCents(m.valorGuardado), 0);
}

export function reservaEssenciais(
  despesas: readonly Despesa[],
  saldoContasCentavos: number,
  metasReservaCentavos: number,
  agora: number,
  config: ConfigPainel = CONFIG_PADRAO,
): ReservaPainel {
  const { mes, ano } = mesAnoDe(agora);
  const totais: number[] = [];
  for (let i = 1; i <= 3; i++) {
    const p = normalizaMes(mes - i, ano);
    const ini = inicioDoMes(p.mes, p.ano);
    const fim = fimDoMes(p.mes, p.ano);
    if (!despesas.some((d) => d.natureza === "NORMAL" && d.data >= ini && d.data <= fim)) continue;
    let ess = 0;
    for (const v of porCategoria(despesas, ini, fim).values()) if (classificarCategoria(v.nome, config.overrides) === "NECESSIDADES") ess += v.centavos;
    totais.push(Math.max(0, ess));
  }
  const liquidez = saldoContasCentavos + metasReservaCentavos;
  const base = { saldoContasCentavos, metasReservaCentavos, liquidezCentavos: liquidez, mesesMeta: config.mesesReserva };
  if (totais.length === 0) return { ...base, mediaEssenciaisCentavos: null, mesesCobertos: null, metaCentavos: null, faltanteCentavos: null, status: "SEM_DADOS" };
  const n = totais.length;
  const media = Math.floor((2 * totais.reduce((a, b) => a + b, 0) + n) / (2 * n));
  if (media <= 0) return { ...base, mediaEssenciaisCentavos: media, mesesCobertos: null, metaCentavos: null, faltanteCentavos: null, status: "SEM_DADOS" };
  const meta = config.mesesReserva * media;
  const status: StatusReservaPainel = liquidez * 2 < meta ? "CRITICO" : liquidez < meta ? "ATENCAO" : "OK";
  return {
    ...base,
    mediaEssenciaisCentavos: media,
    mesesCobertos: liquidez / media,
    metaCentavos: meta,
    faltanteCentavos: Math.max(0, meta - liquidez),
    status,
  };
}

/** Texto curto (copiável) do painel. */
export function textoDoPainel(p: Painel503020): string {
  const f = (g: GrupoResultado, nome: string) =>
    `${nome}: ${brlCentavos(g.totalCentavos)}${g.percentualDecimos === null ? "" : ` (${decimosParaTexto(g.percentualDecimos)}% da receita, meta ${g.grupo === "POUPANCA" ? "pelo menos " : "até "}${g.metaPercentual}%)`}`;
  return [f(p.necessidades, "Necessidades"), f(p.desejos, "Desejos"), f(p.poupanca, "Poupança/dívidas")].join("\n");
}
