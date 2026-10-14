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
  marcarItensFaturaComoPagos,
  type PagamentoItensFatura,
  pausarRecorrencia,
  retomarRecorrencia,
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
import {
  excluirDivida,
  pagarDivida,
  registrarJurosDivida,
  salvarDivida,
  type DadosDivida,
  type PagamentoDivida,
} from "../finance/dividas";
import {
  criarAcertoAvulso,
  desfazerRateio,
  dividirDespesa,
  excluirAcerto,
  excluirPessoa,
  quitarAcerto,
  salvarPessoa,
  vincularLancamentos,
  type VinculoLancamentos,
  type AcertoAvulso,
  type DivisaoDespesa,
  type QuitacaoAcerto,
} from "../finance/rateios";
import { aplicarMudancas, desfazerMudancas, novaRegraAprendida, validarRegra, type MudancaRegra, type SugestaoRegra } from "../finance/regras";
import { chaveTag, normalizarTags, validarProjeto } from "../finance/tags";
import type { Categoria, DespesaFixa, Investimento, Meta, Projeto, RegraCategoria } from "../finance/types";
import { aplicarConciliacao, desfazerConciliacao, desfazerLote, type Plano, type PlanoInverso } from "../conciliacao/plano";
import { useSession } from "../session";
import { estamparNovos, type Identidade } from "../finance/autoria";
import { limparAnexosDaLixeira } from "../firebase/anexosArquivos";
import { useStore } from "./store";
import { trilhaStore } from "../backups/instancias";

/** Quem está lançando (R32); null no modo local. */
function autorAtual(): string | null {
  const s = useSession.getState();
  return s.modo === "firebase" ? (s.user?.email ?? null) : null;
}

/** R67: identidade de quem lança (uid + nome); null no modo local (autor vazio). */
function identidadeAtual(): Identidade | null {
  const s = useSession.getState();
  return s.modo === "firebase" && s.user ? { uid: s.user.uid, nome: s.user.email ?? "Membro" } : null;
}

type Executar = ReturnType<typeof useStore.getState>["executar"];

/**
 * Ponto ÚNICO de execução das ações do usuário: os lançamentos NOVOS ganham autor/autorUid (R67) quando vazios.
 * `modo` "executor" (duplicar/repetir): o autor é quem executa, nunca o do original; "nenhum": não carimba
 * (restaurar da lixeira, antecipação - itens derivados mantêm o autor original).
 */
function exec(modo: "preencher" | "executor" | "nenhum" = "preencher"): Executar {
  const executar = useStore.getState().executar;
  if (modo === "nenhum") return executar;
  return ((fn) =>
    executar((ds, ctx) => {
      const r = fn(ds, ctx);
      return r.ok ? { ...r, ds: estamparNovos(ds, r.ds, identidadeAtual(), modo) } : r;
    })) as Executar;
}

/** R66: ponto ÚNICO da trilha de alterações. Roda a operação e registra o diff dos lançamentos; falha de log nunca propaga. */
function rastreado<R extends { ok: boolean }>(run: () => R, dica?: "RESTAURAR_LIXEIRA"): R {
  const antes = useStore.getState().ds;
  const r = run();
  if (r.ok) {
    try {
      trilhaStore.registrarDiff(antes, useStore.getState().ds, Date.now(), dica);
    } catch {
      /* auditoria é informativa */
    }
  }
  return r;
}

type Falha = { ok: false; erro: string };
const falha = (erro: string): Falha => ({ ok: false, erro });

