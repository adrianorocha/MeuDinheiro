/**
 * R99 - consulta PÚBLICA ao Banco Central do Brasil (SGS e PTAX), somente leitura.
 *
 * - Só requisições GET sem cabeçalhos, sem cookies (`credentials: "omit"`) e sem NENHUM dado do usuário.
 * - Funções de parsing/validação são puras; as de rede recebem `fetch` injetável (testes) e têm timeout de 8 s.
 * - Uma série que falha não derruba as outras: o resultado é parcial, com a lista de falhas.
 * - Datas em ISO (AAAA-MM-DD) internamente; "hoje" é sempre injetado. Datas futuras são tratadas como inválidas.
 */
import type { IndiceId } from "./indices";

export const BCB_SGS_BASE = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.";
export const BCB_PTAX_BASE = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata";
export const TIMEOUT_MS = 8000;
/** Validade do cache local: 6 horas. */
export const TTL_MS = 6 * 60 * 60 * 1000;
export const FONTE_BCB = "Banco Central do Brasil (SGS/PTAX)";

// ------------------------------------------------------------------ séries SGS

export type TipoSerie = "mensal" | "selicMeta" | "salario";
export type ChaveSerie = IndiceId | "selicMeta" | "salarioMinimo";

export interface DefSerie {
  chave: ChaveSerie;
  codigo: number;
  tipo: TipoSerie;
  nome: string;
}

/** Séries consultadas. IVAR, TBF e TJLP não têm série mensal em % confirmada: ficam como estão ("manual"/semente). */
export const SERIES: readonly DefSerie[] = [
  { chave: "selic", codigo: 4390, tipo: "mensal", nome: "Selic acumulada no mês" },
  { chave: "cdi", codigo: 4391, tipo: "mensal", nome: "CDI acumulado no mês" },
  { chave: "tr", codigo: 7811, tipo: "mensal", nome: "TR mensal" },
  { chave: "poupanca", codigo: 196, tipo: "mensal", nome: "Rendimento mensal da poupança" },
  { chave: "ipca", codigo: 433, tipo: "mensal", nome: "IPCA" },
  { chave: "inpc", codigo: 188, tipo: "mensal", nome: "INPC" },
  { chave: "igpm", codigo: 189, tipo: "mensal", nome: "IGP-M" },
  { chave: "igpdi", codigo: 190, tipo: "mensal", nome: "IGP-DI" },
  { chave: "incc", codigo: 192, tipo: "mensal", nome: "INCC-DI" },
  { chave: "ipcfipe", codigo: 7465, tipo: "mensal", nome: "IPC-Fipe" },
  { chave: "ipcae", codigo: 10764, tipo: "mensal", nome: "IPCA-E" },
  { chave: "selicMeta", codigo: 432, tipo: "selicMeta", nome: "Meta da Selic (% a.a.)" },
  { chave: "salarioMinimo", codigo: 1619, tipo: "salario", nome: "Salário mínimo" },
];

/** Quantos meses (valores) guardar por índice mensal. */
export const MESES_SERIE = 3;

/** Faixas plausíveis: valores fora dela são descartados como lixo. */
export const FAIXAS = {
  mensal: { min: -5, max: 10 },
  selicMeta: { min: 0, max: 60 },
  salario: { min: 100, max: 100000 },
} as const;

export interface PontoSgs {
  /** AAAA-MM-DD. */
  data: string;
  valor: number;
}

export function urlSgs(codigo: number, ultimos: number): string {
  return `${BCB_SGS_BASE}${codigo}/dados/ultimos/${ultimos}?formato=json`;
}

/** "dd/mm/aaaa" -> "aaaa-mm-dd" (null se inválida, inclusive dia inexistente). */
export function dataBrParaIso(s: unknown): string | null {
  if (typeof s !== "string") return null;
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s.trim());
  if (!m) return null;
  const d = Number(m[1]);
  const mes = Number(m[2]);
  const a = Number(m[3]);
  const t = new Date(Date.UTC(a, mes - 1, d));
  if (t.getUTCFullYear() !== a || t.getUTCMonth() !== mes - 1 || t.getUTCDate() !== d) return null;
  return `${m[3]}-${m[2]}-${m[1]}`;
}

