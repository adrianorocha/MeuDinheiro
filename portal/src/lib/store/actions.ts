import { normalizaNome } from "../finance/calc";
import {
  adiantarOcorrenciaFixa,
  anteciparPagamento,
  type Antecipacao,
  type AdiantamentoFixa,
  adicionarLancamento,
  duplicarLancamento,
  esvaziarLixeira,
  repetirLancamento,
  restaurarDaLixeira,
  type UnidadeRepeticao,
  agendarTransferencia,
  alternarPago,
  aportarMeta,
  criarCartao,
  criarConta,
  criarDespesaFixa,
  criarMeta,
  editarDespesaFixa,
  editarCartao,
  editarConta,
  editarLancamento,
  excluirCartao,
  excluirConta,
  excluirLancamento,
  excluirMeta,
  executarAgendadasVencidas,
  inserirItem,
  marcarPago,
  pagarFatura,
  pagarItensFatura,
  type PagamentoItensFatura,
  processarDespesasFixas,
  removerItem,
  transferir,
  atualizarItem,
  ajustarSaldoConta,
  type AjusteSaldo,
  type EdicaoConta,
  type EdicaoLancamento,
  type NovaConta,
  type NovaDespesaFixa,
  type NovaTransferencia,
  type NovoCartao,
  type NovoLancamento,
} from "../finance/operations";
import { auditarSaldos, recalcularSaldos } from "../finance/conferencia";
import { round2 } from "../finance/money";
import type { Categoria, DespesaFixa, Investimento, Meta } from "../finance/types";
import { aplicarConciliacao, desfazerConciliacao, desfazerLote, type Plano, type PlanoInverso } from "../conciliacao/plano";
import { useSession } from "../session";
import { useStore } from "./store";

/** Quem está lançando (R32); null no modo local. */
function autorAtual(): string | null {
  const s = useSession.getState();
  return s.modo === "firebase" ? (s.user?.email ?? null) : null;
}

function exec() {
  return useStore.getState().executar;
}

type Falha = { ok: false; erro: string };
const falha = (erro: string): Falha => ({ ok: false, erro });

