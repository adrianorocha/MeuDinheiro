import { cartaoIdDe } from "./calc";
import { toCents } from "./money";
import { chaveCategoria } from "./painel503020";
import type { Celula, Documento } from "./relatorios";
import { brlCentavos } from "./centavos";
import type { Dataset, Despesa } from "./types";

/**
 * R61 - Relatório de apoio ao Imposto de Renda (IRPF) por ano-calendário. Funções puras, em CENTAVOS; espelham
 * `domain/ImpostoRenda.kt` (mesmos cenários em `imposto.test.ts` e `ImpostoRendaTest`).
 *
 * NÃO é a declaração oficial nem aconselhamento tributário: é uma TRIAGEM feita só com o que foi lançado no app.
 * Tudo o que entra é REALIZADO (pago/recebido); o que ainda está pendente aparece à parte, como estimativa.
 */

// ---------------------------------------------------------------- constantes (UM lugar, conferir a cada ano)

/**
 * Limite anual de dedução com instrução, POR PESSOA (titular ou dependente), em centavos.
 * CONFERIR O LIMITE VIGENTE DO ANO-BASE na Receita Federal: o valor de referência abaixo (R$ 3.561,50) é o da
 * tabela vigente conhecida (anos-calendário 2015 a 2025) e pode mudar. O usuário pode alterá-lo na tela.
 */
export const LIMITE_EDUCACAO_PADRAO_CENTAVOS = 356_150;

/** Previdência complementar (PGBL): dedução limitada a 12% da renda tributável (base aproximada aqui). */
export const LIMITE_PGBL_PERCENTUAL = 12;

/** Contas com saldo abaixo disto normalmente não precisam constar em Bens e Direitos (conferir). */
export const SALDO_MINIMO_CONTA_CENTAVOS = 14_000;

export const AVISO_IR =
  "Isto NÃO é a declaração oficial nem aconselhamento tributário: é uma triagem feita só com os lançamentos do app. " +
  "Confira com os informes de rendimentos (empregador, bancos, corretoras), recibos e notas fiscais antes de declarar.";

// ---------------------------------------------------------------- tipos

export type ClasseRendimento = "TRIBUTAVEL" | "ISENTO" | "EXCLUSIVO" | "INVESTIMENTO" | "FORA" | "REVISAR";
export const CLASSES_RENDIMENTO: readonly ClasseRendimento[] = ["TRIBUTAVEL", "ISENTO", "EXCLUSIVO", "INVESTIMENTO", "FORA", "REVISAR"];
/** Classes que o usuário pode ESCOLHER para uma categoria (REVISAR é só o estado "ainda sem classificação"). */
export const CLASSES_ESCOLHA: readonly ClasseRendimento[] = ["TRIBUTAVEL", "ISENTO", "EXCLUSIVO", "INVESTIMENTO", "FORA"];

export const ROTULO_CLASSE: Record<ClasseRendimento, string> = {
  TRIBUTAVEL: "Tributável",
  ISENTO: "Isento / não tributável",
  EXCLUSIVO: "Tributação exclusiva/definitiva",
  INVESTIMENTO: "Investimentos (conferir informe do banco)",
  FORA: "Não é rendimento (fora)",
  REVISAR: "Sem classificação (revise)",
};

export type TipoDeducao = "SAUDE" | "EDUCACAO" | "PENSAO" | "PREVIDENCIA" | "DOACAO";
export const TIPOS_DEDUCAO: readonly TipoDeducao[] = ["SAUDE", "EDUCACAO", "PENSAO", "PREVIDENCIA", "DOACAO"];
export const ROTULO_DEDUCAO: Record<TipoDeducao, string> = {
  SAUDE: "Saúde",
  EDUCACAO: "Educação",
  PENSAO: "Pensão alimentícia",
  PREVIDENCIA: "Previdência privada (PGBL)",
  DOACAO: "Doações dedutíveis",
};

