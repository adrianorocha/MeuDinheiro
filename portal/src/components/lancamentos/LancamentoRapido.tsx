"use client";

import { Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Input, InputValor, Segmentado, Select } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { sugerirCategoria } from "@/lib/finance/analises";
import { normalizar } from "@/lib/finance/texto";
import type { Dataset } from "@/lib/finance/types";
import { formatBRL, parseValorBR, valorParaCampo } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

interface Recente {
  descricao: string;
  categoria: string;
  valor: number;
  conta: string;
}

/** Últimos lançamentos NORMAIS distintos por descrição (para os chips). */
export function recentes(ds: Dataset, max: number): Recente[] {
  const vistos = new Set<string>();
  const out: Recente[] = [];
  for (const d of [...ds.despesas].sort((a, b) => b.data - a.data || b.id - a.id)) {
    if (d.natureza !== "NORMAL" || d.cartaoId) continue;
    const k = normalizar(d.descricao);
    if (!k || vistos.has(k)) continue;
    vistos.add(k);
    out.push({ descricao: d.descricao, categoria: d.categoria, valor: d.valor, conta: d.conta });
    if (out.length >= max) break;
  }
  return out;
}

function Chip({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} className="rounded-full border border-line bg-surface px-3 py-1 text-xs font-medium text-muted transition-colors hover:bg-surface-2 hover:text-fg">
      {children}
    </button>
  );
}

function Formulario({ onFechar }: { onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const rec = useMemo(() => recentes(ds, 6), [ds]);
  const cats = useMemo(() => [...new Set(rec.map((r) => r.categoria).filter(Boolean))].slice(0, 5), [rec]);
  const valores = useMemo(() => [...new Set(rec.map((r) => r.valor))].slice(0, 5), [rec]);
  const [tipo, setTipo] = useState<"DEBITO" | "CREDITO">("DEBITO");
  const [descricao, setDescricao] = useState("");
  const [valorTxt, setValorTxt] = useState("");
  const [categoria, setCategoria] = useState("");
  const [conta, setConta] = useState(ds.contas[0]?.conta ?? "");
  const [erro, setErro] = useState<string | null>(null);
  const sugestao = useMemo(() => sugerirCategoria(descricao, ds.despesas), [descricao, ds.despesas]);

  function enviar(e: FormEvent) {
    e.preventDefault();
    const valor = parseValorBR(valorTxt);
    if (!(valor > 0)) return setErro("Informe um valor maior que zero.");
    const pic = ds.categorias.find((c) => normalizar(c.nome) === normalizar(categoria))?.pic ?? "";
    const r = acoes.addLancamento({ descricao, valor, data: Date.now(), categoria, pic, tipo, conta });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", "Lançamento salvo.");
    onFechar();
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      {ds.contas.length === 0 && <ErroBox>Cadastre uma conta antes de lançar.</ErroBox>}
      <Segmentado
        rotulo="Tipo"
        valor={tipo}
        onChange={setTipo}
        opcoes={[
          { valor: "DEBITO", rotulo: "Despesa" },
          { valor: "CREDITO", rotulo: "Receita" },
        ]}
      />
      {rec.length > 0 && (
        <div>
          <p className="mb-1.5 text-xs text-muted">Recentes</p>
          <div className="flex flex-wrap gap-1.5">
            {rec.map((r) => (
              <Chip
                key={r.descricao}
                onClick={() => {
                  setDescricao(r.descricao);
                  setCategoria(r.categoria);
                  setValorTxt(valorParaCampo(r.valor));
                  if (ds.contas.some((c) => c.conta === r.conta)) setConta(r.conta);
                }}
              >
                {r.descricao}
              </Chip>
            ))}
          </div>
        </div>
      )}
      <Input rotulo="Descrição" value={descricao} onChange={(e) => setDescricao(e.target.value)} maxLength={120} autoFocus required />
      <InputValor rotulo="Valor (R$)" value={valorTxt} onChange={(e) => setValorTxt(e.target.value)} />
      {valores.length > 0 && (
        <div className="-mt-2 flex flex-wrap gap-1.5" aria-label="Valores recentes">
          {valores.map((v) => (
            <Chip key={v} onClick={() => setValorTxt(valorParaCampo(v))}>
              {formatBRL(v)}
            </Chip>
          ))}
        </div>
      )}
      <Input rotulo="Categoria" list="rapido-categorias" value={categoria} onChange={(e) => setCategoria(e.target.value)} required />
      <datalist id="rapido-categorias">
        {ds.categorias.map((c) => (
          <option key={c.id} value={c.nome} />
        ))}
      </datalist>
      <div className="-mt-2 flex flex-wrap items-center gap-1.5">
        {sugestao && normalizar(sugestao) !== normalizar(categoria) && (
          <Chip onClick={() => setCategoria(sugestao)}>Sugestão: usar {sugestao}</Chip>
        )}
        {cats.map((c) => (
          <Chip key={c} onClick={() => setCategoria(c)}>
            {c}
          </Chip>
        ))}
      </div>
      <Select rotulo="Conta" value={conta} onChange={(e) => setConta(e.target.value)}>
        {ds.contas.map((c) => (
          <option key={c.id} value={c.conta}>
            {c.banco} · {c.conta}
          </option>
        ))}
      </Select>
      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} />
    </form>
  );
}

function ehCampoDeTexto(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false;
  return ["INPUT", "TEXTAREA", "SELECT"].includes(el.tagName) || el.isContentEditable;
}

/** Botão "+" flutuante global e atalho de teclado "N" (fora de campos de texto e diálogos). */
export function LancamentoRapido() {
  const [aberto, setAberto] = useState(false);

  useEffect(() => {
    function tecla(e: KeyboardEvent) {
      if (e.key.toLowerCase() !== "n" || e.ctrlKey || e.metaKey || e.altKey || e.repeat) return;
      if (ehCampoDeTexto(e.target) || document.querySelector("dialog[open]")) return;
      e.preventDefault();
      setAberto(true);
    }
    window.addEventListener("keydown", tecla);
    return () => window.removeEventListener("keydown", tecla);
  }, []);

  return (
    <>
      <button
        type="button"
        onClick={() => setAberto(true)}
        aria-label="Lançamento rápido (atalho N)"
        title="Lançamento rápido (N)"
        className="fixed bottom-20 right-4 z-40 flex size-14 items-center justify-center rounded-full bg-primary text-primary-fg shadow-lg transition-transform hover:scale-105 md:bottom-6 md:right-6"
      >
        <Plus size={26} aria-hidden />
      </button>
      <Modal aberto={aberto} onFechar={() => setAberto(false)} titulo="Lançamento rápido">
        {aberto && <Formulario onFechar={() => setAberto(false)} />}
      </Modal>
    </>
  );
}
