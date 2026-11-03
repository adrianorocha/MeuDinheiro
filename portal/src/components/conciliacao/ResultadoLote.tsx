"use client";

import { CheckCircle2, ClipboardCheck, Download, PlusCircle, Undo2, XCircle, CircleSlash } from "lucide-react";
import { PainelContadores } from "@/components/conciliacao/ExecucaoLote";
import { Button } from "@/components/ui/Button";
import { Badge, Card, ProgressBar } from "@/components/ui/Misc";
import { arquivosComPendencias, taxaConciliacao, type ResultadoArquivo } from "@/lib/conciliacao/lote-multiplo";
import type { LoteMultiplo } from "@/lib/conciliacao/lote";
import { formatPercentual } from "@/lib/format";

const STATUS = {
  CONCLUIDO: { rotulo: "Concluído", tom: "pos" as const, icone: <CheckCircle2 size={12} className="mr-1" aria-hidden /> },
  ERRO: { rotulo: "Erro", tom: "neg" as const, icone: <XCircle size={12} className="mr-1" aria-hidden /> },
  CANCELADO: { rotulo: "Cancelado", tom: "warn" as const, icone: <CircleSlash size={12} className="mr-1" aria-hidden /> },
};

function LinhaTabela({ a }: { a: ResultadoArquivo }) {
  const st = STATUS[a.status];
  return (
    <tr className="border-b border-line last:border-0 align-top">
      <th scope="row" className="max-w-[14rem] px-2 py-2 text-left font-medium">
        <span className="block truncate" title={a.nome}>
          {a.nome}
        </span>
        {a.erro && <span className="block whitespace-normal text-xs font-normal text-neg">{a.erro}</span>}
      </th>
      <td className="px-2 py-2">
        <Badge tom={st.tom}>
          {st.icone}
          {st.rotulo}
        </Badge>
      </td>
      <td className="tabular px-2 py-2 text-right">{a.contadores.conciliados}</td>
      <td className="tabular px-2 py-2 text-right">{a.contadores.criados}</td>
      <td className="tabular px-2 py-2 text-right">{a.contadores.pendentes}</td>
      <td className="tabular px-2 py-2 text-right">{a.contadores.erros}</td>
      <td className="px-2 py-2 text-muted">{a.rotuloDestino}</td>
    </tr>
  );
}

interface Props {
  lote: LoteMultiplo;
  onDesfazer: () => void;
  onExportar: () => void;
  onRevisarPendencias: () => void;
  onNovo: () => void;
}

/** Passo 4: resultado do lote (métricas, tabela por arquivo, taxa e ações). */
export function ResultadoLote({ lote, onDesfazer, onExportar, onRevisarPendencias, onNovo }: Props) {
  const r = lote.resultado;
  const taxa = taxaConciliacao(r.contadores);
  const pendencias = arquivosComPendencias(r).length;
  const algoAplicado = r.arquivos.some((a) => a.inverso && (a.inverso.restaurar.length > 0 || a.inverso.remover.length > 0));
  return (
    <Card aria-labelledby="t-res-lote">
      <h2 id="t-res-lote" className="mb-3 flex items-center gap-2 text-lg font-semibold">
        <CheckCircle2 className="text-pos" size={22} aria-hidden /> 4. {r.cancelado ? "Lote interrompido" : "Lote concluído"}
      </h2>
      {lote.desfeito && (
        <p role="status" className="mb-3 rounded-xl bg-warn-soft px-3 py-2 text-sm text-warn">
          Lote desfeito: valores restaurados e lançamentos criados removidos.
        </p>
      )}
      <div className="mb-4">
        <div className="mb-1 flex items-center justify-between text-sm">
          <span className="font-medium">Taxa de conciliação</span>
          <span className="tabular font-semibold">{formatPercentual(taxa)}</span>
        </div>
        <ProgressBar razao={taxa} tom={taxa >= 0.9 ? "pos" : taxa >= 0.6 ? "primary" : "warn"} rotulo="Taxa de conciliação do lote" />
        <p className="mt-1 text-xs text-muted">
          {r.contadores.conciliados + r.contadores.criados + r.contadores.duplicados} de {r.contadores.transacoes} transações do extrato resolvidas (conciliadas, criadas ou já conciliadas antes).
        </p>
      </div>
      <PainelContadores c={r.contadores} />
      <div className="mt-4 overflow-x-auto rounded-xl border border-line">
        <table className="w-full min-w-[40rem] text-sm">
          <caption className="sr-only">Resultado por arquivo</caption>
          <thead className="bg-surface-2 text-xs text-muted">
            <tr>
              <th scope="col" className="px-2 py-2 text-left font-medium">Arquivo</th>
              <th scope="col" className="px-2 py-2 text-left font-medium">Status</th>
              <th scope="col" className="px-2 py-2 text-right font-medium">Conciliados</th>
              <th scope="col" className="px-2 py-2 text-right font-medium">Criados</th>
              <th scope="col" className="px-2 py-2 text-right font-medium">Pendentes</th>
              <th scope="col" className="px-2 py-2 text-right font-medium">Erros</th>
              <th scope="col" className="px-2 py-2 text-left font-medium">Destino</th>
            </tr>
          </thead>
          <tbody>
            {r.arquivos.map((a) => (
              <LinhaTabela key={a.id} a={a} />
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button icone={<Undo2 size={16} aria-hidden />} disabled={lote.desfeito || !algoAplicado} onClick={onDesfazer}>
          Desfazer lote inteiro
        </Button>
        <Button icone={<Download size={16} aria-hidden />} onClick={onExportar}>
          Exportar relatório CSV
        </Button>
        <Button icone={<ClipboardCheck size={16} aria-hidden />} disabled={pendencias === 0 || lote.desfeito} onClick={onRevisarPendencias}>
          Revisar pendências{pendencias > 0 ? ` (${pendencias})` : ""}
        </Button>
        <Button variante="primary" icone={<PlusCircle size={16} aria-hidden />} onClick={onNovo}>
          Novo lote
        </Button>
      </div>
      <p className="mt-3 text-xs text-muted">O botão de desfazer vale enquanto esta aba estiver aberta. Depois, use &quot;Desfazer conciliação&quot; em cada lançamento (Lançamentos).</p>
    </Card>
  );
}
