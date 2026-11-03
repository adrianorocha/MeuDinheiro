import { normalizarConfigIR, type ConfigIR } from "./imposto";
import { normalizarConfig, type ConfigPainel } from "./painel503020";

/**
 * R57 - Configurações sincronizadas (documento único `configuracoes/geral`). Funções puras; espelham
 * `domain/ConfiguracaoGeral.kt`. O local continua sendo a fonte imediata; a nuvem só sobrescreve se for MAIS NOVA.
 */

export interface ConfigGeral {
  /** R54 - metas %, meses da reserva e classificação por categoria. Ausente = nunca configurado. */
  painel503020?: ConfigPainel | null;
  /** R51 - limite diário até a próxima receita prevista (false = fim do mês). Ausente = padrão. */
  limiteDiarioAteReceita?: boolean | null;
  /** R61 - escolhas do relatório de IR (classe por categoria, limite de educação…). Ausente = padrões. */
  ir?: ConfigIR | null;
  /** epoch ms da última alteração feita pelo usuário; ausente/0 = nunca alterada. */
  atualizadoEm?: number | null;
}

export type AcaoConfig = "NADA" | "APLICAR_NUVEM" | "ENVIAR_LOCAL";

function ts(c: ConfigGeral | null | undefined): number {
  const v = c?.atualizadoEm;
  return typeof v === "number" && Number.isFinite(v) && v > 0 ? v : 0;
}

/**
 * Regra de mesclagem: quem tem `atualizadoEm` MAIOR vence. Empate ou nuvem sem data = a nuvem NÃO sobrescreve o
 * local. Sem nuvem = nada a aplicar; se o local tem data, vale enviar.
 */
export function decidirConfig(local: ConfigGeral | null | undefined, nuvem: ConfigGeral | null | undefined): AcaoConfig {
  const l = ts(local);
  const n = ts(nuvem);
  if (n > l) return "APLICAR_NUVEM";
  if (l > n) return "ENVIAR_LOCAL";
  return "NADA";
}

/** Resultado da mesclagem: a configuração que passa a valer e o que fazer com a nuvem. */
export function mesclarConfig(
  local: ConfigGeral | null | undefined,
  nuvem: ConfigGeral | null | undefined,
): { config: ConfigGeral; acao: AcaoConfig } {
  const acao = decidirConfig(local, nuvem);
  if (acao === "APLICAR_NUVEM") return { config: normalizarGeral(nuvem), acao };
  return { config: normalizarGeral(local), acao };
}

/** Aceita qualquer coisa (documento da nuvem ou JSON local) e devolve uma configuração válida. */
export function normalizarGeral(entrada: unknown): ConfigGeral {
  const e = (entrada && typeof entrada === "object" ? entrada : {}) as Record<string, unknown>;
  const out: ConfigGeral = {};
  if (e.painel503020 && typeof e.painel503020 === "object") out.painel503020 = normalizarConfig(e.painel503020);
  if (typeof e.limiteDiarioAteReceita === "boolean") out.limiteDiarioAteReceita = e.limiteDiarioAteReceita;
  if (e.ir && typeof e.ir === "object") out.ir = normalizarConfigIR(e.ir);
  const t = typeof e.atualizadoEm === "number" && Number.isFinite(e.atualizadoEm) && e.atualizadoEm > 0 ? Math.trunc(e.atualizadoEm) : null;
  if (t !== null) out.atualizadoEm = t;
  return out;
}

/** Aplica uma alteração do usuário (carimba `atualizadoEm`). */
export function alterarConfig(atual: ConfigGeral, patch: Partial<Omit<ConfigGeral, "atualizadoEm">>, agora: number): ConfigGeral {
  const base = normalizarGeral(atual);
  // nunca volta no tempo (relógio atrasado): garante estritamente maior que o valor anterior
  const t = Math.max(agora, ts(base) + 1);
  return { ...base, ...normalizarGeral(patch), atualizadoEm: t };
}
