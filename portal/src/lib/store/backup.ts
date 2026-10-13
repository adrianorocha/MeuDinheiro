import { z } from "zod";
import { recalcularTudo } from "../finance/calc";
import { mesAnoDe } from "../finance/dates";
import { novoIdInt } from "../finance/ids";
import { round2 } from "../finance/money";
import type { Dataset, Entity } from "../finance/types";
import { NATUREZAS } from "../finance/types";
import { normalizarTags } from "../finance/tags";
import { normalizarGeral, type ConfigGeral } from "../finance/configuracoes";

const num = z.number().finite();
const id = num;
const str = z.string().default("");
const dataCampo = z.union([num, z.string()]);

const contaSchema = z.object({
  id: id.optional(),
  saldo: num.default(0),
  banco: str,
  pic: str,
  agencia: str,
  conta: str,
  titular: str,
});

const despesaSchema = z.object({
  id: id.optional(),
  descricao: str,
  valor: num,
  data: dataCampo.optional(),
  categoria: str,
  conta: str,
  pic: str,
  tipo: z.enum(["DEBITO", "CREDITO"]),
  mes: num.optional(),
  ano: num.optional(),
  cartaoId: num.nullish(),
  valorOriginal: num.optional(),
  moedaOriginal: z.string().optional(),
  cotacaoNaData: num.optional(),
  pago: z.boolean().optional(),
  natureza: z.enum(NATUREZAS).optional(),
  grupoId: z.string().nullish(),
  autor: z.string().nullish(),
  fitid: z.string().nullish(),
  conciliadoEm: num.nullish(),
  tags: z.array(z.string()).nullish(),
});

const despesaFixaSchema = z.object({
  id: id.optional(),
  descricao: str,
  valor: num,
  conta: str,
  categoria: str,
  pic: str,
  tipo: z.enum(["DEBITO", "CREDITO"]),
  diaVencimento: num,
  ultimaDataLancamento: dataCampo.nullish(),
  cartaoId: num.nullish(),
  pausada: z.boolean().nullish(),
  pausadaAte: num.nullish(),
});

const categoriaSchema = z.object({ id: id.optional(), nome: str, pic: str });
const orcamentoSchema = z.object({ id: id.optional(), categoria: str, valorLimite: num });
const metaSchema = z.object({
  id: id.optional(),
  nome: str,
  valorObjetivo: num,
  valorGuardado: num.default(0),
  icone: z.string().default("ic_savings"),
  dataAlvo: dataCampo.nullish(),
});
const investimentoSchema = z.object({
  id: id.optional(),
  nome: str,
  tipo: str,
  valorInvestido: num,
  valorAtual: num,
});
const cartaoSchema = z.object({
  id: id.optional(),
  nome: str,
  finalCartao: str,
  tipo: z.string().default("CRÉDITO"),
  limiteDisponivel: num.optional(),
  limiteTotal: num,
  diaFechamento: num,
  diaVencimento: num,
  contaId: num,
  cartaoPrincipalId: num.nullish(),
  limiteProprio: num.nullish(),
});
const agendadaSchema = z.object({
  id: id.optional(),
  dataAgendada: dataCampo,
  contaOrigem: str,
  contaDestino: str,
  valor: num,
  executada: z.boolean().default(false),
});
const patrimonioSchema = z.object({
  id: id.optional(),
  dataMillis: dataCampo,
  valorTotal: num,
  mesReferencia: str,
});
const transacaoSchema = z.object({
  id: id.optional(),
  descricao: str,
  valor: num,
  bancoNome: str,
  categoriaNome: str,
  categoriaCorHex: str,
  timestamp: num.optional(),
});

const regraCategoriaSchema = z.object({
  id: id.optional(),
  padrao: str,
  tipoMatch: z.enum(["CONTEM", "COMECA_COM", "IGUAL"]).default("CONTEM"),
  categoria: str,
  conta: z.string().nullish(),
  tipo: z.enum(["DEBITO", "CREDITO"]).nullish(),
  ativa: z.boolean().default(true),
  prioridade: num.default(0),
  criadaEm: num.optional(),
});

const projetoSchema = z.object({
  id: id.optional(),
  nome: str,
  tag: str,
  orcamento: num.nullish(),
  inicio: dataCampo.nullish(),
  fim: dataCampo.nullish(),
  ativo: z.boolean().default(true),
  cor: z.string().nullish(),
});

