"use client";

import { ArrowRight, BadgeCheck, CalendarClock, CheckCircle2, CircleDollarSign, Copy, FilePlus2, FileSearch, GitCompareArrows, Link2, Loader2, PenLine, Square, Wallet } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitulo, ProgressBar } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import type { Contadores, EtapaArquivo, LinhaAtualizacao } from "@/lib/conciliacao/lote-multiplo";
import { formatData } from "@/lib/format";

export interface EstadoExecucao {
  total: number;
  /** índice (base 0) do arquivo corrente */
  indice: number;
  nome: string;
  etapa: EtapaArquivo;
  pctArquivo: number;
  pctGeral: number;
  contadores: Contadores;
  recentes: LinhaAtualizacao[];
  cancelando: boolean;
}

const ETAPAS: { id: EtapaArquivo; rotulo: string; icone: ReactNode }[] = [
  { id: "LENDO", rotulo: "Lendo", icone: <FileSearch size={16} aria-hidden /> },
  { id: "COMPARANDO", rotulo: "Comparando", icone: <GitCompareArrows size={16} aria-hidden /> },
  { id: "APLICANDO", rotulo: "Aplicando", icone: <PenLine size={16} aria-hidden /> },
  { id: "CONCLUIDO", rotulo: "Concluído", icone: <CheckCircle2 size={16} aria-hidden /> },
];

const ROTULO_ETAPA: Partial<Record<EtapaArquivo, string>> = { LENDO: "Lendo", COMPARANDO: "Comparando", APLICANDO: "Aplicando", CONCLUIDO: "Concluído", ERRO: "Erro" };

function Etapas({ etapa }: { etapa: EtapaArquivo }) {
  const ordem = ETAPAS.findIndex((e) => e.id === etapa);
  return (
    <ol className="grid grid-cols-2 gap-2 sm:grid-cols-4" aria-label="Etapas do arquivo atual">
      {ETAPAS.map((e, i) => {
        const feita = i < ordem || etapa === "CONCLUIDO";
        const atual = i === ordem && etapa !== "CONCLUIDO";
        return (
          <li
            key={e.id}
            aria-current={atual ? "step" : undefined}
            className={`flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium ${atual ? "bg-primary text-primary-fg" : feita ? "bg-pos-soft text-pos" : "bg-surface-2 text-muted"}`}
          >
            {atual ? <Loader2 size={16} className="animate-spin" aria-hidden /> : e.icone}
            {e.rotulo}
          </li>
        );
      })}
    </ol>
  );
}

function Contador({ icone, rotulo, valor, tom }: { icone: ReactNode; rotulo: string; valor: number; tom: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-surface-2 px-3 py-2">
      <span className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${tom}`}>{icone}</span>
      <div>
        <p className="tabular text-lg font-semibold leading-tight">{valor}</p>
        <p className="text-xs text-muted">{rotulo}</p>
      </div>
    </div>
  );
}

/** Painel de contadores acumulados (ao vivo na execução, final no resultado). */
export function PainelContadores({ c }: { c: Contadores }) {
  return (
    <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
      <Contador icone={<Link2 size={16} aria-hidden />} rotulo="Conciliados" valor={c.conciliados} tom="bg-pos-soft text-pos" />
      <Contador icone={<FilePlus2 size={16} aria-hidden />} rotulo="Criados" valor={c.criados} tom="bg-primary-soft text-primary" />
      <Contador icone={<CircleDollarSign size={16} aria-hidden />} rotulo="Valores atualizados" valor={c.valoresAlterados} tom="bg-warn-soft text-warn" />
      <Contador icone={<CalendarClock size={16} aria-hidden />} rotulo="Datas atualizadas" valor={c.datasAlteradas} tom="bg-warn-soft text-warn" />
      <Contador icone={<Wallet size={16} aria-hidden />} rotulo="Marcados como pagos" valor={c.pagosMarcados} tom="bg-pos-soft text-pos" />
      <Contador icone={<Copy size={16} aria-hidden />} rotulo="Duplicados ignorados" valor={c.duplicados} tom="bg-surface text-muted" />
      <Contador icone={<BadgeCheck size={16} aria-hidden />} rotulo="Pendentes para revisão" valor={c.pendentes} tom="bg-neg-soft text-neg" />
    </div>
  );
}

const ROTULO_MUDANCA = { valor: "valor", data: "data", pago: "pago" } as const;

function ListaAtualizacoes({ linhas }: { linhas: LinhaAtualizacao[] }) {
  if (linhas.length === 0) return <p className="text-sm text-muted">Aguardando as primeiras atualizações…</p>;
  return (
    <ul className="space-y-1.5" aria-label="Últimas atualizações">
      {linhas.map((l, i) => (
        <li key={`${l.arquivo}-${l.descricaoBanco}-${l.data}-${i}`} className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg bg-surface-2 px-3 py-1.5 text-xs">
          <Badge tom={l.acao === "criar" ? "primary" : "pos"}>{l.acao === "criar" ? "Criado" : "Conciliado"}</Badge>
          <span className="max-w-[14rem] truncate font-medium" title={l.descricaoBanco}>
            {l.descricaoBanco}
          </span>
          <ArrowRight size={12} className="text-muted" aria-hidden />
          <span className="max-w-[14rem] truncate" title={l.lancamento}>
            {l.lancamento}
          </span>
          <Money valor={l.valor} tom="auto" className="font-semibold" />
          <span className="text-muted">{formatData(l.data)}</span>
          {l.mudancas.map((m) => (
            <Badge key={m} tom="warn">
              {ROTULO_MUDANCA[m]} atualizado
            </Badge>
          ))}
        </li>
      ))}
    </ul>
  );
}

/** Passo 3: mostrador de progresso do lote. */
export function ExecucaoLote({ e, onCancelar }: { e: EstadoExecucao; onCancelar: () => void }) {
  const atual = Math.min(e.indice + 1, e.total);
  const status = `Arquivo ${atual} de ${e.total}: ${ROTULO_ETAPA[e.etapa] ?? ""} ${e.nome}`;
  return (
    <Card aria-labelledby="t-exec">
      <CardTitulo
        id="t-exec"
        acao={
          <Button tamanho="sm" icone={<Square size={14} aria-hidden />} onClick={onCancelar} disabled={e.cancelando}>
            {e.cancelando ? "Cancelando após este arquivo…" : "Cancelar"}
          </Button>
        }
      >
        3. Conciliando
      </CardTitulo>
      <p className="sr-only" role="status" aria-live="polite">
        {status}
      </p>
      <div className="space-y-4">
        <div>
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="font-medium">
              Arquivo {atual} de {e.total}
            </span>
            <span className="tabular font-semibold">{e.pctGeral}%</span>
          </div>
          <ProgressBar razao={e.pctGeral / 100} rotulo="Progresso geral do lote" />
        </div>
        <div className="rounded-xl border border-line p-3">
          <p className="mb-2 truncate text-sm" title={e.nome}>
            Arquivo atual: <strong>{e.nome}</strong>
          </p>
          <Etapas etapa={e.etapa} />
          <div className="mt-2 flex items-center gap-2">
            <ProgressBar razao={e.pctArquivo / 100} rotulo={`Etapa do arquivo ${e.nome}`} />
            <span className="tabular w-10 shrink-0 text-right text-xs text-muted">{e.pctArquivo}%</span>
          </div>
        </div>
        <PainelContadores c={e.contadores} />
        <div>
          <h3 className="mb-2 text-sm font-semibold text-muted">O que está sendo atualizado</h3>
          <ListaAtualizacoes linhas={e.recentes} />
        </div>
      </div>
    </Card>
  );
}
