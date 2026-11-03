"use client";

import { ExternalLink, Pencil, RefreshCw, RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { Badge, Card, CardTitulo } from "@/components/ui/Misc";
import { Money } from "@/components/ui/Money";
import { descreverEstado } from "@/lib/finance/bcb";
import { poupancaPelaRegra } from "@/lib/finance/rendimento";
import {
  acumuladoDosUltimos,
  anualDeMensal,
  competenciaAnterior,
  FONTE_INDICES,
  pctParaFracao,
  SLUG_SALARIO_MINIMO,
  urlHistorico,
  URL_FONTE_INDICES,
  valorMensalPct,
  type ItemIndice,
  type OrigemIndice,
} from "@/lib/finance/indices";
import { formatData } from "@/lib/format";
import { useIndices } from "@/lib/indicesStore";
import { AvisoReferencia, fmtNum, fmtPctFracao, lerNum, paraCampo } from "./util";

function SeloOrigem({ origem, parcial }: { origem: OrigemIndice | undefined; parcial?: boolean }) {
  if (origem === "bcb") return <Badge tom="primary">{parcial ? "BCB · parcial do mês" : "BCB"}</Badge>;
  if (origem === "manual") return <Badge tom="primary">manual</Badge>;
  return <Badge>semente (Debit)</Badge>;
}

function LinhaIndice({ item, automaticoDisponivel }: { item: ItemIndice; automaticoDisponivel: boolean }) {
  const atualizar = useIndices((s) => s.atualizar);
  const usarAutomatico = useIndices((s) => s.usarAutomatico);
  const [editando, setEditando] = useState(false);
  const [serieTxt, setSerieTxt] = useState("");
  const [compTxt, setCompTxt] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const mensal = valorMensalPct(item);
  const url = urlHistorico(item.slug);
  const acum = item.serie.length > 1 ? acumuladoDosUltimos(item.serie, item.serie.length) : null;

  function abrir() {
    setSerieTxt(item.serie.map((v) => paraCampo(v)).join("; "));
    setCompTxt(item.competencia);
    setErro(null);
    setEditando(true);
  }

  function salvar(e: FormEvent) {
    e.preventDefault();
    const partes = serieTxt.split(";").map((p) => lerNum(p));
    if (partes.length === 0 || partes.length > 24 || partes.some((p) => !Number.isFinite(p) || Math.abs(p) >= 1000)) {
      setErro("Informe os valores em % separados por ponto e vírgula (ex.: 0,07; -0,32; 0,82).");
      return;
    }
    if (compTxt && !/^\d{2}\/\d{4}$/.test(compTxt.trim())) {
      setErro("Competência no formato MM/AAAA.");
      return;
    }
    atualizar((c) => {
      const alvo = c.itens.find((i) => i.id === item.id);
      if (alvo) {
        alvo.serie = partes;
        alvo.competencia = compTxt.trim();
        alvo.aproximada = false;
        alvo.origem = "manual";
        delete alvo.parcial;
      }
      return c;
    });
    setEditando(false);
  }

  return (
    <>
      <tr className="border-t border-line align-top">
        <th scope="row" className="py-2 pr-3 text-left font-medium">
          {item.nome} <SeloOrigem origem={item.origem} parcial={item.parcial} />
        </th>
        <td className="py-2 pr-3 text-right tabular-nums">{fmtNum(mensal, 4)}%</td>
        <td className="py-2 pr-3 text-right tabular-nums">{fmtPctFracao(anualDeMensal(pctParaFracao(mensal)), 2)}</td>
        <td className="py-2 pr-3">
          {item.competencia ? competenciaAnterior(item.competencia, 0) : "—"}
          {item.aproximada && <span className="ml-1 text-xs text-muted">(aprox.)</span>}
          {item.parcial && <span className="ml-1 block text-xs text-muted">acumulado parcial do mês</span>}
        </td>
        <td className="py-2 pr-3 text-xs text-muted">
          {item.serie.length > 1 ? (
            <>
              {item.serie.map((v, k) => (
                <span key={k} className="mr-2 whitespace-nowrap">
                  {item.competencia ? `${competenciaAnterior(item.competencia, item.serie.length - 1 - k)}: ` : ""}
                  {fmtNum(v, 2)}
                </span>
              ))}
              {acum && <span className="whitespace-nowrap">· acumulado {acum.mesesUsados}m: {fmtPctFracao(acum.acumulado, 2)}</span>}
            </>
          ) : (
            "—"
          )}
        </td>
        <td className="py-2 pr-3">
          {url ? (
            <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-primary underline">
              ver histórico <ExternalLink size={12} aria-hidden />
            </a>
          ) : (
            <span className="text-muted">—</span>
          )}
        </td>
        <td className="py-2 text-right">
          {item.origem === "manual" && automaticoDisponivel && (
            <Button tamanho="sm" className="mr-1" onClick={() => usarAutomatico(item.id)} aria-label={`Usar valor automático de ${item.nome}`}>
              Usar automático
            </Button>
          )}
          <Button tamanho="sm" icone={<Pencil size={14} aria-hidden />} onClick={abrir} aria-label={`Editar ${item.nome}`} aria-expanded={editando}>
            Editar
          </Button>
        </td>
      </tr>
      {editando && (
        <tr className="bg-surface-2/50">
          <td colSpan={7} className="p-3">
            <form onSubmit={salvar} className="grid gap-3 sm:grid-cols-[2fr_1fr_auto] sm:items-end">
              <Input rotulo={`${item.nome}: últimos valores mensais em % (o último é o vigente)`} value={serieTxt} onChange={(e) => setSerieTxt(e.target.value)} dica="Separe por ponto e vírgula, do mais antigo ao mais recente." erro={erro} />
              <Input rotulo="Competência do último valor (MM/AAAA)" value={compTxt} onChange={(e) => setCompTxt(e.target.value)} placeholder="09/2026" />
              <div className="flex gap-2">
                <Button type="submit" variante="primary">
                  Salvar
                </Button>
                <Button onClick={() => setEditando(false)}>Cancelar</Button>
              </div>
            </form>
          </td>
        </tr>
      )}
    </>
  );
}

function Opcional({ rotulo, valor, onSalvar, dica, sufixo = "%", extra }: { rotulo: string; valor: number | null; onSalvar: (v: number | null) => void; dica?: string; sufixo?: string; extra?: ReactNode }) {
  const [txt, setTxt] = useState<string | null>(null);
  const mostrado = txt ?? (valor === null ? "" : paraCampo(valor));
  const invalido = txt !== null && txt.trim() !== "" && !Number.isFinite(lerNum(txt));
  return (
    <div>
      <Input
        rotulo={`${rotulo} (${sufixo})`}
        inputMode="decimal"
        value={mostrado}
        dica={dica}
        erro={invalido ? "Número inválido." : null}
        onChange={(e) => setTxt(e.target.value)}
        onBlur={() => {
          if (txt === null) return;
          if (txt.trim() === "") onSalvar(null);
          else {
            const v = lerNum(txt);
            if (Number.isFinite(v)) onSalvar(v);
          }
          setTxt(null);
        }}
      />
      {extra}
    </div>
  );
}

export function IndicesPanel() {
  const conjunto = useIndices((s) => s.conjunto);
  const atualizar = useIndices((s) => s.atualizar);
  const restaurar = useIndices((s) => s.restaurar);
  const usarAutomatico = useIndices((s) => s.usarAutomatico);
  const atualizarBcb = useIndices((s) => s.atualizarBcb);
  const bcb = useIndices((s) => s.bcb);
  const carregando = useIndices((s) => s.carregando);
  const erro = useIndices((s) => s.erro);
  const offline = useIndices((s) => s.offline);
  const avisoParcial = useIndices((s) => s.avisoParcial);
  const [editSM, setEditSM] = useState<string | null>(null);
  const taxas = conjunto.itens.filter((i) => i.grupo === "TAXA");
  const inflacao = conjunto.itens.filter((i) => i.grupo === "INFLACAO");
  const sm = conjunto.salarioMinimo;

  // no máximo uma busca automática a cada 6 h (respeita o cache); só ao abrir esta aba
  useEffect(() => {
    void useIndices.getState().atualizarBcb(false);
  }, []);

  const poupBcb = conjunto.itens.find((i) => i.id === "poupanca");
  const tr = conjunto.itens.find((i) => i.id === "tr");
  const selicAnual = conjunto.extras.selicMetaAnual !== null ? conjunto.extras.selicMetaAnual / 100 : null;
  const dicaPoup = selicAnual !== null && tr ? poupancaPelaRegra(selicAnual, pctParaFracao(valorMensalPct(tr))) : null;
  const autoSelic = conjunto.extras.selicMetaOrigem === "manual" ? bcb?.selicMeta ?? null : null;

  const tabela = (titulo: string, itens: ItemIndice[], id: string) => (
    <div className="mt-2 overflow-x-auto">
      <table className="w-full min-w-[720px] text-sm">
        <caption className="sr-only">{titulo}</caption>
        <thead>
          <tr className="text-left text-xs uppercase text-muted">
            <th scope="col" className="py-1 pr-3" id={id}>
              {titulo}
            </th>
            <th scope="col" className="py-1 pr-3 text-right">Mensal (% a.m.)</th>
            <th scope="col" className="py-1 pr-3 text-right">Anual equivalente</th>
            <th scope="col" className="py-1 pr-3">Competência</th>
            <th scope="col" className="py-1 pr-3">Últimos meses (%)</th>
            <th scope="col" className="py-1 pr-3">Histórico</th>
            <th scope="col" className="py-1 text-right"><span className="sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          {itens.map((i) => (
            <LinhaIndice key={i.id} item={i} automaticoDisponivel={!!bcb?.itens[i.id]} />
          ))}
        </tbody>
      </table>
    </div>
  );

  return (
    <div className="space-y-4">
      <AvisoReferencia>Valores de referência do Banco Central, atualizados ao abrir esta aba (no máximo a cada 6 horas) ou em &quot;Atualizar agora&quot;; você pode editar qualquer valor e a sua edição prevalece. Confira o histórico antes de decidir.</AvisoReferencia>
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-surface px-3 py-2 text-sm">
        <p role="status" aria-live="polite" className="min-w-0 flex-1">
          {descreverEstado({ atualizadoEm: bcb?.atualizadoEm ?? null, carregando, erro, offline })}
          {avisoParcial && <span className="mt-1 block text-xs text-warn">{avisoParcial}</span>}
        </p>
        <Button tamanho="sm" icone={<RefreshCw size={14} aria-hidden className={carregando ? "animate-spin" : ""} />} onClick={() => void atualizarBcb(true)} disabled={carregando}>
          Atualizar agora
        </Button>
      </div>
      <Card aria-labelledby="t-indices">
        <CardTitulo
          id="t-indices"
          acao={
            <Button tamanho="sm" icone={<RotateCcw size={14} aria-hidden />} onClick={restaurar}>
              Restaurar padrão (descarta edições manuais)
            </Button>
          }
        >
          Índices e taxas
        </CardTitulo>
        <p className="text-sm text-muted">
          {FONTE_INDICES}. Selos: <strong>BCB</strong> = valor do Banco Central (competência real); <strong>manual</strong> = editado por você (não é sobrescrito); <strong>semente (Debit)</strong> = valor de reserva de 28/10/2026, com competência aproximada. IGP-DI e IPCA-E vêm do BCB; IVAR, TBF e TJLP não têm série confirmada e ficam como a semente/edição. Selic e CDI do mês corrente são acumulados parciais. Anual equivalente = (1 + taxa mensal)^12 − 1; para a inflação é só o mês repetido por 12 vezes, não o acumulado de 12 meses.
        </p>
        <div className="mt-3 max-w-xs">
          <Input
            rotulo="Conferido em"
            type="date"
            value={conjunto.conferidoEm}
            onChange={(e) => {
              const v = e.target.value;
              if (/^\d{4}-\d{2}-\d{2}$/.test(v)) atualizar((c) => ({ ...c, conferidoEm: v }));
            }}
            dica={`Data da conferência: ${formatData(new Date(`${conjunto.conferidoEm}T12:00:00`).getTime())}`}
          />
        </div>
        {tabela("Taxas", taxas, "h-taxas")}
        {tabela("Inflação e preços", inflacao, "h-infl")}
        {poupBcb && dicaPoup !== null && (
          <p className="mt-3 rounded-xl bg-surface-2 px-3 py-2 text-xs text-muted">
            Poupança: taxa do mês <strong>{fmtNum(valorMensalPct(poupBcb), 4)}% a.m.</strong>
            {poupBcb.origem === "bcb" ? " (BCB, série 196)" : ""}. Dica calculada pela regra (
            {selicAnual !== null && selicAnual > 0.085 ? "Selic acima de 8,5% a.a.: 0,5% a.m. + TR" : "Selic até 8,5% a.a.: 70% da Selic + TR"}, com Selic meta de {fmtPctFracao(selicAnual ?? 0, 2)} a.a.):{" "}
            {fmtPctFracao(dicaPoup, 4)} a.m. A taxa real prevalece; a dica serve só de conferência.
          </p>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-line pt-3 text-sm">
          <span className="font-medium">Salário mínimo</span>
          {editSM === null ? (
            <>
              <Money valor={sm.centavos / 100} className="font-semibold" />
              <span className="text-xs text-muted">({sm.competencia || "—"})</span>
              <SeloOrigem origem={sm.origem} />
              {sm.origem === "manual" && bcb?.salarioMinimo && (
                <Button tamanho="sm" onClick={() => usarAutomatico("salarioMinimo")} aria-label="Usar valor automático do salário mínimo">
                  Usar automático
                </Button>
              )}
              <Button tamanho="sm" onClick={() => setEditSM(paraCampo(sm.centavos / 100, 2))} aria-label="Editar salário mínimo">
                Editar
              </Button>
            </>
          ) : (
            <form
              className="flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                const v = lerNum(editSM);
                if (!Number.isFinite(v) || v <= 0) return;
                atualizar((c) => ({ ...c, salarioMinimo: { ...c.salarioMinimo, centavos: Math.round(v * 100), origem: "manual" } }));
                setEditSM(null);
              }}
            >
              <Input rotulo="Salário mínimo (R$)" inputMode="decimal" value={editSM} onChange={(e) => setEditSM(e.target.value)} />
              <Button type="submit" variante="primary">Salvar</Button>
              <Button onClick={() => setEditSM(null)}>Cancelar</Button>
            </form>
          )}
          <a href={urlHistorico(SLUG_SALARIO_MINIMO) ?? "#"} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-primary underline">
            ver histórico <ExternalLink size={12} aria-hidden />
          </a>
        </div>
        <p className="mt-3 text-xs text-muted">
          Fonte geral:{" "}
          <a href={URL_FONTE_INDICES} target="_blank" rel="noopener noreferrer" className="underline">
            debit.com.br/tabelas/indicadores-economicos
          </a>
          , usada como valor de reserva e nos links &quot;ver histórico&quot; (só abrem a página do site). Os valores automáticos vêm de consultas públicas, somente leitura, a api.bcb.gov.br (SGS); nenhum dado seu é enviado.
        </p>
      </Card>

      <Card aria-labelledby="t-extras">
        <CardTitulo id="t-extras">Outros valores (informe ou use o automático)</CardTitulo>
        <div className="grid gap-3 sm:grid-cols-2">
          <Opcional
            rotulo="Meta da Selic"
            sufixo="% a.a."
            valor={conjunto.extras.selicMetaAnual}
            onSalvar={(v) => atualizar((c) => ({ ...c, extras: { ...c.extras, selicMetaAnual: v } }))}
            dica={`${conjunto.extras.selicMetaOrigem === "bcb" ? "Banco Central (série 432). " : conjunto.extras.selicMetaOrigem === "manual" ? "Valor informado por você. " : ""}Usada na regra da poupança (acima de 8,5% a.a.). Em branco: usa o valor do BCB ou a Selic mensal anualizada.`}
            extra={
              autoSelic ? (
                <Button tamanho="sm" className="mt-1" onClick={() => usarAutomatico("selicMeta")}>
                  Usar automático ({fmtNum(autoSelic.valor, 2)}% a.a.)
                </Button>
              ) : undefined
            }
          />
          <Opcional rotulo="Inflação acumulada em 12 meses" sufixo="%" valor={conjunto.extras.inflacao12m} onSalvar={(v) => atualizar((c) => ({ ...c, extras: { ...c.extras, inflacao12m: v } }))} dica="Opcional; usada como referência no ganho real e nos reajustes." />
          <Opcional
            rotulo="Juros do rotativo do cartão (estimativa, edite)"
            sufixo="% a.m."
            valor={conjunto.extras.taxaRotativoMensal}
            onSalvar={(v) => atualizar((c) => ({ ...c, extras: { ...c.extras, taxaRotativoMensal: v !== null && v >= 0 ? v : c.extras.taxaRotativoMensal } }))}
            dica="Estimativa de referência; confira a taxa no seu contrato/fatura."
          />
          <Opcional
            rotulo="Juros do cheque especial (estimativa, edite)"
            sufixo="% a.m."
            valor={conjunto.extras.taxaChequeEspecialMensal}
            onSalvar={(v) => atualizar((c) => ({ ...c, extras: { ...c.extras, taxaChequeEspecialMensal: v !== null && v >= 0 ? v : c.extras.taxaChequeEspecialMensal } }))}
            dica="Estimativa de referência; confira a taxa no seu contrato."
          />
        </div>
      </Card>
    </div>
  );
}
