"use client";

import { formatBRL } from "@/lib/format";

export interface SerieGrafico {
  nome: string;
  /** Cor CSS (token do tema). */
  cor: string;
  traco?: string;
  /** Valores em centavos, um por mês (índice = mês). */
  valores: number[];
}

/** Gráfico de linhas simples em SVG (sem biblioteca) + tabela equivalente para leitores de tela. */
export function GraficoLinhas({ series, rotulo, passoTabela = 1 }: { series: SerieGrafico[]; rotulo: string; passoTabela?: number }) {
  const L = 600;
  const A = 200;
  const mx = 56;
  const n = Math.max(1, ...series.map((s) => s.valores.length - 1));
  const todos = series.flatMap((s) => s.valores);
  const min = Math.min(0, ...todos);
  const max = Math.max(1, ...todos);
  const x = (m: number) => mx + (m / n) * (L - mx - 10);
  const y = (v: number) => 10 + (1 - (v - min) / (max - min)) * (A - 30);
  const fmt = (c: number) => formatBRL(c / 100).replace(/,00$/, "");
  return (
    <figure>
      <svg viewBox={`0 0 ${L} ${A}`} className="w-full" role="img" aria-label={rotulo}>
        <line x1={mx} y1={A - 20} x2={L - 10} y2={A - 20} stroke="var(--line)" />
        <line x1={mx} y1={10} x2={mx} y2={A - 20} stroke="var(--line)" />
        <text x={mx - 4} y={16} textAnchor="end" fontSize={10} fill="var(--muted)">{fmt(max)}</text>
        <text x={mx - 4} y={A - 22} textAnchor="end" fontSize={10} fill="var(--muted)">{fmt(min)}</text>
        <text x={mx} y={A - 6} fontSize={10} fill="var(--muted)">mês 0</text>
        <text x={L - 10} y={A - 6} textAnchor="end" fontSize={10} fill="var(--muted)">mês {n}</text>
        {series.map((s) => (
          <polyline key={s.nome} fill="none" stroke={s.cor} strokeWidth={2} strokeDasharray={s.traco} points={s.valores.map((v, m) => `${x(m).toFixed(1)},${y(v).toFixed(1)}`).join(" ")} />
        ))}
      </svg>
      <figcaption className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
        {series.map((s) => (
          <span key={s.nome} className="inline-flex items-center gap-1.5">
            <svg width="22" height="6" aria-hidden>
              <line x1="0" y1="3" x2="22" y2="3" stroke={s.cor} strokeWidth="2" strokeDasharray={s.traco} />
            </svg>
            {s.nome}
          </span>
        ))}
      </figcaption>
      <details className="mt-2 text-sm">
        <summary className="cursor-pointer text-primary">Ver os valores em tabela</summary>
        <div className="mt-2 max-h-64 overflow-auto rounded-lg border border-line">
          <table className="w-full text-xs">
            <caption className="sr-only">{rotulo}</caption>
            <thead className="sticky top-0 bg-surface-2 text-left uppercase text-muted">
              <tr>
                <th scope="col" className="px-2 py-1">Mês</th>
                {series.map((s) => (
                  <th key={s.nome} scope="col" className="px-2 py-1 text-right">{s.nome}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: n + 1 }, (_, m) => m)
                .filter((m) => m % passoTabela === 0 || m === n)
                .map((m) => (
                  <tr key={m} className="border-t border-line">
                    <th scope="row" className="px-2 py-1 text-left font-normal">{m}</th>
                    {series.map((s) => (
                      <td key={s.nome} className="px-2 py-1 text-right tabular-nums">{formatBRL((s.valores[m] ?? 0) / 100)}</td>
                    ))}
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
