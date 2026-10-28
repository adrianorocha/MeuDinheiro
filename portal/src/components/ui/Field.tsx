import { useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

const CONTROLE =
  "h-10 w-full rounded-lg border border-line bg-surface px-3 text-sm text-fg placeholder:text-muted/70 disabled:opacity-60 aria-[invalid=true]:border-neg";

interface CampoProps {
  rotulo: string;
  erro?: string | null;
  dica?: string;
  className?: string;
  children: (ids: { id: string; descritoPor: string | undefined; invalido: boolean }) => ReactNode;
}

/** Rótulo + controle + dica/erro, com ids/aria ligados. */
export function Campo({ rotulo, erro, dica, className = "", children }: CampoProps) {
  const id = useId();
  const msgId = `${id}-msg`;
  const temMsg = Boolean(erro || dica);
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-sm font-medium">
        {rotulo}
      </label>
      {children({ id, descritoPor: temMsg ? msgId : undefined, invalido: Boolean(erro) })}
      {temMsg && (
        <p id={msgId} className={`text-xs ${erro ? "text-neg" : "text-muted"}`} role={erro ? "alert" : undefined}>
          {erro ?? dica}
        </p>
      )}
    </div>
  );
}

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, "id"> & { rotulo: string; erro?: string | null; dica?: string; wrapperClassName?: string };

export function Input({ rotulo, erro, dica, wrapperClassName, className = "", ...rest }: InputProps) {
  return (
    <Campo rotulo={rotulo} erro={erro} dica={dica} className={wrapperClassName}>
      {({ id, descritoPor, invalido }) => (
        <input id={id} aria-describedby={descritoPor} aria-invalid={invalido} className={`${CONTROLE} ${className}`} {...rest} />
      )}
    </Campo>
  );
}

type SelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, "id"> & { rotulo: string; erro?: string | null; dica?: string; wrapperClassName?: string };

export function Select({ rotulo, erro, dica, wrapperClassName, className = "", children, ...rest }: SelectProps) {
  return (
    <Campo rotulo={rotulo} erro={erro} dica={dica} className={wrapperClassName}>
      {({ id, descritoPor, invalido }) => (
        <select id={id} aria-describedby={descritoPor} aria-invalid={invalido} className={`${CONTROLE} ${className}`} {...rest}>
          {children}
        </select>
      )}
    </Campo>
  );
}

/** Input monetário (aceita "1.234,56"). O parse é feito por `parseValorBR` no submit. */
export function InputValor(props: Omit<InputProps, "type" | "inputMode">) {
  return <Input inputMode="decimal" autoComplete="off" placeholder="0,00" {...props} />;
}

export function Checkbox({ rotulo, ...rest }: Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "id"> & { rotulo: string }) {
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      <input id={id} type="checkbox" className="size-4 accent-[var(--primary)]" {...rest} />
      <label htmlFor={id} className="text-sm">
        {rotulo}
      </label>
    </div>
  );
}

/** Seletor segmentado acessível (radiogroup). */
export function Segmentado<T extends string>({
  rotulo,
  valor,
  opcoes,
  onChange,
}: {
  rotulo: string;
  valor: T;
  opcoes: readonly { valor: T; rotulo: string }[];
  onChange: (v: T) => void;
}) {
  return (
    <div role="radiogroup" aria-label={rotulo} className="inline-flex w-full rounded-lg border border-line bg-surface-2 p-0.5">
      {opcoes.map((o) => (
        <button
          key={o.valor}
          type="button"
          role="radio"
          aria-checked={valor === o.valor}
          onClick={() => onChange(o.valor)}
          className={`h-9 flex-1 rounded-md px-3 text-sm font-medium transition-colors ${
            valor === o.valor ? "bg-surface text-fg shadow-sm" : "text-muted hover:text-fg"
          }`}
        >
          {o.rotulo}
        </button>
      ))}
    </div>
  );
}