const TIPOS_DIVIDA = ["EMPRESTIMO", "FINANCIAMENTO", "CHEQUE_ESPECIAL", "CARTAO_ROTATIVO", "CONSIGNADO", "OUTRA"] as const;

/** R58 */
const dividaSchema = z.object({
  id: id.optional(),
  nome: str,
  tipo: z.string().optional(),
  credor: z.string().nullish(),
  saldoDevedor: num.default(0),
  valorOriginal: num.nullish(),
  taxaJurosMensal: num.nullish(),
  parcelaMinima: num.nullish(),
  parcelasRestantes: num.nullish(),
  diaVencimento: num.nullish(),
  conta: z.string().nullish(),
  ativa: z.boolean().default(true),
});

/** R59 */
const pessoaSchema = z.object({ id: id.optional(), nome: str, contato: z.string().nullish() });

const acertoSchema = z.object({
  id: id.optional(),
  pessoaId: num,
  tipo: z.string().optional(),
  valor: num.default(0),
  descricao: str,
  data: dataCampo.optional(),
  despesaId: num.nullish(),
  quitado: z.boolean().default(false),
  quitadoEm: dataCampo.nullish(),
  grupoRateio: z.string().nullish(),
});

const lixeiraSchema = z.object({
  id: id.optional(),
  tipo: z.string().default("DESPESA"),
  descricao: str,
  valor: num.default(0),
  excluidoEm: num,
  payload: z.string().default("{}"),
});

export const backupSchema = z.object({
  versaoBackup: num.optional(),
  contas: z.array(contaSchema).default([]),
  despesas: z.array(despesaSchema).default([]),
  despesasFixas: z.array(despesaFixaSchema).default([]),
  categorias: z.array(categoriaSchema).default([]),
  orcamentos: z.array(orcamentoSchema).default([]),
  metas: z.array(metaSchema).default([]),
  investimentos: z.array(investimentoSchema).default([]),
  cartoes: z.array(cartaoSchema).default([]),
  transferenciasAgendadas: z.array(agendadaSchema).default([]),
  patrimonio: z.array(patrimonioSchema).default([]),
  lixeira: z.array(lixeiraSchema).default([]),
  transacao: z.array(transacaoSchema).optional(),
  transacoes: z.array(transacaoSchema).optional(),
  regrasCategoria: z.array(regraCategoriaSchema).default([]),
  projetos: z.array(projetoSchema).default([]),
  /** R58/R59 - opcionais: backup antigo (sem estas chaves) vale como "sem dívidas/pessoas/acertos". */
  dividas: z.array(dividaSchema).default([]),
  pessoas: z.array(pessoaSchema).default([]),
  acertos: z.array(acertoSchema).default([]),
  /** R57 - configurações sincronizadas (objeto único; aceita qualquer coisa e normaliza). */
  configuracoes: z.unknown().optional(),
});

type Backup = z.infer<typeof backupSchema>;

/** Converte data (ms ou texto legado) em epoch ms; texto inválido vira `agora`. */
export function paraMillis(valor: number | string | null | undefined, agora: number): number {
  if (typeof valor === "number") return valor;
  if (typeof valor === "string") {
    const t = valor.trim();
    if (/^\d{10,}$/.test(t)) return Number(t);
    const p = Date.parse(t);
    return Number.isNaN(p) ? agora : p;
  }
  return agora;
}

/** Garante ids válidos e únicos; ids preservados sempre que possível (R: restore preserva ids). */
function comIds<T extends { id?: number }>(lista: readonly T[], gerar: (usados: Set<number>) => number): (T & Entity)[] {
  const usados = new Set<number>();
  for (const i of lista) if (i.id) usados.add(i.id);
  const vistos = new Set<number>();
  return lista.map((i) => {
    let novo = i.id;
    if (!novo || vistos.has(novo)) {
      novo = gerar(usados);
      usados.add(novo);
    }
    vistos.add(novo);
    return { ...i, id: novo } as T & Entity;
  });
}

