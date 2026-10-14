import type { DespesaDoc, MensagemPush, OrcamentoDoc } from "./tipos";
import { chaveMes } from "./tempo";
import { centavos } from "./resumoSemanal";

export type Limiar = 80 | 100;

const norm = (s: string) => s.trim().toLowerCase();

/**
 * Gasto do mês na categoria em centavos — MESMA regra do app (Financas.progressoOrcamento): natureza NORMAL, no mês civil,
 * mesma categoria (sem diferenciar maiúsculas/espaços); débitos somam, estornos de cartão (crédito com cartão) subtraem e
 * receitas comuns (crédito sem cartão) ficam de fora. Nunca negativo.
 */
export function gastoDaCategoria(despesas: readonly DespesaDoc[], categoria: string, mes: string): number {
  let total = 0;
  for (const d of despesas) {
    if (d.natureza !== "NORMAL" || chaveMes(d.data) !== mes || norm(d.categoria) !== norm(categoria)) continue;
    const c = centavos(d.valor);
    if (d.tipo === "DEBITO") total += c;
    else if (d.cartaoId !== null) total -= c;
  }
  return Math.max(0, total);
}

/** Chave de idempotência `categoria|AAAA-MM|limiar` (a mesma do app: R22). */
export function chaveAviso(categoria: string, mes: string, limiar: Limiar): string {
  return `${norm(categoria).slice(0, 80)}|${mes}|${limiar}`;
}

/** Id de documento Firestore seguro (sem "/") para a chave acima, em `users/{uid}/avisosEnviados/{id}`. */
export function idDaMarca(chave: string): string {
  return encodeURIComponent(chave).replace(/%/g, "~").replace(/\./g, "~2E");
}

export interface AvisoOrcamento {
  categoria: string;
  limiar: Limiar;
  chave: string;
  /** chaves a gravar como "já avisado" (100% também marca o 80%) */
  marcar: string[];
}

/**
 * Decide o aviso de UMA categoria: 100% (estourou) ou 80% (atenção), no máximo um, e só se ainda não foi avisado
 * (`jaAvisados` = chaves já gravadas em avisosEnviados). Avisar 100% implica 80%; se o 100% já foi avisado nada mais sai;
 * se o 80% já foi, o 100% ainda sai. Limite <= 0 = sem aviso.
 */
export function decidirAvisoOrcamento(orc: OrcamentoDoc, gastoCentavos: number, mes: string, jaAvisados: ReadonlySet<string>): AvisoOrcamento | null {
  const limite = centavos(orc.valorLimite);
  if (limite <= 0) return null;
  const pct = gastoCentavos / limite;
  const k100 = chaveAviso(orc.categoria, mes, 100);
  const k80 = chaveAviso(orc.categoria, mes, 80);
  if (pct >= 1 && !jaAvisados.has(k100)) return { categoria: orc.categoria, limiar: 100, chave: k100, marcar: [k100, k80] };
  if (pct >= 0.8 && pct < 1 && !jaAvisados.has(k80) && !jaAvisados.has(k100)) return { categoria: orc.categoria, limiar: 80, chave: k80, marcar: [k80] };
  return null;
}

/** Texto (sem valores): "Mercado: 80% do orçamento" / "Mercado estourou o orçamento". */
export function montarAvisoOrcamento(a: AvisoOrcamento): MensagemPush {
  const cat = a.categoria.trim().slice(0, 40);
  const estourou = a.limiar === 100;
  return {
    tipo: "ORCAMENTO",
    titulo: estourou ? `Orçamento de ${cat} estourado` : `Orçamento de ${cat} em 80%`,
    corpo: estourou ? "Você passou do limite deste mês. Toque para ver." : "Você já usou 80% do limite deste mês. Toque para ver.",
    extra: { categoria: cat, limiar: String(a.limiar) },
  };
}
