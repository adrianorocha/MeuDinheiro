"use client";

import { FilePlus2, Gauge, ListChecks, Sparkles } from "lucide-react";
import { BarraEmpilhada, LegendaClasses } from "@/components/conciliacao/BarraEmpilhada";
import { Checkbox, Input } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import type { OpcoesLote as Opcoes, ResultadoArquivo } from "@/lib/conciliacao/lote-multiplo";

interface Props {
  opcoes: Opcoes;
  onChange: (o: Opcoes) => void;
  /** prévia por arquivo (sem aplicar) */
  previa: ResultadoArquivo[];
}

/** Passo 2 do lote: modo "Conciliação rápida", opções e prévia por arquivo (sem aplicar). */
export function OpcoesLote({ opcoes, onChange, previa }: Props) {
  const set = (p: Partial<Opcoes>) => onChange({ ...opcoes, ...p });
  return (
    <div className="space-y-4">
      <Card aria-labelledby="t-opcoes">
        <CardTitulo id="t-opcoes">
          <span className="inline-flex items-center gap-2">
            <Gauge size={16} aria-hidden /> 2. Opções do lote · Conciliação rápida
          </span>
        </CardTitulo>
        <div className="grid gap-4 lg:grid-cols-2">
          <fieldset className="space-y-3">
            <legend className="sr-only">O que fazer em cada arquivo</legend>
            <div>
              <Checkbox rotulo="Conciliar automáticos (valor exato, data próxima, candidato único)" checked disabled readOnly />
            </div>
            <div>
              <Checkbox rotulo="Incluir sugeridos de alta confiança" checked={opcoes.sugeridos} onChange={(e) => set({ sugeridos: e.target.checked })} />
              <p className="ml-6 text-xs text-muted">Só valor exato, pontuação alta e sem concorrente próximo. Os demais ficam para revisão.</p>
            </div>
            <div>
              <Checkbox rotulo="Criar lançamentos que só estão no extrato" checked={opcoes.criar} onChange={(e) => set({ criar: e.target.checked })} />
              <p className="ml-6 text-xs text-muted">Categoria sugerida pelo histórico (ou &quot;Outros&quot;). Conta: já pago; cartão: pendente na fatura.</p>
            </div>
            <div>
              <Checkbox
                rotulo="Lançar parcelas restantes (reserva o limite)"
                checked={opcoes.parcelasRestantes !== false}
                disabled={!opcoes.criar}
                onChange={(e) => set({ parcelasRestantes: e.target.checked })}
              />
              <p className="ml-6 text-xs text-muted">Em cartão, uma compra 3/10 criada do extrato também lança 4/10 a 10/10 em aberto, para o limite refletir o saldo devedor. Só vale com &quot;Criar lançamentos&quot;.</p>
            </div>
          </fieldset>
          <div className="grid grid-cols-2 gap-3">
            <Input
              rotulo="Janela de datas (± dias)"
              type="number"
              min={0}
              max={30}
              placeholder="Auto"
              value={opcoes.janelaDias ?? ""}
              onChange={(e) => set({ janelaDias: e.target.value === "" ? undefined : Math.max(0, Math.min(30, Number(e.target.value) || 0)) })}
              dica="Vazio = padrão: 3 (conta) ou 5 (cartão)."
            />
            <Input
              rotulo="Tolerância de valor (%)"
              type="number"
              min={0}
              max={20}
              step={0.5}
              value={(opcoes.toleranciaPct ?? 0.02) * 100}
              onChange={(e) => set({ toleranciaPct: Math.max(0, Math.min(20, Number(e.target.value) || 0)) / 100 })}
              dica="Mínimo de R$ 1,00."
            />
          </div>
        </div>
        <p className="mt-3 flex items-start gap-2 text-xs text-muted">
          <Sparkles size={14} className="mt-0.5 shrink-0" aria-hidden />
          Os arquivos são processados em sequência: cada um é comparado com o estado já atualizado pelo anterior, então transações repetidas entre períodos que se sobrepõem viram duplicadas e não geram lançamentos duplos.
        </p>
      </Card>

      <Card aria-labelledby="t-previa">
        <CardTitulo id="t-previa">
          <span className="inline-flex items-center gap-2">
            <ListChecks size={16} aria-hidden /> Prévia por arquivo (nada foi aplicado ainda)
          </span>
        </CardTitulo>
        <LegendaClasses />
        <ul className="mt-3 space-y-3">
          {previa.map((p) => (
            <li key={p.id} className="rounded-xl border border-line p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="min-w-0 truncate text-sm font-medium" title={p.nome}>
                  {p.nome}
                </p>
                <span className="text-xs text-muted">{p.rotuloDestino}</span>
              </div>
              <div className="my-2">
                <BarraEmpilhada classes={p.classes} rotulo={`Distribuição de ${p.nome}`} />
              </div>
              <div className="flex flex-wrap gap-1.5 text-xs">
                <Badge tom="pos">{p.classes.AUTOMATICO} automáticos</Badge>
                <Badge tom="warn">{p.classes.SUGERIDO} sugeridos</Badge>
                <Badge tom="neg">{p.classes.SO_NO_EXTRATO} só no extrato</Badge>
                <Badge>{p.classes.DUPLICADO} duplicados</Badge>
                <Badge tom="primary">
                  <FilePlus2 size={12} className="mr-1" aria-hidden />
                  {p.contadores.conciliados} conciliar · {p.contadores.criados} criar
                </Badge>
              </div>
              {p.status === "ERRO" && <p className="mt-1 text-xs text-neg">Este arquivo não poderá ser aplicado: {p.erro}</p>}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
