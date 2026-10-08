"use client";

import { AlertTriangle, CheckCircle2, Clock, FileCode2, FileSpreadsheet, FileText, Loader2, Trash2, XCircle } from "lucide-react";
import type { ReactNode } from "react";
import type { EntradaFila, ProgressoLeitura, StatusFila } from "@/components/conciliacao/useFila";
import { Button, IconButton } from "@/components/ui/Button";
import { Select } from "@/components/ui/Field";
import { Badge, ProgressBar } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import type { Destino } from "@/lib/conciliacao/tipos";
import type { Dataset } from "@/lib/finance/types";
import { formatData } from "@/lib/format";

export function formatTamanho(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1).replace(".", ",")} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}

export function destinoParaValor(d: Destino | null | undefined): string {
  if (!d) return "";
  return d.tipo === "CONTA" ? `C:${d.conta}` : `K:${d.cartaoId}`;
}

export function valorParaDestino(v: string): Destino | null {
  if (v.startsWith("C:")) return { tipo: "CONTA", conta: v.slice(2) };
  if (v.startsWith("K:")) return { tipo: "CARTAO", cartaoId: Number(v.slice(2)) };
  return null;
}

const STATUS: Record<StatusFila, { rotulo: string; tom: "neutro" | "primary" | "pos" | "warn" | "neg"; icone: ReactNode }> = {
  FILA: { rotulo: "Na fila", tom: "neutro", icone: <Clock size={14} aria-hidden /> },
  LENDO: { rotulo: "Lendo", tom: "primary", icone: <Loader2 size={14} className="animate-spin" aria-hidden /> },
  PRONTO: { rotulo: "Pronto", tom: "pos", icone: <CheckCircle2 size={14} aria-hidden /> },
  REVISAR: { rotulo: "Precisa de revisão", tom: "warn", icone: <AlertTriangle size={14} aria-hidden /> },
  ERRO: { rotulo: "Erro", tom: "neg", icone: <XCircle size={14} aria-hidden /> },
};

function IconeFormato({ formato }: { formato?: "OFX" | "CSV" }) {
  const Icone = formato === "OFX" ? FileCode2 : formato === "CSV" ? FileSpreadsheet : FileText;
  return (
    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
      <Icone size={20} aria-hidden />
    </span>
  );
}

function periodoTexto(p: { inicio: number; fim: number } | null | undefined): string {
  return p ? `${formatData(p.inicio)} a ${formatData(p.fim)}` : "—";
}

interface CartaoProps {
  e: EntradaFila;
  ds: Dataset;
  onDestino: (id: string, d: Destino | null) => void;
  onMapear: (id: string) => void;
  onRemover: (id: string) => void;
}