/** Configuração do usuário (vai em `configuracoes/geral` -> `ir`; chaves por `chaveCategoria`). */
export interface ConfigIR {
  rendimentos: Record<string, ClasseRendimento>;
  deducoes: Record<string, TipoDeducao | "NAO">;
  limiteEducacaoCentavos: number;
  /** Quantas pessoas (titular + dependentes) têm despesa de educação: o limite vale por pessoa. */
  pessoasEducacao: number;
}

export const CONFIG_IR_PADRAO: ConfigIR = {
  rendimentos: {},
  deducoes: {},
  limiteEducacaoCentavos: LIMITE_EDUCACAO_PADRAO_CENTAVOS,
  pessoasEducacao: 1,
};

/** Aceita qualquer coisa (JSON local/nuvem) e devolve uma configuração válida. */
export function normalizarConfigIR(entrada: unknown): ConfigIR {
  const e = (entrada && typeof entrada === "object" ? entrada : {}) as Record<string, unknown>;
  const rendimentos: Record<string, ClasseRendimento> = {};
  if (e.rendimentos && typeof e.rendimentos === "object") {
    for (const [k, v] of Object.entries(e.rendimentos as Record<string, unknown>)) {
      const chave = chaveCategoria(k);
      if (chave && (CLASSES_ESCOLHA as readonly unknown[]).includes(v)) rendimentos[chave] = v as ClasseRendimento;
    }
  }
  const deducoes: Record<string, TipoDeducao | "NAO"> = {};
  if (e.deducoes && typeof e.deducoes === "object") {
    for (const [k, v] of Object.entries(e.deducoes as Record<string, unknown>)) {
      const chave = chaveCategoria(k);
      if (chave && (v === "NAO" || (TIPOS_DEDUCAO as readonly unknown[]).includes(v))) deducoes[chave] = v as TipoDeducao | "NAO";
    }
  }
  const lim = e.limiteEducacaoCentavos;
  const pes = e.pessoasEducacao;
  return {
    rendimentos,
    deducoes,
    limiteEducacaoCentavos: typeof lim === "number" && Number.isInteger(lim) && lim >= 0 && lim <= 100_000_000 ? lim : LIMITE_EDUCACAO_PADRAO_CENTAVOS,
    pessoasEducacao: typeof pes === "number" && Number.isInteger(pes) && pes >= 1 && pes <= 20 ? pes : 1,
  };
}

// ---------------------------------------------------------------- classificação padrão por nome

const norm = (s: string) => chaveCategoria(s);
function palavras(chave: string): string[] {
  return chave.split(/[^a-z0-9]+/).filter(Boolean);
}

const FORA = ["reembolso", "reembolsos", "estorno", "estornos", "devolucao", "emprestimo", "emprestimos", "transferencia", "transferencias", "resgate", "resgates", "aporte", "aportes", "acerto", "acertos", "rateio", "ajuste", "troco"];
const INVEST = ["rendimento", "rendimentos", "juros", "dividendos", "dividendo", "proventos", "jcp", "cdb", "lci", "lca", "fii", "fiis", "acoes", "tesouro", "investimento", "investimentos", "poupanca"];
const TRIBUT = ["salario", "salarios", "ordenado", "prolabore", "ferias", "freelance", "freela", "comissao", "bonus", "honorarios", "autonomo", "aluguel", "alugueis"];
const ISENTO = ["heranca", "fgts"];
const EXCLUSIVO = ["plr"];

/** Classe padrão de um rendimento pelo NOME (categoria; na falta, a descrição). `REVISAR` = não sei. */
export function classificarRendimento(categoria: string, descricao = "", overrides: Readonly<Record<string, ClasseRendimento>> = {}): ClasseRendimento {
  const chave = norm(categoria);
  const o = chave ? overrides[chave] : undefined;
  if (o && (CLASSES_ESCOLHA as readonly string[]).includes(o)) return o;
  const porNome = (t: string): ClasseRendimento | null => {
    const c = norm(t);
    if (!c) return null;
    const ps = palavras(c);
    const tem = (lista: readonly string[]) => ps.some((p) => lista.includes(p));
    // doação RECEBIDA é isenta; "pro labore" vira duas palavras
    if (c.replace(/[\s-]/g, "").includes("prolabore")) return "TRIBUTAVEL";
    if (c.includes("doacao recebida") || c.includes("doacoes recebidas")) return "ISENTO";
    if (tem(FORA)) return "FORA";
    if (tem(INVEST)) return "INVESTIMENTO";
    if (/(^|[^0-9])13([^0-9]|$)/.test(c) || c.includes("decimo terceiro") || tem(EXCLUSIVO)) return "EXCLUSIVO";
    if (tem(TRIBUT)) return "TRIBUTAVEL";
    if (tem(ISENTO)) return "ISENTO";
    return null;
  };
  return porNome(categoria) ?? porNome(descricao) ?? "REVISAR";
}

