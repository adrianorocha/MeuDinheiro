import { cartaoIdDe, cartoesDoGrupo, limiteGrupo, principalDe, resumoFatura } from "./calc";
import { rotuloCartao, recalcularSaldos, type CorrecaoSaldo } from "./conferencia";
import { fromCents, toCents } from "./money";
import { editarCartao, excluirLancamento } from "./operations";
import { faturasPagasComPendencias, pagamentosNoCartao } from "./reprocessar";
import type { Cartao, Dataset, Despesa } from "./types";

/**
 * R105 - auditoria dos cartões de crédito: acha POR QUE o limite/fatura não bate, aponta os lançamentos envolvidos
 * e oferece a correção. `auditarCartoes` é puro (não altera nada); `aplicarCorrecoesCartao` aplica só o que for pedido.
 */

export type TipoAchado =
  | "LIMITE_GRAVADO"
  | "ITEM_EM_FATURA_PAGA"
  | "PAGAMENTO_NO_CARTAO"
  | "POSSIVEL_DUPLICIDADE"
  | "CONTA_DIFERENTE"
  | "FATURA_PAGA_DIVERGENTE"
  | "LIMITE_FORA_DA_FAIXA"
  | "DIFERENCA_BANCO"
  | "CANDIDATO_BANCO";

export type AcaoCorrecao =
  | { tipo: "REGRAVAR_LIMITE" }
  | { tipo: "MARCAR_PAGOS"; ids: number[] }
  | { tipo: "MARCAR_ABERTOS"; ids: number[] }
  | { tipo: "REMOVER"; ids: number[] }
  | { tipo: "AJUSTAR_CONTA"; ids: number[]; conta: string }
  | { tipo: "AJUSTAR_LIMITE_TOTAL"; cartaoId: number; limiteTotal: number };

export interface Achado {
  /** Estável entre auditorias (serve de chave e de seleção na tela). */
  chave: string;
  tipo: TipoAchado;
  severidade: "erro" | "aviso" | "info";
  titulo: string;
  detalhe: string;
  /** Lançamentos envolvidos (para apontar na tela). */
  lancamentoIds: number[];
  /** Quanto o limite disponível do grupo MUDA ao aplicar a correção (R$; + libera, − consome). 0 quando não afeta. */
  impactoLimite: number;
  acao: AcaoCorrecao | null;
  /** Seguro aplicar em lote ("Corrigir o que é seguro"): só regrava/ajusta marcações; nunca apaga nem muda valor. */
  segura: boolean;
}

export interface AuditoriaGrupo {
  principalId: number;
  rotulo: string;
  limiteTotal: number;
  limiteCalculado: number;
  /** menor limite gravado entre os cartões do grupo (o que o app mostraria) */
  limiteGravado: number;
  /** Limite informado pelo usuário (app do banco), quando houver. */
  limiteBanco: number | null;
  /** limiteCalculado − limiteBanco (R$); > 0: o app mostra mais disponível que o banco. null sem limite informado. */
  diferencaBanco: number | null;
  achados: Achado[];
  ok: boolean;
}

export interface RelatorioCartoes {
  grupos: AuditoriaGrupo[];
  totalAchados: number;
  totalSeguros: number;
}

const normaliza = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/\s+/g, " ").trim();
const ehCredito = (c: Cartao) => !/D[ÉE]BITO/i.test(c.tipo);
const liquidoCents = (itens: readonly Despesa[]) => itens.reduce((s, d) => s + (d.tipo === "DEBITO" ? toCents(d.valor) : -toCents(d.valor)), 0);
const dia = (ms: number) => {
  const x = new Date(ms);
  return `${x.getFullYear()}-${x.getMonth() + 1}-${x.getDate()}`;
};
const mesAno = (m: number, a: number) => `${String(m).padStart(2, "0")}/${a}`;
const GRUPO_FATURA = /^fatura:(\d+):(\d{4})-(\d{2})$/;
const GRUPO_GERADO = /^(parc|fixa|rep):/;

