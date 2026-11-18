"use client";

import { X } from "lucide-react";
import { useId, useMemo, useState } from "react";
import type { KeyboardEvent } from "react";
import { chaveTag, MAX_TAG_CHARS, MAX_TAGS, normalizarTags, rotuloTag, tagsUsadas } from "@/lib/finance/tags";
import type { Despesa } from "@/lib/finance/types";

interface Props {
  valor: string[];
  onChange: (v: string[]) => void;
  /** Lançamentos de onde saem as sugestões (tags já usadas). */
  despesas: readonly Despesa[];
  rotulo?: string;
}

/** R56 - campo de tags: chips com remoção, Enter/vírgula adicionam, sugestões das tags já usadas. */
export function TagsInput({ valor, onChange, despesas, rotulo = "Tags" }: Props) {
  const id = useId();
  const [texto, setTexto] = useState("");
  const usadas = useMemo(() => tagsUsadas(despesas), [despesas]);
  const chavesSel = new Set(valor.map(chaveTag));
  const termo = chaveTag(texto);
  const sugestoes = usadas
    .filter((t) => !chavesSel.has(t.chave) && (!termo || t.chave.includes(termo)))
    .slice(0, 8);
  const cheio = valor.length >= MAX_TAGS;

  function adicionar(bruto: string) {
    const r = rotuloTag(bruto);
    if (!r) return;
    onChange(normalizarTags([...valor, r]));
    setTexto("");
  }

  function aoTeclar(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" || e.key === "," || e.key === ";") {
      e.preventDefault();
      adicionar(texto);
    } else if (e.key === "Backspace" && texto === "" && valor.length > 0) {
      onChange(valor.slice(0, -1));
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {rotulo} <span className="font-normal text-muted">(opcional, até {MAX_TAGS})</span>
      </label>
      <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-line bg-surface px-2 py-1.5">
        {valor.map((t) => (
          <span key={chaveTag(t)} className="inline-flex items-center gap-1 rounded-full bg-primary-soft px-2 py-0.5 text-xs font-medium text-primary">
            #{t}
            <button type="button" aria-label={`Remover tag ${t}`} onClick={() => onChange(valor.filter((x) => chaveTag(x) !== chaveTag(t)))}>
              <X size={12} aria-hidden />
            </button>
          </span>
        ))}
        <input
          id={id}
          value={texto}
          maxLength={MAX_TAG_CHARS + 1}
          disabled={cheio}
          placeholder={cheio ? "Máximo de tags atingido" : valor.length === 0 ? "ex.: viagem, reforma" : ""}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={aoTeclar}
          onBlur={() => adicionar(texto)}
          className="min-w-24 flex-1 bg-transparent py-0.5 text-sm outline-none"
          autoComplete="off"
        />
      </div>
      {sugestoes.length > 0 && !cheio && (
        <div className="flex flex-wrap gap-1.5" aria-label="Tags já usadas">
          {sugestoes.map((t) => (
            <button
              key={t.chave}
              type="button"
              onClick={() => adicionar(t.rotulo)}
              className="rounded-full border border-line px-2.5 py-0.5 text-xs text-muted hover:bg-surface-2 hover:text-fg"
            >
              #{t.rotulo}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
