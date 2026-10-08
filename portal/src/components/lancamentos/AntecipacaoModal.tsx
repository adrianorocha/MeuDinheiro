"use client";

import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Checkbox, Input, InputValor, Segmentado } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal, RodapeForm } from "@/components/ui/Modal";
import { saldoConta } from "@/lib/finance/calc";
import { antecipaveisDoGrupo } from "@/lib/finance/operations";
import { fromCents, round2, toCents } from "@/lib/finance/money";
import type { Despesa } from "@/lib/finance/types";
import { deInputData, formatBRL, formatData, paraInputData, parseValorBR } from "@/lib/format";
import { useAgora, useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

type Modo = "quitar" | "parcial";
type Desconto = "cobrado" | "valor" | "percentual";
type Ordem = "proximas" | "ultimas";

interface Props {
  aberto: boolean;
  onFechar: () => void;
  base: Despesa | null;
}

/** R40 — paga adiantado: quita parcelas (com desconto) ou abate só um valor do que vem pela frente. */
export function AntecipacaoModal({ aberto, onFechar, base }: Props) {
  return (
    <Modal aberto={aberto} onFechar={onFechar} titulo={`Antecipar: ${base?.descricao ?? ""}`}>
      {base && <Corpo key={base.id} base={base} onFechar={onFechar} />}
    </Modal>
  );
}

function Corpo({ base, onFechar }: { base: Despesa; onFechar: () => void }) {
  const ds = useDataset();
  const agora = useAgora();
  const avisar = useStore((s) => s.avisar);
  const candidatos = useMemo(() => antecipaveisDoGrupo(ds, base), [ds, base]);
  const [sel, setSel] = useState<Set<number>>(new Set([base.id]));
  const [modo, setModo] = useState<Modo>("quitar");
  const [descModo, setDescModo] = useState<Desconto>("cobrado");
  const [descTxt, setDescTxt] = useState("");
  const [parcialTxt, setParcialTxt] = useState("");
  const [ordem, setOrdem] = useState<Ordem>("proximas");
  const [dataTxt, setDataTxt] = useState(paraInputData(agora));
  const [erro, setErro] = useState<string | null>(null);

  const escolhidos = candidatos.filter((d) => sel.has(d.id));
  const devido = fromCents(escolhidos.reduce((s, d) => s + toCents(d.valor), 0));

  /** Resolve (pago, desconto) conforme o modo; null = entrada inválida. */
  const calculo = useMemo(() => {
    if (modo === "parcial") {
      const v = parseValorBR(parcialTxt);
      return Number.isFinite(v) && v > 0 ? { pago: round2(v), desconto: 0 } : null;
    }
    if (descTxt.trim() === "") return { pago: devido, desconto: 0 };
    const v = parseValorBR(descTxt);
    if (!Number.isFinite(v) || v < 0) return null;
    if (descModo === "cobrado") return { pago: round2(v), desconto: round2(devido - v) };
    if (descModo === "valor") return { pago: round2(devido - v), desconto: round2(v) };
    return { pago: round2(devido * (1 - v / 100)), desconto: round2((devido * v) / 100) };
  }, [modo, parcialTxt, descTxt, descModo, devido]);

  const conta = ds.contas.find((c) => c.conta === base.conta);
  const saldo = saldoConta(ds.despesas, base.conta);
  const pct = calculo && devido > 0 ? (calculo.desconto / devido) * 100 : 0;

  function alternar(id: number) {
    setSel((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (escolhidos.length === 0) return setErro("Selecione ao menos uma parcela.");
    if (!calculo) return setErro("Informe um valor válido.");
    const data = deInputData(dataTxt);
    if (!Number.isFinite(data)) return setErro("Data inválida.");
    const ultimaPrimeiro = ordem === "ultimas" && modo === "parcial";
    const ordenados = [...escolhidos].sort((a, b) => (ultimaPrimeiro ? b.data - a.data : a.data - b.data));
    const r = acoes.anteciparPagamento({ ids: ordenados.map((d) => d.id), valorPago: calculo.pago, desconto: calculo.desconto, data });
    if (!r.ok) return setErro(r.erro);
    avisar(
      "sucesso",
      r.economia > 0
        ? `Pago ${formatBRL(calculo.pago)} e abatido ${formatBRL(r.economia)} de desconto.`
        : `Pago ${formatBRL(calculo.pago)} adiantado.${r.restante > 0 ? ` Restam ${formatBRL(r.restante)} em aberto.` : ""}`,
    );
    onFechar();
  }

  if (candidatos.length === 0) return <p className="text-sm text-muted">Este lançamento não pode ser antecipado (compras de cartão são pagas pela fatura).</p>;

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <Segmentado
        rotulo="Tipo de antecipação"
        valor={modo}
        onChange={(m) => {
          setModo(m);
          setErro(null);
        }}
        opcoes={[
          { valor: "quitar", rotulo: "Quitar parcelas" },
          { valor: "parcial", rotulo: "Adiantar um valor" },
        ]}
      />

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 flex w-full items-center justify-between text-sm font-medium">
          <span>{candidatos.length > 1 ? "Parcelas em aberto" : "Lançamento"}</span>
          {candidatos.length > 1 && (
            <span className="flex gap-3 text-xs">
              <button type="button" className="underline" onClick={() => setSel(new Set(candidatos.map((d) => d.id)))}>
                Todas
              </button>
              <button type="button" className="underline" onClick={() => setSel(new Set(candidatos.slice(-2).map((d) => d.id)))}>
                Últimas 2
              </button>
            </span>
          )}
        </legend>
        <ul className="max-h-48 divide-y divide-line overflow-y-auto rounded-lg border border-line px-3">
          {candidatos.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-3 py-2">
              <Checkbox rotulo={`${d.descricao} · vence ${formatData(d.data)}`} checked={sel.has(d.id)} onChange={() => alternar(d.id)} />
              <span className="text-sm tabular-nums">{formatBRL(d.valor)}</span>
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted">
          Em aberto nas selecionadas: <strong>{formatBRL(devido)}</strong>
        </p>
      </fieldset>

      {modo === "quitar" ? (
        <>
          <Segmentado
            rotulo="Como informar o desconto"
            valor={descModo}
            onChange={(m) => {
              setDescModo(m);
              setDescTxt("");
            }}
            opcoes={[
              { valor: "cobrado", rotulo: "Valor cobrado" },
              { valor: "valor", rotulo: "Desconto R$" },
              { valor: "percentual", rotulo: "Desconto %" },
            ]}
          />
          <InputValor
            rotulo={descModo === "cobrado" ? "Valor cobrado pelo banco (R$)" : descModo === "valor" ? "Desconto (R$)" : "Desconto (%)"}
            value={descTxt}
            onChange={(e) => setDescTxt(e.target.value)}
            dica="Deixe vazio para pagar sem desconto. Na quitação antecipada, a lei dá direito à redução proporcional dos juros."
          />
        </>
      ) : (
        <>
          <InputValor
            rotulo="Valor a adiantar (R$)"
            value={parcialTxt}
            onChange={(e) => setParcialTxt(e.target.value)}
            dica="Abate do que vem pela frente; o que sobrar continua em aberto, com o mesmo vencimento."
          />
          {escolhidos.length > 1 && (
            <Segmentado
              rotulo="Abater a partir de"
              valor={ordem}
              onChange={setOrdem}
              opcoes={[
                { valor: "proximas", rotulo: "Parcela mais próxima" },
                { valor: "ultimas", rotulo: "Última parcela" },
              ]}
            />
          )}
        </>
      )}

      <Input rotulo="Data do pagamento" type="date" value={dataTxt} onChange={(e) => setDataTxt(e.target.value)} />

      <div className="rounded-lg bg-surface-2 p-3 text-sm" aria-live="polite">
        {calculo ? (
          <ul className="flex flex-col gap-1">
            <li className="flex justify-between">
              <span>Dívida abatida</span>
              <span>{formatBRL(fromCents(toCents(calculo.pago) + toCents(calculo.desconto)))}</span>
            </li>
            {calculo.desconto > 0 && (
              <li className="flex justify-between text-pos">
                <span>Desconto ({pct.toFixed(1).replace(".", ",")}%)</span>
                <span>− {formatBRL(calculo.desconto)}</span>
              </li>
            )}
            <li className="flex justify-between font-semibold">
              <span>Sai da conta</span>
              <span>{formatBRL(calculo.pago)}</span>
            </li>
            <li className="flex justify-between text-muted">
              <span>Saldo {conta ? `(${conta.banco})` : ""} depois</span>
              <span className={saldo - calculo.pago < 0 ? "text-neg" : ""}>{formatBRL(saldo - calculo.pago)}</span>
            </li>
          </ul>
        ) : (
          <p className="text-muted">Informe o valor para ver o resumo.</p>
        )}
      </div>

      {erro && <ErroBox>{erro}</ErroBox>}
      <RodapeForm onCancelar={onFechar} rotuloEnviar="Pagar agora" />
    </form>
  );
}