function conferirGrupo(ds: Dataset, principal: Cartao, limiteBanco: number | null): AuditoriaGrupo {
  const grupo = cartoesDoGrupo(principal, ds.cartoes);
  const ids = new Set(grupo.map((c) => c.id));
  const doGrupo = ds.despesas.filter((d) => {
    const cid = cartaoIdDe(d);
    return cid !== null && ids.has(cid);
  });
  const calculado = limiteGrupo(principal, ds.cartoes, ds.despesas);
  const achados: Achado[] = [];
  const add = (a: Achado) => achados.push(a);
  const nome = rotuloCartao(principal);

  // 1) limite gravado diferente do calculado
  const divergentes = grupo.filter((c) => toCents(c.limiteDisponivel) !== toCents(calculado));
  if (divergentes.length > 0) {
    const gravado = divergentes[0].limiteDisponivel;
    add({
      chave: `${principal.id}:limite-gravado`,
      tipo: "LIMITE_GRAVADO",
      severidade: "erro",
      titulo: "Limite gravado diferente do calculado",
      detalhe: `Gravado ${brl(gravado)}; pelos lançamentos em aberto o correto é ${brl(calculado)} (diferença ${brl(fromCents(toCents(gravado) - toCents(calculado)))}). ${divergentes.map(rotuloCartao).join(", ")}.`,
      lancamentoIds: [],
      impactoLimite: 0,
      acao: { tipo: "REGRAVAR_LIMITE" },
      segura: true,
    });
  }

  // 2) itens em aberto de fatura já quitada (R70)
  for (const f of faturasPagasComPendencias(ds, { cartaoId: principal.id })) {
    add({
      chave: `${principal.id}:fatura-paga:${f.ano}-${f.mes}`,
      tipo: "ITEM_EM_FATURA_PAGA",
      severidade: "erro",
      titulo: `Fatura ${mesAno(f.mes, f.ano)} já paga, mas ${f.itens.length} item(ns) seguem em aberto`,
      detalhe: `Esses itens inflam o limite usado em ${brl(f.liquido)}. Marcar como pagos libera o limite sem criar pagamento nem mexer na conta.`,
      lancamentoIds: f.itens.map((d) => d.id),
      impactoLimite: fromCents(liquidoCents(f.itens)),
      acao: { tipo: "MARCAR_PAGOS", ids: f.itens.map((d) => d.id) },
      segura: true,
    });
  }

  // 3) pagamento de fatura importado como crédito dentro do cartão (abate em dobro)
  const pagos = pagamentosNoCartao(ds, { cartaoId: principal.id });
  for (const p of pagos) {
    add({
      chave: `${principal.id}:pagamento-no-cartao:${p.id}`,
      tipo: "PAGAMENTO_NO_CARTAO",
      severidade: "aviso",
      titulo: `Pagamento de fatura dentro do cartão: "${p.descricao}"`,
      detalhe: `Crédito de ${brl(p.valor)} em ${data(p.data)}. O pagamento já está na conta; dentro do cartão ele abate a fatura em dobro. Remover envia para a lixeira.`,
      lancamentoIds: [p.id],
      impactoLimite: p.pago ? 0 : -p.valor,
      acao: { tipo: "REMOVER", ids: [p.id] },
      segura: false,
    });
  }
  const idsPagos = new Set(pagos.map((p) => p.id));

  // 4) possível duplicidade (mesma compra em aberto repetida)
  const gruposDup = new Map<string, Despesa[]>();
  for (const d of doGrupo) {
    if (d.pago || d.tipo !== "DEBITO" || d.natureza !== "NORMAL" || idsPagos.has(d.id)) continue;
    if (d.grupoId && GRUPO_GERADO.test(d.grupoId)) continue;
    const k = `${cartaoIdDe(d)}|${dia(d.data)}|${toCents(d.valor)}|${normaliza(d.descricao)}`;
    const l = gruposDup.get(k) ?? [];
    l.push(d);
    gruposDup.set(k, l);
  }
  for (const [k, lista] of gruposDup) {
    if (lista.length < 2) continue;
    // lançamentos que o banco já distinguiu (FITIDs diferentes) não são duplicata
    const fits = lista.map((d) => d.fitid).filter((f): f is string => !!f);
    if (fits.length === lista.length && new Set(fits).size === fits.length) continue;
    const ordenada = [...lista].sort((a, b) => a.id - b.id);
    const extras = ordenada.slice(1);
    add({
      chave: `${principal.id}:duplicidade:${k}`,
      tipo: "POSSIVEL_DUPLICIDADE",
      severidade: "aviso",
      titulo: `Possível duplicidade: "${ordenada[0].descricao}" ${brl(ordenada[0].valor)} em ${data(ordenada[0].data)} (${ordenada.length}×)`,
      detalhe: `Mesma descrição, valor, dia e cartão. Se for repetição real, ignore; se for duplicata, remover as ${extras.length} cópia(s) devolve ${brl(fromCents(extras.length * toCents(ordenada[0].valor)))} ao limite (vão para a lixeira).`,
      lancamentoIds: ordenada.map((d) => d.id),
      impactoLimite: fromCents(extras.length * toCents(ordenada[0].valor)),
      acao: { tipo: "REMOVER", ids: extras.map((d) => d.id) },
      segura: false,
    });
  }

  // 5) compra do cartão registrada em outra conta
  const contaDoCartao = ds.contas.find((c) => c.id === principal.contaId);
  if (contaDoCartao) {
    const fora = doGrupo.filter((d) => d.conta !== contaDoCartao.conta);
    if (fora.length > 0) {
      add({
        chave: `${principal.id}:conta-diferente`,
        tipo: "CONTA_DIFERENTE",
        severidade: "info",
        titulo: `${fora.length} compra(s) do cartão em conta diferente da conta do cartão`,
        detalhe: `O cartão paga pela conta ${contaDoCartao.banco} · ${contaDoCartao.conta}. Isso distorce as pendências por conta; o limite não muda.`,
        lancamentoIds: fora.map((d) => d.id),
        impactoLimite: 0,
        acao: { tipo: "AJUSTAR_CONTA", ids: fora.map((d) => d.id), conta: contaDoCartao.conta },
        segura: false,
      });
    }
  }

  // 6) pagamento completo da fatura com valor diferente do total do ciclo (só informativo)
  const vistos = new Set<string>();
  for (const pg of ds.despesas) {
    if (pg.natureza !== "PAGAMENTO_FATURA" || !pg.grupoId) continue;
    const m = GRUPO_FATURA.exec(pg.grupoId);
    if (!m || Number(m[1]) !== principal.id) continue;
    const mes = Number(m[3]);
    const ano = Number(m[2]);
    if (vistos.has(`${ano}-${mes}`)) continue;
    vistos.add(`${ano}-${mes}`);
    const r = resumoFatura(principal, ds.despesas, mes, ano, ds.cartoes);
    const itensAteOPagamento = r.itens.filter((d) => d.data <= pg.data);
    const esperado = liquidoCents(itensAteOPagamento);
    if (esperado !== toCents(pg.valor)) {
      const dif = toCents(pg.valor) - esperado;
      add({
        chave: `${principal.id}:pagamento-divergente:${ano}-${mes}`,
        tipo: "FATURA_PAGA_DIVERGENTE",
        severidade: "aviso",
        titulo: `Fatura ${mesAno(mes, ano)}: pagamento ${brl(pg.valor)} × itens ${brl(fromCents(esperado))}`,
        detalhe: `O pagamento está ${dif > 0 ? "acima" : "abaixo"} do total dos itens da fatura em ${brl(Math.abs(fromCents(dif)))}. Confira se falta lançar uma compra (${dif > 0 ? "pagou a mais" : "pagou a menos"}) ou se houve juros/desconto. Não há correção automática.`,
        lancamentoIds: [pg.id, ...itensAteOPagamento.map((d) => d.id)],
        impactoLimite: 0,
        acao: null,
        segura: false,
      });
    }
  }

  // 7) limite calculado fora da faixa possível
  if (toCents(calculado) > toCents(principal.limiteTotal)) {
    const creditos = doGrupo.filter((d) => !d.pago && d.tipo === "CREDITO");
    add({
      chave: `${principal.id}:acima-do-total`,
      tipo: "LIMITE_FORA_DA_FAIXA",
      severidade: "aviso",
      titulo: "Limite disponível acima do limite total",
      detalhe: `Há mais créditos (estornos/pagamentos) em aberto do que compras: disponível ${brl(calculado)} para um limite de ${brl(principal.limiteTotal)}. Verifique os créditos listados.`,
      lancamentoIds: creditos.map((d) => d.id),
      impactoLimite: 0,
      acao: null,
      segura: false,
    });
  } else if (toCents(calculado) < 0) {
    add({
      chave: `${principal.id}:negativo`,
      tipo: "LIMITE_FORA_DA_FAIXA",
      severidade: "aviso",
      titulo: "Limite disponível negativo",
      detalhe: `As compras em aberto passam do limite total (${brl(calculado)}). Se o banco não mostra isso, há itens a mais em aberto (já pagos ou duplicados) ou o limite total está desatualizado.`,
      lancamentoIds: [],
      impactoLimite: 0,
      acao: null,
      segura: false,
    });
  }

  // 8) comparação com o limite informado pelo usuário (app do banco)
  let diferencaBanco: number | null = null;
  if (limiteBanco !== null) {
    const dif = toCents(calculado) - toCents(limiteBanco);
    diferencaBanco = fromCents(dif);
    if (dif !== 0) {
      const alvo = Math.abs(dif);
      // dif > 0: o banco tem MAIS usado que o app → candidatos: itens marcados como pagos que o banco ainda cobra;
      // dif < 0: o app tem MAIS usado que o banco → candidatos: itens em aberto que o banco já não considera.
      const candidatos = doGrupo.filter((d) => toCents(d.valor) === alvo && (dif > 0 ? d.pago : !d.pago));
      add({
        chave: `${principal.id}:diferenca-banco`,
        tipo: "DIFERENCA_BANCO",
        severidade: "erro",
        titulo: `Diferença de ${brl(Math.abs(fromCents(dif)))} para o limite do banco`,
        detalhe:
          `O app calcula ${brl(calculado)} disponível; o banco informa ${brl(limiteBanco)}. ` +
          (dif > 0 ? "O banco considera mais compras em aberto do que o app (falta lançar ou há item marcado como pago)." : "O app considera mais compras em aberto do que o banco (item já pago, duplicado ou estornado).") +
          (candidatos.length > 0 ? ` ${candidatos.length} lançamento(s) têm exatamente esse valor (abaixo).` : " Nenhum lançamento isolado tem exatamente esse valor."),
        lancamentoIds: candidatos.map((d) => d.id),
        impactoLimite: 0,
        acao: toCents(principal.limiteTotal) - dif > 0 ? { tipo: "AJUSTAR_LIMITE_TOTAL", cartaoId: principal.id, limiteTotal: fromCents(toCents(principal.limiteTotal) - dif) } : null,
        segura: false,
      });
      for (const d of candidatos) {
        add({
          chave: `${principal.id}:candidato:${d.id}`,
          tipo: "CANDIDATO_BANCO",
          severidade: "info",
          titulo: `Suspeito: "${d.descricao}" ${brl(d.valor)} em ${data(d.data)} (${d.pago ? "marcado como pago" : "em aberto"})`,
          detalhe: d.pago ? "Se o banco ainda cobra este valor, marque-o como em aberto: o limite usado sobe e passa a bater." : "Se o banco já não considera este valor (pago/estornado), marque-o como pago: o limite usado cai e passa a bater.",
          lancamentoIds: [d.id],
          impactoLimite: d.pago ? -d.valor : d.valor,
          acao: d.pago ? { tipo: "MARCAR_ABERTOS", ids: [d.id] } : { tipo: "MARCAR_PAGOS", ids: [d.id] },
          segura: false,
        });
      }
    }
  }

  return {
    principalId: principal.id,
    rotulo: nome,
    limiteTotal: principal.limiteTotal,
    limiteCalculado: calculado,
    limiteGravado: Math.min(...grupo.map((c) => c.limiteDisponivel)),
    limiteBanco,
    diferencaBanco,
    achados,
    ok: achados.every((a) => a.severidade === "info"),
  };
}

