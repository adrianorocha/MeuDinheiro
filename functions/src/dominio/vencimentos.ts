import type { DespesaDoc, MensagemPush } from "./tipos";
import { numeroDoDia } from "./tempo";

/**
 * "A pagar" = mesma regra do app (DespesaDao.obterPendentes…): não paga, DÉBITO, SEM cartão (compra de cartão entra pela
 * fatura, não aqui — evita aviso duplicado) e natureza NORMAL.
 */
export function entraNoAPagar(d: DespesaDoc): boolean {
  return !d.pago && d.tipo === "DEBITO" && d.cartaoId === null && d.natureza === "NORMAL";
}

export interface SelecaoVencimentos {
  atrasadas: DespesaDoc[];
  hoje: DespesaDoc[];
  amanha: DespesaDoc[];
}

/** Já venceu, vence hoje ou vence amanhã, pelo dia civil de São Paulo. Ordenadas por data. */
export function selecionarVencimentos(despesas: readonly DespesaDoc[], agoraMs: number): SelecaoVencimentos {
  const hoje = numeroDoDia(agoraMs);
  const sel: SelecaoVencimentos = { atrasadas: [], hoje: [], amanha: [] };
  for (const d of despesas.filter(entraNoAPagar).sort((a, b) => a.data - b.data)) {
    const dia = numeroDoDia(d.data);
    if (dia < hoje) sel.atrasadas.push(d);
    else if (dia === hoje) sel.hoje.push(d);
    else if (dia === hoje + 1) sel.amanha.push(d);
  }
  return sel;
}

const plural = (n: number, um: string, varios: string) => `${n} ${n === 1 ? um : varios}`;

/**
 * Texto do aviso agrupado em pt-BR. Só contagens (sem valores, nomes ou descrições): é o que passa pelo FCM e pode aparecer
 * na tela bloqueada. Sem nada a avisar = null.
 */
export function montarAvisoVencimentos(sel: SelecaoVencimentos): MensagemPush | null {
  const a = sel.atrasadas.length;
  const h = sel.hoje.length;
  const m = sel.amanha.length;
  const total = a + h + m;
  if (total === 0) return null;

  const partes: string[] = [];
  if (a > 0) partes.push(`${a} em atraso`);
  if (h > 0) partes.push(`${plural(h, "vence", "vencem")} hoje`);
  if (m > 0) partes.push(`${plural(m, "vence", "vencem")} amanhã`);

  let titulo: string;
  if (h + m === 0) titulo = plural(a, "conta atrasada", "contas atrasadas");
  else if (a === 0 && h === 0) titulo = m === 1 ? "1 conta vence amanhã" : `${m} contas vencem amanhã`;
  else titulo = plural(total, "conta a pagar", "contas a pagar");

  // "2 em atraso, 1 vence hoje e 3 vencem amanhã. Toque para ver."
  const lista = partes.length > 1 ? `${partes.slice(0, -1).join(", ")} e ${partes[partes.length - 1]}` : partes[0];
  const corpo = `${lista}. Toque para ver.`;
  return {
    tipo: "VENCIMENTOS",
    titulo,
    corpo: corpo.charAt(0).toUpperCase() + corpo.slice(1),
    extra: { atrasadas: String(a), hoje: String(h), amanha: String(m) },
  };
}