const SAUDE = ["saude", "medico", "medicos", "consulta", "consultas", "dentista", "odontologico", "psicologo", "psicologa", "psicologia", "terapia", "fisioterapia", "fonoaudiologia", "plano", "convenio", "exame", "exames", "laboratorio", "hospital", "clinica", "cirurgia", "nutricionista", "pediatra", "dermatologista", "oftalmologista", "ortopedista", "cardiologista"];
const EDUCACAO = ["educacao", "escola", "faculdade", "universidade", "colegio", "creche", "graduacao", "mestrado", "doutorado", "ensino"];
const PENSAO = ["pensao"];
const PREVIDENCIA = ["previdencia", "pgbl", "funpresp"];
const DOACAO = ["doacao", "doacoes"];
/** Itens de "Saúde" que a Receita normalmente NÃO aceita (não entram no total; ficam listados com aviso). */
const NAO_DEDUTIVEL = ["farmacia", "drogaria", "remedio", "remedios", "medicamento", "medicamentos", "oculos", "lente", "lentes", "suplemento", "suplementos", "vitamina", "vitaminas"];

/** Tipo padrão de dedução pelo NOME da categoria; `null` = não candidata. */
export function classificarDeducao(categoria: string, overrides: Readonly<Record<string, TipoDeducao | "NAO">> = {}): TipoDeducao | null {
  const chave = norm(categoria);
  if (!chave) return null;
  const o = overrides[chave];
  if (o === "NAO") return null;
  if (o && (TIPOS_DEDUCAO as readonly string[]).includes(o)) return o;
  const ps = palavras(chave);
  const tem = (lista: readonly string[]) => ps.some((p) => lista.includes(p));
  if (tem(PENSAO)) return "PENSAO";
  if (tem(PREVIDENCIA)) return "PREVIDENCIA";
  if (tem(DOACAO)) return "DOACAO";
  if (tem(EDUCACAO)) return "EDUCACAO";
  if (tem(SAUDE)) return "SAUDE";
  return null;
}

/** O lançamento de saúde é de um tipo que normalmente não é dedutível (remédio, óculos…)? */
export function pareceNaoDedutivel(descricao: string): boolean {
  return palavras(norm(descricao)).some((p) => NAO_DEDUTIVEL.includes(p));
}

// ---------------------------------------------------------------- comprovante (campo `anexos`, R60)

/** "Tem comprovante?": o lançamento tem ao menos um anexo (R60). Dado antigo, sem o campo = sem comprovante. */
export function temComprovante(d: Despesa): boolean {
  return Array.isArray(d.anexos) && d.anexos.length > 0;
}

// ---------------------------------------------------------------- resultado

export interface ItemIR {
  id: number;
  data: number;
  descricao: string;
  categoria: string;
  centavos: number;
  comprovante: boolean;
  /** forma de pagamento: conta (número) ou "Cartão" */
  origem: string;
  /** aviso por item (ex.: medicamento) */
  atencao?: string;
}

export interface GrupoIR {
  categoria: string;
  chave: string;
  /** Rendimentos: classe efetiva; Deduções: ignorado (use `tipo`). */
  classe?: ClasseRendimento;
  tipo?: TipoDeducao;
  totalCentavos: number;
  itens: ItemIR[];
}

export interface TotalClasse {
  classe: ClasseRendimento;
  totalCentavos: number;
  quantidade: number;
}