/** "AAAA-MM-DD" do dia local de `d`. */
export function hojeIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Competência "MM/AAAA" de uma data ISO. */
export function competenciaDeIso(iso: string): string {
  return `${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
}

/** Valor string do BCB ("0.82", ponto decimal) ou número -> número finito; senão NaN. */
export function lerValor(v: unknown): number {
  if (typeof v === "number") return Number.isFinite(v) ? v : Number.NaN;
  if (typeof v !== "string" || v.trim() === "") return Number.NaN;
  const n = Number(v.trim());
  return Number.isFinite(n) ? n : Number.NaN;
}

/** Converte a resposta crua do SGS em pontos válidos (data válida e valor numérico). Nunca lança. */
export function parsePontosSgs(json: unknown): PontoSgs[] {
  if (!Array.isArray(json)) return [];
  const out: PontoSgs[] = [];
  for (const it of json) {
    if (!it || typeof it !== "object") continue;
    const r = it as Record<string, unknown>;
    const data = dataBrParaIso(r.data);
    const valor = lerValor(r.valor);
    if (data && Number.isFinite(valor)) out.push({ data, valor });
  }
  return out;
}

/** Mantém só pontos com data <= hoje e valor na faixa; ordena do mais antigo ao mais recente. */
export function filtrarPontos(pontos: readonly PontoSgs[], hoje: string, faixa: { min: number; max: number }): PontoSgs[] {
  return pontos
    .filter((p) => p.data <= hoje && p.valor >= faixa.min && p.valor <= faixa.max)
    .sort((a, b) => (a.data < b.data ? -1 : a.data > b.data ? 1 : 0));
}

/** Item mais recente cuja data <= hoje (e valor na faixa); null se não houver. */
export function maisRecenteAteHoje(pontos: readonly PontoSgs[], hoje: string, faixa: { min: number; max: number }): PontoSgs | null {
  const v = filtrarPontos(pontos, hoje, faixa);
  return v.length ? v[v.length - 1] : null;
}

export interface IndiceBcb {
  /** Últimos valores em %, do mais antigo ao mais recente (um por mês). */
  serie: number[];
  /** "MM/AAAA" do último valor. */
  competencia: string;
  /** true = a competência é o mês corrente (acumulado parcial do mês, ainda sujeito a mudança). */
  parcial: boolean;
}

/**
 * Série mensal -> últimos `n` meses (um valor por mês; se houver repetição no mês fica o último) com a competência real.
 * A data da série é o 1º dia do mês de referência. `parcial` = competência igual ao mês de `hoje`.
 */
export function indiceMensal(pontos: readonly PontoSgs[], hoje: string, n = MESES_SERIE): IndiceBcb | null {
  const v = filtrarPontos(pontos, hoje, FAIXAS.mensal);
  const porMes = new Map<string, number>();
  for (const p of v) porMes.set(p.data.slice(0, 7), p.valor);
  const meses = [...porMes.keys()].sort();
  if (!meses.length) return null;
  const ult = meses.slice(-n);
  const ultimo = ult[ult.length - 1];
  return {
    serie: ult.map((m) => porMes.get(m) as number),
    competencia: `${ultimo.slice(5, 7)}/${ultimo.slice(0, 4)}`,
    parcial: ultimo === hoje.slice(0, 7),
  };
}

export interface SalarioBcb {
  centavos: number;
  competencia: string;
}

export function salarioDe(pontos: readonly PontoSgs[], hoje: string): SalarioBcb | null {
  const p = maisRecenteAteHoje(pontos, hoje, FAIXAS.salario);
  return p ? { centavos: Math.round(p.valor * 100), competencia: competenciaDeIso(p.data) } : null;
}

export interface SelicMetaBcb {
  /** % a.a. */
  valor: number;
  /** AAAA-MM-DD da vigência encontrada. */
  data: string;
}

export function selicMetaDe(pontos: readonly PontoSgs[], hoje: string): SelicMetaBcb | null {
  const p = maisRecenteAteHoje(pontos, hoje, FAIXAS.selicMeta);
  return p ? { valor: p.valor, data: p.data } : null;
}

// ------------------------------------------------------------------ rede (fetch injetável)

export interface RespostaMin {
  ok: boolean;
  status: number;
  json(): Promise<unknown>;
}
export type FetchLike = (url: string, init?: RequestInit) => Promise<RespostaMin>;

export interface OpcoesRede {
  fetch?: FetchLike;
  timeoutMs?: number;
}

function fetchPadrao(): FetchLike {
  return (url, init) => fetch(url, init);
}

/** GET JSON público com timeout (AbortController). Lança Error com mensagem curta em pt-BR. */
export async function buscarJson(url: string, op: OpcoesRede = {}): Promise<unknown> {
  const f = op.fetch ?? fetchPadrao();
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), op.timeoutMs ?? TIMEOUT_MS);
  try {
    const res = await f(url, { method: "GET", credentials: "omit", mode: "cors", cache: "no-store", signal: ctl.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (e) {
    if (ctl.signal.aborted) throw new Error("tempo esgotado");
    throw e instanceof Error ? e : new Error("falha de rede");
  } finally {
    clearTimeout(timer);
  }
}

export interface Falha {
  fonte: string;
  motivo: string;
}

export interface SnapshotIndices {
  /** Epoch ms da consulta. */
  atualizadoEm: number;
  itens: Partial<Record<IndiceId, IndiceBcb>>;
  selicMeta: SelicMetaBcb | null;
  salarioMinimo: SalarioBcb | null;
  falhas: Falha[];
}

const msgErro = (e: unknown) => (e instanceof Error && e.message ? e.message : "falha de rede");

/** Consulta todas as séries em paralelo; cada uma isolada. Devolve snapshot parcial + falhas. Nunca lança. */
export async function buscarIndicesBcb(agora: Date, op: OpcoesRede = {}): Promise<SnapshotIndices> {
  const hoje = hojeIso(agora);
  const snap: SnapshotIndices = { atualizadoEm: agora.getTime(), itens: {}, selicMeta: null, salarioMinimo: null, falhas: [] };
  await Promise.all(
    SERIES.map(async (def) => {
      try {
        // margem para descartar datas futuras (a série 432/1619 traz datas posteriores a hoje)
        const json = await buscarJson(urlSgs(def.codigo, def.tipo === "mensal" ? 8 : 12), op);
        const pontos = parsePontosSgs(json);
        if (def.tipo === "mensal") {
          const r = indiceMensal(pontos, hoje);
          if (!r) throw new Error("sem valor válido");
          // "parcial do mês" só faz sentido para o acumulado de Selic/CDI; TR/poupança do mês vigente são taxas fixas do período
          if (def.chave !== "selic" && def.chave !== "cdi") r.parcial = false;
          snap.itens[def.chave as IndiceId] = r;
        } else if (def.tipo === "selicMeta") {
          const r = selicMetaDe(pontos, hoje);
          if (!r) throw new Error("sem valor válido");
          snap.selicMeta = r;
        } else {
          const r = salarioDe(pontos, hoje);
          if (!r) throw new Error("sem valor válido");
          snap.salarioMinimo = r;
        }
      } catch (e) {
        snap.falhas.push({ fonte: `${def.nome} (SGS ${def.codigo})`, motivo: msgErro(e) });
      }
    }),
  );
  return snap;
}

export const snapshotVazio = (s: SnapshotIndices): boolean => Object.keys(s.itens).length === 0 && !s.selicMeta && !s.salarioMinimo;

/** Junta consulta nova sobre a anterior: o que falhou agora mantém o último valor conhecido. */
export function mesclarSnapshots(antigo: SnapshotIndices | null, novo: SnapshotIndices): SnapshotIndices {
  if (!antigo) return novo;
  return {
    atualizadoEm: novo.atualizadoEm,
    itens: { ...antigo.itens, ...novo.itens },
    selicMeta: novo.selicMeta ?? antigo.selicMeta,
    salarioMinimo: novo.salarioMinimo ?? antigo.salarioMinimo,
    falhas: novo.falhas,
  };
}

// ------------------------------------------------------------------ cache (TTL) e texto de estado

export const CHAVE_CACHE_INDICES = "meudinheiro:bcb-indices:v1";
export const CHAVE_CACHE_CAMBIO = "meudinheiro:bcb-cambio:v1";

/** true quando não há cache, a data é inválida/futura ou passaram mais de 6 h. */
export function cacheExpirado(atualizadoEm: number | null | undefined, agora: number, ttl = TTL_MS): boolean {
  if (typeof atualizadoEm !== "number" || !Number.isFinite(atualizadoEm)) return true;
  const idade = agora - atualizadoEm;
  return idade < 0 || idade >= ttl;
}

const num = (n: unknown): n is number => typeof n === "number" && Number.isFinite(n);

/** Reconstrói o snapshot de índices a partir do JSON salvo, validando tudo. null se inválido. */
export function lerSnapshotIndices(raw: unknown): SnapshotIndices | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  if (!num(r.atualizadoEm)) return null;
  const out: SnapshotIndices = { atualizadoEm: r.atualizadoEm, itens: {}, selicMeta: null, salarioMinimo: null, falhas: [] };
  const ids = new Set(SERIES.filter((s) => s.tipo === "mensal").map((s) => s.chave));
  if (r.itens && typeof r.itens === "object") {
    for (const [id, v] of Object.entries(r.itens as Record<string, unknown>)) {
      if (!ids.has(id as ChaveSerie) || !v || typeof v !== "object") continue;
      const x = v as Record<string, unknown>;
      if (
        Array.isArray(x.serie) &&
        x.serie.length >= 1 &&
        x.serie.length <= 24 &&
        x.serie.every((n) => num(n) && n >= FAIXAS.mensal.min && n <= FAIXAS.mensal.max) &&
        typeof x.competencia === "string" &&
        /^\d{2}\/\d{4}$/.test(x.competencia)
      ) {
        out.itens[id as IndiceId] = { serie: [...(x.serie as number[])], competencia: x.competencia, parcial: x.parcial === true };
      }
    }
  }
  const sm = r.selicMeta as Record<string, unknown> | null | undefined;
  if (sm && num(sm.valor) && sm.valor >= FAIXAS.selicMeta.min && sm.valor <= FAIXAS.selicMeta.max && typeof sm.data === "string" && /^\d{4}-\d{2}-\d{2}$/.test(sm.data)) {
    out.selicMeta = { valor: sm.valor, data: sm.data };
  }
  const sal = r.salarioMinimo as Record<string, unknown> | null | undefined;
  if (sal && num(sal.centavos) && Number.isInteger(sal.centavos) && sal.centavos > 0 && sal.centavos < 1e9 && typeof sal.competencia === "string" && sal.competencia.length <= 12) {
    out.salarioMinimo = { centavos: sal.centavos, competencia: sal.competencia };
  }
  return out;
}

const p2 = (n: number) => String(n).padStart(2, "0");
/** "28/10 14:05" no fuso local. */
export function fmtDataHora(ms: number): string {
  const d = new Date(ms);
  return `${p2(d.getDate())}/${p2(d.getMonth() + 1)} ${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

export interface EstadoAtualizacao {
  atualizadoEm: number | null;
  carregando: boolean;
  erro: string | null;
  offline: boolean;
}

/** Linha de estado exibida na tela. */
export function descreverEstado(e: EstadoAtualizacao): string {
  if (e.carregando) return "Atualizando com o Banco Central…";
  if (e.offline) return e.atualizadoEm !== null ? `Offline: usando valores salvos de ${fmtDataHora(e.atualizadoEm)}` : "Offline: usando os valores de referência (semente).";
  if (e.erro) {
    const resto = e.atualizadoEm !== null ? ` Usando valores salvos de ${fmtDataHora(e.atualizadoEm)}.` : " Usando os valores de referência (semente).";
    return `Não foi possível atualizar: ${e.erro}.${resto}`;
  }
  if (e.atualizadoEm !== null) return `Atualizado em ${fmtDataHora(e.atualizadoEm)} — Banco Central`;
  return "Ainda não atualizado: usando os valores de referência (semente).";
}

// ------------------------------------------------------------------ PTAX (câmbio)

export interface MoedaDef {
  codigo: string;
  nome: string;
}

export const MOEDAS: readonly MoedaDef[] = [
  { codigo: "USD", nome: "Dólar americano" },
  { codigo: "EUR", nome: "Euro" },
  { codigo: "GBP", nome: "Libra esterlina" },
  { codigo: "JPY", nome: "Iene japonês" },
  { codigo: "CHF", nome: "Franco suíço" },
  { codigo: "CAD", nome: "Dólar canadense" },
  { codigo: "AUD", nome: "Dólar australiano" },
  { codigo: "DKK", nome: "Coroa dinamarquesa" },
  { codigo: "NOK", nome: "Coroa norueguesa" },
  { codigo: "SEK", nome: "Coroa sueca" },
];

export type TipoBoletim = "Abertura" | "Intermediário" | "Fechamento";

export interface Boletim {
  /** "AAAA-MM-DD HH:mm:ss". */
  dataHora: string;
  /** R$ por 1 unidade da moeda. */
  compra: number;
  venda: number;
  tipo: TipoBoletim;
}

export interface CotacaoMoeda {
  moeda: string;
  compra: number;
  venda: number;
  dataHora: string;
  tipo: TipoBoletim;
  /** true = boletim intermediário do dia corrente (ainda sem fechamento). */
  intermediario: boolean;
  /** Variação % da venda contra o fechamento anterior; null se não há referência. */
  variacaoPct: number | null;
}

export interface SnapshotCambio {
  atualizadoEm: number;
  cotacoes: CotacaoMoeda[];
  falhas: Falha[];
}

/** MM-DD-AAAA exigido pelo OData da PTAX. */
export function dataPtax(d: Date): string {
  return `${p2(d.getMonth() + 1)}-${p2(d.getDate())}-${d.getFullYear()}`;
}

export function urlPtax(moeda: string, inicio: Date, fim: Date): string {
  return (
    `${BCB_PTAX_BASE}/CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)` +
    `?@moeda='${moeda}'&@dataInicial='${dataPtax(inicio)}'&@dataFinalCotacao='${dataPtax(fim)}'` +
    `&$orderby=dataHoraCotacao%20desc&$format=json`
  );
}

/** Resposta OData -> boletins válidos (venda/compra > 0, data não futura), do mais recente ao mais antigo. Nunca lança. */
export function parseBoletins(json: unknown, hoje: string): Boletim[] {
  const lista = json && typeof json === "object" ? (json as Record<string, unknown>).value : null;
  if (!Array.isArray(lista)) return [];
  const out: Boletim[] = [];
  for (const it of lista) {
    if (!it || typeof it !== "object") continue;
    const r = it as Record<string, unknown>;
    const compra = lerValor(r.cotacaoCompra);
    const venda = lerValor(r.cotacaoVenda);
    const dh = typeof r.dataHoraCotacao === "string" ? /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})/.exec(r.dataHoraCotacao) : null;
    const tipo = r.tipoBoletim;
    if (!dh || !(compra > 0) || !(venda > 0) || compra > 1e6 || venda > 1e6) continue;
    if (tipo !== "Fechamento" && tipo !== "Intermediário" && tipo !== "Abertura") continue;
    if (dh[1] > hoje) continue;
    out.push({ dataHora: `${dh[1]} ${dh[2]}`, compra, venda, tipo });
  }
  return out.sort((a, b) => (a.dataHora < b.dataHora ? 1 : a.dataHora > b.dataHora ? -1 : 0));
}

