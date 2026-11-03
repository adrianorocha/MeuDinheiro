/**
 * R97 - à vista × parcelado: taxa implícita, total pago, juros embutidos e comparação com o rendimento líquido do dinheiro
 * aplicado no CDI. Frações decimais nas taxas; valores em reais (entrada/saída) com cálculo em ponto flutuante e
 * arredondamento para centavos só na saída. Parcelas postecipadas (1ª parcela 30 dias depois da compra).
 *
 * Valor presente para comparar: cada parcela k vale hoje `parcela / fatorLiquido(k)`, onde `fatorLiquido` é o de `rendimento.ts`
 * (quanto 1 real aplicado hoje no CDI × % vira em k meses, líquido de IR regressivo). Assim o VP é "quanto eu precisaria
 * aplicar hoje para pagar cada parcela no vencimento".
 */
import { fatorLiquido } from "./rendimento";

export interface EntradaParcelado {
  aVista: number;
  n: number;
  parcela: number;
  /** Paga na hora (opcional). */
  entrada?: number;
}

export function validarParcelado(e: EntradaParcelado): string | null {
  const entrada = e.entrada ?? 0;
  if (!(e.aVista > 0)) return "Informe o preço à vista.";
  if (!Number.isInteger(e.n) || e.n < 1 || e.n > 600) return "Informe de 1 a 600 parcelas.";
  if (!(e.parcela > 0)) return "Informe o valor da parcela.";
  if (!(entrada >= 0) || entrada >= e.aVista) return "A entrada deve ser menor que o preço à vista.";
  return null;
}

function valorPresenteFixo(parcela: number, n: number, i: number): number {
  let vp = 0;
  for (let k = 1; k <= n; k++) vp += parcela / Math.pow(1 + i, k);
  return vp;
}

/**
 * Taxa mensal implícita: i tal que (aVista − entrada) = Σ parcela/(1+i)^k, por bisseção em [−0,99; 100] (tolerância 1e-9 em i).
 * Total das parcelas = valor financiado => 0. Retorna null se as entradas forem inválidas.
 */
export function taxaImplicita(e: EntradaParcelado): number | null {
  if (validarParcelado(e) !== null) return null;
  const alvo = e.aVista - (e.entrada ?? 0);
  if (Math.abs(e.n * e.parcela - alvo) < 1e-9) return 0;
  let lo = -0.99;
  let hi = 100;
  for (let it = 0; it < 300 && hi - lo > 1e-12; it++) {
    const mid = (lo + hi) / 2;
    // VP decresce com i: VP(mid) > alvo => a taxa real é maior.
    if (valorPresenteFixo(e.parcela, e.n, mid) > alvo) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

export interface ResultadoParcelado {
  taxaMensal: number;
  taxaAnual: number;
  /** entrada + n × parcela. */
  totalPago: number;
  /** totalPago − aVista (pode ser ≤ 0). */
  juros: number;
  /** Valor presente (reais, 2 casas) das parcelas + entrada à taxa líquida do CDI × %. */
  valorPresente: number;
  /** valorPresente − aVista: > 0 = parcelar custa mais que pagar à vista (em valor presente). */
  diferenca: number;
  /** Desconto à vista (sobre o total parcelado) que iguala as duas opções: (totalPago − VP)/totalPago. */
  descontoBreakEven: number;
  /** Desconto à vista realmente oferecido: (totalPago − aVista)/totalPago. */
  descontoOferecido: number;
  /** "AVISTA": o preço à vista é menor que o VP das parcelas; "PARCELADO": o VP das parcelas é menor. */
  menorCusto: "AVISTA" | "PARCELADO" | "EMPATE";
}

const r2 = (x: number): number => Math.round(x * 100 + 1e-7 * Math.sign(x)) / 100;

export function compararParcelado(e: EntradaParcelado, cdiMensal: number, pctCdi: number): ResultadoParcelado | null {
  const i = taxaImplicita(e);
  if (i === null || !Number.isFinite(cdiMensal) || !(pctCdi >= 0)) return null;
  const entrada = e.entrada ?? 0;
  const taxa = cdiMensal * pctCdi;
  let vp = entrada;
  for (let k = 1; k <= e.n; k++) vp += e.parcela / fatorLiquido(taxa, k);
  const total = entrada + e.n * e.parcela;
  const dif = vp - e.aVista;
  return {
    taxaMensal: i,
    taxaAnual: Math.pow(1 + i, 12) - 1,
    totalPago: r2(total),
    juros: r2(total - e.aVista),
    valorPresente: r2(vp),
    diferenca: r2(dif),
    descontoBreakEven: (total - vp) / total,
    descontoOferecido: (total - e.aVista) / total,
    menorCusto: Math.abs(dif) < 0.005 ? "EMPATE" : dif > 0 ? "AVISTA" : "PARCELADO",
  };
}
