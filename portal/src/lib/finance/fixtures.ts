import type { Cartao, Conta, Dataset, Despesa } from "./types";
import { emptyDataset } from "./types";

/** Data local (mes 1-12) em epoch ms. */
export function dt(ano: number, mes: number, dia: number, hora = 12): number {
  return new Date(ano, mes - 1, dia, hora, 0, 0, 0).getTime();
}

export function conta(over: Partial<Conta> = {}): Conta {
  return { id: 1, saldo: 0, banco: "Banco A", pic: "", agencia: "0001", conta: "111", titular: "Teste", ...over };
}

export function cartao(over: Partial<Cartao> = {}): Cartao {
  return {
    id: 10,
    nome: "Cartão A",
    finalCartao: "1234",
    tipo: "CRÉDITO",
    limiteDisponivel: 1000,
    limiteTotal: 1000,
    diaFechamento: 25,
    diaVencimento: 5,
    contaId: 1,
    cartaoPrincipalId: null,
      ...over,
  };
}

let seq = 1;
export function desp(over: Partial<Despesa> = {}): Despesa {
  const data = over.data ?? dt(2025, 3, 10);
  const d = new Date(data);
  return {
    id: seq++,
    descricao: "Item",
    valor: 10,
    data,
    categoria: "Alimentação",
    conta: "111",
    pic: "",
    tipo: "DEBITO",
    mes: d.getMonth() + 1,
    ano: d.getFullYear(),
    cartaoId: null,
    valorOriginal: over.valor ?? 10,
    moedaOriginal: "BRL",
    cotacaoNaData: 1,
    pago: true,
    natureza: "NORMAL",
    grupoId: null,
    autor: null,
    fitid: null,
    conciliadoEm: null,
    ...over,
  };
}

export function dataset(over: Partial<Dataset> = {}): Dataset {
  return { ...emptyDataset(), ...over };
}