/** Ações de domínio: aplicam as regras puras (R1–R17) e persistem o resultado. */
export const acoes = {
  // lançamentos
  addLancamento: (input: NovoLancamento) => exec()((ds, ctx) => adicionarLancamento(ds, { autor: autorAtual(), ...input }, ctx)),
  editLancamento: (id: number, patch: EdicaoLancamento) => exec()((ds) => editarLancamento(ds, id, patch)),
  removerLancamento: (id: number, opcoes?: { grupoParcelas?: boolean }) => exec()((ds, ctx) => excluirLancamento(ds, id, opcoes, ctx)),
  // conciliação de extratos (R38)
  aplicarConciliacao: (plano: Plano) => exec()((ds, ctx) => aplicarConciliacao(ds, plano, ctx.agora)),
  desfazerLoteConciliacao: (inverso: PlanoInverso) => exec()((ds) => ({ ok: true as const, ds: desfazerLote(ds, inverso) })),
  desfazerConciliacao: (id: number) => exec()((ds) => desfazerConciliacao(ds, id)),
  duplicarLancamento: (id: number) => exec()((ds, ctx) => duplicarLancamento(ds, id, ctx)),
  repetirLancamento: (id: number, opcoes: { n: number; intervalo: number; unidade: UnidadeRepeticao }) =>
    exec()((ds, ctx) => repetirLancamento(ds, id, opcoes, ctx)),
  restaurarLixeira: (id: number) => exec()((ds, ctx) => restaurarDaLixeira(ds, id, ctx)),
  esvaziarLixeira: () => exec()((ds) => ({ ok: true as const, ds: esvaziarLixeira(ds) })),
  alternarPago: (id: number) => exec()((ds) => alternarPago(ds, id)),
  darBaixa: (id: number) => exec()((ds) => marcarPago(ds, id)),
  // antecipação de pagamento (R40)
  anteciparPagamento: (input: Antecipacao) => exec()((ds, ctx) => anteciparPagamento(ds, input, ctx)),
  adiantarOcorrenciaFixa: (input: AdiantamentoFixa) => exec()((ds, ctx) => adiantarOcorrenciaFixa(ds, input, ctx)),

  // contas
  criarConta: (input: NovaConta) => exec()((ds, ctx) => criarConta(ds, input, ctx)),
  editarConta: (id: number, patch: EdicaoConta) => exec()((ds) => editarConta(ds, id, patch)),
  excluirConta: (id: number, forcar = false) => exec()((ds) => excluirConta(ds, id, { forcar })),
  ajustarSaldoConta: (input: AjusteSaldo) => exec()((ds, ctx) => ajustarSaldoConta(ds, input, ctx)),
  transferir: (input: NovaTransferencia) => exec()((ds, ctx) => transferir(ds, input, ctx)),
  agendarTransferencia: (input: { dataAgendada: number; contaOrigem: string; contaDestino: string; valor: number }) =>
    exec()((ds, ctx) => agendarTransferencia(ds, input, ctx)),
  executarAgendadas: () => exec()((ds, ctx) => executarAgendadasVencidas(ds, ctx)),
  excluirAgendada: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, transferenciasAgendadas: removerItem(ds.transferenciasAgendadas, id) } })),

  // cartões
  criarCartao: (input: NovoCartao) => exec()((ds, ctx) => criarCartao(ds, input, ctx)),
  editarCartao: (id: number, patch: Partial<NovoCartao>) => exec()((ds) => editarCartao(ds, id, patch)),
  excluirCartao: (id: number, forcar = false) => exec()((ds) => excluirCartao(ds, id, { forcar })),
  pagarFatura: (cartaoId: number, mes: number, ano: number) => exec()((ds, ctx) => pagarFatura(ds, cartaoId, mes, ano, ctx)),
  pagarItensFatura: (input: PagamentoItensFatura) => exec()((ds, ctx) => pagarItensFatura(ds, input, ctx)),

  // orçamentos (um por categoria)
  salvarOrcamento: (categoria: string, valorLimite: number) =>
    exec()((ds, ctx) => {
      if (!categoria.trim()) return falha("Selecione a categoria.");
      if (!(round2(valorLimite) > 0)) return falha("O limite do orçamento deve ser maior que zero.");
      const alvo = normalizaNome(categoria);
      const existente = ds.orcamentos.find((o) => normalizaNome(o.categoria) === alvo);
      if (existente) {
        return { ok: true as const, ds: { ...ds, orcamentos: atualizarItem(ds.orcamentos, existente.id, { valorLimite: round2(valorLimite) }) } };
      }
      const { lista } = inserirItem(ds.orcamentos, { categoria: categoria.trim(), valorLimite: round2(valorLimite) }, ctx);
      return { ok: true as const, ds: { ...ds, orcamentos: lista } };
    }),
  excluirOrcamento: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, orcamentos: removerItem(ds.orcamentos, id) } })),

  // metas
  criarMeta: (input: { nome: string; valorObjetivo: number; icone?: string; dataAlvo?: number | null }) => exec()((ds, ctx) => criarMeta(ds, input, ctx)),
  editarMeta: (id: number, patch: Partial<Pick<Meta, "nome" | "valorObjetivo" | "dataAlvo">>) =>
    exec()((ds) => {
      if (patch.nome !== undefined && !patch.nome.trim()) return falha("Informe o nome da meta.");
      if (patch.valorObjetivo !== undefined && !(patch.valorObjetivo > 0)) return falha("O objetivo deve ser maior que zero.");
      return {
        ok: true as const,
        ds: {
          ...ds,
          metas: atualizarItem(ds.metas, id, {
            ...(patch.nome !== undefined ? { nome: patch.nome.trim() } : {}),
            ...(patch.valorObjetivo !== undefined ? { valorObjetivo: round2(patch.valorObjetivo) } : {}),
            ...(patch.dataAlvo !== undefined ? { dataAlvo: patch.dataAlvo } : {}),
          }),
        },
      };
    }),
  aportarMeta: (input: { metaId: number; conta: string; valor: number }) => exec()((ds, ctx) => aportarMeta(ds, input, ctx)),
  excluirMeta: (id: number, contaDestino: string | null) => exec()((ds, ctx) => excluirMeta(ds, id, contaDestino, ctx)),

  // investimentos
  salvarInvestimento: (dados: Omit<Investimento, "id">, id?: number) =>
    exec()((ds, ctx) => {
      if (!dados.nome.trim()) return falha("Informe o nome do investimento.");
      if (!(dados.valorInvestido >= 0) || !(dados.valorAtual >= 0)) return falha("Valores inválidos.");
      const limpo = { ...dados, nome: dados.nome.trim(), valorInvestido: round2(dados.valorInvestido), valorAtual: round2(dados.valorAtual) };
      if (id !== undefined) return { ok: true as const, ds: { ...ds, investimentos: atualizarItem(ds.investimentos, id, limpo) } };
      return { ok: true as const, ds: { ...ds, investimentos: inserirItem(ds.investimentos, limpo, ctx).lista } };
    }),
  atualizarValorInvestimento: (id: number, valorAtual: number) =>
    exec()((ds) => {
      if (!(valorAtual >= 0)) return falha("Valor inválido.");
      return { ok: true as const, ds: { ...ds, investimentos: atualizarItem(ds.investimentos, id, { valorAtual: round2(valorAtual) }) } };
    }),
  excluirInvestimento: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, investimentos: removerItem(ds.investimentos, id) } })),

  // recorrências
  criarDespesaFixa: (input: NovaDespesaFixa) =>
    exec()((ds, ctx) => {
      const r = criarDespesaFixa(ds, input, ctx);
      if (!r.ok) return r;
      const p = processarDespesasFixas(r.ds, ctx);
      return p.ok ? { ok: true as const, ds: p.ds, fixa: r.fixa, criados: p.criados } : p;
    }),
  editarDespesaFixa: (id: number, patch: Partial<Omit<DespesaFixa, "id" | "ultimaDataLancamento">>) =>
    exec()((ds) => editarDespesaFixa(ds, id, patch)),
  excluirDespesaFixa: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, despesasFixas: removerItem(ds.despesasFixas, id) } })),
  processarFixas: () => exec()((ds, ctx) => processarDespesasFixas(ds, ctx)),

  // conferência de saldos (R45)
  conferirSaldos: () => auditarSaldos(useStore.getState().ds, Date.now()),
  recalcularSaldos: () =>
    exec()((ds) => {
      const r = recalcularSaldos(ds);
      return { ok: true as const, ds: r.ds, correcoes: r.correcoes };
    }),

  // categorias
  salvarCategoria: (dados: { nome: string; pic: string }, id?: number) =>
    exec()((ds, ctx) => {
      const nome = dados.nome.trim();
      if (!nome) return falha("Informe o nome da categoria.");
      const alvo = normalizaNome(nome);
      if (ds.categorias.some((c) => c.id !== id && normalizaNome(c.nome) === alvo)) return falha("Já existe uma categoria com esse nome.");
      const limpo: Omit<Categoria, "id"> = { nome, pic: dados.pic };
      if (id !== undefined) return { ok: true as const, ds: { ...ds, categorias: atualizarItem(ds.categorias, id, limpo) } };
      return { ok: true as const, ds: { ...ds, categorias: inserirItem(ds.categorias, limpo, ctx).lista } };
    }),
  excluirCategoria: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, categorias: removerItem(ds.categorias, id) } })),
  adicionarCategoriasPadrao: (itens: readonly { nome: string; pic: string }[]) =>
    exec()((ds, ctx) => {
      let lista = ds.categorias;
      for (const i of itens) lista = inserirItem(lista, { nome: i.nome, pic: i.pic }, ctx).lista;
      return { ok: true as const, ds: { ...ds, categorias: lista } };
    }),
};