/** Ações de domínio: aplicam as regras puras (R1–R17) e persistem o resultado. */
export const acoes = {
  // lançamentos
  addLancamento: (input: NovoLancamento) => rastreado(() => exec()((ds, ctx) => adicionarLancamento(ds, { autor: autorAtual(), ...input }, ctx))),
  editLancamento: (id: number, patch: EdicaoLancamento) => rastreado(() => exec()((ds) => editarLancamento(ds, id, patch))),
  removerLancamento: (id: number, opcoes?: { grupoParcelas?: boolean }) => rastreado(() => exec()((ds, ctx) => excluirLancamento(ds, id, opcoes, ctx))),
  // conciliação de extratos (R38)
  aplicarConciliacao: (plano: Plano) => exec()((ds, ctx) => aplicarConciliacao(ds, plano, ctx.agora)),
  desfazerLoteConciliacao: (inverso: PlanoInverso) => exec()((ds) => ({ ok: true as const, ds: desfazerLote(ds, inverso) })),
  desfazerConciliacao: (id: number) => exec()((ds) => desfazerConciliacao(ds, id)),
  duplicarLancamento: (id: number) => rastreado(() => exec("executor")((ds, ctx) => duplicarLancamento(ds, id, ctx))),
  repetirLancamento: (id: number, opcoes: { n: number; intervalo: number; unidade: UnidadeRepeticao }) =>
    rastreado(() => exec("executor")((ds, ctx) => repetirLancamento(ds, id, opcoes, ctx))),
  restaurarLixeira: (id: number) => rastreado(() => exec("nenhum")((ds, ctx) => restaurarDaLixeira(ds, id, ctx)), "RESTAURAR_LIXEIRA"),
  esvaziarLixeira: () => {
    const antes = useStore.getState().ds.lixeira;
    const r = exec()((ds) => ({ ok: true as const, ds: esvaziarLixeira(ds) }));
    if (r.ok) void limparAnexosDaLixeira(antes, []); // R60: apaga os arquivos dos anexos (melhor esforço)
    return r;
  },
  alternarPago: (id: number) => rastreado(() => exec()((ds) => alternarPago(ds, id))),
  darBaixa: (id: number) => rastreado(() => exec()((ds) => marcarPago(ds, id))),
  // antecipação de pagamento (R40)
  anteciparPagamento: (input: Antecipacao) => exec("nenhum")((ds, ctx) => anteciparPagamento(ds, input, ctx)),
  adiantarOcorrenciaFixa: (input: AdiantamentoFixa) => exec("nenhum")((ds, ctx) => adiantarOcorrenciaFixa(ds, input, ctx)),

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
  marcarItensFaturaComoPagos: (input: PagamentoItensFatura) => exec()((ds) => marcarItensFaturaComoPagos(ds, input)),

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
  // R47 - pausar/retomar recorrência
  pausarRecorrencia: (id: number, ate?: number | null) => exec()((ds, ctx) => pausarRecorrencia(ds, id, ate, ctx)),
  retomarRecorrencia: (id: number) => exec()((ds, ctx) => retomarRecorrencia(ds, id, ctx)),
  processarFixas: () => exec()((ds, ctx) => processarDespesasFixas(ds, ctx)),

  // conferência de saldos (R45)
  conferirSaldos: () => auditarSaldos(useStore.getState().ds, Date.now()),
  recalcularSaldos: () =>
    exec()((ds) => {
      const r = recalcularSaldos(ds);
      return { ok: true as const, ds: r.ds, correcoes: r.correcoes };
    }),

  // R55 - regras de categorização
  salvarRegra: (dados: Omit<RegraCategoria, "id" | "criadaEm">, id?: number) =>
    exec()((ds, ctx) => {
      const limpo = { ...dados, padrao: dados.padrao.trim(), categoria: dados.categoria.trim(), conta: dados.conta?.trim() || null, tipo: dados.tipo ?? null };
      const msg = validarRegra(limpo);
      if (msg) return falha(msg);
      if (id !== undefined) {
        if (!ds.regrasCategoria.some((r) => r.id === id)) return falha("Regra não encontrada.");
        return { ok: true as const, ds: { ...ds, regrasCategoria: atualizarItem(ds.regrasCategoria, id, limpo) } };
      }
      return { ok: true as const, ds: { ...ds, regrasCategoria: inserirItem(ds.regrasCategoria, { ...limpo, criadaEm: ctx.agora }, ctx).lista } };
    }),
  alternarRegra: (id: number) =>
    exec()((ds) => {
      const r = ds.regrasCategoria.find((x) => x.id === id);
      if (!r) return falha("Regra não encontrada.");
      return { ok: true as const, ds: { ...ds, regrasCategoria: atualizarItem(ds.regrasCategoria, id, { ativa: !r.ativa }) } };
    }),
  /** Muda a prioridade em +1/-1 (a regra de maior prioridade vence). */
  ajustarPrioridadeRegra: (id: number, delta: number) =>
    exec()((ds) => {
      const r = ds.regrasCategoria.find((x) => x.id === id);
      if (!r) return falha("Regra não encontrada.");
      return { ok: true as const, ds: { ...ds, regrasCategoria: atualizarItem(ds.regrasCategoria, id, { prioridade: r.prioridade + delta }) } };
    }),
  excluirRegra: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, regrasCategoria: removerItem(ds.regrasCategoria, id) } })),
  criarRegraAprendida: (s: SugestaoRegra) =>
    exec()((ds, ctx) => {
      const nova = novaRegraAprendida(s, 0, ctx.agora);
      const { id: _id, ...sem } = nova;
      void _id;
      return { ok: true as const, ds: { ...ds, regrasCategoria: inserirItem(ds.regrasCategoria, sem, ctx).lista } };
    }),
  /** Aplica a prévia confirmada; devolve o que é preciso para "Desfazer" imediato. */
  aplicarRegrasExistentes: (mudancas: readonly MudancaRegra[]) =>
    exec()((ds) => {
      const r = aplicarMudancas(ds.despesas, mudancas);
      return { ok: true as const, ds: { ...ds, despesas: r.despesas }, desfazer: r.desfazer };
    }),
  desfazerRegrasExistentes: (desfazer: readonly { id: number; categoria: string }[]) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, despesas: desfazerMudancas(ds.despesas, desfazer) } })),

  // R56 - projetos
  salvarProjeto: (dados: Omit<Projeto, "id" | "tag"> & { tag: string }, id?: number) =>
    exec()((ds, ctx) => {
      const tagRotulo = normalizarTags([dados.tag])[0] ?? "";
      const limpo: Omit<Projeto, "id"> = {
        nome: dados.nome.trim(),
        tag: chaveTag(tagRotulo),
        orcamento: dados.orcamento != null && dados.orcamento > 0 ? round2(dados.orcamento) : null,
        inicio: dados.inicio ?? null,
        fim: dados.fim ?? null,
        ativo: dados.ativo,
        cor: dados.cor ?? null,
      };
      const msg = validarProjeto(limpo, ds.projetos, id);
      if (msg) return falha(msg);
      if (id !== undefined) {
        if (!ds.projetos.some((p) => p.id === id)) return falha("Projeto não encontrado.");
        return { ok: true as const, ds: { ...ds, projetos: atualizarItem(ds.projetos, id, limpo) } };
      }
      return { ok: true as const, ds: { ...ds, projetos: inserirItem(ds.projetos, limpo, ctx).lista } };
    }),
  excluirProjeto: (id: number) =>
    exec()((ds) => ({ ok: true as const, ds: { ...ds, projetos: removerItem(ds.projetos, id) } })),

  // R58 - dívidas
  salvarDivida: (dados: DadosDivida, id?: number) => exec()((ds, ctx) => salvarDivida(ds, dados, id, ctx)),
  excluirDivida: (id: number) => exec()((ds) => excluirDivida(ds, id)),
  registrarJurosDivida: (id: number, juros: number) => exec()((ds) => registrarJurosDivida(ds, id, juros)),
  pagarDivida: (input: PagamentoDivida) => exec()((ds, ctx) => pagarDivida(ds, { autor: autorAtual(), ...input }, ctx)),

  // R59 - pessoas, rateios e acertos
  salvarPessoa: (dados: { nome: string; contato?: string | null }, id?: number) => exec()((ds, ctx) => salvarPessoa(ds, dados, id, ctx)),
  excluirPessoa: (id: number) => exec()((ds) => excluirPessoa(ds, id)),
  dividirDespesa: (input: DivisaoDespesa) => exec()((ds, ctx) => dividirDespesa(ds, input, ctx)),
  desfazerRateio: (despesaId: number) => exec()((ds) => desfazerRateio(ds, despesaId)),
  criarAcertoAvulso: (input: AcertoAvulso) => exec()((ds, ctx) => criarAcertoAvulso(ds, input, ctx)),
  quitarAcerto: (input: QuitacaoAcerto) => exec()((ds, ctx) => quitarAcerto(ds, input, ctx)),
  excluirAcerto: (id: number) => exec()((ds) => excluirAcerto(ds, id)),
  // R68 - vincular lançamentos existentes a uma pessoa (só cria acertos; não altera lançamentos nem saldos)
  vincularLancamentos: (input: VinculoLancamentos) => exec()((ds, ctx) => vincularLancamentos(ds, input, ctx)),

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
