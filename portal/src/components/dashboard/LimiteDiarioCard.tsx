"use client";

import { CalendarDays } from "lucide-react";
import Link from "next/link";
import { useMemo } from "react";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { limiteDiarioDoDataset, type StatusLimite } from "@/lib/finance/projecao";
import { useAgora, useDataset } from "@/lib/hooks";
import { useConfigGeral } from "@/lib/configGeral";
import { useSession } from "@/lib/session";

const ROTULO: Record<StatusLimite, { texto: string; tom: "pos" | "warn" | "neg" }> = {
  VERDE: { texto: "Dentro do limite", tom: "pos" },
  AMARELO: { texto: "Perto do limite", tom: "warn" },
  VERMELHO: { texto: "Acima do limite", tom: "neg" },
};

/** R51 - "Quanto posso gastar hoje" (card compacto do dashboard). */
export function LimiteDiarioCard() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  // R57: a opção é local-first e sincroniza com a nuvem (configuracoes/geral); padrão = fim do mês.
  const ateReceita = useConfigGeral((s) => s.config.limiteDiarioAteReceita === true);
  const alterarConfig = useConfigGeral((s) => s.alterar);

  const r = useMemo(() => limiteDiarioDoDataset(ds, agora, { ateProximaReceita: ateReceita }), [ds, agora, ateReceita]);
  const info = r.mesNoVermelho ? { texto: "Mês no vermelho", tom: "neg" as const } : ROTULO[r.status];

  return (
    <Card aria-labelledby="t-limite" className="mt-3">
      <CardTitulo id="t-limite" acao={<Badge tom={info.tom}>{info.texto}</Badge>}>
        Quanto posso gastar hoje
      </CardTitulo>
      <div className="flex flex-wrap items-end gap-x-6 gap-y-1">
        <p className="text-2xl font-semibold tracking-tight">
          <Money valor={r.restanteHojeCentavos / 100} tom="auto" />
          <span className="ml-2 text-xs font-normal text-muted">restante de hoje</span>
        </p>
        <p className="text-xs text-muted">
          Limite diário <Money valor={r.limiteDiarioCentavos / 100} /> · gasto hoje <Money valor={r.gastoHojeCentavos / 100} /> · {r.diasRestantes}{" "}
          {r.diasRestantes === 1 ? "dia" : "dias"} até o fim do período
        </p>
      </div>
      <p className="mt-2 text-sm" role="status">
        {privado ? info.texto : r.mensagem}
      </p>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        <label className="flex items-center gap-2 text-xs text-muted">
          <input
            type="checkbox"
            checked={ateReceita}
            onChange={(e) => alterarConfig({ limiteDiarioAteReceita: e.target.checked })}
          />
          Considerar até a próxima receita prevista
        </label>
        <Link href="/calendario/" className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">
          <CalendarDays size={14} aria-hidden /> Ver calendário
        </Link>
      </div>
    </Card>
  );
}
