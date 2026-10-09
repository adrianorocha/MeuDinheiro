import { cartaoDeDebito, cartaoIdDe, ehGrupoParcelas, estaAtrasada, faturaDaCompra, principalDe, vencimentoFatura } from "./calc";
import { toCents, fromCents } from "./money";
import { normalizar } from "./texto";
import { parcelaDaDescricao } from "./relatorios";
import type { Dataset, Despesa, Natureza } from "./types";

/**
 * R48 - conteúdo do comprovante de um lançamento. Função pura (a mesma lógica do app, `Comprovante.kt`):
 * decide QUAIS linhas existem (campos vazios não aparecem); o desenho (PNG/PDF) fica em `lib/exportar/comprovante.ts`.
 */

export interface SeloComprovante {
  id: string;
  rotulo: string;
  cor: string;
}

export interface StatusComprovante {
  id: "PAGO" | "PENDENTE" | "VENCIDO";
  rotulo: string;
  cor: string;
}

export interface ComprovanteModelo {
  selo: SeloComprovante;
  /** já formatado, com sinal (+ receita / − despesa) */
  valor: string;
  entrada: boolean;
  status: StatusComprovante;
  linhas: { rotulo: string; valor: string }[];
  id: string;
  emitidoEm: string;
}

export const AVISO_COMPROVANTE = "Documento gerado pelo MeuDinheiro — sem valor fiscal";

const SELOS: Record<string, SeloComprovante> = {
  RECEITA: { id: "RECEITA", rotulo: "RECEITA", cor: "#15803d" },
  DESPESA: { id: "DESPESA", rotulo: "DESPESA", cor: "#b91c1c" },
  TRANSFERENCIA: { id: "TRANSFERENCIA", rotulo: "TRANSFERÊNCIA", cor: "#1d4ed8" },
  PAGAMENTO_FATURA: { id: "PAGAMENTO_FATURA", rotulo: "PAGAMENTO DE FATURA", cor: "#6d28d9" },
  APORTE_META: { id: "APORTE_META", rotulo: "APORTE EM META", cor: "#0f766e" },
  RESGATE_META: { id: "RESGATE_META", rotulo: "RESGATE DE META", cor: "#0e7490" },
  AJUSTE: { id: "AJUSTE", rotulo: "AJUSTE DE SALDO", cor: "#b45309" },
  SALDO_INICIAL: { id: "SALDO_INICIAL", rotulo: "SALDO INICIAL", cor: "#475569" },
};

const STATUS: Record<StatusComprovante["id"], StatusComprovante> = {
  PAGO: { id: "PAGO", rotulo: "PAGO", cor: "#15803d" },
  PENDENTE: { id: "PENDENTE", rotulo: "PENDENTE", cor: "#b45309" },
  VENCIDO: { id: "VENCIDO", rotulo: "VENCIDO", cor: "#b91c1c" },
};

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const diaFmt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
const emissaoFmt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
const dia = (ms: number) => diaFmt.format(new Date(ms));

/** Só os 4 últimos dígitos ficam visíveis. */
export function mascararNumero(s: string): string {
  const digitos = s.replace(/\D/g, "");
  return digitos.length > 4 ? `•••• ${digitos.slice(-4)}` : s;
}

function seloDe(d: Despesa): SeloComprovante {
  const n: Natureza = d.natureza;
  if (n !== "NORMAL" && SELOS[n]) return SELOS[n];
  return d.tipo === "CREDITO" ? SELOS.RECEITA : SELOS.DESPESA;
}