export function normalizarBackup(b: Backup, agora: number): Dataset {
  const gerarInt = (usados: Set<number>) => novoIdInt(usados);
  const gerarLong = (usados: Set<number>) => {
    let n = agora;
    while (usados.has(n)) n += 1;
    return n;
  };

  const contas = comIds(b.contas, gerarInt).map((c) => ({ ...c, saldo: round2(c.saldo) }));
  const despesas = comIds(b.despesas, gerarLong).map((d) => {
    const data = paraMillis(d.data, agora);
    const { mes, ano } = mesAnoDe(data);
    const valor = round2(d.valor);
    return {
      id: d.id,
      descricao: d.descricao,
      valor,
      data,
      categoria: d.categoria,
      conta: d.conta,
      pic: d.pic,
      tipo: d.tipo,
      mes: d.mes && d.mes >= 1 && d.mes <= 12 ? d.mes : mes,
      ano: d.ano && d.ano > 1900 ? d.ano : ano,
      cartaoId: d.cartaoId ? d.cartaoId : null,
      valorOriginal: d.valorOriginal && d.valorOriginal > 0 ? d.valorOriginal : valor,
      moedaOriginal: d.moedaOriginal || "BRL",
      cotacaoNaData: d.cotacaoNaData && d.cotacaoNaData > 0 ? d.cotacaoNaData : 1,
      // v1 legado não tinha `pago`: lançamentos antigos já eram efetivados.
      pago: d.pago ?? true,
      natureza: d.natureza ?? "NORMAL",
      grupoId: d.grupoId ?? null,
      autor: d.autor ?? null,
      fitid: d.fitid ?? null,
      conciliadoEm: d.conciliadoEm ? d.conciliadoEm : null,
      ...(normalizarTags(d.tags).length > 0 ? { tags: normalizarTags(d.tags) } : {}),
    };
  });
  const ds: Dataset = {
    contas,
    despesas,
    despesasFixas: comIds(b.despesasFixas, gerarInt).map((f) => ({
      ...f,
      cartaoId: f.cartaoId ? f.cartaoId : null,
      pausada: f.pausada === true,
      pausadaAte: f.pausada === true && f.pausadaAte ? f.pausadaAte : null,
      ultimaDataLancamento:
        f.ultimaDataLancamento === null || f.ultimaDataLancamento === undefined
          ? null
          : paraMillis(f.ultimaDataLancamento, agora),
    })),
    categorias: comIds(b.categorias, gerarInt),
    orcamentos: comIds(b.orcamentos, gerarInt),
    metas: comIds(b.metas, gerarInt).map((m) => ({
      ...m,
      dataAlvo: m.dataAlvo === null || m.dataAlvo === undefined ? null : paraMillis(m.dataAlvo, agora),
    })),
    investimentos: comIds(b.investimentos, gerarInt),
    cartoes: comIds(b.cartoes, gerarInt).map((c) => ({
      ...c,
      limiteDisponivel: c.limiteDisponivel ?? c.limiteTotal,
      cartaoPrincipalId: c.cartaoPrincipalId ? c.cartaoPrincipalId : null,
      limiteProprio: c.limiteProprio ? c.limiteProprio : null,
    })),
    transferenciasAgendadas: comIds(b.transferenciasAgendadas, gerarInt).map((t) => ({
      ...t,
      dataAgendada: paraMillis(t.dataAgendada, agora),
    })),
    patrimonio: comIds(b.patrimonio, gerarInt).map((p) => ({ ...p, dataMillis: paraMillis(p.dataMillis, agora) })),
    lixeira: comIds(b.lixeira, gerarInt).map((l) => ({ ...l, tipo: "DESPESA" as const })),
    transacoes: comIds(b.transacao ?? b.transacoes ?? [], gerarInt).map((t) => ({
      ...t,
      timestamp: t.timestamp ?? agora,
    })),
    regrasCategoria: comIds(b.regrasCategoria, gerarInt).map((r) => ({
      id: r.id,
      padrao: r.padrao,
      tipoMatch: r.tipoMatch,
      categoria: r.categoria,
      conta: r.conta ? r.conta : null,
      tipo: r.tipo ?? null,
      ativa: r.ativa,
      prioridade: Math.trunc(r.prioridade),
      criadaEm: r.criadaEm ?? agora,
    })),
    projetos: comIds(b.projetos, gerarInt).map((p) => ({
      id: p.id,
      nome: p.nome,
      tag: p.tag,
      orcamento: p.orcamento != null && p.orcamento > 0 ? p.orcamento : null,
      inicio: p.inicio == null ? null : paraMillis(p.inicio, agora),
      fim: p.fim == null ? null : paraMillis(p.fim, agora),
      ativo: p.ativo,
      cor: p.cor ? p.cor : null,
    })),
    dividas: comIds(b.dividas, gerarInt).map((d) => ({
      id: d.id,
      nome: d.nome,
      tipo: (TIPOS_DIVIDA as readonly string[]).includes(d.tipo ?? "") ? (d.tipo as (typeof TIPOS_DIVIDA)[number]) : "OUTRA",
      credor: d.credor ? d.credor : null,
      saldoDevedor: round2(Math.max(0, d.saldoDevedor)),
      valorOriginal: d.valorOriginal != null && d.valorOriginal > 0 ? round2(d.valorOriginal) : null,
      taxaJurosMensal: d.taxaJurosMensal != null && d.taxaJurosMensal > 0 ? d.taxaJurosMensal : 0,
      parcelaMinima: d.parcelaMinima != null && d.parcelaMinima > 0 ? round2(d.parcelaMinima) : null,
      parcelasRestantes: d.parcelasRestantes != null && d.parcelasRestantes >= 0 ? Math.trunc(d.parcelasRestantes) : null,
      diaVencimento: d.diaVencimento != null && d.diaVencimento >= 1 && d.diaVencimento <= 31 ? Math.trunc(d.diaVencimento) : null,
      conta: d.conta ? d.conta : null,
      ativa: d.ativa,
    })),
    pessoas: comIds(b.pessoas, gerarInt).map((p) => ({ id: p.id, nome: p.nome, contato: p.contato ? p.contato : null })),
    acertos: comIds(b.acertos, gerarInt).map((a) => ({
      id: a.id,
      pessoaId: a.pessoaId,
      tipo: a.tipo === "A_PAGAR" ? ("A_PAGAR" as const) : ("A_RECEBER" as const),
      valor: round2(a.valor),
      descricao: a.descricao,
      data: paraMillis(a.data, agora),
      despesaId: a.despesaId ? a.despesaId : null,
      quitado: a.quitado,
      quitadoEm: a.quitadoEm == null ? null : paraMillis(a.quitadoEm, agora),
      grupoRateio: a.grupoRateio ? a.grupoRateio : null,
    })),
  };
  return ds;
}