const brl = (v: number) => v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const data = (ms: number) => new Date(ms).toLocaleDateString("pt-BR");

/**
 * Audita todos os grupos de cartão de crédito. `limiteBanco` (opcional) = limite DISPONÍVEL que o banco mostra,
 * por id do cartão principal; com ele a auditoria também aponta de onde vem a diferença.
 */
export function auditarCartoes(ds: Dataset, limiteBanco: Readonly<Record<number, number | null | undefined>> = {}): RelatorioCartoes {
  const vistos = new Set<number>();
  const grupos: AuditoriaGrupo[] = [];
  for (const c of ds.cartoes) {
    const p = principalDe(c, ds.cartoes);
    if (vistos.has(p.id) || !ehCredito(p)) continue;
    vistos.add(p.id);
    const informado = limiteBanco[p.id];
    grupos.push(conferirGrupo(ds, p, informado === null || informado === undefined || !Number.isFinite(informado) ? null : informado));
  }
  const todos = grupos.flatMap((g) => g.achados);
  return { grupos, totalAchados: todos.filter((a) => a.severidade !== "info").length, totalSeguros: todos.filter((a) => a.segura).length };
}

export interface ResultadoCorrecaoCartao {
  ds: Dataset;
  correcoes: CorrecaoSaldo[];
  /** quantas ações foram aplicadas */
  aplicadas: number;
  /** ações que não puderam ser aplicadas (com o motivo) */
  falhas: string[];
}