/** Um fechamento por dia (o mais recente do dia), do mais recente ao mais antigo. */
export function fechamentosPorDia(boletins: readonly Boletim[]): Boletim[] {
  const vistos = new Set<string>();
  const out: Boletim[] = [];
  for (const b of [...boletins].sort((x, y) => (x.dataHora < y.dataHora ? 1 : -1))) {
    if (b.tipo !== "Fechamento") continue;
    const dia = b.dataHora.slice(0, 10);
    if (vistos.has(dia)) continue;
    vistos.add(dia);
    out.push(b);
  }
  return out;
}

/**
 * Cotação exibida: o fechamento mais recente; se existir intermediário de dia POSTERIOR ao último fechamento
 * (dia corrente antes do fechamento), usa o intermediário mais recente, rotulado. Variação % da venda contra o
 * fechamento anterior (o de dia anterior ao exibido).
 */
export function calcularCotacao(moeda: string, boletins: readonly Boletim[]): CotacaoMoeda | null {
  const fech = fechamentosPorDia(boletins);
  const interm = boletins.filter((b) => b.tipo !== "Fechamento");
  const ultimoInterm = interm.length ? interm.reduce((a, b) => (a.dataHora >= b.dataHora ? a : b)) : null;
  const ultimoFech = fech[0] ?? null;
  let atual: Boletim | null = ultimoFech;
  if (ultimoInterm && (!ultimoFech || ultimoInterm.dataHora.slice(0, 10) > ultimoFech.dataHora.slice(0, 10))) atual = ultimoInterm;
  if (!atual) return null;
  const dia = atual.dataHora.slice(0, 10);
  const anterior = fech.find((b) => b.dataHora.slice(0, 10) < dia) ?? null;
  const variacaoPct = anterior ? (atual.venda / anterior.venda - 1) * 100 : null;
  return { moeda, compra: atual.compra, venda: atual.venda, dataHora: atual.dataHora, tipo: atual.tipo, intermediario: atual.tipo !== "Fechamento", variacaoPct };
}