export interface TotalDeducao {
  tipo: TipoDeducao;
  /** soma dos lançamentos (sem os "não dedutíveis") */
  totalCentavos: number;
  /** teto aplicado (educação por pessoa; PGBL 12%); null = sem limite calculado */
  limiteCentavos: number | null;
  /** o que entra: min(total, limite) */
  deduzivelCentavos: number;
  excessoCentavos: number;
}

export interface SaldoItem {
  nome: string;
  detalhe: string;
  centavos: number;
}

export interface RelatorioIR {
  ano: number;
  /** início/fim do ano-calendário (ms) */
  inicio: number;
  fim: number;
  rendimentos: {
    porClasse: TotalClasse[];
    grupos: GrupoIR[];
    totalRealizadoCentavos: number;
    /** receitas NORMAL ainda não recebidas no ano (estimativa; NÃO entram nos totais) */
    previstoCentavos: number;
  };
  deducoes: {
    porTipo: TotalDeducao[];
    grupos: GrupoIR[];
    naoDedutiveis: ItemIR[];
    naoDedutiveisCentavos: number;
    totalDeduzivelCentavos: number;
    /** candidatos ainda NÃO pagos no ano (estimativa; NÃO entram) */
    pendentesCentavos: number;
    semComprovante: number;
    quantidade: number;
    limiteEducacaoTotalCentavos: number;
    limitePgblCentavos: number;
    baseTributavelCentavos: number;
  };
  bens: {
    contas: SaldoItem[];
    totalContasCentavos: number;
    investimentos: SaldoItem[];
    totalInvestimentosCentavos: number;
    metas: SaldoItem[];
    totalMetasCentavos: number;
    dividas: SaldoItem[];
    totalDividasCentavos: number;
    /** não nulo quando investimentos/metas/dívidas mostram o saldo ATUAL e o ano consultado não é o ano anterior ao corrente */
    avisoSaldoAtual: string | null;
  };
  checagem: {
    /** receitas NORMAL pagas do ano, sem estorno de cartão (= R5 "receitas realizadas") */
    receitasRealizadasCentavos: number;
    somaClassesCentavos: number;
    confere: boolean;
    /** Σ por grupo = Σ por tipo (antes dos limites) */
    deducoesConferem: boolean;
  };
  /** rendimento tributável - deduções candidatas (APENAS indicativo; não é a base de cálculo oficial) */
  indicativoBaseCentavos: number;
  avisos: string[];
}

// ---------------------------------------------------------------- cálculo

function ehDoAno(d: Despesa, ini: number, fim: number): boolean {
  return d.data >= ini && d.data <= fim;
}

function origemDe(d: Despesa): string {
  return cartaoIdDe(d) !== null ? "Cartão" : d.conta;
}

function item(d: Despesa, centavos: number, atencao?: string): ItemIR {
  const base: ItemIR = {
    id: d.id,
    data: d.data,
    descricao: d.descricao,
    categoria: d.categoria.trim() || "Sem categoria",
    centavos,
    comprovante: temComprovante(d),
    origem: origemDe(d),
  };
  return atencao ? { ...base, atencao } : base;
}

function ordenarItens(a: ItemIR, b: ItemIR): number {
  return a.data - b.data || a.id - b.id;
}

/** Saldo de uma conta (número) ao FIM do instante `fimMs`: mesma regra de `saldoConta` (R3), só até a data. */
export function saldoContaEm(despesas: readonly Despesa[], numeroConta: string, fimMs: number): number {
  let total = 0;
  for (const d of despesas) {
    if (d.conta !== numeroConta || !d.pago || cartaoIdDe(d) !== null || d.data > fimMs) continue;
    total += d.tipo === "CREDITO" ? toCents(d.valor) : -toCents(d.valor);
  }
  return total;
}

