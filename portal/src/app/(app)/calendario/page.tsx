"use client";

import { AlertTriangle, ArrowDownCircle, ArrowUpCircle, CircleCheck, CreditCard, Repeat } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitulo, PageHeader } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { mesAnoDe } from "@/lib/finance/dates";
import { brlCentavos, calendarioDoDataset, dataDeSerial, serialDeMs, type DiaCalendario, type EventoProjecao } from "@/lib/finance/projecao";
import { useAgora, useDataset } from "@/lib/hooks";
import { useSession } from "@/lib/session";
import { acoes } from "@/lib/store/actions";
import { useStore } from "@/lib/store/store";

const SEMANA = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"];

function compacto(centavos: number): string {
  const reais = Math.abs(centavos) / 100;
  const t = reais >= 1000 ? `${(reais / 1000).toFixed(1).replace(".", ",")}k` : String(Math.round(reais));
  return `${centavos < 0 ? "-" : ""}${t}`;
}

function rotuloTipo(e: EventoProjecao): string {
  switch (e.tipo) {
    case "RECEITA":
      return "A receber";
    case "RECORRENCIA_RECEITA":
      return "Recebimento recorrente (ainda não lançado)";
    case "RECORRENCIA_DESPESA":
      return "Despesa recorrente (ainda não lançada)";
    case "FATURA":
      return "Fatura do cartão";
    default:
      return "A pagar";
  }
}

function descricaoDia(d: DiaCalendario, privado: boolean): string {
  const partes = [`Dia ${d.diaDoMes}`];
  if (d.ehHoje) partes.push("hoje");
  if (d.eventos.length > 0) partes.push(`${d.eventos.length} item(ns)`);
  if (d.saldoProjetadoCentavos !== null && !privado) {
    partes.push(`saldo projetado ${brlCentavos(d.saldoProjetadoCentavos)}${d.negativo ? ", negativo" : ""}`);
  } else if (d.negativo) {
    partes.push("saldo projetado negativo");
  }
  return partes.join(", ");
}

function Item({ e }: { e: EventoProjecao }) {
  const avisar = useStore((s) => s.avisar);
  const entrada = e.centavos > 0;
  const Icone = e.tipo === "FATURA" ? CreditCard : e.tipo.startsWith("RECORRENCIA") ? Repeat : entrada ? ArrowUpCircle : ArrowDownCircle;
  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <span className={`flex size-8 items-center justify-center rounded-full ${entrada ? "bg-pos-soft text-pos" : "bg-neg-soft text-neg"}`} aria-hidden>
        <Icone size={16} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{e.descricao}</p>
        <p className="truncate text-xs text-muted">
          {rotuloTipo(e)} · {e.origem}
        </p>
      </div>
      {e.atrasado && <Badge tom="neg">Atrasado</Badge>}
      <Money valor={e.centavos / 100} tom="auto" className="text-sm font-semibold" />
      {e.lancamentoId !== null && (
        <Button
          tamanho="sm"
          icone={<CircleCheck size={14} aria-hidden />}
          aria-label={`Dar baixa em ${e.descricao}`}
          onClick={() => {
            const r = acoes.darBaixa(e.lancamentoId as number);
            avisar(r.ok ? "sucesso" : "erro", r.ok ? `Baixa em "${e.descricao}".` : r.erro);
          }}
        >
          Dar baixa
        </Button>
      )}
      {e.tipo === "FATURA" && (
        <Link href="/cartoes/" className="text-sm font-medium text-primary hover:underline">
          Pagar
        </Link>
      )}
    </li>
  );
}