/**
 * Aplica as ações dos achados escolhidos e regrava saldos/limites. Idempotente (ids que já não existem/estão no
 * estado pedido são ignorados). Remoções vão para a lixeira (R31); nada é apagado de vez.
 */
export function aplicarCorrecoesCartao(ds: Dataset, achados: readonly Achado[], opcoes: { agora?: number } = {}): ResultadoCorrecaoCartao {
  const agora = opcoes.agora ?? Date.now();
  let atual = ds;
  let aplicadas = 0;
  const falhas: string[] = [];
  const marcar = (ids: readonly number[], pago: boolean) => {
    const set = new Set(ids);
    let n = 0;
    atual = {
      ...atual,
      despesas: atual.despesas.map((d) => {
        if (!set.has(d.id) || cartaoIdDe(d) === null || d.pago === pago) return d;
        n++;
        return { ...d, pago };
      }),
    };
    return n;
  };
  for (const a of achados) {
    const acao = a.acao;
    if (!acao) continue;
    switch (acao.tipo) {
      case "REGRAVAR_LIMITE":
        aplicadas++;
        break;
      case "MARCAR_PAGOS":
        if (marcar(acao.ids, true) > 0) aplicadas++;
        break;
      case "MARCAR_ABERTOS":
        if (marcar(acao.ids, false) > 0) aplicadas++;
        break;
      case "REMOVER": {
        let n = 0;
        for (const id of acao.ids) {
          const d = atual.despesas.find((x) => x.id === id);
          if (!d || cartaoIdDe(d) === null) continue;
          const r = excluirLancamento(atual, id, {}, { agora });
          if (r.ok) {
            atual = r.ds;
            n++;
          } else falhas.push(`${a.titulo}: ${r.erro}`);
        }
        if (n > 0) aplicadas++;
        break;
      }
      case "AJUSTAR_CONTA": {
        const set = new Set(acao.ids);
        let n = 0;
        atual = { ...atual, despesas: atual.despesas.map((d) => (set.has(d.id) && cartaoIdDe(d) !== null && d.conta !== acao.conta ? (n++, { ...d, conta: acao.conta }) : d)) };
        if (n > 0) aplicadas++;
        break;
      }
      case "AJUSTAR_LIMITE_TOTAL": {
        const r = editarCartao(atual, acao.cartaoId, { limiteTotal: acao.limiteTotal });
        if (r.ok) {
          atual = r.ds;
          aplicadas++;
        } else falhas.push(`${a.titulo}: ${r.erro}`);
        break;
      }
    }
  }
  const rec = recalcularSaldos(atual);
  // correções contra o dataset ORIGINAL (marcar pago/remover também muda limites)
  const correcoes: CorrecaoSaldo[] = [];
  rec.ds.contas.forEach((c) => {
    const antes = ds.contas.find((x) => x.id === c.id);
    if (antes && toCents(antes.saldo) !== toCents(c.saldo)) correcoes.push({ tipo: "conta", id: c.id, rotulo: `${c.banco} · ${c.conta}`, antes: antes.saldo, depois: c.saldo });
  });
  rec.ds.cartoes.forEach((c) => {
    const antes = ds.cartoes.find((x) => x.id === c.id);
    if (antes && toCents(antes.limiteDisponivel) !== toCents(c.limiteDisponivel)) correcoes.push({ tipo: "cartao", id: c.id, rotulo: rotuloCartao(c), antes: antes.limiteDisponivel, depois: c.limiteDisponivel });
  });
  return { ds: rec.ds, correcoes, aplicadas, falhas };
}

/** Atalho: aplica só as correções seguras (regravar limite e marcar itens de fatura paga). */
export function corrigirSeguras(ds: Dataset, opcoes: { agora?: number } = {}): ResultadoCorrecaoCartao & { achados: Achado[] } {
  const achados = auditarCartoes(ds).grupos.flatMap((g) => g.achados).filter((a) => a.segura);
  return { ...aplicarCorrecoesCartao(ds, achados, opcoes), achados };
}
