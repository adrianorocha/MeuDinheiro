"use client";

import { Plus } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox, Input, Segmentado } from "@/components/ui/Field";
import { ErroBox } from "@/components/ui/Misc";
import { Modal } from "@/components/ui/Modal";
import { Money } from "@/components/ui/Money";
import { brlCentavos, decimosParaTexto } from "@/lib/finance/centavos";
import { toCents } from "@/lib/finance/money";
import { grupoRateioDe, ratear, type ModoRateio, type ParticipanteRateio } from "@/lib/finance/rateios";
import type { Despesa } from "@/lib/finance/types";
import { formatData, parseValorBR } from "@/lib/format";
import { useDataset } from "@/lib/hooks";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const MODOS: readonly { valor: ModoRateio; rotulo: string }[] = [
  { valor: "IGUAL", rotulo: "Igual" },
  { valor: "VALOR", rotulo: "Por valor" },
  { valor: "PERCENTUAL", rotulo: "Por %" },
];

interface Linha {
  on: boolean;
  valor: string;
  pct: string;
}

/** Texto digitado -> centavos (0 se inválido). */
const centavosDe = (t: string) => {
  const v = parseValorBR(t);
  return Number.isFinite(v) && v > 0 ? toCents(v) : 0;
};
/** "33,3" -> 333 (décimos de ponto percentual). */
const decimosDe = (t: string) => {
  const v = parseValorBR(t);
  return Number.isFinite(v) && v > 0 ? Math.round(v * 10) : 0;
};

/**
 * R59 - "Dividir esta despesa": escolhe pessoas e cotas (igual, por valor ou por %). O lançamento continua com o valor TOTAL
 * (nada muda no saldo); só nascem acertos A_RECEBER. A sobra de centavos fica com você.
 */
export function DividirModal({ despesa, onFechar }: { despesa: Despesa | null; onFechar: () => void }) {
  return (
    <Modal aberto={despesa !== null} onFechar={onFechar} titulo={`Dividir: ${despesa?.descricao ?? ""}`}>
      {despesa && <Conteudo despesa={despesa} onFechar={onFechar} />}
    </Modal>
  );
}