export default function CalendarioPage() {
  const ds = useDataset();
  const agora = useAgora();
  const privado = useSession((s) => s.privado);
  const [mesAno, setMesAno] = useState(() => mesAnoDe(agora));
  const [escolhido, setEscolhido] = useState<number | null>(null);

  const cal = useMemo(() => calendarioDoDataset(ds, agora, mesAno.mes, mesAno.ano), [ds, agora, mesAno]);
  const hoje = serialDeMs(agora);
  const selecionado = cal.dias.find((d) => d.dia === escolhido) ?? cal.dias.find((d) => d.dia === hoje) ?? cal.dias[0];
  const vazios = cal.dias[0].diaDaSemana;
  const menorDia = cal.menorSaldo ? dataDeSerial(cal.menorSaldo.dia) : null;
  const dd = (n: number) => String(n).padStart(2, "0");

  return (
    <>
      <PageHeader
        titulo="Calendário financeiro"
        descricao="Vencimentos, recebimentos previstos e saldo projetado dia a dia"
        acoes={
          <MonthPicker
            mes={mesAno.mes}
            ano={mesAno.ano}
            onChange={(v) => {
              setMesAno(v);
              setEscolhido(null);
            }}
          />
        }
      />

      {cal.alerta && (
        <div role="alert" className="mb-4 flex items-start gap-2 rounded-xl border border-neg/40 bg-neg-soft px-4 py-3 text-sm text-neg">
          <AlertTriangle size={18} className="mt-0.5 shrink-0" aria-hidden />
          <span>{cal.alerta}</span>
        </div>
      )}

      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <Card>
          <p className="text-xs font-medium text-muted">Menor saldo projetado do mês</p>
          <p className="mt-1 text-xl font-semibold">
            {cal.menorSaldo && menorDia ? (
              <>
                <Money valor={cal.menorSaldo.centavos / 100} tom="auto" />
                <span className="ml-2 text-xs font-normal text-muted">
                  em {dd(menorDia.dia)}/{dd(menorDia.mes)}
                </span>
              </>
            ) : (
              <span className="text-sm font-normal text-muted">Sem projeção para meses passados.</span>
            )}
          </p>
        </Card>
        <Card>
          <p className="text-xs font-medium text-muted">Saldo projetado no fim do mês</p>
          <p className="mt-1 text-xl font-semibold">
            {cal.saldoFimDoMesCentavos !== null ? (
              <Money valor={cal.saldoFimDoMesCentavos / 100} tom="auto" />
            ) : (
              <span className="text-sm font-normal text-muted">-</span>
            )}
          </p>
        </Card>
      </div>

      <Card aria-label="Calendário do mês">
        <div className="grid grid-cols-7 gap-1 text-center text-xs font-medium text-muted" aria-hidden>
          {SEMANA.map((s) => (
            <div key={s}>{s}</div>
          ))}
        </div>
        <div className="mt-1 grid grid-cols-7 gap-1" role="group" aria-label={`Dias de ${dd(cal.mes)}/${cal.ano}`}>
          {Array.from({ length: vazios }, (_, i) => (
            <div key={`v${i}`} aria-hidden />
          ))}
          {cal.dias.map((d) => {
            const sel = d.dia === selecionado.dia;
            return (
              <button
                key={d.dia}
                type="button"
                onClick={() => setEscolhido(d.dia)}
                aria-pressed={sel}
                aria-label={descricaoDia(d, privado)}
                className={`flex min-h-16 flex-col items-stretch rounded-lg border p-1 text-left text-[11px] transition-colors sm:min-h-20 sm:p-1.5 ${
                  d.negativo ? "border-neg/60 bg-neg-soft" : "border-line bg-surface hover:bg-surface-2"
                } ${d.passado ? "opacity-60" : ""} ${sel ? "ring-2 ring-primary" : ""}`}
              >
                <span className="flex items-center justify-between">
                  <span className={`text-xs font-semibold ${d.ehHoje ? "rounded-full bg-primary px-1.5 text-primary-fg" : ""}`}>{d.diaDoMes}</span>
                  {d.negativo && <AlertTriangle size={12} className="text-neg" aria-hidden />}
                </span>
                <span className="mt-0.5 flex flex-wrap items-center gap-x-1 text-muted">
                  {d.entradasCentavos > 0 && <ArrowUpCircle size={10} className="text-pos" aria-hidden />}
                  {d.saidasCentavos > 0 && <ArrowDownCircle size={10} className="text-neg" aria-hidden />}
                  {d.eventos.length > 0 && <span>{d.eventos.length}</span>}
                </span>
                {d.saldoProjetadoCentavos !== null && (
                  <span className={`tabular mt-auto truncate font-medium ${d.negativo ? "text-neg" : ""}`}>
                    {privado ? "•••" : compacto(d.saldoProjetadoCentavos)}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted" aria-label="Legenda">
          <li className="inline-flex items-center gap-1">
            <ArrowUpCircle size={12} className="text-pos" aria-hidden /> Recebimento previsto
          </li>
          <li className="inline-flex items-center gap-1">
            <ArrowDownCircle size={12} className="text-neg" aria-hidden /> Vencimento (conta, fatura ou recorrência)
          </li>
          <li className="inline-flex items-center gap-1">
            <AlertTriangle size={12} className="text-neg" aria-hidden /> Saldo projetado negativo (fundo vermelho)
          </li>
          <li className="inline-flex items-center gap-1">
            <span className="rounded-full bg-primary px-1.5 text-primary-fg">9</span> Hoje
          </li>
          <li>Número no rodapé do dia: saldo projetado ao fim do dia (R$; k = mil). Dias passados não têm projeção.</li>
        </ul>
      </Card>

      <Card className="mt-4" aria-label="Itens do dia selecionado">
        <CardTitulo>
          {dd(selecionado.diaDoMes)}/{dd(selecionado.mes)}/{selecionado.ano}
          {selecionado.ehHoje ? " (hoje)" : ""} · {selecionado.eventos.length} item(ns)
        </CardTitulo>
        {selecionado.eventos.length === 0 ? (
          <p className="text-sm text-muted">Nenhum vencimento ou recebimento previsto neste dia.</p>
        ) : (
          <ul className="divide-y divide-line">
            {selecionado.eventos.map((e, i) => (
              <Item key={`${e.tipo}-${e.lancamentoId ?? e.cartaoId ?? "x"}-${i}`} e={e} />
            ))}
          </ul>
        )}
        {selecionado.saldoProjetadoCentavos !== null && (
          <p className="mt-3 text-sm text-muted">
            Saldo projetado ao fim do dia: <Money valor={selecionado.saldoProjetadoCentavos / 100} tom="auto" className="font-semibold" />
          </p>
        )}
      </Card>
    </>
  );
}
