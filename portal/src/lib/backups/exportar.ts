import type { Dataset } from "../finance/types";
import { criarZip, type ArquivoZip } from "./zip";

/**
 * R66 - "Exportar tudo": um CSV por coleção (mesmo formato do app: UTF-8 com BOM, separador `;`, vírgula decimal,
 * datas dd/mm/aaaa), empacotados num ZIP sem compressão.
 */

const BOM = "﻿";
const p2 = (n: number) => String(n).padStart(2, "0");

export function dataBr(ms: number | null | undefined): string {
  if (ms == null || !(ms > 0)) return "";
  const d = new Date(ms);
  return `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

/** Escapa um campo: aspas se tiver `;`, aspas, quebra de linha ou espaço nas pontas. */
export function campoCsv(v: unknown): string {
  let t: string;
  if (v === null || v === undefined) t = "";
  else if (typeof v === "number") t = Number.isInteger(v) ? String(v) : v.toFixed(2).replace(".", ",");
  else if (typeof v === "boolean") t = v ? "sim" : "não";
  else t = String(v);
  return /[";\n\r]/.test(t) || t !== t.trim() ? `"${t.replace(/"/g, '""')}"` : t;
}

export function montarCsv(cabecalho: readonly string[], linhas: readonly (readonly unknown[])[]): string {
  const todas = [cabecalho, ...linhas].map((l) => l.map(campoCsv).join(";"));
  return `${BOM}${todas.join("\r\n")}\r\n`;
}

/** Um CSV por coleção, indexado pelo nome do arquivo. Coleções vazias saem só com o cabeçalho. */
export function csvsDoDataset(ds: Dataset): Record<string, string> {
  const r: Record<string, string> = {};
  r["despesas.csv"] = montarCsv(
    ["id", "data", "descricao", "valor", "tipo", "categoria", "conta", "cartao_id", "pago", "natureza", "tags", "autor", "grupo_id", "moeda", "valor_original"],
    [...ds.despesas].sort((a, b) => a.data - b.data).map((d) => [d.id, dataBr(d.data), d.descricao, d.valor, d.tipo, d.categoria, d.conta, d.cartaoId, d.pago, d.natureza, (d.tags ?? []).join(","), d.autor, d.grupoId, d.moedaOriginal, d.valorOriginal]),
  );
  r["contas.csv"] = montarCsv(["id", "banco", "agencia", "conta", "titular", "saldo"], ds.contas.map((c) => [c.id, c.banco, c.agencia, c.conta, c.titular, c.saldo]));
  r["cartoes.csv"] = montarCsv(
    ["id", "nome", "final", "tipo", "limite_total", "limite_disponivel", "dia_fechamento", "dia_vencimento", "conta_id", "cartao_principal_id", "limite_proprio"],
    ds.cartoes.map((c) => [c.id, c.nome, c.finalCartao, c.tipo, c.limiteTotal, c.limiteDisponivel, c.diaFechamento, c.diaVencimento, c.contaId, c.cartaoPrincipalId, c.limiteProprio]),
  );
  r["categorias.csv"] = montarCsv(["id", "nome", "icone"], ds.categorias.map((c) => [c.id, c.nome, c.pic]));
  r["orcamentos.csv"] = montarCsv(["id", "categoria", "limite"], ds.orcamentos.map((o) => [o.id, o.categoria, o.valorLimite]));
  r["metas.csv"] = montarCsv(["id", "nome", "objetivo", "guardado", "data_alvo"], ds.metas.map((m) => [m.id, m.nome, m.valorObjetivo, m.valorGuardado, dataBr(m.dataAlvo)]));
  r["investimentos.csv"] = montarCsv(["id", "nome", "tipo", "investido", "atual"], ds.investimentos.map((i) => [i.id, i.nome, i.tipo, i.valorInvestido, i.valorAtual]));
  r["recorrencias.csv"] = montarCsv(
    ["id", "descricao", "valor", "tipo", "conta", "categoria", "dia_vencimento", "cartao_id", "ultimo_lancamento", "pausada", "pausada_ate"],
    ds.despesasFixas.map((f) => [f.id, f.descricao, f.valor, f.tipo, f.conta, f.categoria, f.diaVencimento, f.cartaoId, dataBr(f.ultimaDataLancamento), f.pausada === true, dataBr(f.pausadaAte)]),
  );
  r["dividas.csv"] = montarCsv(
    ["id", "nome", "tipo", "credor", "saldo_devedor", "valor_original", "juros_mensal_pct", "parcela_minima", "parcelas_restantes", "dia_vencimento", "conta", "ativa"],
    ds.dividas.map((d) => [d.id, d.nome, d.tipo, d.credor, d.saldoDevedor, d.valorOriginal, d.taxaJurosMensal, d.parcelaMinima, d.parcelasRestantes, d.diaVencimento, d.conta, d.ativa]),
  );
  r["pessoas.csv"] = montarCsv(["id", "nome", "contato"], ds.pessoas.map((p) => [p.id, p.nome, p.contato]));
  r["acertos.csv"] = montarCsv(
    ["id", "pessoa_id", "tipo", "valor", "descricao", "data", "despesa_id", "quitado", "quitado_em", "grupo_rateio"],
    ds.acertos.map((a) => [a.id, a.pessoaId, a.tipo, a.valor, a.descricao, dataBr(a.data), a.despesaId, a.quitado, dataBr(a.quitadoEm), a.grupoRateio]),
  );
  r["projetos.csv"] = montarCsv(["id", "nome", "tag", "orcamento", "inicio", "fim", "ativo"], ds.projetos.map((p) => [p.id, p.nome, p.tag, p.orcamento, dataBr(p.inicio), dataBr(p.fim), p.ativo]));
  r["regras.csv"] = montarCsv(
    ["id", "padrao", "tipo_match", "categoria", "conta", "tipo", "ativa", "prioridade"],
    ds.regrasCategoria.map((g) => [g.id, g.padrao, g.tipoMatch, g.categoria, g.conta, g.tipo, g.ativa, g.prioridade]),
  );
  r["transferencias_agendadas.csv"] = montarCsv(
    ["id", "data", "origem", "destino", "valor", "executada"],
    ds.transferenciasAgendadas.map((t) => [t.id, dataBr(t.dataAgendada), t.contaOrigem, t.contaDestino, t.valor, t.executada]),
  );
  r["patrimonio.csv"] = montarCsv(["id", "data", "valor_total", "mes"], ds.patrimonio.map((p) => [p.id, dataBr(p.dataMillis), p.valorTotal, p.mesReferencia]));
  r["lixeira.csv"] = montarCsv(["id", "descricao", "valor", "excluido_em"], ds.lixeira.map((l) => [l.id, l.descricao, l.valor, dataBr(l.excluidoEm)]));
  return r;
}

export function zipDoDataset(ds: Dataset, quando: Date = new Date()): Uint8Array {
  const enc = new TextEncoder();
  const arquivos: ArquivoZip[] = Object.entries(csvsDoDataset(ds)).map(([nome, texto]) => ({ nome, dados: enc.encode(texto) }));
  return criarZip(arquivos, quando);
}
