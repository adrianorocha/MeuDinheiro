"use client";

import { useId, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Select } from "@/components/ui/Field";
import { Badge, Card, CardTitulo, EmptyState, ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { diasDoPar, similaridadeDescricao } from "@/lib/conciliacao/matching";
import {
  aplicarConciliacao,
  categoriaSugerida,
  mesclaPadrao,
  type AcaoPlano,
  type Mescla,
  type Plano,
} from "@/lib/conciliacao/plano";
import { limparDescricaoBanco } from "@/lib/conciliacao/texto-banco";
import type { ClasseMatch, Destino, ItemMatch, ResultadoMatching } from "@/lib/conciliacao/tipos";
import { toCents } from "@/lib/finance/money";
import { normalizar } from "@/lib/finance/texto";
import type { Dataset, Despesa } from "@/lib/finance/types";
import { formatBRL, formatData } from "@/lib/format";
import { useAgora } from "@/lib/hooks";

type Aba = "AUTOMATICO" | "SUGERIDO" | "SO_NO_EXTRATO" | "SO_NO_APP" | "DUPLICADO";

const ABAS: { id: Aba; rotulo: string; ajuda: string }[] = [
  { id: "AUTOMATICO", rotulo: "Automáticos", ajuda: "Valor exato, data próxima e único candidato. Já vêm marcados." },
  { id: "SUGERIDO", rotulo: "Sugeridos", ajuda: "Há candidato, mas com diferença de valor/data ou mais de uma opção. Confirme um a um." },
  { id: "SO_NO_EXTRATO", rotulo: "Só no extrato", ajuda: "Não há lançamento no app. Marque para criar, ou escolha um lançamento existente." },
  { id: "SO_NO_APP", rotulo: "Só no app", ajuda: "Lançamentos do período sem par no extrato (podem estar pendentes, futuros ou esquecidos no banco). Informativo." },
  { id: "DUPLICADO", rotulo: "Duplicados", ajuda: "Já conciliados em importação anterior. Nada será feito." },
];

interface Escolha {
  acao: "conciliar" | "criar" | "ignorar";
  lancamentoId?: number;
  mescla: Mescla;
  categoria: string;
}

function tipoDoPar(valorBanco: number, d: Despesa): "EXATO" | "DIFERENCA" {
  return toCents(Math.abs(valorBanco)) === toCents(d.valor) ? "EXATO" : "DIFERENCA";
}

function padrao(it: ItemMatch, ds: Dataset, destino: Destino): Escolha {
  const categoria = categoriaSugerida(ds, it.transacao.descricao);
  const mescla = mesclaPadrao(it, destino);
  if (it.classe === "AUTOMATICO") return { acao: "conciliar", lancamentoId: it.lancamentoId, mescla, categoria };
  if (it.classe === "SUGERIDO") return { acao: "ignorar", lancamentoId: it.lancamentoId, mescla, categoria };
  return { acao: "ignorar", mescla, categoria };
}

function descreverAcao(e: Escolha, it: ItemMatch, d: Despesa | undefined, destino: Destino): string {
  if (e.acao === "ignorar") return it.classe === "DUPLICADO" ? "Nada será feito (já conciliado antes)." : "Nada será feito (ignorado).";
  if (e.acao === "criar") return `Criará um lançamento de ${formatBRL(Math.abs(it.transacao.valor))} em ${formatData(it.transacao.data)}, categoria "${e.categoria}", ${destino.tipo === "CONTA" ? "já pago" : "pendente na fatura"}.`;
  if (!d) return "Escolha um lançamento.";
  const partes = [`Vinculará ao lançamento "${d.descricao}"`];
  if (e.mescla.data && diasDoPar(it.transacao, d) !== 0) partes.push(`data → ${formatData(it.transacao.data)}`);
  if (e.mescla.valor && toCents(Math.abs(it.transacao.valor)) !== toCents(d.valor)) partes.push(`valor → ${formatBRL(Math.abs(it.transacao.valor))}`);
  if (e.mescla.descricao) partes.push(`descrição → "${limparDescricaoBanco(it.transacao.descricao)}"`);
  if (e.mescla.pago && destino.tipo === "CONTA" && !d.pago) partes.push("marcará como pago");
  return `${partes[0]}${partes.length > 1 ? ` · ${partes.slice(1).join(" · ")}` : ""}.`;
}

interface LinhaProps {
  it: ItemMatch;
  e: Escolha;
  ds: Dataset;
  destino: Destino;
  porId: Map<number, Despesa>;
  conflito: boolean;
  onChange: (p: Partial<Escolha>) => void;
}

function Linha({ it, e, ds, destino, porId, conflito, onChange }: LinhaProps) {
  const id = useId();
  const t = it.transacao;
  const d = e.lancamentoId !== undefined ? porId.get(e.lancamentoId) : undefined;
  const candidatos = [it.lancamentoId, ...it.alternativas.map((a) => a.lancamentoId)].filter((x): x is number => x !== undefined);
  const podeConciliar = it.classe === "AUTOMATICO" || it.classe === "SUGERIDO" || (it.classe === "SO_NO_EXTRATO" && candidatos.length > 0);
  const podeCriar = it.classe === "SO_NO_EXTRATO";
  const ativo = e.acao !== "ignorar";
  const dias = d ? diasDoPar(t, d) : 0;
  const difValor = d ? toCents(Math.abs(t.valor)) - toCents(d.valor) : 0;
  const difDesc = d ? similaridadeDescricao(t.descricao, d.descricao) < 0.5 : false;
  const classeBorda = conflito ? "border-neg" : ativo ? "border-primary/60" : "border-line";

  return (
    <article className={`rounded-xl border bg-surface p-3 ${classeBorda}`} aria-label={`${t.descricao} ${formatBRL(t.valor)}`}>
      <div className="grid gap-3 md:grid-cols-[1fr_1fr]">
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted">Extrato</p>
          <p className="truncate text-sm font-medium" title={t.descricao}>
            {limparDescricaoBanco(t.descricao)}
          </p>
          <p className="text-xs text-muted">
            {formatData(t.data)} · <Money valor={t.valor} tom="auto" className="font-semibold" />
          </p>
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium text-muted">Lançamento do app</p>
          {e.acao === "criar" || (!d && podeCriar) ? (
            <div className="mt-1">
              <Input rotulo="Categoria do novo lançamento" value={e.categoria} onChange={(ev) => onChange({ categoria: ev.target.value })} list={`${id}-cats`} />
              <datalist id={`${id}-cats`}>
                {ds.categorias.map((c) => (
                  <option key={c.id} value={c.nome} />
                ))}
              </datalist>
            </div>
          ) : d ? (
            <>
              <p className="truncate text-sm font-medium">{d.descricao}</p>
              <p className="text-xs text-muted">
                {formatData(d.data)} · <Money valor={d.tipo === "CREDITO" ? d.valor : -d.valor} tom="auto" className="font-semibold" /> · {d.categoria}
              </p>
              <div className="mt-1 flex flex-wrap gap-1">
                {dias !== 0 && <Badge tom="warn">data {dias > 0 ? "+" : ""}{dias}d</Badge>}
                {difValor !== 0 && <Badge tom="warn">valor {formatBRL(Math.abs(difValor) / 100)}</Badge>}
                {difDesc && <Badge tom="warn">descrição difere</Badge>}
                {dias === 0 && difValor === 0 && !difDesc && <Badge tom="pos">idêntico</Badge>}
              </div>
            </>
          ) : (
            <p className="text-sm text-muted">{it.classe === "DUPLICADO" ? "Já conciliado anteriormente." : "Nenhum lançamento candidato."}</p>
          )}
        </div>
      </div>

      {(podeConciliar || podeCriar) && (
        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line pt-3">
          {podeConciliar && (
            <Checkbox rotulo="Conciliar" checked={e.acao === "conciliar"} onChange={(ev) => onChange(ev.target.checked ? { acao: "conciliar", lancamentoId: e.lancamentoId ?? candidatos[0] } : { acao: "ignorar" })} />
          )}
          {podeCriar && <Checkbox rotulo="Criar lançamento" checked={e.acao === "criar"} onChange={(ev) => onChange(ev.target.checked ? { acao: "criar" } : { acao: "ignorar" })} />}
          {candidatos.length > 1 && (
            <div className="min-w-48 flex-1">
              <Select
                rotulo="Trocar lançamento"
                value={e.lancamentoId ?? ""}
                onChange={(ev) => {
                  const novo = Number(ev.target.value);
                  const dn = porId.get(novo);
                  onChange({ lancamentoId: novo, acao: "conciliar", mescla: { ...e.mescla, valor: dn ? tipoDoPar(t.valor, dn) === "DIFERENCA" : false } });
                }}
              >
                {candidatos.map((c) => {
                  const dc = porId.get(c);
                  return (
                    <option key={c} value={c}>
                      {dc ? `${formatData(dc.data)} · ${dc.descricao} · ${formatBRL(dc.valor)}` : c}
                    </option>
                  );
                })}
              </Select>
            </div>
          )}
          {e.acao === "conciliar" && (
            <details className="text-sm">
              <summary className="cursor-pointer text-primary">Opções de mescla</summary>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
                <Checkbox rotulo="Usar data do banco" checked={e.mescla.data} onChange={(ev) => onChange({ mescla: { ...e.mescla, data: ev.target.checked } })} />
                <Checkbox rotulo="Usar valor do banco" checked={e.mescla.valor} onChange={(ev) => onChange({ mescla: { ...e.mescla, valor: ev.target.checked } })} />
                <Checkbox rotulo="Usar descrição do banco" checked={e.mescla.descricao} onChange={(ev) => onChange({ mescla: { ...e.mescla, descricao: ev.target.checked } })} />
                {destino.tipo === "CONTA" && <Checkbox rotulo="Marcar como pago" checked={e.mescla.pago} onChange={(ev) => onChange({ mescla: { ...e.mescla, pago: ev.target.checked } })} />}
              </div>
            </details>
          )}
        </div>
      )}
      <p className={`mt-2 text-xs ${conflito ? "text-neg" : "text-muted"}`} role={conflito ? "alert" : undefined}>
        {conflito ? "Este lançamento foi escolhido para mais de uma linha do extrato." : descreverAcao(e, it, d, destino)}
      </p>
    </article>
  );
}

interface Props {
  ds: Dataset;
  destino: Destino;
  resultado: ResultadoMatching;
  onAplicar: (plano: Plano, acoes: Map<number, { acao: string; lancamentoId?: number }>) => void;
  onVoltar: () => void;
}

/** Passo 3: revisão lado a lado, em abas, com prévia e confirmação antes de aplicar. */
export function Revisao({ ds, destino, resultado, onAplicar, onVoltar }: Props) {
  const agora = useAgora();
  const [aba, setAba] = useState<Aba>(() => (resultado.itens.some((i) => i.classe === "AUTOMATICO") ? "AUTOMATICO" : resultado.itens.some((i) => i.classe === "SUGERIDO") ? "SUGERIDO" : resultado.itens.some((i) => i.classe === "SO_NO_EXTRATO") ? "SO_NO_EXTRATO" : "DUPLICADO"));
  const [over, setOver] = useState<Record<number, Partial<Escolha>>>({});
  const [busca, setBusca] = useState("");
  const [soDif, setSoDif] = useState(false);
  const [previa, setPrevia] = useState(false);

  const porId = useMemo(() => new Map(ds.despesas.map((d) => [d.id, d])), [ds.despesas]);
  const escolha = (it: ItemMatch): Escolha => ({ ...padrao(it, ds, destino), ...over[it.indice] });
  const atualizar = (indice: number, p: Partial<Escolha>) => setOver((o) => ({ ...o, [indice]: { ...o[indice], ...p } }));

  const contagem = useMemo(() => {
    const c: Record<Aba, number> = { AUTOMATICO: 0, SUGERIDO: 0, SO_NO_EXTRATO: 0, DUPLICADO: 0, SO_NO_APP: resultado.soNoApp.length };
    for (const it of resultado.itens) c[it.classe as ClasseMatch]++;
    return c;
  }, [resultado]);

  const escolhas = resultado.itens.map((it) => ({ it, e: escolha(it) }));
  const usos = new Map<number, number>();
  for (const { e } of escolhas) if (e.acao === "conciliar" && e.lancamentoId !== undefined) usos.set(e.lancamentoId, (usos.get(e.lancamentoId) ?? 0) + 1);
  const conflitos = [...usos.values()].some((n) => n > 1);
  const nConciliar = escolhas.filter(({ e }) => e.acao === "conciliar").length;
  const nCriar = escolhas.filter(({ e }) => e.acao === "criar").length;

  const plano: Plano = useMemo(() => {
    const acoes: AcaoPlano[] = [];
    for (const it of resultado.itens) {
      const e = { ...padrao(it, ds, destino), ...over[it.indice] };
      if (e.acao === "conciliar" && e.lancamentoId !== undefined) acoes.push({ tipo: "CONCILIAR", transacao: it.transacao, lancamentoId: e.lancamentoId, mescla: e.mescla });
      else if (e.acao === "criar") acoes.push({ tipo: "CRIAR", transacao: it.transacao, categoria: e.categoria });
    }
    return { destino, acoes };
  }, [resultado.itens, ds, destino, over]);

  const termo = normalizar(busca);
  const visiveis = escolhas.filter(({ it, e }) => {
    if (it.classe !== aba) return false;
    const d = e.lancamentoId !== undefined ? porId.get(e.lancamentoId) : undefined;
    if (termo && !normalizar(`${it.transacao.descricao} ${d?.descricao ?? ""}`).includes(termo)) return false;
    if (soDif && d) {
      const igual = diasDoPar(it.transacao, d) === 0 && toCents(Math.abs(it.transacao.valor)) === toCents(d.valor);
      if (igual) return false;
    }
    return true;
  });
  const apps = resultado.soNoApp.filter((d) => !termo || normalizar(`${d.descricao} ${d.categoria}`).includes(termo));

  const marcarTodos = (marcar: boolean) =>
    setOver((o) => {
      const n = { ...o };
      for (const { it } of visiveis) {
        if (aba === "SO_NO_EXTRATO") n[it.indice] = { ...n[it.indice], acao: marcar ? "criar" : "ignorar" };
        else if (it.lancamentoId !== undefined) n[it.indice] = { ...n[it.indice], acao: marcar ? "conciliar" : "ignorar" };
      }
      return n;
    });

  const simulacao = useMemo(() => (previa ? aplicarConciliacao(ds, plano, agora) : null), [previa, ds, plano, agora]);
  const alvoAntes = destino.tipo === "CONTA" ? ds.contas.find((c) => c.conta === destino.conta)?.saldo : ds.cartoes.find((c) => c.id === destino.cartaoId)?.limiteDisponivel;
  const alvoDepois = simulacao?.ok ? (destino.tipo === "CONTA" ? simulacao.ds.contas.find((c) => c.conta === destino.conta)?.saldo : simulacao.ds.cartoes.find((c) => c.id === destino.cartaoId)?.limiteDisponivel) : undefined;

  function confirmar() {
    const acoes = new Map<number, { acao: string; lancamentoId?: number }>();
    for (const { it, e } of escolhas) {
      if (it.classe === "DUPLICADO") acoes.set(it.indice, { acao: "duplicado" });
      else if (e.acao === "conciliar") acoes.set(it.indice, { acao: "conciliado", lancamentoId: e.lancamentoId });
      else if (e.acao === "criar") acoes.set(it.indice, { acao: "criado" });
      else acoes.set(it.indice, { acao: "ignorado" });
    }
    setPrevia(false);
    onAplicar(plano, acoes);
  }

  const abaAtual = ABAS.find((a) => a.id === aba)!;

  return (
    <Card aria-labelledby="t-revisao">
      <CardTitulo id="t-revisao">3. Revisar e conciliar</CardTitulo>
      <div role="tablist" aria-label="Classes de resultado" className="flex flex-wrap gap-1">
        {ABAS.map((a) => (
          <button
            key={a.id}
            type="button"
            role="tab"
            id={`aba-${a.id}`}
            aria-selected={aba === a.id}
            aria-controls="painel-revisao"
            onClick={() => setAba(a.id)}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium ${aba === a.id ? "bg-primary text-primary-fg" : "bg-surface-2 text-muted hover:text-fg"}`}
          >
            {a.rotulo} <span className="tabular opacity-80">({contagem[a.id]})</span>
          </button>
        ))}
      </div>
      <p className="mt-2 text-xs text-muted">{abaAtual.ajuda}</p>

      <div className="mt-3 flex flex-wrap items-end gap-3">
        <div className="min-w-48 flex-1">
          <Input rotulo="Buscar nesta aba" type="search" value={busca} onChange={(e) => setBusca(e.target.value)} autoComplete="off" />
        </div>
        {(aba === "AUTOMATICO" || aba === "SUGERIDO") && <Checkbox rotulo="Só com diferenças" checked={soDif} onChange={(e) => setSoDif(e.target.checked)} />}
        {aba !== "SO_NO_APP" && aba !== "DUPLICADO" && (
          <div className="flex gap-2">
            <Button tamanho="sm" onClick={() => marcarTodos(true)}>
              Marcar todos
            </Button>
            <Button tamanho="sm" onClick={() => marcarTodos(false)}>
              Limpar
            </Button>
          </div>
        )}
      </div>

      <div id="painel-revisao" role="tabpanel" aria-labelledby={`aba-${aba}`} className="mt-3 space-y-2">
        {aba === "SO_NO_APP" ? (
          apps.length === 0 ? (
            <EmptyState titulo="Nada aqui" descricao="Todos os lançamentos do período têm par no extrato." />
          ) : (
            <ul className="divide-y divide-line rounded-xl border border-line bg-surface">
              {apps.map((d) => (
                <li key={d.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                  <span className="min-w-0 flex-1 truncate">
                    {formatData(d.data)} · {d.descricao}
                  </span>
                  {!d.pago && <Badge tom="warn">pendente</Badge>}
                  <Money valor={d.tipo === "CREDITO" ? d.valor : -d.valor} tom="auto" className="font-semibold" />
                </li>
              ))}
            </ul>
          )
        ) : visiveis.length === 0 ? (
          <EmptyState titulo="Nenhuma linha nesta aba" descricao={busca || soDif ? "Ajuste os filtros." : undefined} />
        ) : (
          visiveis.slice(0, 200).map(({ it, e }) => <Linha key={it.indice} it={it} e={e} ds={ds} destino={destino} porId={porId} conflito={e.acao === "conciliar" && e.lancamentoId !== undefined && (usos.get(e.lancamentoId) ?? 0) > 1} onChange={(p) => atualizar(it.indice, p)} />)
        )}
        {visiveis.length > 200 && <p className="text-xs text-muted">Mostrando 200 de {visiveis.length}; use a busca para refinar.</p>}
      </div>

      <div className="sticky bottom-20 mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface p-3 shadow-lg md:bottom-4">
        <p className="text-sm" aria-live="polite">
          <strong>{nConciliar}</strong> a conciliar · <strong>{nCriar}</strong> a criar
          {conflitos && <span className="ml-2 text-neg">Há lançamentos escolhidos duas vezes.</span>}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button onClick={onVoltar}>Voltar</Button>
          {contagem.AUTOMATICO > 0 && (
            <Button
              onClick={() => {
                setOver((o) => {
                  const n = { ...o };
                  for (const it of resultado.itens) if (it.classe === "AUTOMATICO") n[it.indice] = { ...n[it.indice], acao: "conciliar" };
                  return n;
                });
                setPrevia(true);
              }}
              disabled={conflitos}
            >
              Aplicar todos os automáticos ({contagem.AUTOMATICO})
            </Button>
          )}
          <Button variante="primary" disabled={nConciliar + nCriar === 0 || conflitos} onClick={() => setPrevia(true)}>
            Revisar e aplicar
          </Button>
        </div>
      </div>

      <Modal aberto={previa} onFechar={() => setPrevia(false)} titulo="Confirmar conciliação">
        {simulacao && !simulacao.ok && <ErroBox>{simulacao.erro}</ErroBox>}
        {simulacao?.ok && (
          <div className="space-y-3 text-sm">
            <p>
              Serão <strong>{simulacao.resumo.conciliados}</strong> lançamento(s) conciliado(s) e <strong>{simulacao.resumo.criados}</strong> criado(s).
            </p>
            <ul className="list-disc pl-5 text-muted">
              <li>{simulacao.resumo.datasAlteradas} data(s) alterada(s) para a do banco</li>
              <li>{simulacao.resumo.valoresAlterados} valor(es) alterado(s) para o do banco</li>
              <li>{simulacao.resumo.pagosMarcados} lançamento(s) marcado(s) como pago</li>
            </ul>
            {alvoAntes !== undefined && alvoDepois !== undefined && (
              <p>
                {destino.tipo === "CONTA" ? "Saldo da conta" : "Limite disponível"}: <Money valor={alvoAntes} /> → <Money valor={alvoDepois} className="font-semibold" />
              </p>
            )}
            <div className="max-h-56 overflow-y-auto rounded-xl border border-line">
              <table className="w-full text-xs">
                <caption className="sr-only">Mudanças que serão aplicadas</caption>
                <tbody>
                  {plano.acoes.slice(0, 40).map((a, i) => {
                    const d = a.tipo === "CONCILIAR" ? porId.get(a.lancamentoId) : undefined;
                    return (
                      <tr key={i} className="border-b border-line last:border-0">
                        <td className="px-2 py-1 font-medium">{a.tipo === "CONCILIAR" ? "Conciliar" : "Criar"}</td>
                        <td className="px-2 py-1">{a.tipo === "CONCILIAR" ? (d?.descricao ?? "?") : limparDescricaoBanco(a.transacao.descricao)}</td>
                        <td className="px-2 py-1 text-right">{formatBRL(Math.abs(a.transacao.valor))}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {plano.acoes.length > 40 && <p className="text-xs text-muted">… e mais {plano.acoes.length - 40}.</p>}
            <p className="text-xs text-muted">Você poderá desfazer este lote logo depois.</p>
            <div className="flex justify-end gap-2">
              <Button onClick={() => setPrevia(false)}>Cancelar</Button>
              <Button variante="primary" onClick={confirmar}>
                Aplicar
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </Card>
  );
}
