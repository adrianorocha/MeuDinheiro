import { recalcularTudo, resumoFatura } from "./finance/calc";
import { MESES_ABREV, somaMeses } from "./finance/dates";
import {
  adicionarLancamento,
  agendarTransferencia,
  aportarMeta,
  criarCartao,
  criarConta,
  criarDespesaFixa,
  criarMeta,
  inserirItem,
  pagarFatura,
  processarDespesasFixas,
  transferir,
  type Ctx,
  type NovoLancamento,
} from "./finance/operations";
import { emptyDataset } from "./finance/types";
import type { Dataset, Result } from "./finance/types";
import { CATEGORIAS_PADRAO } from "./catalogo";

function dia(base: number, mesesAtras: number, d: number, hora = 12): number {
  const ref = new Date(somaMeses(base, -mesesAtras));
  const max = new Date(ref.getFullYear(), ref.getMonth() + 1, 0).getDate();
  return new Date(ref.getFullYear(), ref.getMonth(), Math.min(d, max), hora).getTime();
}

function deve<T extends { ds: Dataset }>(r: Result<T>): T {
  if (!r.ok) throw new Error(`Falha ao montar dados de exemplo: ${r.erro}`);
  return r;
}

/** Dataset de demonstração coerente (gerado pelas mesmas operações do app), relativo a `agora`. */
export function criarDatasetDemo(agora: number = Date.now()): Dataset {
  let ds = emptyDataset();
  const ctxEm = (t: number): Ctx => ({ agora: t });
  const origem = dia(agora, 6, 1);
  const c0 = ctxEm(origem);

  for (const c of CATEGORIAS_PADRAO) ds = { ...ds, categorias: inserirItem(ds.categorias, { nome: c.nome, pic: c.pic }, c0).lista };

  ds = deve(criarConta(ds, { banco: "Itaú", pic: "itau", agencia: "0123", conta: "98765-4", titular: "Titular Exemplo", saldoInicial: 4000 }, c0)).ds;
  ds = deve(criarConta(ds, { banco: "Nubank", pic: "nubank", agencia: "0001", conta: "12345-6", titular: "Titular Exemplo", saldoInicial: 6000 }, c0)).ds;
  ds = deve(criarConta(ds, { banco: "Caixa Econômica", pic: "caixa_economica", agencia: "0456", conta: "5555-1", titular: "Titular Exemplo", saldoInicial: 2500 }, c0)).ds;
  const [itau, nubank] = ds.contas;

  ds = deve(criarCartao(ds, { nome: "Nubank Platinum", finalCartao: "4321", tipo: "CRÉDITO", limiteTotal: 8000, diaFechamento: 25, diaVencimento: 5, contaId: nubank.id }, c0)).ds;
  ds = deve(criarCartao(ds, { nome: "Itaú Click", finalCartao: "9876", tipo: "MÚLTIPLO", limiteTotal: 5000, diaFechamento: 10, diaVencimento: 17, contaId: itau.id }, c0)).ds;
  const [cartNu, cartIt] = ds.cartoes;

  const add = (ctx: Ctx, l: NovoLancamento) => {
    ds = deve(adicionarLancamento(ds, l, ctx)).ds;
  };

  for (let atras = 5; atras >= 0; atras--) {
    const atual = atras === 0;
    const ctx = ctxEm(atual ? agora : dia(agora, atras, 28, 20));
    const d = (n: number) => dia(agora, atras, n);

    if (!atual) {
      // Paga as faturas do mês anterior no começo do mês.
      const pag = ctxEm(dia(agora, atras, 6));
      for (const c of ds.cartoes) {
        const anterior = new Date(somaMeses(agora, -(atras + 1)));
        const f = { mes: anterior.getMonth() + 1, ano: anterior.getFullYear() };
        if (resumoFatura(c, ds.despesas, f.mes, f.ano).emAberto > 0) {
          const r = pagarFatura(ds, c.id, f.mes, f.ano, pag);
          if (r.ok) ds = r.ds;
        }
      }
    }

    add(ctx, { descricao: "Salário", valor: 6500, data: d(5), categoria: "Salário", conta: itau.conta, pic: "salary", tipo: "CREDITO" });
    if (atras % 2 === 1) add(ctx, { descricao: "Projeto freelance", valor: 1200 + atras * 80, data: d(18), categoria: "Salário", conta: nubank.conta, pic: "salary", tipo: "CREDITO" });
    add(ctx, { descricao: "Supermercado", valor: 620 + atras * 37.9, data: d(8), categoria: "Supermercado", conta: itau.conta, pic: "supermarket", tipo: "DEBITO" });
    add(ctx, { descricao: "Combustível", valor: 210 + atras * 12.5, data: d(12), categoria: "Combustível", conta: itau.conta, pic: "fuel", tipo: "DEBITO" });
    add(ctx, { descricao: "Restaurante", valor: 95.9 + atras * 4, data: d(15), categoria: "Alimentação", cartaoId: cartNu.id, pic: "restaurant", tipo: "DEBITO" });
    add(ctx, { descricao: "Lanche", valor: 38.5, data: d(3), categoria: "Lanche", cartaoId: cartIt.id, pic: "lunch", tipo: "DEBITO" });
    add(ctx, { descricao: "Cinema", valor: 64, data: d(20), categoria: "Cinema", cartaoId: cartNu.id, pic: "cinema", tipo: "DEBITO" });
    if (!atual) {
      add(ctx, { descricao: "Aluguel", valor: 1800, data: d(28), categoria: "Moradia", conta: itau.conta, pic: "bank", tipo: "DEBITO" });
      add(ctx, { descricao: "Academia", valor: 120, data: d(10), categoria: "Academia", conta: nubank.conta, pic: "gym", tipo: "DEBITO" });
    }
    if (atras === 4) {
      add(ctx, { descricao: "Notebook", valor: 3600, data: d(11), categoria: "Compras", cartaoId: cartNu.id, pic: "shopping", tipo: "DEBITO", parcelas: 6 });
    }
    if (atras === 3) {
      ds = deve(transferir(ds, { origem: itau.conta, destino: nubank.conta, valor: 1000, data: d(14) }, ctx)).ds;
    }
    if (atras === 2) {
      add(ctx, { descricao: "Assinatura streaming (USD)", valor: 52, valorOriginal: 10, moedaOriginal: "USD", cotacaoNaData: 5.2, data: d(9), categoria: "Cinema", cartaoId: cartNu.id, pic: "cinema", tipo: "DEBITO" });
      add(ctx, { descricao: "Estorno compra", valor: 64, data: d(21), categoria: "Cinema", cartaoId: cartNu.id, pic: "cinema", tipo: "CREDITO" });
    }
  }

  const ctxAgora = ctxEm(agora);
  const metaViagem = deve(criarMeta(ds, { nome: "Viagem", valorObjetivo: 8000, icone: "ic_savings" }, ctxAgora));
  ds = metaViagem.ds;
  const metaReserva = deve(criarMeta(ds, { nome: "Reserva de emergência", valorObjetivo: 20000, icone: "ic_savings" }, ctxAgora));
  ds = metaReserva.ds;
  ds = deve(aportarMeta(ds, { metaId: metaViagem.meta.id, conta: itau.conta, valor: 1500 }, ctxEm(dia(agora, 2, 15)))).ds;
  ds = deve(aportarMeta(ds, { metaId: metaReserva.meta.id, conta: itau.conta, valor: 2000 }, ctxEm(dia(agora, 1, 15)))).ds;

  let invs = ds.investimentos;
  for (const i of [
    { nome: "Tesouro Selic 2029", tipo: "Renda Fixa", valorInvestido: 5000, valorAtual: 5430.25 },
    { nome: "PETR4", tipo: "Ações", valorInvestido: 3000, valorAtual: 3350.5 },
    { nome: "HGLG11", tipo: "FIIs", valorInvestido: 2000, valorAtual: 1950 },
    { nome: "Bitcoin", tipo: "Cripto", valorInvestido: 1000, valorAtual: 1380 },
  ]) {
    invs = inserirItem(invs, i, ctxAgora).lista;
  }
  ds = { ...ds, investimentos: invs };

  let orcs = ds.orcamentos;
  for (const [categoria, valorLimite] of [["Alimentação", 800], ["Supermercado", 1200], ["Combustível", 400], ["Cinema", 150], ["Compras", 600]] as const) {
    orcs = inserirItem(orcs, { categoria, valorLimite }, ctxAgora).lista;
  }
  ds = { ...ds, orcamentos: orcs };

  const fixa = (descricao: string, valor: number, conta: string, categoria: string, pic: string, diaVencimento: number) => {
    ds = deve(criarDespesaFixa(ds, { descricao, valor, conta, categoria, pic, tipo: "DEBITO", diaVencimento }, ctxAgora)).ds;
  };
  fixa("Aluguel", 1800, itau.conta, "Moradia", "bank", 28);
  fixa("Academia", 120, nubank.conta, "Academia", "gym", 10);
  fixa("Internet", 110, itau.conta, "Moradia", "bank", 15);
  ds = deve(processarDespesasFixas(ds, ctxAgora)).ds;

  const dias = (n: number) => agora + n * 86_400_000;
  add(ctxAgora, { descricao: "Conta de luz", valor: 187.4, data: dias(-5), categoria: "Moradia", conta: itau.conta, pic: "bank", tipo: "DEBITO", pago: false });
  add(ctxAgora, { descricao: "Seguro do carro", valor: 342, data: dias(3), categoria: "Transporte", conta: itau.conta, pic: "transport", tipo: "DEBITO", pago: false });
  ds = deve(agendarTransferencia(ds, { dataAgendada: dias(10), contaOrigem: itau.conta, contaDestino: nubank.conta, valor: 300 }, ctxAgora)).ds;

  // Evolução patrimonial: 11 meses anteriores (o do mês atual é mantido pela rotina de snapshot).
  let patr = ds.patrimonio;
  for (let atras = 11; atras >= 1; atras--) {
    const data = dia(agora, atras, 28);
    const mes = new Date(data).getMonth();
    patr = inserirItem(patr, { dataMillis: data, valorTotal: 9000 + (11 - atras) * 1350 + (mes % 3) * 220, mesReferencia: MESES_ABREV[mes] }, ctxAgora).lista;
  }
  ds = { ...ds, patrimonio: patr };

  return recalcularTudo(ds);
}