const DIA_MS = 24 * 60 * 60 * 1000;
/** Janela de ~10 dias corridos para o quadro de cotações. */
export const JANELA_COTACAO_DIAS = 10;
/** Janela de ~45 dias corridos para o gráfico (≈ 30 pregões). */
export const JANELA_HISTORICO_DIAS = 45;

/** Cotações das 10 moedas (uma consulta por moeda, isoladas). Nunca lança. */
export async function buscarCotacoes(agora: Date, op: OpcoesRede = {}): Promise<SnapshotCambio> {
  const hoje = hojeIso(agora);
  const inicio = new Date(agora.getTime() - JANELA_COTACAO_DIAS * DIA_MS);
  const snap: SnapshotCambio = { atualizadoEm: agora.getTime(), cotacoes: [], falhas: [] };
  const res = await Promise.all(
    MOEDAS.map(async (m) => {
      try {
        const boletins = parseBoletins(await buscarJson(urlPtax(m.codigo, inicio, agora), op), hoje);
        const c = calcularCotacao(m.codigo, boletins);
        if (!c) throw new Error("sem cotação no período");
        return { c };
      } catch (e) {
        return { f: { fonte: `${m.nome} (${m.codigo})`, motivo: msgErro(e) } };
      }
    }),
  );
  for (const r of res) {
    if ("c" in r && r.c) snap.cotacoes.push(r.c);
    else if ("f" in r && r.f) snap.falhas.push(r.f);
  }
  return snap;
}