function Conteudo({ despesa, onFechar }: { despesa: Despesa; onFechar: () => void }) {
  const ds = useDataset();
  const avisar = useStore((s) => s.avisar);
  const [modo, setModo] = useState<ModoRateio>("IGUAL");
  const [linhas, setLinhas] = useState<Record<number, Linha>>({});
  const [novoNome, setNovoNome] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [desfazendo, setDesfazendo] = useState(false);

  const total = toCents(despesa.valor);
  const existentes = ds.acertos.filter((a) => a.grupoRateio === grupoRateioDe(despesa.id));
  const pessoas = useMemo(() => [...ds.pessoas].sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR")), [ds.pessoas]);
  const linha = (id: number): Linha => linhas[id] ?? { on: false, valor: "", pct: "" };
  const marcadas = pessoas.filter((p) => linha(p.id).on);

  const participantes: ParticipanteRateio[] = marcadas.map((p) =>
    modo === "VALOR" ? { pessoaId: p.id, valor: centavosDe(linha(p.id).valor) } : modo === "PERCENTUAL" ? { pessoaId: p.id, percentual: decimosDe(linha(p.id).pct) } : { pessoaId: p.id },
  );
  const somaPct = participantes.reduce((s, p) => s + (p.percentual ?? 0), 0);
  const vocePct = 1000 - somaPct;
  const previa = marcadas.length > 0 ? ratear(total, modo, participantes, modo === "PERCENTUAL" ? vocePct : undefined) : null;

  function alterar(id: number, patch: Partial<Linha>) {
    setLinhas((l) => ({ ...l, [id]: { ...linha(id), ...patch } }));
    setErro(null);
  }

  function adicionarPessoa() {
    const r = acoes.salvarPessoa({ nome: novoNome });
    if (!r.ok) return setErro(r.erro);
    setNovoNome("");
    alterar(r.pessoa.id, { on: true });
  }

  function enviar(e: FormEvent) {
    e.preventDefault();
    if (marcadas.length === 0) return setErro("Escolha ao menos uma pessoa.");
    const r = acoes.dividirDespesa({ despesaId: despesa.id, modo, participantes, vocePercentual: modo === "PERCENTUAL" ? vocePct : undefined });
    if (!r.ok) return setErro(r.erro);
    avisar("sucesso", `Despesa dividida: ${brlCentavos(r.voce)} ficam com você. O lançamento continua com o valor total.`);
    onFechar();
  }

  if (existentes.length > 0) {
    const aReceber = existentes.reduce((s, a) => s + toCents(a.valor), 0);
    return (
      <div className="flex flex-col gap-3 text-sm">
        <p className="text-muted">Esta despesa já foi dividida. O lançamento continua com o valor total ({brlCentavos(total)}).</p>
        <ul className="divide-y divide-line rounded-lg border border-line">
          {existentes.map((a) => (
            <li key={a.id} className="flex items-center justify-between gap-2 px-3 py-2">
              <span>{ds.pessoas.find((p) => p.id === a.pessoaId)?.nome ?? "Pessoa removida"}</span>
              <span className="flex items-center gap-2">
                <Money valor={a.valor} />
                <span className="text-xs text-muted">{a.quitado ? "recebido" : "a receber"}</span>
              </span>
            </li>
          ))}
        </ul>
        <p>
          Você realmente gastou <strong>{brlCentavos(total - aReceber)}</strong>.
        </p>
        {erro && <ErroBox>{erro}</ErroBox>}
        <div className="mt-2 flex justify-end gap-2">
          <Button onClick={onFechar}>Fechar</Button>
          <Button
            variante="danger"
            disabled={desfazendo}
            onClick={() => {
              setDesfazendo(true);
              const r = acoes.desfazerRateio(despesa.id);
              setDesfazendo(false);
              if (!r.ok) return setErro(r.erro);
              avisar("sucesso", "Divisão desfeita.");
              onFechar();
            }}
          >
            Desfazer divisão
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted">
        {formatData(despesa.data)} · total {brlCentavos(total)}. O lançamento NÃO muda e o saldo continua o mesmo: só são criados valores a receber das pessoas escolhidas.
      </p>
      <Segmentado rotulo="Como dividir" valor={modo} opcoes={MODOS} onChange={(m) => { setModo(m); setErro(null); }} />

      <div className="flex flex-col gap-2">
        {pessoas.length === 0 && <p className="text-sm text-muted">Nenhuma pessoa cadastrada. Adicione abaixo.</p>}
        {pessoas.map((p) => {
          const l = linha(p.id);
          const cota = previa && previa.ok ? previa.cotas.find((c) => c.pessoaId === p.id)?.centavos : undefined;
          return (
            <div key={p.id} className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <Checkbox rotulo={p.nome} checked={l.on} onChange={(e) => alterar(p.id, { on: e.target.checked })} />
              </div>
              {l.on && modo === "VALOR" && (
                <input aria-label={`Valor de ${p.nome}`} inputMode="decimal" placeholder="0,00" value={l.valor} onChange={(e) => alterar(p.id, { valor: e.target.value })} className="h-9 w-28 rounded-lg border border-line bg-surface px-2 text-right text-sm" />
              )}
              {l.on && modo === "PERCENTUAL" && (
                <input aria-label={`Percentual de ${p.nome}`} inputMode="decimal" placeholder="0" value={l.pct} onChange={(e) => alterar(p.id, { pct: e.target.value })} className="h-9 w-20 rounded-lg border border-line bg-surface px-2 text-right text-sm" />
              )}
              {l.on && cota !== undefined && <span className="w-24 text-right text-sm tabular-nums">{brlCentavos(cota)}</span>}
            </div>
          );
        })}
      </div>

      <div className="flex items-end gap-2">
        <Input rotulo="Nova pessoa" value={novoNome} onChange={(e) => setNovoNome(e.target.value)} maxLength={60} wrapperClassName="flex-1" />
        <Button icone={<Plus size={16} aria-hidden />} onClick={adicionarPessoa} disabled={!novoNome.trim()}>
          Adicionar
        </Button>
      </div>

      {previa && previa.ok && (
        <div className="rounded-lg border border-line bg-surface-2 p-3 text-sm" aria-live="polite">
          <p>
            Fica com você: <strong>{brlCentavos(previa.voce)}</strong>
            {modo === "PERCENTUAL" && <span className="text-muted"> ({decimosParaTexto(vocePct)}%)</span>}
          </p>
          <p className="text-muted">Quanto você realmente gasta com esta despesa: {brlCentavos(previa.voce)} de {brlCentavos(total)}.</p>
        </div>
      )}
      {previa && !previa.ok && <p className="text-sm text-warn" aria-live="polite">{previa.erro}</p>}
      {erro && <ErroBox>{erro}</ErroBox>}
      <div className="flex justify-end gap-2">
        <Button onClick={onFechar}>Cancelar</Button>
        <Button type="submit" variante="primary" disabled={!previa || !previa.ok}>
          Dividir
        </Button>
      </div>
    </form>
  );
}
