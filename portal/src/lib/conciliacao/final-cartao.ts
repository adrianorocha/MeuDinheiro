import type { Cartao } from "../finance/types";

/**
 * R101 - final do cartão (4 últimos dígitos) usado numa linha do extrato/fatura para vincular o gasto ao cartão certo
 * do grupo (físico, adicionais e virtuais compartilham a fatura, R18). Funções puras.
 */

const SEM_ACENTO = /[̀-ͯ]/g;
const soDigitos = (s: string | undefined | null) => (s ?? "").replace(/\D/g, "");

/**
 * Final citado no TEXTO da linha: "final 1234", "cartão 1234", "card ending 1234", "terminado em 1234",
 * "**** 1234", "xxxx.1234". Exige uma palavra-chave ou máscara antes dos 4 dígitos (nunca um número solto,
 * para não confundir com valor, ano ou parcela).
 */
export function finalNaDescricao(descricao: string): string | undefined {
  const t = descricao.normalize("NFD").replace(SEM_ACENTO, "").toLowerCase();
  const porPalavra = /\b(?:final|fim|ending(?:\s+in)?|termin(?:ado|ada)?\s+em|cartao|card)\s*(?:n[o°º]?\.?\s*)?[:#-]?\s*(?:[*x•]{2,}[\s.-]*)?(\d{4})(?![\d/])/.exec(t);
  if (porPalavra) return porPalavra[1];
  const porMascara = /[*x•]{2,}[\s.-]*(\d{4})(?![\d/])/.exec(t);
  return porMascara?.[1];
}

/** Final vindo de uma célula/campo dedicado (CSV): últimos 4 dígitos de qualquer formato ("1234", "**** 1234", "5502 **** **** 1234"). */
export function finalDaCelula(valor: string | undefined): string | undefined {
  const d = soDigitos(valor);
  return d.length >= 4 ? d.slice(-4) : undefined;
}

/**
 * Cartão do grupo cujo final confere. Só decide quando há EXATAMENTE um (finais repetidos no grupo, como um virtual que
 * repete o do físico, são ambíguos: devolve null e quem chama mantém o cartão padrão).
 */
export function cartaoDoFinal<T extends Pick<Cartao, "id" | "finalCartao">>(final: string | undefined, grupo: readonly T[]): T | null {
  const f = soDigitos(final).slice(-4);
  if (f.length < 4) return null;
  const achados = grupo.filter((c) => soDigitos(c.finalCartao).slice(-4) === f);
  return achados.length === 1 ? achados[0] : null;
}