export interface PontoHistorico {
  /** AAAA-MM-DD. */
  data: string;
  venda: number;
}

/** Histórico (fechamentos) de uma moeda nos últimos ~45 dias corridos, do mais antigo ao mais recente, até 30 pontos. */
export async function buscarHistorico(moeda: string, agora: Date, op: OpcoesRede = {}): Promise<PontoHistorico[]> {
  if (!MOEDAS.some((m) => m.codigo === moeda)) throw new Error("moeda inválida");
  const inicio = new Date(agora.getTime() - JANELA_HISTORICO_DIAS * DIA_MS);
  const boletins = parseBoletins(await buscarJson(urlPtax(moeda, inicio, agora), op), hojeIso(agora));
  return fechamentosPorDia(boletins)
    .slice(0, 30)
    .reverse()
    .map((b) => ({ data: b.dataHora.slice(0, 10), venda: b.venda }));
}

/** Reconstrói o snapshot de câmbio salvo; null se inválido. */
export function lerSnapshotCambio(raw: unknown): SnapshotCambio | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  if (!num(r.atualizadoEm) || !Array.isArray(r.cotacoes)) return null;
  const cotacoes: CotacaoMoeda[] = [];
  for (const it of r.cotacoes) {
    if (!it || typeof it !== "object") continue;
    const c = it as Record<string, unknown>;
    if (
      typeof c.moeda === "string" &&
      MOEDAS.some((m) => m.codigo === c.moeda) &&
      num(c.compra) &&
      c.compra > 0 &&
      num(c.venda) &&
      c.venda > 0 &&
      typeof c.dataHora === "string" &&
      (c.tipo === "Fechamento" || c.tipo === "Intermediário" || c.tipo === "Abertura") &&
      (c.variacaoPct === null || num(c.variacaoPct))
    ) {
      cotacoes.push({ moeda: c.moeda, compra: c.compra, venda: c.venda, dataHora: c.dataHora, tipo: c.tipo, intermediario: c.tipo !== "Fechamento", variacaoPct: c.variacaoPct as number | null });
    }
  }
  return cotacoes.length ? { atualizadoEm: r.atualizadoEm, cotacoes, falhas: [] } : null;
}

// ------------------------------------------------------------------ conversor

/** Spread/IOF informado pelo usuário: % entre 0 e 100 (padrão 0). */
export const spreadValido = (pct: number): boolean => Number.isFinite(pct) && pct >= 0 && pct <= 100;

/** Quantas unidades da moeda `reais` compram, com a venda PTAX e o acréscimo %: reais / (venda × (1 + spread)). NaN se inválido. */
export function reaisParaMoeda(reais: number, venda: number, spreadPct = 0): number {
  if (!Number.isFinite(reais) || !(venda > 0) || !spreadValido(spreadPct)) return Number.NaN;
  return reais / (venda * (1 + spreadPct / 100));
}

/** Custo em R$ de `unidades` da moeda: unidades × venda × (1 + spread). NaN se inválido. */
export function moedaParaReais(unidades: number, venda: number, spreadPct = 0): number {
  if (!Number.isFinite(unidades) || !(venda > 0) || !spreadValido(spreadPct)) return Number.NaN;
  return unidades * venda * (1 + spreadPct / 100);
}
