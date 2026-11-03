import { Money } from "@/components/ui/Money";
import type { ConferenciaFatura } from "@/lib/conciliacao/fatura-total";

const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

interface Props {
  antes: ConferenciaFatura;
  /** total do app depois de aplicar o plano (prévia), quando houver */
  depois?: ConferenciaFatura | null;
}

/** R102 - total da fatura no extrato × soma dos lançamentos do app, com a diferença e as faturas conferidas. */
export function ConferenciaFaturaBox({ antes, depois }: Props) {
  const ref = depois ?? antes;
  return (
    <div className={`rounded-xl px-3 py-2 text-sm ${ref.bate ? "bg-pos-soft" : "bg-warn-soft"}`} role="status">
      <p className="text-xs text-muted">
        Total da fatura{antes.faturas.length > 1 ? "s" : ""} ({antes.faturas.map((f) => `${MESES[f.mes - 1]}/${String(f.ano).slice(2)}`).join(", ")})
      </p>
      <dl className="mt-1 grid grid-cols-2 gap-x-4 gap-y-1">
        <dt className="text-muted">No extrato</dt>
        <dd className="text-right font-semibold">
          <Money valor={antes.totalExtrato} />
        </dd>
        <dt className="text-muted">Soma dos lançamentos{depois ? " (agora)" : ""}</dt>
        <dd className="text-right font-semibold">
          <Money valor={antes.totalApp} />
        </dd>
        {depois && (
          <>
            <dt className="text-muted">Soma dos lançamentos (após aplicar)</dt>
            <dd className="text-right font-semibold">
              <Money valor={depois.totalApp} />
            </dd>
          </>
        )}
        <dt className="text-muted">Em aberto no app</dt>
        <dd className="text-right">
          <Money valor={ref.emAbertoApp} />
        </dd>
        <dt className="font-medium">Diferença</dt>
        <dd className="text-right font-semibold">{ref.bate ? "Bate" : <Money valor={ref.diferenca} tom="auto" />}</dd>
      </dl>
      {!ref.bate && <p className="mt-1 text-xs text-muted">Falta ou sobra lançamento, ou algum valor/data difere do extrato (veja as abas &ldquo;Só no extrato&rdquo; e &ldquo;Só no app&rdquo;).</p>}
    </div>
  );
}
