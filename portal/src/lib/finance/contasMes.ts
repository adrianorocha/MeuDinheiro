import { cartaoIdDe } from "./calc";
import { inicioDoDia } from "./dates";
import { clampDia } from "./dates";
import { fromCents, toCents } from "./money";
import { ocorrenciaDoMes, recorrenciaPausada } from "./operations";
import type { Dataset, Despesa, DespesaFixa } from "./types";

/**
 * R108 - "Contas do mês": visão mensal das despesas fixas (luz, telefone, gás…) sobre as recorrências (R16). Puro: não grava nada.
 * Cada conta mostra a ocorrência do mês (`grupoId fixa:<id>:<AAAA-MM>`), seu estado e o que dá para fazer.
 */

export type EstadoContaMes = "PAGA" | "VENCIDA" | "PENDENTE" | "PROVISIONADA" | "NAO_LANCADA" | "DESABILITADA";

export interface LinhaContaMes {
  regra: DespesaFixa;
  ocorrencia: Despesa | null;
  estado: EstadoContaMes;
  /** valor do lançamento do mês, ou o da regra se ainda não existe */
  valor: number;
  /** data do lançamento do mês, ou a prevista pela regra */
  data: number;
  /** a regra está desabilitada (pausada) — mesmo que o mês já tenha lançamento */
  desabilitada: boolean;
  /** compra no cartão de crédito: a baixa é pela fatura */
  noCartao: boolean;
  /** valor do lançamento difere do da regra (conta variável) */
  valorDifere: boolean;
}

export interface ResumoContasMes {
  linhas: LinhaContaMes[];
  /** soma (R$) das contas do mês que contam (exclui desabilitadas sem lançamento) */
  previsto: number;
  pago: number;
  aPagar: number;
  vencidas: number;
}

export function contasDoMes(ds: Pick<Dataset, "despesas" | "despesasFixas">, mes: number, ano: number, agora: number): ResumoContasMes {
  const hojeIni = inicioDoDia(agora);
  const mesAtual = new Date(agora).getFullYear() * 12 + new Date(agora).getMonth();
  const passado = ano * 12 + (mes - 1) < mesAtual;
  const linhas: LinhaContaMes[] = [];
  for (const regra of ds.despesasFixas) {
    if (regra.tipo !== "DEBITO") continue;
    const oc = ocorrenciaDoMes(ds.despesas, regra, mes, ano);
    const desabilitada = recorrenciaPausada(regra, agora);
    if (!oc && passado) continue; // mês passado sem lançamento: a conta não existia / foi pausada
    const dataPrevista = new Date(ano, mes - 1, clampDia(regra.diaVencimento, mes, ano)).getTime();
    const noCartao = oc ? cartaoIdDe(oc) !== null : !!regra.cartaoId;
    let estado: EstadoContaMes;
    if (oc) {
      if (oc.pago) estado = "PAGA";
      else if (!noCartao && oc.data < hojeIni) estado = "VENCIDA";
      else if (oc.data > agora) estado = "PROVISIONADA";
      else estado = "PENDENTE";
    } else estado = desabilitada ? "DESABILITADA" : "NAO_LANCADA";
    const valor = oc ? oc.valor : regra.valor;
    linhas.push({ regra, ocorrencia: oc, estado, valor, data: oc ? oc.data : dataPrevista, desabilitada, noCartao, valorDifere: !!oc && toCents(oc.valor) !== toCents(regra.valor) });
  }
  linhas.sort((a, b) => a.data - b.data || a.regra.descricao.localeCompare(b.regra.descricao, "pt-BR"));

  let previsto = 0;
  let pago = 0;
  let vencidas = 0;
  for (const l of linhas) {
    if (l.estado === "DESABILITADA") continue;
    const c = toCents(l.valor);
    previsto += c;
    if (l.estado === "PAGA") pago += c;
    if (l.estado === "VENCIDA") vencidas++;
  }
  return { linhas, previsto: fromCents(previsto), pago: fromCents(pago), aPagar: fromCents(previsto - pago), vencidas };
}
