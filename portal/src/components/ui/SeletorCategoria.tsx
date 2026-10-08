"use client";

import { useMemo, useState } from "react";
import { categoriasDisponiveis } from "@/lib/catalogo";
import type { Categoria } from "@/lib/finance/types";
import { Input, Select } from "./Field";

const OUTRA = "__outra__";

interface Props {
  valor: string;
  onChange: (valor: string) => void;
  /** Categorias personalizadas do usuário (as padrão do app são somadas automaticamente). */
  personalizadas: readonly Categoria[];
  rotulo?: string;
  disabled?: boolean;
  dica?: string;
}

/** Lista de categorias (padrão do app + personalizadas) com opção "Outra…" para digitar uma nova. */
export function SeletorCategoria({ valor, onChange, personalizadas, rotulo = "Categoria", disabled, dica }: Props) {
  const opcoes = useMemo(() => categoriasDisponiveis(personalizadas), [personalizadas]);
  const existe = (v: string) => opcoes.some((o) => o.nome.trim().toLowerCase() === v.trim().toLowerCase());
  const [digitando, setDigitando] = useState(() => valor !== "" && !existe(valor));
  const selecionado = digitando ? OUTRA : opcoes.find((o) => o.nome.trim().toLowerCase() === valor.trim().toLowerCase())?.nome ?? "";

  return (
    <div className="flex flex-col gap-2">
      <Select
        rotulo={rotulo}
        value={selecionado}
        disabled={disabled}
        dica={dica}
        required
        onChange={(e) => {
          if (e.target.value === OUTRA) {
            setDigitando(true);
            onChange("");
          } else {
            setDigitando(false);
            onChange(e.target.value);
          }
        }}
      >
        <option value="" disabled>
          Selecione…
        </option>
        {opcoes.map((o) => (
          <option key={o.nome} value={o.nome}>
            {o.nome}
          </option>
        ))}
        <option value={OUTRA}>Outra…</option>
      </Select>
      {digitando && (
        <Input rotulo="Nova categoria" value={valor} onChange={(e) => onChange(e.target.value)} maxLength={60} required autoFocus disabled={disabled} />
      )}
    </div>
  );
}
