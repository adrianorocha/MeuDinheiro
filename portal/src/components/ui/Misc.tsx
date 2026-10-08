import { Loader2 } from "lucide-react";
import type { ReactNode } from "react";

export function Card({ children, className = "", as: Tag = "section", ...rest }: { children: ReactNode; className?: string; as?: "section" | "div" | "article"; "aria-label"?: string; "aria-labelledby"?: string }) {
  return (
    <Tag className={`rounded-2xl border border-line bg-surface p-4 sm:p-5 ${className}`} {...rest}>
      {children}
    </Tag>
  );
}

export function CardTitulo({ children, acao, id }: { children: ReactNode; acao?: ReactNode; id?: string }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 id={id} className="text-sm font-semibold text-muted">
        {children}
      </h2>
      {acao}
    </div>
  );
}

export function PageHeader({ titulo, descricao, acoes }: { titulo: string; descricao?: string; acoes?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{titulo}</h1>
        {descricao && <p className="mt-1 text-sm text-muted">{descricao}</p>}
      </div>
      {acoes && <div className="flex flex-wrap items-center gap-2">{acoes}</div>}
    </div>
  );
}

type Tom = "neutro" | "pos" | "neg" | "warn" | "primary";

const TONS: Record<Tom, string> = {
  neutro: "bg-surface-2 text-muted",
  pos: "bg-pos-soft text-pos",
  neg: "bg-neg-soft text-neg",
  warn: "bg-warn-soft text-warn",
  primary: "bg-primary-soft text-primary",
};

export function Badge({ tom = "neutro", children }: { tom?: Tom; children: ReactNode }) {
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${TONS[tom]}`}>{children}</span>;
}

export function EmptyState({ titulo, descricao, acao, icone }: { titulo: string; descricao?: string; acao?: ReactNode; icone?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-line px-6 py-10 text-center">
      {icone && <div className="text-muted" aria-hidden>{icone}</div>}
      <p className="font-medium">{titulo}</p>
      {descricao && <p className="max-w-md text-sm text-muted">{descricao}</p>}
      {acao && <div className="mt-2">{acao}</div>}
    </div>
  );
}

export function Spinner({ rotulo = "Carregando" }: { rotulo?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-16 text-muted">
      <Loader2 className="animate-spin" size={20} aria-hidden />
      <span className="text-sm">{rotulo}...</span>
    </div>
  );
}

export function ErroBox({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="rounded-xl border border-neg/40 bg-neg-soft px-4 py-3 text-sm text-neg">
      {children}
    </div>
  );
}

/** Barra de progresso; `razao` 1 = 100% (barra limitada a 100%, valor real vai no aria). */
export function ProgressBar({ razao, tom = "primary", rotulo }: { razao: number; tom?: "primary" | "warn" | "neg" | "pos"; rotulo: string }) {
  const pct = Math.max(0, Math.min(1, Number.isFinite(razao) ? razao : 0));
  const cor = { primary: "bg-primary", warn: "bg-warn", neg: "bg-neg", pos: "bg-pos" }[tom];
  return (
    <div
      role="progressbar"
      aria-label={rotulo}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round((Number.isFinite(razao) ? razao : 0) * 100)}
      className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
    >
      <div className={`h-full rounded-full ${cor}`} style={{ width: `${pct * 100}%` }} />
    </div>
  );
}