function CartaoArquivo({ e, ds, onDestino, onMapear, onRemover }: CartaoProps) {
  const st = STATUS[e.status];
  const x = e.extrato;
  return (
    <li className="rounded-xl border border-line bg-surface p-3" aria-label={`${e.nome}: ${st.rotulo}`}>
      <div className="flex items-start gap-3">
        <IconeFormato formato={e.formato} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <p className="truncate text-sm font-medium" title={e.nome}>
              {e.nome}
            </p>
            <Badge tom={st.tom}>
              <span className="mr-1 inline-flex">{st.icone}</span>
              {st.rotulo}
            </Badge>
          </div>
          <p className="mt-0.5 text-xs text-muted">
            {formatTamanho(e.tamanho)}
            {e.formato && ` · ${e.formato}`}
            {x && ` · ${x.transacoes.length} transação(ões) · ${periodoTexto(x.periodo)}`}
          </p>
          {x?.saldoFinal && (
            <p className="text-xs text-muted">
              Saldo final: <Money valor={x.saldoFinal.valor} /> em {formatData(x.saldoFinal.data)}
            </p>
          )}
          {e.status === "LENDO" && (
            <div className="mt-2 flex items-center gap-2">
              <ProgressBar razao={e.pct / 100} rotulo={`Lendo ${e.nome}`} />
              <span className="tabular w-10 shrink-0 text-right text-xs text-muted">{e.pct}%</span>
            </div>
          )}
          {e.status === "ERRO" && <p className="mt-1 text-xs text-neg">{e.erro}</p>}
          {e.status === "REVISAR" && <p className="mt-1 text-xs text-warn">Não foi possível detectar nem recuperar o mapeamento das colunas deste CSV.</p>}
        </div>
        <IconButton rotulo={`Remover ${e.nome}`} onClick={() => onRemover(e.id)}>
          <Trash2 size={16} aria-hidden />
        </IconButton>
      </div>
      {e.status === "PRONTO" && (
        <div className="mt-3 max-w-md">
          <Select rotulo="Destino" value={destinoParaValor(e.destino)} onChange={(ev) => onDestino(e.id, valorParaDestino(ev.target.value))} erro={e.destino ? null : "Escolha a conta ou o cartão."}>
            {!e.destino && <option value="">Escolher…</option>}
            {ds.contas.length > 0 && (
              <optgroup label="Contas">
                {ds.contas.map((c) => (
                  <option key={c.id} value={`C:${c.conta}`}>
                    {c.banco} · {c.conta}
                  </option>
                ))}
              </optgroup>
            )}
            {ds.cartoes.length > 0 && (
              <optgroup label="Cartões">
                {ds.cartoes.map((c) => (
                  <option key={c.id} value={`K:${c.id}`}>
                    {c.nome}
                    {c.cartaoPrincipalId != null ? " (virtual)" : ""}
                  </option>
                ))}
              </optgroup>
            )}
          </Select>
        </div>
      )}
      {e.status === "REVISAR" && (
        <div className="mt-3">
          <Button tamanho="sm" variante="primary" onClick={() => onMapear(e.id)}>
            Mapear colunas
          </Button>
        </div>
      )}
    </li>
  );
}

interface Props {
  itens: EntradaFila[];
  ds: Dataset;
  leitura: ProgressoLeitura | null;
  onDestino: (id: string, d: Destino | null) => void;
  onMapear: (id: string) => void;
  onRemover: (id: string) => void;
}

/** Totais da fila: arquivos, transações e período coberto. */
export function TotaisFila({ itens }: { itens: EntradaFila[] }) {
  const prontos = itens.filter((i) => i.status === "PRONTO" && i.extrato);
  const transacoes = prontos.reduce((s, i) => s + (i.extrato?.transacoes.length ?? 0), 0);
  let inicio = Infinity;
  let fim = -Infinity;
  for (const i of prontos) {
    const p = i.extrato?.periodo;
    if (p) {
      inicio = Math.min(inicio, p.inicio);
      fim = Math.max(fim, p.fim);
    }
  }
  const item = (rotulo: string, valor: string) => (
    <div className="rounded-xl bg-surface-2 px-3 py-2">
      <dt className="text-xs text-muted">{rotulo}</dt>
      <dd className="text-sm font-semibold">{valor}</dd>
    </div>
  );
  return (
    <dl className="mb-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
      {item("Arquivos", `${prontos.length} pronto(s) de ${itens.length}`)}
      {item("Transações", String(transacoes))}
      {item("Período coberto", Number.isFinite(inicio) ? periodoTexto({ inicio, fim }) : "—")}
    </dl>
  );
}

/** Fila visual de arquivos: um cartão por arquivo com formato, resumo, destino editável e status. */
export function FilaArquivos({ itens, ds, leitura, onDestino, onMapear, onRemover }: Props) {
  return (
    <div>
      {leitura && (
        <div className="mb-3 rounded-xl border border-line bg-surface-2 p-3" role="status" aria-live="polite">
          <p className="mb-2 flex items-center gap-2 text-sm font-medium">
            <Loader2 size={16} className="animate-spin text-primary" aria-hidden />
            Lendo arquivos {Math.min(leitura.feitos + 1, leitura.total)}/{leitura.total} · {leitura.pct}%
          </p>
          <ProgressBar razao={leitura.pct / 100} rotulo="Leitura dos arquivos" />
        </div>
      )}
      <TotaisFila itens={itens} />
      <ul className="space-y-2">
        {itens.map((e) => (
          <CartaoArquivo key={e.id} e={e} ds={ds} onDestino={onDestino} onMapear={onMapear} onRemover={onRemover} />
        ))}
      </ul>
    </div>
  );
}
