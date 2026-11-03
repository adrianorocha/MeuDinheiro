import type { DespesaDoc, MensagemPush } from "./tipos";
import { numeroDoDia } from "./tempo";

export interface ResumoSemanal {
  entradasCentavos: number;
  saidasCentavos: number;
  topCategoria: string | null;
  topCentavos: number;
  /** quantidade de lançamentos considerados */
  qtd: number;
}

export const centavos = (valor: number) => Math.round(valor * 100);

const semCartao = (d: DespesaDoc) => d.cartaoId === null;
const norm = (s: string) => s.trim().toLowerCase();

/**
 * Janela da semana: os 7 dias civis (São Paulo) que terminam HOJE, inclusive. Retorna [primeiroDia, hoje] em número do dia.
 */
export function janelaSemana(agoraMs: number): { primeiro: number; ultimo: number } {
  const ultimo = numeroDoDia(agoraMs);
  return { primeiro: ultimo - 6, ultimo };
}

/**
 * Totais da semana em CENTAVOS (sem ponto flutuante acumulado), só natureza NORMAL.
 *  - entrada: CRÉDITO sem cartão e PAGO (recebida);
 *  - saída: DÉBITO pago, ou compra de cartão (gasto na data da compra, como o orçamento do app).
 * Top categoria = a de maior saída (empate: ordem alfabética, para ser determinístico).
 */
export function calcularResumoSemanal(despesas: readonly DespesaDoc[], agoraMs: number): ResumoSemanal {
  const { primeiro, ultimo } = janelaSemana(agoraMs);
  let entradas = 0;
  let saidas = 0;
  let qtd = 0;
  const porCategoria = new Map<string, { rotulo: string; centavos: number }>();
  for (const d of despesas) {
    if (d.natureza !== "NORMAL") continue;
    const dia = numeroDoDia(d.data);
    if (dia < primeiro || dia > ultimo) continue;
    const c = centavos(d.valor);
    if (d.tipo === "CREDITO") {
      if (semCartao(d) && d.pago) {
        entradas += c;
        qtd++;
      }
    } else if (d.pago || !semCartao(d)) {
      saidas += c;
      qtd++;
      const k = norm(d.categoria) || "sem categoria";
      const atual = porCategoria.get(k) ?? { rotulo: d.categoria.trim() || "Sem categoria", centavos: 0 };
      atual.centavos += c;
      porCategoria.set(k, atual);
    }
  }
  let top: { rotulo: string; centavos: number } | null = null;
  for (const v of [...porCategoria.values()].sort((a, b) => a.rotulo.localeCompare(b.rotulo, "pt-BR"))) {
    if (!top || v.centavos > top.centavos) top = v;
  }
  return { entradasCentavos: entradas, saidasCentavos: saidas, topCategoria: top?.rotulo ?? null, topCentavos: top?.centavos ?? 0, qtd };
}

/**
 * Mensagem do resumo. `corpo` é genérico (aparece na web e na tela bloqueada). Os valores vão em `extra` (centavos) para o
 * APP formatar só na versão privada da notificação. Semana sem movimento = null (não incomoda à toa).
 */
export function montarResumoSemanal(r: ResumoSemanal): MensagemPush | null {
  if (r.qtd === 0) return null;
  const extra: Record<string, string> = {
    entradasCentavos: String(r.entradasCentavos),
    saidasCentavos: String(r.saidasCentavos),
  };
  if (r.topCategoria) {
    extra.topCategoria = r.topCategoria.slice(0, 40);
    extra.topCentavos = String(r.topCentavos);
  }
  return { tipo: "RESUMO_SEMANAL", titulo: "Resumo da sua semana", corpo: "Toque para ver quanto entrou, quanto saiu e onde você mais gastou.", extra };
}
