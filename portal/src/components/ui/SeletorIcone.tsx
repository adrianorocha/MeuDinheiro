"use client";

import { useId, useMemo, useState } from "react";
import { GRUPO_ORIGINAIS, GRUPOS_PICS, NOME_PIC, PicIcon } from "./PicIcon";

const semAcento = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

interface Props {
  valor: string;
  onChange: (pic: string) => void;
  rotulo?: string;
}

/** Seletor visual de ícone de categoria: grade agrupada com busca por nome. Pic desconhecido aparece como "Atual". */
export function SeletorIcone({ valor, onChange, rotulo = "Ícone" }: Props) {
  const idBusca = useId();
  const [busca, setBusca] = useState("");

  const grupos = useMemo(() => {
    const base = [GRUPO_ORIGINAIS, ...GRUPOS_PICS];
    const conhecido = base.some((g) => g.itens.some((i) => i.chave === valor));
    const todos = !valor || conhecido ? base : [{ id: "atual", rotulo: "Atual", itens: [{ chave: valor, rotulo: NOME_PIC[valor] ?? valor }] }, ...base];
    const q = semAcento(busca.trim());
    if (!q) return todos;
    return todos
      .map((g) => ({ ...g, itens: g.itens.filter((i) => semAcento(i.rotulo).includes(q) || semAcento(i.chave).includes(q)) }))
      .filter((g) => g.itens.length > 0);
  }, [busca, valor]);

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={idBusca} className="text-sm font-medium">
        {rotulo}: <span className="font-normal text-muted">{NOME_PIC[valor] ?? valor}</span>
      </label>
      <input
        id={idBusca}
        type="search"
        value={busca}
        onChange={(e) => setBusca(e.target.value)}
        placeholder="Buscar ícone…"
        autoComplete="off"
        className="h-10 w-full rounded-lg border border-line bg-surface px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      />
      <div className="max-h-64 overflow-y-auto rounded-lg border border-line p-2" role="group" aria-label={rotulo}>
        {grupos.length === 0 && <p className="p-2 text-sm text-muted">Nenhum ícone encontrado.</p>}
        {grupos.map((g) => (
          <section key={g.id} aria-label={g.rotulo} className="mb-3 last:mb-0">
            <h4 className="mb-1.5 px-1 text-xs font-semibold uppercase tracking-wide text-muted">{g.rotulo}</h4>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(2.75rem,1fr))] gap-1.5">
              {g.itens.map((i) => {
                const ativo = i.chave === valor;
                return (
                  <button
                    key={i.chave}
                    type="button"
                    aria-label={i.rotulo}
                    aria-pressed={ativo}
                    title={i.rotulo}
                    onClick={() => onChange(i.chave)}
                    className={`flex size-11 items-center justify-center rounded-lg border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
                      ativo ? "border-primary bg-primary-soft text-primary" : "border-transparent hover:bg-primary-soft/60"
                    }`}
                  >
                    <PicIcon pic={i.chave} size={20} />
                  </button>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