export function relatorioIR(
  ds: Pick<Dataset, "despesas" | "contas" | "investimentos" | "metas" | "dividas">,
  ano: number,
  config: ConfigIR = CONFIG_IR_PADRAO,
  agora: number = Date.now(),
): RelatorioIR {
  const cfg = normalizarConfigIR(config);
  const ini = new Date(ano, 0, 1, 0, 0, 0, 0).getTime();
  const fim = new Date(ano, 11, 31, 23, 59, 59, 999).getTime();

  // ---- (a) rendimentos
  const gruposRend = new Map<string, GrupoIR>();
  const totais = new Map<ClasseRendimento, TotalClasse>(CLASSES_RENDIMENTO.map((c) => [c, { classe: c, totalCentavos: 0, quantidade: 0 }]));
  let previsto = 0;
  let receitas = 0;
  // ---- (b) deduções
  const gruposDed = new Map<string, GrupoIR>();
  const naoDed: ItemIR[] = [];
  let pendentes = 0;

  for (const d of ds.despesas) {
    if (d.natureza !== "NORMAL" || !ehDoAno(d, ini, fim)) continue;
    const c = toCents(d.valor);
    if (d.tipo === "CREDITO") {
      if (cartaoIdDe(d) !== null) continue; // estorno de cartão: não é rendimento
      if (!d.pago) {
        const cl = classificarRendimento(d.categoria, d.descricao, cfg.rendimentos);
        if (cl !== "FORA") previsto += c;
        continue;
      }
      receitas += c;
      const classe = classificarRendimento(d.categoria, d.descricao, cfg.rendimentos);
      const nome = d.categoria.trim() || "Sem categoria";
      const chave = `${classe}|${norm(nome)}`;
      const g = gruposRend.get(chave) ?? { categoria: nome, chave: norm(nome), classe, totalCentavos: 0, itens: [] };
      g.totalCentavos += c;
      g.itens.push(item(d, c));
      gruposRend.set(chave, g);
      const t = totais.get(classe)!;
      t.totalCentavos += c;
      t.quantidade += 1;
    } else {
      const tipo = classificarDeducao(d.categoria, cfg.deducoes);
      if (!tipo) continue;
      if (!d.pago) {
        if (!(tipo === "SAUDE" && pareceNaoDedutivel(d.descricao))) pendentes += c;
        continue;
      }
      if (tipo === "SAUDE" && pareceNaoDedutivel(d.descricao)) {
        naoDed.push(item(d, c, "Medicamentos, óculos e similares normalmente não são dedutíveis."));
        continue;
      }
      const nome = d.categoria.trim() || "Sem categoria";
      const chave = `${tipo}|${norm(nome)}`;
      const g = gruposDed.get(chave) ?? { categoria: nome, chave: norm(nome), tipo, totalCentavos: 0, itens: [] };
      g.totalCentavos += c;
      g.itens.push(item(d, c, tipo === "DOACAO" ? "Só vale para fundos e projetos incentivados (confira o recibo)." : undefined));
      gruposDed.set(chave, g);
    }
  }

  const ordenarGrupos = (m: Map<string, GrupoIR>): GrupoIR[] =>
    [...m.values()]
      .map((g) => ({ ...g, itens: [...g.itens].sort(ordenarItens) }))
      .sort((a, b) => b.totalCentavos - a.totalCentavos || a.categoria.localeCompare(b.categoria, "pt-BR"));
  const gRend = ordenarGrupos(gruposRend);
  const gDed = ordenarGrupos(gruposDed);
  const porClasse = CLASSES_RENDIMENTO.map((c) => totais.get(c)!);
  const somaClasses = porClasse.reduce((a, t) => a + t.totalCentavos, 0);
  const tributavel = totais.get("TRIBUTAVEL")!.totalCentavos;

  // ---- limites
  const limEduc = cfg.limiteEducacaoCentavos * cfg.pessoasEducacao;
  const limPgbl = Math.floor((tributavel * LIMITE_PGBL_PERCENTUAL) / 100);
  const porTipo: TotalDeducao[] = TIPOS_DEDUCAO.map((tipo) => {
    const total = gDed.filter((g) => g.tipo === tipo).reduce((a, g) => a + g.totalCentavos, 0);
    const limite = tipo === "EDUCACAO" ? limEduc : tipo === "PREVIDENCIA" ? limPgbl : null;
    const deduzivel = limite === null ? total : Math.min(total, limite);
    return { tipo, totalCentavos: total, limiteCentavos: limite, deduzivelCentavos: deduzivel, excessoCentavos: total - deduzivel };
  });
  const totalDeduzivel = porTipo.reduce((a, t) => a + t.deduzivelCentavos, 0);
  const somaTipos = porTipo.reduce((a, t) => a + t.totalCentavos, 0);
  const somaGrupos = gDed.reduce((a, g) => a + g.totalCentavos, 0);
  const todosItens = gDed.flatMap((g) => g.itens);

  // ---- (c) bens e dívidas
  const contas: SaldoItem[] = ds.contas
    .map((c) => ({ nome: c.banco, detalhe: `conta ${c.conta}`, centavos: saldoContaEm(ds.despesas, c.conta, fim) }))
    .sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR") || a.detalhe.localeCompare(b.detalhe));
  const investimentos: SaldoItem[] = ds.investimentos.map((i) => ({ nome: i.nome, detalhe: i.tipo, centavos: toCents(i.valorAtual) }));
  const metas: SaldoItem[] = ds.metas.map((m) => ({ nome: m.nome, detalhe: "meta", centavos: toCents(m.valorGuardado) }));
  const dividas: SaldoItem[] = ds.dividas
    .filter((d) => d.ativa)
    .map((d) => ({ nome: d.nome, detalhe: d.credor?.trim() ? d.credor.trim() : d.tipo, centavos: toCents(d.saldoDevedor) }));
  const soma = (l: SaldoItem[]) => l.reduce((a, x) => a + x.centavos, 0);
  const anoCorrente = new Date(agora).getFullYear();
  const mostraAtual = investimentos.length + metas.length + dividas.length > 0;
  const avisoSaldoAtual =
    mostraAtual && ano !== anoCorrente - 1
      ? `Investimentos, metas e dívidas mostram o saldo ATUAL, não o de 31/12/${ano}. Para declarar ${ano}, use o saldo de 31/12/${ano} do informe do banco ou da corretora.`
      : null;

  const avisos: string[] = [];
  if (ano > anoCorrente) avisos.push(`O ano ${ano} ainda não começou: os valores são só o que já foi lançado.`);
  else if (ano === anoCorrente) avisos.push(`O ano ${ano} ainda está em andamento: os totais são parciais.`);
  if (totais.get("REVISAR")!.quantidade > 0) avisos.push("Há receitas sem classificação: escolha a classe delas para o total de rendimentos fazer sentido.");
  if (naoDed.length > 0) avisos.push("Alguns itens de saúde (remédios, óculos) ficaram fora do total por normalmente não serem dedutíveis.");
  if (porTipo.some((t) => t.excessoCentavos > 0)) avisos.push("Parte das despesas passou do limite de dedução e não entra no total deduzível.");
  avisos.push("Estornos de cartão não são abatidos das despesas candidatas: confira os recibos.");
  avisos.push(`Valores de referência (limite de educação, PGBL ${LIMITE_PGBL_PERCENTUAL}%) podem mudar: confirme na Receita Federal.`);

  return {
    ano,
    inicio: ini,
    fim,
    rendimentos: { porClasse, grupos: gRend, totalRealizadoCentavos: somaClasses, previstoCentavos: previsto },
    deducoes: {
      porTipo,
      grupos: gDed,
      naoDedutiveis: naoDed.sort(ordenarItens),
      naoDedutiveisCentavos: naoDed.reduce((a, i) => a + i.centavos, 0),
      totalDeduzivelCentavos: totalDeduzivel,
      pendentesCentavos: pendentes,
      semComprovante: todosItens.filter((i) => !i.comprovante).length,
      quantidade: todosItens.length,
      limiteEducacaoTotalCentavos: limEduc,
      limitePgblCentavos: limPgbl,
      baseTributavelCentavos: tributavel,
    },
    bens: {
      contas,
      totalContasCentavos: soma(contas),
      investimentos,
      totalInvestimentosCentavos: soma(investimentos),
      metas,
      totalMetasCentavos: soma(metas),
      dividas,
      totalDividasCentavos: soma(dividas),
      avisoSaldoAtual,
    },
    checagem: {
      receitasRealizadasCentavos: receitas,
      somaClassesCentavos: somaClasses,
      confere: receitas === somaClasses,
      deducoesConferem: somaTipos === somaGrupos,
    },
    indicativoBaseCentavos: tributavel - totalDeduzivel,
    avisos,
  };
}

