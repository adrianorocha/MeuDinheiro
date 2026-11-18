"use client";

import { ChevronLeft, ChevronRight, Copy, Share2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Segmentado } from "@/components/ui/Field";
import { Badge, Card, EmptyState, PageHeader } from "@/components/ui/Misc";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { mesAnoDe } from "@/lib/finance/dates";
import { resumoInteligente, textoDoResumo, type Insight, type TipoPeriodo, type TomInsight } from "@/lib/finance/resumo";
import { MASCARA } from "@/lib/format";
import { useAgora, useDataset, useNotificar } from "@/lib/hooks";
import { useSession } from "@/lib/session";

const TOM_BADGE: Record<TomInsight, { tom: "pos" | "neg" | "warn" | "neutro"; rotulo: string }> = {
  POSITIVO: { tom: "pos", rotulo: "Bom sinal" },
  NEGATIVO: { tom: "neg", rotulo: "Atenção" },
  ALERTA: { tom: "warn", rotulo: "Alerta" },
  NEUTRO: { tom: "neutro", rotulo: "Informação" },
};

const mascarar = (t: string, privado: boolean) => (privado ? t.replace(/-?R\$ [\d.]+,\d{2}/g, MASCARA) : t);

function CartaoInsight({ i, privado }: { i: Insight; privado: boolean }) {
  const b = TOM_BADGE[i.tom];
  return (
    <li>
      <Card as="article" aria-label={i.titulo}>
        <div className="mb-1 flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">{i.titulo}</h2>
          <Badge tom={b.tom}>{b.rotulo}</Badge>
        </div>
        <p className="text-sm text-fg">{mascarar(i.texto, privado)}</p>
      </Card>
    </li>
  );
}

/** R53 - resumo mensal/semanal inteligente (sem campos novos; tudo calculado dos lançamentos). */
export default function ResumoPage() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const notificar = useNotificar();
  const [tipo, setTipo] = useState<TipoPeriodo>("MES");
  const [ref, setRef] = useState(agora);

  const r = useMemo(() => resumoInteligente(ds, tipo, ref, agora), [ds, tipo, ref, agora]);
  const { mes, ano } = mesAnoDe(ref);
  const texto = useMemo(() => textoDoResumo(r), [r]);

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(texto);
      notificar.sucesso("Resumo copiado.");
    } catch {
      notificar.erro("Não foi possível copiar. Selecione o texto manualmente.");
    }
  };
  const compartilhar = async () => {
    if (typeof navigator !== "undefined" && typeof navigator.share === "function") {
      try {
        await navigator.share({ title: `Resumo de ${r.periodo.rotulo}`, text: texto });
      } catch {
        // usuário cancelou o compartilhamento
      }
    } else {
      await copiar();
    }
  };
  const andarSemana = (dias: number) => {
    const d = new Date(ref);
    setRef(new Date(d.getFullYear(), d.getMonth(), d.getDate() + dias, 12).getTime());
  };

  return (
    <>
      <PageHeader
        titulo="Resumo inteligente"
        descricao="O que mudou em relação ao período anterior, do mais para o menos relevante"
        acoes={
          <>
            <Button variante="secondary" icone={<Copy size={16} aria-hidden />} onClick={() => void copiar()}>
              Copiar
            </Button>
            <Button variante="primary" icone={<Share2 size={16} aria-hidden />} onClick={() => void compartilhar()}>
              Compartilhar
            </Button>
          </>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="w-56">
          <Segmentado
            rotulo="Período do resumo"
            valor={tipo}
            opcoes={[
              { valor: "MES", rotulo: "Mês" },
              { valor: "SEMANA", rotulo: "Semana" },
            ]}
            onChange={(v) => setTipo(v)}
          />
        </div>
        {tipo === "MES" ? (
          <MonthPicker mes={mes} ano={ano} onChange={(v) => setRef(new Date(v.ano, v.mes - 1, 15, 12).getTime())} />
        ) : (
          <div role="group" aria-label="Semana" className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-1">
            <IconButton rotulo="Semana anterior" onClick={() => andarSemana(-7)}>
              <ChevronLeft size={18} aria-hidden />
            </IconButton>
            <span className="min-w-44 text-center text-sm font-medium" aria-live="polite">
              {r.periodo.rotulo}
            </span>
            <IconButton rotulo="Próxima semana" onClick={() => andarSemana(7)}>
              <ChevronRight size={18} aria-hidden />
            </IconButton>
          </div>
        )}
        {r.periodo.emAndamento && <Badge tom="primary">Em andamento: dia {r.periodo.diasDecorridos} de {r.periodo.diasTotal}</Badge>}
      </div>

      {r.vazio || r.insights.length === 0 ? (
        <EmptyState titulo="Sem dados para resumir" descricao="Não há lançamentos comuns neste período nem no anterior. Escolha outro período ou registre seus gastos." />
      ) : (
        <ul className="grid gap-3 md:grid-cols-2" aria-label="Insights do período">
          {r.insights.map((i) => (
            <CartaoInsight key={i.id} i={i} privado={privado} />
          ))}
        </ul>
      )}
      <p className="mt-4 text-xs text-muted">
        Não entram transferências, aportes/resgates de metas, pagamentos de fatura nem ajustes. Em período em andamento a comparação é feita com o mesmo ponto do período anterior.
      </p>
    </>
  );
}