export function montarComprovante(d: Despesa, ds: Pick<Dataset, "contas" | "cartoes" | "despesas" | "despesasFixas">, agora: number): ComprovanteModelo {
  const entrada = d.tipo === "CREDITO";
  const selo = seloDe(d);
  const status = d.pago ? STATUS.PAGO : estaAtrasada(d, agora) ? STATUS.VENCIDO : STATUS.PENDENTE;
  const linhas: { rotulo: string; valor: string }[] = [];
  const add = (rotulo: string, valor: string | null | undefined) => {
    if (valor && valor.trim()) linhas.push({ rotulo, valor: valor.trim() });
  };

  const parcela = parcelaDaDescricao(d.descricao);
  add("Descrição", parcela ? parcela.base : d.descricao);
  add("Categoria", d.categoria);
  if (d.natureza !== "NORMAL") add("Natureza", selo.rotulo.charAt(0) + selo.rotulo.slice(1).toLowerCase());

  const cid = cartaoIdDe(d);
  const cartao = ds.cartoes.find((c) => c.id === (cid ?? cartaoDeDebito(d)));
  const conta = ds.contas.find((c) => c.conta === d.conta);

  add("Data do lançamento", dia(d.data));
  if (cid !== null && cartao) {
    const principal = principalDe(cartao, ds.cartoes);
    const ref = faturaDaCompra(principal, d.data);
    add("Fatura de referência", `${String(ref.mes).padStart(2, "0")}/${ref.ano}`);
    add("Vencimento da fatura", dia(vencimentoFatura(principal, ref.mes, ref.ano)));
  } else if (cid !== null) {
    add("Fatura de referência", `${String(d.mes).padStart(2, "0")}/${d.ano}`);
  } else if (!d.pago) {
    add("Vencimento", dia(d.data));
  }
  if (d.pago) add("Data do pagamento", dia(d.data));

  const contaTxt = conta ? `${conta.banco}${conta.agencia ? ` · ag. ${conta.agencia}` : ""} · ${mascararNumero(conta.conta)}` : mascararNumero(d.conta);
  add(cid !== null ? "Conta da fatura" : entrada ? "Conta de destino" : "Conta de origem", contaTxt);

  if (d.natureza === "TRANSFERENCIA") {
    const outra = d.grupoId ? ds.despesas.find((x) => x.grupoId === d.grupoId && x.id !== d.id && x.conta !== d.conta) : undefined;
    if (outra) {
      const nomeOutra = ds.contas.find((c) => c.conta === outra.conta)?.banco ?? mascararNumero(outra.conta);
      const minha = conta?.banco ?? mascararNumero(d.conta);
      add("Transferência", entrada ? `${nomeOutra} → ${minha}` : `${minha} → ${nomeOutra}`);
    }
  }

  if (cartao) {
    add("Cartão", `${cartao.nome}${cartao.finalCartao ? ` •••• ${cartao.finalCartao}` : ""}`);
    add("Tipo do cartão", cartao.cartaoPrincipalId != null ? "Virtual" : "Físico");
  }
  if (cid !== null) add("Modalidade", "Crédito");
  else if (cartaoDeDebito(d) !== null) add("Modalidade", "Débito");

  if (parcela) {
    add("Parcela", `${parcela.i}/${parcela.n}`);
    const grupo = d.grupoId && ehGrupoParcelas(d) ? ds.despesas.filter((x) => x.grupoId === d.grupoId) : [];
    if (grupo.length >= 2) add("Valor total da compra", brl.format(fromCents(grupo.reduce((a, x) => a + toCents(x.valor), 0))));
    add("Parcelas restantes", String(parcela.n - parcela.i));
  }

  const fixa = ds.despesasFixas.find(
    (f) => normalizar(f.descricao) === normalizar(parcela ? parcela.base : d.descricao) && toCents(f.valor) === toCents(d.valor) && f.conta === d.conta,
  );
  if (fixa) add("Recorrência", `Despesa fixa · todo dia ${fixa.diaVencimento}`);

  if (d.moedaOriginal !== "BRL" && d.valorOriginal > 0) {
    add("Valor original", `${d.moedaOriginal} ${d.valorOriginal.toFixed(2).replace(".", ",")} (cotação ${String(d.cotacaoNaData).replace(".", ",")})`);
  }
  add("Lançado por", d.autor);
  if (d.conciliadoEm != null) add("Conciliação", `Conciliado em ${dia(d.conciliadoEm)}`);

  return {
    selo,
    valor: `${entrada ? "+" : "−"} ${brl.format(d.valor)}`,
    entrada,
    status,
    linhas,
    id: `MD-${String(d.id).slice(-6).padStart(6, "0")}`,
    emitidoEm: emissaoFmt.format(new Date(agora)).replace(",", " às"),
  };
}