// ---------------------------------------------------------------- exportação (PDF/CSV pelos mesmos utilitários do R20)

const dataBr = (ms: number) => new Date(ms).toLocaleDateString("pt-BR");
const reais = (c: number) => c / 100;

/**
 * Documento (R20) com o resumo, o aviso e UMA tabela com todas as listas (rendimentos, deduções, bens/dívidas), pronta
 * para `gerarPdf` e `csvDeDocumento` sem alterar os relatórios existentes.
 */
export function documentoIR(r: RelatorioIR): Documento {
  const linhas: Celula[][] = [];
  const add = (secao: string, data: string, desc: string, cat: string, info: string, comp: string, valor: number) =>
    linhas.push([secao, data, desc, cat, info, comp, reais(valor)]);

  for (const g of r.rendimentos.grupos) {
    for (const i of g.itens) add("Rendimentos", dataBr(i.data), i.descricao, g.categoria, ROTULO_CLASSE[g.classe ?? "REVISAR"], "", i.centavos);
  }
  for (const g of r.deducoes.grupos) {
    for (const i of g.itens) {
      add("Deduções candidatas", dataBr(i.data), i.descricao, g.categoria, ROTULO_DEDUCAO[g.tipo as TipoDeducao], i.comprovante ? "Sim" : "Não", i.centavos);
    }
  }
  for (const i of r.deducoes.naoDedutiveis) add("Fora das deduções (provável não dedutível)", dataBr(i.data), i.descricao, i.categoria, "Saúde", i.comprovante ? "Sim" : "Não", i.centavos);
  for (const s of r.bens.contas) add("Saldo das contas em 31/12", "", s.nome, "", s.detalhe, "", s.centavos);
  for (const s of r.bens.investimentos) add("Investimentos (saldo atual)", "", s.nome, "", s.detalhe, "", s.centavos);
  for (const s of r.bens.metas) add("Metas (saldo atual)", "", s.nome, "", s.detalhe, "", s.centavos);
  for (const s of r.bens.dividas) add("Dívidas (saldo devedor atual)", "", s.nome, "", s.detalhe, "", s.centavos);

  const totais: { rotulo: string; valor: string }[] = [
    ...r.rendimentos.porClasse.filter((t) => t.totalCentavos !== 0).map((t) => ({ rotulo: ROTULO_CLASSE[t.classe], valor: brlCentavos(t.totalCentavos) })),
    ...r.deducoes.porTipo.filter((t) => t.totalCentavos !== 0).map((t) => ({ rotulo: `${ROTULO_DEDUCAO[t.tipo]} (dedutível)`, valor: brlCentavos(t.deduzivelCentavos) })),
    { rotulo: "Total de deduções candidatas", valor: brlCentavos(r.deducoes.totalDeduzivelCentavos) },
    { rotulo: "Saldo das contas em 31/12", valor: brlCentavos(r.bens.totalContasCentavos) },
  ];
  const filtros = [AVISO_IR, ...(r.bens.avisoSaldoAtual ? [r.bens.avisoSaldoAtual] : []), ...r.avisos];
  return {
    titulo: `Apoio ao Imposto de Renda - ano-calendário ${r.ano}`,
    rotuloFiltros: "Aviso e premissas",
    filtros,
    totais,
    colunas: ["Seção", "Data", "Descrição", "Categoria", "Classe/Tipo", "Comprovante", "Valor"],
    colunasMoeda: [6],
    linhas,
    grafico: null,
    nomeArquivo: `imposto-de-renda-${r.ano}`,
  };
}

/** Anos para escolher na tela: o ano anterior ao corrente (o da declaração) e os que têm lançamentos. */
export function anosComDados(despesas: readonly Despesa[], agora: number): number[] {
  const set = new Set<number>([new Date(agora).getFullYear() - 1]);
  for (const d of despesas) set.add(new Date(d.data).getFullYear());
  return [...set].sort((a, b) => b - a);
}
