"use client";

import { CalendarClock, Unlock } from "lucide-react";
import { Money } from "@/components/ui/Money";
import type { ComprometimentoCartao } from "@/lib/finance/calc";
import { formatData, formatMesAno } from "@/lib/format";

interface Props {
  nome: string;
  c: ComprometimentoCartao;
}

/** R44 - barra empilhada do limite (fatura atual × parcelas futuras × disponível) e quanto volta ao pagar cada fatura. */
export function LimiteComprometido({ nome, c }: Props) {
  const total = c.limiteTotal > 0 ? c.limiteTotal : Math.max(c.emAbertoTotal + Math.max(c.disponivel, 0), 0);
  const atual = Math.max(0, c.faturaAtual + c.anteriores);
  const futuras = Math.max(0, c.parcelasFuturas);
  const disponivel = Math.max(0, c.disponivel);
  const pct = (v: number) => (total > 0 ? Math.min(100, (v / total) * 100) : 0);
  const partes = [
    { chave: "atual", rotulo: c.anteriores > 0 ? "Fatura atual e anteriores" : "Fatura atual", valor: atual, cor: "bg-warn" },
    { chave: "futuras", rotulo: "Parcelas futuras", valor: futuras, cor: "bg-primary" },
    { chave: "disp", rotulo: "Disponível", valor: disponivel, cor: "bg-pos" },
  ];
  const descricao = partes.map((p) => `${p.rotulo}: ${p.valor.toFixed(2).replace(".", ",")} reais`).join("; ");
  return (
    <div className="mt-3 border-t border-line pt-3">
      <p className="text-xs font-medium text-muted">Como o limite está comprometido</p>
      <div role="img" aria-label={`${nome}. ${descricao}`} className="mt-2 flex h-2.5 w-full overflow-hidden rounded-full bg-surface-2">
        {partes.map((p) => (p.valor > 0 ? <div key={p.chave} className={p.cor} style={{ width: `${pct(p.valor)}%` }} /> : null))}
      </div>
      <ul className="mt-2 grid gap-1 text-xs" aria-label="Legenda do limite">
        {partes.map((p) => (
          <li key={p.chave} className="flex items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 text-muted">
              <span className={`size-2.5 rounded-full ${p.cor}`} aria-hidden />
              {p.rotulo}
            </span>
            <Money valor={p.valor} className="font-medium" />
          </li>
        ))}
      </ul>
      <div className="mt-3">
        <p className="flex items-center gap-1.5 text-xs font-medium text-muted">
          <Unlock size={13} aria-hidden /> Limite que volta ao pagar
        </p>
        {c.liberacaoPorFatura.length === 0 ? (
          <p className="mt-1 text-xs text-muted">Nenhuma fatura em aberto.</p>
        ) : (
          <ul className="mt-1 divide-y divide-line text-xs" aria-label="Limite que volta ao pagar cada fatura">
            {c.liberacaoPorFatura.slice(0, 6).map((f) => (
              <li key={`${f.ano}-${f.mes}`} className="flex items-center justify-between gap-2 py-1">
                <span className="inline-flex min-w-0 items-center gap-1.5">
                  <CalendarClock size={13} className="shrink-0 text-muted" aria-hidden />
                  <span className="truncate">
                    Fatura {formatMesAno(f.mes, f.ano)} <span className="text-muted">· vence {formatData(f.vencimento)}</span>
                  </span>
                </span>
                <span className="font-medium text-pos">
                  +<Money valor={f.valor} />
                </span>
              </li>
            ))}
          </ul>
        )}
        {c.liberacaoPorFatura.length > 6 && <p className="mt-1 text-xs text-muted">+ {c.liberacaoPorFatura.length - 6} faturas seguintes.</p>}
      </div>
    </div>
  );
}