export type ResultadoImport = { ok: true; dataset: Dataset; versao: number; configuracoes?: ConfigGeral } | { ok: false; erro: string };

/** Valida e converte o texto de um backup JSON (v2, ou v1 legado com `data` em texto). */
export function importarBackup(texto: string, agora: number = Date.now()): ResultadoImport {
  let json: unknown;
  try {
    json = JSON.parse(texto);
  } catch {
    return { ok: false, erro: "O arquivo não é um JSON válido." };
  }
  if (typeof json !== "object" || json === null || Array.isArray(json)) {
    return { ok: false, erro: "Formato de backup não reconhecido." };
  }
  const r = backupSchema.safeParse(json);
  if (!r.success) {
    const primeiro = r.error.issues[0];
    const caminho = primeiro ? primeiro.path.join(".") : "";
    return { ok: false, erro: `Backup inválido${caminho ? ` em "${caminho}"` : ""}: ${primeiro?.message ?? "erro desconhecido"}.` };
  }
  const config = r.data.configuracoes !== undefined ? normalizarGeral(r.data.configuracoes) : undefined;
  return {
    ok: true,
    dataset: recalcularTudo(normalizarBackup(r.data, agora)),
    versao: r.data.versaoBackup ?? 1,
    ...(config && Object.keys(config).length > 0 ? { configuracoes: config } : {}),
  };
}

/** Backup v2 compatível com o app Android. */
export function exportarBackup(ds: Dataset, configuracoes?: ConfigGeral | null): Record<string, unknown> {
  return {
    versaoBackup: 2,
    contas: ds.contas,
    despesas: ds.despesas,
    despesasFixas: ds.despesasFixas,
    categorias: ds.categorias,
    orcamentos: ds.orcamentos,
    metas: ds.metas,
    investimentos: ds.investimentos,
    cartoes: ds.cartoes,
    transferenciasAgendadas: ds.transferenciasAgendadas,
    patrimonio: ds.patrimonio,
    lixeira: ds.lixeira,
    transacao: ds.transacoes,
    regrasCategoria: ds.regrasCategoria,
    projetos: ds.projetos,
    dividas: ds.dividas,
    pessoas: ds.pessoas,
    acertos: ds.acertos,
    ...(configuracoes && Object.keys(configuracoes).length > 0 ? { configuracoes } : {}),
  };
}
